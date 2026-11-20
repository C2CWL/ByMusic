package com.sosauce.chocola.core.domain.online

import java.net.URI
import java.security.MessageDigest

/**
 * Turns an online track id into a real, playable URL at play time.
 *
 * Step 0 only ships [UnavailableOnlineMediaResolver]; the plugin-backed implementation
 * comes with the plugin stage. No real music source is wired in here.
 */
interface OnlineMediaResolver {
    /** Must return a URL accepted by [OnlineUrlPolicy.isAllowedMedia], or throw. */
    suspend fun resolve(request: ResolveRequest): ResolvedMedia
}

data class ResolveRequest(
    val id: OnlineMediaId,
    val quality: QualityRequest
)

data class ResolvedMedia(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val userAgent: String? = null,
    /** Tier actually served, if known. Null means "not reported / not measured yet". */
    val servedTier: QualityTier? = null,
    /** What was tried before this result, for the UI and diagnostics. */
    val attempts: List<FallbackAttempt> = emptyList()
) {
    /**
     * Headers to send with the audio request. [userAgent] is added as `User-Agent`
     * unless the plugin already set one. Headers containing CR/LF are dropped.
     */
    fun requestHeaders(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, value) in headers) {
            if (name.isBlank() || name.hasLineBreak() || value.hasLineBreak()) continue
            out[name] = value
        }
        val ua = userAgent
        if (ua != null && ua.isNotBlank() && !ua.hasLineBreak() &&
            out.keys.none { it.equals("User-Agent", ignoreCase = true) }
        ) {
            out["User-Agent"] = ua
        }
        return out
    }

    private fun String.hasLineBreak() = indexOf('\r') >= 0 || indexOf('\n') >= 0
}

class OnlineResolveException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Default until the plugin stage: online tracks cannot be resolved yet. */
object UnavailableOnlineMediaResolver : OnlineMediaResolver {
    override suspend fun resolve(request: ResolveRequest): ResolvedMedia =
        throw OnlineResolveException("No online plugin is available")
}

/** Artwork may use http(s); audio requires a valid HTTPS URL without embedded credentials. */
object OnlineUrlPolicy {
    fun isAllowedMedia(url: String): Boolean {
        if (url != url.trim()) return false
        val uri = parseWebUrl(url) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null
    }

    fun isAllowed(url: String?): Boolean = parseWebUrl(url) != null

    private fun parseWebUrl(url: String?): URI? {
        if (url.isNullOrBlank() || url.length > 8192) return null
        val uri = try {
            URI(url.trim())
        } catch (_: java.net.URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        return uri.takeIf { (scheme == "http" || scheme == "https") && !it.host.isNullOrBlank() }
    }
}

/**
 * Cache key for the playback cache. It is built from the online id, the served tier and
 * the URL's host+path (not the query, which usually carries an expiring signature).
 * Different files therefore never share cached bytes, while re-signed URLs of the same
 * file still hit the cache.
 */
object OnlineCacheKey {
    fun of(id: OnlineMediaId, servedTier: QualityTier?, url: String): String {
        val location = try {
            val u = URI(url)
            (u.host.orEmpty().lowercase()) + (u.rawPath.orEmpty())
        } catch (_: Exception) {
            url
        }
        return id.encode() + "#" + (servedTier?.name ?: "unknown") + "#" + sha1(location).take(16)
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Small thread-safe TTL + LRU memo so seeks/reopens don't call the plugin again. */
class ResolvedMediaMemo(
    private val ttlMillis: Long = 10 * 60 * 1000L,
    private val maxEntries: Int = 64,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private class Entry(val value: ResolvedMedia, val storedAt: Long)

    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: String): ResolvedMedia? {
        val entry = map[key] ?: return null
        if (clock() - entry.storedAt >= ttlMillis) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: ResolvedMedia) {
        map[key] = Entry(value, clock())
    }

    @Synchronized
    fun invalidate(key: String) {
        map.remove(key)
    }

    companion object {
        fun keyOf(request: ResolveRequest): String =
            request.id.encode() + "|" + request.quality.chain.joinToString(",") { it.name }
    }
}
