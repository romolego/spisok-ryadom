package com.spisokryadom.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.spisokryadom.app.SpisokRyadomApp
import com.spisokryadom.app.data.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val message: String? = null,
    /** Если не null — JSON готов, нужно открыть file picker для сохранения */
    val pendingExportJson: String? = null
)

class SettingsViewModel(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun prepareExport() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                val json = backupManager.exportToJson()
                _uiState.value = _uiState.value.copy(isLoading = false, pendingExportJson = json)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Ошибка экспорта: ${e.message}"
                )
            }
        }
    }

    /** Вызывается после того, как UI забрал JSON и передал в file picker */
    fun exportConsumed(success: Boolean) {
        _uiState.value = _uiState.value.copy(
            pendingExportJson = null,
            message = if (success) "База экспортирована" else null
        )
    }

    fun importFromJson(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            try {
                backupManager.importFromJson(json)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "База успешно импортирована"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "Ошибка импорта: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpisokRyadomApp
                SettingsViewModel(backupManager = app.container.backupManager)
            }
        }
    }
}
