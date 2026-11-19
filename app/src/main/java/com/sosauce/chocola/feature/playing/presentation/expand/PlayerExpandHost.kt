@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.expand

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.NowPlayingButton
import com.sosauce.chocola.core.designsystem.components.ActionButtonsRow
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import com.sosauce.chocola.core.designsystem.components.CuteSlider
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.TitleAndArtist
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberHiddenNowPlayingButtons
import com.sosauce.chocola.feature.playing.presentation.NowPlaying
import com.sosauce.chocola.feature.playing.presentation.NowPlayingDragToClose
import com.sosauce.chocola.feature.playing.presentation.components.MoreOptionsButton
import com.sosauce.chocola.feature.playing.presentation.components.QuickActionsRow
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.Phase
import com.sosauce.chocola.core.presentation.preferences.rememberUseTurntable
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableEnabledOverride
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableMotionPaused
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableShared
import com.sosauce.chocola.feature.playing.presentation.components.TurntableColors
import com.sosauce.chocola.feature.playing.presentation.components.TurntableSharedState
import com.sosauce.chocola.feature.playing.presentation.components.rememberTurntableThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    private val settleSpec = tween<Float>(
        durationMillis = PlayerExpandMath.DURATION_MS,
        easing = FastOutSlowInEasing
    )
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

    fun onPageDragSettled(velocityPxPerSec: Float) {
        pageDragging = false
        if (reduceMotion) return
        animateTo(PlayerExpandMath.pageDragSettleTarget(animatable.value, velocityPxPerSec))
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

    fun onDragSettled(velocityPxPerSec: Float) {
        if (reduceMotion) return
        val target = PlayerExpandMath.settleTarget(animatable.value, velocityPxPerSec)
        animateTo(target, initialVelocity = 0f)
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

    private fun animateTo(target: Float, initialVelocity: Float = 0f) {
        settleJob?.cancel()
        phase = Phase.Settling
        settleJob = scope.launch {
            animatable.animateTo(
                targetValue = target,
                animationSpec = settleSpec,
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
        Animatable(if (initiallyExpanded) 1f else 0f)
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
    val miniBarTheme = MiniBarTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes
    )

    val progress = controller.progress
    val showOverlay = progress > 0f ||
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
                interactive = PlayerExpandPopup.isInteractive(progress)
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
                val settled = PlayerExpandMath.isSettledExpanded(progress)
                val miniContainer = controller.miniContainer
                val miniArt = controller.miniArt
                val fullArt = controller.fullArt
                val canDrawTransition = PlayerExpandMath.boundsReady(miniContainer, miniArt, fullArt)

                // The page is composed first so the transition layer sits above it
                // and its slot never moves between transition and settled states.
                val keepPageReady = style == VinylExpandMath.Style.Vinyl &&
                    canDrawTransition &&
                    VinylExpandMath.shouldPrepareSettledPage(progress)
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
                            .background(com.sosauce.chocola.feature.playing.presentation.components.turntableBackdropBrush())
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
                                // Swipe down on the settled turntable page runs the
                                // expand progress backwards (same transition layer).
                                dragToClose = if (style == VinylExpandMath.Style.Vinyl && canDrawTransition) {
                                    NowPlayingDragToClose(
                                        onStart = controller::onPageDragStarted,
                                        onDelta = { px ->
                                            val travel = PlayerExpandMath.travelPx(
                                                viewport.height,
                                                miniContainer!!.height
                                            )
                                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                                        },
                                        onStop = controller::onPageDragSettled
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
                        progress = progress,
                        libraryStyle = libraryStyle,
                        musicState = musicState,
                        miniContainer = miniContainer!!,
                        miniArt = miniArt!!,
                        fullArt = fullArt!!,
                        viewport = viewport,
                        miniBarTheme = miniBarTheme,
                        turntable = turntableShared,
                        onDragDelta = { px ->
                            val travel = PlayerExpandMath.travelPx(viewport.height, miniContainer.height)
                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                        },
                        onDragSettled = controller::onDragSettled,
                        onCollapse = controller::collapse,
                        onHandlePlayerActions = onHandlePlayerActions,
                        onNavigate = onNavigate
                    )
                } else if (!settled && canDrawTransition) {
                    TransitionLayer(
                        progress = progress,
                        libraryStyle = libraryStyle,
                        musicState = musicState,
                        miniContainer = miniContainer!!,
                        miniArt = miniArt!!,
                        fullArt = fullArt!!,
                        viewport = viewport,
                        onDragDelta = { px ->
                            val travel = PlayerExpandMath.travelPx(viewport.height, miniContainer.height)
                            controller.onDragDelta(PlayerExpandMath.dragDeltaProgress(px, travel))
                        },
                        onDragSettled = controller::onDragSettled,
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

@Composable
private fun TransitionLayer(
    progress: Float,
    libraryStyle: Boolean,
    musicState: MusicState,
    miniContainer: Rect,
    miniArt: Rect,
    fullArt: Rect,
    viewport: Rect,
    onDragDelta: (Float) -> Unit,
    onDragSettled: (Float) -> Unit,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val container = PlayerExpandMath.containerRect(progress, miniContainer, viewport)
    val cover = PlayerExpandMath.coverRect(progress, miniArt, fullArt)
    val containerCorner = PlayerExpandMath.containerCornerRadiusDp(progress, libraryStyle)
    val coverCorner = PlayerExpandMath.coverCornerRadiusDp(progress, libraryStyle)
    val fullChrome = PlayerExpandMath.fullChromeAlpha(progress)
    val topChrome = PlayerExpandMath.topChromeAlpha(progress)
    val dragState = rememberDraggableState { delta -> onDragDelta(delta) }

    Box(Modifier.fillMaxSize()) {
        val left = container.left
        val top = container.top
        val widthPx = container.width.coerceAtLeast(1f)
        val heightPx = container.height.coerceAtLeast(1f)
        val cornerDp = containerCorner.dp
        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .requiredSize(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .shadow(
                    elevation = if (progress in 0.02f..0.98f) 8.dp else 0.dp,
                    shape = RoundedCornerShape(cornerDp)
                )
                .clip(RoundedCornerShape(cornerDp))
                .background(MaterialTheme.colorScheme.background)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragSettled(velocity) }
                )
        ) {
            if (fullChrome > 0f) {
                TransitionChrome(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fullChrome },
                    musicState = musicState,
                    cover = cover,
                    container = container,
                    progress = progress,
                    topChromeAlpha = topChrome,
                    pointerEnabled = progress > 0.8f,
                    onCollapse = onCollapse,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onNavigate = onNavigate
                )
            }

            // Single cover, positioned in container space so the rising clip applies.
            val coverShape = RoundedCornerShape(coverCorner.dp)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (cover.left - container.left).roundToInt(),
                            (cover.top - container.top).roundToInt()
                        )
                    }
                    .requiredSize(
                        with(density) { cover.width.coerceAtLeast(1f).toDp() },
                        with(density) { cover.height.coerceAtLeast(1f).toDp() }
                    )
                    .clip(coverShape)
            ) {
                val artUri = musicState.track.artUri
                if (artUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                } else {
                    AsyncImage(
                        model = artUri,
                        contentDescription = stringResource(R.string.artwork),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val miniChrome = PlayerExpandMath.miniChromeAlpha(progress)
            if (miniChrome > 0f) {
                MiniChromeGhost(
                    musicState = musicState,
                    libraryStyle = libraryStyle,
                    miniContainer = miniContainer,
                    miniArt = miniArt,
                    alpha = miniChrome,
                    origin = container,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

@Composable
private fun TransitionChrome(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    cover: Rect,
    container: Rect,
    progress: Float,
    topChromeAlpha: Float,
    pointerEnabled: Boolean,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val hiddenButtons = rememberHiddenNowPlayingButtons()
    val startGap = with(density) { 12.dp.toPx() }
    val endGap = with(density) { 22.dp.toPx() }
    val gap = PlayerExpandMath.chromeGapPx(progress, startGap, endGap)
    val columnTopInContainer = (cover.bottom + gap - container.top).coerceAtLeast(0f)

    Box(modifier = modifier.graphicsLayer { alpha = if (pointerEnabled) 1f else 1f }) {
        if (topChromeAlpha > 0f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 15.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = topChromeAlpha },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (NowPlayingButton.CLOSE !in hiddenButtons) {
                    IconButton(
                        onClick = onCollapse,
                        enabled = pointerEnabled && topChromeAlpha > 0.5f,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)
                        ),
                        modifier = Modifier.size(
                            IconButtonDefaults.smallContainerSize(
                                IconButtonDefaults.IconButtonWidthOption.Wide
                            )
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_down),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Box(Modifier.weight(1f))
                MoreOptionsButton(
                    modifier = Modifier,
                    musicState = musicState,
                    onNavigate = onNavigate,
                    onShrinkToSearchbar = onCollapse,
                    onHandlePlayerActions = onHandlePlayerActions,
                    hiddenButtons = hiddenButtons
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, columnTopInContainer.roundToInt()) }
                .padding(horizontal = 15.dp)
                .graphicsLayer { this.alpha = 1f },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TitleAndArtist(
                title = musicState.track.title,
                artist = musicState.track.artist,
                album = musicState.track.album,
                isFavorite = musicState.track.isFavorite,
                onToggleFavorite = {
                    if (pointerEnabled) {
                        musicState.track.mediaId.toLongOrNull()
                            ?.let { onHandlePlayerActions(PlayerActions.ToggleFavorite(it)) }
                    }
                },
                hiddenButtons = hiddenButtons
            )
            CuteSlider(
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions
            )
            ActionButtonsRow(
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions,
                hiddenButtons = hiddenButtons
            )
            QuickActionsRow(
                musicState = musicState,
                onShowSpeedCard = {},
                onHandlePlayerActions = onHandlePlayerActions,
                hiddenButtons = hiddenButtons
            )
        }
    }
}

@Composable
internal fun MiniChromeGhost(
    musicState: MusicState,
    libraryStyle: Boolean,
    miniContainer: Rect,
    miniArt: Rect,
    alpha: Float,
    origin: Rect,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val left = miniArt.right + with(density) { 12.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (miniContainer.left - origin.left).roundToInt(),
                    (miniContainer.top - origin.top).roundToInt()
                )
            }
            .requiredSize(
                with(density) { miniContainer.width.coerceAtLeast(1f).toDp() },
                with(density) { miniContainer.height.coerceAtLeast(1f).toDp() }
            )
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = with(density) { (left - miniContainer.left).toDp() }, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = musicState.track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (libraryStyle) {
                    Text(
                        text = musicState.track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            PlayPauseButton(
                isPlaying = musicState.isPlaying,
                onHandlePlayerActions = onHandlePlayerActions
            )
            if (libraryStyle) {
                IconButton(onClick = { onNavigate(Screen.Queue) }) {
                    Icon(
                        painter = painterResource(R.drawable.queue),
                        contentDescription = stringResource(R.string.queue)
                    )
                }
            }
        }
    }
}
