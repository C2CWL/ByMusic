@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalDragOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.designsystem.components.liquidGlass
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.MiniPlayerDefaults
import com.sosauce.chocola.core.presentation.components.MiniPlayerFace
import com.sosauce.chocola.core.presentation.components.drawMusicPosition
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberArtLyrics
import com.sosauce.chocola.core.presentation.preferences.rememberCarousel
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.core.presentation.preferences.rememberUseTurntable
import com.sosauce.chocola.feature.lyrics.presentation.LyricsList
import com.sosauce.chocola.feature.playing.presentation.NowPlaying
import com.sosauce.chocola.feature.playing.presentation.VinylLayout
import com.sosauce.chocola.feature.playing.presentation.components.LocalArtworkSlot
import com.sosauce.chocola.feature.playing.presentation.components.LocalOpenVinylLyrics
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableEnabledOverride
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableMotionPaused
import com.sosauce.chocola.feature.playing.presentation.components.LocalTurntableShared
import com.sosauce.chocola.feature.playing.presentation.components.TURNTABLE_COVER_SWAP_MS
import com.sosauce.chocola.feature.playing.presentation.components.TurntableColors
import com.sosauce.chocola.feature.playing.presentation.components.TurntableDisc
import com.sosauce.chocola.feature.playing.presentation.components.TurntableSharedState
import com.sosauce.chocola.feature.playing.presentation.components.TURNTABLE_COLOR_CHANGE_MS
import com.sosauce.chocola.feature.playing.presentation.components.playerTurntableThemeState
import com.sosauce.chocola.feature.playing.presentation.components.rememberPlayerArtworkSeed
import com.sosauce.chocola.feature.playing.presentation.components.turntableBackdrop
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.smoothstep
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSheet(
    state: PlayerSheetState,
    route: Any,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val entry = state.dockEntry(route)
    val dock = state.dock(entry)
    val vinyl by rememberUseTurntable()
    val landscape = rememberIsLandscape()
    val portraitVinyl = vinyl && !landscape
    val artworkSeed = rememberPlayerArtworkSeed(musicState.track.artUri.takeIf { musicState.isPlayerReady })
    val turntableTheme = playerTurntableThemeState(artworkSeed)
    val turntable = remember { TurntableSharedState() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    var root by remember { mutableStateOf<SheetRect?>(null) }
    var fullArtInWindow by remember { mutableStateOf<SheetRect?>(null) }
    var savedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingToRestore by remember { mutableStateOf(savedRoute != null) }
    var hadReadyPlayer by remember { mutableStateOf(false) }
    val miniFocus = remember { FocusRequester() }
    val fullFocus = remember { FocusRequester() }
    var transferFocus by remember { mutableStateOf(false) }
    val activityWindow = LocalActivity.current?.window
    val routeIdentity = route.toString()

    val window = root
    val viewport = window?.offset(-window.left, -window.top)
    val dockRect = if (dock != null && window != null) dock.bounds.offset(-window.left, -window.top) else null
    val measuredArt = if (window != null) fullArtInWindow?.offset(-window.left, -window.top) else null
    // Preserve an in-bounds layout slot while letting the portrait record bleed off the edge.
    val fullArt = measuredArt?.let {
        if (portraitVinyl) it.offset(VinylLayout.portraitDiscOffset(it.width, rtl), 0f) else it
    }
    val ready = musicState.isPlayerReady && dockRect != null && measuredArt != null && viewport != null &&
        measuredArt.left >= 0f && measuredArt.top >= 0f &&
        measuredArt.right <= viewport.right + 1f && measuredArt.bottom <= viewport.bottom + 1f

    val hostedEntry = entry.takeIf { dock != null && musicState.isPlayerReady }
    LaunchedEffect(ready, hostedEntry, routeIdentity, musicState.isPlayerReady) {
        state.updateHost(hostedEntry, ready)
        if (hadReadyPlayer && !musicState.isPlayerReady) {
            waitingToRestore = false
            savedRoute = null
        }
        hadReadyPlayer = musicState.isPlayerReady
        if (waitingToRestore && savedRoute != routeIdentity) {
            waitingToRestore = false
            savedRoute = null
        }
        if (waitingToRestore && ready) {
            waitingToRestore = false
            state.open()
        }
    }
    LaunchedEffect(state.ownsBack, ready, hostedEntry) {
        if (state.ownsBack && !ready) {
            delay(500L)
            state.cancelPendingOpen()
        }
    }
    LaunchedEffect(state.settled, ready, routeIdentity) {
        if (!waitingToRestore && ready) {
            when (state.settled) {
                0f -> savedRoute = null
                1f -> savedRoute = routeIdentity
            }
        } else if (!waitingToRestore) {
            savedRoute = null
        }
    }
    LaunchedEffect(viewport?.width, viewport?.height, fullArt, vinyl, rtl) {
        state.cancelDrag()
    }
    LaunchedEffect(state.ownsBack) {
        if (state.ownsBack) {
            transferFocus = true
            focusManager.clearFocus(force = true)
        }
    }
    LaunchedEffect(state.settled, state.lyricsVisible, ready) {
        if (ready && transferFocus) {
            if (state.settled == 1f && !state.lyricsVisible) fullFocus.requestFocus()
            if (state.settled == 0f) {
                miniFocus.requestFocus()
                transferFocus = false
            }
        }
    }
    val darkBackdrop by remember(state) { derivedStateOf { state.progress >= .75f } }
    val darkSheet = vinyl && darkBackdrop && hostedEntry != null
    DisposableEffect(activityWindow, darkSheet) {
        val controller = activityWindow?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val status = controller?.isAppearanceLightStatusBars
        val navigation = controller?.isAppearanceLightNavigationBars
        if (darkSheet) {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (darkSheet && controller != null && status != null && navigation != null) {
                controller.isAppearanceLightStatusBars = status
                controller.isAppearanceLightNavigationBars = navigation
            }
        }
    }

    // Keep root measurement alive on routes that have no dock.
    Box(Modifier.fillMaxSize().onGloballyPositioned { coordinates ->
        val origin = coordinates.localToWindow(Offset.Zero)
        root = SheetRect(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
    }, contentAlignment = AbsoluteAlignment.TopLeft) {
        if (!musicState.isPlayerReady || dock == null || dockRect == null || viewport == null) return@Box

        val glass = dock.glass
        val colors = MaterialTheme.colorScheme
        val tintBase = colors.surfaceContainerLow
        val artTint = animateColorAsState(
            targetValue = lerp(tintBase, artworkSeed ?: tintBase, if (tintBase.luminance() > .5f) .20f else .11f),
            animationSpec = tween(TURNTABLE_COLOR_CHANGE_MS),
            label = "Player artwork tint"
        )
        val pixelsPerDp = density.density
        val startArtSize = with(density) {
            (if (glass) MiniPlayerDefaults.GLASS_ART_DP else MiniPlayerDefaults.FLOATING_ART_DP).dp.roundToPx().toFloat()
        }
        val startArtInset = with(density) {
            // Floating thumbnail: the row includes 6dp of vertical padding on each side.
            8.dp.roundToPx().toFloat()
        }
        val startArtLeft = if (rtl) dockRect.width - startArtInset - startArtSize else startArtInset
        val startArtTop = if (glass) {
            ((dockRect.height - startArtSize) / 2f).roundToInt().toFloat()
        } else {
            with(density) { 6.dp.roundToPx().toFloat() }
        }
        val miniArt = remember(startArtLeft, startArtTop, startArtSize) {
            SheetRect(startArtLeft, startArtTop, startArtLeft + startArtSize, startArtTop + startArtSize)
        }
        val label = remember(fullArt, miniArt, dockRect, vinyl, rtl, portraitVinyl) {
            when {
                fullArt == null -> miniArt.offset(dockRect.left, dockRect.top)
                vinyl -> VinylExpandMath.labelSlot(fullArt, rtl, portraitVinyl)
                else -> fullArt
            }
        }
        val surface by remember(dockRect, viewport, state) {
            derivedStateOf { PlayerExpandMath.containerRect(state.progress, dockRect, viewport) }
        }
        val cover by remember(dockRect, viewport, miniArt, label, pixelsPerDp, state) {
            derivedStateOf {
                val g = state.progress
                PlayerExpandMath.coverRect(g, miniArt, label, surface, PlayerExpandMath.settleOffsetDp(g) * pixelsPerDp)
            }
        }
        val record by remember(dockRect, viewport, miniArt, label, pixelsPerDp, rtl, portraitVinyl, state) {
            derivedStateOf { VinylExpandMath.artAroundLabel(cover, rtl, portraitVinyl) }
        }
        val topCorner = if (glass) MiniPlayerDefaults.CORNER_DP else 24f
        val scrim = colors.scrim
        val miniInteractive = state.settled == 0f && !state.ownsBack
        val fullInteractive = state.settled == 1f && !state.lyricsVisible
        val lyricsVisible = vinyl && state.lyricsVisible && fullArt != null
        val artSlot: @Composable (Modifier) -> Unit = remember {
            { modifier ->
                Box(modifier.onGloballyPositioned { coordinates ->
                    val origin = coordinates.localToWindow(Offset.Zero)
                    fullArtInWindow = if (coordinates.size.width > 0 && coordinates.size.height > 0 &&
                        origin.x.isFinite() && origin.y.isFinite()
                    ) {
                        SheetRect(origin.x, origin.y, origin.x + coordinates.size.width, origin.y + coordinates.size.height)
                    } else null
                })
            }
        }

        val travel = PlayerExpandMath.travelPx(dockRect.top, viewport.top)
        val openAction = stringResource(R.string.now_playing)
        val goTo: (Screen) -> Unit = { state.navigate(it, onNavigate) }
        val glassCovered by remember(state) { derivedStateOf { state.progress >= .25f } }
        val backdrop = dock.backdrop
        DisposableEffect(backdrop, glassCovered) {
            backdrop?.frozen = glassCovered
            onDispose { backdrop?.frozen = false }
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val g = state.progress
                    shape = SheetOutline(
                        surface,
                        PlayerExpandMath.corner(g, topCorner).dp.toPx(),
                        PlayerExpandMath.corner(g, dock.bottomCornerDp).dp.toPx()
                    )
                    shadowElevation = if (glass) MiniPlayerDefaults.ELEVATION_DP.dp.toPx() * PlayerExpandMath.shadowAlpha(g) else 0f
                    ambientShadowColor = scrim.copy(alpha = .12f)
                    spotShadowColor = scrim.copy(alpha = .20f)
                    clip = true
                }
                .sheetDrag(state, entry, travel, fullArt, dock)
                .then(if (portraitVinyl && fullInteractive && fullArt != null) {
                    Modifier.vinylTrackSwipe(fullArt, rtl, onHandlePlayerActions)
                } else Modifier),
            contentAlignment = AbsoluteAlignment.TopLeft
        ) {
            val dockWidth = dockRect.width.roundToInt()
            val dockHeight = dockRect.height.roundToInt()
            val floatingColor = colors.surfaceContainer
            val floatingPositionColor = colors.surfaceContainerHigh
            if (glass) {
                val shape = remember { RoundedCornerShape(MiniPlayerDefaults.CORNER_DP.dp) }
                Box(Modifier
                    .fixedSlot(dockWidth, dockHeight)
                    .layout { measurable, constraints ->
                        val glassLayer = measurable.measure(constraints)
                        layout(glassLayer.width, glassLayer.height) {
                            glassLayer.place(surface.left.roundToInt(), surface.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = surface.width / size.width.coerceAtLeast(1f)
                        scaleY = surface.height / size.height.coerceAtLeast(1f)
                    }
                    .drawWithContent {
                        if (PlayerExpandMath.tintAlpha(state.progress) < 1f) drawContent()
                    }
                    .liquidGlass(shape, backdrop = backdrop))
            }
            Box(Modifier.fillMaxSize().drawBehind {
                val tint = PlayerExpandMath.tintAlpha(state.progress)
                if (!glass) drawRect(floatingColor)
                if (tint > 0f) drawRect(artTint.value, alpha = tint)
            })

            val fullPlayer: @Composable () -> Unit = {
                val vinylColors = MaterialTheme.colorScheme
                if (vinyl) {
                    val backdropModifier = if (portraitVinyl) {
                        val brush = remember(vinylColors.surface, vinylColors.primary) {
                            Brush.verticalGradient(
                                0f to vinylColors.surface,
                                .64f to vinylColors.surface,
                                1f to lerp(vinylColors.surface, vinylColors.primary, .16f)
                            )
                        }
                        Modifier.background(brush)
                    } else Modifier.background(vinylColors.surface).turntableBackdrop()
                    Box(
                        Modifier.fillMaxSize()
                            .graphicsLayer { alpha = VinylExpandMath.backdropAlpha(state.progress) }
                            .then(backdropModifier)
                    )
                }
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            alpha = PlayerExpandMath.chromeAlpha(state.progress) *
                                VinylLyricsMath.pageAlpha(state.lyricsProgress)
                        }
                        // Draw-only translation keeps slot coordinates in the untransformed viewport.
                        .drawWithContent {
                            translate(top = PlayerExpandMath.settleOffsetDp(state.progress).dp.toPx()) {
                                this@drawWithContent.drawContent()
                            }
                        }
                        .sheetSemantics(fullInteractive)
                        .then(if (fullInteractive) Modifier.focusRequester(fullFocus).focusable() else Modifier)
                ) {
                    CompositionLocalProvider(
                        LocalArtworkSlot provides artSlot,
                        LocalTurntableEnabledOverride provides vinyl,
                        LocalOpenVinylLyrics provides if (vinyl) state::openLyrics else null
                    ) {
                        NowPlaying(
                            modifier = Modifier.fillMaxSize(),
                            musicState = musicState,
                            onHandlePlayerActions = onHandlePlayerActions,
                            onNavigate = goTo,
                            onShrinkToSearchbar = state::close
                        )
                    }
                }
                if (vinyl) {
                    val artBase = fullArt?.width?.roundToInt() ?: startArtSize.roundToInt()
                    val recordDegrees: (Float) -> Float = { angle ->
                        VinylExpandMath.normalizeDegrees(angle) * VinylExpandMath.discAlpha(state.progress)
                    }
                    Box(Modifier.fixedSlot(artBase, artBase).graphicsLayer {
                        project(record)
                        alpha = if (lyricsVisible) 0f else 1f
                    }) {
                        CompositionLocalProvider(
                            LocalTurntableShared provides turntable,
                            LocalTurntableMotionPaused provides !fullInteractive
                        ) {
                            TurntableDisc(
                                musicState = musicState,
                                blur = 0,
                                drawLabel = portraitVinyl,
                                slideTracks = portraitVinyl,
                                recordAlpha = { VinylExpandMath.discAlpha(state.progress) },
                                armAlpha = { VinylExpandMath.armAlpha(state.progress) },
                                armOffset = { VinylExpandMath.armOffset(state.progress) },
                                rotationDegrees = recordDegrees
                            )
                        }
                    }
                }
            }
            if (vinyl) TurntableColors(turntableTheme, fullPlayer) else fullPlayer()

            Box(
                Modifier.fixedSlot(dockWidth, dockHeight)
                    .graphicsLayer {
                        val fade = PlayerExpandMath.miniFaceAlpha(state.progress)
                        alpha = fade
                        translationX = surface.left
                        translationY = surface.top - 6.dp.toPx() * (1f - fade)
                    }
                    .sheetSemantics(miniInteractive)
                    .then(if (miniInteractive) {
                        Modifier.focusRequester(miniFocus).clickable(onClickLabel = openAction, onClick = state::open)
                    } else Modifier)
                    .then(if (glass) Modifier else Modifier.drawMusicPosition(
                        position = { musicState.position.toFloat() },
                        trackDuration = { musicState.duration },
                        color = floatingPositionColor
                    )),
                contentAlignment = Alignment.Center
            ) {
                if (glass) {
                    MiniPlayerFace(musicState, onHandlePlayerActions, goTo)
                } else {
                    FloatingPlayerFace(musicState, onHandlePlayerActions)
                }
            }

            SheetArtwork(
                state = state,
                musicState = musicState,
                readCover = { cover },
                fullArt = measuredArt,
                travel = travel,
                baseSide = label.width.roundToInt().coerceAtLeast(startArtSize.roundToInt()),
                miniArtSize = startArtSize,
                miniIconDp = if (glass) 22f else 20f,
                vinyl = vinyl,
                portraitVinyl = portraitVinyl,
                rotation = { turntable.rotation.value },
                lyricsVisible = lyricsVisible,
                interactive = fullInteractive,
                onHandlePlayerActions = onHandlePlayerActions
            )
            // Above both sets of controls, below the independent lyrics page.
            if (!miniInteractive && !fullInteractive) {
                Box(Modifier.fillMaxSize().pointerInput(state) {
                    detectTapGestures(onTap = { state.reverse() })
                })
            }

            if (lyricsVisible && fullArt != null && window != null) {
                TurntableColors(turntableTheme) {
                    VinylLyricsLayer(
                        progress = state.lyricsProgress,
                        musicState = musicState,
                        fullArt = Rect(fullArt.left, fullArt.top, fullArt.right, fullArt.bottom),
                        windowOrigin = Offset(window.left, window.top),
                        rtl = rtl,
                        portraitVinyl = portraitVinyl,
                        turntable = turntable,
                        onClose = state::closeLyrics,
                        onNavigate = goTo,
                        onHandlePlayerActions = onHandlePlayerActions
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingPlayerFace(musicState: MusicState, onAction: (PlayerActions) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.padding(start = 8.dp).size(MiniPlayerDefaults.FLOATING_ART_DP.dp))
        AnimatedContent(
            targetState = musicState.track.title,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            },
            modifier = Modifier.padding(start = 12.dp).weight(1f)
        ) {
            Text(it, Modifier.basicMarquee())
        }
        IconButton(onClick = { onAction(PlayerActions.SeekToPreviousMusic) }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.previous_song))
        }
        PlayPauseButton(isPlaying = musicState.isPlaying, onHandlePlayerActions = onAction)
        IconButton(onClick = { onAction(PlayerActions.SeekToNextMusic) }, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.skip_next), stringResource(R.string.next_song))
        }
    }
}

private data class SheetOutline(
    val rect: SheetRect,
    val top: Float,
    val bottom: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = Rect(rect.left, rect.top, rect.right, rect.bottom)
        if (top == 0f && bottom == 0f) return Outline.Rectangle(bounds)
        val maxRadius = minOf(rect.width, rect.height) / 2f
        val topRadius = CornerRadius(top.coerceAtMost(maxRadius))
        val bottomRadius = CornerRadius(bottom.coerceAtMost(maxRadius))
        return Outline.Rounded(
            RoundRect(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                topLeftCornerRadius = topRadius,
                topRightCornerRadius = topRadius,
                bottomLeftCornerRadius = bottomRadius,
                bottomRightCornerRadius = bottomRadius
            )
        )
    }
}

// A slot changes size on endpoint layout changes. Motion only updates its layer.
private fun Modifier.fixedSlot(width: Int, height: Int) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(1), height.coerceAtLeast(1)))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private fun GraphicsLayerScope.project(rect: SheetRect) {
    transformOrigin = TransformOrigin(0f, 0f)
    translationX = rect.left
    translationY = rect.top
    scaleX = rect.width / size.width.coerceAtLeast(1f)
    scaleY = rect.height / size.height.coerceAtLeast(1f)
}

private fun Modifier.sheetSemantics(enabled: Boolean): Modifier =
    if (enabled) this else focusProperties { canFocus = false }.clearAndSetSemantics { hideFromAccessibility() }

@Composable
private fun Modifier.sheetDrag(
    state: PlayerSheetState,
    entry: PlayerDockEntry?,
    travel: Float,
    fullArt: SheetRect?,
    dock: PlayerDock
): Modifier {
    val currentDock by rememberUpdatedState(dock)
    return pointerInput(state, entry, travel, fullArt) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            if (!state.canStartGesture()) return@awaitEachGesture
            val atDock = state.progress == 0f
            val atFull = state.progress == 1f
            if (atFull && fullArt != null) {
                val margin = 24.dp.toPx()
                val nearArt = down.position.x in (fullArt.left - margin)..(fullArt.right + margin) &&
                    down.position.y <= fullArt.bottom + margin
                if (!nearArt) return@awaitEachGesture
            }

            var start: Float? = null
            var displacement = 0f
            var stoppingPlayback = false
            var dragDock: PlayerDock? = null
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val change = awaitVerticalTouchSlopOrCancellation(down.id) { event, overSlop ->
                // The sheet's clip scopes hit testing. Children see Main pass first.
                val movement = event.position - down.position
                if (abs(movement.x) > abs(movement.y) ||
                    atFull && overSlop < 0f ||
                    atDock && overSlop > 0f && currentDock.dragDown == null
                ) {
                    return@awaitVerticalTouchSlopOrCancellation
                }
                stoppingPlayback = atDock && overSlop > 0f && currentDock.dragDown != null
                if (stoppingPlayback) dragDock = currentDock
                if (!stoppingPlayback) start = state.beginDrag()
                if (stoppingPlayback || start != null) {
                    displacement = overSlop
                    if (stoppingPlayback) dragDock?.dragDown?.invoke(displacement)
                    event.consume()
                    tracker.addPosition(event.uptimeMillis, event.position)
                }
            } ?: return@awaitEachGesture
            val dragStart = start
            fun projectedProgress(): Float = PlayerExpandMath.dragProgress(dragStart ?: 0f, displacement, travel)
            if (dragStart != null) state.dragTo(projectedProgress())
            var completed = false
            try {
                var pointer = change.id
                while (true) {
                    val event = awaitVerticalDragOrCancellation(pointer) ?: break
                    if (event.id != pointer) tracker.resetTracking()
                    pointer = event.id
                    tracker.addPosition(event.uptimeMillis, event.position)
                    if (event.changedToUpIgnoreConsumed()) {
                        completed = true
                        break
                    }
                    displacement += event.position.y - event.previousPosition.y
                    if (stoppingPlayback) dragDock?.dragDown?.invoke(displacement)
                    else state.dragTo(projectedProgress())
                    event.consume()
                }
                if (!stoppingPlayback && completed && dragStart != null) {
                    state.endDrag(projectedProgress(), tracker.calculateVelocity().y, travel, atFull)
                }
            } finally {
                if (stoppingPlayback) dragDock?.stopDragDown?.invoke(completed && displacement > 0f)
                else state.cancelDrag()
            }
        }
    }
}

@Composable
private fun Modifier.vinylTrackSwipe(
    bounds: SheetRect,
    rtl: Boolean,
    onAction: (PlayerActions) -> Unit
): Modifier {
    val action by rememberUpdatedState(onAction)
    return pointerInput(bounds, rtl) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.x !in bounds.left..bounds.right || down.position.y !in bounds.top..bounds.bottom) {
                return@awaitEachGesture
            }
            var displacement = 0f
            val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                val delta = change.position - down.position
                if (abs(delta.x) > abs(delta.y)) {
                    displacement = overSlop
                    change.consume()
                }
            } ?: return@awaitEachGesture
            val ended = horizontalDrag(drag.id) { change ->
                displacement += change.position.x - change.previousPosition.x
                change.consume()
            }
            if (ended && abs(displacement) > viewConfiguration.touchSlop) {
                val previous = (displacement > 0f) != rtl
                action(if (previous) PlayerActions.SeekToPreviousMusic else PlayerActions.SeekToNextMusic)
            }
        }
    }
}

@Composable
private fun SheetArtwork(
    state: PlayerSheetState,
    musicState: MusicState,
    readCover: () -> SheetRect,
    fullArt: SheetRect?,
    travel: Float,
    baseSide: Int,
    miniArtSize: Float,
    miniIconDp: Float,
    vinyl: Boolean,
    portraitVinyl: Boolean,
    rotation: () -> Float,
    lyricsVisible: Boolean,
    interactive: Boolean,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val carousel by rememberCarousel()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val artLyrics by rememberArtLyrics()
    var showLyrics by remember(musicState.track.mediaId) { mutableStateOf(false) }
    LaunchedEffect(state.settled) {
        if (state.settled == 0f) showLyrics = false
    }
    val action by rememberUpdatedState(onHandlePlayerActions)
    val context = LocalContext.current
    val artUri = musicState.track.artUri
    val request = remember(artUri, context) {
        ImageRequest.Builder(context).data(artUri).crossfade(TURNTABLE_COVER_SWAP_MS).build()
    }
    var lastCover by remember { mutableStateOf<Painter?>(null) }
    val background = colors.surfaceContainerHigh
    Box(
        Modifier.fixedSlot(baseSide, baseSide)
            .graphicsLayer {
                val g = state.progress
                val cover = readCover()
                project(cover)
                transformOrigin = TransformOrigin.Center
                translationX += (cover.width - size.width) / 2f
                translationY += (cover.height - size.height) / 2f
                val end = if (vinyl) cover.width / 2f else 14.dp.toPx()
                val radius = PlayerExpandMath.lerp(
                    MiniPlayerDefaults.ART_CORNER_DP.dp.toPx(),
                    end,
                    smoothstep(0f, .6f, g)
                )
                shape = RoundedCornerShape(radius / scaleX.coerceAtLeast(.001f))
                clip = true
                rotationZ = if (vinyl) VinylExpandMath.normalizeDegrees(rotation()) * VinylExpandMath.discAlpha(g) else 0f
                alpha = when {
                    lyricsVisible -> 0f
                    // Hand the shared cover off to the sliding record at the full-size endpoint.
                    portraitVinyl -> 1f - smoothstep(.85f, 1f, g)
                    else -> 1f
                }
            }
            .background(background)
            .sheetSemantics(interactive)
            .then(if (artLyrics && interactive) Modifier.clickable { showLyrics = !showLyrics } else Modifier)
            .then(if (carousel && interactive && !vinyl) Modifier.pointerInput(rtl) {
                var delta = 0f
                detectHorizontalDragGestures(
                    onDragStart = { delta = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        delta += if (rtl) -amount else amount
                    },
                    onDragEnd = {
                        if (delta > viewConfiguration.touchSlop) action(PlayerActions.SeekToPreviousMusic)
                        else if (delta < -viewConfiguration.touchSlop) action(PlayerActions.SeekToNextMusic)
                    }
                )
            } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.music_note),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(.4f).graphicsLayer {
                val expectedMiniFraction = miniIconDp.dp.toPx() / miniArtSize
                scaleX = PlayerExpandMath.lerp(expectedMiniFraction / .4f, 1f, state.progress)
                scaleY = scaleX
            },
            tint = contentColorFor(background)
        )
        AsyncImage(
            model = request,
            contentDescription = stringResource(R.string.artwork),
            placeholder = lastCover,
            onSuccess = { lastCover = it.painter },
            onError = { lastCover = null },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
    if (showLyrics && fullArt != null) {
        val scroll = remember(state, travel) { ArtworkLyricsScroll(state, travel) }
        DisposableEffect(scroll) { onDispose { scroll.cancel() } }
        Box(
            Modifier.fixedSlot(fullArt.width.roundToInt(), fullArt.height.roundToInt())
                .graphicsLayer {
                    project(fullArt)
                    val g = state.progress
                    alpha = if (lyricsVisible) 0f else PlayerExpandMath.chromeAlpha(g)
                    translationY += PlayerExpandMath.settleOffsetDp(g).dp.toPx()
                }
                .sheetSemantics(interactive)
                .clickable(enabled = interactive, onClickLabel = stringResource(R.string.close)) { showLyrics = false }
                .nestedScroll(scroll)
                .background(colors.surface.copy(alpha = .88f))
        ) {
            LyricsList(
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions,
                textShadow = true,
                emptyLyrics = { Text(stringResource(R.string.no_lyrics_note)) }
            )
        }
    }
}

/** Receives downward deltas left over after the inline artwork lyrics reach their top. */
private class ArtworkLyricsScroll(
    private val state: PlayerSheetState,
    private val travel: Float
) : NestedScrollConnection {
    private var start: Float? = null
    private var displacement = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !state.dragging) return Offset.Zero
        return update(available.y)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f || state.settled != 1f) return Offset.Zero
        start = state.beginDrag() ?: return Offset.Zero
        displacement = 0f
        return update(available.y)
    }

    private fun update(deltaY: Float): Offset {
        val initial = start ?: return Offset.Zero
        displacement += deltaY
        state.dragTo(PlayerExpandMath.dragProgress(initial, displacement, travel))
        return Offset(0f, deltaY)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val initial = start ?: return Velocity.Zero
        start = null
        state.endDrag(
            PlayerExpandMath.dragProgress(initial, displacement, travel),
            available.y,
            travel,
            startedExpanded = true
        )
        return Velocity(0f, available.y)
    }

    fun cancel() {
        if (start != null) {
            start = null
            state.cancelDrag()
        }
    }
}
