package com.sosauce.chocola.feature.recognition.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Imported plugins that declare song recognition. */
interface RecognitionPluginRegistry {
    val plugins: StateFlow<List<SongRecognitionPlugin>>

    fun find(id: String): SongRecognitionPlugin? = plugins.value.firstOrNull { it.info.id == id }
}

/**
 * The only registry in this version. There is no plugin runtime yet (it arrives with the
 * online-music plugin stage), so the list is always empty and no recognition entry is shown.
 * Deliberately no built-in service and no keys.
 */
object EmptyRecognitionPluginRegistry : RecognitionPluginRegistry {
    private val empty = MutableStateFlow<List<SongRecognitionPlugin>>(emptyList())
    override val plugins: StateFlow<List<SongRecognitionPlugin>> = empty.asStateFlow()
}

object RecognitionEntryPolicy {
    /** The recognition button and the "听歌识曲" group only appear when a plugin declares it. */
    fun isEntryVisible(plugins: List<RecognitionPluginInfo>): Boolean = plugins.isNotEmpty()
}
