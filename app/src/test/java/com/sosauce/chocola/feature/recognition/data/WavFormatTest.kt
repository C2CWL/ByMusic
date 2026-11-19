package com.sosauce.chocola.feature.recognition.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavFormatTest {

    @Test
    fun headerDescribesMonoPcm16() {
        val dataBytes = 44_100L * 2 * 10
        val h = WavFormat.header(44_100, 1, dataBytes)
        assertEquals(WavFormat.HEADER_SIZE, h.size)
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + dataBytes.toInt(), b.getInt(4))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(44_100, b.getInt(24))
        assertEquals(88_200, b.getInt(28))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        assertEquals(dataBytes.toInt(), b.getInt(40))
    }

    @Test
    fun durationFromBytes() {
        assertEquals(10_000L, WavFormat.durationMs(44_100, 1, 44_100L * 2 * 10))
    }

    @Test
    fun levelIsZeroForSilenceAndBounded() {
        assertEquals(0f, WavFormat.level(ShortArray(100), 100))
        val loud = ShortArray(100) { Short.MAX_VALUE }
        val level = WavFormat.level(loud, 100)
        assertTrue(level in 0.99f..1f)
        assertEquals(0f, WavFormat.level(loud, 0))
    }
}
