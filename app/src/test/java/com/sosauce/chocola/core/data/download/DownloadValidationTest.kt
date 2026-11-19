package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadValidationTest {
    @Test
    fun acceptsOnlyTierActuallyDeliveredAndStepsDown() = runBlocking {
        val calls = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val request = QualityRequest.autoFromTop()
        val outcome = validateDownload<String>(
            request,
            expectedDurationMs = 180_000,
            discard = { removed += it },
            attempt = { key ->
                calls += key
                when (key) {
                    "super" -> DownloadAttempt.Decoded(audio(192, 180_000), "bad-super")
                    "high" -> DownloadAttempt.Decoded(audio(160, 180_000), "bad-high")
                    "standard" -> DownloadAttempt.Decoded(audio(320, 180_000), "standard")
                    else -> error("unexpected duplicate or lower-tier retry: $key")
                }
            }
        )
        assertEquals(listOf("super", "high", "standard"), calls)
        assertEquals(listOf("bad-super", "bad-high"), removed)
        assertEquals("standard", (outcome as DownloadValidation.Accepted<String>).value)
        assertEquals(QualityTier.LOSSY_320, outcome.measuredQuality)
        assertTrue(outcome.downgraded)
    }

    @Test
    fun triesEachMusicFreeKeyOnlyOnceWhenAllTiersFail() = runBlocking {
        val calls = mutableListOf<String>()
        val result = validateDownload<String>(
            QualityRequest.autoFromTop(),
            expectedDurationMs = null,
            discard = { error("no file was produced") },
            attempt = { key ->
                calls += key
                DownloadAttempt.Failed(DownloadRejection.UNAVAILABLE)
            }
        )
        assertEquals(listOf("super", "high", "standard", "low"), calls)
        assertEquals(calls, (result as DownloadValidation.Rejected).failures.map { it.key })
    }

    @Test
    fun rejectsBadDurationAndDeletesCandidateBeforeTryingLowerKey() = runBlocking {
        val removed = mutableListOf<String>()
        val request = QualityRequest.preferred(QualityTier.LOSSLESS)
        val outcome = validateDownload<String>(
            request,
            expectedDurationMs = 180_000,
            discard = { removed += it },
            attempt = { key ->
                when (key) {
                    "high" -> DownloadAttempt.Decoded(audio(900, 120_000), "preview")
                    else -> DownloadAttempt.Failed(DownloadRejection.UNAVAILABLE)
                }
            }
        )
        assertEquals(listOf("preview"), removed)
        assertTrue(outcome is DownloadValidation.Rejected)
        assertEquals(listOf("high", "standard", "low"), (outcome as DownloadValidation.Rejected).failures.map { it.key })
        assertEquals(DownloadRejection.DURATION_MISMATCH, outcome.failures.first().reason)
    }

    @Test
    fun acceptsLosslessOnlyWhenDecoderReportsLosslessCodec() = runBlocking {
        val result = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSLESS, allowDowngrade = false),
            expectedDurationMs = null,
            discard = {},
            attempt = { DownloadAttempt.Decoded(lossless(), "flac") }
        )
        assertFalse((result as DownloadValidation.Accepted<String>).downgraded)
        assertEquals("high", result.key)
    }

    @Test
    fun rejects192KbpsForStandardWithoutTryingLowerWhenExact() = runBlocking {
        val removed = mutableListOf<String>()
        val result = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSY_320, allowDowngrade = false),
            expectedDurationMs = 180_000,
            discard = { removed += it },
            attempt = { key ->
                assertEquals("standard", key)
                DownloadAttempt.Decoded(audio(192, 180_000), "low-bitrate")
            }
        )
        assertEquals(listOf("low-bitrate"), removed)
        assertEquals(
            DownloadValidation.Rejected(listOf(DownloadFailure("standard", DownloadRejection.QUALITY_MISMATCH))),
            result
        )
    }

    @Test
    fun rejectsUnknownCodecRatherThanNamingItAudio() {
        assertEquals(null, DecodedDownload("application/octet-stream", 180_000, 0, null, null).quality())
    }

    private fun audio(kbps: Int, durationMs: Long) =
        DecodedDownload("audio/mpeg", durationMs, 44_100, null, kbps)

    private fun lossless() =
        DecodedDownload("audio/flac", 180_000, 44_100, 16, null)
}
