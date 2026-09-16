package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.Report
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.ReportRepository
import javax.inject.Inject

class GetOrCreateReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
    private val caseRepository: CaseRepository
) {
    suspend operator fun invoke(reportId: Long, caseId: Long?): Report {
        if (reportId > 0L) {
            reportRepository.getById(reportId)?.let { return it }
        }
        if (caseId != null && caseId > 0L) {
            reportRepository.getForCase(caseId)?.let { return it }
            val case = caseRepository.getById(caseId)
            if (case != null) {
                return Report(
                    caseId = case.id,
                    caseNo = case.caseNo,
                    caseYear = case.caseYear,
                    court = listOf(case.caseType, case.court)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" "),
                    partiesSummary = case.parties
                        .sortedBy { it.orderIndex }
                        .joinToString("، ") { it.reportDisplayName },
                    subjectOfCase = case.subjectOfCase,
                    assignment = case.preliminaryMission
                )
            }
        }
        return Report()
    }
}
