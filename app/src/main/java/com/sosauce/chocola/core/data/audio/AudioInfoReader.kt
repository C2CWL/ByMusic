package com.sosauce.chocola.core.data.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.webkit.MimeTypeMap
import com.kyant.taglib.AudioPropertiesReadStyle
import com.kyant.taglib.TagLib
import com.sosauce.chocola.core.domain.model.CuteTrack

/** Reads local audio stream facts with the dependencies already present in the app. */
object AudioInfoReader {

    fun read(context: Context, track: CuteTrack): AudioInfo {
        val format = fileExtension(context, track)
        var estimatedBitRate = 0
        var fileSize = 0L

        context.contentResolver.openFileDescriptor(track.uri, "r")?.use { fd ->
            fileSize = fd.statSize
            if (track.durationMillis > 0 && fileSize > 0) {
                estimatedBitRate = ((fileSize * 8_000L) / track.durationMillis).toInt()
            }
            val dup = fd.dup()?.detachFd() ?: return@use
            TagLib.getAudioProperties(dup, AudioPropertiesReadStyle.Fast)?.let { properties ->
                return AudioInfo(
                    format = format,
                    bitRate = properties.bitrate.takeIf { it > 0 }?.times(1000) ?: estimatedBitRate,
                    sampleRate = properties.sampleRate,
                    channels = properties.channels,
                    durationMillis = properties.length.takeIf { it > 0 }?.toLong()
                        ?: track.durationMillis
                )
            }
        }

        return readFromMediaExtractor(context, track, format, estimatedBitRate)
            ?: AudioInfo(format = format, bitRate = estimatedBitRate, durationMillis = track.durationMillis)
    }

    private fun readFromMediaExtractor(
        context: Context,
        track: CuteTrack,
        format: String,
        estimatedBitRate: Int
    ): AudioInfo? = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, track.uri, null)
            var audioFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioFormat = candidate
                    break
                }
            }
            val audio = audioFormat ?: return@runCatching null
            AudioInfo(
                format = format,
                bitRate = audio.getIntOrZero(MediaFormat.KEY_BIT_RATE).takeIf { it > 0 } ?: estimatedBitRate,
                sampleRate = audio.getIntOrZero(MediaFormat.KEY_SAMPLE_RATE),
                bitDepth = audio.getIntOrZero("bits-per-sample"),
                channels = audio.getIntOrZero(MediaFormat.KEY_CHANNEL_COUNT),
                durationMillis = if (audio.containsKey(MediaFormat.KEY_DURATION)) {
                    audio.getLong(MediaFormat.KEY_DURATION)
                } else {
                    track.durationMillis
                }
            )
        } finally {
            extractor.release()
        }
    }.getOrNull()

    private fun MediaFormat.getIntOrZero(name: String): Int =
        if (containsKey(name)) getInteger(name) else 0

    private fun fileExtension(context: Context, track: CuteTrack): String {
        val mime = context.contentResolver.getType(track.uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime).orEmpty()
    }
}
