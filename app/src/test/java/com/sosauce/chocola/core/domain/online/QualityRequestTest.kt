package com.sosauce.chocola.core.domain.online

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import com.sosauce.chocola.core.domain.online.QualityTier.HIRES
import com.sosauce.chocola.core.domain.online.QualityTier.LOSSLESS
import com.sosauce.chocola.core.domain.online.QualityTier.LOSSY_128
import com.sosauce.chocola.core.domain.online.QualityTier.LOSSY_192
import com.sosauce.chocola.core.domain.online.QualityTier.LOSSY_320
import com.sosauce.chocola.core.domain.online.QualityTier.LOSSY_LOW
import com.sosauce.chocola.core.domain.online.QualityTier.SPATIAL

class QualityRequestTest {

    @Test
    fun autoStartsFromTopAndWalksDown() {
        val request = QualityRequest.autoFromTop()
        assertEquals(listOf(HIRES, LOSSLESS, LOSSY_320, LOSSY_192, LOSSY_128, LOSSY_LOW), request.chain)
        assertEquals(HIRES, request.requested)
        assertEquals(QualityRequest.Mode.AUTO_FROM_TOP, request.mode)
    }

    @Test
    fun autoUsesOnlyAvailableTiersHighestFirst() {
        val request = QualityRequest.autoFromTop(listOf(LOSSY_128, LOSSLESS, LOSSY_320))
        assertEquals(listOf(LOSSLESS, LOSSY_320, LOSSY_128), request.chain)
    }

    @Test
    fun spatialOnlyWhenAskedFor() {
        assertEquals(SPATIAL, QualityRequest.autoFromTop(includeSpatial = true).requested)
        assertEquals(LOSSY_128, QualityRequest.autoFromTop(listOf(SPATIAL)).requested)
    }

    @Test
    fun preferredThenDownAndExact() {
        assertEquals(
            listOf(LOSSY_320, LOSSY_192, LOSSY_128, LOSSY_LOW),
            QualityRequest.preferred(LOSSY_320).chain
        )
        val exact = QualityRequest.preferred(LOSSLESS, allowDowngrade = false)
        assertEquals(listOf(LOSSLESS), exact.chain)
        assertEquals(QualityRequest.Mode.EXACT, exact.mode)
    }

    @Test
    fun musicFreeKeysMapAndDeduplicate() {
        assertEquals(listOf("super", "high", "standard", "low"), QualityRequest.autoFromTop().musicFreeKeyChain())
        assertEquals(LOSSY_128, QualityTier.fromMusicFreeKey("low"))
        assertEquals(LOSSY_320, QualityTier.fromMusicFreeKey("standard"))
        assertEquals(LOSSLESS, QualityTier.fromMusicFreeKey("high"))
        assertEquals(HIRES, QualityTier.fromMusicFreeKey("super"))
        assertNull(QualityTier.fromMusicFreeKey("flac24bit"))
        assertNull(QualityTier.fromMusicFreeKey(null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyChainIsRejected() {
        QualityRequest(emptyList(), QualityRequest.Mode.EXACT)
    }

    @Test
    fun tryInOrderFallsDownUntilSuccess() = runBlocking {
        val tried = mutableListOf<QualityTier>()
        val outcome = QualityRequest.autoFromTop().tryInOrder { tier ->
            tried += tier
            when (tier) {
                HIRES -> null
                LOSSLESS -> throw IllegalStateException("broken link")
                else -> "url-$tier"
            }
        }!!
        assertEquals(listOf(HIRES, LOSSLESS, LOSSY_320), tried)
        assertEquals(LOSSY_320, outcome.tier)
        assertEquals("url-LOSSY_320", outcome.value)
        assertEquals(
            listOf(
                FallbackAttempt(HIRES, "no result"),
                FallbackAttempt(LOSSLESS, "broken link"),
                FallbackAttempt(LOSSY_320, null)
            ),
            outcome.attempts
        )
    }

    @Test
    fun tryInOrderReturnsNullWhenEverythingFails() = runBlocking {
        val outcome = QualityRequest.preferred(LOSSY_192).tryInOrder<String> { null }
        assertNull(outcome)
    }

    @Test
    fun tryInOrderNeverSwallowsCancellation() = runBlocking {
        var calls = 0
        try {
            QualityRequest.autoFromTop().tryInOrder<String> {
                calls++
                throw CancellationException("stop")
            }
            fail("cancellation must propagate")
        } catch (e: CancellationException) {
            assertEquals("stop", e.message)
        }
        assertTrue(calls == 1)
    }
}
