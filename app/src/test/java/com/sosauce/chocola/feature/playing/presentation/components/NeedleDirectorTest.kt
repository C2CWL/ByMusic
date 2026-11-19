package com.sosauce.chocola.feature.playing.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeedleDirectorTest {

    private val rest = TurntableMotion.ARM_REST_DEGREES
    private val onDisc = TurntableMotion.ARM_ON_DISC_DEGREES

    @Test
    fun designTimings() {
        assertEquals(250, NeedleMove.Lift.durationMs)
        assertEquals(450, NeedleMove.Drop.durationMs)
        assertEquals(650, NeedleMove.PlayPause.durationMs)
        assertEquals(350L, TurntableNeedle.SKIP_SETTLE_MS)
        assertEquals(300L, TurntableNeedle.BUFFER_GRACE_MS)
    }

    @Test
    fun singleSkipWhilePlayingLiftsAtOnceThenDropsAfterTheSettleTime() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        assertEquals(onDisc, needle.currentTarget, 0f)

        needle.onTrackChanged(nowMs = 0)
        assertEquals(NeedleMove.Lift, needle.step(0))
        assertEquals(rest, needle.currentTarget, 0f)
        assertEquals(350L, needle.nextDeadlineMs())

        assertNull(needle.step(349))
        assertEquals(rest, needle.currentTarget, 0f)

        assertEquals(NeedleMove.Drop, needle.step(350))
        assertEquals(onDisc, needle.currentTarget, 0f)
        assertNull(needle.nextDeadlineMs())
        assertNull(needle.step(2_000))
    }

    @Test
    fun rapidSkipsLiftOnceAndOnlyRestartTheSettleTimer() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        val moves = mutableListOf<NeedleMove>()
        for (t in listOf(0L, 120L, 240L, 360L)) {
            needle.onTrackChanged(t)
            needle.step(t)?.let(moves::add)
            assertEquals(rest, needle.currentTarget, 0f)
        }
        assertEquals(listOf(NeedleMove.Lift), moves)
        assertEquals(710L, needle.nextDeadlineMs())
        assertNull(needle.step(709))
        assertEquals(NeedleMove.Drop, needle.step(710))
        assertEquals(onDisc, needle.currentTarget, 0f)
    }

    @Test
    fun skipWhilePausedLeavesTheArmOutUntilPlayIsPressed() {
        val needle = NeedleDirector(isPlaying = false, playWhenReady = false)
        assertEquals(rest, needle.currentTarget, 0f)

        needle.onTrackChanged(0)
        assertNull(needle.step(0))
        needle.onTrackChanged(100)
        assertNull(needle.step(100))
        assertNull(needle.step(1_000))
        assertEquals(rest, needle.currentTarget, 0f)

        needle.onPlayback(isPlaying = false, playWhenReady = true, nowMs = 2_000)
        assertNull(needle.step(2_000))
        needle.onPlayback(isPlaying = true, playWhenReady = true, nowMs = 2_050)
        assertEquals(NeedleMove.PlayPause, needle.step(2_050))
        assertEquals(onDisc, needle.currentTarget, 0f)
    }

    @Test
    fun bufferingBlipOfATrackChangeDoesNotMoveTheArm() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        needle.onTrackChanged(0)
        assertEquals(NeedleMove.Lift, needle.step(0))
        // Player: playing -> buffering -> playing while the user still wants playback.
        needle.onPlayback(isPlaying = false, playWhenReady = true, nowMs = 10)
        assertNull(needle.step(10))
        needle.onPlayback(isPlaying = true, playWhenReady = true, nowMs = 180)
        assertNull(needle.step(180))
        assertEquals(NeedleMove.Drop, needle.step(350))
    }

    @Test
    fun bufferingBlipWithoutASkipKeepsTheArmOnTheDisc() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        needle.onPlayback(isPlaying = false, playWhenReady = true, nowMs = 0)
        assertNull(needle.step(0))
        assertNull(needle.step(299))
        needle.onPlayback(isPlaying = true, playWhenReady = true, nowMs = 299)
        assertNull(needle.step(299))
        assertNull(needle.nextDeadlineMs())
        assertEquals(onDisc, needle.currentTarget, 0f)
    }

    @Test
    fun aStallLongerThanTheGraceLiftsTheArmLikeAPause() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        needle.onPlayback(isPlaying = false, playWhenReady = true, nowMs = 1_000)
        assertEquals(1_300L, needle.nextDeadlineMs())
        assertNull(needle.step(1_299))
        assertEquals(NeedleMove.PlayPause, needle.step(1_300))
        assertEquals(rest, needle.currentTarget, 0f)
    }

    @Test
    fun userPauseLiftsTheArmImmediately() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        needle.onPlayback(isPlaying = false, playWhenReady = false, nowMs = 0)
        assertEquals(NeedleMove.PlayPause, needle.step(0))
        assertEquals(rest, needle.currentTarget, 0f)
        needle.onPlayback(isPlaying = true, playWhenReady = true, nowMs = 500)
        assertEquals(NeedleMove.PlayPause, needle.step(500))
        assertEquals(onDisc, needle.currentTarget, 0f)
    }

    @Test
    fun pauseDuringTheSettleKeepsTheArmOutAndPlayLandsItNormally() {
        val needle = NeedleDirector(isPlaying = true, playWhenReady = true)
        needle.onTrackChanged(0)
        assertEquals(NeedleMove.Lift, needle.step(0))
        needle.onPlayback(isPlaying = false, playWhenReady = false, nowMs = 100)
        assertNull(needle.step(100))
        assertNull(needle.step(1_000))
        assertEquals(rest, needle.currentTarget, 0f)
        needle.onPlayback(isPlaying = true, playWhenReady = true, nowMs = 2_000)
        assertEquals(NeedleMove.PlayPause, needle.step(2_000))
    }
}
