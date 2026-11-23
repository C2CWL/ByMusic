package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.data.plugin.MusicFreeItems.string
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.online.OnlineLyricsProvider
import com.sosauce.chocola.core.domain.online.OnlineLyricsText
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.OnlineMediaResolver
import com.sosauce.chocola.core.domain.online.OnlineResolveException
import com.sosauce.chocola.core.domain.online.OnlineUrlPolicy
import com.sosauce.chocola.core.domain.online.QualityTier
import com.sosauce.chocola.core.domain.online.ResolveRequest
import com.sosauce.chocola.core.domain.online.ResolvedMedia
import com.sosauce.chocola.core.domain.online.tryInOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Base64

/** One page of music search results from one plugin. */
data class OnlineSearchPage(
    val pluginId: String,
    val tracks: List<CuteTrack>,
    val isEnd: Boolean
)

/** What getMediaSource returned for one quality key. */
data class PluginMediaSource(
    val url: String,
    val headers: Map<String, String>,
    val userAgent: String?,
    val quality: String?
)

/**
 * The MusicFree calls ByMusic's screens and player use: search (music), getMediaSource,
 * getLyric and getMusicInfo. Other protocol methods are callable through
 * [PluginManager.call] and get their screens in later steps.
 */
class PluginMusicService(
    private val manager: PluginManager,
    private val items: OnlineMusicItemStore
) {

    suspend fun searchMusic(pluginId: String, query: String, page: Int): OnlineSearchPage {
        val runtime = manager.runtime(pluginId)
        val manifest = runtime.manifest
        if (!manifest.canSearchMusic) throw PluginException(PluginErrorType.METHOD_MISSING, "这个插件不支持搜歌")
        val result = runtime.call(
            "search",
            listOf(JsonPrimitive(query), JsonPrimitive(page), JsonPrimitive("music"))
        ) as? JsonObject ?: return OnlineSearchPage(pluginId, emptyList(), true)
        val data = result["data"] as? kotlinx.serialization.json.JsonArray
        val remembered = LinkedHashMap<OnlineMediaId, JsonObject>()
        val tracks = data.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val withPlatform = MusicFreeItems.withPlatform(item, manifest.platform)
            val track = MusicFreeItems.toTrack(pluginId, manifest.effectivePrimaryKey, withPlatform)
                ?: return@mapNotNull null
            OnlineMediaId.parse(track.mediaId)?.let { remembered[it] = withPlatform }
            track
        }.distinctBy { it.mediaId }
        withContext(Dispatchers.IO) { items.putAll(remembered) }
        val isEnd = (result["isEnd"] as? JsonPrimitive)?.booleanOrNull ?: true
        return OnlineSearchPage(pluginId, tracks, isEnd || tracks.isEmpty())
    }

    /** Loads the plugin if needed; throws when it is missing, disabled or fails to load. */
    suspend fun ensureUsable(pluginId: String) {
        manager.runtime(pluginId)
    }

    /** The item to hand back to the plugin: the one it returned earlier, or a minimal one. */
    suspend fun itemFor(id: OnlineMediaId, platform: String): JsonObject {
        val stored = withContext(Dispatchers.IO) { items.get(id) }
        if (stored != null) return stored
        return JsonObject(mapOf("id" to JsonPrimitive(id.trackId), "platform" to JsonPrimitive(platform)))
    }

    suspend fun mediaSource(id: OnlineMediaId, qualityKey: String, timeoutMillis: Long): PluginMediaSource? {
        val runtime = manager.runtime(id.pluginId)
        val item = itemFor(id, runtime.manifest.platform)
        val quality = (item["qualities"] as? JsonObject)?.get(qualityKey) as? JsonObject
        val fallback = acceptedSource(
            sequenceOf(quality?.string("url"), item.string("url")).firstOrNull { !it.isNullOrBlank() },
            emptyMap(), null, null
        )
        if (!runtime.manifest.has("getMediaSource")) return fallback

        val raw = runtime.call("getMediaSource", listOf(item, JsonPrimitive(qualityKey)), timeoutMillis)
        val result = raw as? JsonObject
        val url = ((raw as? JsonPrimitive) ?: (result?.get("url") as? JsonPrimitive))
            ?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return fallback
        val headers = (result?.get("headers") as? JsonObject).orEmpty()
            .mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }
            .toMap()
        return acceptedSource(url, headers, result?.string("userAgent"), result?.string("quality")) ?: fallback
    }

    /** getLyric → rawLrc + translation; without getLyric, the item's own rawLrc. */
    suspend fun lyrics(id: OnlineMediaId): OnlineLyricsText? {
        val runtime = manager.runtime(id.pluginId)
        val item = itemFor(id, runtime.manifest.platform)
        val fallback = item.string("rawLrc")?.let { OnlineLyricsText(it, item.string("translation")) }
        if (!runtime.manifest.has("getLyric")) return fallback
        val result = runtime.call("getLyric", listOf(item)) as? JsonObject ?: return fallback
        val raw = result.string("rawLrc") ?: return fallback
        return OnlineLyricsText(raw, result.string("translation"))
    }

    /** getMusicInfo: merges the extra fields into the remembered item. */
    suspend fun refreshInfo(id: OnlineMediaId): JsonObject? {
        val runtime = manager.runtime(id.pluginId)
        if (!runtime.manifest.has("getMusicInfo")) return null
        val item = itemFor(id, runtime.manifest.platform)
        val extra = runtime.call("getMusicInfo", listOf(item)) as? JsonObject ?: return null
        val merged = JsonObject(item + extra + ("platform" to JsonPrimitive(runtime.manifest.platform)))
        withContext(Dispatchers.IO) { items.putAll(mapOf(id to merged)) }
        return merged
    }
}

/**
 * Connects the player's resolve step (#36) to the plugin's getMediaSource. Quality is
 * requested from the top of [ResolveRequest.quality] (by default the highest tier) and
 * steps down one tier at a time. Tiers that share a MusicFree key are asked only once.
 */
class PluginOnlineMediaResolver(
    private val service: PluginMusicService,
    private val perTierTimeoutMillis: Long = PER_TIER_TIMEOUT_MS
) : OnlineMediaResolver {

    override suspend fun resolve(request: ResolveRequest): ResolvedMedia {
        // A missing, disabled or broken plugin fails right away instead of once per tier.
        try {
            service.ensureUsable(request.id.pluginId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnlineResolveException(e.message ?: "插件不可用", e)
        }

        val asked = HashMap<String, Result<PluginMediaSource?>>()
        var lastError: String? = null
        val outcome = request.quality.tryInOrder { tier ->
            val key = tier.musicFreeKey
            val result = asked.getOrPut(key) {
                try {
                    Result.success(service.mediaSource(request.id, key, perTierTimeoutMillis))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e.message
                    Result.failure(e)
                }
            }
            val source = result.getOrThrow() ?: return@tryInOrder null
            if (!OnlineUrlPolicy.isAllowedMedia(source.url) || PluginNetwork.parseUrl(source.url) == null) {
                val reason = "插件没有提供有效的 HTTPS 音频链接"
                lastError = reason
                throw OnlineResolveException(reason)
            }
            source
        } ?: throw OnlineResolveException(
            "插件没有给出可播放的地址" + (lastError?.let { "（$it）" } ?: "")
        )

        val source = outcome.value
        return ResolvedMedia(
            url = source.url,
            headers = source.headers,
            userAgent = source.userAgent,
            // Only what the plugin reports; real quality is measured in a later step.
            servedTier = QualityTier.fromMusicFreeKey(source.quality),
            attempts = outcome.attempts
        )
    }

    companion object {
        const val PER_TIER_TIMEOUT_MS = 8_000L

        /**
         * MusicFree strips `user:password@` from a plugin URL and sends it as
         * `Authorization: Basic`. ByMusic otherwise rejects any URL with userinfo,
         * so the same source plays in MusicFree and fails here.
         */
        internal fun normalizeMusicFreeSourceUrl(raw: String): NormalizedSourceUrl? {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
            val userInfo = uri.rawUserInfo?.takeIf { it.isNotEmpty() } ?: return NormalizedSourceUrl(uri.toString(), null)
            val decoded = runCatching { URI(null, userInfo, null).path }.getOrNull() ?: return null
            val separator = decoded.indexOf(':')
            if (separator <= 0 || separator == decoded.lastIndex) return null
            val token = Base64.getEncoder().encodeToString(
                decoded.toByteArray(StandardCharsets.UTF_8)
            )
            val stripped = stripUserInfo(raw.trim()) ?: return null
            return NormalizedSourceUrl(stripped, "Basic $token")
        }

        private fun acceptedSource(
            rawUrl: String?,
            headers: Map<String, String>,
            userAgent: String?,
            quality: String?
        ): PluginMediaSource? {
            if (rawUrl.isNullOrBlank()) return null
            val normalized = normalizeMusicFreeSourceUrl(rawUrl) ?: return null
            if (!OnlineUrlPolicy.isAllowedMedia(normalized.url) || PluginNetwork.parseUrl(normalized.url) == null) {
                return null
            }
            val merged = buildMap {
                putAll(headers)
                val auth = normalized.authorization
                if (auth != null && keys.none { it.equals("Authorization", ignoreCase = true) }) {
                    put("Authorization", auth)
                }
            }
            return PluginMediaSource(normalized.url, merged, userAgent, quality)
        }

        /** Removes `user:password@` without re-encoding the path or query. */
        private fun stripUserInfo(raw: String): String? {
            val scheme = raw.indexOf("://")
            if (scheme < 0) return null
            val authority = scheme + 3
            val at = raw.indexOf('@', authority)
            val end = raw.indexOfAny(charArrayOf('/', '?', '#'), authority).let { if (it < 0) raw.length else it }
            if (at < 0 || at > end) return null
            return raw.removeRange(authority, at + 1)
        }
    }
}

internal data class NormalizedSourceUrl(val url: String, val authorization: String?)

class PluginOnlineLyricsProvider(private val service: PluginMusicService) : OnlineLyricsProvider {
    override suspend fun lyrics(id: OnlineMediaId): OnlineLyricsText? = try {
        service.lyrics(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
