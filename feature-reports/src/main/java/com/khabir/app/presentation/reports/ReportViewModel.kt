package com.khabir.app.presentation.reports

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.export.OfficeInteropService
import com.khabir.app.data.report.ReportSketchStore
import com.khabir.app.data.ocr.ArabicPetitionOcrService
import com.khabir.app.data.ocr.MultiPageDocumentReader
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.LegalDocumentPurpose
import com.khabir.app.data.ai.PersonalAiKeyStore
import com.khabir.app.domain.model.Report
import com.khabir.app.domain.model.ReportCustomSectionCodec
import com.khabir.app.domain.model.ReportSectionDefinition
import com.khabir.app.domain.model.ReportTemplateKind
import com.khabir.app.domain.model.ReportTemplate
import com.khabir.app.domain.model.ReportTemplateCatalog
import com.khabir.app.domain.model.ReportTemplateCodec
import com.khabir.app.domain.model.orderedTemplateSections
import com.khabir.app.domain.usecase.report.ExportReportToPdfUseCase
import com.khabir.app.domain.usecase.report.ExportReportToWordUseCase
import com.khabir.app.domain.usecase.report.GetOrCreateReportUseCase
import com.khabir.app.domain.usecase.report.SaveReportUseCase
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
import java.io.File
import javax.inject.Inject

data class ReportUiState(
    val reportId: Long = 0L,
    val caseId: Long? = null,
    val caseNo: String = "",
    val caseYear: String = "",
    val court: String = "",
    val manualHeader: String = "",
    val template: ReportTemplate = ReportTemplateCatalog.civil,
    val customSectionContents: Map<String, String> = emptyMap(),
    val partiesSummary: String = "",
    val subjectOfCase: String = "",
    val assignment: String = "",
    val proceedings: String = "",
    val partyStatements: String = "",
    val witnessStatements: String = "",
    val inspection: String = "",
    val documentsSubmitted: String = "",
    val facts: String = "",
    val research: String = "",
    val technicalOpinion: String = "",
    val calculationsTable: String = "",
    val siteSketchPath: String = "",
    val conclusion: String = "",
    val attachmentsNote: String = "",
    val depositDate: LocalDate? = null,
    val updatedAt: Long = 0L,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isExporting: Boolean = false,
    val isOcrProcessing: Boolean = false,
    val pendingReportPages: List<File> = emptyList(),
    val autoSaveStatus: String = "",
    val errorMessage: String? = null,
    val exportedFileUri: Uri? = null,
    val exportedMimeType: String? = null,
    val importedOfficeText: String? = null,
    val importedOfficeSource: String? = null,
    val pendingExcelImport: Map<String, String> = emptyMap(),
    val assistantReply: String = "",
    val isAssistantWorking: Boolean = false,
    val learningStatus: String = "",
    val pendingLearningText: String = "",
    val finalRequestsPlacement: FinalRequestsPlacement = FinalRequestsPlacement.START,
    val savedWordTemplateUri: String = "",
    val pendingTemplateUri: Uri? = null,
    val templateParagraphs: List<String> = emptyList()
) {
    val isIndependent: Boolean get() = caseId == null
}

@HiltViewModel
@OptIn(FlowPreview::class)
class ReportViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val getOrCreateReport: GetOrCreateReportUseCase,
    private val saveReport: SaveReportUseCase,
    private val exportReportToWord: ExportReportToWordUseCase,
    private val exportReportToPdf: ExportReportToPdfUseCase,
    private val officeInterop: OfficeInteropService,
    private val reportSketchStore: ReportSketchStore,
    private val arabicOcr: ArabicPetitionOcrService,
    private val geminiVision: GeminiDocumentVisionService,
    private val personalAiKeyStore: PersonalAiKeyStore,
    private val multiPageReader: MultiPageDocumentReader,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    private val autoSaveRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        val reportId = savedStateHandle.get<Long>("reportId") ?: 0L
        val rawCaseId = savedStateHandle.get<Long>("caseId") ?: 0L
        val caseId = rawCaseId.takeIf { it > 0L }
        viewModelScope.launch {
            val report = getOrCreateReport(reportId, caseId)
            personalAiKeyStore.migrateLegacyReportTemplate(report.templateId)
            _uiState.update {
                it.fromReport(report).copy(
                    isLoading = false,
                    savedWordTemplateUri = personalAiKeyStore.readReportTemplateUri(report.templateId)
                )
            }
        }
        viewModelScope.launch {
            autoSaveRequests.debounce(1200).collect {
                val state = _uiState.value
                if (state.isLoading || state.isExporting) return@collect
                runCatching { saveReport(state.toReport()) }
                    .onSuccess { id -> _uiState.update { it.copy(reportId = id, autoSaveStatus = "تم الحفظ تلقائياً") } }
                    .onFailure { _uiState.update { it.copy(autoSaveStatus = "تعذر الحفظ التلقائي") } }
            }
        }
    }

    private fun edit(transform: (ReportUiState) -> ReportUiState) {
        _uiState.update { transform(it).copy(autoSaveStatus = "جارٍ الحفظ...") }
        autoSaveRequests.tryEmit(Unit)
    }

    fun onCaseNoChanged(v: String) = edit { it.copy(caseNo = v) }
    fun onCaseYearChanged(v: String) = edit { it.copy(caseYear = v) }
    fun onCourtChanged(v: String) = edit { it.copy(court = v) }
    fun onManualHeaderChanged(v: String) = edit { it.copy(manualHeader = v) }
    fun onTemplateSelected(templateId: String) = edit { state ->
        val selected = ReportTemplateCatalog.all.firstOrNull { it.id == templateId } ?: return@edit state
        state.copy(
            template = selected,
            savedWordTemplateUri = personalAiKeyStore.readReportTemplateUri(selected.id)
        )
    }
    fun onTemplateNameChanged(value: String) = edit { it.copy(template = it.template.copy(name = value)) }
    fun onSectionTitleChanged(sectionId: String, value: String) = edit { it.copy(template = it.template.renameSection(sectionId, value)) }
    fun onSectionEnabledChanged(sectionId: String, enabled: Boolean) = edit { it.copy(template = it.template.setSectionEnabled(sectionId, enabled)) }
    fun onSectionMoved(sectionId: String, targetIndex: Int) = edit { it.copy(template = it.template.moveSection(sectionId, targetIndex)) }
    fun onSectionHeadingAdded(sectionId: String) = edit { it.copy(template = it.template.addHeadingLine(sectionId)) }
    fun onSectionHeadingChanged(sectionId: String, index: Int, value: String) = edit { it.copy(template = it.template.updateHeadingLine(sectionId, index, value)) }
    fun onSectionHeadingRemoved(sectionId: String, index: Int) = edit { it.copy(template = it.template.removeHeadingLine(sectionId, index)) }
    fun onSectionAdded(title: String) = edit { state ->
        if (title.isBlank()) state else state.copy(template = state.template.addSection(title))
    }
    fun onSectionRemoved(sectionId: String) = edit { state ->
        state.copy(
            template = state.template.deleteSection(sectionId),
            customSectionContents = state.customSectionContents - sectionId
        )
    }
    fun onCustomSectionChanged(sectionId: String, value: String) = edit { state ->
        state.copy(customSectionContents = state.customSectionContents + (sectionId to value))
    }
    fun onPartiesSummaryChanged(v: String) = edit { it.copy(partiesSummary = v) }
    fun onSubjectChanged(v: String) = edit { it.copy(subjectOfCase = v) }
    fun onFinalRequestsPlacementChanged(value: FinalRequestsPlacement) = edit { state ->
        state.copy(
            finalRequestsPlacement = value,
            subjectOfCase = CaseSubjectFormatter.reorderFormatted(state.subjectOfCase, value)
        )
    }
    fun onAssignmentChanged(v: String) = edit { it.copy(assignment = v) }
    fun onProceedingsChanged(v: String) = edit { it.copy(proceedings = v) }
    fun onPartyStatementsChanged(v: String) = edit { it.copy(partyStatements = v) }
    fun onWitnessStatementsChanged(v: String) = edit { it.copy(witnessStatements = v) }
    fun onInspectionChanged(v: String) = edit { it.copy(inspection = v) }
    fun onDocumentsChanged(v: String) = edit { it.copy(documentsSubmitted = v) }
    fun onFactsChanged(v: String) = edit { it.copy(facts = v) }
    fun onResearchChanged(v: String) = edit { it.copy(research = v) }
    fun onOpinionChanged(v: String) = edit { it.copy(technicalOpinion = v) }
    fun onCalculationsChanged(v: String) = edit { it.copy(calculationsTable = v) }
    fun onSiteSketchSaved(bitmap: Bitmap) {
        viewModelScope.launch {
            runCatching { reportSketchStore.save(bitmap) }
                .onSuccess { path ->
                    val previous = _uiState.value.siteSketchPath
                    edit { it.copy(siteSketchPath = path) }
                    if (previous.isNotBlank() && previous != path) reportSketchStore.delete(previous)
                }
                .onFailure { error -> _uiState.update { it.copy(errorMessage = error.message ?: "تعذر حفظ المخطط") } }
        }
    }
    fun onSiteSketchRemoved() {
        val previous = _uiState.value.siteSketchPath
        edit { it.copy(siteSketchPath = "") }
        reportSketchStore.delete(previous)
    }
    fun onConclusionChanged(v: String) = edit { it.copy(conclusion = v) }
    fun onAttachmentsNoteChanged(v: String) = edit { it.copy(attachmentsNote = v) }
    fun onDepositDateChanged(v: LocalDate?) = edit { it.copy(depositDate = v) }

    fun onReportPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            when (val result = arabicOcr.recognize(bitmap)) {
                is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                    it.copy(
                        isOcrProcessing = false,
                        importedOfficeText = result.text,
                        importedOfficeSource = "صورة OCR"
                    )
                }
                is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                    it.copy(isOcrProcessing = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onReportPhotoCapturedWithGemini(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            when (val result = geminiVision.analyze(bitmap, LegalDocumentPurpose.REPORT)) {
                is GeminiDocumentVisionService.Result.Success -> _uiState.update {
                    it.copy(isOcrProcessing = false, importedOfficeText = result.text, importedOfficeSource = "Gemini Vision")
                }
                is GeminiDocumentVisionService.Result.Unavailable -> useLocalOcrFallback(bitmap, result.message)
                is GeminiDocumentVisionService.Result.Failure -> useLocalOcrFallback(bitmap, result.message)
            }
        }
    }

    private val documentResults = mutableMapOf<String, String>()

    fun prepareReportDocuments(pages: List<File>) {
        if (_uiState.value.isOcrProcessing) return
        documentResults.clear()
        _uiState.update { it.copy(pendingReportPages = pages.take(10), errorMessage = null) }
    }

    fun cancelReportDocuments() {
        if (_uiState.value.isOcrProcessing) return
        _uiState.value.pendingReportPages.forEach { it.delete() }
        documentResults.clear()
        _uiState.update { it.copy(pendingReportPages = emptyList(), errorMessage = null) }
        com.khabir.app.data.monetization.WorkAdEvents.finished()
    }

    fun analyzeRequestedDocuments(documents: List<ReportDocumentRequest>) {
        if (_uiState.value.isOcrProcessing) return
        val expected = _uiState.value.pendingReportPages
        if (documents.flatMap { it.pages } != expected || expected.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            try {
                val texts = documents.mapIndexed { index, document ->
                    val cacheKey = document.pages.joinToString("|") { it.path } + "\n" + document.instruction
                    val resultText = documentResults[cacheKey] ?: run {
                        val bitmaps = mutableListOf<Bitmap>()
                        try {
                            for (file in document.pages) {
                                val bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.khabir.app.presentation.common.decodeCameraBitmap(context, Uri.fromFile(file), 1400)
                                } ?: error("تعذر فتح صورة من المستند ${index + 1}")
                                bitmaps += bitmap
                            }
                            when (val result = geminiVision.analyzeReportDocument(bitmaps, document.instruction)) {
                                is GeminiDocumentVisionService.Result.Success -> result.text.also { documentResults[cacheKey] = it }
                                is GeminiDocumentVisionService.Result.Failure -> error("المستند ${index + 1}: ${result.message}")
                                is GeminiDocumentVisionService.Result.Unavailable -> error(result.message)
                            }
                        } finally { bitmaps.forEach { it.recycle() } }
                    }
                    "المستند ${index + 1}\n$resultText"
                }
                expected.forEach { it.delete() }; documentResults.clear()
                _uiState.update { it.copy(pendingReportPages = emptyList(), importedOfficeText = texts.joinToString("\n\n"), importedOfficeSource = "نتائج المستندات") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { _uiState.update { it.copy(errorMessage = error.message ?: "تعذر تحليل المستندات") } }
            finally { _uiState.update { it.copy(isOcrProcessing = false) } }
        }
    }

    fun onDocumentPagesCaptured(pageFiles: List<File>, useAi: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            val result = multiPageReader.read(pageFiles, LegalDocumentPurpose.REPORT, useAi)
            _uiState.update {
                if (result.text.isBlank()) it.copy(
                    isOcrProcessing = false,
                    errorMessage = result.warnings.firstOrNull() ?: "لم يتم استخراج نص من الصفحات"
                ) else it.copy(
                    isOcrProcessing = false,
                    importedOfficeText = result.text,
                    importedOfficeSource = "مستند متعدد الصفحات (${result.pagesRead} صفحة)",
                    errorMessage = if (result.usedLocalFallback) "استُخدم OCR المحلي لبعض الصفحات؛ راجع النص قبل الاعتماد." else null
                )
            }
        }
    }

    fun onPetitionSubjectPagesCaptured(pageFiles: List<File>, useAi: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, errorMessage = null) }
            val result = multiPageReader.read(pageFiles, LegalDocumentPurpose.PETITION_SUBJECT, useAi)
            _uiState.update {
                if (result.text.isBlank()) it.copy(
                    isOcrProcessing = false,
                    errorMessage = result.warnings.firstOrNull() ?: "لم يتم استخراج موضوع الدعوى من الصفحات"
                ) else it.copy(
                    isOcrProcessing = false,
                    importedOfficeText = CaseSubjectFormatter.format(result.text),
                    importedOfficeSource = "موضوع الدعوى — مراجعة قبل الاعتماد",
                    errorMessage = if (result.usedLocalFallback) "استُخدم OCR المحلي لبعض الصفحات؛ راجع الطلبات والشرح." else null
                )
            }
        }
    }

    suspend fun transcribeAudio(file: java.io.File): GeminiDocumentVisionService.Result = geminiVision.transcribeAudio(file)

    fun refineVoiceTranscript(text: String, onReady: (String) -> Unit) {
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

    fun assistantContext(): String = _uiState.value.toReport().let { report ->
        buildString {
            append("القضية: ").append(report.caseNo).append(" لسنة ").append(report.caseYear).append(" — ").append(report.court)
            append("\nالخصوم:\n").append(report.partiesSummary)
            report.orderedTemplateSections().forEach { (heading, content) ->
                if (content.isNotBlank()) append("\n\n").append(heading).append(":\n").append(content)
            }
        }
    }

    fun learnedRules(): String = personalAiKeyStore.readReportStyleMemory()
    fun expertInstructions(): String = personalAiKeyStore.readInstructions()

    fun onImportLearningReport(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(learningStatus = "جارٍ قراءة التقرير المعتمد…", errorMessage = null) }
            runCatching { officeInterop.readDocxText(uri) }
                .onSuccess { text ->
                    if (text.isBlank()) {
                        _uiState.update { it.copy(learningStatus = "", errorMessage = "لم يتم العثور على نص داخل تقرير Word") }
                    } else {
                        _uiState.update {
                            it.copy(
                                pendingLearningText = text,
                                learningStatus = "راجع التقرير قبل استخراج أسلوب الصياغة"
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(learningStatus = "", errorMessage = e.message ?: "تعذر قراءة تقرير Word للتعلّم") }
                }
        }
    }

    fun onLearningTextChanged(value: String) = _uiState.update { it.copy(pendingLearningText = value) }
    fun onLearningImportConsumed() = _uiState.update { it.copy(pendingLearningText = "") }

    fun onAskAssistant(request: String, approvedContext: String, approvedRules: String) {
        if (request.isBlank() || _uiState.value.isAssistantWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAssistantWorking = true, assistantReply = "", errorMessage = null) }
            when (val result = geminiVision.assistReport(request, approvedContext, approvedRules)) {
                is GeminiDocumentVisionService.Result.Success -> _uiState.update {
                    it.copy(isAssistantWorking = false, assistantReply = result.text)
                }
                is GeminiDocumentVisionService.Result.Unavailable -> _uiState.update {
                    it.copy(isAssistantWorking = false, errorMessage = result.message)
                }
                is GeminiDocumentVisionService.Result.Failure -> _uiState.update {
                    it.copy(isAssistantWorking = false, errorMessage = result.message)
                }
            }
        }
    }

    fun onAssistantReplyChanged(value: String) = _uiState.update { it.copy(assistantReply = value) }
    fun onAssistantReplyConsumed() = _uiState.update { it.copy(assistantReply = "") }

    /** Saves only rules the expert has reviewed and explicitly approved. */
    fun onLearnFromCurrentReport(approvedRules: String) {
        if (approvedRules.isBlank()) {
            _uiState.update { it.copy(learningStatus = "لا توجد قواعد لاعتمادها") }
            return
        }
        personalAiKeyStore.writeReportStyleMemory(approvedRules)
        _uiState.update { it.copy(learningStatus = "تم اعتماد قواعد الصياغة فقط؛ لم يُحفظ نص القضية") }
    }

    fun onDeriveStyleRulesFromApprovedReport(approvedSample: String, onReady: (String) -> Unit) {
        if (approvedSample.isBlank()) {
            _uiState.update { it.copy(learningStatus = "لا يوجد نص تقرير لاستخراج الأسلوب منه") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(learningStatus = "جارٍ استخراج قواعد الصياغة دون حفظ بيانات القضية…") }
            when (val result = geminiVision.deriveReportStyleRules(approvedSample)) {
                is GeminiDocumentVisionService.Result.Success -> {
                    _uiState.update { it.copy(learningStatus = "راجع القواعد المقترحة قبل اعتمادها") }
                    onReady(result.text)
                }
                is GeminiDocumentVisionService.Result.Unavailable ->
                    _uiState.update { it.copy(learningStatus = result.message) }
                is GeminiDocumentVisionService.Result.Failure ->
                    _uiState.update { it.copy(learningStatus = result.message) }
            }
        }
    }

    fun onClearReportLearning() {
        personalAiKeyStore.clearReportStyleMemory()
        _uiState.update { it.copy(learningStatus = "تم مسح أسلوب التقارير المحفوظ") }
    }

    private suspend fun useLocalOcrFallback(bitmap: Bitmap, reason: String) {
        when (val local = arabicOcr.recognize(bitmap)) {
            is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                it.copy(isOcrProcessing = false, importedOfficeText = local.text, importedOfficeSource = "OCR محلي", errorMessage = "$reason — تم استخدام OCR المحلي.")
            }
            is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                it.copy(isOcrProcessing = false, errorMessage = "$reason — ${local.message}")
            }
        }
    }

    fun onSave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching { saveReport(_uiState.value.toReport()) }
                .onSuccess { id -> _uiState.update { it.copy(reportId = id, isSaving = false, autoSaveStatus = "تم الحفظ") }; com.khabir.app.data.monetization.WorkAdEvents.finished() }
                .onFailure { e -> _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "تعذر حفظ التقرير") } }
        }
    }

    fun onImportWord(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { officeInterop.readDocxText(uri) }
                .onSuccess { text -> _uiState.update { it.copy(isLoading = false, importedOfficeText = text, importedOfficeSource = "Word", errorMessage = if (text.isBlank()) "لم يتم العثور على نص داخل ملف Word" else null) } }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "تعذر استيراد Word") } }
        }
    }

    fun onImportPowerPoint(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { officeInterop.readPptxText(uri) }
                .onSuccess { text ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            importedOfficeText = text,
                            importedOfficeSource = "PowerPoint",
                            errorMessage = if (text.isBlank()) "لم يتم العثور على نص داخل ملف PowerPoint" else null
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "تعذر استيراد PowerPoint") }
                }
        }
    }

    fun onImportEditableReportTemplate(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            runCatching { officeInterop.templateParagraphs(uri) }
                .onSuccess { paragraphs ->
                    val detected = detectEditableTemplateSections(paragraphs)
                    if (detected.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "لم أتعرف على عناوين تقرير قانونية داخل القالب. استخدم «قالب Word بنفس التنسيق» أو عدّل عناوين الملف."
                            )
                        }
                    } else {
                        val template = ReportTemplate(
                            id = "imported_${System.currentTimeMillis()}",
                            name = "قالب مستورد للعمل عليه",
                            kind = ReportTemplateKind.CUSTOM,
                            sections = detected,
                            isBuiltIn = false,
                            verifiedFromUserReports = false,
                            referenceReport = "قالب Word مستورد بواسطة المستخدم"
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                template = template,
                                savedWordTemplateUri = "",
                                autoSaveStatus = "تم تحويل قالب Word إلى هيكل تقرير قابل للتعديل"
                            )
                        }
                        autoSaveRequests.tryEmit(Unit)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "تعذر تحويل قالب Word إلى تقرير قابل للتعديل") }
                }
        }
    }

    private fun detectEditableTemplateSections(paragraphs: List<String>): List<ReportSectionDefinition> {
        val headings = linkedMapOf(
            "subject" to listOf("الموضوع"),
            "assignment" to listOf("المأمورية", "المامورية"),
            "proceedings" to listOf("مباشرة المأمورية", "مباشرة المامورية"),
            "statements" to listOf("الأقوال", "اقوال", "أقوال المدعي", "أقوال المستأنفين"),
            "witnesses" to listOf("سماع الشهود", "أقوال الشهود"),
            "inspection" to listOf("المعاينة", "المعاينه"),
            "documents" to listOf("بحث المستندات", "فحص المستندات", "المستندات"),
            "facts" to listOf("الوقائع", "الحكم المستأنف", "التقارير السابقة"),
            "research" to listOf("البحث", "البحث والدراسة"),
            "calculations" to listOf("الحسابات", "حساب الريع", "حساب نصيب"),
            "conclusion" to listOf("النتيجة النهائية", "النتيجه النهائيه", "النتيجة")
        )
        val normalizedParagraphs = paragraphs
            .map { it.replace("ـ", "").replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }

        val found = mutableListOf<ReportSectionDefinition>()
        val used = mutableSetOf<String>()
        normalizedParagraphs.forEach { paragraph ->
            headings.entries.firstOrNull { (id, labels) ->
                id !in used && labels.any { label -> paragraph.equals(label, true) || paragraph.startsWith(label, true) }
            }?.let { (id, labels) ->
                used += id
                val title = when (id) {
                    "subject" -> "الموضوع"
                    "assignment" -> "المأمورية"
                    "proceedings" -> "مباشرة المأمورية"
                    "statements" -> "أقوال طرفي الخصومة"
                    "witnesses" -> "أقوال الشهود"
                    "inspection" -> "المعاينة على الطبيعة"
                    "documents" -> "بحث المستندات"
                    "facts" -> "الوقائع والتقارير السابقة"
                    "research" -> "البحث"
                    "calculations" -> "الحسابات والجداول"
                    "conclusion" -> "النتيجة النهائية"
                    else -> labels.first()
                }
                found += ReportSectionDefinition(
                    id = id,
                    title = title,
                    enabled = true,
                    removable = id != "conclusion",
                    orderIndex = found.size
                )
            }
        }
        if ("conclusion" !in used && found.isNotEmpty()) {
            found += ReportSectionDefinition("conclusion", "النتيجة النهائية", removable = false, orderIndex = found.size)
        }
        return found
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
            _uiState.update { it.copy(isSaving = true, isExporting = true, errorMessage = null) }
            val report = _uiState.value.toReport()
            val id = runCatching { saveReport(report) }.getOrElse { e ->
                finishExportFailure(e, "تعذر حفظ التقرير")
                return@launch
            }
            val selectedSections = report.orderedTemplateSections()
            val replacements = linkedMapOf(
                "رقم_الدعوى" to report.caseNo, "رقم الدعوى" to report.caseNo,
                "السنة" to report.caseYear, "سنة_الدعوى" to report.caseYear,
                "المحكمة" to report.court, "رأس_التقرير" to report.manualHeader, "رأس التقرير" to report.manualHeader,
                "اسم_القالب" to report.templateName, "اسم القالب" to report.templateName,
                "الخصوم" to report.partiesSummary,
                "الموضوع" to report.subjectOfCase, "المأمورية" to report.assignment,
                "مباشرة_المأمورية" to report.proceedings, "مباشرة المأمورية" to report.proceedings,
                "أقوال_الأطراف" to report.partyStatements, "أقوال طرفي التداعي" to report.partyStatements,
                "سماع_الشهود" to report.witnessStatements, "سماع الشهود" to report.witnessStatements,
                "المعاينة" to report.inspection, "المعاينة على الطبيعة" to report.inspection,
                "المستندات" to report.documentsSubmitted, "الإطلاع والمستندات" to report.documentsSubmitted, "بحث المستندات" to report.documentsSubmitted,
                "الوقائع" to report.facts, "البحث" to report.research,
                "الحسابات" to com.khabir.app.domain.model.ReportCalculationCodec.exportText(report.calculationsTable),
                "النتيجة" to report.conclusion, "النتيجة_النهائية" to report.conclusion,
                "المرفقات" to report.attachmentsNote, "تاريخ_الإيداع" to report.depositDate?.toString().orEmpty(),
                "بنود_القالب" to selectedSections.joinToString("\n\n") { (heading, content) -> "$heading\n$content".trim() },
                "بنود القالب" to selectedSections.joinToString("\n\n") { (heading, content) -> "$heading\n$content".trim() }
            )
            // A user-renamed or user-added section can be placed in the Word
            // template directly as {{عنوان البند}}. Known aliases above remain
            // authoritative for backwards compatibility.
            selectedSections.forEach { (heading, content) ->
                if (heading.isNotBlank()) replacements.putIfAbsent(heading, content)
            }
            runCatching { officeInterop.fillDocxTemplate(uri, replacements, "تقرير_من_قالب_${report.caseNo.ifBlank { "جديد" }}", mapping) }
                .onSuccess { outputUri ->
                    personalAiKeyStore.writeReportTemplateUri(report.templateId, uri.toString())
                    personalAiKeyStore.writeReportTemplateMapping(report.templateId, mapping)
                    _uiState.update { it.copy(savedWordTemplateUri = uri.toString(), reportId = id, isSaving = false, isExporting = false, autoSaveStatus = "تم حفظ قالب Word الشخصي لهذا النوع وإنشاء النسخة", exportedFileUri = outputUri, exportedMimeType = WORD_MIME) }
                }
                .onFailure { e -> finishExportFailure(e, "تعذر تعبئة قالب Word", id) }
        }
    }

    fun onUseSavedWordTemplate() {
        val value = _uiState.value.savedWordTemplateUri
        if (value.isBlank()) return
        val templateId = _uiState.value.template.id
        onFillWordTemplate(Uri.parse(value), personalAiKeyStore.readReportTemplateMapping(templateId))
    }

    fun onForgetWordTemplate() {
        val templateId = _uiState.value.template.id
        personalAiKeyStore.clearReportTemplate(templateId)
        _uiState.update { it.copy(savedWordTemplateUri = "", autoSaveStatus = "تم حذف قالب Word الشخصي لهذا النوع") }
    }

    fun onImportExcel(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, pendingExcelImport = emptyMap()) }
            runCatching { officeInterop.readXlsxRows(uri) }
                .onSuccess { rows ->
                    val pairs = rows.dropWhile { row -> row.firstOrNull()?.trim() in setOf("الحقل", "Field") }
                        .mapNotNull { row -> row.getOrNull(0)?.trim()?.takeIf(String::isNotBlank)?.let { it to row.getOrNull(1).orEmpty() } }
                        .toMap()
                        .filterKeys { it in KNOWN_EXCEL_FIELDS }
                    _uiState.update { it.copy(isLoading = false, pendingExcelImport = pairs, importedOfficeSource = if (pairs.isEmpty()) null else "Excel", errorMessage = if (pairs.isEmpty()) "لم يتم العثور على خانات معروفة في ملف Excel" else null) }
                }
                .onFailure { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "تعذر استيراد Excel") } }
        }
    }

    fun onApplyExcelImport() {
        val pairs = _uiState.value.pendingExcelImport
        if (pairs.isEmpty()) return
        edit { state ->
            state.copy(
                caseNo = pairs["رقم الدعوى"] ?: state.caseNo,
                caseYear = pairs["السنة"] ?: state.caseYear,
                court = pairs["المحكمة"] ?: state.court,
                manualHeader = pairs["رأس التقرير"] ?: state.manualHeader,
                partiesSummary = pairs["الخصوم"] ?: state.partiesSummary,
                subjectOfCase = pairs["الموضوع"] ?: state.subjectOfCase,
                assignment = pairs["المأمورية"] ?: state.assignment,
                proceedings = pairs["مباشرة المأمورية"] ?: state.proceedings,
                partyStatements = pairs["أقوال طرفي التداعي"] ?: state.partyStatements,
                witnessStatements = pairs["سماع الشهود"] ?: state.witnessStatements,
                inspection = pairs["المعاينة على الطبيعة"] ?: state.inspection,
                documentsSubmitted = pairs["بحث المستندات"] ?: pairs["الإطلاع والمستندات"] ?: state.documentsSubmitted,
                facts = pairs["الوقائع والملاحظات"] ?: state.facts,
                research = pairs["البحث"] ?: pairs["البحث والدراسة"] ?: state.research,
                calculationsTable = pairs["الحسابات والجداول"] ?: state.calculationsTable,
                conclusion = pairs["النتيجة النهائية"] ?: state.conclusion,
                attachmentsNote = pairs["ملاحظات المرفقات"] ?: state.attachmentsNote,
                pendingExcelImport = emptyMap(), importedOfficeSource = null
            )
        }
    }

    fun onCancelExcelImport() = _uiState.update { it.copy(pendingExcelImport = emptyMap(), importedOfficeSource = null) }
    fun onImportedOfficeTextConsumed() {
        _uiState.update { it.copy(importedOfficeText = null, importedOfficeSource = null) }
        com.khabir.app.data.monetization.WorkAdEvents.finished()
    }
    fun onExportWord() = exportWord()
    fun onExportPdf() = exportPdf()

    fun onExportExcel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isExporting = true, errorMessage = null) }
            val report = _uiState.value.toReport()
            val id = runCatching { saveReport(report) }.getOrElse { e ->
                finishExportFailure(e, "تعذر حفظ التقرير")
                return@launch
            }
            val rows = listOf(
                listOf("الحقل", "القيمة"), listOf("رقم الدعوى", report.caseNo), listOf("السنة", report.caseYear), listOf("المحكمة", report.court),
                listOf("رأس التقرير", report.manualHeader), listOf("الخصوم", report.partiesSummary), listOf("الموضوع", report.subjectOfCase), listOf("المأمورية", report.assignment),
                listOf("مباشرة المأمورية", report.proceedings), listOf("أقوال طرفي التداعي", report.partyStatements), listOf("سماع الشهود", report.witnessStatements), listOf("المعاينة على الطبيعة", report.inspection),
                listOf("بحث المستندات", report.documentsSubmitted), listOf("الوقائع والملاحظات", report.facts), listOf("البحث", report.research),
                listOf("الحسابات والجداول", com.khabir.app.domain.model.ReportCalculationCodec.exportText(report.calculationsTable)), listOf("النتيجة النهائية", report.conclusion), listOf("ملاحظات المرفقات", report.attachmentsNote)
            )
            runCatching { officeInterop.exportXlsx("بيانات_تقرير_${report.caseNo.ifBlank { "جديد" }}", rows) }
                .onSuccess { uri -> _uiState.update { it.copy(reportId = id, isSaving = false, isExporting = false, autoSaveStatus = "تم الحفظ", exportedFileUri = uri, exportedMimeType = EXCEL_MIME) } }
                .onFailure { e -> finishExportFailure(e, "تعذر تصدير Excel", id) }
        }
    }

    private fun exportWord() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isExporting = true, errorMessage = null) }
            val report = _uiState.value.toReport()
            val id = runCatching { saveReport(report) }.getOrElse { e ->
                finishExportFailure(e, "تعذر حفظ التقرير قبل إنشاء Word")
                return@launch
            }
            runCatching { exportReportToWord(report.copy(id = id)) }
                .onSuccess { result ->
                    when (result) {
                        is ExportReportToWordUseCase.Result.Success -> _uiState.update { it.copy(reportId = id, isSaving = false, isExporting = false, autoSaveStatus = "تم إنشاء تقرير خبراء وزارة العدل Word", exportedFileUri = result.uri, exportedMimeType = WORD_MIME) }
                        ExportReportToWordUseCase.Result.MissingCaseData -> exportError(id, "أكمل رقم الدعوى والسنة والمحكمة قبل التصدير")
                        ExportReportToWordUseCase.Result.MissingExpertProfile -> exportError(id, "أكمل بيانات الخبير الثابتة أولًا")
                    }
                }
                .onFailure { e -> finishExportFailure(e, "تعذر إنشاء ملف Word لتقرير خبراء وزارة العدل", id) }
        }
    }

    private fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, isExporting = true, errorMessage = null) }
            val report = _uiState.value.toReport()
            val id = runCatching { saveReport(report) }.getOrElse { e ->
                finishExportFailure(e, "تعذر حفظ التقرير قبل إنشاء PDF")
                return@launch
            }
            runCatching { exportReportToPdf(report.copy(id = id)) }
                .onSuccess { result ->
                    when (result) {
                        is ExportReportToPdfUseCase.Result.Success -> _uiState.update { it.copy(reportId = id, isSaving = false, isExporting = false, autoSaveStatus = "تم إنشاء تقرير خبراء وزارة العدل PDF", exportedFileUri = result.uri, exportedMimeType = PDF_MIME) }
                        ExportReportToPdfUseCase.Result.MissingCaseData -> exportError(id, "أكمل رقم الدعوى والسنة والمحكمة قبل التصدير")
                        ExportReportToPdfUseCase.Result.MissingExpertProfile -> exportError(id, "أكمل بيانات الخبير الثابتة أولًا")
                    }
                }
                .onFailure { e -> finishExportFailure(e, "تعذر إنشاء ملف PDF لتقرير خبراء وزارة العدل", id) }
        }
    }

    private fun finishExportFailure(error: Throwable, fallback: String, reportId: Long = _uiState.value.reportId) {
        _uiState.update {
            it.copy(
                reportId = reportId,
                isSaving = false,
                isExporting = false,
                exportedFileUri = null,
                exportedMimeType = null,
                errorMessage = error.message?.takeIf(String::isNotBlank) ?: fallback
            )
        }
    }

    private fun exportError(id: Long, message: String) = _uiState.update { it.copy(reportId = id, isSaving = false, isExporting = false, exportedFileUri = null, exportedMimeType = null, errorMessage = message) }
    fun onExportEventConsumed() = _uiState.update { it.copy(exportedFileUri = null, exportedMimeType = null) }

    companion object {
        const val WORD_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        const val PDF_MIME = "application/pdf"
        const val EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        const val POWERPOINT_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        private val KNOWN_EXCEL_FIELDS = setOf("رقم الدعوى", "السنة", "المحكمة", "رأس التقرير", "الخصوم", "الموضوع", "المأمورية", "مباشرة المأمورية", "أقوال طرفي التداعي", "سماع الشهود", "المعاينة على الطبيعة", "الإطلاع والمستندات", "بحث المستندات", "الوقائع والملاحظات", "البحث", "البحث والدراسة", "الحسابات والجداول", "النتيجة النهائية", "ملاحظات المرفقات")
    }
}

private fun ReportUiState.fromReport(r: Report) = copy(
    reportId = r.id, caseId = r.caseId, caseNo = r.caseNo, caseYear = r.caseYear, court = r.court, manualHeader = r.manualHeader,
    template = ReportTemplateCodec.decode(r.templateId, r.templateName, r.templateSectionsSpec),
    customSectionContents = ReportCustomSectionCodec.decode(r.customSectionContentsSpec), partiesSummary = r.partiesSummary,
    subjectOfCase = r.subjectOfCase, assignment = r.assignment, proceedings = r.proceedings, partyStatements = r.partyStatements, witnessStatements = r.witnessStatements,
    inspection = r.inspection, documentsSubmitted = r.documentsSubmitted, facts = r.facts, research = r.research,
    technicalOpinion = r.technicalOpinion, calculationsTable = r.calculationsTable, siteSketchPath = r.siteSketchPath, conclusion = r.conclusion, attachmentsNote = r.attachmentsNote, depositDate = r.depositDate, updatedAt = r.updatedAt
)

private fun ReportUiState.toReport() = Report(
    id = reportId, caseId = caseId, caseNo = caseNo, caseYear = caseYear, court = court, manualHeader = manualHeader,
    templateId = template.id, templateName = template.name, templateSectionsSpec = ReportTemplateCodec.encode(template),
    customSectionContentsSpec = ReportCustomSectionCodec.encode(customSectionContents), partiesSummary = partiesSummary,
    subjectOfCase = subjectOfCase, assignment = assignment, proceedings = proceedings, partyStatements = partyStatements, witnessStatements = witnessStatements,
    inspection = inspection, documentsSubmitted = documentsSubmitted, facts = facts, research = research, technicalOpinion = technicalOpinion,
    calculationsTable = calculationsTable, siteSketchPath = siteSketchPath, conclusion = conclusion, attachmentsNote = attachmentsNote, depositDate = depositDate, updatedAt = updatedAt
)
