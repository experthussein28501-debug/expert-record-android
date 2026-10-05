package com.khabir.app.domain.model

object CaseDocumentFields {
    fun report(case: Case): Map<String, String> = linkedMapOf(
        "رقم الدعوى" to case.caseNo, "السنة" to case.caseYear,
        "المحكمة" to listOf(case.caseType, case.court).filter(String::isNotBlank).distinct().joinToString(" "),
        "الخصوم" to PartySummaries.report(case),
        "موضوع الدعوى" to PartySummaries.subject(case), "المأمورية" to case.preliminaryMission
    )
    fun minutes(case: Case): Map<String, String> = linkedMapOf(
        "رقم الدعوى" to case.caseNo, "السنة" to case.caseYear,
        "المحكمة" to listOf(case.caseType, case.court).filter(String::isNotBlank).distinct().joinToString(" "),
        "المدعون" to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isPlaintiff }),
        "المدعى عليهم" to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isDefendant })
    )
    fun subject(explanation: String, finalRequests: String): String {
        if (explanation.isBlank() && finalRequests.isBlank()) return ""
        return UnifiedCaseSubject.compose(explanation, finalRequests)
    }
}

data class CaseFieldUpdate(val key: String, val current: String, val proposed: String)
