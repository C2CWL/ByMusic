package com.sosauce.chocola.core.data.download

/** What the first bytes of a file say it is. Pure Kotlin. */
enum class AudioContainer(val extension: String?, val mimeType: String?, val label: String) {
    FLAC("flac", "audio/flac", "FLAC"),
    MP3("mp3", "audio/mpeg", "MP3"),
    MP4("m4a", "audio/mp4", "M4A"),
    OGG_VORBIS("ogg", "audio/ogg", "OGG"),
    OGG_OPUS("opus", "audio/ogg", "Opus"),
    WAV("wav", "audio/wav", "WAV"),
    AAC_ADTS("aac", "audio/aac", "AAC"),

    /** An HTML/JSON/text page, i.e. an error page instead of audio. */
    TEXT(null, null, "网页或文字"),
    UNKNOWN(null, null, "未知格式");

    val isAudio: Boolean get() = extension != null
}

/** FLAC STREAMINFO: exact sample rate, bit depth and length. */
data class FlacInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long
) {
    val durationMs: Long get() = if (sampleRate > 0) totalSamples * 1000 / sampleRate else 0
}

data class SniffResult(val container: AudioContainer, val flac: FlacInfo? = null)

object AudioSniffer {
    /** How many bytes [sniff] wants (an ID3 tag with a big cover can push the audio further). */
    const val HEADER_BYTES = 256 * 1024

    fun sniff(header: ByteArray): SniffResult {
        if (header.size < 4) return SniffResult(AudioContainer.UNKNOWN)
        var offset = 0
        // ID3v2 in front of MP3 (or, rarely, FLAC).
        if (header.startsWith("ID3")) {
            if (header.size < 10) return SniffResult(AudioContainer.MP3)
            val size = ((header[6].toInt() and 0x7F) shl 21) or ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or (header[9].toInt() and 0x7F)
            val footer = if ((header[5].toInt() and 0x10) != 0) 10 else 0
            offset = 10 + size + footer
            if (offset + 4 > header.size) return SniffResult(AudioContainer.MP3) // tag bigger than what we read
            if (header.startsWith("fLaC", offset)) return SniffResult(AudioContainer.FLAC, parseFlac(header, offset))
            return if (isMpegFrame(header, offset)) SniffResult(AudioContainer.MP3) else SniffResult(AudioContainer.UNKNOWN)
        }
        if (header.startsWith("fLaC")) return SniffResult(AudioContainer.FLAC, parseFlac(header, 0))
        if (header.size >= 12 && header.startsWith("ftyp", 4)) return SniffResult(AudioContainer.MP4)
        if (header.startsWith("OggS")) {
            val opus = header.indexOf("OpusHead", limit = 512) >= 0
            return SniffResult(if (opus) AudioContainer.OGG_OPUS else AudioContainer.OGG_VORBIS)
        }
        if (header.size >= 12 && header.startsWith("RIFF") && header.startsWith("WAVE", 8)) {
            return SniffResult(AudioContainer.WAV)
        }
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        if (b0 == 0xFF && (b1 and 0xF6) == 0xF0) return SniffResult(AudioContainer.AAC_ADTS)
        if (isMpegFrame(header, 0)) return SniffResult(AudioContainer.MP3)
        if (looksLikeText(header)) return SniffResult(AudioContainer.TEXT)
        return SniffResult(AudioContainer.UNKNOWN)
    }

    /** MPEG audio frame sync with a valid layer and bitrate index. */
    private fun isMpegFrame(b: ByteArray, at: Int): Boolean {
        if (at + 4 > b.size) return false
        val h0 = b[at].toInt() and 0xFF
        val h1 = b[at + 1].toInt() and 0xFF
        val h2 = b[at + 2].toInt() and 0xFF
        if (h0 != 0xFF || (h1 and 0xE0) != 0xE0) return false
        val version = (h1 shr 3) and 0x3
        val layer = (h1 shr 1) and 0x3
        val bitrate = (h2 shr 4) and 0xF
        val rate = (h2 shr 2) and 0x3
        return version != 1 && layer != 0 && bitrate != 0 && bitrate != 0xF && rate != 3
    }

    private fun parseFlac(b: ByteArray, at: Int): FlacInfo? {
        // "fLaC", then metadata block header (4 bytes), STREAMINFO is always first (34 bytes).
        val s = at + 8
        if (s + 18 > b.size) return null
        if ((b[at + 4].toInt() and 0x7F) != 0) return null
        fun u(i: Int) = b[s + i].toLong() and 0xFF
        val sampleRate = ((u(10) shl 12) or (u(11) shl 4) or (u(12) shr 4)).toInt()
        val channels = ((u(12) shr 1) and 0x7).toInt() + 1
        val bits = (((u(12) and 0x1) shl 4) or (u(13) shr 4)).toInt() + 1
        val total = ((u(13) and 0xF) shl 32) or (u(14) shl 24) or (u(15) shl 16) or (u(16) shl 8) or u(17)
        if (sampleRate <= 0) return null
        return FlacInfo(sampleRate, channels, bits, total)
    }

    private fun looksLikeText(b: ByteArray): Boolean {
        val start = String(b, 0, minOf(b.size, 64), Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t').lowercase()
        return start.startsWith("<!doctype") || start.startsWith("<html") || start.startsWith("<?xml") ||
            start.startsWith("{") || start.startsWith("[") || start.startsWith("<head") || start.startsWith("<body")
    }

    private fun ByteArray.startsWith(ascii: String, at: Int = 0): Boolean {
        if (at < 0 || at + ascii.length > size) return false
        for (i in ascii.indices) if (this[at + i] != ascii[i].code.toByte()) return false
        return true
    }

    private fun ByteArray.indexOf(ascii: String, limit: Int): Int {
        val end = minOf(size, limit) - ascii.length
        for (i in 0..end) if (startsWith(ascii, i)) return i
        return -1
    }
}
