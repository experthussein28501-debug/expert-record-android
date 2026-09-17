package com.khabir.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.backup.BackupOperationResult
import com.khabir.app.data.backup.EncryptedBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val password: String = "",
    val isBusy: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    val restartRequired: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupService: EncryptedBackupService
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun onPasswordChanged(value: String) = _uiState.update {
        it.copy(password = value, message = null, isError = false, restartRequired = false)
    }

    fun createBackup(uri: Uri) {
        val password = _uiState.value.password
        if (password.length < 6) {
            _uiState.update { it.copy(message = "اكتب كلمة مرور من ٦ أحرف على الأقل", isError = true) }
            return
        }
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "جارٍ تجهيز النسخة الاحتياطية المشفرة...", isError = false) }
            when (val result = backupService.exportTo(uri, password)) {
                is BackupOperationResult.Success -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = result.message,
                        isError = false,
                        restartRequired = result.restartRequired
                    )
                }
                is BackupOperationResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, message = result.message, isError = true, restartRequired = false)
                }
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        val password = _uiState.value.password
        if (password.length < 6) {
            _uiState.update { it.copy(message = "اكتب كلمة مرور النسخة الاحتياطية", isError = true) }
            return
        }
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = "جارٍ فحص وفك النسخة الاحتياطية...", isError = false) }
            when (val result = backupService.stageRestore(uri, password)) {
                is BackupOperationResult.Success -> _uiState.update {
                    it.copy(
                        isBusy = false,
                        message = result.message,
                        isError = false,
                        restartRequired = result.restartRequired
                    )
                }
                is BackupOperationResult.Failure -> _uiState.update {
                    it.copy(isBusy = false, message = result.message, isError = true, restartRequired = false)
                }
            }
        }
    }
}
