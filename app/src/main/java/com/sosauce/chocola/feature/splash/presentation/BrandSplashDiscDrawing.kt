package com.sosauce.chocola.feature.splash.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.sosauce.chocola.feature.splash.BrandSplashDisc

private val bodyColor = Color(BrandSplashDisc.BODY_COLOR)
private val rimColor = Color(BrandSplashDisc.RIM_COLOR)
private val grooveColor = Color(BrandSplashDisc.GROOVE_COLOR)
private val sheenColor = Color(BrandSplashDisc.SHEEN_COLOR)
private val labelColor = Color(BrandSplashDisc.LABEL_COLOR)
private val labelRimColor = Color(BrandSplashDisc.LABEL_RIM_COLOR)
private val holeColor = Color(BrandSplashDisc.HOLE_COLOR)

/**
 * Draws the same disc as res/drawable/splash_system_mark.xml, scaled so that the
 * disc radius is [radius] px. Plain circles and arcs only (no bitmap, no vector
 * cache), so drawing it at a few thousand px while it grows stays cheap.
 */
internal fun DrawScope.drawBrandDisc(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    val unit = radius / BrandSplashDisc.RADIUS
    val a = alpha.coerceIn(0f, 1f)

    drawCircle(color = bodyColor, radius = radius, center = center, alpha = a)
    drawCircle(
        color = rimColor, radius = radius, center = center, alpha = a,
        style = Stroke(width = BrandSplashDisc.RIM_WIDTH * unit)
    )

    val grooveStroke = Stroke(width = BrandSplashDisc.GROOVE_WIDTH * unit)
    BrandSplashDisc.GROOVES.forEachIndexed { index, fraction ->
        drawCircle(
            color = grooveColor,
            radius = radius * fraction,
            center = center,
            alpha = a * BrandSplashDisc.grooveAlpha(index),
            style = grooveStroke
        )
    }

    val sheenStroke = Stroke(width = BrandSplashDisc.SHEEN_WIDTH * unit, cap = StrokeCap.Round)
    BrandSplashDisc.SHEEN_HALF_SPANS.forEachIndexed { layer, halfSpan ->
        val layerAlpha = a * BrandSplashDisc.SHEEN_ALPHAS[layer]
        for (index in BrandSplashDisc.SHEEN_GROOVES) {
            val r = radius * BrandSplashDisc.GROOVES[index]
            drawArc(
                color = sheenColor,
                startAngle = BrandSplashDisc.SHEEN_CENTER_DEG - halfSpan,
                sweepAngle = halfSpan * 2f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
                alpha = layerAlpha,
                style = sheenStroke
            )
        }
    }

    val labelRadius = radius * BrandSplashDisc.LABEL_FRACTION
    drawCircle(color = labelColor, radius = labelRadius, center = center, alpha = a)
    drawCircle(
        color = labelRimColor, radius = labelRadius, center = center, alpha = a,
        style = Stroke(width = BrandSplashDisc.LABEL_RIM_WIDTH * unit)
    )
    drawCircle(
        color = holeColor,
        radius = radius * BrandSplashDisc.HOLE_FRACTION,
        center = center,
        alpha = a
    )
}
