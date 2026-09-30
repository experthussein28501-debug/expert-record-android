package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.Report
import com.khabir.app.domain.model.ReportTemplateCatalog
import com.khabir.app.domain.model.ReportTemplateCodec
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
                val template = ReportTemplateCatalog.suggestFor(case.caseType, case.court)
                val composedSubject = composeCaseSubject(case.subjectOfCase, case.finalRequests)
                return Report(
                    caseId = case.id,
                    caseNo = case.caseNo,
                    caseYear = case.caseYear,
                    court = listOf(case.caseType, case.court)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" "),
                    templateId = template.id,
                    templateName = template.name,
                    templateSectionsSpec = ReportTemplateCodec.encode(template),
                    partiesSummary = case.parties.filter { it.role != com.khabir.app.domain.model.PartyRole.LAWYER }
                        .sortedBy { it.orderIndex }
                        .joinToString("، ") { it.reportDisplayName },
                    subjectOfCase = composedSubject,
                    assignment = case.preliminaryMission
                )
            }
        }
        return Report()
    }

    private fun composeCaseSubject(explanation: String, finalRequests: String): String =
        com.khabir.app.domain.model.UnifiedCaseSubject.compose(explanation, finalRequests)
}
