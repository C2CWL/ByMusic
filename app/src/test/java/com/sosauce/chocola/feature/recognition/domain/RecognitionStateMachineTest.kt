package com.sosauce.chocola.feature.recognition.domain

import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.Cancel
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.OutcomeReceived
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.PermissionResult
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.PluginSelected
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.RationaleDismissed
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.RecordingFailed
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.RecordingFinished
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.RecordingProgress
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent.StartRequested
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionStateMachineTest {

    private val a = RecognitionPluginInfo(id = "a", name = "Sample A")
    private val b = RecognitionPluginInfo(id = "b", name = "Sample B")
    private val song = RecognizedSong(
        title = "Sample title",
        artist = "Sample artist",
        sourcePluginId = "a",
        sourcePluginName = "Sample A"
    )

    private fun recording(pluginId: String = "a") =
        reduceRecognition(
            RecognitionState.Idle,
            StartRequested(listOf(a, b), pluginId, permissionGranted = true)
        ) as RecognitionState.Recording

    @Test
    fun startWithoutPluginsGoesToNoPlugin() {
        val s = reduceRecognition(
            RecognitionState.Idle,
            StartRequested(emptyList(), null, permissionGranted = true)
        )
        assertEquals(RecognitionState.NoPlugin, s)
    }

    @Test
    fun startWithoutPermissionShowsRationaleFirst() {
        val s = reduceRecognition(
            RecognitionState.Idle,
            StartRequested(listOf(a), null, permissionGranted = false)
        )
        assertEquals(RecognitionState.PermissionRationale("a"), s)
    }

    @Test
    fun rationaleThenGrantStartsRecording() {
        val s = reduceRecognition(RecognitionState.PermissionRationale("b"), PermissionResult(true))
        assertTrue(s is RecognitionState.Recording)
        assertEquals("b", (s as RecognitionState.Recording).pluginId)
        assertEquals(RecognitionDefaults.DEFAULT_RECORD_MS, s.totalMs)
    }

    @Test
    fun rationaleThenDenyGoesToDenied() {
        val s = reduceRecognition(RecognitionState.PermissionRationale("a"), PermissionResult(false))
        assertEquals(RecognitionState.PermissionDenied, s)
    }

    @Test
    fun dismissingRationaleReturnsToIdle() {
        val s = reduceRecognition(RecognitionState.PermissionRationale("a"), RationaleDismissed)
        assertEquals(RecognitionState.Idle, s)
    }

    @Test
    fun permissionResultOutsideRationaleIsIgnored() {
        assertEquals(
            RecognitionState.Idle,
            reduceRecognition(RecognitionState.Idle, PermissionResult(true))
        )
    }

    @Test
    fun preferredPluginIsUsedWhenPresentOtherwiseFirst() {
        assertEquals("b", recording("b").pluginId)
        assertEquals("a", recording("missing").pluginId)
    }

    @Test
    fun durationIsClampedToEightToTwelveSeconds() {
        val short = reduceRecognition(
            RecognitionState.Idle,
            StartRequested(listOf(a), null, true, durationMs = 1_000)
        ) as RecognitionState.Recording
        val long = reduceRecognition(
            RecognitionState.Idle,
            StartRequested(listOf(a), null, true, durationMs = 60_000)
        ) as RecognitionState.Recording
        assertEquals(8_000L, short.totalMs)
        assertEquals(12_000L, long.totalMs)
    }

    @Test
    fun progressUpdatesCountdownAndKeepsBoundedWaveform() {
        var s: RecognitionState = recording()
        repeat(RecognitionDefaults.WAVEFORM_SAMPLES + 10) { i ->
            s = reduceRecognition(s, RecordingProgress(elapsedMs = (i + 1) * 100L, level = 2f))
        }
        s as RecognitionState.Recording
        assertEquals(RecognitionDefaults.WAVEFORM_SAMPLES, s.levels.size)
        assertTrue(s.levels.all { it in 0f..1f })
        assertEquals(5_000L, s.elapsedMs)
        assertEquals(5, s.remainingSeconds)
    }

    @Test
    fun remainingSecondsRoundsUpAndProgressNeverGoesBackwards() {
        var s: RecognitionState = recording()
        s = reduceRecognition(s, RecordingProgress(2_500, 0.5f))
        assertEquals(8, (s as RecognitionState.Recording).remainingSeconds)
        s = reduceRecognition(s, RecordingProgress(1_000, 0.5f))
        assertEquals(2_500L, (s as RecognitionState.Recording).elapsedMs)
        s = reduceRecognition(s, RecordingProgress(99_000, 0.5f))
        assertEquals(0, (s as RecognitionState.Recording).remainingSeconds)
        assertEquals(1f, s.progress)
    }

    @Test
    fun fullHappyPath() {
        var s: RecognitionState = recording()
        s = reduceRecognition(s, RecordingFinished)
        assertEquals(RecognitionState.Identifying("a"), s)
        s = reduceRecognition(s, OutcomeReceived(RecognitionOutcome.Match(song)))
        assertEquals(RecognitionState.Success(song), s)
    }

    @Test
    fun threeFailureKindsMapToSeparateReasons() {
        val identifying = RecognitionState.Identifying("a")
        assertEquals(
            RecognitionState.Failure("a", FailureReason.NoMatch),
            reduceRecognition(identifying, OutcomeReceived(RecognitionOutcome.NoMatch))
        )
        assertEquals(
            RecognitionState.Failure("a", FailureReason.Network),
            reduceRecognition(identifying, OutcomeReceived(RecognitionOutcome.NetworkError()))
        )
        assertEquals(
            RecognitionState.Failure(
                "a",
                FailureReason.Plugin(PluginErrorKind.QUOTA_EXHAUSTED, "quota")
            ),
            reduceRecognition(
                identifying,
                OutcomeReceived(RecognitionOutcome.PluginError(PluginErrorKind.QUOTA_EXHAUSTED, "quota"))
            )
        )
    }

    @Test
    fun recordingFailureIsItsOwnReason() {
        assertEquals(
            RecognitionState.Failure("a", FailureReason.RecordingFailed),
            reduceRecognition(recording(), RecordingFailed)
        )
    }

    @Test
    fun cancelFromRecordingOrIdentifyingReturnsToIdle() {
        assertEquals(RecognitionState.Idle, reduceRecognition(recording(), Cancel))
        assertEquals(RecognitionState.Idle, reduceRecognition(RecognitionState.Identifying("a"), Cancel))
    }

    @Test
    fun lateEventsAfterCancelAreIgnored() {
        val idle = reduceRecognition(recording(), Cancel)
        assertSame(idle, reduceRecognition(idle, RecordingProgress(500, 0.3f)))
        assertSame(idle, reduceRecognition(idle, RecordingFinished))
        assertSame(idle, reduceRecognition(idle, OutcomeReceived(RecognitionOutcome.Match(song))))
    }

    @Test
    fun startIsIgnoredWhileBusy() {
        val rec = recording()
        assertSame(rec, reduceRecognition(rec, StartRequested(listOf(a), null, true)))
        val identifying = RecognitionState.Identifying("a")
        assertSame(identifying, reduceRecognition(identifying, StartRequested(listOf(a), null, true)))
    }

    @Test
    fun retryFromFailureStartsAgain() {
        val failed = RecognitionState.Failure("a", FailureReason.Network)
        val switched = reduceRecognition(failed, PluginSelected("b"))
        assertEquals("b", switched.activePluginId)
        val again = reduceRecognition(
            switched,
            StartRequested(listOf(a, b), switched.activePluginId, permissionGranted = true)
        )
        assertEquals("b", (again as RecognitionState.Recording).pluginId)
    }

    @Test
    fun switchingPluginWhileRecordingKeepsProgress() {
        val rec = reduceRecognition(recording(), RecordingProgress(3_000, 0.4f))
        val switched = reduceRecognition(rec, PluginSelected("b")) as RecognitionState.Recording
        assertEquals("b", switched.pluginId)
        assertEquals(3_000L, switched.elapsedMs)
    }
}
