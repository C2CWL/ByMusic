@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.height
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.playing.presentation.NowPlaying
import com.sosauce.chocola.feature.playing.presentation.NowPlayingDragToClose
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.Phase
import com.sosauce.chocola.core.presentation.preferences.rememberUseTurntable
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableEnabledOverride
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableMotionPaused
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableShared
import com.sosauce.chocola.feature.playing.presentation.components.TurntableColors
import com.sosauce.chocola.feature.playing.presentation.components.TurntableSharedState
import com.sosauce.chocola.feature.playing.presentation.components.rememberTurntableThemeState
import com.sosauce.chocola.feature.playing.presentation.components.turntableBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinator for the mini-player expand transition. All visual pieces
 * (container, the single cover, chrome alphas) read [progress] only.
 */
@Stable
class PlayerExpandController internal constructor(
    private val scope: CoroutineScope,
    private val animatable: Animatable<Float, AnimationVector1D>
) {
    var phase by mutableStateOf(Phase.Collapsed)
        private set
    var miniContainer by mutableStateOf<Rect?>(null)
    var miniArt by mutableStateOf<Rect?>(null)
    var fullArt by mutableStateOf<Rect?>(null)
    var reduceMotion: Boolean = false
    var pendingExpand by mutableStateOf(false)
        private set
    var pendingMeasure by mutableStateOf(false)
        private set

    /**
     * A swipe down that started on the settled page. The (hidden) page stays
     * composed until it ends, so the gesture is not cut off while the
     * transition layer draws the way back.
     */
    var pageDragging by mutableStateOf(false)
        private set

    /**
     * The transition card has drawn a frame with progress > 0. From then on the
     * library bar is hidden ([barHidden]) until progress is back at 0, so the
     * bar and the card (which starts as an exact copy of it) are never both
     * visible as two different things.
     */
    var cardDrawn by mutableStateOf(false)
        private set

    /** Pixel copy of the mini bar's glass surface, taken when it was tapped. */
    var miniSurface by mutableStateOf<ImageBitmap?>(null)
        private set

    val barHidden: Boolean get() = PlayerExpandMath.barHidden(animatable.value, cardDrawn)

    fun onCardDrawn() {
        if (!cardDrawn && animatable.value > 0f) cardDrawn = true
    }

    internal fun onHandoffFinished() {
        if (animatable.value <= 0f) cardDrawn = false
    }

    fun onMiniSurface(snapshot: ImageBitmap?) {
        if (snapshot != null) miniSurface = snapshot
    }

    val progress: Float get() = animatable.value
    val isRunning: Boolean get() = animatable.isRunning

    /**
     * Turntable page -> lyrics layer, inside the overlay. 0 = turntable page,
     * 1 = lyrics page. Only moves while the page itself is settled.
     */
    private val lyrics = Animatable(0f)
    private var lyricsJob: Job? = null

    val lyricsProgress: Float get() = lyrics.value

    /** Lyrics layer is open or on its way; back closes it first. */
    var lyricsOpen by mutableStateOf(false)
        private set

    fun openLyrics() {
        if (!PlayerExpandMath.isSettledExpanded(animatable.value)) return
        lyricsOpen = true
        runLyricsTo(1f, VinylLyricsMath.ENTER_MS)
    }

    fun closeLyrics() {
        lyricsOpen = false
        runLyricsTo(0f, VinylLyricsMath.EXIT_MS)
    }

    /** Drops the lyrics layer at once (player closing, leaving to another screen). */
    fun dismissLyricsNow() {
        lyricsOpen = false
        lyricsJob?.cancel()
        if (lyrics.value != 0f) {
            lyricsJob = scope.launch { lyrics.snapTo(0f) }
        }
    }

    private fun runLyricsTo(target: Float, durationMs: Int) {
        lyricsJob?.cancel()
        lyricsJob = scope.launch {
            if (reduceMotion) {
                lyrics.snapTo(target)
            } else {
                // Reversing mid-way starts from the current value: no jump.
                lyrics.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing))
            }
        }
    }

    private val settleEasing = Easing { PlayerExpandMath.settleEasing(it) }
    private var settleJob: Job? = null

    fun onMiniContainer(bounds: Rect) {
        miniContainer = bounds
    }

    fun onMiniArt(bounds: Rect) {
        miniArt = bounds
    }

    fun onFullArt(bounds: Rect) {
        if (bounds.width <= 0f || bounds.height <= 0f) return
        fullArt = bounds
        pendingMeasure = false
        if (pendingExpand && PlayerExpandMath.boundsReady(miniContainer, miniArt, fullArt)) {
            pendingExpand = false
            animateTo(1f)
        }
    }

    fun requestMeasure() {
        if (fullArt == null) pendingMeasure = true
    }

    fun open() {
        if (reduceMotion) {
            pendingExpand = false
            snapTo(1f)
            return
        }
        if (PlayerExpandMath.boundsReady(miniContainer, miniArt, fullArt)) {
            pendingExpand = false
            animateTo(1f)
        } else {
            pendingExpand = true
            pendingMeasure = true
            phase = Phase.Preparing
        }
    }

    fun onPageDragStarted() {
        dismissLyricsNow()
        pageDragging = true
    }

    /**
     * A page swipe ended. Continues from the current progress with the finger's
     * speed ([travelPx] converts px/s into progress/s); never restarts.
     */
    fun onPageDragSettled(velocityPxPerSec: Float, travelPx: Float = 0f) {
        pageDragging = false
        if (reduceMotion) return
        animateTo(
            PlayerExpandMath.pageDragSettleTarget(animatable.value, velocityPxPerSec),
            initialVelocity = PlayerExpandMath.progressVelocity(velocityPxPerSec, travelPx)
        )
    }

    fun collapse() {
        dismissLyricsNow()
        pageDragging = false
        pendingExpand = false
        if (reduceMotion) {
            snapTo(0f)
        } else {
            animateTo(0f)
        }
    }

    fun onDragDelta(deltaProgress: Float) {
        pendingExpand = false
        if (reduceMotion) return
        interruptSettle()
        phase = Phase.Dragging
        val next = PlayerExpandMath.applyDragDelta(animatable.value, deltaProgress)
        settleJob?.cancel()
        settleJob = scope.launch { animatable.snapTo(next) }
    }

    /** A drag on the card ended: settle from here, carrying the finger's speed. */
    fun onDragSettled(velocityPxPerSec: Float, travelPx: Float = 0f) {
        if (reduceMotion) return
        val target = PlayerExpandMath.settleTarget(animatable.value, velocityPxPerSec)
        animateTo(target, initialVelocity = PlayerExpandMath.progressVelocity(velocityPxPerSec, travelPx))
    }

    fun onPlayerNotReady() {
        dismissLyricsNow()
        pendingExpand = false
        if (animatable.value > 0f) {
            if (reduceMotion) snapTo(0f) else animateTo(0f)
        }
    }

    fun invalidateFullArt() {
        fullArt = null
    }

    /**
     * If a hidden measure pass never reports artwork bounds, drop the
     * invisible overlay so the library stays interactive. A still-pending
     * open then snaps to the settled page (no transition) rather than hanging.
     */
    fun cancelStuckMeasure() {
        pendingMeasure = false
        if (pendingExpand) {
            pendingExpand = false
            if (reduceMotion || fullArt == null) {
                snapTo(1f)
            } else {
                animateTo(1f)
            }
        }
    }

    private fun interruptSettle() {
        if (animatable.isRunning) {
            val kept = PlayerExpandMath.interruptSettle(animatable.value)
            settleJob?.cancel()
            settleJob = scope.launch {
                animatable.stop()
                animatable.snapTo(kept.second)
            }
            phase = kept.first
        }
    }

    /**
     * Taps / back use the same decelerate curve both ways, shortened by the
     * distance left (so a reverse mid-way is not a slow restart). A release
     * with speed uses a critically damped spring that starts at that speed:
     * no stop, no jump, no overshoot past 0 or 1 (the animatable is bounded).
     */
    private fun animateTo(target: Float, initialVelocity: Float = 0f) {
        settleJob?.cancel()
        phase = Phase.Settling
        val from = animatable.value
        val spec: AnimationSpec<Float> = if (initialVelocity == 0f) {
            tween(
                durationMillis = PlayerExpandMath.settleDurationMs(from, target),
                easing = settleEasing
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
        settleJob = scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = spec,
                initialVelocity = initialVelocity
            )
            phase = when {
                PlayerExpandMath.isSettledExpanded(animatable.value) -> Phase.Expanded
                PlayerExpandMath.isSettledCollapsed(animatable.value) -> Phase.Collapsed
                else -> Phase.Settling
            }
        }
    }

    private fun snapTo(target: Float) {
        settleJob?.cancel()
        settleJob = scope.launch {
            animatable.snapTo(target)
            phase = if (target >= 1f) Phase.Expanded else Phase.Collapsed
        }
    }
}

@Composable
fun rememberPlayerExpandController(initiallyExpanded: Boolean): PlayerExpandController {
    val scope = rememberCoroutineScope()
    val animatable = remember {
        // Bounded: a fast release can never push the card past the page or the bar.
        Animatable(if (initiallyExpanded) 1f else 0f).apply { updateBounds(0f, 1f) }
    }
    val controller = remember(scope, animatable) {
        PlayerExpandController(scope, animatable)
    }
    DisposableEffect(controller) {
        onDispose {
            // The remembered scope is cancelled with the composition; this
            // is a belt-and-braces clear of any settle job reference.
        }
    }
    return controller
}

@Composable
fun systemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

/**
 * Full-screen expand overlay. Rendered in a [Popup] so the library bottom bar
 * does not remeasure to the screen height while the page is opening. The popup
 * is pinned to the activity window origin (see [PlayerExpandPopup]); all bounds
 * fed to the controller are `boundsInWindow()` values in that same space.
 *
 * There is no endpoint hold. Progress 0 and 1 are rest states only.
 */
@Composable
fun PlayerExpandOverlay(
    controller: PlayerExpandController,
    musicState: MusicState,
    libraryStyle: Boolean,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    // Read and prepared while the overlay is hidden, so an open starts with the
    // turntable switch loaded and the artwork colors already picked.
    val useTurntable by rememberUseTurntable()
    val turntableThemeState = rememberTurntableThemeState(
        artworkUri = if (useTurntable) musicState.track.artUri else null
    )
    val turntableShared = remember { TurntableSharedState() }
    val miniBackground = if (libraryStyle) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    // The bar's colors, read here (outside the turntable theme) so the card's
    // copy of the bar looks exactly like the bar.
    val barColors = MaterialTheme.colorScheme
    val barContentColor = LocalContentColor.current
    val miniSurface = controller.miniSurface
    val skin = if (libraryStyle) {
        remember(miniSurface, barColors, barContentColor, miniBackground) {
            MiniCardSkin(miniSurface, miniBackground, barColors, barContentColor)
        }
    } else {
        null
    }

    val readProgress = remember(controller) { { controller.progress } }
    val movingOrExpanded by remember(controller) { derivedStateOf { controller.progress > 0f } }
    val interactive by remember(controller) {
        derivedStateOf { PlayerExpandPopup.isInteractive(controller.progress) }
    }
    // Hand-back to the bar: when progress reaches 0 the bar is shown again in
    // that frame (barHidden), and the card, now identical to it, stays on top
    // for a couple of frames so the popup window goes away over the same pixels.
    var lingering by remember(controller) { mutableStateOf(false) }
    LaunchedEffect(controller, movingOrExpanded) {
        if (movingOrExpanded) {
            lingering = true
        } else {
            if (lingering) {
                repeat(PlayerExpandMath.HANDOFF_LINGER_FRAMES) { withFrameNanos { } }
            }
            lingering = false
            controller.onHandoffFinished()
        }
    }
    val showOverlay = movingOrExpanded ||
        lingering ||
        controller.pendingMeasure ||
        controller.pendingExpand ||
        controller.phase == Phase.Preparing
    if (!showOverlay) return

    LaunchedEffect(controller.pendingMeasure, controller.pendingExpand) {
        if (controller.pendingMeasure || controller.pendingExpand) {
            delay(400)
            if (controller.fullArt == null && (controller.pendingMeasure || controller.pendingExpand)) {
                controller.cancelStuckMeasure()
            }
        }
    }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val viewport = Rect(
        0f,
        0f,
        windowInfo.containerSize.width.toFloat().coerceAtLeast(1f),
        windowInfo.containerSize.height.toFloat().coerceAtLeast(1f)
    )
    val viewportDp = with(density) {
        androidx.compose.ui.unit.DpSize(viewport.width.toDp(), viewport.height.toDp())
    }

    // Registered after the player's own back handler, so back closes the
    // lyrics layer first and lands on the turntable page.
    androidx.activity.compose.BackHandler(enabled = controller.lyricsOpen) {
        controller.closeLyrics()
    }

    // Pin the popup to the activity window origin (not to the bottom bar it is
    // composed in), so popup coordinates == activity window coordinates.
    Popup(
        popupPositionProvider = PlayerExpandPopup.positionProvider,
        properties = PopupProperties(
            flags = PlayerExpandPopup.flags(
                interactive = interactive
            ),
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            // A full-window gesture exclusion would block the system back swipe.
            excludeFromSystemGesture = false
        )
    ) {
        val style = VinylExpandMath.styleFor(useTurntable)
        val overlayBody: @Composable () -> Unit = {
            Box(
                modifier = Modifier.requiredSize(viewportDp.width, viewportDp.height)
            ) {
                val settled by remember(controller) {
                    derivedStateOf { PlayerExpandMath.isSettledExpanded(controller.progress) }
                }
                val prepareSettledPage by remember(controller) {
                    derivedStateOf { VinylExpandMath.shouldPrepareSettledPage(controller.progress) }
                }
                val miniContainer = controller.miniContainer
                val miniArt = controller.miniArt
                val fullArt = controller.fullArt
                val canDrawTransition = PlayerExpandMath.boundsReady(miniContainer, miniArt, fullArt)

                // The page is composed first so the transition layer sits above it
                // and its slot never moves between transition and settled states.
                val keepPageReady = canDrawTransition && prepareSettledPage
                val travel = if (miniContainer != null) {
                    PlayerExpandMath.travelPx(viewport.height, miniContainer.height)
                } else {
                    0f
                }
                // Lyrics layer over the settled turntable page (0 = page, 1 = lyrics).
                val lyricsProgress = if (style == VinylExpandMath.Style.Vinyl && settled) {
                    controller.lyricsProgress
                } else {
                    0f
                }
                val lyricsActive = style == VinylExpandMath.Style.Vinyl && settled && fullArt != null &&
                    (lyricsProgress > 0f || controller.lyricsOpen)
                if (lyricsActive) {
                    // The page (its backdrop included) fades out; this same opaque
                    // backdrop stays under it, so nothing behind the overlay shows.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .turntableBackdrop()
                    )
                }
                if (settled || controller.pendingMeasure ||
                    (controller.pendingExpand && !canDrawTransition) || keepPageReady ||
                    controller.pageDragging
                ) {
                    val hideForMeasure = !settled
                    // Hide the whole page, backdrop included (the turntable backdrop is
                    // drawn outside the page's own modifier).
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = when {
                                    hideForMeasure -> 0f
                                    lyricsActive -> VinylLyricsMath.pageAlpha(lyricsProgress)
                                    else -> 1f
                                }
                            }
                    ) {
                        CompositionLocalProvider(
                            LocalTurntableMotionPaused provides (hideForMeasure || lyricsActive),
                            com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableRecordHidden provides lyricsActive
                        ) {
                            NowPlaying(
                                modifier = Modifier.fillMaxSize(),
                                musicState = musicState,
                                onHandlePlayerActions = onHandlePlayerActions,
                                onNavigate = onNavigate,
                                onShrinkToSearchbar = controller::collapse,
                                onArtworkBoundsChanged = controller::onFullArt,
                                // Pull down anywhere on the settled page (both styles)
                                // runs the expand progress backwards with the finger.
                                dragToClose = if (canDrawTransition) {
                                    NowPlayingDragToClose(
                                        onStart = controller::onPageDragStarted,
                                        onDelta = { px ->
                                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                                        },
                                        onStop = { velocity -> controller.onPageDragSettled(velocity, travel) }
                                    )
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }

                if (!settled && canDrawTransition && style == VinylExpandMath.Style.Vinyl) {
                    VinylTransitionLayer(
                        progress = readProgress,
                        libraryStyle = libraryStyle,
                        musicState = musicState,
                        miniContainer = miniContainer!!,
                        miniArt = miniArt!!,
                        fullArt = fullArt!!,
                        viewport = viewport,
                        miniBackground = miniBackground,
                        turntable = turntableShared,
                        skin = skin,
                        onCardDrawn = controller::onCardDrawn,
                        onDragDelta = { px ->
                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                        },
                        onDragSettled = { velocity -> controller.onDragSettled(velocity, travel) },
                        onCollapse = controller::collapse,
                        onHandlePlayerActions = onHandlePlayerActions,
                        onNavigate = onNavigate
                    )
                } else if (!settled && canDrawTransition) {
                    ClassicTransitionLayer(
                        progress = readProgress,
                        libraryStyle = libraryStyle,
                        musicState = musicState,
                        miniContainer = miniContainer!!,
                        miniArt = miniArt!!,
                        fullArt = fullArt!!,
                        viewport = viewport,
                        skin = skin,
                        onCardDrawn = controller::onCardDrawn,
                        onDragDelta = { px ->
                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                        },
                        onDragSettled = { velocity -> controller.onDragSettled(velocity, travel) },
                        onCollapse = controller::collapse,
                        onHandlePlayerActions = onHandlePlayerActions,
                        onNavigate = onNavigate
                    )
                }

                if (lyricsActive) {
                    VinylLyricsLayer(
                        progress = lyricsProgress,
                        musicState = musicState,
                        fullArt = fullArt!!,
                        turntable = turntableShared,
                        onClose = controller::closeLyrics,
                        onNavigate = { screen ->
                            controller.dismissLyricsNow()
                            controller.collapse()
                            onNavigate(screen)
                        },
                        onHandlePlayerActions = onHandlePlayerActions
                    )
                }
            }
        }
        CompositionLocalProvider(
            LocalTurntableEnabledOverride provides useTurntable,
            LocalTurntableShared provides turntableShared,
            com.sosauce.chocola.feature.playing.presentation.components.LocalOpenVinylLyrics provides
                if (style == VinylExpandMath.Style.Vinyl) ({ controller.openLyrics() }) else null
        ) {
            if (style == VinylExpandMath.Style.Vinyl) {
                // One turntable color theme for the transition and the settled page,
                // so landing on the page does not pick colors again.
                TurntableColors(turntableThemeState) { overlayBody() }
            } else {
                overlayBody()
            }
        }
    }
}
