package com.sosauce.chocola.feature.settings.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sosauce.chocola.core.data.datastore.UserPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class BackupViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(BackupState())
    val state = _state.asStateFlow()

    private val _events = Channel<BackupEvents>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: BackupAction) {
        when (action) {
            is BackupAction.Export -> export(action.uri)
            is BackupAction.Import -> import(action.uri)
        }
    }

    private fun export(uri: Uri) = run {
        userPreferences.writeBackup(uri)
    }

    private fun import(uri: Uri) = run {
        _state.update { it.copy(isImporting = true) }
        userPreferences.importSettingsJson(uri)
        _state.update { it.copy(isImporting = false) }
    }

    private fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _events.send(BackupEvents.Success) }
                .onFailure { _events.send(BackupEvents.Error(it.message)) }
        }
    }
}

data class BackupState(
    val isImporting: Boolean = false
)

sealed interface BackupAction {
    data class Export(val uri: Uri) : BackupAction
    data class Import(val uri: Uri) : BackupAction
}

sealed interface BackupEvents {
    data object Success : BackupEvents
    data class Error(val message: String?) : BackupEvents
}