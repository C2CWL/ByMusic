package com.sosauce.chocola.core.data.download

import com.sosauce.chocola.core.domain.online.QualityTier

/** What decoding the file found out. */
data class MeasuredAudio(
    val container: AudioContainer,
    /** Decoder mime from MediaExtractor, e.g. audio/flac, audio/mpeg, audio/mp4a-latm, audio/alac. */
    val codecMime: String?,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    /** Known for FLAC (STREAMINFO) and WAV; 0 otherwise. */
    val bitsPerSample: Int,
    val sizeBytes: Long
) {
    val averageKbps: Int
        get() = if (durationMs > 0) (sizeBytes * 8 / durationMs).toInt() else 0
}

/**
 * Measured quality and the download rules (design doc §四 5). Pure Kotlin, no Android.
 * MusicFree's four keys are only request names; what counts is the file.
 */
object DownloadQuality {

    /** Top to bottom, as asked from plugins. */
    val KEYS = listOf("super", "high", "standard", "low")

    private val LOSSLESS_MIMES = setOf("audio/flac", "audio/alac", "audio/raw", "audio/x-wav", "audio/wav")
    private val SPATIAL_MIMES = setOf("audio/eac3-joc", "audio/ac4")

    fun isLossless(m: MeasuredAudio): Boolean =
        m.container == AudioContainer.FLAC || m.container == AudioContainer.WAV || m.codecMime in LOSSLESS_MIMES

    fun tierOf(m: MeasuredAudio): QualityTier {
        if (m.codecMime in SPATIAL_MIMES) return QualityTier.SPATIAL
        if (isLossless(m)) {
            return if (m.bitsPerSample > 16 || m.sampleRate > 48_000) QualityTier.HIRES else QualityTier.LOSSLESS
        }
        val kbps = m.averageKbps
        return when {
            kbps <= 0 -> QualityTier.LOSSY_LOW
            kbps <= 112 -> QualityTier.LOSSY_LOW
            kbps < 160 -> QualityTier.LOSSY_128
            kbps < 240 -> QualityTier.LOSSY_192
            else -> QualityTier.LOSSY_320
        }
    }

    /** The lowest real tier that still counts as having delivered [key]. */
    fun minimumTierFor(key: String): QualityTier = when (key) {
        "super" -> QualityTier.HIRES
        "high" -> QualityTier.LOSSLESS
        "standard" -> QualityTier.LOSSY_192
        else -> QualityTier.LOSSY_LOW
    }

    /** Best key a file of [tier] satisfies. */
    fun keyFor(tier: QualityTier): String = KEYS.first { tier >= minimumTierFor(it) }

    fun keyLabel(key: String?): String = when (key) {
        "super" -> "Hi-Res"
        "high" -> "无损"
        "standard" -> "320k"
        "low" -> "128k"
        null -> "自动"
        else -> key
    }

    /** e.g. "FLAC 16bit/44.1kHz", "MP3 320k", "AAC 256k". */
    fun formatLabel(m: MeasuredAudio): String {
        val codec = when {
            m.codecMime == "audio/alac" -> "ALAC"
            m.codecMime == "audio/opus" || m.container == AudioContainer.OGG_OPUS -> "Opus"
            m.codecMime == "audio/vorbis" -> "OGG"
            m.codecMime == "audio/mp4a-latm" || m.container == AudioContainer.AAC_ADTS -> "AAC"
            m.codecMime in SPATIAL_MIMES -> "杜比全景声"
            m.codecMime == "audio/eac3" -> "E-AC-3"
            else -> m.container.label
        }
        if (isLossless(m)) {
            val bits = if (m.bitsPerSample > 0) "${m.bitsPerSample}bit/" else ""
            return "$codec $bits${khz(m.sampleRate)}"
        }
        val kbps = m.averageKbps
        return if (kbps > 0) "$codec ${roundKbps(kbps)}k" else codec
    }

    private fun khz(rate: Int): String {
        if (rate <= 0) return ""
        val k = rate / 1000.0
        return if (rate % 1000 == 0) "${rate / 1000}kHz" else "%.1fkHz".format(java.util.Locale.ROOT, k)
    }

    /** Nominal bitrates read better than 317k. */
    private fun roundKbps(kbps: Int): Int {
        val nominal = listOf(64, 96, 128, 160, 192, 256, 320)
        val near = nominal.minByOrNull { kotlin.math.abs(it - kbps) }!!
        return if (kotlin.math.abs(near - kbps) <= near * 0.06) near else kbps
    }

    /** Keys to ask for, in order. Auto = from the top; a picked key steps down only when allowed. */
    fun chainFor(requestedKey: String?, autoDowngrade: Boolean): List<String> {
        if (requestedKey == null) return KEYS
        val start = KEYS.indexOf(requestedKey).coerceAtLeast(0)
        return if (autoDowngrade) KEYS.drop(start) else listOf(KEYS[start])
    }

    sealed interface Verdict {
        /** Keep it. [servedKey] is what the file really counts as. */
        data class Accept(val servedKey: String, val downgraded: Boolean) : Verdict

        /** Throw the file away. [tryLower] means another (lower) tier may still work. */
        data class Reject(val reason: String, val tryLower: Boolean, val suggestedKey: String?) : Verdict
    }

    /**
     * Rules after a successful decode check:
     * - length must match the plugin's duration (±3 s or 2 %); much shorter = probably a preview;
     * - the real quality must reach [askedKey]. If it does not and downgrading is allowed,
     *   the file is kept as the best lower key still in [chain] (no second download);
     *   otherwise it is rejected with a reason like "要的是 FLAC，下到的是 128k MP3".
     */
    fun judge(
        measured: MeasuredAudio,
        expectedDurationMs: Long,
        askedKey: String,
        chain: List<String>,
        autoDowngrade: Boolean,
        requestedByUser: Boolean
    ): Verdict {
        val label = formatLabel(measured)
        val askedIndex = chain.indexOf(askedKey)
        val lowerKeys = if (askedIndex >= 0) chain.drop(askedIndex + 1) else emptyList()
        val canStepDown = lowerKeys.isNotEmpty() && (autoDowngrade || !requestedByUser)
        if (expectedDurationMs > 0 && measured.durationMs > 0) {
            if (measured.durationMs < expectedDurationMs * 7 / 10) {
                return Verdict.Reject(
                    "只下到 ${seconds(measured.durationMs)}，歌曲有 ${seconds(expectedDurationMs)}，像是试听片段，文件已删除",
                    tryLower = canStepDown,
                    suggestedKey = lowerKeys.firstOrNull()
                )
            }
            val tolerance = maxOf(3_000L, expectedDurationMs / 50)
            if (kotlin.math.abs(measured.durationMs - expectedDurationMs) > tolerance) {
                return Verdict.Reject(
                    "时长对不上（文件 ${seconds(measured.durationMs)}，歌曲 ${seconds(expectedDurationMs)}），文件已删除",
                    tryLower = canStepDown,
                    suggestedKey = lowerKeys.firstOrNull()
                )
            }
        }
        val tier = tierOf(measured)
        if (tier >= minimumTierFor(askedKey)) return Verdict.Accept(askedKey, downgraded = false)
        val fallback = lowerKeys.firstOrNull { tier >= minimumTierFor(it) }
        if (fallback != null && (autoDowngrade || !requestedByUser)) {
            return Verdict.Accept(fallback, downgraded = true)
        }
        return Verdict.Reject(
            "要的是 ${keyLabel(askedKey)}，下到的是 $label，文件已删除",
            tryLower = false,
            suggestedKey = fallback ?: keyFor(tier).takeIf { it != askedKey }
        )
    }

    private fun seconds(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(java.util.Locale.ROOT, s / 60, s % 60)
    }
}
