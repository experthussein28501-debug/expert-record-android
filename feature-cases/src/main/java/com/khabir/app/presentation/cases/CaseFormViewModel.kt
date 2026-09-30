package com.khabir.app.presentation.cases

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.ocr.ArabicPetitionOcrService
import com.khabir.app.data.ocr.MultiPageDocumentReader
import com.khabir.app.data.ai.GeminiDocumentVisionService
import com.khabir.app.data.ai.LegalDocumentPurpose
import com.khabir.app.domain.model.*
import com.khabir.app.domain.usecase.cases.GetCaseUseCase
import com.khabir.app.domain.usecase.cases.SaveCaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.io.File
import javax.inject.Inject

data class PartyDraft(
    val localId: Long,
    val firstName: String = "",
    val restName: String = "",
    val role: PartyRole = PartyRole.OTHER,
    val withCapacity: Boolean = false,
    val address: String = "",
    val claimKind: String = "أصلية"
)

internal fun PartyDraft.forNextParty(): PartyDraft = copy(localId = 0L, firstName = "")

data class CaseFormUiState(
    val caseId: Long = 0L,
    val incomingNo: String = "",
    val incomingDate: LocalDate = LocalDate.now(),
    val caseNo: String = "",
    val caseYear: String = LocalDate.now().year.toString(),
    val court: String = "",
    val caseType: String = "",
    val subjectOfCase: String = "",
    val finalRequests: String = "",
    val preliminaryMission: String = "",
    val receiptDate: LocalDate? = null,
    val preliminaryJudgmentDate: LocalDate? = null,
    val hearingDate: LocalDate? = null,
    val hearingTime: String = "",
    val parties: List<PartyDraft> = emptyList(),
    val partyEntry: PartyDraft = PartyDraft(localId = 0L),
    val adminNotes: String = "",
    val isSaving: Boolean = false,
    val validationErrors: List<CaseValidationError> = emptyList(),
    val savedSuccessfully: Boolean = false,
    val voiceReviewText: String? = null,
    val reviewSourceLabel: String = "الإملاء الصوتي",
    val voiceAppliedMessage: String? = null,
    val isOcrProcessing: Boolean = false,
    val ocrMessage: String? = null,
    val pendingPagePaths: List<String> = emptyList(),
    val documentReview: List<ReviewedDocument> = emptyList(),
    val selectedDocumentIds: Set<Int> = emptySet(),
    val partyConflicts: List<Pair<PartyDraft, PartyDraft>> = emptyList(),
    val aiFailureMessage: String? = null
)

@HiltViewModel
class CaseFormViewModel @Inject constructor(
    private val getCase: GetCaseUseCase,
    private val saveCase: SaveCaseUseCase,
    private val arabicOcr: ArabicPetitionOcrService,
    private val geminiVision: GeminiDocumentVisionService,
    private val multiPageReader: MultiPageDocumentReader,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaseFormUiState())
    val uiState: StateFlow<CaseFormUiState> = _uiState.asStateFlow()
    private var nextId = 1L

    init {
        val id: Long = savedStateHandle.get<Long>("caseId") ?: 0L
        if (id != 0L) viewModelScope.launch {
            getCase(id)?.let { c ->
                _uiState.value = CaseFormUiState(
                    caseId = c.id,
                    incomingNo = c.incomingNo,
                    incomingDate = c.incomingDate,
                    caseNo = c.caseNo,
                    caseYear = c.caseYear,
                    court = c.court,
                    caseType = c.caseType,
                    subjectOfCase = c.subjectOfCase,
                    finalRequests = c.finalRequests,
                    preliminaryMission = c.preliminaryMission,
                    receiptDate = c.receiptDate,
                    preliminaryJudgmentDate = c.preliminaryJudgmentDate,
                    hearingDate = c.hearingDate,
                    hearingTime = c.hearingTime,
                    parties = c.parties.map { p -> PartyDraft(nextId++, p.firstName, p.restName, p.role, p.withCapacity, p.address, p.claimKind) },
                    adminNotes = c.adminNotes
                )
            }
        }
    }

    fun onIncomingNoChanged(v: String) = _uiState.update { it.copy(incomingNo = v, savedSuccessfully = false) }
    fun onIncomingDateChanged(v: LocalDate) = _uiState.update { it.copy(incomingDate = v) }
    fun onCaseNoChanged(v: String) = _uiState.update { it.copy(caseNo = v) }
    fun onCaseYearChanged(v: String) = _uiState.update { it.copy(caseYear = v) }
    fun onCourtChanged(v: String) = _uiState.update { it.copy(court = v) }
    fun onCaseTypeChanged(v: String) = _uiState.update { it.copy(caseType = v) }
    fun onSubjectOfCaseChanged(v: String) = _uiState.update { it.copy(subjectOfCase = v, finalRequests = "") }
    fun onFinalRequestsChanged(v: String) = _uiState.update { it.copy(finalRequests = v) }
    fun onPreliminaryMissionChanged(v: String) = _uiState.update { it.copy(preliminaryMission = v) }
    fun onReceiptDateChanged(v: LocalDate?) = _uiState.update { it.copy(receiptDate = v) }
    fun onPreliminaryJudgmentDateChanged(v: LocalDate?) = _uiState.update { it.copy(preliminaryJudgmentDate = v) }
    fun onHearingDateChanged(v: LocalDate?) = _uiState.update { it.copy(hearingDate = v) }
    fun onHearingTimeChanged(v: String) = _uiState.update { it.copy(hearingTime = v) }
    fun onAdminNotesChanged(v: String) = _uiState.update { it.copy(adminNotes = v) }

    fun updatePartyEntry(fn: (PartyDraft) -> PartyDraft) = _uiState.update { state ->
        state.copy(partyEntry = fn(state.partyEntry))
    }

    fun savePartyEntry() = _uiState.update { state ->
        val entry = state.partyEntry
        if (entry.firstName.isBlank() && entry.restName.isBlank()) return@update state
        val saved = entry.copy(localId = nextId++, address = if (entry.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(entry.address) else entry.address)
        state.copy(
            parties = state.parties + saved,
            partyEntry = entry.forNextParty()
        )
    }

    fun updateParty(id: Long, update: (PartyDraft) -> PartyDraft) = _uiState.update { state -> state.copy(parties = state.parties.map { if (it.localId == id) update(it) else it }) }

    fun removeParty(id: Long) = _uiState.update { it.copy(parties = it.parties.filterNot { p -> p.localId == id }) }

    fun onVoiceRecognitionResult(text: String) {
        _uiState.update {
            it.copy(
                voiceReviewText = text,
                reviewSourceLabel = "الإملاء الصوتي",
                voiceAppliedMessage = null
            )
        }
        viewModelScope.launch {
            when (val result = geminiVision.refineTranscript(text, LegalDocumentPurpose.PETITION)) {
                is GeminiDocumentVisionService.Result.Success -> _uiState.update { it.copy(voiceReviewText = result.text, reviewSourceLabel = "الإملاء الصوتي + Gemini") }
                else -> Unit
            }
        }
    }

    fun onVoiceRecognitionResultWithoutAi(text: String) {
        _uiState.update {
            it.copy(
                voiceReviewText = text,
                reviewSourceLabel = "الإملاء الصوتي من الهاتف",
                voiceAppliedMessage = null
            )
        }
    }

    fun onPetitionPhotoCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, ocrMessage = "جارٍ قراءة العريضة العربية...") }
            when (val result = arabicOcr.recognize(bitmap)) {
                is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                    it.copy(
                        isOcrProcessing = false,
                        ocrMessage = "تم استخراج النص. راجعه قبل تعبئة الخانات.",
                        voiceReviewText = result.text,
                        reviewSourceLabel = "OCR العريضة"
                    )
                }
                is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                    it.copy(isOcrProcessing = false, ocrMessage = result.message)
                }
            }
        }
    }

    fun onPetitionPhotoCapturedWithGemini(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, ocrMessage = "جارٍ تحليل العريضة بواسطة Gemini...") }
            when (val result = geminiVision.analyze(bitmap, LegalDocumentPurpose.PETITION)) {
                is GeminiDocumentVisionService.Result.Success -> _uiState.update {
                    it.copy(isOcrProcessing = false, ocrMessage = "تم تحليل الصورة. راجع البيانات قبل الحفظ.", voiceReviewText = result.text, reviewSourceLabel = "Gemini Vision")
                }
                is GeminiDocumentVisionService.Result.Unavailable -> useLocalOcrFallback(bitmap, result.message)
                is GeminiDocumentVisionService.Result.Failure -> useLocalOcrFallback(bitmap, result.message)
            }
        }
    }

    fun onDocumentPagesCaptured(pageFiles: List<File>, useAi: Boolean) {
        onDocumentPagesAdded(pageFiles)
        if (useAi) analyzePendingDocumentsWithAi() else analyzePendingDocumentsLocally()
    }

    fun onDocumentPagesAdded(pageFiles: List<File>) {
        if (_uiState.value.isOcrProcessing || _uiState.value.selectedDocumentIds.isNotEmpty()) { pageFiles.forEach { it.delete() }; return }
        _uiState.update { state ->
            val available = (10 - state.pendingPagePaths.size).coerceAtLeast(0)
            val accepted = pageFiles.take(available)
            pageFiles.drop(available).forEach { it.delete() }
            state.copy(
                pendingPagePaths = state.pendingPagePaths + accepted.map(File::getAbsolutePath),
                documentReview = emptyList(),
                aiFailureMessage = null,
                ocrMessage = if (accepted.isEmpty()) "تم الوصول إلى الحد الأقصى: 10 صور" else "تمت إضافة ${accepted.size} صورة؛ راجع الترتيب ثم ابدأ التحليل"
            )
        }
    }

    fun removePendingPage(index: Int) {
        if (_uiState.value.isOcrProcessing || _uiState.value.selectedDocumentIds.isNotEmpty()) return
        _uiState.update { state ->
            state.pendingPagePaths.getOrNull(index)?.let { File(it).delete() }
            state.copy(
                pendingPagePaths = state.pendingPagePaths.filterIndexed { position, _ -> position != index },
                documentReview = emptyList(),
                aiFailureMessage = null
            )
        }
    }

    fun movePendingPage(index: Int, offset: Int) {
        if (_uiState.value.isOcrProcessing || _uiState.value.selectedDocumentIds.isNotEmpty()) return
        _uiState.update { state ->
            val target = index + offset
            if (index !in state.pendingPagePaths.indices || target !in state.pendingPagePaths.indices) return@update state
            val reordered = state.pendingPagePaths.toMutableList()
            val item = reordered.removeAt(index)
            reordered.add(target, item)
            state.copy(pendingPagePaths = reordered, documentReview = emptyList(), aiFailureMessage = null)
        }
    }

    fun analyzePendingDocumentsWithAi() {
        if (_uiState.value.isOcrProcessing || _uiState.value.selectedDocumentIds.isNotEmpty()) return
        val files = _uiState.value.pendingPagePaths.map(::File).filter(File::exists)
        if (files.isEmpty()) {
            _uiState.update { it.copy(ocrMessage = "أضف صورة واحدة على الأقل قبل التحليل") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, aiFailureMessage = null, ocrMessage = "جارٍ فحص الصور وتحديد المستندات...") }
            val result = multiPageReader.read(
                pageFiles = files,
                purpose = LegalDocumentPurpose.PETITION,
                useAi = true,
                fallbackToLocal = false,
                deleteAfterRead = false,
                detectMultipleDocuments = true
            )
            _uiState.update {
                if (result.text.isBlank()) it.copy(
                    isOcrProcessing = false,
                    aiFailureMessage = result.aiError ?: result.warnings.joinToString("\n").takeIf(String::isNotBlank) ?: "لم يتم استخراج نص من الصور",
                    ocrMessage = null
                ) else it.copy(
                    isOcrProcessing = false,
                    documentReview = DocumentReviewParser.parse(result.text),
                    aiFailureMessage = null,
                    ocrMessage = "تم فحص ${result.pagesRead} صورة وتقسيمها إلى مستندات للمراجعة"
                )
            }
        }
    }

    fun analyzePendingDocumentsLocally() {
        if (_uiState.value.isOcrProcessing || _uiState.value.selectedDocumentIds.isNotEmpty()) return
        val files = _uiState.value.pendingPagePaths.map(::File).filter(File::exists)
        if (files.isEmpty()) {
            _uiState.update { it.copy(ocrMessage = "أضف صورة واحدة على الأقل قبل الاستخراج المحلي") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isOcrProcessing = true, aiFailureMessage = null, ocrMessage = "جارٍ تشغيل OCR العربي المحلي...") }
            val result = multiPageReader.read(
                pageFiles = files,
                purpose = LegalDocumentPurpose.PETITION,
                useAi = false,
                deleteAfterRead = false
            )
            _uiState.update {
                if (result.text.isBlank()) it.copy(
                    isOcrProcessing = false,
                    ocrMessage = result.warnings.joinToString("\n").takeIf(String::isNotBlank) ?: "لم يتم استخراج نص من الصور"
                ) else it.copy(
                    isOcrProcessing = false,
                    voiceReviewText = result.text,
                    reviewSourceLabel = "OCR عربي محلي",
                    aiFailureMessage = null,
                    ocrMessage = "تمت قراءة ${result.pagesRead} صورة محليًا؛ راجع النص قبل الاعتماد"
                )
            }
        }
    }

    fun useReviewedDocument(documentId: Int) {
        val state = _uiState.value
        val document = state.documentReview.firstOrNull { it.id == documentId } ?: return
        val group = DocumentReviewParser.matchingGroup(state.documentReview, document)
        useReviewedDocuments(group.map { it.id }.toSet())
    }

    fun useReviewedDocuments(documentIds: Set<Int>) {
        val state = _uiState.value
        val group = state.documentReview.filter { it.id in documentIds }
        if (group.isEmpty() || group.size != documentIds.size) return
        _uiState.update {
            it.copy(
                voiceReviewText = DocumentReviewParser.combinedText(group),
                reviewSourceLabel = "مراجعة ${group.size} مستند للقضية المختارة",
                selectedDocumentIds = group.map { doc -> doc.id }.toSet(),
                aiFailureMessage = null
            )
        }
    }

    fun editReviewedDocument(documentId: Int, text: String) {
        val parsed = PetitionIntakeParser.parse(text)
        _uiState.update { state -> state.copy(documentReview = state.documentReview.map { doc ->
            if (doc.id != documentId) doc else doc.copy(rawText = text, caseNo = parsed.caseNo,
                caseYear = parsed.caseYear, court = parsed.court,
                primaryPartyNames = parsed.parties.map { it.name },
                status = DocumentMatchStatus.UNCERTAIN, reason = "تم تعديل البيانات؛ ستعاد المطابقة عند اختيار القضية")
        }) }
    }

    fun excludeReviewedDocument(documentId: Int) = removeReviewedDocuments(setOf(documentId))

    private fun removeReviewedDocuments(ids: Set<Int>) {
        val state = _uiState.value
        val remaining = state.documentReview.filterNot { it.id in ids }
        val keptPages = remaining.flatMap { it.pageNumbers }.toSet()
        val removedPages = state.documentReview.filter { it.id in ids }.flatMap { it.pageNumbers }.toSet() - keptPages
        val oldToNew = mutableMapOf<Int, Int>()
        val paths = state.pendingPagePaths.filterIndexed { index, path ->
            if (index + 1 in removedPages) { File(path).delete(); false }
            else { oldToNew[index + 1] = oldToNew.size + 1; true }
        }
        _uiState.update { it.copy(
            pendingPagePaths = paths,
            documentReview = remaining.map { doc -> doc.copy(pageNumbers = doc.pageNumbers.mapNotNull(oldToNew::get)) },
            selectedDocumentIds = it.selectedDocumentIds - ids
        ) }
    }

    fun dismissDocumentReview() = _uiState.update { it.copy(documentReview = emptyList()) }

    fun clearPendingDocuments() {
        if (_uiState.value.isOcrProcessing) return
        val hadDocuments = _uiState.value.pendingPagePaths.isNotEmpty()
        _uiState.value.pendingPagePaths.forEach { File(it).delete() }
        _uiState.update { it.copy(pendingPagePaths = emptyList(), documentReview = emptyList(), aiFailureMessage = null) }
        if (hadDocuments) com.khabir.app.data.monetization.WorkAdEvents.finished()
    }

    private suspend fun useLocalOcrFallback(bitmap: Bitmap, reason: String) {
        when (val local = arabicOcr.recognize(bitmap)) {
            is ArabicPetitionOcrService.Result.Success -> _uiState.update {
                it.copy(isOcrProcessing = false, ocrMessage = "$reason — تم استخدام OCR العربي المحلي.", voiceReviewText = local.text, reviewSourceLabel = "OCR محلي")
            }
            is ArabicPetitionOcrService.Result.Failure -> _uiState.update {
                it.copy(isOcrProcessing = false, ocrMessage = "$reason — ${local.message}")
            }
        }
    }

    fun clearOcrMessage() = _uiState.update { it.copy(ocrMessage = null) }
    fun cancelVoiceReview() {
        _uiState.update { it.copy(voiceReviewText = null, selectedDocumentIds = emptySet()) }
    }

    fun applyVoiceReview(
        editedText: String,
        reviewedParties: List<PetitionIntakeParser.ParsedParty>? = null
    ) {
        val current = _uiState.value
        val parsed = PetitionIntakeParser.parse(editedText)
        val acceptedParties = reviewedParties ?: parsed.parties
        val newParties = acceptedParties.map { item ->
            val parts = item.name.trim().split(Regex("\\s+"), limit = 2)
            PartyDraft(
                localId = nextId++,
                firstName = parts.firstOrNull().orEmpty(),
                restName = parts.getOrNull(1).orEmpty(),
                role = item.role,
                withCapacity = item.withCapacity,
                address = item.address.trim(),
                claimKind = item.claimKind
            )
        }.filter { it.firstName.isNotBlank() || it.restName.isNotBlank() }
        val changed = mutableListOf<String>()
        if (parsed.incomingNo != null) changed += "الوارد"
        if (parsed.incomingDate != null) changed += "تاريخ الوارد"
        if (parsed.caseNo != null) changed += "رقم الدعوى"
        if (parsed.caseYear != null) changed += "السنة"
        if (parsed.court != null) changed += "المحكمة"
        if (parsed.caseType != null) changed += "نوع الدعوى"
        if (parsed.receiptDate != null) changed += "تاريخ الاستلام"
        if (parsed.preliminaryJudgmentDate != null) changed += "تاريخ الحكم التمهيدي"
        if (parsed.subjectOfCase != null) changed += "موضوع الدعوى"
        if (parsed.finalRequests != null) changed += "الطلبات الختامية"
        if (parsed.preliminaryMission != null) changed += "مأمورية الحكم التمهيدي"
        if (newParties.isNotEmpty()) changed += "${newParties.size} خصم"

        val merged = PartyDraftMerger.merge(current.parties, newParties)
        _uiState.value = current.copy(
            partyConflicts = current.partyConflicts + merged.conflicts,
            incomingNo = parsed.incomingNo ?: current.incomingNo,
            incomingDate = parsed.incomingDate ?: current.incomingDate,
            caseNo = parsed.caseNo ?: current.caseNo,
            caseYear = parsed.caseYear ?: if (current.selectedDocumentIds.isNotEmpty() && current.caseId == 0L) "" else current.caseYear,
            court = parsed.court ?: current.court,
            caseType = parsed.caseType ?: current.caseType,
            subjectOfCase = parsed.subjectOfCase ?: current.subjectOfCase,
            finalRequests = parsed.finalRequests ?: current.finalRequests,
            preliminaryMission = parsed.preliminaryMission ?: current.preliminaryMission,
            receiptDate = parsed.receiptDate ?: current.receiptDate,
            preliminaryJudgmentDate = parsed.preliminaryJudgmentDate ?: current.preliminaryJudgmentDate,
            parties = merged.parties,
            adminNotes = listOfNotNull(parsed.notes).joinToString("\n").takeIf(String::isNotBlank)?.let { note ->
                if (current.adminNotes.isBlank()) note else current.adminNotes + "\n" + note
            } ?: current.adminNotes,
            voiceReviewText = null,
            voiceAppliedMessage = if (changed.isEmpty()) {
                "لم أتعرف على خانات محددة؛ أضفت النص إلى الملاحظات"
            } else {
                "تم تعبئة: ${changed.joinToString("، ")}"
            }
        )
        if (changed.isEmpty() && editedText.isNotBlank()) {
            _uiState.update { it.copy(adminNotes = listOf(it.adminNotes, editedText).filter(String::isNotBlank).joinToString("\n")) }
        }
    }

    fun clearVoiceMessage() = _uiState.update { it.copy(voiceAppliedMessage = null) }

    fun onSave(onSaved: (Long) -> Unit = {}) {
        val s = _uiState.value
        if (s.partyConflicts.isNotEmpty()) return
        val c = Case(
            id = s.caseId,
            incomingNo = s.incomingNo.trim(),
            incomingDate = s.incomingDate,
            caseNo = s.caseNo.trim(),
            caseYear = s.caseYear.trim(),
            court = s.court.trim(),
            caseType = s.caseType.trim(),
            subjectOfCase = s.subjectOfCase.trim(),
            finalRequests = s.finalRequests.trim(),
            preliminaryMission = s.preliminaryMission.trim(),
            adminNotes = s.adminNotes.trim(),
            parties = s.parties.filter { it.firstName.isNotBlank() || it.restName.isNotBlank() }.mapIndexed { i, p ->
                Party(
                    firstName = p.firstName.trim(),
                    restName = p.restName.trim(),
                    role = p.role,
                    address = if (p.role == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(p.address) else p.address.trim(),
                    orderIndex = i,
                    withCapacity = p.withCapacity,
                    claimKind = p.claimKind
                )
            },
            receiptDate = s.receiptDate,
            preliminaryJudgmentDate = s.preliminaryJudgmentDate,
            hearingDate = s.hearingDate,
            hearingTime = s.hearingTime.trim()
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val r = saveCase(c)) {
                is SaveCaseUseCase.Result.Success -> {
                    _uiState.update { it.copy(caseId = r.caseId, isSaving = false, savedSuccessfully = true, validationErrors = emptyList()) }
                    if (s.selectedDocumentIds.isNotEmpty()) removeReviewedDocuments(s.selectedDocumentIds)
                    val remaining = _uiState.value
                    if (remaining.documentReview.isNotEmpty()) {
                        _uiState.value = CaseFormUiState(pendingPagePaths = remaining.pendingPagePaths,
                            documentReview = remaining.documentReview,
                            ocrMessage = "تم حفظ القضية؛ اختر المجموعة التالية للمراجعة")
                    } else {
                        clearPendingDocuments()
                        onSaved(r.caseId)
                        com.khabir.app.data.monetization.WorkAdEvents.finished()
                    }
                }
                is SaveCaseUseCase.Result.Invalid -> _uiState.update { it.copy(isSaving = false, validationErrors = r.errors) }
            }
        }
    }

    fun resolvePartyConflict(choice: Int) = _uiState.update { state ->
        val conflict = state.partyConflicts.firstOrNull() ?: return@update state
        val parties = when (choice) {
            1 -> state.parties.map { if (it.localId == conflict.first.localId) it.copy(address = conflict.second.address) else it }
            2 -> state.parties + conflict.second
            else -> state.parties
        }
        state.copy(parties = parties, partyConflicts = state.partyConflicts.drop(1))
    }

    override fun onCleared() {
        _uiState.value.pendingPagePaths.forEach { File(it).delete() }
        super.onCleared()
    }

}
