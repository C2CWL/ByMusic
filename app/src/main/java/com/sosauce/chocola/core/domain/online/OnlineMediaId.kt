package com.sosauce.chocola.core.domain.online

/**
 * Identifies one track that comes from an online plugin.
 *
 * Text form (used as Media3 mediaId): `online:<plugin>:<trackId>`
 * - `<plugin>` is percent-encoded, so it may contain ':' or '%'.
 * - `<trackId>` is everything after the second ':' and is kept as-is, so it may contain ':'.
 *
 * Local MediaStore tracks use plain numeric ids, which never start with `online:`,
 * so the two kinds can never be confused.
 *
 * Pure Kotlin on purpose: no android.* types, so it is covered by JVM unit tests.
 */
data class OnlineMediaId(
    val pluginId: String,
    val trackId: String
) {
    init {
        require(pluginId.isNotEmpty()) { "pluginId must not be empty" }
        require(trackId.isNotEmpty()) { "trackId must not be empty" }
    }

    fun encode(): String = PREFIX + PercentCodec.encode(pluginId) + ":" + trackId

    override fun toString(): String = encode()

    companion object {
        const val PREFIX = "online:"

        /** Cheap check usable on every mediaId; never throws. */
        fun isOnline(mediaId: String?): Boolean = parse(mediaId) != null

        /** Returns null for anything that is not a well-formed online id (e.g. local ids). */
        fun parse(mediaId: String?): OnlineMediaId? {
            if (mediaId == null || !mediaId.startsWith(PREFIX)) return null
            val rest = mediaId.substring(PREFIX.length)
            val sep = rest.indexOf(':')
            if (sep <= 0 || sep == rest.length - 1) return null
            val plugin = PercentCodec.decode(rest.substring(0, sep)) ?: return null
            val track = rest.substring(sep + 1)
            if (plugin.isEmpty() || track.isEmpty()) return null
            return OnlineMediaId(plugin, track)
        }
    }
}

/**
 * Playback URI given to Media3 for online tracks. It is an opaque URI without a path,
 * so ExoPlayer's content-type sniffing by file extension never kicks in, and it is
 * replaced by the real URL inside ResolvingDataSource right before loading.
 */
object OnlinePlaybackUri {
    const val SCHEME = "bymusic-online"
    private const val PREFIX = "$SCHEME:"

    fun of(id: OnlineMediaId): String = PREFIX + PercentCodec.encode(id.encode())

    /** Returns null for every non-online URI (content://, file://, plain paths, http...). */
    fun parse(uri: String?): OnlineMediaId? {
        if (uri == null || !uri.startsWith(PREFIX)) return null
        val decoded = PercentCodec.decode(uri.substring(PREFIX.length)) ?: return null
        return OnlineMediaId.parse(decoded)
    }
}

/** RFC 3986 style percent-encoding of UTF-8 bytes; only unreserved chars stay raw. */
internal object PercentCodec {
    private const val HEX = "0123456789ABCDEF"

    private fun isUnreserved(b: Int): Boolean =
        (b in 'A'.code..'Z'.code) || (b in 'a'.code..'z'.code) || (b in '0'.code..'9'.code) ||
            b == '-'.code || b == '.'.code || b == '_'.code || b == '~'.code

    fun encode(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size)
        for (byte in bytes) {
            val b = byte.toInt() and 0xFF
            if (isUnreserved(b)) {
                sb.append(b.toChar())
            } else {
                sb.append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
        return sb.toString()
    }

    /** Returns null on malformed input instead of throwing. */
    fun decode(value: String): String? {
        val out = java.io.ByteArrayOutputStream(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%') {
                if (i + 2 >= value.length) return null
                val hi = Character.digit(value[i + 1], 16)
                val lo = Character.digit(value[i + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write((hi shl 4) or lo)
                i += 3
            } else {
                val bytes = c.toString().toByteArray(Charsets.UTF_8)
                out.write(bytes, 0, bytes.size)
                i++
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }
}
