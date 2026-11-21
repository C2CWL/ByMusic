package com.sosauce.chocola.feature.online

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.core.data.plugin.PluginManager
import com.sosauce.chocola.core.data.plugin.PluginMusicService
import com.sosauce.chocola.core.domain.model.CuteTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    val isLoadingPlugins: Boolean = true,
    /** Imported plugins, including disabled or recognition-only plugins. */
    val hasAnyPlugin: Boolean = false,
    val selectedId: String? = null,
    val query: String = "",
    val results: List<CuteTrack> = emptyList(),
    val page: Int = 0,
    val isEnd: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val submittedQuery: String? = null
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
        val visible = if (s.selectedId != null && searchable.none { it.id == s.selectedId }) {
            s.clearSearch().copy(selectedId = null)
        } else {
            s
        }
        visible.copy(plugins = searchable, hasAnyPlugin = entries.isNotEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnlineSearchUiState())

    init {
        viewModelScope.launch {
            manager.ensureLoaded()
            ui.update { it.copy(isLoadingPlugins = false) }
            manager.plugins.collect {
                val id = ui.value.selectedId
                if (id != null && id != currentPluginId()) {
                    job?.cancel()
                    ui.update { state -> state.clearSearch().copy(selectedId = null) }
                }
            }
        }
    }

    fun setQuery(query: String) {
        if (query.isBlank()) job?.cancel()
        ui.update { state ->
            if (query.isBlank()) state.clearSearch().copy(query = query) else state.copy(query = query)
        }
    }

    fun select(id: String) {
        if (currentPluginId() == id) return
        job?.cancel()
        ui.update { it.clearSearch().copy(selectedId = id) }
        if (ui.value.query.isNotBlank()) search()
    }

    fun search() {
        val pluginId = currentPluginId() ?: return
        val s = ui.value
        val query = s.query.trim()
        if (query.isEmpty() || (s.isLoading && s.selectedId == pluginId && s.submittedQuery == query)) return
        load(pluginId, query, page = 1, append = false)
    }

    fun loadMore() {
        val s = ui.value
        val query = s.submittedQuery ?: return
        val pluginId = currentPluginId() ?: return
        if (s.selectedId != pluginId || s.isLoading || s.isEnd || s.page == 0) return
        load(pluginId, query, page = s.page + 1, append = true)
    }

    fun retry() {
        val s = ui.value
        val query = s.submittedQuery ?: return
        val pluginId = currentPluginId() ?: return
        if (s.selectedId != pluginId || s.error == null || s.isLoading) return
        load(pluginId, query, page = s.page + 1, append = s.page > 0)
    }

    private fun OnlineSearchUiState.clearSearch() = copy(
        submittedQuery = null, results = emptyList(), page = 0, isEnd = true, isLoading = false, error = null
    )

    /** Reads [ui] directly so a just-made selection is seen before [state] recombines. */
    private fun currentPluginId(): String? {
        val ids = manager.plugins.value
            .filter { it.plugin.enabled && it.manifest.canSearchMusic }
            .map { it.id }
        return ui.value.selectedId?.takeIf { it in ids } ?: ids.firstOrNull()
    }

    private fun load(pluginId: String, query: String, page: Int, append: Boolean) {
        job?.cancel()
        ui.update { state ->
            val next = if (append) state else state.clearSearch()
            next.copy(isLoading = true, error = null, selectedId = pluginId, submittedQuery = query)
        }
        job = viewModelScope.launch {
            try {
                val result = service.searchMusic(pluginId, query, page)
                ensureActive()
                ui.update { s ->
                    val merged = (if (append) s.results + result.tracks else result.tracks).distinctBy { it.mediaId }
                    s.copy(
                        results = merged,
                        page = page,
                        isEnd = result.isEnd,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                ui.update { it.copy(isLoading = false, error = e.message?.takeIf { it.isNotBlank() } ?: "搜索失败") }
            }
        }
    }
}
