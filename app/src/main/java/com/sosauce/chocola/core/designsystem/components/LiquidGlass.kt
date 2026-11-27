package com.sosauce.chocola.core.designsystem.components

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.core.presentation.preferences.rememberLightweightGlass
import kotlin.math.ceil

/**
 * Backdrop for the liquid-glass mini player.
 *
 * Unlike the reverted #83 (Cloudy `sky`, a full-screen bitmap captured every frame),
 * the screen content is recorded into a [GraphicsLayer] display list while it is
 * drawn anyway, and only the mini player's own small area is blurred on the GPU
 * with a RenderEffect (API 31+). Nothing is copied to a bitmap.
 *
 * One instance per screen: the screen marks its scrolling content with
 * [glassBackdropSource] and provides the instance through [LocalGlassBackdrop];
 * the mini player (a sibling in the bottom bar slot, never inside the recorded
 * content) samples it with [liquidGlass].
 */
@Stable
class GlassBackdrop internal constructor() {
    /** Recorded screen content; null until the source has drawn once. */
    internal var layer by mutableStateOf<GraphicsLayer?>(null)

    /** Window position of the recorded content. */
    internal var sourceOffset by mutableStateOf(Offset.Zero)

    /** Color painted behind the screen content (scaffold container), drawn under the blur. */
    internal var baseColor by mutableStateOf(Color.Unspecified)

    /** Plain field on purpose: written while drawing, must not invalidate anything. */
    internal var recorded: Boolean = false

    /**
     * While the player expand transition runs the backdrop stops re-recording, so
     * the glass keeps its last sample and the list is not recorded twice per frame.
     */
    var frozen by mutableStateOf(false)

    companion object {
        /** RenderEffect blur needs Android 12 (API 31). */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

@Composable
fun rememberGlassBackdrop(): GlassBackdrop = remember { GlassBackdrop() }

/**
 * Records the content this modifier wraps so a [liquidGlass] surface can blur it.
 * A no-op below API 31 or when the glass setting is off.
 */
@Composable
fun Modifier.glassBackdropSource(
    backdrop: GlassBackdrop?,
    baseColor: Color = MaterialTheme.colorScheme.surface
): Modifier {
    val glassEnabled by rememberLightweightGlass()
    if (backdrop == null || !glassEnabled || !GlassBackdrop.isSupported) return this
    val layer = rememberGraphicsLayer()
    SideEffect { backdrop.baseColor = baseColor }
    DisposableEffect(backdrop, layer) {
        backdrop.layer = layer
        backdrop.recorded = false
        onDispose {
            if (backdrop.layer === layer) {
                backdrop.layer = null
                backdrop.recorded = false
            }
        }
    }
    return this
        .onGloballyPositioned { backdrop.sourceOffset = it.positionInWindow() }
        .drawWithContent {
            if (backdrop.frozen && backdrop.recorded) {
                // Keep the last recording for the glass; draw the live content directly.
                drawContent()
            } else {
                layer.record { this@drawWithContent.drawContent() }
                backdrop.recorded = true
                drawLayer(layer)
            }
        }
}

/**
 * Liquid-glass surface for the mini player.
 *
 * - Setting off: opaque surface (unchanged behaviour).
 * - API 31+ with a [GlassBackdrop]: live blur of only this surface's area, a light
 *   tint for text contrast, and a specular rim.
 * - Otherwise (API 28–30, or no backdrop on this screen): the static [libraryGlass]
 *   with the same specular rim.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current
): Modifier {
    val enabled by rememberLightweightGlass()
    val colors = MaterialTheme.colorScheme
    // Off: libraryGlass already falls back to the opaque surface.
    if (!enabled) return libraryGlass(shape)

    val dark = colors.surface.luminance() < 0.5f
    val rim = rememberGlassRim(dark)
    if (backdrop == null || !GlassBackdrop.isSupported) {
        return libraryGlass(shape).glassRim(shape, rim)
    }

    val blurLayer = rememberGraphicsLayer()
    val blurRadius = 22.dp
    var selfOffset by remember { mutableStateOf(Offset.Zero) }
    // Tint keeps text readable over whatever scrolls behind (pure black included).
    val tintTop = colors.surface.copy(alpha = if (dark) 0.58f else 0.50f)
    val tintBottom = colors.surfaceContainerHigh.copy(alpha = if (dark) 0.50f else 0.38f)

    return this
        .onGloballyPositioned { selfOffset = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            val base = backdrop.baseColor.takeOrElse(colors.surface)
            drawRect(base)
            val source = backdrop.layer
            if (source != null && backdrop.recorded) {
                val radiusPx = blurRadius.toPx()
                val pad = ceil(radiusPx * 2f)
                blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
                blurLayer.record(
                    size = IntSize(
                        (size.width + pad * 2).toInt().coerceAtLeast(1),
                        (size.height + pad * 2).toInt().coerceAtLeast(1)
                    )
                ) {
                    translate(
                        left = backdrop.sourceOffset.x - selfOffset.x + pad,
                        top = backdrop.sourceOffset.y - selfOffset.y + pad
                    ) {
                        drawLayer(source)
                    }
                }
                translate(-pad, -pad) { drawLayer(blurLayer) }
            }
            drawRect(Brush.verticalGradient(listOf(tintTop, tintBottom)))
        }
        .glassRim(shape, rim)
}

private fun Color.takeOrElse(other: Color): Color = if (this == Color.Unspecified) other else this

internal data class GlassRim(val highlight: Color, val edge: Color, val glow: Color)

@Composable
private fun rememberGlassRim(dark: Boolean): GlassRim = remember(dark) {
    if (dark) {
        GlassRim(
            highlight = Color.White.copy(alpha = 0.16f),
            edge = Color.White.copy(alpha = 0.10f),
            glow = Color.White.copy(alpha = 0.06f)
        )
    } else {
        GlassRim(
            highlight = Color.White.copy(alpha = 0.90f),
            edge = Color.White.copy(alpha = 0.55f),
            glow = Color.White.copy(alpha = 0.35f)
        )
    }
}

/** Specular top light, thin bright edge and a soft corner glow drawn over the content. */
internal fun Modifier.glassRim(shape: Shape, rim: GlassRim): Modifier = drawWithCache {
    val stroke = 1.dp.toPx()
    val inset = Size(size.width - stroke, size.height - stroke)
    val insetOutline = shape.createOutline(inset, layoutDirection, this)
    val glow = Brush.radialGradient(
        colors = listOf(rim.glow, Color.Transparent),
        center = Offset(size.width * 0.12f, -size.height * 0.35f),
        radius = size.width * 0.45f
    )
    val edgeBrush = Brush.verticalGradient(listOf(rim.highlight, rim.edge, rim.edge.copy(alpha = rim.edge.alpha * 0.6f)))
    onDrawWithContent {
        drawRect(glow)
        drawContent()
        translate(stroke / 2f, stroke / 2f) {
            drawOutlineStroke(insetOutline, edgeBrush, stroke)
        }
    }
}

private fun DrawScope.drawOutlineStroke(outline: Outline, brush: Brush, width: Float) {
    drawOutline(outline = outline, brush = brush, style = Stroke(width = width))
}
