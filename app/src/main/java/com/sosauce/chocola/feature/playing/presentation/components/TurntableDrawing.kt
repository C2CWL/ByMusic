package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Glint phase (0..1) shared by the page and the expand transition. It is read
 * only while drawing, so advancing it redraws the sheen without recomposing.
 */
@Stable
internal class TurntableGlintClock {
    private val phaseState = mutableFloatStateOf(0f)

    val phase: Float get() = phaseState.floatValue

    fun advance(deltaSeconds: Float) {
        if (deltaSeconds <= 0f) return
        phaseState.floatValue = TurntableGlint.advance(phaseState.floatValue, deltaSeconds)
    }
}

/**
 * Turntable state that outlives one composed page. The expand overlay keeps a
 * single instance so the transition can draw the record at the exact angle and
 * with the exact glints the settled page shows (and continues from).
 */
@Stable
internal class TurntableSharedState {
    val rotation: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val glint: TurntableGlintClock = TurntableGlintClock()
}

internal val LocalTurntableShared = staticCompositionLocalOf<TurntableSharedState?> { null }

/**
 * True while the page is composed but hidden under the expand transition. The
 * record then holds still (no spin, no glint motion), like the transition does.
 */
internal val LocalTurntableMotionPaused = compositionLocalOf { false }

/** Record fills this fraction of the artwork box, anchored to its start edge. */
internal const val TURNTABLE_DISC_FRACTION = 0.91f

/** Center label (the round cover) as a fraction of the record. */
internal const val TURNTABLE_LABEL_FRACTION = 0.55f

private fun isOledSurface(surface: Color): Boolean = surface.luminance() < TurntableSheen.OLED_LUMINANCE

/**
 * Vinyl body that spins: a dark record lightly tinted by the cover, fine
 * grooves of uneven brightness and faint dust. No highlight here; light does
 * not turn with the record (see [turntableSheen]). Everything that does not
 * depend on the draw is built once per size and color.
 */
internal fun Modifier.turntableVinyl(surface: Color, accent: Color): Modifier =
    drawWithCache {
        val radius = size.minDimension / 2f
        val oled = isOledSurface(surface)
        val tint = if (oled) 0.7f else 1f
        val body =
            Brush.radialGradient(
                0f to surface,
                0.54f to lerp(surface, accent, 0.10f * tint),
                0.60f to lerp(surface, accent, 0.24f * tint),
                0.80f to lerp(surface, accent, 0.17f * tint),
                0.96f to lerp(surface, accent, 0.27f * tint),
                1f to lerp(surface, accent, 0.12f * tint),
                radius = radius,
            )
        val grooveColor = lerp(accent, Color.White, 0.35f)
        val grooveStroke = Stroke(width = 0.35.dp.toPx())
        val dustColor = lerp(accent, Color.White, 0.6f)
        val dustUnit = 0.7.dp.toPx()
        val rimColor = accent.copy(alpha = 0.16f)
        val rimStroke = Stroke(0.6.dp.toPx())
        onDrawBehind {
            drawCircle(body)
            for (index in 0 until TurntableGrooves.COUNT) {
                drawCircle(
                    color = grooveColor,
                    radius = radius * TurntableGrooves.radius[index],
                    alpha = TurntableGrooves.alpha[index] * tint,
                    style = grooveStroke,
                )
            }
            for (index in 0 until TurntableGrooves.DUST_COUNT) {
                drawCircle(
                    color = dustColor,
                    radius = dustUnit * TurntableGrooves.dustSize[index],
                    center = Offset(
                        center.x + TurntableGrooves.dustX[index] * radius,
                        center.y + TurntableGrooves.dustY[index] * radius,
                    ),
                    alpha = TurntableGrooves.dustAlpha[index] * tint,
                )
            }
            drawCircle(rimColor, radius * 0.995f, style = rimStroke)
        }
    }

private fun sheenBrush(light: Color, peak: Float, halfWidth: Float, center: Offset): Brush {
    val stops =
        TurntableSheen.lobeStops(halfWidth)
            .map { (at, strength) -> at to light.copy(alpha = (peak * strength).coerceIn(0f, 1f)) }
            .toTypedArray()
    return Brush.sweepGradient(*stops, center = center)
}

/**
 * Fixed sheen over the grooves (does not spin): two soft fan highlights, a
 * thin lit edge on the light side, and up to three slow glints read from
 * [glint] at draw time. Place it over the record, same size, without
 * rotation. Brushes and strokes are built once per size and color; drawing
 * a frame allocates nothing.
 */
internal fun Modifier.turntableSheen(
    surface: Color,
    accent: Color,
    glint: TurntableGlintClock?,
    glintAlpha: () -> Float = { 1f },
): Modifier =
    drawWithCache {
        val radius = size.minDimension / 2f
        val k = TurntableSheen.intensity(isOledSurface(surface))
        val light = lerp(accent, Color.White, 0.55f)
        val middle = Offset(size.width / 2f, size.height / 2f)
        val wide = sheenBrush(light, 0.15f * k, TurntableSheen.WIDE_HALF_WIDTH, middle)
        val narrow = sheenBrush(lerp(light, Color.White, 0.5f), 0.20f * k, TurntableSheen.NARROW_HALF_WIDTH, middle)
        val rim = sheenBrush(light, 0.55f * k, 0.12f, middle)
        val wideStroke = Stroke(radius * 0.42f)
        val narrowCore = Stroke(radius * 0.10f)
        val narrowSoft = Stroke(radius * 0.24f)
        val rimStroke = Stroke(0.9.dp.toPx())
        val glintRadius = radius * 0.06f
        val glintBrush =
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.85f),
                0.35f to light.copy(alpha = 0.35f),
                1f to light.copy(alpha = 0f),
                center = Offset.Zero,
                radius = glintRadius,
            )
        onDrawBehind {
            drawCircle(wide, radius * 0.775f, style = wideStroke)
            drawCircle(narrow, radius * 0.80f, alpha = 0.5f, style = narrowSoft)
            drawCircle(narrow, radius * 0.80f, style = narrowCore)
            drawCircle(rim, radius * 0.99f, style = rimStroke)
            val strength = glintAlpha()
            if (glint != null && strength > 0f) {
                val phase = glint.phase
                for (index in 0 until TurntableGlint.COUNT) {
                    val alpha = TurntableGlint.alpha(index, phase) * strength * k
                    if (alpha <= 0.004f) continue
                    val angle = TurntableGlint.angleDegrees(index, phase) * (PI.toFloat() / 180f)
                    val r = radius * TurntableGlint.radiusFraction(index)
                    translate(center.x + cos(angle) * r, center.y + sin(angle) * r) {
                        drawCircle(glintBrush, glintRadius, Offset.Zero, alpha = alpha.coerceIn(0f, 1f))
                    }
                }
            }
        }
    }

/**
 * Soft halo around the record, in artwork-box space (the record is anchored
 * to the start edge at [TURNTABLE_DISC_FRACTION]). It fades out with no hard
 * edge and is a little brighter on the light side. Built once per size/color.
 */
internal fun Modifier.turntableHalo(surface: Color, accent: Color): Modifier =
    drawWithCache {
        val discRadius = size.minDimension * TURNTABLE_DISC_FRACTION / 2f
        val discCenter = Offset(size.width * TURNTABLE_DISC_FRACTION / 2f, size.height / 2f)
        val k = TurntableSheen.haloIntensity(isOledSurface(surface))
        val haloRadius = discRadius * 1.22f
        val halo =
            Brush.radialGradient(
                0f to accent.copy(alpha = 0.22f * k),
                0.80f to accent.copy(alpha = 0.26f * k),
                0.86f to accent.copy(alpha = 0.13f * k),
                0.93f to accent.copy(alpha = 0.045f * k),
                1f to accent.copy(alpha = 0f),
                center = discCenter,
                radius = haloRadius,
            )
        val lightCenter = discCenter - Offset(discRadius * 0.12f, discRadius * 0.12f)
        val lightRadius = discRadius * 1.16f
        val lit = lerp(accent, Color.White, 0.4f)
        val lightSide =
            Brush.radialGradient(
                0f to lit.copy(alpha = 0f),
                0.78f to lit.copy(alpha = 0.07f * k),
                1f to lit.copy(alpha = 0f),
                center = lightCenter,
                radius = lightRadius,
            )
        onDrawBehind {
            drawCircle(halo, haloRadius, discCenter)
            drawCircle(lightSide, lightRadius, lightCenter)
        }
    }

/** Tonearm, in artwork-box space. */
internal fun DrawScope.drawTurntableArm(
    armAngle: Float,
    surface: Color,
    metal: Color,
) {
    val unit = size.minDimension
    val pivot = Offset(size.width * 0.88f, size.height * 0.13f)
    rotate(armAngle, pivot) {
        val arm =
            Path().apply {
                moveTo(pivot.x, pivot.y)
                cubicTo(unit * 0.87f, unit * 0.36f, unit * 0.82f, unit * 0.61f, unit * 0.69f, unit * 0.74f)
            }
        drawPath(arm, surface.copy(alpha = 0.7f), style = Stroke(unit * 0.023f, cap = StrokeCap.Round))
        drawPath(arm, lerp(surface, metal, 0.6f), style = Stroke(unit * 0.013f, cap = StrokeCap.Round))
        drawPath(arm, metal.copy(alpha = 0.55f), style = Stroke(unit * 0.003f, cap = StrokeCap.Round))
        rotate(39f, Offset(unit * 0.69f, unit * 0.74f)) {
            drawRoundRect(
                brush = Brush.linearGradient(listOf(metal, lerp(surface, metal, 0.5f))),
                topLeft = Offset(unit * 0.661f, unit * 0.702f),
                size = Size(unit * 0.058f, unit * 0.12f),
                cornerRadius = CornerRadius(unit * 0.012f),
            )
            drawLine(metal, Offset(unit * 0.68f, unit * 0.822f), Offset(unit * 0.68f, unit * 0.84f), unit * 0.005f)
        }
        drawCircle(surface, unit * 0.066f, pivot)
        drawCircle(
            Brush.radialGradient(listOf(metal, lerp(surface, metal, 0.6f)), center = pivot, radius = unit * 0.05f),
            unit * 0.05f,
            pivot,
        )
        drawCircle(metal.copy(alpha = 0.45f), unit * 0.024f, pivot, style = Stroke(unit * 0.002f))
    }
}
