package com.sosauce.chocola.core.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPositionClockTest {

    private val times = listOf(0, 1000, 2500, 4000)

    @Test
    fun playingPollIsFastEnoughToKeepLagWellUnderASecond() {
        assertTrue(LyricsPositionClock.PLAYING_POLL_MS in 16L..200L)
        assertTrue(LyricsPositionClock.PLAYING_POLL_MS < 1_000L)
    }

    @Test
    fun pollingStopsWhenPaused() {
        assertTrue(LyricsPositionClock.shouldPollWhilePlaying(true))
        assertFalse(LyricsPositionClock.shouldPollWhilePlaying(false))
    }

    @Test
    fun lineIndexFollowsTheMediaClockWithoutShiftingTimestamps() {
        assertEquals(-1, LyricsPositionClock.currentLineIndex(0, emptyList()))
        assertEquals(0, LyricsPositionClock.currentLineIndex(0, times))
        assertEquals(0, LyricsPositionClock.currentLineIndex(999, times))
        assertEquals(1, LyricsPositionClock.currentLineIndex(1000, times))
        assertEquals(1, LyricsPositionClock.currentLineIndex(1049, times))
        assertEquals(1, LyricsPositionClock.currentLineIndex(2499, times))
        assertEquals(2, LyricsPositionClock.currentLineIndex(2500, times))
    }

    @Test
    fun seekJumpsToTheMatchingLineImmediately() {
        assertEquals(3, LyricsPositionClock.currentLineIndex(4000, times))
        assertEquals(0, LyricsPositionClock.currentLineIndex(200, times))
        assertEquals(2, LyricsPositionClock.currentLineIndex(3999, times))
    }

    @Test
    fun pausedPositionKeepsTheSameLineUntilTheClockMoves() {
        val pausedAt = 1800L
        val index = LyricsPositionClock.currentLineIndex(pausedAt, times)
        assertEquals(1, index)
        assertEquals(index, LyricsPositionClock.currentLineIndex(pausedAt, times))
    }
}
