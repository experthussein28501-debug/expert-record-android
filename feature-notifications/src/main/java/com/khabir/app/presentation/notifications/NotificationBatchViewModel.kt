package com.khabir.app.presentation.notifications

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.ocr.ArabicPetitionOcrService
import com.khabir.app.data.ocr.MultiPageDocumentReader
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.LegalDocumentPurpose
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.usecase.cases.SearchCasesUseCase
import com.khabir.app.domain.usecase.notification.CreateNotificationBatchUseCase
import com.khabir.app.domain.usecase.notification.ExportNotificationBatchToWordUseCase
import com.khabir.app.domain.usecase.notification.ListNotificationBatchesUseCase
import com.khabir.app.domain.usecase.notification.RecipientSelection
import com.khabir.app.domain.usecase.notification.ReuseBatchAsNewUseCase
import com.khabir.app.domain.repository.NotificationBatchRepository
import com.khabir.app.presentation.cases.PetitionIntakeParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.File
import javax.inject.Inject

data class NotificationScreenUiState(
    val mode: Mode = Mode.BrowsingBatches,
    val caseSource: CaseSource = CaseSource.NEW_CASE,
    val pastBatches: List<NotificationBatch> = emptyList(),
    val selectedPastBatchIds: Set<Long> = emptySet(),
    val caseSearchQuery: String = "",
    val caseSearchResults: List<Case> = emptyList(),
    val selectedRegisteredCaseId: Long? = null,
    val selectedParties: List<SelectedParty> = emptyList(),
    val manualRecipients: List<RecipientSelection.Manual> = emptyList(),
    val manualCaseNo: String = "",
    val manualCaseYear: String = LocalDate.now().year.toString(),
    val manualCourt: String = "",
    val manualPetition: String = "",
    val manualFirstName: String = "",
    val manualRestName: String = "",
    val manualRole: PartyRole = PartyRole.OTHER,
    val manualWithCapacity: Boolean = false,
    val manualAddress: String = "",
    val appointmentDate: LocalDate = LocalDate.now(),
    val appointmentTime: String = "",
    val appointmentLocation: String = "",
    val requestedDocuments: String = "",
    val isCreating: Boolean = false,
    val isExporting: Boolean = false,
    val exportingType: ExportNotificationBatchToWordUseCase.OutputType? = null,
    val errorMessage: String? = null,
    val lastCreatedBatchId: Long? = null,
    val editingBatchId: Long? = null,
    val exportedFileUri: Uri? = null,
    val isOcrProcessing: Boolean = false,
    val ocrReviewText: String? = null,
    val authorityEditor: com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft? = null,
    val approvedAuthorityNotices: Map<String, com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft> = emptyMap()
) {
    enum class Mode { BrowsingBatches, BuildingNew }
    enum class CaseSource { NEW_CASE, REGISTERED_CASES }

    val selectedRegisteredCase: Case?
        get() = selectedRegisteredCaseId?.let { id -> caseSearchResults.firstOrNull { it.id == id } }

    val authorityCandidates: List<com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft>
        get() {
            fun government(name: String) = listOf("وزير", "وزارة", "مدير", "مديرية", "محافظ", "هيئة", "مصلحة", "وحدة محلية").any(name::contains)
            fun summary(names: List<String>) = names.joinToString("، ")
            return if (caseSource == CaseSource.REGISTERED_CASES) selectedParties.map { it.case }.distinctBy { it.id }
                .filter { c -> c.parties.any { government(it.reportDisplayName) } }
                .map { c -> com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft(
                    key = "case:${c.id}", caseNo = c.caseNo, caseYear = c.caseYear, court = c.court,
                    subject = com.khabir.app.domain.usecase.notification.NoticeSubject.suggest(c.finalRequests, c.subjectOfCase),
                    judgmentDate = c.preliminaryJudgmentDate?.toString().orEmpty(), incomingNo = c.incomingNo,
                    plaintiffs = summary(c.parties.filter { it.role == PartyRole.PLAINTIFF }.map { it.reportDisplayName }),
                    defendants = summary(c.parties.filter { it.role == PartyRole.DEFENDANT }.map { it.reportDisplayName })
                ) }
            else if (manualRecipients.any { government(it.firstName + " " + it.restName) })
                listOf(com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft(
                    key = "manual:$manualCaseNo/$manualCaseYear/$manualCourt",
                    caseNo = manualCaseNo, caseYear = manualCaseYear, court = manualCourt,
                    subject = com.khabir.app.domain.usecase.notification.NoticeSubject.suggest("", manualPetition),
                    plaintiffs = summary(manualRecipients.filter { it.role == PartyRole.PLAINTIFF }.map { it.firstName + " " + it.restName }),
                    defendants = summary(manualRecipients.filter { it.role == PartyRole.DEFENDANT }.map { it.firstName + " " + it.restName })
                ))
            else emptyList()
        }
}

data class SelectedParty(val case: Case, val party: Party)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class NotificationBatchViewModel @Inject constructor(
    private val searchCases: SearchCasesUseCase,
    private val listBatches: ListNotificationBatchesUseCase,
    private val createBatch: CreateNotificationBatchUseCase,
    private val reuseBatch: ReuseBatchAsNewUseCase,
    private val exportBatch: ExportNotificationBatchToWordUseCase,
    private val arabicOcr: ArabicPetitionOcrService,
    private val geminiVision: GeminiDocumentVisionService,
    private val multiPageReader: MultiPageDocumentReader,
    private val batchRepository: NotificationBatchRepository
) : ViewModel() {

    companion object {
        /**
         * رسالة الخطأ عند نقص بيانات إضافة مخاطَب يدويًا، أو null لو البيانات مكتملة.
         * منطق صرف بدون حالة الـViewModel، قابل للاختبار مباشرة.
         */
        fun manualRecipientValidationError(state: NotificationScreenUiState): String? {
            val missingCaseInfo = state.manualCaseNo.isBlank() || state.manualCaseYear.isBlank() || state.manualCourt.isBlank()
            if (state.manualRole == PartyRole.LAWYER && com.khabir.app.domain.model.LawyerNotification.address(state.manualAddress).isBlank()) return "اكتب مدينة المحامي لتحديد المحكمة ونقابة المحامين"
            val missingName = state.manualFirstName.isBlank() && state.manualRestName.isBlank()
            return if (missingCaseInfo || missingName) "أكمل رقم وسنة الدعوى والمحكمة واسم الطرف" else null
        }

        /** مفتاح تفرّد لمخاطَب يدوي، مستخدم لمنع تكرار نفس الاسم/الصفة/العنوان عند الاستخراج من صورة. */
        fun recipientKey(recipient: RecipientSelection.Manual): String = listOf(
            recipient.firstName,
            recipient.restName,
            recipient.role.name,
            recipient.address
        ).joinToString("|") { it.trim().lowercase().replace(Regex("\\s+"), " ") }
    }

    private val _uiState = MutableStateFlow(NotificationScreenUiState())
    val uiState: StateFlow<NotificationScreenUiState> = _uiState.asStateFlow()
    private val caseQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            listBatches().collect { batches ->
                _uiState.update { state ->
                    state.copy(
                        pastBatches = batches,
                        selectedPastBatchIds = state.selectedPastBatchIds.intersect(batches.map { it.id }.toSet())
                    )
                }
            }
        }
        viewModelScope.launch {
            caseQuery.debounce(200).flatMapLatest { searchCases(it) }.collect { results ->
                _uiState.update { state ->
                    val selectedStillExists = state.selectedRegisteredCaseId?.let { id -> results.any { it.id == id } } == true
                    state.copy(
                        caseSearchResults = results,
                        selectedRegisteredCaseId = if (selectedStillExists) state.selectedRegisteredCaseId else null,
                        selectedParties = state.selectedParties
                    )
                }
            }
        }
    }

    fun onStartNewBatch() = _uiState.update {
        it.copy(
            authorityEditor = null, approvedAuthorityNotices = emptyMap(), manualPetition = "",
            mode = NotificationScreenUiState.Mode.BuildingNew,
            caseSource = NotificationScreenUiState.CaseSource.NEW_CASE,
            selectedRegisteredCaseId = null,
            selectedParties = emptyList(),
            manualRecipients = emptyList(),
            lastCreatedBatchId = null,
            editingBatchId = null,
            errorMessage = null
        )
    }

    fun onCancelNewBatch() = _uiState.update {
        it.copy(
            authorityEditor = null, approvedAuthorityNotices = emptyMap(), manualPetition = "",
            mode = NotificationScreenUiState.Mode.BrowsingBatches,
            selectedRegisteredCaseId = null,
            selectedParties = emptyList(),
            manualRecipients = emptyList(),
            editingBatchId = null,
            errorMessage = null
        )
    }

    fun onEditPastBatch(batchId: Long) {
        val batch = _uiState.value.pastBatches.firstOrNull { it.id == batchId } ?: return
        val ordinary = batch.recipients.filterNot { it.isAuthorityNotice }
        val first = ordinary.firstOrNull()
        _uiState.update {
            it.copy(
                mode = NotificationScreenUiState.Mode.BuildingNew,
                caseSource = NotificationScreenUiState.CaseSource.NEW_CASE,
                editingBatchId = batch.id,
                manualCaseNo = first?.caseNo.orEmpty(),
                manualCaseYear = first?.caseYear.orEmpty(),
                manualCourt = first?.court.orEmpty(),
                manualRecipients = ordinary.map { r ->
                    RecipientSelection.Manual(
                        caseNo = r.caseNo,
                        caseYear = r.caseYear,
                        court = r.court,
                        firstName = r.partyFirstName,
                        restName = r.partyRestName,
                        role = r.partyRole,
                        address = r.partyAddress,
                        withCapacity = r.withCapacity
                    )
                },
                appointmentDate = batch.appointmentDate,
                appointmentTime = batch.appointmentTime,
                appointmentLocation = batch.appointmentLocation,
                requestedDocuments = batch.requestedDocuments,
                selectedRegisteredCaseId = null,
                selectedParties = emptyList(),
                errorMessage = "تعديل نفس دفعة الإخطارات — الحفظ سيحدّث السجل الحالي"
            )
        }
    }

    fun onCaseSourceChanged(source: NotificationScreenUiState.CaseSource) = _uiState.update { state ->
        if (state.caseSource == source) state else state.copy(
            authorityEditor = null, approvedAuthorityNotices = emptyMap(), manualPetition = "",
            caseSource = source,
            selectedRegisteredCaseId = null,
            selectedParties = emptyList(),
            manualRecipients = emptyList(),
            errorMessage = null
        )
    }

    fun onCaseQueryChanged(q: String) {
        _uiState.update { it.copy(caseSearchQuery = q, selectedRegisteredCaseId = null) }
        caseQuery.value = q
    }

    fun onRegisteredCaseSelected(case: Case) = _uiState.update {
        it.copy(selectedRegisteredCaseId = case.id, errorMessage = null)
    }

    fun onChangeRegisteredCase() = _uiState.update {
        it.copy(selectedRegisteredCaseId = null, errorMessage = null)
    }

    fun onTogglePartySelection(case: Case, party: Party) = _uiState.update { state ->
        if (state.selectedRegisteredCaseId != case.id) return@update state
        val exists = state.selectedParties.any { it.case.id == case.id && it.party.id == party.id }
        state.copy(
            selectedParties = if (exists) {
                state.selectedParties.filterNot { it.case.id == case.id && it.party.id == party.id }
            } else {
                state.selectedParties + SelectedParty(case, party)
            },
            errorMessage = null
        )
    }

    fun onSelectAllParties(case: Case) = _uiState.update { state ->
        if (state.selectedRegisteredCaseId != case.id) state else state.copy(
            selectedParties = state.selectedParties.filterNot { it.case.id == case.id } + case.parties.map { SelectedParty(case, it) },
            errorMessage = null
        )
    }

    fun onClearPartySelection() = _uiState.update { state ->
        state.copy(selectedParties = state.selectedParties.filterNot { it.case.id == state.selectedRegisteredCaseId }, errorMessage = null)
    }

    fun onRemoveSelectedCase(caseId: Long) = _uiState.update { state ->
        state.copy(selectedParties = state.selectedParties.filterNot { it.case.id == caseId })
    }

    fun onTogglePastBatch(batchId: Long) = _uiState.update { state ->
        state.copy(
            selectedPastBatchIds = if (batchId in state.selectedPastBatchIds) {
                state.selectedPastBatchIds - batchId
            } else {
                state.selectedPastBatchIds + batchId
            },
            errorMessage = null
        )
    }

    fun onSelectAllPastBatches() = _uiState.update { state ->
        state.copy(selectedPastBatchIds = state.pastBatches.map { it.id }.toSet(), errorMessage = null)
    }

    fun onClearPastBatchSelection() = _uiState.update {
        it.copy(selectedPastBatchIds = emptySet(), errorMessage = null)
    }

    fun onDeletePastBatch(batchId: Long) {
        viewModelScope.launch {
            runCatching { batchRepository.delete(batchId) }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "تعذر إزالة الإخطار") } }
        }
    }

    fun onAppointmentDateChanged(v: LocalDate) = _uiState.update { it.copy(appointmentDate = v) }
    fun onAppointmentTimeChanged(v: String) = _uiState.update { it.copy(appointmentTime = v) }
    fun onAppointmentLocationChanged(v: String) = _uiState.update { it.copy(appointmentLocation = v) }
    fun onRequestedDocumentsChanged(v: String) = _uiState.update { it.copy(requestedDocuments = v) }
    fun onManualCaseNoChanged(v: String) = _uiState.update { it.copy(manualCaseNo = v) }
    fun onManualCaseYearChanged(v: String) = _uiState.update { it.copy(manualCaseYear = v) }
    fun onManualCourtChanged(v: String) = _uiState.update { it.copy(manualCourt = v) }
    fun onManualFirstNameChanged(v: String) = _uiState.update { it.copy(manualFirstName = v) }
    fun onManualRestNameChanged(v: String) = _uiState.update { it.copy(manualRestName = v) }
    fun onManualRoleChanged(v: PartyRole) = _uiState.update { it.copy(manualRole = v, manualAddress = if (it.manualRole != v && (it.manualRole == PartyRole.LAWYER || v == PartyRole.LAWYER)) "" else it.manualAddress) }
    fun onManualWithCapacityChanged(v: Boolean) = _uiState.update { it.copy(manualWithCapacity = v) }
    fun onManualAddressChanged(v: String) = _uiState.update { it.copy(manualAddress = v) }

    fun refineVoiceTranscript(text: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            when (val result = geminiVision.refineTranscript(text, LegalDocumentPurpose.NOTIFICATION)) {
                is GeminiDocumentVisionService.Result.Success -> onReady(result.text)
                else -> onReady(text)
            }
        }
    }
    fun onCameraError(message: String) = _uiState.update { it.copy(errorMessage = message) }

    fun onPetitionPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            when (val result = arabicOcr.recognize(bitmap)) {
                is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                    it.copy(isOcrProcessing = false, ocrReviewText = result.text)
                }
                is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                    it.copy(isOcrProcessing = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onPetitionPhotoCapturedWithGemini(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            when (val result = geminiVision.analyze(bitmap, LegalDocumentPurpose.NOTIFICATION)) {
                is GeminiDocumentVisionService.Result.Success -> _uiState.update {
                    it.copy(isOcrProcessing = false, ocrReviewText = result.text)
                }
                is GeminiDocumentVisionService.Result.Unavailable -> useLocalOcrFallback(bitmap, result.message)
                is GeminiDocumentVisionService.Result.Failure -> useLocalOcrFallback(bitmap, result.message)
            }
        }
    }

    fun onDocumentPagesCaptured(pageFiles: List<File>, useAi: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            val result = multiPageReader.read(pageFiles, LegalDocumentPurpose.NOTIFICATION, useAi)
            _uiState.update {
                if (result.text.isBlank()) it.copy(
                    isOcrProcessing = false,
                    errorMessage = result.warnings.firstOrNull() ?: "لم يتم استخراج نص من الصفحات"
                ) else it.copy(
                    isOcrProcessing = false,
                    ocrReviewText = result.text,
                    errorMessage = if (result.usedLocalFallback) "استُخدم OCR المحلي لبعض صفحات المستند؛ راجع الناتج." else null
                )
            }
        }
    }

    private suspend fun useLocalOcrFallback(bitmap: Bitmap, reason: String) {
        when (val local = arabicOcr.recognize(bitmap)) {
            is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                it.copy(isOcrProcessing = false, ocrReviewText = local.text, errorMessage = "$reason — تم استخدام OCR المحلي، راجع النص.")
            }
            is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                it.copy(isOcrProcessing = false, errorMessage = "$reason — ${local.message}")
            }
        }
    }

    fun onDismissOcrReview() = _uiState.update { it.copy(ocrReviewText = null) }

    fun onApplyOcrReview(
        editedText: String,
        reviewedParties: List<PetitionIntakeParser.ParsedParty>? = null
    ) {
        val state = _uiState.value
        val parsed = PetitionIntakeParser.parse(editedText)
        val acceptedParties = reviewedParties ?: parsed.parties
        val caseNo = parsed.caseNo ?: state.manualCaseNo
        val caseYear = parsed.caseYear ?: state.manualCaseYear
        val court = parsed.court ?: state.manualCourt
        val extracted = acceptedParties.mapNotNull { party ->
            val nameParts = party.name.trim().split(Regex("\\s+"), limit = 2)
            val firstName = nameParts.firstOrNull().orEmpty()
            val restName = nameParts.getOrNull(1).orEmpty()
            if (firstName.isBlank() && restName.isBlank()) null else RecipientSelection.Manual(
                caseNo = caseNo,
                caseYear = caseYear,
                court = court,
                firstName = firstName,
                restName = restName,
                role = party.role,
                address = party.address,
                withCapacity = party.withCapacity
            )
        }
        val existingKeys = state.manualRecipients.mapTo(mutableSetOf()) { recipientKey(it) }
        val uniqueExtracted = extracted.filter { existingKeys.add(recipientKey(it)) }
        _uiState.update {
            it.copy(
                manualCaseNo = caseNo,
                manualCaseYear = caseYear,
                manualCourt = court,
                manualRecipients = it.manualRecipients + uniqueExtracted,
                manualPetition = editedText,
                ocrReviewText = null,
                errorMessage = when {
                    acceptedParties.isNotEmpty() && uniqueExtracted.isNotEmpty() -> "تم اعتماد ${uniqueExtracted.size} خصم؛ أكمل رقم الدعوى والسنة والمحكمة إن لم تظهر تلقائيًا"
                    acceptedParties.isNotEmpty() -> "لم تُضف أسماء جديدة لأنها موجودة بالفعل"
                    else -> "تمت قراءة الصورة، لكن لم أتعرف على أسماء خصوم واضحة"
                }
            )
        }
    }

    fun onAddManualRecipient() {
        val state = _uiState.value
        val validationError = manualRecipientValidationError(state)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }
        val recipient = RecipientSelection.Manual(
            state.manualCaseNo.trim(),
            state.manualCaseYear.trim(),
            state.manualCourt.trim(),
            state.manualFirstName.trim(),
            state.manualRestName.trim(),
            state.manualRole,
            if (state.manualRole == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(state.manualAddress) else state.manualAddress.trim(),
            state.manualWithCapacity
        )
        _uiState.update {
            it.copy(
                manualRecipients = it.manualRecipients + recipient,
                manualFirstName = "",
                errorMessage = null
            )
        }
    }

    fun onRemoveManualRecipient(index: Int) = _uiState.update { state ->
        state.copy(manualRecipients = state.manualRecipients.filterIndexed { i, _ -> i != index })
    }

    fun onOpenAuthorityNotice(draft: com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft) = _uiState.update {
        it.copy(authorityEditor = it.approvedAuthorityNotices[draft.key] ?: draft)
    }
    fun onEditAuthorityNotice(draft: com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft) = _uiState.update { it.copy(authorityEditor = draft) }
    fun onDismissAuthorityNotice() = _uiState.update { it.copy(authorityEditor = null) }
    fun onRemoveAuthorityNotice(key: String) = _uiState.update { it.copy(approvedAuthorityNotices = it.approvedAuthorityNotices - key) }
    fun onApproveAuthorityNotice() = _uiState.update { state ->
        val draft = state.authorityEditor
        if (draft == null || !draft.valid()) state.copy(errorMessage = "أكمل عنوان الهيئة وموضوع الدعوى، وراجع تاريخ الحكم")
        else state.copy(approvedAuthorityNotices = state.approvedAuthorityNotices + (draft.key to draft), authorityEditor = null, errorMessage = null)
    }

    fun onGenerateBatch() {
        val state = _uiState.value
        if (state.isCreating) return
        if (state.caseSource == NotificationScreenUiState.CaseSource.NEW_CASE &&
            (state.manualCaseNo.isBlank() || state.manualCaseYear.isBlank() || state.manualCourt.isBlank())
        ) {
            _uiState.update { it.copy(errorMessage = "أكمل رقم وسنة الدعوى والمحكمة قبل إنشاء الإخطارات") }
            return
        }
        val selections: List<RecipientSelection> = when (state.caseSource) {
            NotificationScreenUiState.CaseSource.NEW_CASE -> state.manualRecipients.map { recipient ->
                recipient.copy(
                    caseNo = state.manualCaseNo.trim(),
                    caseYear = state.manualCaseYear.trim(),
                    court = state.manualCourt.trim()
                )
            }
            NotificationScreenUiState.CaseSource.REGISTERED_CASES -> state.selectedParties.map { RecipientSelection.FromCase(it.case, it.party) }
        }
        if (selections.isEmpty()) {
            _uiState.update {
                it.copy(errorMessage = if (state.caseSource == NotificationScreenUiState.CaseSource.NEW_CASE) "أضف شخصًا واحدًا على الأقل للإخطار" else "اختر شخصًا واحدًا على الأقل من الدعوى المسجلة")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            val activeKeys = state.authorityCandidates.map { it.key }.toSet()
            val approved = state.approvedAuthorityNotices.filterKeys { it in activeKeys }.values.toList()
            when (val result = createBatch(
                state.appointmentDate,
                state.appointmentTime,
                state.appointmentLocation,
                state.requestedDocuments,
                selections,
                authorityNotices = approved,
                existingBatchId = state.editingBatchId
            )) {
                is CreateNotificationBatchUseCase.Result.Success -> { _uiState.update {
                    it.copy(
                        isCreating = false,
                        lastCreatedBatchId = result.batchId,
                        selectedPastBatchIds = setOf(result.batchId),
                        mode = NotificationScreenUiState.Mode.BrowsingBatches,
                        selectedRegisteredCaseId = null,
                        selectedParties = emptyList(),
                        manualRecipients = emptyList(),
                        requestedDocuments = "",
                        editingBatchId = null
                    )
                }
                    com.khabir.app.data.monetization.WorkAdEvents.finished()
                }
                CreateNotificationBatchUseCase.Result.MissingLawyerCity -> _uiState.update { it.copy(isCreating = false, errorMessage = "حدد مدينة المحامي في بياناته قبل إنشاء الإخطار") }
                CreateNotificationBatchUseCase.Result.EmptySelection -> _uiState.update { it.copy(isCreating = false, errorMessage = "اختر طرفًا واحدًا على الأقل") }
                CreateNotificationBatchUseCase.Result.MissingExpertProfile -> _uiState.update { it.copy(isCreating = false, errorMessage = "أكمل بيانات الخبير أولًا") }
            }
        }
    }

    fun onExportBatch(id: Long, type: ExportNotificationBatchToWordUseCase.OutputType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportingType = type, errorMessage = null) }
            when (val result = exportBatch(id, type)) {
                is ExportNotificationBatchToWordUseCase.Result.Success -> _uiState.update { it.copy(isExporting = false, exportingType = null, exportedFileUri = result.uri) }
                ExportNotificationBatchToWordUseCase.Result.BatchNotFound -> _uiState.update { it.copy(isExporting = false, exportingType = null, errorMessage = "دفعة الإخطارات غير موجودة") }
            }
        }
    }

    fun onExportSelected(type: ExportNotificationBatchToWordUseCase.OutputType) {
        val state = _uiState.value
        val ids = state.pastBatches.filter { it.id in state.selectedPastBatchIds }.map { it.id }
        if (ids.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "اختر قضية أو دفعة واحدة على الأقل") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportingType = type, errorMessage = null) }
            when (val result = exportBatch(ids, type)) {
                is ExportNotificationBatchToWordUseCase.Result.Success -> _uiState.update {
                    it.copy(isExporting = false, exportingType = null, exportedFileUri = result.uri)
                }
                ExportNotificationBatchToWordUseCase.Result.BatchNotFound -> _uiState.update {
                    it.copy(isExporting = false, exportingType = null, errorMessage = "تعذر العثور على إحدى القضايا المحددة")
                }
            }
        }
    }

    fun onExportEventConsumed() = _uiState.update { it.copy(exportedFileUri = null) }
}
