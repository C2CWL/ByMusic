package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.lerp
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.smoothstep

/**
 * Pure geometry for the expand transition when the turntable page is on.
 *
 * First half: the mini-player cover grows as a square into the record's place
 * (the card grows with it, the backdrop is still the light mini-bar color).
 * Second half: the cover shrinks into the record's round center label while its
 * corners close, and the record, glow and tonearm fade in around it. At progress 1 every rect equals the settled page's
 * turntable layout ([discRectFor], [labelRectFor] of the measured artwork box),
 * so handing over to the real page changes no pixel position.
 *
 * Fractions mirror TurntableDisc: record = 0.91 of the artwork box, anchored to
 * its start edge and vertically centered; label = 0.55 of the record, centered.
 */
object VinylExpandMath {

    const val DISC_FRACTION = 0.91f
    const val LABEL_FRACTION = 0.55f

    /** End of the square-cover phase: the cover covers the record's place here. */
    const val SQUARE_PHASE_END = 0.5f

    /** Cover corners start closing at [SQUARE_PHASE_END] and are a circle by this progress. */
    const val ROUND_END = 0.85f

    /** Record body fades in over [SQUARE_PHASE_END, DISC_FADE_END]. */
    const val DISC_FADE_END = 0.8f

    /** Tonearm fades in late. */
    const val ARM_FADE_START = 0.6f

    /** Still star points fade in near the end, matching the settled page. */
    const val STARS_FADE_START = 0.7f

    /** Mini-bar color gives way to the turntable backdrop over [BACKDROP_FADE_START, BACKDROP_FADE_END]. */
    const val BACKDROP_FADE_START = 0.4f
    const val BACKDROP_FADE_END = 0.8f

    /**
     * From this progress the settled page is composed (hidden, under the
     * transition) so its cover image and layout are ready on the landing frame.
     */
    const val PREPARE_PAGE_FROM = 0.85f

    fun shouldPrepareSettledPage(progress: Float): Boolean = progress >= PREPARE_PAGE_FROM

    enum class Style {
        /** Turntable off: unchanged square-cover transition. */
        Classic,

        /** Turntable on: every frame drawn as the turntable page. */
        Vinyl,
    }

    fun styleFor(turntableEnabled: Boolean): Style =
        if (turntableEnabled) Style.Vinyl else Style.Classic

    /** Settled record rect inside the measured artwork box. */
    fun discRectFor(artBox: Rect): Rect {
        val side = minOf(artBox.width, artBox.height) * DISC_FRACTION
        val top = artBox.top + (artBox.height - side) / 2f
        return Rect(artBox.left, top, artBox.left + side, top + side)
    }

    /** Settled round cover rect (record label). */
    fun labelRectFor(artBox: Rect): Rect {
        val disc = discRectFor(artBox)
        return squareAt(disc.center, disc.width * LABEL_FRACTION)
    }

    /**
     * The cover: mini-player art at 0, the record's full square at
     * [SQUARE_PHASE_END], record label at 1. Center travels only in the first
     * half; the second half shrinks it in place on the record's center.
     */
    fun labelRect(progress: Float, miniArt: Rect, fullArt: Rect): Rect {
        val t = progress.coerceIn(0f, 1f)
        val disc = discRectFor(fullArt)
        val end = labelRectFor(fullArt)
        // The mini art may not be perfectly square; keep the cover square.
        val startSide = minOf(miniArt.width, miniArt.height)
        return if (t <= SQUARE_PHASE_END) {
            val u = t / SQUARE_PHASE_END
            squareAt(
                Offset(lerp(miniArt.center.x, disc.center.x, u), lerp(miniArt.center.y, disc.center.y, u)),
                lerp(startSide, disc.width, u)
            )
        } else {
            val v = (t - SQUARE_PHASE_END) / (1f - SQUARE_PHASE_END)
            squareAt(disc.center, lerp(disc.width, end.width, v))
        }
    }

    /**
     * Record rect. It sits at its settled place for the whole transition and
     * only fades in ([discAlpha]) under the shrinking cover, so it never moves.
     */
    fun discRect(@Suppress("UNUSED_PARAMETER") progress: Float, fullArt: Rect): Rect = discRectFor(fullArt)

    /** Artwork box implied by a record rect (inverse of [discRectFor]). Hosts glow and tonearm. */
    fun artBoxForDisc(disc: Rect): Rect {
        val side = disc.width / DISC_FRACTION
        val top = disc.center.y - side / 2f
        return Rect(disc.left, top, disc.left + side, top + side)
    }

    /** Cover corner radius in px: mini corner while square, full circle from [ROUND_END]. */
    fun labelCornerRadiusPx(progress: Float, startRadiusPx: Float, labelSidePx: Float): Float {
        val circle = labelSidePx / 2f
        val start = startRadiusPx.coerceIn(0f, circle)
        return lerp(start, circle, smoothstep(SQUARE_PHASE_END, ROUND_END, progress))
    }

    fun discAlpha(progress: Float): Float = smoothstep(SQUARE_PHASE_END, DISC_FADE_END, progress)

    fun armAlpha(progress: Float): Float = smoothstep(ARM_FADE_START, 1f, progress)

    fun starsAlpha(progress: Float): Float = smoothstep(STARS_FADE_START, 1f, progress)

    fun backdropAlpha(progress: Float): Float = smoothstep(BACKDROP_FADE_START, BACKDROP_FADE_END, progress)

    /** Degrees folded into (-180, 180], so the cover turns the short way. */
    fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Record angle during the transition. The square cover stays upright (0)
     * through the first half; the label turns to [settledDegrees] in the second.
     * The record does not spin here.
     */
    fun recordRotation(progress: Float, settledDegrees: Float): Float {
        val v = ((progress - SQUARE_PHASE_END) / (1f - SQUARE_PHASE_END)).coerceIn(0f, 1f)
        return normalizeDegrees(settledDegrees) * v
    }

    private fun squareAt(center: Offset, side: Float): Rect {
        val half = side / 2f
        return Rect(center.x - half, center.y - half, center.x + half, center.y + half)
    }
}
