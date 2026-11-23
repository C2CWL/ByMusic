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
    fun keepsDecodableAudioEvenWhenBitrateIsBelowRequestedTier() = runBlocking {
        val calls = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val outcome = validateDownload<String>(
            QualityRequest.autoFromTop(),
            expectedDurationMs = 180_000,
            discard = { removed += it },
            attempt = { key ->
                calls += key
                DownloadAttempt.Decoded(audio(192, 180_000), "playable")
            }
        )
        assertEquals(listOf("super"), calls)
        assertTrue(removed.isEmpty())
        assertEquals("playable", (outcome as DownloadValidation.Accepted<String>).value)
        assertEquals(QualityTier.LOSSY_192, outcome.measuredQuality)
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
    fun keepsDecodableAudioWhenPluginDurationDoesNotMatch() = runBlocking {
        val removed = mutableListOf<String>()
        val outcome = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSLESS),
            expectedDurationMs = 180_000,
            discard = { removed += it },
            attempt = { key ->
                assertEquals("high", key)
                DownloadAttempt.Decoded(audio(900, 120_000), "full-track")
            }
        )
        assertTrue(removed.isEmpty())
        assertEquals("full-track", (outcome as DownloadValidation.Accepted<String>).value)
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
    fun acceptsLowerMeasuredQualityFromSameKeyWhenDowngradeAllowed() = runBlocking {
        listOf("standard" to 192, "low" to 96).forEach { (key, kbps) ->
            val request = QualityRequest.preferred(
                if (key == "standard") QualityTier.LOSSY_320 else QualityTier.LOSSY_128
            )
            val result = validateDownload<String>(
                request,
                expectedDurationMs = 180_000,
                discard = { error("valid audio should be kept") },
                attempt = {
                    assertEquals(key, it)
                    DownloadAttempt.Decoded(audio(kbps, 180_000), "audio")
                }
            ) as DownloadValidation.Accepted<String>
            assertEquals(audio(kbps, 180_000).quality(), result.measuredQuality)
            assertTrue(result.downgraded)
        }
    }

    @Test
    fun keepsLowerBitrateWhenExactQualityWasRequested() = runBlocking {
        val result = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSY_320, allowDowngrade = false),
            expectedDurationMs = 180_000,
            discard = { error("valid audio should be kept") },
            attempt = {
                assertEquals("standard", it)
                DownloadAttempt.Decoded(audio(192, 180_000), "low-bitrate")
            }
        ) as DownloadValidation.Accepted<String>
        assertEquals("low-bitrate", result.value)
        assertEquals(QualityTier.LOSSY_192, result.measuredQuality)
        assertTrue(result.belowRequested)
        assertTrue(result.downgraded)
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
