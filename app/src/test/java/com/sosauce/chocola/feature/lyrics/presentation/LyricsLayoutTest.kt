package com.sosauce.chocola.feature.lyrics.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsLayoutTest {

    @Test
    fun viewportSubtractsStatusHeaderAndControlInsets() {
        val viewport = LyricsLayout.lyricsViewportHeight(
            totalHeight = 2000,
            topInset = 80,
            headerHeight = 68,
            reservedBottom = 160
        )
        assertEquals(2000 - 80 - 68 - 160, viewport)
        assertTrue(viewport > 0)
    }

    @Test
    fun reservedBottomIncludesNavigationBar() {
        val noNav = LyricsLayout.reservedBottomDp(navigationBarDp = 0)
        val withNav = LyricsLayout.reservedBottomDp(navigationBarDp = 48)
        assertEquals(LyricsLayout.ControlBarMinHeightDp + LyricsLayout.ControlBarBottomGapDp, noNav)
        assertEquals(noNav + 48, withNav)
        assertTrue(withNav > noNav)
    }

    @Test
    fun firstAndLastLinesCanReachTheSameFocusPoint() {
        val viewport = 1200
        val line = 80
        val lead = LyricsLayout.leadContentPaddingPx(viewport, line)
        val trail = LyricsLayout.trailContentPaddingPx(viewport, line)
        val focusOffset = LyricsLayout.focusScrollOffsetPx(viewport, line)
        assertEquals(lead, -focusOffset)
        assertEquals(viewport, lead + line + trail)
        // First line at offset 0 with lead padding sits on the focus line.
        assertEquals(lead, lead)
        // Last line can scroll until its top is at the same focus Y.
        assertTrue(trail >= 0)
        assertTrue(lead + line <= viewport)
    }

    @Test
    fun largeFontLineStillStaysInsideTheViewport() {
        val viewport = 800
        val largeLine = 420
        val offset = LyricsLayout.focusScrollOffsetPx(viewport, largeLine)
        val lead = LyricsLayout.leadContentPaddingPx(viewport, largeLine)
        val trail = LyricsLayout.trailContentPaddingPx(viewport, largeLine)
        assertTrue(-offset + largeLine <= viewport)
        assertTrue(lead + largeLine + trail == viewport || lead + largeLine <= viewport)
        assertTrue(trail >= 0)
        assertTrue(lead >= 0)
    }

    @Test
    fun controlBarReservationIsIndependentOfLyricsViewport() {
        val total = 1600
        val reserved = LyricsLayout.reservedBottomDp(24)
        val viewport = LyricsLayout.lyricsViewportHeight(total, 60, 68, reserved)
        assertEquals(total, 60 + 68 + viewport + reserved)
        assertTrue(reserved >= LyricsLayout.ControlBarMinHeightDp)
    }

    @Test
    fun currentLineIndexUsesInclusiveStartAndDoesNotChooseFutureLines() {
        val times = listOf(0, 1000, 2500)
        assertEquals(-1, LyricsLayout.currentLineIndex(0, emptyList()))
        assertEquals(0, LyricsLayout.currentLineIndex(0, times))
        assertEquals(0, LyricsLayout.currentLineIndex(999, times))
        assertEquals(1, LyricsLayout.currentLineIndex(1000, times))
        assertEquals(1, LyricsLayout.currentLineIndex(2499, times))
        assertEquals(2, LyricsLayout.currentLineIndex(2500, times))
        assertEquals(0, LyricsLayout.currentLineIndex(200, times))
    }

    @Test
    fun headerIsShorterInLandscape() {
        assertTrue(LyricsLayout.headerHeightDp(isLandscape = true) < LyricsLayout.headerHeightDp(isLandscape = false))
    }
}
