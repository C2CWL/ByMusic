package com.sosauce.chocola.core.data.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal data class VerifiedDownloadAudio(val container: AudioContainer, val audio: DecodedDownload)

/** Inspect a private temporary file. A successful result requires PCM near both ends. */
internal suspend fun decodeDownloadAudio(file: File): VerifiedDownloadAudio? =
    withContext(Dispatchers.IO) {
        try {
            inspect(file)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

private suspend fun inspect(file: File): VerifiedDownloadAudio? {
    val length = file.length()
    if (length <= 0) return null
    val header = file.inputStream().buffered().use { it.readNBytesCompat(AudioSniffer.HEADER_BYTES) }
    val sniff = AudioSniffer.sniff(header)
    if (!sniff.container.isAudio) return null
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        val index = (0 until extractor.trackCount).singleOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        if (!sniff.container.supports(mime)) return null
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        if (durationUs < 1_000 || sampleRate <= 0) return null
        extractor.selectTrack(index)
        // Check actual output close to the claimed end to catch truncated payloads.
        val openingTarget = minOf(1_500_000L, durationUs / 2)
        val closingTarget = maxOf(openingTarget, durationUs - minOf(500_000L, durationUs / 5))
        if (!decodeThrough(extractor, format, mime, 0, openingTarget)) return null
        if (closingTarget > openingTarget && !decodeThrough(
                extractor, format, mime, maxOf(0, durationUs - 2_000_000L), closingTarget
            )
        ) return null
        val durationMs = durationUs / 1_000
        val trackBitrate = if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
            format.getInteger(MediaFormat.KEY_BIT_RATE).takeIf { it > 0 }
        } else {
            null
        }
        val averageKbps = trackBitrate?.div(1_000) ?: (length.toDouble() * 8 / durationMs).toInt()
        return VerifiedDownloadAudio(
            sniff.container,
            DecodedDownload(mime, durationMs, sampleRate, sniff.flac?.bitsPerSample, averageKbps)
        )
    } finally {
        extractor.release()
    }
}

private fun AudioContainer.supports(mime: String): Boolean = when (this) {
    AudioContainer.FLAC -> mime == "audio/flac"
    AudioContainer.MP3 -> mime == "audio/mpeg"
    AudioContainer.MP4 -> mime in setOf("audio/mp4a-latm", "audio/alac", "audio/eac3", "audio/eac3-joc", "audio/ac4")
    AudioContainer.OGG_VORBIS -> mime == "audio/vorbis"
    AudioContainer.OGG_OPUS -> mime == "audio/opus"
    AudioContainer.WAV -> mime == "audio/raw"
    AudioContainer.AAC_ADTS -> mime == "audio/mp4a-latm"
    AudioContainer.TEXT, AudioContainer.UNKNOWN -> false
}

private suspend fun decodeThrough(
    extractor: MediaExtractor,
    format: MediaFormat,
    mime: String,
    seekUs: Long,
    targetUs: Long
): Boolean {
    extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    val codec = MediaCodec.createDecoderByType(mime)
    try {
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        val deadline = SystemClock.elapsedRealtime() + 8_000
        var inputEnded = false
        while (SystemClock.elapsedRealtime() < deadline) {
            currentCoroutineContext().ensureActive()
            if (!inputEnded) {
                val input = codec.dequeueInputBuffer(5_000)
                if (input >= 0) {
                    val buffer = codec.getInputBuffer(input) ?: return false
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val output = codec.dequeueOutputBuffer(info, 5_000)
            if (output >= 0) {
                codec.releaseOutputBuffer(output, false)
                if (info.size > 0 && info.presentationTimeUs >= targetUs) return true
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return false
            }
        }
        return false
    } finally {
        codec.release()
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val bytes = ByteArray(limit)
    var offset = 0
    while (offset < limit) {
        val count = read(bytes, offset, limit - offset)
        if (count <= 0) break
        offset += count
    }
    return bytes.copyOf(offset)
}
