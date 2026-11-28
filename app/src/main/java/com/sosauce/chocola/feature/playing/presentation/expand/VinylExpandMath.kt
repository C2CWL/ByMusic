package com.sosauce.chocola.feature.playing.presentation.expand

import com.sosauce.chocola.feature.playing.presentation.VinylLayout
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.smoothstep

object VinylExpandMath {
    const val DISC_FRACTION = .91f
    const val LABEL_FRACTION = .55f

    internal fun discFraction(portrait: Boolean) =
        if (portrait) VinylLayout.PORTRAIT_DISC_FRACTION else DISC_FRACTION

    internal fun coverFraction(portrait: Boolean) =
        if (portrait) VinylLayout.PORTRAIT_COVER_FRACTION else LABEL_FRACTION

    internal fun labelSlot(art: SheetRect, rtl: Boolean, portrait: Boolean = false): SheetRect {
        val disc = minOf(art.width, art.height) * discFraction(portrait)
        val label = disc * coverFraction(portrait)
        val inset = (disc - label) / 2f
        val left = if (rtl) art.right - disc + inset else art.left + inset
        val top = art.centerY - label / 2f
        return SheetRect(left, top, left + label, top + label)
    }

    internal fun artAroundLabel(label: SheetRect, rtl: Boolean, portrait: Boolean = false): SheetRect {
        val discSide = label.width / coverFraction(portrait)
        val side = discSide / discFraction(portrait)
        val left = label.centerX - if (rtl) side - discSide / 2f else discSide / 2f
        val top = label.centerY - side / 2f
        return SheetRect(left, top, left + side, top + side)
    }

    fun discAlpha(g: Float) = smoothstep(.25f, .65f, g)
    fun armAlpha(g: Float) = smoothstep(.6f, .95f, g)
    fun armOffset(g: Float) = -14f * (1f - smoothstep(.6f, 1f, g))
    fun backdropAlpha(g: Float) = smoothstep(.2f, .75f, g)

    fun normalizeDegrees(degrees: Float): Float {
        var angle = degrees % 360f
        if (angle > 180f) angle -= 360f
        if (angle <= -180f) angle += 360f
        return angle
    }
}
