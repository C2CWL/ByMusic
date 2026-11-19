package com.sosauce.chocola.feature.recognition.data

import com.sosauce.chocola.feature.recognition.domain.PluginErrorKind
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent
import com.sosauce.chocola.feature.recognition.domain.RecognitionOutcome
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginRegistry
import com.sosauce.chocola.feature.recognition.domain.RecognitionState
import com.sosauce.chocola.feature.recognition.domain.reduceRecognition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Runs one record → identify round. Kept free of Android UI types so it can be tested on
 * the JVM. The temporary clip is deleted in a `finally` block, so it is gone after a match,
 * any failure, a timeout, or cancellation.
 */
class RecognitionSession(
    private val recorder: AudioRecorder,
    private val audioFiles: RecognitionAudioFiles,
    private val registry: RecognitionPluginRegistry,
    private val history: RecognitionHistoryStore?,
    private val identifyTimeoutMs: Long = DEFAULT_IDENTIFY_TIMEOUT_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _state = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()

    /** Last clip path, exposed for tests only. */
    @Volatile
    var lastClip: File? = null
        private set

    fun purgeLeftovers() = audioFiles.purgeLeftoversOnce()

    fun dispatch(event: RecognitionEvent) {
        _state.update { reduceRecognition(it, event) }
    }

    /** Call while the state is [RecognitionState.Recording]; suspends until the round ends. */
    suspend fun run() {
        val recording = _state.value as? RecognitionState.Recording ?: return
        val clip = audioFiles.create()
        lastClip = clip
        try {
            val audio = try {
                recorder.record(clip, recording.totalMs) { elapsed, level ->
                    dispatch(RecognitionEvent.RecordingProgress(elapsed, level))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dispatch(RecognitionEvent.RecordingFailed)
                return
            }
            dispatch(RecognitionEvent.RecordingFinished)

            val identifying = _state.value as? RecognitionState.Identifying ?: return
            val plugin = registry.find(identifying.pluginId)
            val outcome = if (plugin == null) {
                RecognitionOutcome.PluginError(PluginErrorKind.SCRIPT_ERROR, "plugin removed")
            } else {
                try {
                    withTimeout(identifyTimeoutMs) { plugin.recognize(audio) }
                } catch (e: TimeoutCancellationException) {
                    RecognitionOutcome.NetworkError("timeout")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    RecognitionOutcome.PluginError(PluginErrorKind.SCRIPT_ERROR, e.message)
                }
            }
            audioFiles.delete(clip)
            dispatch(RecognitionEvent.OutcomeReceived(outcome))
            record(identifying.pluginId, plugin?.info?.name ?: identifying.pluginId)
        } finally {
            audioFiles.delete(clip)
        }
    }

    private fun record(pluginId: String, pluginName: String) {
        val store = history ?: return
        val now = clock()
        val entry = when (val s = _state.value) {
            is RecognitionState.Success -> RecognitionHistoryEntry.success(s.song, now)
            is RecognitionState.Failure ->
                RecognitionHistoryEntry.failure(s.reason, pluginId, pluginName, now)

            else -> null
        } ?: return
        runCatching { store.add(entry) }
    }

    companion object {
        const val DEFAULT_IDENTIFY_TIMEOUT_MS = 30_000L
    }
}
