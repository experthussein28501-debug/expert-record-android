package com.khabir.app.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.AiProvider
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.data.backup.BackupOperationResult
import com.khabir.app.data.backup.EncryptedBackupService
import com.khabir.app.data.backup.PendingBackupRestore
import com.khabir.app.domain.model.ExpertProfile
import com.khabir.app.domain.usecase.profile.GetExpertProfileUseCase
import com.khabir.app.domain.usecase.profile.SaveExpertProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExpertProfileField { MINISTRY, DEPARTMENT, NAME, SPECIALIZATION, ADDRESS }

data class ExpertProfileUiState(
    val ministryOrSector: String = "",
    val department: String = "",
    val expertName: String = "",
    val specialization: String = "",
    val officeAddress: String = "",
    val jobTitle: String = "",
    val attendancePhrase: String = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
    val personalAiKey: String = "",
    val hasPersonalAiKey: Boolean = false,
    val aiProvider: AiProvider = AiProvider.GEMINI,
    val aiInstructions: String = "",
    val isSaving: Boolean = false,
    val isTestingAiKey: Boolean = false,
    val savedJustNow: Boolean = false,
    val validationMessage: String? = null,
    val aiKeyMessage: String? = null,
    val ocrReviewField: ExpertProfileField? = null,
    val backupPassword: String = "",
    val isBackupWorking: Boolean = false,
    val backupMessage: String? = null,
    val backupRestartRequired: Boolean = false
)

@HiltViewModel
class ExpertProfileViewModel @Inject constructor(
    getExpertProfile: GetExpertProfileUseCase,
    private val saveExpertProfile: SaveExpertProfileUseCase,
    private val personalAiKeyStore: PersonalAiKeyStore,
    private val geminiVision: GeminiDocumentVisionService,
    private val encryptedBackupService: EncryptedBackupService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExpertProfileUiState())
    val uiState: StateFlow<ExpertProfileUiState> = _uiState.asStateFlow()

    private var keyRevision = 0L

    init {
        _uiState.update {
            it.copy(
                hasPersonalAiKey = personalAiKeyStore.read().isNotBlank(),
                aiProvider = personalAiKeyStore.readProvider(),
                aiInstructions = personalAiKeyStore.readInstructions(),
                backupMessage = PendingBackupRestore.consumeLastStatus(appContext)
            )
        }
        viewModelScope.launch {
            getExpertProfile().collect { p ->
                if (p != null) {
                    _uiState.update {
                        it.copy(
                            ministryOrSector = p.ministryOrSector,
                            department = p.department,
                            expertName = p.expertName,
                            specialization = p.specialization,
                            officeAddress = p.officeAddress,
                            jobTitle = p.jobTitle,
                            attendancePhrase = p.attendancePhrase
                        )
                    }
                }
            }
        }
    }

    fun onMinistryChanged(v: String) = changed { copy(ministryOrSector = v) }
    fun onDepartmentChanged(v: String) = changed { copy(department = v) }
    fun onNameChanged(v: String) = changed { copy(expertName = v) }
    fun onSpecializationChanged(v: String) = changed { copy(specialization = v) }
    fun onAddressChanged(v: String) = changed { copy(officeAddress = v) }
    fun onJobTitleChanged(v: String) = changed { copy(jobTitle = v) }
    fun onAttendancePhraseChanged(v: String) = changed { copy(attendancePhrase = v) }

    private fun invalidateKey() {
        keyRevision++
        personalAiKeyStore.clear()
    }

    fun onPersonalAiKeyChanged(v: String) {
        invalidateKey()
        changed { copy(personalAiKey = v, hasPersonalAiKey = false) }
    }

    fun onAiProviderChanged(v: AiProvider) {
        invalidateKey()
        changed { copy(aiProvider = v, hasPersonalAiKey = false) }
    }

    fun onAiInstructionsChanged(v: String) = changed { copy(aiInstructions = v) }

    fun clearPersonalAiKey() {
        invalidateKey()
        _uiState.update {
            it.copy(
                personalAiKey = "",
                hasPersonalAiKey = false,
                savedJustNow = false,
                aiKeyMessage = "تم حذف مفتاح الذكاء الاصطناعي"
            )
        }
    }

    private fun changed(update: ExpertProfileUiState.() -> ExpertProfileUiState) =
        _uiState.update { it.update().copy(savedJustNow = false, validationMessage = null, aiKeyMessage = null) }

    fun onCaptureField(field: ExpertProfileField) = _uiState.update { it.copy(ocrReviewField = field) }
    fun onDismissOcrReview() = _uiState.update { it.copy(ocrReviewField = null) }

    fun onOcrTextConfirmed(text: String) {
        val field = _uiState.value.ocrReviewField ?: return
        _uiState.update { s ->
            val updated = when (field) {
                ExpertProfileField.MINISTRY -> s.copy(ministryOrSector = text)
                ExpertProfileField.DEPARTMENT -> s.copy(department = text)
                ExpertProfileField.NAME -> s.copy(expertName = text)
                ExpertProfileField.SPECIALIZATION -> s.copy(specialization = text)
                ExpertProfileField.ADDRESS -> s.copy(officeAddress = text)
            }
            updated.copy(ocrReviewField = null, savedJustNow = false)
        }
    }

    fun testAndSaveAiKey() {
        val s = _uiState.value
        if (s.isTestingAiKey) return
        val testedRevision = keyRevision
        if (s.personalAiKey.isBlank()) {
            _uiState.update { it.copy(aiKeyMessage = "أدخل مفتاح ${s.aiProvider.label} أولًا") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(isTestingAiKey = true, aiKeyMessage = "جارٍ إجراء اتصال حقيقي بخدمة ${s.aiProvider.label}...")
            }
            val result = geminiVision.validatePersonalKey(s.personalAiKey.trim(), s.aiProvider)
            if (testedRevision != keyRevision) {
                _uiState.update {
                    it.copy(isTestingAiKey = false, aiKeyMessage = "تغير المفتاح أثناء الاختبار؛ اختبر المفتاح الحالي")
                }
                return@launch
            }
            when (result) {
                is GeminiDocumentVisionService.Result.Success -> {
                    personalAiKeyStore.write(s.personalAiKey.trim())
                    personalAiKeyStore.writeProvider(s.aiProvider)
                    _uiState.update {
                        it.copy(
                            isTestingAiKey = false,
                            personalAiKey = "",
                            hasPersonalAiKey = true,
                            aiKeyMessage = result.text
                        )
                    }
                }
                is GeminiDocumentVisionService.Result.Unavailable ->
                    _uiState.update { it.copy(isTestingAiKey = false, aiKeyMessage = result.message) }
                is GeminiDocumentVisionService.Result.Failure ->
                    _uiState.update { it.copy(isTestingAiKey = false, aiKeyMessage = result.message) }
            }
        }
    }

    fun onBackupPasswordChanged(value: String) = _uiState.update {
        it.copy(backupPassword = value, backupMessage = null, backupRestartRequired = false)
    }

    fun exportBackup(uri: Uri) {
        val password = _uiState.value.backupPassword
        if (_uiState.value.isBackupWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupWorking = true, backupMessage = "جارٍ تجهيز النسخة الاحتياطية المشفرة...", backupRestartRequired = false) }
            when (val result = encryptedBackupService.exportTo(uri, password)) {
                is BackupOperationResult.Success -> _uiState.update {
                    it.copy(isBackupWorking = false, backupMessage = result.message, backupRestartRequired = result.restartRequired)
                }
                is BackupOperationResult.Failure -> _uiState.update {
                    it.copy(isBackupWorking = false, backupMessage = result.message, backupRestartRequired = false)
                }
            }
        }
    }

    fun restoreBackup(uri: Uri) {
        val password = _uiState.value.backupPassword
        if (_uiState.value.isBackupWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupWorking = true, backupMessage = "جارٍ فحص وفك النسخة الاحتياطية...", backupRestartRequired = false) }
            when (val result = encryptedBackupService.stageRestore(uri, password)) {
                is BackupOperationResult.Success -> _uiState.update {
                    it.copy(isBackupWorking = false, backupMessage = result.message, backupRestartRequired = result.restartRequired)
                }
                is BackupOperationResult.Failure -> _uiState.update {
                    it.copy(isBackupWorking = false, backupMessage = result.message, backupRestartRequired = false)
                }
            }
        }
    }

    fun onSave() {
        val s = _uiState.value
        val missing = listOfNotNull(
            if (s.expertName.isBlank()) "اسم الخبير" else null,
            if (s.jobTitle.isBlank()) "الصفة الوظيفية" else null,
            if (s.department.isBlank()) "الإدارة / المكتب" else null,
            if (s.officeAddress.isBlank()) "عنوان المكتب" else null
        )
        if (missing.isNotEmpty()) {
            _uiState.update { it.copy(validationMessage = "أكمل: ${missing.joinToString("، ")}") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, validationMessage = null) }
            personalAiKeyStore.writeInstructions(s.aiInstructions)
            saveExpertProfile(
                ExpertProfile(
                    ministryOrSector = s.ministryOrSector.trim(),
                    department = s.department.trim(),
                    expertName = s.expertName.trim(),
                    specialization = s.specialization.trim(),
                    officeAddress = s.officeAddress.trim(),
                    jobTitle = s.jobTitle.trim(),
                    attendancePhrase = s.attendancePhrase.trim()
                )
            )
            _uiState.update {
                it.copy(isSaving = false, savedJustNow = true, hasPersonalAiKey = personalAiKeyStore.read().isNotBlank())
            }
        }
    }
}
