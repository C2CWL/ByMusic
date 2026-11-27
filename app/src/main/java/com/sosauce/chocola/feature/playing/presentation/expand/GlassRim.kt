package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Specular rim of the liquid-glass mini bar, redrawn on the rising transition
 * card so the glass edge leaves with the card and fades out
 * ([PlayerExpandMath.glassRimAlpha]) instead of vanishing at once.
 */
internal fun DrawScope.drawTransitionGlassRim(alpha: Float, cornerRadiusPx: Float, dark: Boolean) {
    if (alpha <= 0f) return
    val stroke = 1.dp.toPx()
    val top = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.90f)
    val bottom = if (dark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.35f)
    val radius = (cornerRadiusPx - stroke / 2f).coerceAtLeast(0f)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(top, bottom)),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        alpha = alpha.coerceIn(0f, 1f),
        style = Stroke(width = stroke)
    )
}
