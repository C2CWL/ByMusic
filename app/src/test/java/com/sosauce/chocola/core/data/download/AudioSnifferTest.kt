package com.sosauce.chocola.core.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioSnifferTest {
    @Test
    fun rejectsErrorPagesAndId3WithoutAnAudioFrame() {
        assertEquals(AudioContainer.TEXT, AudioSniffer.sniff("\uFEFF  <!DOCTYPE html>".toByteArray()).container)
        assertEquals(AudioContainer.TEXT, AudioSniffer.sniff(" {\"error\":403}".toByteArray()).container)
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0, 0, 0, 0, 1)
        assertEquals(AudioContainer.UNKNOWN, AudioSniffer.sniff(tag).container)
    }

    @Test
    fun recognizesMP3AfterAnEmptyTag() {
        val tag = "ID3".toByteArray() + byteArrayOf(4, 0, 0, 0, 0, 0, 0)
        val mpegLayer3 = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0)
        assertEquals(AudioContainer.MP3, AudioSniffer.sniff(tag + mpegLayer3).container)
    }

    @Test
    fun readsCompleteFlacStreamInfoOnly() {
        val streamInfo = ByteArray(34)
        // 48000 Hz, stereo, 24 bits per sample, 48000 samples.
        val packed = (48_000L shl 44) or (1L shl 41) or (23L shl 36) or 48_000L
        for (i in 0..7) streamInfo[10 + i] = (packed ushr ((7 - i) * 8)).toByte()
        val header = "fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34) + streamInfo
        assertEquals(FlacInfo(48_000, 2, 24, 48_000), AudioSniffer.sniff(header).flac)
        assertNull(AudioSniffer.sniff(header.copyOf(41)).flac)
    }
}
