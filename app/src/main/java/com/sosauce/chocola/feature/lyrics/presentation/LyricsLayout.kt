package com.sosauce.chocola.feature.lyrics.presentation

/**
 * Pure layout math for the lyrics page. These values keep the current line in the
 * upper-middle of the lyrics viewport, reserve space for system insets and the
 * control bar, and let the first and last lines reach that same focus point.
 */
object LyricsLayout {
    const val HorizontalPaddingDp = 24
    const val HeaderArtworkDp = 52
    const val CompactHeaderArtworkDp = 40
    const val HeaderVerticalPaddingDp = 8
    const val ControlBarMinHeightDp = 80
    const val ControlBarBottomGapDp = 16
    const val FadeHeightDp = 32
    const val AutoScrollResumeMs = 4_000L

    /** Current line sits in the upper-middle, not against the top edge. */
    const val FocusFraction = 0.38f

    fun headerHeightDp(isLandscape: Boolean): Int {
        val artwork = if (isLandscape) CompactHeaderArtworkDp else HeaderArtworkDp
        return artwork + HeaderVerticalPaddingDp * 2
    }

    fun reservedBottomDp(navigationBarDp: Int): Int =
        ControlBarMinHeightDp + ControlBarBottomGapDp + navigationBarDp.coerceAtLeast(0)

    fun lyricsViewportHeight(
        totalHeight: Int,
        topInset: Int,
        headerHeight: Int,
        reservedBottom: Int
    ): Int = (totalHeight - topInset.coerceAtLeast(0) - headerHeight.coerceAtLeast(0) - reservedBottom.coerceAtLeast(0))
        .coerceAtLeast(0)

    /**
     * [androidx.compose.foundation.lazy.LazyListState.animateScrollToItem] offset.
     * A negative value places the item lower than the top of the viewport.
     */
    fun focusScrollOffsetPx(
        viewportHeightPx: Int,
        lineHeightPx: Int,
        focusFraction: Float = FocusFraction
    ): Int {
        if (viewportHeightPx <= 0) return 0
        val safeLine = lineHeightPx.coerceAtLeast(1)
        val maxOffset = (viewportHeightPx - safeLine).coerceAtLeast(0)
        val target = (viewportHeightPx * focusFraction).toInt().coerceIn(0, maxOffset)
        return -target
    }

    /** Top content padding so the first line can sit on the focus line. */
    fun leadContentPaddingPx(
        viewportHeightPx: Int,
        lineHeightPx: Int,
        focusFraction: Float = FocusFraction
    ): Int {
        if (viewportHeightPx <= 0) return 0
        val safeLine = lineHeightPx.coerceAtLeast(1)
        val maxOffset = (viewportHeightPx - safeLine).coerceAtLeast(0)
        return (viewportHeightPx * focusFraction).toInt().coerceIn(0, maxOffset)
    }

    /** Bottom content padding so the last line can sit on the focus line. */
    fun trailContentPaddingPx(
        viewportHeightPx: Int,
        lineHeightPx: Int,
        focusFraction: Float = FocusFraction
    ): Int {
        if (viewportHeightPx <= 0) return 0
        val safeLine = lineHeightPx.coerceAtLeast(1)
        val focusY = leadContentPaddingPx(viewportHeightPx, safeLine, focusFraction)
        return (viewportHeightPx - focusY - safeLine).coerceAtLeast(0)
    }

    fun currentLineIndex(positionMs: Long, timestampsMs: List<Int>): Int {
        if (timestampsMs.isEmpty()) return -1
        return timestampsMs.indexOfLast { positionMs >= it }
    }
}
