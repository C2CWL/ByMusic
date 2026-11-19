package com.sosauce.chocola.core.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsFrameEstimateTest {

    @Test
    fun advancesWithElapsedFrameTime() {
        val pos = LyricsPositionClock.estimateMs(
            baseMs = 10_000L, sampleMs = 10_000L, elapsedNanos = 16_000_000L, speed = 1f, isPlaying = true
        )
        assertEquals(10_016L, pos)
    }

    @Test
    fun respectsPlaybackSpeed() {
        val pos = LyricsPositionClock.estimateMs(
            baseMs = 0L, sampleMs = 0L, elapsedNanos = 100_000_000L, speed = 1.5f, isPlaying = true
        )
        assertEquals(150L, pos)
    }

    @Test
    fun pausedStaysOnBase() {
        val pos = LyricsPositionClock.estimateMs(
            baseMs = 5_000L, sampleMs = 5_000L, elapsedNanos = 900_000_000L, speed = 1f, isPlaying = false
        )
        assertEquals(5_000L, pos)
    }

    @Test
    fun neverRunsFarAheadOfLastSample() {
        val pos = LyricsPositionClock.estimateMs(
            baseMs = 1_000L, sampleMs = 1_000L, elapsedNanos = 5_000_000_000L, speed = 1f, isPlaying = true
        )
        assertEquals(1_000L + LyricsPositionClock.MAX_LEAD_MS, pos)
    }

    @Test
    fun badSpeedFallsBackToNormal() {
        val pos = LyricsPositionClock.estimateMs(
            baseMs = 0L, sampleMs = 0L, elapsedNanos = 10_000_000L, speed = Float.NaN, isPlaying = true
        )
        assertEquals(10L, pos)
    }

    @Test
    fun smallBackwardSampleKeepsShownValue() {
        assertEquals(10_080L, LyricsPositionClock.reconcileBaseMs(10_080L, 10_020L, isPlaying = true))
    }

    @Test
    fun largeBackwardSampleIsASeek() {
        assertEquals(2_000L, LyricsPositionClock.reconcileBaseMs(10_000L, 2_000L, isPlaying = true))
    }

    @Test
    fun forwardSampleIsTaken() {
        assertEquals(30_000L, LyricsPositionClock.reconcileBaseMs(10_000L, 30_000L, isPlaying = true))
    }

    @Test
    fun pausedTakesSampleExactly() {
        assertEquals(10_020L, LyricsPositionClock.reconcileBaseMs(10_080L, 10_020L, isPlaying = false))
    }
}
