package com.khabir.app.presentation.workminutes

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.data.export.OfficeInteropService
import com.khabir.app.domain.model.ReportCoverFields
import com.khabir.app.domain.model.WorkMinutesEntry
import com.khabir.app.domain.model.WorkMinutesRecord
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
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val autoSaveStatus: String = "",
    val errorMessage: String? = null,
    val exportedFileUri: Uri? = null,
    val savedWordTemplateUri: String = "",
    val pendingTemplateUri: Uri? = null,
    val templateParagraphs: List<String> = emptyList()
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
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(WorkMinutesUiState())
    val uiState: StateFlow<WorkMinutesUiState> = _uiState.asStateFlow()
    private val autoSaveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
                runCatching { saveWorkMinutes(state.toRecord()) }
                    .onSuccess { id -> _uiState.update { it.copy(recordId = id, autoSaveStatus = "تم الحفظ تلقائياً") } }
                    .onFailure { _uiState.update { it.copy(autoSaveStatus = "تعذر الحفظ التلقائي") } }
            }
        }
    }

    private fun edit(transform: (WorkMinutesUiState) -> WorkMinutesUiState) {
        _uiState.update { transform(it).copy(autoSaveStatus = "جارٍ الحفظ...") }
        autoSaveRequests.tryEmit(Unit)
    }

    fun onCaseNoChanged(value: String) = edit { it.copy(caseNo = value) }
    fun onCaseYearChanged(value: String) = edit { it.copy(caseYear = value) }
    fun onCourtChanged(value: String) = edit { it.copy(court = value) }
    fun onPlaintiffsChanged(value: String) = edit { it.copy(plaintiffsSummary = value) }
    fun onDefendantsChanged(value: String) = edit { it.copy(defendantsSummary = value) }

    /** إضافة محضر جديد برقم تسلسلي تلقائي، وتاريخ افتراضي مشتق من قاعدة الاستلام/التحديد الموصوفة. */
    fun onAddEntry() = edit { state ->
        val nextNumber = (state.entries.maxOfOrNull { it.number } ?: 0) + 1
        val previous = state.entries.lastOrNull()
        val defaultDate = previous?.scheduledFollowUpDate
            ?: caseReceiptDate.takeIf { state.entries.isEmpty() }
        val newEntry = WorkMinutesEntry(
            number = nextNumber,
            openingDate = defaultDate,
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

    fun onCopiesCountChanged(value: Int) = edit { state -> state.copy(copiesCount = value.coerceIn(1, 20)) }

    fun onErrorMessageConsumed() = _uiState.update { it.copy(errorMessage = null) }
    fun onExportConsumed() = _uiState.update { it.copy(exportedFileUri = null) }

    /** يحل بيانات غلاف الدعوى (وزارة/قطاع/إدارة/خصوم...) من القضية المرتبطة أو من الحقول اليدوية. */
    private suspend fun resolveCover(state: WorkMinutesUiState, profile: com.khabir.app.domain.model.ExpertProfile): ReportCoverFields? {
        val linkedCase = state.caseId?.let { caseRepository.getById(it) }
        return when {
            linkedCase != null -> ReportCoverFields.from(linkedCase, profile)
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

    fun onExport() {
        val state = _uiState.value
        if (state.entries.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "أضف محضر أعمال واحد على الأقل قبل التصدير") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
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
            _uiState.update { it.copy(isExporting = false, exportedFileUri = uri) }
        }
    }

    /**
     * قالب Word خارجي لمحاضر الأعمال — نفس آلية `OfficeInteropService.fillDocxTemplate`
     * المستخدمة فعليًا في التقارير: تعبئة تلقائية للخانات الفارغة أسفل عناوين معروفة، أو
     * `{{الحقل}}`/`«الحقل»` في أي مكان بالقالب، أو ربط يدوي لفقرات موجودة عبر [onApplyTemplateMapping]
     * لو محتاج القالب يفضل شكله زي ما هو بدون علامات.
     */
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
            val id = runCatching { saveWorkMinutes(state.toRecord()) }.getOrElse { e ->
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
                            recordId = id, isExporting = false,
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

    /** نفس منطق فتح/غلق المحضر المستخدم فى `WorkMinutesDocxBuilder`، بصيغة نصية عادية بدل XML. */
    private fun entryText(entry: WorkMinutesEntry): String {
        val dateFormat = java.time.format.DateTimeFormatter.ofPattern("d/M/yyyy")
        val sb = StringBuilder("محضر اعمال رقم (${entry.number})\n")
        sb.append("فتح هذا المحضر اليوم")
        entry.openingDate?.let { sb.append(" ${it.format(dateFormat)}") }
        if (entry.openingTime.isNotBlank()) sb.append(" الساعة ${entry.openingTime}")
        sb.append(" بالمكتب")
        if (entry.bodyText.isNotBlank()) sb.append(" ${entry.bodyText}")
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
