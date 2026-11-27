package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.presentation.components.MiniPlayerFace
import kotlin.math.roundToInt

/**
 * What the expand card borrows from the library mini bar so that, at progress 0,
 * the card is pixel-for-pixel the bar (the bar itself is hidden then, see
 * [PlayerExpandMath.barHidden]): its glass surface snapshot, its colors and
 * its content ([MiniPlayerFace]).
 */
internal class MiniCardSkin(
    val surface: ImageBitmap?,
    val fallbackSurface: Color,
    val colors: ColorScheme,
    val contentColor: Color
) {
    val shadowAmbient: Color get() = colors.scrim.copy(alpha = 0.12f)
    val shadowSpot: Color get() = colors.scrim.copy(alpha = 0.20f)
}

/**
 * The bar's glass surface stretched over the card, fading out by
 * [PlayerExpandMath.MINI_SURFACE_FADE_END]. Drawn in the card's own (clipped) space.
 */
internal fun DrawScope.drawMiniSurface(skin: MiniCardSkin, progress: Float) {
    val alpha = PlayerExpandMath.miniSurfaceAlpha(progress)
    if (alpha <= 0f) return
    val image = skin.surface
    if (image != null && image.width > 0 && image.height > 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha
        )
    } else {
        drawRect(skin.fallbackSurface, alpha = alpha)
    }
}

/** Card shadow: the bar's own shadow at 0, gone before the page lands. */
internal fun GraphicsLayerScope.miniCardShadow(skin: MiniCardSkin, progress: Float) {
    shadowElevation = PlayerExpandMath.containerElevationDp(progress).dp.toPx()
    ambientShadowColor = skin.shadowAmbient
    spotShadowColor = skin.shadowSpot
}

/**
 * The bar's title, artist and buttons at the bar's exact window place, inside
 * a card whose content is laid out in window coordinates. The artwork slot is
 * left empty: the one moving cover is drawn by the transition layer.
 */
@Composable
internal fun MiniCardFace(
    skin: MiniCardSkin,
    miniContainer: Rect,
    musicState: MusicState,
    progress: () -> Float
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .absoluteOffset { IntOffset(miniContainer.left.roundToInt(), miniContainer.top.roundToInt()) }
            .requiredSize(
                with(density) { miniContainer.width.coerceAtLeast(1f).toDp() },
                with(density) { miniContainer.height.coerceAtLeast(1f).toDp() }
            )
            .graphicsLayer { alpha = PlayerExpandMath.miniFaceAlpha(progress()) },
        contentAlignment = Alignment.Center
    ) {
        MaterialTheme(colorScheme = skin.colors) {
            CompositionLocalProvider(LocalContentColor provides skin.contentColor) {
                MiniPlayerFace(
                    musicState = musicState,
                    onHandlePlayerActions = {},
                    onNavigate = {},
                    artAlpha = 0f
                )
            }
        }
    }
}
