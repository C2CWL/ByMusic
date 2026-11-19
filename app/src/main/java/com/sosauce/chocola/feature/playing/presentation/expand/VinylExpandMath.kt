package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.lerp
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.smoothstep

/**
 * Pure geometry for the expand transition when the turntable page is on.
 *
 * The mini-player cover is carried to the record's round center label while its
 * corners close into a circle; the record grows around it; the tonearm fades in
 * over the second half. At progress 1 every rect equals the settled page's
 * turntable layout ([discRectFor], [labelRectFor] of the measured artwork box),
 * so handing over to the real page changes no pixel position.
 *
 * Fractions mirror TurntableDisc: record = 0.91 of the artwork box, anchored to
 * its start edge and vertically centered; label = 0.55 of the record, centered.
 */
object VinylExpandMath {

    const val DISC_FRACTION = 0.91f
    const val LABEL_FRACTION = 0.55f

    /** Cover corners reach a full circle by this progress. */
    const val ROUND_END = 0.5f

    /** Record body fades from nothing to fully opaque over [0, DISC_FADE_END]. */
    const val DISC_FADE_END = 0.35f

    /** Tonearm fades in over the second half. */
    const val ARM_FADE_START = 0.5f

    /** Still star points fade in near the end, matching the settled page. */
    const val STARS_FADE_START = 0.7f

    /** Mini-bar color gives way to the turntable backdrop over [0, BACKDROP_FADE_END]. */
    const val BACKDROP_FADE_END = 0.5f

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

    /** The cover: mini-player art at 0, record label at 1. */
    fun labelRect(progress: Float, miniArt: Rect, fullArt: Rect): Rect {
        val t = progress.coerceIn(0f, 1f)
        val end = labelRectFor(fullArt)
        // The mini art may not be perfectly square; keep the label square.
        val startSide = minOf(miniArt.width, miniArt.height)
        val side = lerp(startSide, end.width, t)
        val center = Offset(
            lerp(miniArt.center.x, end.center.x, t),
            lerp(miniArt.center.y, end.center.y, t)
        )
        return squareAt(center, side)
    }

    /** Record growth factor relative to its final size ratio; ease-out so it opens early. */
    fun discGrowth(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t)
    }

    /** Record around the label: same size as the label at 0, label / 0.55 at 1. */
    fun discRect(progress: Float, label: Rect): Rect {
        val scale = lerp(1f, 1f / LABEL_FRACTION, discGrowth(progress))
        return squareAt(label.center, label.width * scale)
    }

    /** Artwork box implied by a record rect (inverse of [discRectFor]). Hosts glow and tonearm. */
    fun artBoxForDisc(disc: Rect): Rect {
        val side = disc.width / DISC_FRACTION
        val top = disc.center.y - side / 2f
        return Rect(disc.left, top, disc.left + side, top + side)
    }

    /** Cover corner radius in px: mini corner at 0, full circle from [ROUND_END]. */
    fun labelCornerRadiusPx(progress: Float, startRadiusPx: Float, labelSidePx: Float): Float {
        val circle = labelSidePx / 2f
        val start = startRadiusPx.coerceIn(0f, circle)
        return lerp(start, circle, smoothstep(0f, ROUND_END, progress))
    }

    fun discAlpha(progress: Float): Float = smoothstep(0f, DISC_FADE_END, progress)

    fun armAlpha(progress: Float): Float = smoothstep(ARM_FADE_START, 1f, progress)

    fun starsAlpha(progress: Float): Float = smoothstep(STARS_FADE_START, 1f, progress)

    fun backdropAlpha(progress: Float): Float = smoothstep(0f, BACKDROP_FADE_END, progress)

    /** Degrees folded into (-180, 180], so the cover turns the short way. */
    fun normalizeDegrees(degrees: Float): Float {
        var d = degrees % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }

    /**
     * Record angle during the transition. The mini cover is upright (0); the
     * settled record sits at [settledDegrees]. The record does not spin here.
     */
    fun recordRotation(progress: Float, settledDegrees: Float): Float =
        normalizeDegrees(settledDegrees) * progress.coerceIn(0f, 1f)

    private fun squareAt(center: Offset, side: Float): Rect {
        val half = side / 2f
        return Rect(center.x - half, center.y - half, center.x + half, center.y + half)
    }
}
