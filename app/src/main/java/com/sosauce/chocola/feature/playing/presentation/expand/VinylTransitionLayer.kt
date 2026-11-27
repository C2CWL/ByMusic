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
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
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
import com.sosauce.chocola.feature.playing.presentation.components.drawTurntableStars
import com.sosauce.chocola.feature.playing.presentation.components.turntableBackdropBrush
import com.sosauce.chocola.feature.playing.presentation.components.turntableHalo
import com.sosauce.chocola.feature.playing.presentation.components.turntableSheen
import com.sosauce.chocola.feature.playing.presentation.components.turntableVinyl
import kotlin.math.roundToInt

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
    progress: () -> Float,
    libraryStyle: Boolean,
    musicState: MusicState,
    miniContainer: Rect,
    miniArt: Rect,
    fullArt: Rect,
    viewport: Rect,
    miniBackground: Color,
    turntable: TurntableSharedState,
    onDragDelta: (Float) -> Unit,
    onDragSettled: (Float) -> Unit,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val container = remember(progress, miniContainer, viewport) {
        derivedStateOf { PlayerExpandMath.containerRect(progress(), miniContainer, viewport) }
    }
    val frame = remember(progress, miniArt, fullArt) {
        derivedStateOf {
            val t = progress()
            val label = VinylExpandMath.labelRect(t, miniArt, fullArt)
            val disc = VinylExpandMath.discRect(t, label)
            VinylArtFrame(label, disc, VinylExpandMath.artBoxForDisc(disc))
        }
    }
    val endLabel = VinylExpandMath.labelRectFor(fullArt)
    val endDisc = VinylExpandMath.discRectFor(fullArt)
    val endArtBox = VinylExpandMath.artBoxForDisc(endDisc)
    val startRadiusPx = with(density) { PlayerExpandMath.coverCornerRadiusDp(0f, libraryStyle).dp.toPx() }
    val recordDegrees = { VinylExpandMath.recordRotation(progress(), turntable.rotation.value) }
    val showChrome by remember(progress) { derivedStateOf { PlayerExpandMath.fullChromeAlpha(progress()) > 0f } }
    val dragState = rememberDraggableState { delta -> onDragDelta(delta) }

    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val metal = MaterialTheme.colorScheme.secondary
    val backdrop = turntableBackdropBrush()
    val starlight = MaterialTheme.colorScheme.onSurface
    val darkSurface = surface.luminance() < 0.5f
    val armDegrees = TurntableMotion.playPauseArmTarget(musicState.isPlaying)

    Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Box(
            modifier = Modifier
                .absoluteOffset {
                    val rect = container.value
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .graphicsLayer {
                    val t = progress()
                    shape = RoundedCornerShape(PlayerExpandMath.containerCornerRadiusDp(t, libraryStyle).dp)
                    shadowElevation = if (t in 0.02f..0.98f) {
                        // Grows with the card's opacity so no shadow shows through the glass.
                        8.dp.toPx() * PlayerExpandMath.transitionBackgroundAlpha(t)
                    } else {
                        0f
                    }
                    clip = true
                }
                .drawWithContent {
                    val t = progress()
                    val backgroundAlpha = PlayerExpandMath.transitionBackgroundAlpha(t)
                    val backdropAlpha = VinylExpandMath.backdropAlpha(t)
                    drawRect(miniBackground, alpha = backgroundAlpha)
                    if (backdropAlpha > 0f) {
                        drawRect(surface, alpha = backdropAlpha * backgroundAlpha)
                        drawRect(backdrop, alpha = backdropAlpha * backgroundAlpha)
                        drawTurntableStars(starlight, alpha = backdropAlpha * backgroundAlpha)
                    }
                    drawContent()
                    drawTransitionGlassRim(
                        alpha = PlayerExpandMath.glassRimAlpha(t),
                        cornerRadiusPx = PlayerExpandMath.containerCornerRadiusDp(t, libraryStyle).dp.toPx(),
                        dark = darkSurface
                    )
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragSettled(velocity) }
                )
                .layout { measurable, _ ->
                    // Only the outer clip changes size. Measure the content at its final
                    // viewport size and cancel the clip's movement in window coordinates.
                    val content = measurable.measure(Constraints.fixed(viewport.width.pixelSize(), viewport.height.pixelSize()))
                    val rect = container.value
                    layout(rect.width.pixelSize(), rect.height.pixelSize()) {
                        content.place(-rect.left.roundToInt(), -rect.top.roundToInt())
                    }
                },
            contentAlignment = AbsoluteAlignment.TopLeft
        ) {
            if (showChrome) {
                VinylTransitionChrome(
                    musicState = musicState,
                    fullArt = fullArt,
                    chromeAlpha = { PlayerExpandMath.fullChromeAlpha(progress()) },
                    topChromeAlpha = { PlayerExpandMath.topChromeAlpha(progress()) },
                    pointerEnabled = { progress() > 0.8f },
                    onCollapse = onCollapse,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onNavigate = onNavigate
                )
            }

            // Glow under the record (artwork-box space).
            Box(
                Modifier
                    .fixedArtLayer(endArtBox, { frame.value.artBox })
                    .graphicsLayer { alpha = VinylExpandMath.discAlpha(progress()) }
                    .turntableHalo(surface, accent)
            )

            // Record body, grown around the cover.
            Box(
                Modifier
                    .fixedArtLayer(endDisc, { frame.value.disc })
                    .graphicsLayer {
                        alpha = VinylExpandMath.discAlpha(progress())
                        rotationZ = recordDegrees()
                    }
                    .clip(CircleShape)
                    .turntableVinyl(surface, accent)
            )

            // Fixed sheen (does not spin), under the cover so its square
            // corners mid-transition stay on top. Glints fade in late,
            // on the same curve the old star points used.
            Box(
                Modifier
                    .fixedArtLayer(endDisc, { frame.value.disc })
                    .graphicsLayer { alpha = VinylExpandMath.discAlpha(progress()) }
                    .turntableSheen(surface, accent, turntable.glint) { VinylExpandMath.starsAlpha(progress()) }
            )

            // The one cover: mini art at 0, record label at 1. Corners close into a circle.
            Box(
                modifier = Modifier
                    .fixedArtLayer(endLabel, { frame.value.label }) {
                        rotationZ = recordDegrees()
                        val label = frame.value.label
                        val radius = VinylExpandMath.labelCornerRadiusPx(progress(), startRadiusPx, label.width)
                        shape = RoundedCornerShape(radius / scaleX)
                        clip = true
                    }
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

            Canvas(
                Modifier.fixedArtLayer(endArtBox, { frame.value.artBox }) {
                    alpha = VinylExpandMath.armAlpha(progress())
                }
            ) { drawTurntableArm(armDegrees, surface, metal) }
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
    fullArt: Rect,
    chromeAlpha: () -> Float,
    topChromeAlpha: () -> Float,
    pointerEnabled: () -> Boolean,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val hiddenButtons = rememberHiddenNowPlayingButtons()
    val isLandscape = rememberIsLandscape()
    val guardedCollapse: () -> Unit = { if (pointerEnabled()) onCollapse() }
    val guardedActions: (PlayerActions) -> Unit = { if (pointerEnabled()) onHandlePlayerActions(it) }
    val guardedNavigate: (Screen) -> Unit = { if (pointerEnabled()) onNavigate(it) }
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
            .graphicsLayer { alpha = chromeAlpha() },
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

private data class VinylArtFrame(val label: Rect, val disc: Rect, val artBox: Rect)

private fun Float.pixelSize(): Int = coerceAtLeast(1f).roundToInt()

/** Measures once at the settled size; motion updates only the layer transform. */
private fun Modifier.fixedArtLayer(
    settled: Rect,
    frame: () -> Rect,
    layer: GraphicsLayerScope.() -> Unit = {}
): Modifier = layout { measurable, constraints ->
    val art = measurable.measure(Constraints.fixed(settled.width.pixelSize(), settled.height.pixelSize()))
    layout(constraints.minWidth, constraints.minHeight) {
        art.placeWithLayer(0, 0) {
            val rect = frame()
            scaleX = rect.width / art.width
            scaleY = rect.height / art.height
            translationX = rect.center.x - art.width / 2f
            translationY = rect.center.y - art.height / 2f
            transformOrigin = TransformOrigin.Center
            layer()
        }
    }
}
