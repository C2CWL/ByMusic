package com.sosauce.chocola.feature.plugins.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.plugin.PluginCandidate
import com.sosauce.chocola.core.data.plugin.PluginCatalog
import com.sosauce.chocola.core.data.plugin.PluginCatalogEntry
import com.sosauce.chocola.core.data.plugin.PluginCatalogError
import com.sosauce.chocola.core.data.plugin.PluginCatalogException
import com.sosauce.chocola.core.data.plugin.PluginEntry
import com.sosauce.chocola.core.data.plugin.PluginFileReader
import com.sosauce.chocola.core.data.plugin.PluginImport
import com.sosauce.chocola.core.data.plugin.PluginManager
import com.sosauce.chocola.core.data.plugin.PluginSubscriptionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImportTab { FILE, LINK }

/** The import / update sheet. [candidate] is set once the plugin was read (not installed yet). */
data class ImportSheetState(
    val tab: ImportTab = ImportTab.FILE,
    val url: String = "",
    val subscriptions: List<String> = emptyList(),
    val isReading: Boolean = false,
    val error: String? = null,
    val candidate: PluginCandidate? = null,
    val catalog: PluginCatalog? = null,
    val riskAccepted: Boolean = false,
    val isInstalling: Boolean = false,
    /** True when opened from "检查更新" of an installed plugin. */
    val isUpdate: Boolean = false
)

data class PluginsUiState(
    val plugins: List<PluginEntry> = emptyList(),
    val sheet: ImportSheetState? = null,
    /** Plugin id → "checking for update" spinner. */
    val checkingUpdate: Set<String> = emptySet(),
    val codeViewer: CodeViewerState? = null,
    val variablesEditor: VariablesEditorState? = null,
    val confirmDelete: PluginEntry? = null
)

data class CodeViewerState(val title: String, val code: String)

data class VariablesEditorState(val plugin: PluginEntry, val values: Map<String, String>)

sealed interface PluginsEvent {
    data class Message(val text: String) : PluginsEvent
}

class PluginsViewModel(
    private val application: Application,
    private val manager: PluginManager
) : AndroidViewModel(application) {

    private val ui = MutableStateFlow(PluginsUiState())
    private val eventChannel = Channel<PluginsEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var readJob: Job? = null
    private val subscriptions = PluginSubscriptionStore(application)

    val state: StateFlow<PluginsUiState> = combine(ui, manager.plugins) { s, plugins ->
        s.copy(plugins = plugins.sortedBy { it.plugin.installedAtMs })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PluginsUiState())

    init {
        viewModelScope.launch { manager.ensureLoaded() }
    }

    fun openImport() = ui.update { it.copy(sheet = ImportSheetState(subscriptions = subscriptions.list())) }

    fun closeSheet() {
        if (ui.value.sheet?.isInstalling == true) return
        readJob?.cancel()
        ui.update { it.copy(sheet = null) }
    }

    fun selectTab(tab: ImportTab) {
        val sheet = ui.value.sheet ?: return
        if (sheet.tab == tab || sheet.isInstalling) return
        readJob?.cancel()
        updateSheet {
            it.copy(tab = tab, isReading = false, error = null, candidate = null, catalog = null, riskAccepted = false)
        }
    }

    fun setUrl(url: String) = updateSheet { it.copy(url = url, error = null) }

    fun setRiskAccepted(accepted: Boolean) = updateSheet { it.copy(riskAccepted = accepted) }

    fun readFile(uri: Uri) = read {
        val (source, isJson) = withContext(Dispatchers.IO) {
            PluginFileReader.read(application, uri) to PluginFileReader.hasJsonExtension(application, uri)
        }
        manager.readImportFile(source, isJson)
    }

    fun readLink() {
        val url = ui.value.sheet?.url?.trim().orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) {
            updateSheet { it.copy(error = application.getString(R.string.plugins_url_required)) }
            return
        }
        read { manager.readImportUrl(url) }
    }

    fun addSubscription(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) {
            updateSheet { it.copy(error = application.getString(R.string.plugins_url_required)) }
            return
        }
        subscriptions.add(clean)
        updateSheet { it.copy(url = clean, subscriptions = subscriptions.list(), error = null) }
    }

    fun removeSubscription(url: String) {
        subscriptions.remove(url)
        updateSheet { it.copy(subscriptions = subscriptions.list()) }
    }

    fun refreshSubscription(url: String) {
        updateSheet { it.copy(url = url, error = null) }
        read { manager.readImportUrl(url) }
    }

    fun readCatalogEntry(entry: PluginCatalogEntry) = read(keepCatalog = true) {
        PluginImport.Script(manager.inspectFromUrl(entry.url))
    }

    fun resetImport() = updateSheet { it.copy(catalog = null, error = null) }

    fun backToCatalog() = updateSheet { it.copy(candidate = null, error = null, riskAccepted = false) }

    private fun read(keepCatalog: Boolean = false, block: suspend () -> PluginImport) {
        val sheet = ui.value.sheet ?: return
        if (sheet.isInstalling) return
        readJob?.cancel()
        updateSheet {
            it.copy(
                isReading = true, error = null, candidate = null,
                catalog = if (keepCatalog) it.catalog else null, riskAccepted = false
            )
        }
        readJob = viewModelScope.launch {
            try {
                when (val imported = block()) {
                    is PluginImport.Script -> updateSheet { it.copy(isReading = false, candidate = imported.candidate) }
                    is PluginImport.Catalog -> {
                        if (sheet.url.startsWith("https://", ignoreCase = true)) subscriptions.add(sheet.url)
                        updateSheet {
                            it.copy(isReading = false, catalog = imported.catalog, subscriptions = subscriptions.list())
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PluginCatalogException) {
                val message = when (e.error) {
                    PluginCatalogError.INVALID_JSON -> R.string.plugins_catalog_invalid_json
                    PluginCatalogError.MISSING_LIST -> R.string.plugins_catalog_missing_list
                    PluginCatalogError.NO_VALID_LINKS -> R.string.plugins_catalog_no_links
                }
                updateSheet { it.copy(isReading = false, error = application.getString(message)) }
            } catch (e: Exception) {
                updateSheet { it.copy(isReading = false, error = e.message ?: "读取失败") }
            }
        }
    }

    fun install() {
        val sheet = ui.value.sheet ?: return
        val candidate = sheet.candidate ?: return
        if (!sheet.riskAccepted || sheet.isInstalling) return
        updateSheet { it.copy(isInstalling = true) }
        viewModelScope.launch {
            try {
                manager.install(candidate)
                if (sheet.catalog == null) {
                    ui.update { it.copy(sheet = null) }
                } else {
                    updateSheet { it.copy(candidate = null, error = null, riskAccepted = false, isInstalling = false) }
                }
                eventChannel.send(PluginsEvent.Message("已安装：${candidate.manifest.platform}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateSheet { it.copy(isInstalling = false, error = e.message ?: "安装失败") }
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { manager.setEnabled(id, enabled) }
    }

    fun askDelete(entry: PluginEntry) = ui.update { it.copy(confirmDelete = entry) }

    fun dismissDelete() = ui.update { it.copy(confirmDelete = null) }

    fun confirmDelete() {
        val entry = ui.value.confirmDelete ?: return
        ui.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            manager.delete(entry.id)
            eventChannel.send(PluginsEvent.Message("已删除：${entry.manifest.platform}"))
        }
    }

    /** Never installs by itself: a newer version opens the same confirm sheet as an import. */
    fun checkUpdate(entry: PluginEntry) {
        if (entry.id in ui.value.checkingUpdate) return
        ui.update { it.copy(checkingUpdate = it.checkingUpdate + entry.id) }
        viewModelScope.launch {
            try {
                val candidate = manager.checkUpdate(entry.id)
                if (candidate == null) {
                    eventChannel.send(PluginsEvent.Message("已经是最新版本（${entry.manifest.displayVersion}）"))
                } else {
                    ui.update {
                        it.copy(sheet = ImportSheetState(tab = ImportTab.LINK, url = candidate.sourceUrl.orEmpty(), candidate = candidate, isUpdate = true))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.send(PluginsEvent.Message(e.message ?: "检查更新失败"))
            } finally {
                ui.update { it.copy(checkingUpdate = it.checkingUpdate - entry.id) }
            }
        }
    }

    fun viewCode(entry: PluginEntry) {
        viewModelScope.launch {
            val code = manager.readCode(entry.id)
            if (code == null) {
                eventChannel.send(PluginsEvent.Message("插件文件丢失"))
            } else {
                // Long files are cut for display only.
                val shown = if (code.length > MAX_CODE_CHARS) code.take(MAX_CODE_CHARS) + "\n\n……（只显示前 ${MAX_CODE_CHARS / 1000}K 字）" else code
                ui.update { it.copy(codeViewer = CodeViewerState(entry.manifest.platform, shown)) }
            }
        }
    }

    fun closeCode() = ui.update { it.copy(codeViewer = null) }

    fun editVariables(entry: PluginEntry) = ui.update {
        it.copy(variablesEditor = VariablesEditorState(entry, entry.plugin.userVariables))
    }

    fun setVariable(key: String, value: String) = ui.update { s ->
        val editor = s.variablesEditor ?: return@update s
        s.copy(variablesEditor = editor.copy(values = editor.values + (key to value)))
    }

    fun closeVariables() = ui.update { it.copy(variablesEditor = null) }

    fun saveVariables() {
        val editor = ui.value.variablesEditor ?: return
        ui.update { it.copy(variablesEditor = null) }
        viewModelScope.launch {
            try {
                manager.setUserVariables(editor.plugin.id, editor.values)
                eventChannel.send(PluginsEvent.Message("已保存"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.send(PluginsEvent.Message(e.message ?: "保存失败"))
            }
        }
    }

    private fun updateSheet(change: (ImportSheetState) -> ImportSheetState) = ui.update { s ->
        s.sheet?.let { s.copy(sheet = change(it)) } ?: s
    }

    private companion object {
        const val MAX_CODE_CHARS = 200_000
    }
}
