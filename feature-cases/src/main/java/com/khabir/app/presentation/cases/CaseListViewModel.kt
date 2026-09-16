package com.khabir.app.presentation.cases

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.data.export.OfficeInteropService
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.CaseValidationError
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.usecase.cases.DeleteCaseUseCase
import com.khabir.app.domain.usecase.cases.SaveCaseUseCase
import com.khabir.app.domain.usecase.cases.SearchCasesUseCase
import com.khabir.app.domain.usecase.cases.caseImportDuplicateKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RejectedImportCase(
    val caseNo: String,
    val caseYear: String,
    val court: String,
    val incomingNo: String,
    val reasons: List<String>
)

data class CaseListUiState(
    val query: String = "",
    val cases: List<Case> = emptyList(),
    val isLoading: Boolean = true,
    val isOfficeBusy: Boolean = false,
    val officeMessage: String? = null,
    val exportedFileUri: Uri? = null,
    val showImportReview: Boolean = false,
    val pendingImportCases: List<Case> = emptyList(),
    val pendingImportDuplicates: Int = 0,
    val pendingImportRejected: Int = 0,
    val pendingRejectedCases: List<RejectedImportCase> = emptyList(),
    val showStatementDialog: Boolean = false,
    val statementType: CaseStatementType = CaseStatementType.CIVIL,
    val statementFromDate: LocalDate = LocalDate.now().withDayOfMonth(1),
    val statementToDate: LocalDate = LocalDate.now()
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CaseListViewModel @Inject constructor(
    private val searchCases: SearchCasesUseCase,
    private val deleteCase: DeleteCaseUseCase,
    private val saveCase: SaveCaseUseCase,
    private val officeInterop: OfficeInteropService
): ViewModel() {
    private val query = MutableStateFlow("")
    private val officeState = MutableStateFlow(CaseListUiState())

    private val casesFlow = query
        .debounce(200)
        .flatMapLatest { q -> searchCases(q).map { q to it } }

    val uiState: StateFlow<CaseListUiState> = combine(casesFlow, officeState) { (q, cases), office ->
        office.copy(query = q, cases = cases, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CaseListUiState())

    fun onQueryChanged(newQuery: String) { query.value = newQuery }
    fun onDeleteCase(caseId: Long) { viewModelScope.launch { deleteCase(caseId) } }

    fun openStatementDialog() = officeState.update { it.copy(showStatementDialog = true, officeMessage = null) }
    fun closeStatementDialog() = officeState.update { it.copy(showStatementDialog = false) }
    fun onStatementTypeChanged(type: CaseStatementType) = officeState.update { it.copy(statementType = type) }
    fun onStatementFromDateChanged(date: LocalDate) = officeState.update { it.copy(statementFromDate = date) }
    fun onStatementToDateChanged(date: LocalDate) = officeState.update { it.copy(statementToDate = date) }

    fun onExportStatement() {
        val state = officeState.value
        if (state.statementToDate.isBefore(state.statementFromDate)) {
            officeState.update { it.copy(officeMessage = "تاريخ النهاية يجب ألا يسبق تاريخ البداية") }
            return
        }
        viewModelScope.launch {
            officeState.update { it.copy(isOfficeBusy = true, officeMessage = null, showStatementDialog = false) }
            runCatching {
                val allCases = searchCases("").first()
                val table = CaseStatementTableBuilder.build(
                    cases = allCases,
                    type = state.statementType,
                    fromDate = state.statementFromDate,
                    toDate = state.statementToDate
                )
                if (table.rows.isEmpty()) error("لا توجد قضايا من هذا النوع خلال الفترة المحددة")
                officeInterop.exportXlsx(
                    fileNamePrefix = "بيان_${state.statementType.menuLabel.replace(' ', '_')}",
                    rows = table.asExcelRows()
                )
            }.onSuccess { uri ->
                officeState.update {
                    it.copy(
                        isOfficeBusy = false,
                        officeMessage = "تم تجهيز ${state.statementType.menuLabel} مرتبًا برقم الوارد",
                        exportedFileUri = uri
                    )
                }
            }.onFailure { e ->
                officeState.update { it.copy(isOfficeBusy = false, officeMessage = e.message ?: "تعذر استخراج بيان القضايا") }
            }
        }
    }

    fun onExportExcel() {
        val cases = uiState.value.cases
        viewModelScope.launch {
            officeState.update { it.copy(isOfficeBusy = true, officeMessage = null) }
            val rows = buildList {
                add(listOf("رقم الوارد", "تاريخ الوارد", "رقم الدعوى", "السنة", "المحكمة", "نوع الدعوى", "موضوع الدعوى", "الطلبات الختامية", "مأمورية الحكم التمهيدي", "الاسم الأول", "باقي الاسم", "الصفة", "العنوان", "ملاحظات", "الدعوى", "بصفته"))
                cases.forEach { case ->
                    if (case.parties.isEmpty()) {
                        add(listOf(case.incomingNo, case.incomingDate.toString(), case.caseNo, case.caseYear, case.court, case.caseType, case.subjectOfCase, case.finalRequests, case.preliminaryMission, "", "", "", "", case.adminNotes, "", ""))
                    } else {
                        case.parties.sortedBy { it.orderIndex }.forEach { party ->
                            add(listOf(case.incomingNo, case.incomingDate.toString(), case.caseNo, case.caseYear, case.court, case.caseType, case.subjectOfCase, case.finalRequests, case.preliminaryMission, party.firstName, party.restName, party.role.arabicLabel, party.address, case.adminNotes, party.claimKind, if (party.withCapacity) "نعم" else "لا"))
                        }
                    }
                }
            }
            runCatching { officeInterop.exportXlsx("القضايا_والخصوم", rows) }
                .onSuccess { uri -> officeState.update { it.copy(isOfficeBusy = false, officeMessage = "تم تجهيز ملف Excel", exportedFileUri = uri) } }
                .onFailure { e -> officeState.update { it.copy(isOfficeBusy = false, officeMessage = e.message ?: "تعذر تصدير Excel") } }
        }
    }

    fun onImportExcel(uri: Uri) {
        viewModelScope.launch {
            officeState.update {
                it.copy(
                    isOfficeBusy = true,
                    officeMessage = null,
                    showImportReview = false,
                    pendingImportCases = emptyList(),
                    pendingImportDuplicates = 0,
                    pendingImportRejected = 0,
                    pendingRejectedCases = emptyList()
                )
            }
            runCatching { officeInterop.readXlsxRows(uri) }
                .onSuccess { rows -> prepareImportReview(rows) }
                .onFailure { e -> officeState.update { it.copy(isOfficeBusy = false, officeMessage = e.message ?: "تعذر استيراد Excel") } }
        }
    }

    private suspend fun prepareImportReview(rows: List<List<String>>) {
        if (rows.size < 2) {
            officeState.update { it.copy(isOfficeBusy = false, officeMessage = "ملف Excel لا يحتوي بيانات") }
            return
        }
        val header = rows.first().map { it.trim() }
        fun indexOf(name: String) = header.indexOf(name)
        val required = listOf("رقم الوارد", "تاريخ الوارد", "رقم الدعوى", "السنة", "المحكمة")
        if (required.any { indexOf(it) < 0 }) {
            officeState.update { it.copy(isOfficeBusy = false, officeMessage = "تنسيق Excel غير معروف. استخدم ملفاً مصدراً من التطبيق") }
            return
        }
        fun cell(row: List<String>, name: String): String = indexOf(name).takeIf { it >= 0 }?.let { row.getOrNull(it).orEmpty().trim() }.orEmpty()

        val grouped = rows.drop(1).filter { row -> row.any { it.isNotBlank() } }.groupBy { row ->
            listOf(cell(row, "رقم الوارد"), cell(row, "تاريخ الوارد"), cell(row, "رقم الدعوى"), cell(row, "السنة"), cell(row, "المحكمة")).joinToString("|")
        }

        val knownCaseKeys = searchCases("").first().mapTo(mutableSetOf(), ::caseImportDuplicateKey)
        val workbookKeys = mutableSetOf<String>()
        val pending = mutableListOf<Case>()
        val rejectedCases = mutableListOf<RejectedImportCase>()
        var duplicates = 0

        grouped.values.forEach { group ->
            val first = group.first()
            val caseNo = cell(first, "رقم الدعوى")
            val caseYear = cell(first, "السنة")
            val court = cell(first, "المحكمة")
            val incomingNo = cell(first, "رقم الوارد")
            val incomingDateText = cell(first, "تاريخ الوارد")
            val incomingDate = runCatching { LocalDate.parse(incomingDateText) }.getOrNull()
            if (incomingDate == null) {
                rejectedCases += RejectedImportCase(caseNo, caseYear, court, incomingNo, listOf("تاريخ الوارد غير صالح أو غير مكتوب بصيغة صحيحة"))
                return@forEach
            }
            val parties = group.mapIndexedNotNull { index, row ->
                val firstName = cell(row, "الاسم الأول")
                val restName = cell(row, "باقي الاسم")
                if (firstName.isBlank() && restName.isBlank()) null else Party(
                    firstName = firstName,
                    restName = restName,
                    role = PartyRole.fromArabicLabel(cell(row, "الصفة")),
                    address = cell(row, "العنوان"),
                    orderIndex = index,
                    claimKind = cell(row, "الدعوى").ifBlank { "أصلية" },
                    withCapacity = cell(row, "بصفته") == "نعم"
                )
            }
            val case = Case(
                incomingNo = incomingNo,
                incomingDate = incomingDate,
                caseNo = caseNo,
                caseYear = caseYear,
                court = court,
                caseType = cell(first, "نوع الدعوى"),
                subjectOfCase = cell(first, "موضوع الدعوى"),
                finalRequests = cell(first, "الطلبات الختامية"),
                preliminaryMission = cell(first, "مأمورية الحكم التمهيدي"),
                adminNotes = cell(first, "ملاحظات"),
                parties = parties
            )
            val validationReasons = case.validate().map(::caseValidationReason)
            if (validationReasons.isNotEmpty()) {
                rejectedCases += RejectedImportCase(caseNo, caseYear, court, incomingNo, validationReasons)
                return@forEach
            }

            val duplicateKey = caseImportDuplicateKey(case)
            if (duplicateKey in knownCaseKeys || !workbookKeys.add(duplicateKey)) {
                duplicates++
                return@forEach
            }
            pending += case
        }

        officeState.update {
            it.copy(
                isOfficeBusy = false,
                officeMessage = "تم فحص ملف Excel — راجع النتائج قبل الحفظ",
                showImportReview = true,
                pendingImportCases = pending,
                pendingImportDuplicates = duplicates,
                pendingImportRejected = rejectedCases.size,
                pendingRejectedCases = rejectedCases
            )
        }
    }

    fun onConfirmImport() {
        val pending = officeState.value.pendingImportCases
        if (pending.isEmpty()) {
            clearImportReview("لا توجد قضايا جديدة للحفظ")
            return
        }
        viewModelScope.launch {
            officeState.update { it.copy(isOfficeBusy = true, showImportReview = false, officeMessage = null) }
            val knownCaseKeys = searchCases("").first().mapTo(mutableSetOf(), ::caseImportDuplicateKey)
            var imported = 0
            var duplicates = officeState.value.pendingImportDuplicates
            var rejected = officeState.value.pendingImportRejected

            pending.forEach { case ->
                val duplicateKey = caseImportDuplicateKey(case)
                if (duplicateKey in knownCaseKeys) {
                    duplicates++
                    return@forEach
                }
                when (saveCase(case)) {
                    is SaveCaseUseCase.Result.Success -> {
                        imported++
                        knownCaseKeys += duplicateKey
                    }
                    is SaveCaseUseCase.Result.Invalid -> rejected++
                }
            }

            val details = buildList {
                if (duplicates > 0) add("$duplicates مكررة")
                if (rejected > 0) add("$rejected غير صالحة")
            }.joinToString(" — ")
            officeState.update {
                it.copy(
                    isOfficeBusy = false,
                    officeMessage = "تم حفظ $imported قضية${if (details.isNotBlank()) " — $details" else ""}",
                    pendingImportCases = emptyList(),
                    pendingImportDuplicates = 0,
                    pendingImportRejected = 0,
                    pendingRejectedCases = emptyList()
                )
            }
        }
    }

    fun onCancelImport() = clearImportReview("تم إلغاء الاستيراد دون حفظ أي قضية")

    private fun clearImportReview(message: String?) {
        officeState.update {
            it.copy(
                isOfficeBusy = false,
                officeMessage = message,
                showImportReview = false,
                pendingImportCases = emptyList(),
                pendingImportDuplicates = 0,
                pendingImportRejected = 0,
                pendingRejectedCases = emptyList()
            )
        }
    }

    fun onExportConsumed() = officeState.update { it.copy(exportedFileUri = null) }
    fun onOfficeMessageConsumed() = officeState.update { it.copy(officeMessage = null) }

    companion object {
        const val EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        internal fun caseValidationReason(error: CaseValidationError): String = when (error) {
            CaseValidationError.MissingCaseNo -> "رقم الدعوى غير موجود"
            CaseValidationError.MissingCaseYear -> "سنة الدعوى غير موجودة"
            CaseValidationError.InvalidCaseYear -> "سنة الدعوى يجب أن تكون أربعة أرقام صحيحة"
            CaseValidationError.MissingCourt -> "المحكمة غير موجودة"
        }
    }
}
