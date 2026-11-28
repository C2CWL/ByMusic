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
    fun keepsRecognizedAudioEvenWhenQualityIsBelowRequested() = runBlocking {
        val calls = mutableListOf<String>()
        val outcome = validateDownload<String>(
            QualityRequest.autoFromTop(),
            expectedDurationMs = 180_000
        ) { key ->
            calls += key
            DownloadAttempt.Ready(mp3(192, 180_000), "playable")
        }
        assertEquals(listOf("super"), calls)
        val accepted = outcome as DownloadValidation.Accepted<String>
        assertEquals("playable", accepted.value)
        assertEquals(QualityTier.LOSSY_192, accepted.measuredQuality)
        assertTrue(accepted.belowRequested)
        assertTrue(accepted.downgraded)
        assertTrue(accepted.failures.isEmpty())
    }

    @Test
    fun triesEachMusicFreeKeyOnlyOnceWhenAllTiersFail() = runBlocking {
        val calls = mutableListOf<String>()
        val result = validateDownload<String>(
            QualityRequest.autoFromTop(),
            expectedDurationMs = null
        ) { key ->
            calls += key
            DownloadAttempt.Failed(DownloadRejection.UNAVAILABLE)
        }
        assertEquals(listOf("super", "high", "standard", "low"), calls)
        val rejected = result as DownloadValidation.Rejected
        assertEquals(calls, rejected.failures.map { it.key })
        assertTrue(rejected.failures.all { it.reason == DownloadRejection.UNAVAILABLE })
    }

    @Test
    fun acceptsRecognizedAudioWhenPluginDurationDoesNotMatch() = runBlocking {
        val outcome = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSLESS),
            expectedDurationMs = 180_000
        ) { key ->
            assertEquals("high", key)
            DownloadAttempt.Ready(flac(16, 44_100), "full-track")
        }
        val accepted = outcome as DownloadValidation.Accepted<String>
        assertEquals("full-track", accepted.value)
        assertEquals(QualityTier.LOSSLESS, accepted.measuredQuality)
        assertFalse(accepted.downgraded)
    }

    @Test
    fun acceptsHiResFlacFromSuperKey() = runBlocking {
        val outcome = validateDownload<String>(
            QualityRequest.preferred(QualityTier.HIRES, allowDowngrade = false),
            expectedDurationMs = null
        ) { key ->
            assertEquals("super", key)
            DownloadAttempt.Ready(flac(24, 48_000), "hires")
        }
        val accepted = outcome as DownloadValidation.Accepted<String>
        assertEquals(QualityTier.HIRES, accepted.measuredQuality)
        assertFalse(accepted.belowRequested)
    }

    @Test
    fun reportsLowerMeasuredQualityWithoutDiscarding() = runBlocking {
        listOf("standard" to 192, "low" to 96).forEach { (key, kbps) ->
            val request = QualityRequest.preferred(
                if (key == "standard") QualityTier.LOSSY_320 else QualityTier.LOSSY_128,
                allowDowngrade = false
            )
            val result = validateDownload<String>(
                request,
                expectedDurationMs = 180_000
            ) { requestedKey ->
                assertEquals(key, requestedKey)
                DownloadAttempt.Ready(mp3(kbps, 180_000), "audio")
            } as DownloadValidation.Accepted<String>
            assertEquals(mp3(kbps, 180_000).quality(180_000), result.measuredQuality)
            assertTrue(result.belowRequested)
            assertTrue(result.downgraded)
        }
    }

    @Test
    fun keepsLowerBitrateWhenExactQualityWasRequested() = runBlocking {
        val result = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSY_320, allowDowngrade = false),
            expectedDurationMs = 180_000
        ) {
            assertEquals("standard", it)
            DownloadAttempt.Ready(mp3(192, 180_000), "low-bitrate")
        } as DownloadValidation.Accepted<String>
        assertEquals("low-bitrate", result.value)
        assertEquals(QualityTier.LOSSY_192, result.measuredQuality)
        assertTrue(result.belowRequested)
        assertTrue(result.downgraded)
    }

    @Test
    fun rejectsInvalidAudioWithoutNamingItAudio() = runBlocking {
        val result = validateDownload<String>(
            QualityRequest.preferred(QualityTier.LOSSLESS, allowDowngrade = false),
            expectedDurationMs = null
        ) { DownloadAttempt.Failed(DownloadRejection.INVALID_AUDIO) }
        val rejected = result as DownloadValidation.Rejected
        assertEquals(listOf("high"), rejected.failures.map { it.key })
        assertEquals(DownloadRejection.INVALID_AUDIO, rejected.failures.single().reason)
    }

    private fun mp3(kbps: Int, durationMs: Long) =
        DownloadAudio(AudioContainer.MP3, kbps.toLong() * durationMs / 8, null)

    private fun flac(bits: Int, sampleRate: Int) = DownloadAudio(
        AudioContainer.FLAC,
        bytes = 1_000_000,
        flac = FlacInfo(sampleRate, 2, bits, 0)
    )
}
