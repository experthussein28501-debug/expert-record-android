package com.khabir.app.domain.model

object CaseDocumentFields {
    fun report(case: Case): Map<String, String> = linkedMapOf(
        "رقم الدعوى" to case.caseNo, "السنة" to case.caseYear,
        "المحكمة" to listOf(case.caseType, case.court).filter(String::isNotBlank).distinct().joinToString(" "),
        "الخصوم" to case.parties.filter { it.role != PartyRole.LAWYER }.sortedBy { it.orderIndex }.joinToString("، ") { it.reportDisplayName },
        "موضوع الدعوى" to subject(case.subjectOfCase, case.finalRequests), "المأمورية" to case.preliminaryMission
    )
    fun minutes(case: Case): Map<String, String> = linkedMapOf(
        "رقم الدعوى" to case.caseNo, "السنة" to case.caseYear,
        "المحكمة" to listOf(case.caseType, case.court).filter(String::isNotBlank).distinct().joinToString(" "),
        "المدعون" to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isPlaintiff }),
        "المدعى عليهم" to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isDefendant })
    )
    fun subject(explanation: String, finalRequests: String): String {
        if (explanation.isBlank() && finalRequests.isBlank()) return ""
        return buildString {
            append("أقام المدعي دعواه بموجب صحيفة أودعت قلم كتاب المحكمة وأعلنت قانونًا")
            if (finalRequests.isNotBlank()) append("، وطلب في ختامها:\n").append(finalRequests.trim())
            if (explanation.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(explanation.trim())
        }
    }
}

data class CaseFieldUpdate(val key: String, val current: String, val proposed: String)
