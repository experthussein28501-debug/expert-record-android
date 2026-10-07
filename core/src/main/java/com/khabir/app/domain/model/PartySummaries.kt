package com.khabir.app.domain.model

object PartySummaries {
    fun names(names: List<String>): String {
        val ordered = names.map(String::trim).filter(String::isNotBlank).distinct()
        val first = ordered.firstOrNull().orEmpty()
        return if (ordered.size <= 1 || first.contains("وآخرين")) first else "$first وآخرين"
    }
    fun side(parties: List<Party>): String = names(parties.sortedBy { it.orderIndex }.map { it.reportDisplayName })
    fun misdemeanor(type: String): Boolean = legalNormalize(type).let { it.contains("جنح") || it.contains("جنحة") }
    fun report(case: Case): String {
        val original = case.parties.filter { it.claimKind == "أصلية" && it.role != PartyRole.LAWYER }
        val plaintiff = if (misdemeanor(case.caseType)) "النيابة العامة" else side(original.filter { it.role.isPlaintiff })
        val defendant = side(original.filter { it.role.isDefendant })
        return listOf(plaintiff,defendant).filter(String::isNotBlank).joinToString(" ضد ")
    }
    fun subject(case: Case): String {
        val body = UnifiedCaseSubject.compose(case.subjectOfCase,case.finalRequests)
        if (!misdemeanor(case.caseType) || body.startsWith("النيابة العامة ضد")) return body
        // Criminal files never acquire an invented civil petition opening.
        return listOf(report(case),case.subjectOfCase.takeUnless { it.startsWith("أقام المدعي") }.orEmpty())
            .filter(String::isNotBlank).joinToString("\n\n")
    }
}
