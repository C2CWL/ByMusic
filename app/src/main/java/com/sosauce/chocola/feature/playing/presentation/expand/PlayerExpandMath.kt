package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect

/**
 * Pure geometry and state-machine helpers for the mini-player → now-playing
 * expand transition.
 *
 * The 400 ms duration, FastOutSlowIn timing, fade windows and settle
 * thresholds are this app's own scheme. They are not claimed to be Salt
 * Player parameters.
 *
 * No endpoint holds are applied here; any hold belongs only to preview
 * recordings, never to the running app.
 */
object PlayerExpandMath {

    const val DURATION_MS = 400

    const val MINI_CHROME_FADE_END = 0.22f
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

    const val COVER_CORNER_END_DP = 24f
    const val LIBRARY_COVER_CORNER_START_DP = 5f
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

    fun miniChromeAlpha(progress: Float): Float =
        1f - smoothstep(0f, MINI_CHROME_FADE_END, progress)

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

    fun containerCornerRadiusDp(progress: Float, libraryStyle: Boolean): Float =
        if (libraryStyle) 0f else lerp(24f, 0f, progress.coerceIn(0f, 1f))

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
