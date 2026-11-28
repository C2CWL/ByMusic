package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Lightweight facts from the saved bytes. No MediaCodec decode, no duration gate. */
internal data class DownloadAudio(
    val container: AudioContainer,
    val bytes: Long,
    val flac: FlacInfo?
) {
    fun quality(expectedDurationMs: Long?): QualityTier = when (container) {
        AudioContainer.FLAC ->
            if ((flac?.bitsPerSample ?: 0) >= 24 && (flac?.sampleRate ?: 0) >= 48_000) {
                QualityTier.HIRES
            } else {
                QualityTier.LOSSLESS
            }
        AudioContainer.OGG_OPUS, AudioContainer.AAC_ADTS, AudioContainer.MP4 -> QualityTier.LOSSY_192
        AudioContainer.OGG_VORBIS -> QualityTier.LOSSY_128
        else -> {
            val kbps = if (expectedDurationMs != null && expectedDurationMs > 0) {
                (bytes * 8 / expectedDurationMs).toInt()
            } else {
                0
            }
            when {
                kbps >= 320 -> QualityTier.LOSSY_320
                kbps >= 160 -> QualityTier.LOSSY_192
                kbps >= 112 -> QualityTier.LOSSY_128
                else -> QualityTier.LOSSY_LOW
            }
        }
    }
}

internal sealed interface DownloadAttempt<out T> {
    /** Downloaded bytes were recognized as audio. The caller owns the temporary file. */
    data class Ready<T>(val audio: DownloadAudio, val value: T) : DownloadAttempt<T>
    data class Failed(val reason: DownloadRejection) : DownloadAttempt<Nothing>
}

internal enum class DownloadRejection { UNAVAILABLE, INVALID_AUDIO }

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

/**
 * MusicFree-style tier selection: try each requested MusicFree key once and accept the
 * first response that is recognizable audio. A lower measured tier is reported as a
 * downgrade, but it is no longer a failure; a missing plugin or a non-audio body is.
 */
internal suspend fun <T> validateDownload(
    request: QualityRequest,
    expectedDurationMs: Long?,
    attempt: suspend (key: String) -> DownloadAttempt<T>
): DownloadValidation<T> {
    val failures = mutableListOf<DownloadFailure>()
    for (key in request.chain.map { it.musicFreeKey }.distinct()) {
        currentCoroutineContext().ensureActive()
        when (val result = attempt(key)) {
            is DownloadAttempt.Failed -> failures += DownloadFailure(key, result.reason)
            is DownloadAttempt.Ready ->
                return DownloadValidation.Accepted(
                    result.value,
                    key,
                    result.audio.quality(expectedDurationMs),
                    failures.toList(),
                    result.audio.quality(expectedDurationMs) < request.requested
                )
        }
    }
    return DownloadValidation.Rejected(failures.toList())
}
