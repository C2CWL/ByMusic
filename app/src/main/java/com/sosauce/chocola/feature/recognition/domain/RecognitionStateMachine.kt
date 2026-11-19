package com.sosauce.chocola.feature.recognition.domain

object RecognitionDefaults {
    const val MIN_RECORD_MS = 8_000L
    const val MAX_RECORD_MS = 12_000L
    const val DEFAULT_RECORD_MS = 10_000L

    /** How many recent loudness samples the waveform keeps. */
    const val WAVEFORM_SAMPLES = 40

    fun clampDuration(ms: Long): Long = ms.coerceIn(MIN_RECORD_MS, MAX_RECORD_MS)
}

sealed interface RecognitionState {
    /** Nothing running; the screen shows a start button. */
    data object Idle : RecognitionState

    /** User tapped start but no imported plugin declares recognition. */
    data object NoPlugin : RecognitionState

    /** Explain the microphone use before the system permission prompt (mockup 03). */
    data class PermissionRationale(val pluginId: String) : RecognitionState

    /** User refused the microphone permission. */
    data object PermissionDenied : RecognitionState

    data class Recording(
        val pluginId: String,
        val elapsedMs: Long,
        val totalMs: Long,
        val levels: List<Float>
    ) : RecognitionState {
        val remainingSeconds: Int
            get() = ((totalMs - elapsedMs).coerceAtLeast(0L) + 999L).div(1000L).toInt()

        val progress: Float
            get() = if (totalMs <= 0L) 1f else (elapsedMs.toFloat() / totalMs).coerceIn(0f, 1f)
    }

    data class Identifying(val pluginId: String) : RecognitionState

    data class Success(val song: RecognizedSong) : RecognitionState

    data class Failure(val pluginId: String, val reason: FailureReason) : RecognitionState
}

sealed interface FailureReason {
    data object NoMatch : FailureReason
    data object Network : FailureReason
    data class Plugin(val kind: PluginErrorKind, val detail: String?) : FailureReason

    /** The microphone could not be opened or stopped delivering audio. */
    data object RecordingFailed : FailureReason
}

sealed interface RecognitionEvent {
    data class StartRequested(
        val plugins: List<RecognitionPluginInfo>,
        val preferredPluginId: String?,
        val permissionGranted: Boolean,
        val durationMs: Long = RecognitionDefaults.DEFAULT_RECORD_MS
    ) : RecognitionEvent

    data class PermissionResult(
        val granted: Boolean,
        val durationMs: Long = RecognitionDefaults.DEFAULT_RECORD_MS
    ) : RecognitionEvent

    data object RationaleDismissed : RecognitionEvent

    data class RecordingProgress(val elapsedMs: Long, val level: Float) : RecognitionEvent

    data object RecordingFinished : RecognitionEvent

    data object RecordingFailed : RecognitionEvent

    data class OutcomeReceived(val outcome: RecognitionOutcome) : RecognitionEvent

    data class PluginSelected(val pluginId: String) : RecognitionEvent

    data object Cancel : RecognitionEvent

    data object Reset : RecognitionEvent
}

/**
 * Pure transition function. Events that do not fit the current state are ignored, which
 * keeps late callbacks (a progress tick after cancel, a plugin answer after the user left)
 * from resurrecting old screens.
 */
fun reduceRecognition(state: RecognitionState, event: RecognitionEvent): RecognitionState =
    when (event) {
        is RecognitionEvent.StartRequested -> {
            if (state is RecognitionState.Recording || state is RecognitionState.Identifying) {
                state
            } else {
                val pluginId = pickPlugin(event.plugins, event.preferredPluginId)
                when {
                    pluginId == null -> RecognitionState.NoPlugin
                    !event.permissionGranted -> RecognitionState.PermissionRationale(pluginId)
                    else -> startRecording(pluginId, event.durationMs)
                }
            }
        }

        is RecognitionEvent.PermissionResult -> when (state) {
            is RecognitionState.PermissionRationale ->
                if (event.granted) startRecording(state.pluginId, event.durationMs)
                else RecognitionState.PermissionDenied

            else -> state
        }

        RecognitionEvent.RationaleDismissed ->
            if (state is RecognitionState.PermissionRationale) RecognitionState.Idle else state

        is RecognitionEvent.RecordingProgress -> when (state) {
            is RecognitionState.Recording -> state.copy(
                elapsedMs = event.elapsedMs.coerceIn(state.elapsedMs, state.totalMs),
                levels = (state.levels + event.level.coerceIn(0f, 1f))
                    .takeLast(RecognitionDefaults.WAVEFORM_SAMPLES)
            )

            else -> state
        }

        RecognitionEvent.RecordingFinished -> when (state) {
            is RecognitionState.Recording -> RecognitionState.Identifying(state.pluginId)
            else -> state
        }

        RecognitionEvent.RecordingFailed -> when (state) {
            is RecognitionState.Recording ->
                RecognitionState.Failure(state.pluginId, FailureReason.RecordingFailed)

            else -> state
        }

        is RecognitionEvent.OutcomeReceived -> when (state) {
            is RecognitionState.Identifying -> when (val outcome = event.outcome) {
                is RecognitionOutcome.Match -> RecognitionState.Success(outcome.song)
                RecognitionOutcome.NoMatch ->
                    RecognitionState.Failure(state.pluginId, FailureReason.NoMatch)

                is RecognitionOutcome.NetworkError ->
                    RecognitionState.Failure(state.pluginId, FailureReason.Network)

                is RecognitionOutcome.PluginError -> RecognitionState.Failure(
                    state.pluginId,
                    FailureReason.Plugin(outcome.kind, outcome.detail)
                )
            }

            else -> state
        }

        is RecognitionEvent.PluginSelected -> when (state) {
            is RecognitionState.Recording -> state.copy(pluginId = event.pluginId)
            is RecognitionState.Failure -> state.copy(pluginId = event.pluginId)
            is RecognitionState.PermissionRationale -> state.copy(pluginId = event.pluginId)
            else -> state
        }

        RecognitionEvent.Cancel -> when (state) {
            is RecognitionState.Recording,
            is RecognitionState.Identifying,
            is RecognitionState.PermissionRationale -> RecognitionState.Idle

            else -> state
        }

        RecognitionEvent.Reset -> RecognitionState.Idle
    }

private fun startRecording(pluginId: String, durationMs: Long) = RecognitionState.Recording(
    pluginId = pluginId,
    elapsedMs = 0L,
    totalMs = RecognitionDefaults.clampDuration(durationMs),
    levels = emptyList()
)

private fun pickPlugin(plugins: List<RecognitionPluginInfo>, preferredId: String?): String? =
    plugins.firstOrNull { it.id == preferredId }?.id ?: plugins.firstOrNull()?.id

/** The plugin id the current state is working with, if any. */
val RecognitionState.activePluginId: String?
    get() = when (this) {
        is RecognitionState.PermissionRationale -> pluginId
        is RecognitionState.Recording -> pluginId
        is RecognitionState.Identifying -> pluginId
        is RecognitionState.Failure -> pluginId
        is RecognitionState.Success -> song.sourcePluginId
        else -> null
    }
