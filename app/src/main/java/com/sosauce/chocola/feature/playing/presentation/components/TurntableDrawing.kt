package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Turntable state that outlives one composed page. The expand overlay keeps a
 * single instance so the transition can draw the record at the exact angle and
 * with the exact star field the settled page shows (and continues from).
 */
@Stable
internal class TurntableSharedState {
    val rotation: Animatable<Float, AnimationVector1D> = Animatable(0f)
    val stars: TurntableStarField = TurntableStarField()
}

internal val LocalTurntableShared = staticCompositionLocalOf<TurntableSharedState?> { null }

/**
 * True while the page is composed but hidden under the expand transition. The
 * record then holds still (no spin, no star motion), like the transition does.
 */
internal val LocalTurntableMotionPaused = compositionLocalOf { false }

/** Record fills this fraction of the artwork box, anchored to its start edge. */
internal const val TURNTABLE_DISC_FRACTION = 0.91f

/** Center label (the round cover) as a fraction of the record. */
internal const val TURNTABLE_LABEL_FRACTION = 0.55f

/** Vinyl body: grooves and sheen. Shared by the page and the expand transition. */
internal fun Modifier.turntableVinyl(surface: Color, accent: Color): Modifier =
    drawWithCache {
        val radius = size.minDimension / 2f
        val base = lerp(surface, accent, 0.24f)
        val vinyl =
            Brush.radialGradient(
                0f to surface,
                0.49f to base,
                0.64f to lerp(surface, accent, 0.48f),
                0.85f to base,
                1f to surface,
                radius = radius,
            )
        val sheen =
            Brush.sweepGradient(
                listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.23f),
                    Color.Transparent,
                    Color.Transparent,
                    accent.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
            )
        onDrawBehind {
            drawCircle(vinyl)
            drawCircle(sheen)
            for (index in 0..64) {
                drawCircle(
                    color = accent.copy(alpha = if (index % 5 == 0) 0.16f else 0.065f),
                    radius = radius * (0.555f + index * 0.0065f),
                    style = Stroke(width = if (index % 5 == 0) 0.65.dp.toPx() else 0.35.dp.toPx()),
                )
            }
            drawCircle(accent.copy(alpha = 0.2f), radius * 0.99f, style = Stroke(0.7.dp.toPx()))
        }
    }

/** Soft glow behind the record, in artwork-box space. */
internal fun DrawScope.drawTurntableGlow(accent: Color) {
    val center = Offset(size.width * 0.46f, size.height * 0.52f)
    drawCircle(
        brush =
            Brush.radialGradient(
                listOf(accent.copy(alpha = 0.19f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.53f,
            ),
        radius = size.minDimension * 0.53f,
        center = center,
    )
}

/** Star points over the record, in record space. [alphaScale] fades them as a group. */
internal fun DrawScope.drawTurntableStars(
    stars: TurntableStarField,
    accent: Color,
    alphaScale: Float = 1f,
) {
    if (alphaScale <= 0f) return
    val radius = size.minDimension / 2f
    for (index in 0 until stars.count) {
        val point =
            center +
                Offset(
                    cos(stars.angle[index]) * radius * stars.radius(index),
                    sin(stars.angle[index]) * radius * stars.radius(index),
                )
        val color = lerp(accent, Color.White, 0.65f).copy(alpha = stars.alpha(index) * alphaScale)
        val dot = stars.size[index] * 0.7.dp.toPx()
        drawCircle(color, dot, point)
        if (index % 19 == 0) {
            drawLine(
                color.copy(alpha = 0.22f * alphaScale),
                point - Offset(dot * 3f, 0f),
                point + Offset(dot * 3f, 0f),
                0.5.dp.toPx(),
            )
            drawLine(
                color.copy(alpha = 0.22f * alphaScale),
                point - Offset(0f, dot * 3f),
                point + Offset(0f, dot * 3f),
                0.5.dp.toPx(),
            )
        }
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
