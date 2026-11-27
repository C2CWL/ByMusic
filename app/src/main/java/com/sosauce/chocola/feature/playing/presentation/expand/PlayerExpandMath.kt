package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect
import kotlin.math.roundToInt

/**
 * Pure geometry and state-machine helpers for the mini-player → now-playing
 * expand transition.
 *
 * The 450 ms duration, emphasized-decelerate timing, fade windows and settle
 * thresholds are this app's own scheme. They are not claimed to be Salt
 * Player parameters.
 *
 * No endpoint holds are applied here; any hold belongs only to preview
 * recordings, never to the running app.
 */
object PlayerExpandMath {

    /** Full-distance settle (0 -> 1 or 1 -> 0). Shorter settles scale down with the distance left. */
    const val DURATION_MS = 450
    const val MIN_DURATION_MS = 180

    fun settleDurationMs(from: Float, to: Float): Int {
        val distance = kotlin.math.abs(to.coerceIn(0f, 1f) - from.coerceIn(0f, 1f))
        return (DURATION_MS * distance).roundToInt().coerceIn(MIN_DURATION_MS, DURATION_MS)
    }

    /**
     * Applied once by the tween; shared by container and cover so the
     * artwork never separates from its card. Emphasized decelerate,
     * cubic-bezier(0.2, 0, 0, 1), used for both directions: opening and
     * closing run the same curve, and the first frames of a collapse no
     * longer jump back half the way (the old 1-(1-t)^3 did in ~60 ms).
     */
    fun settleEasing(fraction: Float): Float = cubicBezier(fraction, 0.2f, 0f, 0f, 1f)

    /** y of the CSS-style cubic-bezier(x1, y1, x2, y2) at x, solved by bisection. */
    fun cubicBezier(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val target = x.coerceIn(0f, 1f)
        if (target <= 0f) return 0f
        if (target >= 1f) return 1f
        fun curve(t: Float, a: Float, b: Float): Float {
            val u = 1f - t
            return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
        }
        var lo = 0f
        var hi = 1f
        var t = target
        repeat(40) {
            t = (lo + hi) / 2f
            if (curve(t, x1, x2) < target) lo = t else hi = t
        }
        return curve(t, y1, y2).coerceIn(0f, 1f)
    }

    const val FULL_CHROME_FADE_START = 0.22f
    const val FULL_CHROME_FADE_END = 0.78f
    const val TOP_CHROME_FADE_START = 0.62f
    const val TOP_CHROME_FADE_END = 0.92f

    /** Settled-page gap under the cover, in the same proportion the preview used. */
    const val SETTLED_COVER_GAP_FRACTION_OF_START = 22f / 12f

    const val OPEN_PROGRESS_THRESHOLD = 0.25f
    const val CLOSE_PROGRESS_THRESHOLD = 0.75f
    const val OPEN_VELOCITY_PX_PER_SEC = -800f
    const val CLOSE_VELOCITY_PX_PER_SEC = 800f

    /**
     * The see-through window over the list stays open a little longer than the
     * old 0.22 so the card reads as glass while it rises; the page background is
     * fully opaque well before the settled page takes over (no flash at 1).
     */
    const val BACKGROUND_FADE_END = 0.4f

    /** The mini bar's own face (title, buttons) on the card: full at 0, gone by here. */
    const val MINI_FACE_FADE_END = 0.2f

    /** The mini bar's glass surface on the card: full at 0, gone by here. */
    const val MINI_SURFACE_FADE_END = 0.35f

    /** Card shadow equals the mini bar's (6dp) at 0 and is gone on the full page. */
    const val MINI_ELEVATION_DP = 6f
    const val ELEVATION_FADE_START = 0.6f

    /**
     * After the card is back on the mini bar (progress 0) the overlay stays for
     * this many frames while the real bar is already visible underneath, so the
     * popup window never disappears before the bar is drawn again.
     */
    const val HANDOFF_LINGER_FRAMES = 2

    /** Container corners match the mini bar, stay round while it rises, square at 1. */
    const val LIBRARY_CONTAINER_CORNER_START_DP = 28f
    const val FLOATING_CONTAINER_CORNER_START_DP = 24f
    const val CONTAINER_CORNER_SQUARE_FROM = 0.35f

    const val COVER_CORNER_END_DP = 24f
    /** Same as the mini bar's 10dp artwork clip, so the cover starts exactly on it. */
    const val LIBRARY_COVER_CORNER_START_DP = 10f
    const val FLOATING_COVER_CORNER_START_DP = 10f

    enum class Phase {
        Collapsed,
        Preparing,
        Dragging,
        Settling,
        Expanded
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 == edge0) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Reveal the real mini-player before the transition window is removed. */
    fun transitionBackgroundAlpha(progress: Float): Float =
        smoothstep(0f, BACKGROUND_FADE_END, progress)

    fun miniFaceAlpha(progress: Float): Float =
        1f - smoothstep(0f, MINI_FACE_FADE_END, progress)

    fun miniSurfaceAlpha(progress: Float): Float =
        1f - smoothstep(0f, MINI_SURFACE_FADE_END, progress)

    fun containerElevationDp(progress: Float): Float =
        MINI_ELEVATION_DP * (1f - smoothstep(ELEVATION_FADE_START, 1f, progress))

    /**
     * The real mini bar is hidden as soon as the card has drawn a frame over it
     * (same pixels, so the swap is invisible) and stays hidden until progress is
     * back to exactly 0. Never two bars, never neither.
     */
    fun barHidden(progress: Float, cardDrawn: Boolean): Boolean =
        isSettledExpanded(progress) || (cardDrawn && progress > 0f)

    /** Finger velocity (px/s, positive = down) as expand progress per second. */
    fun progressVelocity(velocityPxPerSec: Float, travelPx: Float): Float =
        if (travelPx <= 0f) 0f else -velocityPxPerSec / travelPx

    fun fullChromeAlpha(progress: Float): Float =
        smoothstep(FULL_CHROME_FADE_START, FULL_CHROME_FADE_END, progress)

    fun topChromeAlpha(progress: Float): Float =
        smoothstep(TOP_CHROME_FADE_START, TOP_CHROME_FADE_END, progress)

    fun coverCornerRadiusDp(progress: Float, libraryStyle: Boolean): Float {
        val start = if (libraryStyle) {
            LIBRARY_COVER_CORNER_START_DP
        } else {
            FLOATING_COVER_CORNER_START_DP
        }
        return lerp(start, COVER_CORNER_END_DP, progress.coerceIn(0f, 1f))
    }

    fun containerCornerRadiusDp(progress: Float, libraryStyle: Boolean): Float {
        val start = if (libraryStyle) {
            LIBRARY_CONTAINER_CORNER_START_DP
        } else {
            FLOATING_CONTAINER_CORNER_START_DP
        }
        return start * (1f - smoothstep(CONTAINER_CORNER_SQUARE_FROM, 1f, progress))
    }

    fun containerRect(progress: Float, miniContainer: Rect, viewport: Rect): Rect {
        val t = progress.coerceIn(0f, 1f)
        return Rect(
            lerp(miniContainer.left, viewport.left, t),
            lerp(miniContainer.top, viewport.top, t),
            lerp(miniContainer.right, viewport.right, t),
            lerp(miniContainer.bottom, viewport.bottom, t)
        )
    }

    fun coverRect(progress: Float, miniArt: Rect, fullArt: Rect): Rect {
        val t = progress.coerceIn(0f, 1f)
        return Rect(
            lerp(miniArt.left, fullArt.left, t),
            lerp(miniArt.top, fullArt.top, t),
            lerp(miniArt.right, fullArt.right, t),
            lerp(miniArt.bottom, fullArt.bottom, t)
        )
    }

    /**
     * Vertical gap between the moving cover and the title column.
     * [startPx] is a compact gap used early; [endPx] is the settled page gap.
     */
    fun chromeGapPx(progress: Float, startPx: Float, endPx: Float): Float =
        lerp(startPx, endPx, progress.coerceIn(0f, 1f))

    /**
     * Drag-settling target. Velocity is in px/s, with negative meaning upward
     * (opening). Mid-range progress with a weak fling goes to the nearer end.
     */
    fun settleTarget(progress: Float, velocityPxPerSec: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        if (velocityPxPerSec <= OPEN_VELOCITY_PX_PER_SEC) return 1f
        if (velocityPxPerSec >= CLOSE_VELOCITY_PX_PER_SEC) return 0f
        if (p >= OPEN_PROGRESS_THRESHOLD && p < CLOSE_PROGRESS_THRESHOLD) {
            return if (p >= 0.5f) 1f else 0f
        }
        return if (p >= OPEN_PROGRESS_THRESHOLD) 1f else 0f
    }

    /**
     * Swipe-down-to-close on the settled page: a short pull already closes
     * (it starts fully open, so the generic nearest-end rule would bounce
     * back until past halfway). A downward fling closes, an upward one stays.
     */
    const val PAGE_DRAG_CLOSE_BELOW = 0.8f

    fun pageDragSettleTarget(progress: Float, velocityPxPerSec: Float): Float {
        if (velocityPxPerSec >= CLOSE_VELOCITY_PX_PER_SEC) return 0f
        if (velocityPxPerSec <= OPEN_VELOCITY_PX_PER_SEC) return 1f
        return if (progress.coerceIn(0f, 1f) < PAGE_DRAG_CLOSE_BELOW) 0f else 1f
    }

    fun applyDragDelta(progress: Float, deltaProgress: Float): Float =
        (progress + deltaProgress).coerceIn(0f, 1f)

    fun dragDeltaProgress(dragPx: Float, travelPx: Float): Float {
        val travel = travelPx.coerceAtLeast(1f)
        return (-dragPx) / travel
    }

    fun travelPx(viewportHeight: Float, miniContainerHeight: Float): Float =
        (viewportHeight - miniContainerHeight).coerceAtLeast(1f)

    fun boundsReady(miniContainer: Rect?, miniArt: Rect?, fullArt: Rect?): Boolean =
        miniContainer != null &&
            miniArt != null &&
            fullArt != null &&
            miniContainer.width > 0f &&
            miniContainer.height > 0f &&
            miniArt.width > 0f &&
            miniArt.height > 0f &&
            fullArt.width > 0f &&
            fullArt.height > 0f

    fun reportsFullPlayerVisible(progress: Float): Boolean = progress > 0f

    fun isSettledExpanded(progress: Float): Boolean = progress >= 0.999f

    fun isSettledCollapsed(progress: Float): Boolean = progress <= 0.001f

    fun reduceMotionTarget(expand: Boolean): Float = if (expand) 1f else 0f

    /**
     * Drag may interrupt a running settle; the visible progress does not jump.
     * Returns the phase to enter and the progress to keep.
     */
    fun interruptSettle(currentProgress: Float): Pair<Phase, Float> =
        Phase.Dragging to currentProgress.coerceIn(0f, 1f)

    fun phaseAfterProgressChange(
        previous: Phase,
        progress: Float,
        animating: Boolean,
        dragging: Boolean
    ): Phase = when {
        dragging -> Phase.Dragging
        isSettledCollapsed(progress) && !animating -> Phase.Collapsed
        isSettledExpanded(progress) && !animating -> Phase.Expanded
        animating -> Phase.Settling
        previous == Phase.Preparing -> Phase.Preparing
        else -> previous
    }
}
