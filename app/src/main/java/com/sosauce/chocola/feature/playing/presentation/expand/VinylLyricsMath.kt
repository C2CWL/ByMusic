package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.lerp
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.smoothstep

/**
 * Pure timing and geometry for "turntable page -> lyrics" inside the expand
 * overlay. One progress drives everything: 0 = turntable page, 1 = lyrics
 * page. Going back runs the very same curves in reverse. Nothing overshoots
 * or bounces: every curve is a clamped smoothstep or a linear lerp of an
 * already eased progress.
 */
object VinylLyricsMath {
    const val ENTER_MS = 450
    const val EXIT_MS = 400

    /** Page controls, title, progress, pill bar: fade out in place. */
    const val PAGE_FADE_END = 0.3f

    /** Tonearm lifts to the pause angle and fades out over this span. */
    const val ARM_END = 0.3f

    /** Halo behind the record fades a bit later than the arm. */
    const val HALO_END = 0.45f

    /** Outer vinyl fades away while the cover keeps flying. */
    const val DISC_FADE_START = 0.45f
    const val DISC_FADE_END = 0.9f

    /** Cover turns back upright over this span. */
    const val UPRIGHT_START = 0.2f

    /** Last 18%: circle closes into the header's rounded square. */
    const val SQUARE_START = 0.82f

    /** Lyrics page parts. */
    const val TOOLBAR_START = 0.2f
    const val TOOLBAR_END = 0.6f
    const val HEADER_START = 0.35f
    const val HEADER_END = 0.9f
    const val LYRICS_START = 0.42f
    const val LYRICS_RISE_DP = 16f
    const val BACKGROUND_END = 0.5f

    /** The real header cover takes over from the flying one only on the last frame. */
    const val LANDED = 0.999f

    fun pageAlpha(p: Float): Float = 1f - smoothstep(0f, PAGE_FADE_END, p)

    fun armAlpha(p: Float): Float = 1f - smoothstep(0f, ARM_END, p)

    fun armDegrees(p: Float, startDegrees: Float, restDegrees: Float): Float =
        lerp(startDegrees, restDegrees, smoothstep(0f, ARM_END, p))

    fun haloAlpha(p: Float): Float = 1f - smoothstep(0f, HALO_END, p)

    fun discAlpha(p: Float): Float = 1f - smoothstep(DISC_FADE_START, DISC_FADE_END, p)

    fun toolbarAlpha(p: Float): Float = smoothstep(TOOLBAR_START, TOOLBAR_END, p)

    fun headerAlpha(p: Float): Float = smoothstep(HEADER_START, HEADER_END, p)

    fun lyricsAlpha(p: Float): Float = smoothstep(LYRICS_START, 1f, p)

    /** One-way 16 dp rise that decelerates into place. */
    fun lyricsOffsetDp(p: Float): Float {
        val t = lyricsAlpha(p)
        return LYRICS_RISE_DP * (1f - t)
    }

    fun backgroundAlpha(p: Float): Float = smoothstep(0f, BACKGROUND_END, p)

    fun landed(p: Float): Boolean = p >= LANDED

    /** Flying cover: the record label at 0, the lyrics header cover at 1. */
    fun coverRect(p: Float, startLabel: Rect, endCover: Rect): Rect {
        val t = p.coerceIn(0f, 1f)
        return Rect(
            lerp(startLabel.left, endCover.left, t),
            lerp(startLabel.top, endCover.top, t),
            lerp(startLabel.right, endCover.right, t),
            lerp(startLabel.bottom, endCover.bottom, t)
        )
    }

    /** Record around the flying cover, same proportion as on the page. */
    fun discAround(cover: Rect, labelFraction: Float = VinylExpandMath.LABEL_FRACTION): Rect {
        val side = cover.width / labelFraction
        val half = side / 2f
        val c: Offset = cover.center
        return Rect(c.x - half, c.y - half, c.x + half, c.y + half)
    }

    /** Circle until [SQUARE_START], then closes into [endCornerPx]. */
    fun coverCornerPx(p: Float, sidePx: Float, endCornerPx: Float): Float {
        val circle = sidePx / 2f
        return lerp(circle, endCornerPx.coerceAtMost(circle), smoothstep(SQUARE_START, 1f, p))
    }

    /** Record angle at the start, back to upright (0) by the end, the short way. */
    fun coverDegrees(p: Float, startDegrees: Float): Float =
        VinylExpandMath.normalizeDegrees(startDegrees) * (1f - smoothstep(UPRIGHT_START, 1f, p))
}
