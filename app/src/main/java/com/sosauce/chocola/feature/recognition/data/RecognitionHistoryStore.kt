package com.sosauce.chocola.feature.recognition.data

import com.sosauce.chocola.feature.recognition.domain.FailureReason
import com.sosauce.chocola.feature.recognition.domain.RecognizedSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
enum class RecognitionHistoryStatus { MATCH, NO_MATCH, NETWORK, PLUGIN_ERROR }

/** One line of history. Text only: the recording itself is never stored. */
@Serializable
data class RecognitionHistoryEntry(
    val id: Long,
    val timestampMs: Long,
    val status: RecognitionHistoryStatus,
    val pluginId: String,
    val pluginName: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val platform: String? = null,
    val platformSongId: String? = null
) {
    companion object {
        fun success(song: RecognizedSong, timestampMs: Long) = RecognitionHistoryEntry(
            id = timestampMs,
            timestampMs = timestampMs,
            status = RecognitionHistoryStatus.MATCH,
            pluginId = song.sourcePluginId,
            pluginName = song.sourcePluginName,
            title = song.title,
            artist = song.artist,
            album = song.album,
            artworkUrl = song.artworkUrl,
            platform = song.platform,
            platformSongId = song.platformSongId
        )

        /** Recording problems are not plugin results, so they are not logged. */
        fun failure(
            reason: FailureReason,
            pluginId: String,
            pluginName: String,
            timestampMs: Long
        ): RecognitionHistoryEntry? {
            val status = when (reason) {
                FailureReason.NoMatch -> RecognitionHistoryStatus.NO_MATCH
                FailureReason.Network -> RecognitionHistoryStatus.NETWORK
                is FailureReason.Plugin -> RecognitionHistoryStatus.PLUGIN_ERROR
                FailureReason.RecordingFailed -> return null
            }
            return RecognitionHistoryEntry(
                id = timestampMs,
                timestampMs = timestampMs,
                status = status,
                pluginId = pluginId,
                pluginName = pluginName
            )
        }
    }
}

/**
 * History kept only on this device, in one small JSON file in the app's private storage.
 * Newest first, capped at [maxEntries]. Corrupt files are treated as empty.
 */
class RecognitionHistoryStore(
    private val file: File,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var loaded = false
    private val _entries = MutableStateFlow<List<RecognitionHistoryEntry>>(emptyList())
    val entries: StateFlow<List<RecognitionHistoryEntry>> = _entries.asStateFlow()

    /** Blocking file read; call off the main thread. */
    fun load(): List<RecognitionHistoryEntry> = synchronized(lock) {
        if (!loaded) {
            _entries.value = readFile()
            loaded = true
        }
        _entries.value
    }

    fun add(entry: RecognitionHistoryEntry) = synchronized(lock) {
        load()
        var id = entry.id
        while (_entries.value.any { it.id == id }) id++
        val updated = (listOf(entry.copy(id = id)) + _entries.value).take(maxEntries)
        writeFile(updated)
        _entries.value = updated
    }

    fun clear() = synchronized(lock) {
        _entries.value = emptyList()
        loaded = true
        file.delete()
    }

    private fun readFile(): List<RecognitionHistoryEntry> {
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString<List<RecognitionHistoryEntry>>(file.readText())
        }.getOrDefault(emptyList()).sortedByDescending { it.timestampMs }.take(maxEntries)
    }

    private fun writeFile(list: List<RecognitionHistoryEntry>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(list))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        const val FILE_NAME = "recognition_history.json"
        const val DEFAULT_MAX_ENTRIES = 200
    }
}
