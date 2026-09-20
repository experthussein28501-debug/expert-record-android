package com.khabir.app.domain.model

object ReportCaseSnapshot {
    const val PLAINTIFFS = "_case_cover_plaintiffs"
    const val DEFENDANTS = "_case_cover_defendants"
    fun parties(case: Case): Map<String, String> = mapOf(
        PLAINTIFFS to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isPlaintiff }),
        DEFENDANTS to ReportCoverFields.coverPartySummary(case.parties.filter { it.role.isDefendant })
    )
    fun cover(report: Report, case: Case, profile: ExpertProfile): ReportCoverFields {
        val metadata = ReportCustomSectionCodec.decode(report.customSectionContentsSpec)
        val base = ReportCoverFields.from(case, profile)
        return base.copy(caseNo = report.caseNo, caseYear = report.caseYear, court = report.court,
            partiesSummary = report.partiesSummary,
            plaintiffsSummary = metadata[PLAINTIFFS] ?: base.plaintiffsSummary,
            defendantsSummary = metadata[DEFENDANTS] ?: base.defendantsSummary)
    }
}
