package com.khabir.app.presentation.workminutes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.LegalDocumentPurpose
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.data.ocr.MultiPageDocumentReader
import com.khabir.app.data.export.OfficeInteropService
import com.khabir.app.domain.model.ReportCoverFields
import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.model.WorkMinutesPhrases
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.DocumentExportRepository
import com.khabir.app.domain.repository.ExpertProfileRepository
import com.khabir.app.domain.usecase.workminutes.GetOrCreateWorkMinutesUseCase
import com.khabir.app.domain.usecase.workminutes.SaveWorkMinutesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

data class WorkMinutesUiState(
    val recordId: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val plaintiffsSummary: String = "",
    val defendantsSummary: String = "",
    val entries: List<WorkMinutesEntry> = emptyList(),
    val copiesCount: Int = 1,
    val expandedEntryNumber: Int? = null,
    val caseUpdates: List<com.khabir.app.domain.model.CaseFieldUpdate> = emptyList(),
    val canRetryPages: Boolean = false,
    val isLeaving: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val isCaptureProcessing: Boolean = false,
    val autoSaveStatus: String = "",
    val errorMessage: String? = null,
    val exportedFileUri: Uri? = null,
    val savedWordTemplateUri: String = "",
    val pendingTemplateUri: Uri? = null,
    val templateParagraphs: List<String> = emptyList(),
    val captureReviewText: String = "",
    val captureReviewSource: String = "",
    val captureTargetEntryNumber: Int? = null
) {
    val isIndependent: Boolean get() = caseId == null
}

@HiltViewModel
@OptIn(FlowPreview::class)
class WorkMinutesViewModel @Inject constructor(
    private val getOrCreateWorkMinutes: GetOrCreateWorkMinutesUseCase,
    private val saveWorkMinutes: SaveWorkMinutesUseCase,
    private val caseRepository: CaseRepository,
    private val expertProfileRepository: ExpertProfileRepository,
    private val exportRepository: DocumentExportRepository,
    private val officeInterop: OfficeInteropService,
    private val personalAiKeyStore: PersonalAiKeyStore,
    private val geminiVision: GeminiDocumentVisionService,
    private val multiPageReader: MultiPageDocumentReader,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkMinutesUiState())
    val uiState: StateFlow<WorkMinutesUiState> = _uiState.asStateFlow()
    private var retryPageRead: (() -> Unit)? = null
    private var retryFiles: List<File> = emptyList()
    fun retryPages() { _uiState.update { it.copy(canRetryPages = false) }; retryPageRead?.invoke() }
    fun cancelPageRetry() {
        retryFiles.forEach { it.delete() }; retryFiles = emptyList(); retryPageRead = null
        _uiState.update { it.copy(canRetryPages = false, errorMessage = null) }
    }
    private val autoSaveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val draftSaver = com.khabir.app.presentation.common.DraftSaveCoordinator(
        snapshot = { _uiState.value.toRecord() },
        persist = { saveWorkMinutes(it) },
        acceptId = { id -> _uiState.update { it.copy(recordId = id) } }
    )

    fun saveAndClose(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.isLeaving || state.isLoading || state.isSaving || state.isExporting) return
        _uiState.update { it.copy(isLeaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                draftSaver.flush()
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isLeaving = false, errorMessage = "تعذر الحفظ؛ لم يتم إغلاق الصفحة. حاول مرة أخرى.") }
            }
        }
    }


    private var caseReceiptDate: LocalDate? = null
    private var defaultExpertName: String = ""

    init {
        val recordId = savedStateHandle.get<Long>("recordId") ?: 0L
        val rawCaseId = savedStateHandle.get<Long>("caseId") ?: 0L
        val caseId = rawCaseId.takeIf { it > 0L }
        viewModelScope.launch {
            val record = getOrCreateWorkMinutes(recordId, caseId)
            caseReceiptDate = caseId?.let { caseRepository.getById(it)?.receiptDate }
            defaultExpertName = expertProfileRepository.get()?.expertName.orEmpty()
            _uiState.update {
                it.copy(
                    recordId = record.id,
                    caseId = record.caseId,
                    caseNo = record.caseNo,
                    caseYear = record.caseYear,
                    court = record.court,
                    plaintiffsSummary = record.plaintiffsSummary,
                    defendantsSummary = record.defendantsSummary,
                    entries = record.entries,
                    copiesCount = record.copiesCount,
                    isLoading = false,
                    savedWordTemplateUri = personalAiKeyStore.readWorkMinutesTemplateUri()
                )
            }
        }
        viewModelScope.launch {
            autoSaveRequests.debounce(1000).collect {
                val state = _uiState.value
                if (state.isLoading) return@collect
                runCatching { draftSaver.flush() }
                    .onSuccess { id -> _uiState.update { it.copy(recordId = id, autoSaveStatus = "تم الحفظ تلقائياً") } }
                    .onFailure { _uiState.update { it.copy(autoSaveStatus = "تعذر الحفظ التلقائي") } }
            }
        }
    }

    private fun edit(transform: (WorkMinutesUiState) -> WorkMinutesUiState) {
        _uiState.update { transform(it).copy(autoSaveStatus = "جارٍ الحفظ...") }
        draftSaver.changed()
        autoSaveRequests.tryEmit(Unit)
    }

    fun reviewCaseUpdates() {
        val caseId = _uiState.value.caseId ?: return
        viewModelScope.launch {
            runCatching {
                val case = caseRepository.getById(caseId) ?: error("تعذر العثور على القضية")
                val state = _uiState.value
                val current = mapOf("رقم الدعوى" to state.caseNo, "السنة" to state.caseYear, "المحكمة" to state.court,
                    "المدعون" to state.plaintiffsSummary, "المدعى عليهم" to state.defendantsSummary)
                val updates = com.khabir.app.domain.model.CaseDocumentFields.minutes(case).mapNotNull { (key, value) ->
                    if (current[key] == value) null else com.khabir.app.domain.model.CaseFieldUpdate(key, current[key].orEmpty(), value)
                }
                _uiState.update { it.copy(caseUpdates = updates, autoSaveStatus = if (updates.isEmpty()) "بيانات القضية مطابقة للمحفوظ" else it.autoSaveStatus) }
            }.onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "تعذر مراجعة القضية") } }
        }
    }
    fun dismissCaseUpdates() = _uiState.update { it.copy(caseUpdates = emptyList()) }
    fun applyCaseUpdates(selected: Set<String>) {
        edit { state ->
            val changes = state.caseUpdates.filter { it.key in selected }.associate { it.key to it.proposed }
            state.copy(caseNo = changes["رقم الدعوى"] ?: state.caseNo, caseYear = changes["السنة"] ?: state.caseYear,
                court = changes["المحكمة"] ?: state.court, plaintiffsSummary = changes["المدعون"] ?: state.plaintiffsSummary,
                defendantsSummary = changes["المدعى عليهم"] ?: state.defendantsSummary,
                caseUpdates = emptyList())
        }
    }

    fun onCaseNoChanged(value: String) = edit { it.copy(caseNo = value) }
    fun onCaseYearChanged(value: String) = edit { it.copy(caseYear = value) }
    fun onCourtChanged(value: String) = edit { it.copy(court = value) }
    fun onPlaintiffsChanged(value: String) = edit { it.copy(plaintiffsSummary = value) }
    fun onDefendantsChanged(value: String) = edit { it.copy(defendantsSummary = value) }

    fun onAddEntry() = edit { state ->
        val nextNumber = (state.entries.maxOfOrNull { it.number } ?: 0) + 1
        val previous = state.entries.lastOrNull()
        val defaultDate = previous?.scheduledFollowUpDate ?: caseReceiptDate.takeIf { state.entries.isEmpty() }
        val fromScheduledAppointment = previous?.scheduledFollowUpDate != null
        val newEntry = WorkMinutesEntry(
            number = nextNumber,
            openingDate = defaultDate,
            openingTime = if (fromScheduledAppointment) previous?.scheduledFollowUpTime.orEmpty() else "",
            bodyText = if (fromScheduledAppointment) WorkMinutesPhrases.ATTENDANCE_AND_DISCUSSION else "",
            expertName = defaultExpertName
        )
        state.copy(entries = state.entries + newEntry, expandedEntryNumber = nextNumber)
    }

    fun onRemoveEntry(number: Int) = edit { state ->
        val remaining = state.entries.filter { it.number != number }.sortedBy { it.number }
            .mapIndexed { index, entry -> entry.copy(number = index + 1) }
        state.copy(entries = remaining, expandedEntryNumber = null)
    }

    fun onEntryExpand(number: Int) = _uiState.update { it.copy(expandedEntryNumber = number) }

    fun onEntryChanged(number: Int, transform: (WorkMinutesEntry) -> WorkMinutesEntry) = edit { state ->
        state.copy(entries = state.entries.map { if (it.number == number) transform(it) else it })
    }

    fun appendToEntry(number: Int, text: String) {
        if (text.isBlank()) return
        onEntryChanged(number) { entry ->
            val merged = listOf(entry.bodyText.trim(), text.trim()).filter { it.isNotBlank() }.joinToString("\n\n")
            entry.copy(bodyText = merged)
        }
    }

    fun onCopiesCountChanged(value: Int) = edit { state -> state.copy(copiesCount = value.coerceIn(1, 20)) }
    fun onErrorMessageConsumed() = _uiState.update { it.copy(errorMessage = null) }
    fun onExportConsumed() = _uiState.update { it.copy(exportedFileUri = null) }

    fun refineVoiceTranscript(text: String, onReady: (String) -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = geminiVision.refineTranscript(text, LegalDocumentPurpose.REPORT)) {
                is GeminiDocumentVisionService.Result.Success -> onReady(result.text)
                is GeminiDocumentVisionService.Result.Failure -> {
                    _uiState.update { it.copy(errorMessage = "تعذر تنقيح الصوت: ${result.message}. تم الاحتفاظ بالنص الأصلي.") }
                    onReady(text)
                }
                is GeminiDocumentVisionService.Result.Unavailable -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    onReady(text)
                }
            }
        }
    }

    fun onDocumentPagesCaptured(entryNumber: Int, pageFiles: List<File>, useAi: Boolean) {
        if (pageFiles.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCaptureProcessing = true,
                    errorMessage = null,
                    captureTargetEntryNumber = entryNumber,
                    captureReviewText = "",
                    captureReviewSource = ""
                )
            }
            retryFiles = pageFiles.take(10)
            retryPageRead = { onDocumentPagesCaptured(entryNumber, pageFiles, useAi) }
            val result = multiPageReader.read(pageFiles.take(10), LegalDocumentPurpose.REPORT, useAi)
            _uiState.update { it.copy(canRetryPages = result.text.isBlank()) }
            _uiState.update { state ->
                if (result.text.isBlank()) state.copy(
                    isCaptureProcessing = false,
                    errorMessage = result.warnings.joinToString("\n").takeIf(String::isNotBlank) ?: "لم يتم استخراج نص من صور محضر الأعمال"
                ) else state.copy(
                    isCaptureProcessing = false,
                    captureReviewText = result.text,
                    captureReviewSource = if (useAi) "AI Vision — ${result.pagesRead} صفحة" else "OCR — ${result.pagesRead} صفحة",
                    errorMessage = if (result.usedLocalFallback) "استخدم التطبيق OCR المحلي لبعض الصور؛ راجع النص قبل الاعتماد." else null
                )
            }
        }
    }

    fun onCaptureReviewChanged(value: String) = _uiState.update { it.copy(captureReviewText = value) }

    fun onCaptureReviewDismissed() = _uiState.update {
        it.copy(captureReviewText = "", captureReviewSource = "", captureTargetEntryNumber = null)
    }

    fun onCaptureReviewAccepted() {
        val state = _uiState.value
        val number = state.captureTargetEntryNumber ?: return
        val text = state.captureReviewText.trim()
        if (text.isBlank()) return
        onCaptureReviewDismissed()
        appendToEntry(number, text)
    }

    private suspend fun resolveCover(state: WorkMinutesUiState, profile: com.khabir.app.domain.model.ExpertProfile): ReportCoverFields? {
        val linkedCase = state.caseId?.let { caseRepository.getById(it) }
        return when {
            linkedCase != null -> ReportCoverFields.from(linkedCase, profile).copy(
                caseNo = state.caseNo, caseYear = state.caseYear, court = state.court,
                plaintiffsSummary = state.plaintiffsSummary, defendantsSummary = state.defendantsSummary
            )
            state.caseNo.isNotBlank() && state.caseYear.isNotBlank() -> ReportCoverFields(
                court = state.court,
                caseNo = state.caseNo,
                caseYear = state.caseYear,
                incomingNo = "",
                incomingYear = "",
                expertName = profile.expertName,
                ministryOrSector = profile.ministryOrSector,
                department = profile.department,
                plaintiffsSummary = state.plaintiffsSummary,
                defendantsSummary = state.defendantsSummary,
                partiesSummary = state.plaintiffsSummary,
                caseType = state.court
            )
            else -> null
        }
    }

    fun onSave() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { draftSaver.flush() }
                .onSuccess { _uiState.update { it.copy(isSaving = false, autoSaveStatus = "تم الحفظ") } }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "تعذر الحفظ") } }
        }
    }

    fun onExport() {
        val state = _uiState.value
        if (state.entries.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "أضف محضر أعمال واحد على الأقل قبل التصدير") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            try {
            draftSaver.flush()
            val profile = expertProfileRepository.get()
            if (profile == null) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "أكمل بيانات الخبير أولًا") }
                return@launch
            }
            val cover = resolveCover(state, profile)
            if (cover == null) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "أكمل بيانات الدعوى أولًا") }
                return@launch
            }
            val safeCaseNo = cover.caseNo.ifBlank { "جديد" }
            val safeCaseYear = cover.caseYear.ifBlank { "بدون_سنة" }
            val entriesWithExpert = state.entries.map { if (it.expertName.isBlank()) it.copy(expertName = profile.expertName) else it }
            val uri = exportRepository.exportWorkMinutesAsWord(
                fileNamePrefix = "محاضر_اعمال_${safeCaseNo}_${safeCaseYear}",
                ministry = "وزارة العدل",
                sector = cover.ministryOrSector,
                department = cover.department,
                incomingNo = if (cover.incomingNo.isBlank()) "" else "${cover.incomingNo}/${cover.incomingYear}",
                caseIntro = "فى الدعوى رقم",
                caseNo = cover.caseNo,
                caseYear = cover.caseYear,
                court = cover.court,
                plaintiffs = cover.plaintiffsSummary,
                defendants = cover.defendantsSummary,
                entries = entriesWithExpert
            )
            _uiState.update { it.copy(isExporting = false, exportedFileUri = uri, autoSaveStatus = "تم الحفظ") }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, errorMessage = e.message ?: "تعذر حفظ وتصدير المحاضر") }
            }
        }
    }

    fun onSelectWordTemplate(uri: Uri) {
        viewModelScope.launch {
            runCatching { officeInterop.templateParagraphs(uri) to officeInterop.canAutoFillBlankReportFields(uri) }
                .onSuccess { (paragraphs, hasBlankFields) ->
                    if (hasBlankFields || paragraphs.any { it.contains("{{") || it.contains("«") }) onFillWordTemplate(uri)
                    else _uiState.update { it.copy(pendingTemplateUri = uri, templateParagraphs = paragraphs) }
                }.onFailure { e -> _uiState.update { it.copy(errorMessage = e.message ?: "تعذر قراءة القالب") } }
        }
    }

    fun onCancelTemplateMapping() = _uiState.update { it.copy(pendingTemplateUri = null, templateParagraphs = emptyList()) }

    fun onApplyTemplateMapping(mapping: Map<String, String>) {
        val uri = _uiState.value.pendingTemplateUri ?: return
        if (mapping.isEmpty()) return
        onCancelTemplateMapping()
        onFillWordTemplate(uri, mapping)
    }

    fun onFillWordTemplate(uri: Uri, mapping: Map<String, String> = emptyMap()) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, errorMessage = null) }
            val state = _uiState.value
            val id = runCatching { draftSaver.flush() }.getOrElse { e ->
                _uiState.update { it.copy(isExporting = false, errorMessage = e.message ?: "تعذر حفظ محاضر الأعمال") }
                return@launch
            }
            val profile = expertProfileRepository.get()
            if (profile == null) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "أكمل بيانات الخبير أولًا") }
                return@launch
            }
            val cover = resolveCover(state, profile)
            if (cover == null) {
                _uiState.update { it.copy(isExporting = false, errorMessage = "أكمل بيانات الدعوى أولًا") }
                return@launch
            }
            val entriesWithExpert = state.entries.map { if (it.expertName.isBlank()) it.copy(expertName = profile.expertName) else it }
                .sortedBy { it.number }
            val entriesText = entriesWithExpert.joinToString("\n\n") { entry -> entryText(entry) }
            val replacements = linkedMapOf(
                "رقم_الدعوى" to cover.caseNo, "رقم الدعوى" to cover.caseNo,
                "السنة" to cover.caseYear, "سنة_الدعوى" to cover.caseYear,
                "المحكمة" to cover.court,
                "القطاع" to cover.ministryOrSector, "الإدارة" to cover.department,
                "الخبير" to profile.expertName, "اسم_الخبير" to profile.expertName, "اسم الخبير" to profile.expertName,
                "المرفوعة من" to cover.plaintiffsSummary, "المرفوعـة من" to cover.plaintiffsSummary,
                "ضد" to cover.defendantsSummary,
                "الوارد" to if (cover.incomingNo.isBlank()) "" else "${cover.incomingNo}/${cover.incomingYear}",
                "عدد_النسخ" to state.copiesCount.toString(), "عدد النسخ" to state.copiesCount.toString(),
                "محاضر_الأعمال" to entriesText, "محاضر الأعمال" to entriesText,
                "متن_المحاضر" to entriesText, "متن المحاضر" to entriesText,
                "مجموعة_محاضر_الأعمال" to entriesText, "مجموعة محاضر اعمال" to entriesText
            )
            entriesWithExpert.forEach { entry ->
                replacements.putIfAbsent("محضر_${entry.number}", entryText(entry))
                replacements.putIfAbsent("محضر ${entry.number}", entryText(entry))
            }
            val safeCaseNo = cover.caseNo.ifBlank { "جديد" }
            val safeCaseYear = cover.caseYear.ifBlank { "بدون_سنة" }
            runCatching { officeInterop.fillDocxTemplate(uri, replacements, "محاضر_اعمال_من_قالب_${safeCaseNo}_${safeCaseYear}", mapping) }
                .onSuccess { outputUri ->
                    personalAiKeyStore.writeWorkMinutesTemplateUri(uri.toString())
                    personalAiKeyStore.writeWorkMinutesTemplateMapping(mapping)
                    _uiState.update {
                        it.copy(
                            recordId = id,
                            isExporting = false,
                            savedWordTemplateUri = uri.toString(),
                            autoSaveStatus = "تم حفظ قالب Word الشخصي لمحاضر الأعمال وإنشاء النسخة",
                            exportedFileUri = outputUri
                        )
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(recordId = id, isExporting = false, errorMessage = e.message ?: "تعذر تعبئة قالب Word") } }
        }
    }

    fun onUseSavedWordTemplate() {
        val value = _uiState.value.savedWordTemplateUri
        if (value.isBlank()) return
        onFillWordTemplate(Uri.parse(value), personalAiKeyStore.readWorkMinutesTemplateMapping())
    }

    fun onForgetWordTemplate() {
        personalAiKeyStore.clearWorkMinutesTemplate()
        _uiState.update { it.copy(savedWordTemplateUri = "", autoSaveStatus = "تم حذف قالب Word الشخصي لمحاضر الأعمال") }
    }

    private fun entryText(entry: WorkMinutesEntry): String {
        val dateFormat = java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")
        val sb = StringBuilder("محضر اعمال رقم (${entry.number})\n")
        sb.append("فتح هذا المحضر اليوم")
        entry.openingDate?.let { sb.append(" ${it.format(dateFormat)}") }
        if (entry.openingTime.isNotBlank()) sb.append(" الساعة ${entry.openingTime}")
        sb.append(" بالمكتب")
        if (entry.bodyText.isNotBlank()) sb.append(" ${entry.bodyText}")
        entry.scheduledFollowUpDate?.let { followUp ->
            sb.append("\nوحددنا يوم ${followUp.format(dateFormat)}")
            if (entry.scheduledFollowUpTime.isNotBlank()) sb.append(" الساعة ${entry.scheduledFollowUpTime}")
            if (entry.scheduledFollowUpLocation.isNotBlank()) sb.append(" ${entry.scheduledFollowUpLocation}")
            sb.append(" موعدًا لمتابعة مباشرة المأمورية.")
        }
        if (entry.closingTime.isNotBlank()) sb.append("\nواقفل المحضر على ذلك فى تاريخه الساعة ${entry.closingTime} بالمكتب")
        if (entry.expertName.isNotBlank()) sb.append("\nالخبير/ ${entry.expertName}")
        return sb.toString()
    }
}

private fun WorkMinutesUiState.toRecord() = WorkMinutesRecord(
    id = recordId,
    caseId = caseId,
    caseNo = caseNo,
    caseYear = caseYear,
    court = court,
    plaintiffsSummary = plaintiffsSummary,
    defendantsSummary = defendantsSummary,
    entries = entries,
    copiesCount = copiesCount
)
