package com.khabir.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.backup.LocalBackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val backupService: LocalBackupService
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, message = null, isError = false) }

    fun createBackup(uri: Uri) {
        val password = _uiState.value.password
        if (password.length < 6) {
            _uiState.update { it.copy(message = "اكتب كلمة مرور من ٦ أحرف على الأقل", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            runCatching { withContext(Dispatchers.IO) { backupService.createEncryptedBackup(uri, password.toCharArray()) } }
                .onSuccess { _uiState.update { it.copy(isBusy = false, message = "تم إنشاء النسخة الاحتياطية المشفرة", isError = false) } }
                .onFailure { error -> _uiState.update { it.copy(isBusy = false, message = error.message ?: "تعذر إنشاء النسخة الاحتياطية", isError = true) } }
        }
    }

    fun restoreBackup(uri: Uri) {
        val password = _uiState.value.password
        if (password.length < 6) {
            _uiState.update { it.copy(message = "اكتب كلمة مرور النسخة الاحتياطية", isError = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, isError = false) }
            when (withContext(Dispatchers.IO) { backupService.restoreEncryptedBackup(uri, password.toCharArray()) }) {
                LocalBackupService.RestoreResult.Success -> _uiState.update {
                    it.copy(isBusy = false, message = "تم استرجاع البيانات. يلزم إعادة تشغيل التطبيق.", restartRequired = true)
                }
                LocalBackupService.RestoreResult.WrongPasswordOrDamagedFile -> _uiState.update {
                    it.copy(isBusy = false, message = "كلمة المرور غير صحيحة أو ملف النسخة تالف", isError = true)
                }
            }
        }
    }
}
