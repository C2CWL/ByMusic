package com.sosauce.chocola.core.data.plugin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

enum class PluginCatalogError { INVALID_JSON, MISSING_LIST, NO_VALID_LINKS }

class PluginCatalogException(val error: PluginCatalogError) : Exception()

/** Metadata from a subscription list, not from a loaded plugin. */
data class PluginCatalogEntry(val url: String, val name: String?, val version: String?)

data class PluginCatalog(val entries: List<PluginCatalogEntry>, val skippedCount: Int) {
    companion object {
        internal fun readOrNull(source: String, expectJson: Boolean): PluginCatalog? {
            val text = source.removePrefix("\uFEFF").trimStart()
            if (!expectJson && !text.startsWith('{') && !text.startsWith('[')) return null
            val parsed = try {
                Json.parseToJsonElement(text)
            } catch (_: SerializationException) {
                if (!expectJson) return null
                throw PluginCatalogException(PluginCatalogError.INVALID_JSON)
            }
            val plugins = (parsed as? JsonObject)?.get("plugins") as? JsonArray
                ?: throw PluginCatalogException(PluginCatalogError.MISSING_LIST)
            val entries = linkedMapOf<String, PluginCatalogEntry>()
            for (item in plugins) {
                val plugin = item as? JsonObject ?: continue
                val url = plugin.stringField("url") ?: continue
                if (!url.startsWith("https://", ignoreCase = true) ||
                    PluginHttpPolicy.check(PluginHttpRequest(url = url)) != null ||
                    URI(url).rawUserInfo != null
                ) continue
                entries.putIfAbsent(url, PluginCatalogEntry(url, plugin.stringField("name"), plugin.stringField("version")))
            }
            if (entries.isEmpty()) throw PluginCatalogException(PluginCatalogError.NO_VALID_LINKS)
            return PluginCatalog(entries.values.toList(), plugins.size - entries.size)
        }

        private fun JsonObject.stringField(key: String): String? = (get(key) as? JsonPrimitive)
            ?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }
    }
}

sealed interface PluginImport {
    data class Script(val candidate: PluginCandidate) : PluginImport
    data class Catalog(val catalog: PluginCatalog) : PluginImport
}
