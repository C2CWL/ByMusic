@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberHiddenNowPlayingButtons
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.feature.playing.presentation.NowPlayingLandscapeBody
import com.sosauce.chocola.feature.playing.presentation.VinylNowPlayingBody
import com.sosauce.chocola.feature.playing.presentation.components.TurntableMotion
import com.sosauce.chocola.feature.playing.presentation.components.TurntableSharedState
import com.sosauce.chocola.feature.playing.presentation.components.drawTurntableArm
import com.sosauce.chocola.feature.playing.presentation.components.turntableBackdropBrush
import com.sosauce.chocola.feature.playing.presentation.components.turntableHalo
import com.sosauce.chocola.feature.playing.presentation.components.turntableSheen
import com.sosauce.chocola.feature.playing.presentation.components.turntableVinyl
import kotlin.math.roundToInt

/** Library-side theme captured outside the turntable colors, for the mini-bar ghost. */
internal class MiniBarTheme(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes
)

/**
 * Expand transition when the turntable page is on. Must be composed inside the
 * same turntable color theme as the settled page (the overlay does this), so
 * colors never change on landing. Every frame is a turntable frame: no square
 * full-size cover, no classic backdrop. The record is still (no spin, no glint
 * motion) while moving; the settled page continues from the same angle/glints.
 * Halo, body and sheen use the exact drawing of the settled page.
 */
@Composable
internal fun VinylTransitionLayer(
    progress: Float,
    libraryStyle: Boolean,
    musicState: MusicState,
    miniContainer: Rect,
    miniArt: Rect,
    fullArt: Rect,
    viewport: Rect,
    miniBarTheme: MiniBarTheme,
    turntable: TurntableSharedState,
    onDragDelta: (Float) -> Unit,
    onDragSettled: (Float) -> Unit,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val container = PlayerExpandMath.containerRect(progress, miniContainer, viewport)
    val containerCorner = PlayerExpandMath.containerCornerRadiusDp(progress, libraryStyle)
    val label = VinylExpandMath.labelRect(progress, miniArt, fullArt)
    val disc = VinylExpandMath.discRect(progress, label)
    val artBox = VinylExpandMath.artBoxForDisc(disc)
    val labelCornerPx = VinylExpandMath.labelCornerRadiusPx(
        progress = progress,
        startRadiusPx = with(density) { PlayerExpandMath.coverCornerRadiusDp(0f, libraryStyle).dp.toPx() },
        labelSidePx = label.width
    )
    val recordDegrees = VinylExpandMath.recordRotation(progress, turntable.rotation.value)
    val discAlpha = VinylExpandMath.discAlpha(progress)
    val armAlpha = VinylExpandMath.armAlpha(progress)
    val starsAlpha = VinylExpandMath.starsAlpha(progress)
    val backdropAlpha = VinylExpandMath.backdropAlpha(progress)
    val fullChrome = PlayerExpandMath.fullChromeAlpha(progress)
    val topChrome = PlayerExpandMath.topChromeAlpha(progress)
    val miniChrome = PlayerExpandMath.miniChromeAlpha(progress)
    val dragState = rememberDraggableState { delta -> onDragDelta(delta) }

    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val metal = MaterialTheme.colorScheme.secondary
    val backdrop = turntableBackdropBrush()
    val miniBackground = miniBarTheme.colorScheme.background
    val armDegrees = TurntableMotion.playPauseArmTarget(musicState.isPlaying)

    Box(Modifier.fillMaxSize()) {
        val cornerDp = containerCorner.dp
        Box(
            modifier = Modifier
                .offset { IntOffset(container.left.roundToInt(), container.top.roundToInt()) }
                .requiredSize(
                    with(density) { container.width.coerceAtLeast(1f).toDp() },
                    with(density) { container.height.coerceAtLeast(1f).toDp() }
                )
                .shadow(
                    elevation = if (progress in 0.02f..0.98f) 8.dp else 0.dp,
                    shape = RoundedCornerShape(cornerDp)
                )
                .clip(RoundedCornerShape(cornerDp))
                .drawBehind {
                    // Mini-bar color first, then the settled page backdrop (opaque
                    // surface + the same gradient) fading over it.
                    drawRect(miniBackground)
                    if (backdropAlpha > 0f) {
                        drawRect(surface, alpha = backdropAlpha)
                        drawRect(backdrop, alpha = backdropAlpha)
                    }
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragSettled(velocity) }
                )
        ) {
            if (fullChrome > 0f) {
                VinylTransitionChrome(
                    musicState = musicState,
                    artBox = artBox,
                    fullArt = fullArt,
                    container = container,
                    chromeAlpha = fullChrome,
                    topChromeAlpha = topChrome,
                    pointerEnabled = progress > 0.8f,
                    onCollapse = onCollapse,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onNavigate = onNavigate
                )
            }

            // Glow under the record (artwork-box space).
            if (discAlpha > 0f) {
                Box(
                    Modifier
                        .placeAt(artBox, container)
                        .graphicsLayer { alpha = discAlpha }
                        .turntableHalo(surface, accent)
                )

                // Record body, grown around the cover.
                Box(
                    Modifier
                        .placeAt(disc, container)
                        .graphicsLayer {
                            alpha = discAlpha
                            rotationZ = recordDegrees
                        }
                        .clip(CircleShape)
                        .turntableVinyl(surface, accent)
                )

                // Fixed sheen (does not spin), under the cover so its square
                // corners mid-transition stay on top. Glints fade in late,
                // on the same curve the old star points used.
                Box(
                    Modifier
                        .placeAt(disc, container)
                        .graphicsLayer { alpha = discAlpha }
                        .turntableSheen(surface, accent, turntable.glint, glintAlpha = starsAlpha)
                )
            }

            // The one cover: mini art at 0, record label at 1. Corners close into a circle.
            Box(
                modifier = Modifier
                    .placeAt(label, container)
                    .graphicsLayer { rotationZ = recordDegrees }
                    .clip(RoundedCornerShape(labelCornerPx))
                    .background(surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.fillMaxSize(0.35f)
                )
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (armAlpha > 0f) {
                Canvas(
                    Modifier
                        .placeAt(artBox, container)
                        .graphicsLayer { alpha = armAlpha }
                ) { drawTurntableArm(armDegrees, surface, metal) }
            }

            if (miniChrome > 0f) {
                // The ghost of the mini bar keeps the library colors it fades out from.
                MaterialTheme(
                    colorScheme = miniBarTheme.colorScheme,
                    typography = miniBarTheme.typography,
                    shapes = miniBarTheme.shapes
                ) {
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
}

/**
 * Page controls during the vinyl transition, laid out with the settled page's
 * own bodies ([VinylNowPlayingBody], landscape [NowPlayingLandscapeBody] in
 * turntable mode) and an empty box in the artwork slot, so the last frame
 * matches the page. The turntable page keeps every control at the bottom, so
 * they fade in at their own final place instead of riding under the record.
 */
@Composable
private fun VinylTransitionChrome(
    musicState: MusicState,
    artBox: Rect,
    fullArt: Rect,
    container: Rect,
    chromeAlpha: Float,
    topChromeAlpha: Float,
    pointerEnabled: Boolean,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val hiddenButtons = rememberHiddenNowPlayingButtons()
    val isLandscape = rememberIsLandscape()
    val guardedCollapse: () -> Unit = { if (pointerEnabled) onCollapse() }
    val guardedActions: (PlayerActions) -> Unit = { if (pointerEnabled) onHandlePlayerActions(it) }
    val guardedNavigate: (Screen) -> Unit = { if (pointerEnabled) onNavigate(it) }
    // Holds the artwork's measured place; the record itself is drawn by the layer.
    val artPlaceholder: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.size(
                with(density) { fullArt.width.toDp() },
                with(density) { fullArt.height.toDp() }
            )
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = chromeAlpha },
        containerColor = Color.Transparent
    ) { paddingValues ->
        if (isLandscape) {
            NowPlayingLandscapeBody(
                paddingValues = paddingValues,
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onHandlePlayerActions = guardedActions,
                onNavigate = guardedNavigate,
                onShrinkToSearchbar = guardedCollapse,
                onShowSpeedCard = {},
                vinylLayout = true,
                artwork = { artPlaceholder(Modifier) }
            )
        } else {
            VinylNowPlayingBody(
                paddingValues = paddingValues,
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onHandlePlayerActions = guardedActions,
                onNavigate = guardedNavigate,
                onShrinkToSearchbar = guardedCollapse,
                onShowSpeedCard = {},
                handleAlpha = topChromeAlpha,
                // The page caps the record size with this modifier; the
                // placeholder is already the measured size.
                artwork = { artPlaceholder(Modifier) }
            )
        }
    }
}

/**
 * Places a child at [rect] (window space) relative to [origin] (window space),
 * at exactly the rect's size. Reports no size to the parent, so a child larger
 * than the parent is never re-centered by its constraints.
 */
private fun Modifier.placeAt(rect: Rect, origin: Rect): Modifier =
    layout { measurable, constraints ->
        val width = rect.width.coerceAtLeast(1f).roundToInt()
        val height = rect.height.coerceAtLeast(1f).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.minWidth, constraints.minHeight) {
            placeable.place((rect.left - origin.left).roundToInt(), (rect.top - origin.top).roundToInt())
        }
    }
