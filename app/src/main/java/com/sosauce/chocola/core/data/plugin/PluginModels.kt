package com.sosauce.chocola.core.data.plugin

import kotlinx.serialization.Serializable

/**
 * ByMusic loads plugins written in the MusicFree plugin format (a CommonJS module that
 * exports `platform`, `version`, `search`, `getMediaSource`, ...). ByMusic itself ships no
 * music source and no plugin: everything comes from files the user imports.
 *
 * Protocol reference: https://musicfree.catcat.work/plugin/protocol.html
 * This is an independent implementation; no MusicFree source code is used.
 */
object MusicFreeProtocol {

    /**
     * Every protocol method the runtime can call (see [PluginRuntime.call]).
     * [UI_WIRED] lists the ones ByMusic's screens already use in this version.
     */
    val METHODS = listOf(
        "search",
        "getMediaSource",
        "getMusicInfo",
        "getLyric",
        "getAlbumInfo",
        "getMusicSheetInfo",
        "getArtistWorks",
        "importMusicItem",
        "importMusicSheet",
        "getTopLists",
        "getTopListDetail",
        "getRecommendSheetTags",
        "getRecommendSheetsByTag",
        "getMusicComments"
    )

    /** Methods ByMusic's UI calls in this version. */
    val UI_WIRED = setOf("search", "getMediaSource", "getLyric", "getMusicInfo")

    /** Libraries a plugin may `require()`; everything else is reported as missing. */
    val SUPPORTED_MODULES = listOf(
        "axios", "crypto-js", "dayjs", "qs", "he", "big-integer", "cheerio", "buffer", "url"
    )

    /** MusicFree's four quality names, highest first. */
    val QUALITY_KEYS = listOf("super", "high", "standard", "low")

    const val SEARCH_MUSIC = "music"

    /**
     * Reserved ByMusic extension (not part of the MusicFree protocol). Song recognition
     * plugins are not wired up yet; the name is reserved so a later version can detect
     * them without changing the plugin format.
     */
    const val RESERVED_RECOGNITION_METHOD = "recognizeSong"
}

/** What a plugin declares about itself, read by running it once in the sandbox. */
@Serializable
data class PluginManifest(
    val platform: String,
    val version: String? = null,
    val author: String? = null,
    val srcUrl: String? = null,
    val description: String? = null,
    val appVersion: String? = null,
    val cacheControl: String? = null,
    val primaryKey: List<String>? = null,
    val supportedSearchType: List<String>? = null,
    val userVariables: List<PluginUserVariable> = emptyList(),
    val methods: List<String> = emptyList(),
    val requiredModules: List<String> = emptyList(),
    val missingModules: List<String> = emptyList()
) {
    val displayVersion: String get() = version?.takeIf { it.isNotBlank() } ?: "0.0.0"

    /** Primary key fields; MusicFree's default is `["id"]`. */
    val effectivePrimaryKey: List<String>
        get() = primaryKey?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() } ?: listOf("id")

    fun has(method: String): Boolean = method in methods

    /** Search is offered only when the plugin exports `search` and allows music search. */
    val canSearchMusic: Boolean
        get() = has("search") &&
            (supportedSearchType == null || MusicFreeProtocol.SEARCH_MUSIC in supportedSearchType)

    /** Known protocol methods this plugin implements, in protocol order. */
    val protocolMethods: List<String>
        get() = MusicFreeProtocol.METHODS.filter { it in methods }
}

@Serializable
data class PluginUserVariable(
    val key: String,
    val name: String? = null,
    val hint: String? = null
)

@Serializable
enum class PluginSourceKind { LOCAL_FILE, URL }

/** One installed plugin as stored in the app's private `plugins/index.json`. */
@Serializable
data class InstalledPlugin(
    /** Stable id used in online track ids; MusicFree identifies plugins by `platform`. */
    val id: String,
    val fileName: String,
    val manifest: PluginManifest,
    val sha256: String,
    val sizeBytes: Long,
    val sourceKind: PluginSourceKind,
    /** The URL the user entered, or the plugin's srcUrl after an update. Null for files. */
    val sourceUrl: String? = null,
    val enabled: Boolean = true,
    val installedAtMs: Long = 0,
    val userVariables: Map<String, String> = emptyMap()
)
