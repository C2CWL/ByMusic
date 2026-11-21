package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.abs

/** Facts from a file that has already passed the decoder check. */
internal data class DecodedDownload(
    val codecMime: String,
    val durationMs: Long,
    val sampleRateHz: Int,
    val bitsPerSample: Int?,
    val averageKbps: Int?
) {
    fun quality(): QualityTier? = when {
        codecMime == "audio/eac3-joc" || codecMime == "audio/ac4" -> QualityTier.SPATIAL
        codecMime == "audio/flac" || codecMime == "audio/alac" || codecMime == "audio/raw" -> {
            if ((bitsPerSample ?: 0) >= 24 && sampleRateHz >= 48_000) QualityTier.HIRES
            else QualityTier.LOSSLESS
        }
        !codecMime.startsWith("audio/") -> null
        else -> when (averageKbps) {
            null -> null
            in Int.MIN_VALUE..0 -> null
            in 1..112 -> QualityTier.LOSSY_LOW
            in 113..159 -> QualityTier.LOSSY_128
            in 160..255 -> QualityTier.LOSSY_192
            else -> QualityTier.LOSSY_320
        }
    }
}

internal sealed interface DownloadAttempt<out T> {
    /** Returned only after decoding succeeds. The attempt owns cleanup of its temporary file. */
    data class Decoded<T>(val audio: DecodedDownload, val value: T) : DownloadAttempt<T>
    data class Failed(val reason: DownloadRejection) : DownloadAttempt<Nothing>
}

internal enum class DownloadRejection {
    UNAVAILABLE, INVALID_AUDIO, DURATION_MISMATCH, QUALITY_MISMATCH
}

internal data class DownloadFailure(val key: String, val reason: DownloadRejection)

internal sealed interface DownloadValidation<out T> {
    data class Accepted<T>(
        val value: T,
        val key: String,
        val measuredQuality: QualityTier,
        val failures: List<DownloadFailure>,
        val belowRequested: Boolean = false
    ) : DownloadValidation<T> {
        val downgraded: Boolean get() = belowRequested || failures.isNotEmpty()
    }

    data class Rejected(val failures: List<DownloadFailure>) : DownloadValidation<Nothing>
}

/** One attempt per plugin key. Accept any requested tier represented by that key. */
internal suspend fun <T> validateDownload(
    request: QualityRequest,
    expectedDurationMs: Long?,
    discard: suspend (T) -> Unit,
    attempt: suspend (key: String) -> DownloadAttempt<T>
): DownloadValidation<T> {
    val failures = mutableListOf<DownloadFailure>()
    for ((key, tiers) in request.chain.groupBy { it.musicFreeKey }) {
        currentCoroutineContext().ensureActive()
        val rejection = when (val result = attempt(key)) {
            is DownloadAttempt.Failed -> result.reason
            is DownloadAttempt.Decoded -> {
                val measured = result.audio.quality()
                val duration = result.audio.durationMs
                val reason = when {
                    measured == null || duration <= 0 -> DownloadRejection.INVALID_AUDIO
                    expectedDurationMs != null && expectedDurationMs > 0 &&
                        abs(duration - expectedDurationMs) > maxOf(3_000L, expectedDurationMs / 50) ->
                        DownloadRejection.DURATION_MISMATCH
                    tiers.none { measured >= it && (measured != QualityTier.SPATIAL || it == QualityTier.SPATIAL) } ->
                        DownloadRejection.QUALITY_MISMATCH
                    else -> return DownloadValidation.Accepted(
                        result.value, key, measured, failures.toList(), measured < request.requested
                    )
                }
                discard(result.value)
                reason
            }
        }
        failures += DownloadFailure(key, rejection)
    }
    return DownloadValidation.Rejected(failures.toList())
}
