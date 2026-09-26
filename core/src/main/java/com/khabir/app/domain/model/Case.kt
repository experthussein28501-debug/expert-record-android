package com.khabir.app.domain.model

import java.time.LocalDate

data class Case(
    val id: Long = 0L,
    val incomingNo: String,
    val incomingDate: LocalDate,
    val caseNo: String,
    val caseYear: String,
    val court: String,
    val caseType: String,
    val subjectOfCase: String = "",
    val finalRequests: String = "",
    val preliminaryMission: String = "",
    val adminNotes: String = "",
    val parties: List<Party> = emptyList(),
    val isArchived: Boolean = false,
    val updatedAt: Long = 0L,
    val receiptDate: LocalDate? = null,
    val preliminaryJudgmentDate: LocalDate? = null,
    val hearingDate: LocalDate? = null,
    val hearingTime: String = ""
) {
    fun validate(): List<CaseValidationError> {
        val errors = mutableListOf<CaseValidationError>()
        if (caseNo.isBlank()) errors += CaseValidationError.MissingCaseNo
        if (caseYear.isBlank()) {
            errors += CaseValidationError.MissingCaseYear
        } else if (usesJudicialYear()) {
            val normalizedYear = caseYear.toWesternDigits().replace("ق", "").replace("هـ", "").trim()
            val year = normalizedYear.toIntOrNull()
            if (year == null || normalizedYear.length !in 1..3 || year !in 1..999) {
                errors += CaseValidationError.InvalidCaseYear
            }
        } else {
            val normalizedYear = caseYear.toWesternDigits().trim()
            val year = normalizedYear.toIntOrNull()
            if (normalizedYear.length != 4 || year == null || year !in 1900..(LocalDate.now().year + 1)) {
                errors += CaseValidationError.InvalidCaseYear
            }
        }
        if (court.isBlank()) errors += CaseValidationError.MissingCourt
        return errors
    }
    fun usesJudicialYear(): Boolean {
        val type = caseType.normalizeArabicAlef()
        val courtName = court.normalizeArabicAlef()
        return type.contains("استئناف عالي") || type.contains("قضاء اداري") ||
            courtName.contains("استئناف عالي") || courtName.contains("قضاء اداري")
    }
}

private fun String.normalizeArabicAlef(): String = replace("أ", "ا")
    .replace("إ", "ا")
    .replace("آ", "ا")
    .trim()

private fun String.toWesternDigits(): String = map { char ->
    when (char) {
        in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
        else -> char
    }
}.joinToString("").trim()

sealed class CaseValidationError {
    data object MissingCaseNo : CaseValidationError()
    data object MissingCaseYear : CaseValidationError()
    data object InvalidCaseYear : CaseValidationError()
    data object MissingCourt : CaseValidationError()
}
