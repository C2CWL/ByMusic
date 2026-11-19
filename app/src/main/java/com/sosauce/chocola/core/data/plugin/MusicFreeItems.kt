package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.OnlineTracks
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Helpers for MusicFree media items (IMusicItem etc.), which are plain JSON objects. */
object MusicFreeItems {

    fun JsonObject.string(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }

    /** `duration` is seconds in the protocol; some plugins send strings. */
    fun JsonObject.durationMillis(): Long {
        val p = this["duration"] as? JsonPrimitive ?: return 0
        val seconds = p.doubleOrNull ?: p.contentOrNull?.toDoubleOrNull() ?: return 0
        return if (seconds.isFinite() && seconds > 0) (seconds * 1000).toLong() else 0
    }

    /**
     * Track id inside `online:<plugin>:<id>`. With the default primary key `["id"]` it is
     * the item's id; with more keys it is a JSON array of their values so items that only
     * differ in a secondary key do not collide.
     */
    fun trackIdOf(item: JsonObject, primaryKey: List<String>): String? {
        val keys = primaryKey.ifEmpty { listOf("id") }
        if (keys == listOf("id")) return item.string("id")
        if (item.string("id") == null) return null
        val values = keys.map { k -> (item[k] as? JsonPrimitive) ?: JsonNull }
        return Json.encodeToString(JsonArray.serializer(), JsonArray(values))
    }

    /** Builds the player track for a search result; null when the item has no id or title. */
    fun toTrack(pluginId: String, primaryKey: List<String>, item: JsonObject): CuteTrack? {
        val trackId = trackIdOf(item, primaryKey) ?: return null
        val title = item.string("title") ?: return null
        return OnlineTracks.create(
            id = OnlineMediaId(pluginId, trackId),
            title = title,
            artist = item.string("artist").orEmpty(),
            album = item.string("album").orEmpty(),
            artworkUrl = item.string("artwork"),
            durationMillis = item.durationMillis()
        )
    }

    /** MusicFree adds `platform` to every item a plugin returns. */
    fun withPlatform(item: JsonObject, platform: String): JsonObject =
        JsonObject(item + ("platform" to JsonPrimitive(platform)))
}

/**
 * Remembers the full media item a plugin returned for each online track, because plugins
 * need their own extra fields (not just the id) in getMediaSource/getLyric. Stored in the
 * app's private plugins directory, most recently used first, capped.
 */
class OnlineMusicItemStore(
    private val file: File?,
    private val maxEntries: Int = 3000
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var loaded = false
    private val map = object : LinkedHashMap<String, JsonObject>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?): Boolean =
            size > maxEntries
    }

    fun get(id: OnlineMediaId): JsonObject? = synchronized(lock) {
        ensureLoaded()
        map[id.encode()]
    }

    fun putAll(items: Map<OnlineMediaId, JsonObject>) = synchronized(lock) {
        if (items.isEmpty()) return
        ensureLoaded()
        for ((id, item) in items) map[id.encode()] = item
        persist()
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val f = file ?: return
        if (!f.isFile) return
        runCatching {
            val decoded = json.decodeFromString(MapSerializer(String.serializer(), JsonElement.serializer()), f.readText())
            for ((k, v) in decoded) runCatching { map[k] = v.jsonObject }
        }
    }

    private fun persist() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(MapSerializer(String.serializer(), JsonObject.serializer()), LinkedHashMap(map)))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "online_items.json"
    }
}
