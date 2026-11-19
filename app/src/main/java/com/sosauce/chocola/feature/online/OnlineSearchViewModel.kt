package com.sosauce.chocola.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.core.data.plugin.PluginManager
import com.sosauce.chocola.core.data.plugin.PluginMusicService
import com.sosauce.chocola.core.domain.model.CuteTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A plugin that can be searched (it exports `search` and allows music search). */
data class SearchablePlugin(val id: String, val name: String)

data class OnlineSearchUiState(
    /** Empty means: show the "import a plugin first" state, no search entry at all. */
    val plugins: List<SearchablePlugin> = emptyList(),
    /** Imported and enabled plugins, searchable or not. */
    val hasAnyPlugin: Boolean = false,
    val selectedId: String? = null,
    val query: String = "",
    val results: List<CuteTrack> = emptyList(),
    val page: Int = 0,
    val isEnd: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false
) {
    val selected: SearchablePlugin?
        get() = plugins.firstOrNull { it.id == selectedId } ?: plugins.firstOrNull()
}

class OnlineSearchViewModel(
    private val manager: PluginManager,
    private val service: PluginMusicService
) : ViewModel() {

    private val ui = MutableStateFlow(OnlineSearchUiState())
    private var job: Job? = null

    val state: StateFlow<OnlineSearchUiState> = combine(ui, manager.plugins) { s, entries ->
        val enabled = entries.filter { it.plugin.enabled }
        val searchable = enabled
            .filter { it.manifest.canSearchMusic }
            .map { SearchablePlugin(it.id, it.manifest.platform) }
        s.copy(plugins = searchable, hasAnyPlugin = enabled.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineSearchUiState())

    init {
        viewModelScope.launch { manager.ensureLoaded() }
    }

    fun setQuery(query: String) = ui.update { it.copy(query = query) }

    fun select(id: String) {
        if (currentPluginId() == id) return
        ui.update { it.copy(selectedId = id, results = emptyList(), page = 0, isEnd = true, error = null, searched = false) }
        if (ui.value.query.isNotBlank()) search()
    }

    fun search() {
        val pluginId = currentPluginId() ?: return
        val query = ui.value.query.trim()
        if (query.isEmpty()) return
        load(pluginId, query, page = 1, append = false)
    }

    fun loadMore() {
        val s = ui.value
        val pluginId = currentPluginId() ?: return
        if (s.isLoading || s.isEnd || s.page == 0) return
        load(pluginId, s.query.trim(), page = s.page + 1, append = true)
    }

    /** Reads [ui] directly so a just-made selection is seen before [state] recombines. */
    private fun currentPluginId(): String? {
        val ids = manager.plugins.value
            .filter { it.plugin.enabled && it.manifest.canSearchMusic }
            .map { it.id }
        return ui.value.selectedId?.takeIf { it in ids } ?: ids.firstOrNull()
    }

    private fun load(pluginId: String, query: String, page: Int, append: Boolean) {
        job?.cancel()
        ui.update { it.copy(isLoading = true, error = null, selectedId = pluginId) }
        job = viewModelScope.launch {
            try {
                val result = service.searchMusic(pluginId, query, page)
                ui.update { s ->
                    val merged = if (append) (s.results + result.tracks).distinctBy { it.mediaId } else result.tracks
                    s.copy(
                        results = merged,
                        page = page,
                        isEnd = result.isEnd,
                        isLoading = false,
                        searched = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ui.update { it.copy(isLoading = false, error = e.message ?: "搜索失败", searched = true) }
            }
        }
    }
}
