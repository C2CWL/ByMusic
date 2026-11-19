package com.sosauce.chocola.feature.recognition.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.feature.recognition.data.RecognitionHistoryStore
import com.sosauce.chocola.feature.recognition.data.RecognitionSession
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginRegistry
import com.sosauce.chocola.feature.recognition.domain.RecognitionState
import com.sosauce.chocola.feature.recognition.domain.activePluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecognitionViewModel(
    private val session: RecognitionSession,
    private val registry: RecognitionPluginRegistry,
    private val history: RecognitionHistoryStore
) : ViewModel() {

    val state: StateFlow<RecognitionState> = session.state

    val plugins: StateFlow<List<RecognitionPluginInfo>> = registry.plugins
        .map { list -> list.map { it.info } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, registry.plugins.value.map { it.info })

    val historyEntries = history.entries

    private var job: Job? = null
    private var lastPluginId: String? = null

    init {
        // Drop any clip a previous crash left behind before anything else can record.
        session.purgeLeftovers()
        viewModelScope.launch(Dispatchers.IO) { history.load() }
    }

    /** Only ever called from a user tap on a visible screen. */
    fun start(permissionGranted: Boolean) {
        session.dispatch(
            RecognitionEvent.StartRequested(
                plugins = plugins.value,
                preferredPluginId = state.value.activePluginId ?: lastPluginId,
                permissionGranted = permissionGranted
            )
        )
        launchIfRecording()
    }

    fun onRationaleDismissed() = session.dispatch(RecognitionEvent.RationaleDismissed)

    fun onPermissionResult(granted: Boolean) {
        session.dispatch(RecognitionEvent.PermissionResult(granted))
        launchIfRecording()
    }

    fun selectPlugin(id: String) {
        lastPluginId = id
        session.dispatch(RecognitionEvent.PluginSelected(id))
    }

    fun cancel() {
        job?.cancel()
        job = null
        session.dispatch(RecognitionEvent.Cancel)
    }

    fun reset() {
        cancel()
        session.dispatch(RecognitionEvent.Reset)
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) { history.clear() }
    }

    private fun launchIfRecording() {
        val recording = state.value as? RecognitionState.Recording ?: return
        if (job?.isActive == true) return
        lastPluginId = recording.pluginId
        job = viewModelScope.launch(Dispatchers.IO) { session.run() }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
