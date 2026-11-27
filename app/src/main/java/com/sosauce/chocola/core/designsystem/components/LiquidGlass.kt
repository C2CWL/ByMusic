package com.sosauce.chocola.core.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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

/** Which surface the mini player actually draws; logged once per change for diagnostics. */
enum class GlassMode { Off, Static, Live }

private const val GLASS_TAG = "ByMusicGlass"

/**
 * Liquid-glass surface for the mini player.
 *
 * - Setting off ([GlassMode.Off]): opaque surface (unchanged behaviour).
 * - API 31+ with a [GlassBackdrop] ([GlassMode.Live]): live blur + saturation of
 *   only this surface's area, a light 15–25% tint, a light-grey outer edge and a
 *   white inner highlight.
 * - Otherwise ([GlassMode.Static], API 28–30 or no backdrop on this screen): the
 *   static translucent gradient with the same edge and highlight.
 *
 * Everything except the content is recorded into [surfaceCapture] (if given)
 * before it is drawn, so the expand transition can start from a pixel copy of
 * exactly this surface.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    surfaceCapture: GraphicsLayer? = null
): Modifier {
    val enabled by rememberLightweightGlass()
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val mode = when {
        !enabled -> GlassMode.Off
        backdrop != null && GlassBackdrop.isSupported -> GlassMode.Live
        else -> GlassMode.Static
    }
    LaunchedEffect(mode) {
        Log.i(
            GLASS_TAG,
            "mini player glass mode=$mode sdk=${Build.VERSION.SDK_INT} " +
                "setting=$enabled backdrop=${backdrop != null}"
        )
    }

    val ownCapture = rememberGraphicsLayer()
    val capture = surfaceCapture ?: ownCapture
    val blurLayer = rememberGraphicsLayer()
    val blurRadius = 22.dp
    var selfOffset by remember { mutableStateOf(Offset.Zero) }

    val surface = colors.surface
    val tintTop = if (dark) colors.surface.copy(alpha = 0.50f) else colors.surface.copy(alpha = 0.22f)
    val tintBottom = if (dark) {
        colors.surfaceContainerHigh.copy(alpha = 0.42f)
    } else {
        colors.surfaceContainerHigh.copy(alpha = 0.15f)
    }
    val staticTop = colors.surfaceContainerHigh.copy(alpha = 0.72f)
    val staticBottom = colors.surface.copy(alpha = 0.60f)
    val edgeColor = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val highlightTop = if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.95f)
    val highlightBottom = if (dark) Color.White.copy(alpha = 0.03f) else Color.White.copy(alpha = 0.30f)

    return this
        .onGloballyPositioned { selfOffset = it.positionInWindow() }
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            val stroke = 1.dp.toPx()
            val inset = stroke * 1.5f
            val innerOutline = shape.createOutline(
                Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f)),
                layoutDirection,
                this
            )
            val radiusPx = blurRadius.toPx()
            val pad = ceil(radiusPx * 2f)
            val effect: RenderEffect? = if (mode == GlassMode.Live && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                glassRenderEffect(radiusPx)
            } else {
                null
            }
            val tint = Brush.verticalGradient(listOf(tintTop, tintBottom))
            val staticFill = Brush.verticalGradient(listOf(staticTop, staticBottom))
            val highlight = Brush.verticalGradient(listOf(highlightTop, highlightBottom))

            fun DrawScope.drawEdges() {
                drawOutline(outline, color = edgeColor, style = Stroke(width = stroke * 2f))
                translate(inset, inset) {
                    drawOutline(innerOutline, brush = highlight, style = Stroke(width = stroke))
                }
            }

            fun DrawScope.drawSurface() {
                when (mode) {
                    GlassMode.Off -> drawRect(surface)
                    GlassMode.Static -> {
                        drawRect(staticFill)
                        drawEdges()
                    }
                    GlassMode.Live -> {
                        val bd = backdrop
                        val source = bd?.layer
                        drawRect(bd?.baseColor?.takeOrElse(surface) ?: surface)
                        if (bd != null && source != null && bd.recorded && effect != null) {
                            blurLayer.renderEffect = effect
                            blurLayer.record(
                                size = IntSize(
                                    (size.width + pad * 2).toInt().coerceAtLeast(1),
                                    (size.height + pad * 2).toInt().coerceAtLeast(1)
                                )
                            ) {
                                translate(
                                    left = bd.sourceOffset.x - selfOffset.x + pad,
                                    top = bd.sourceOffset.y - selfOffset.y + pad
                                ) {
                                    drawLayer(source)
                                }
                            }
                            translate(-pad, -pad) { drawLayer(blurLayer) }
                        }
                        drawRect(tint)
                        drawEdges()
                    }
                }
            }

            onDrawWithContent {
                capture.record {
                    clipPath(clip) { drawSurface() }
                }
                drawLayer(capture)
                drawContent()
            }
        }
}

/** Blur, then saturation, so the colours behind read through the light tint. */
@RequiresApi(Build.VERSION_CODES.S)
private fun glassRenderEffect(radiusPx: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
    val saturation = ColorMatrix().apply { setSaturation(1.6f) }
    val color = android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(saturation))
    // createChainEffect(outer, inner): inner (blur) runs first.
    return android.graphics.RenderEffect.createChainEffect(color, blur).asComposeRenderEffect()
}

private fun Color.takeOrElse(other: Color): Color = if (this == Color.Unspecified) other else this
