package com.sosauce.chocola.core.data.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The download list (`files/downloads/tasks.json`). The real work is scheduled with
 * WorkManager; this file only remembers what to show and what the worker should do.
 * Newest first, capped at [maxEntries] (finished entries are dropped first).
 */
class DownloadStore(
    private val file: File?,
    private val maxEntries: Int = 500
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Any()
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    @Volatile
    private var loaded = false

    fun load() {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            val f = file ?: return
            if (!f.isFile) return
            _tasks.value = runCatching {
                json.decodeFromString(ListSerializer(DownloadTask.serializer()), f.readText())
            }.getOrDefault(emptyList())
        }
    }

    fun get(id: String): DownloadTask? {
        load()
        return _tasks.value.firstOrNull { it.id == id }
    }

    fun put(task: DownloadTask): Unit = mutate { list -> listOf(task) + list.filterNot { it.id == task.id } }

    /** Applies [change] to the task if it exists; returns the new value. */
    fun update(id: String, change: (DownloadTask) -> DownloadTask): DownloadTask? {
        var out: DownloadTask? = null
        mutate { list ->
            list.map { if (it.id == id) change(it).also { n -> out = n } else it }
        }
        return out
    }

    fun remove(ids: Collection<String>): Unit = mutate { list -> list.filterNot { it.id in ids } }

    /** Progress updates are frequent; they are kept in memory and written with the next real change. */
    fun updateProgress(id: String, done: Long, total: Long, speed: Long) {
        synchronized(lock) {
            load()
            _tasks.value = _tasks.value.map {
                if (it.id == id) it.copy(bytesDone = done, bytesTotal = total, bytesPerSecond = speed) else it
            }
        }
    }

    private fun mutate(change: (List<DownloadTask>) -> List<DownloadTask>): Unit = synchronized(lock) {
        load()
        var next = change(_tasks.value)
        if (next.size > maxEntries) {
            val overflow = next.size - maxEntries
            val removable = next.filter { !it.status.isActive }.takeLast(overflow).map { it.id }.toSet()
            next = next.filterNot { it.id in removable }
        }
        _tasks.value = next
        persist(next)
    }

    private fun persist(list: List<DownloadTask>) {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(DownloadTask.serializer()), list))
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        const val DIR_NAME = "downloads"
        const val FILE_NAME = "tasks.json"
    }
}
