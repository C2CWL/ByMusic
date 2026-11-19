package com.sosauce.chocola.feature.recognition.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal PCM16 WAV header, the simplest format a recognition plugin can read. */
object WavFormat {
    const val HEADER_SIZE = 44
    const val MIME_TYPE = "audio/wav"

    fun header(sampleRateHz: Int, channelCount: Int, pcmDataBytes: Long): ByteArray {
        val bitsPerSample = 16
        val byteRate = sampleRateHz * channelCount * bitsPerSample / 8
        val blockAlign = channelCount * bitsPerSample / 8
        val data = pcmDataBytes.coerceIn(0L, Int.MAX_VALUE.toLong() - HEADER_SIZE).toInt()
        return ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + data)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channelCount.toShort())
            putInt(sampleRateHz)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(data)
        }.array()
    }

    fun durationMs(sampleRateHz: Int, channelCount: Int, pcmDataBytes: Long): Long {
        val bytesPerSecond = sampleRateHz.toLong() * channelCount * 2
        return if (bytesPerSecond == 0L) 0L else pcmDataBytes * 1000L / bytesPerSecond
    }

    /** Loudness of a PCM16 chunk mapped to 0..1 for the waveform. */
    fun level(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        val rms = kotlin.math.sqrt(sum / count) / Short.MAX_VALUE
        // sqrt lifts quiet rooms so the bars still move a little.
        return kotlin.math.sqrt(rms).toFloat().coerceIn(0f, 1f)
    }
}
