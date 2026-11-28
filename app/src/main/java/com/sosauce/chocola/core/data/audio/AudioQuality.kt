package com.sosauce.chocola.core.data.audio

import com.sosauce.chocola.R
import java.util.Locale

/** Audio stream facts needed by the UI. Bitrate is stored in bits per second. */
data class AudioInfo(
    val format: String = "",
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val durationMillis: Long = 0L
)

enum class AudioQuality(val labelRes: Int) {
    HI_RES(R.string.quality_hi_res),
    LOSSLESS(R.string.quality_lossless),
    HQ(R.string.quality_hq),
    LQ(R.string.quality_lq),
    UNKNOWN(R.string.quality_unknown)
}

/** Mirrors Halcyon's format normalization closely enough for local files. */
fun normalizedAudioFormat(raw: String): String {
    val value = raw.uppercase(Locale.ROOT)
    return when {
        "EAC3" in value || "E-AC-3" in value || "EC-3" in value -> "EC3"
        "AC4" in value || "AC-4" in value -> "AC4"
        "AC3" in value || "AC-3" in value -> "AC3"
        "ALAC" in value -> "ALAC"
        "FLAC" in value -> "FLAC"
        "AAC" in value -> "AAC"
        "M4A" in value || "MP4" in value -> "M4A"
        "MP3" in value || "MPEG" in value -> "MP3"
        "WAV" in value || "PCM" in value -> "WAV"
        "OPUS" in value -> "OPUS"
        "OGG" in value -> "OGG"
        "APE" in value -> "APE"
        "DSD" in value -> "DSD"
        "AIFF" in value -> "AIFF"
        else -> value
    }
}

fun effectiveBitDepth(info: AudioInfo): Int {
    if (info.bitDepth > 0) return info.bitDepth
    val format = normalizedAudioFormat(info.format)
    if (format in setOf("FLAC", "ALAC", "WAV", "APE")) {
        return if (info.sampleRate >= 88_200 || info.bitRate >= 1_600_000) 24 else 16
    }
    return 0
}

fun detectAudioQuality(info: AudioInfo): AudioQuality {
    val format = normalizedAudioFormat(info.format)
    val isSurround = format in setOf("AC3", "EC3", "AC4") || info.channels >= 6
    val isLossless = format in setOf("FLAC", "ALAC", "WAV", "APE", "DSD", "AIFF")
    val isKnownLossy = format in setOf("MP3", "AAC", "M4A", "OGG", "OPUS", "WMA")
    val bitDepth = effectiveBitDepth(info)
    return when {
        isSurround -> AudioQuality.HQ
        isLossless && bitDepth >= 24 && info.sampleRate >= 192_000 -> AudioQuality.HI_RES
        isLossless && (bitDepth > 16 || info.sampleRate > 44_100) -> AudioQuality.HI_RES
        isLossless -> AudioQuality.LOSSLESS
        info.bitRate >= 319_000 -> AudioQuality.HQ
        info.bitRate > 0 || isKnownLossy -> AudioQuality.LQ
        else -> AudioQuality.UNKNOWN
    }
}
