package com.khabir.app.domain.model

object ReportCaseSnapshot {
    const val PLAINTIFFS = "_case_cover_plaintiffs"
    const val DEFENDANTS = "_case_cover_defendants"
    fun parties(case: Case): Map<String, String> = mapOf(
        PLAINTIFFS to summary(case, true),
        DEFENDANTS to summary(case, false)
    )
    private fun summary(case: Case, plaintiff: Boolean): String {
        val parties = case.parties.filter { if (plaintiff) it.role.isPlaintiff else it.role.isDefendant }
        return if (ReportCoverFields.isEstateOrGuardianship(case.caseType, case.court)) ReportCoverFields.fullPartySummary(parties)
            else ReportCoverFields.coverPartySummary(parties)
    }
    fun cover(report: Report, case: Case, profile: ExpertProfile): ReportCoverFields {
        val metadata = ReportCustomSectionCodec.decode(report.customSectionContentsSpec)
        val base = ReportCoverFields.from(case, profile)
        return base.copy(caseNo = report.caseNo, caseYear = report.caseYear, court = report.court,
            partiesSummary = report.partiesSummary,
            plaintiffsSummary = metadata[PLAINTIFFS] ?: base.plaintiffsSummary,
            defendantsSummary = metadata[DEFENDANTS] ?: base.defendantsSummary)
    }
}
