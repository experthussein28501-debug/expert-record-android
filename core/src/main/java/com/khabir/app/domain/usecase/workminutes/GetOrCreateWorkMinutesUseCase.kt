package com.khabir.app.domain.usecase.workminutes

import com.khabir.app.domain.model.ReportCoverFields
import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.WorkMinutesRepository
import javax.inject.Inject

class GetOrCreateWorkMinutesUseCase @Inject constructor(
    private val workMinutesRepository: WorkMinutesRepository,
    private val caseRepository: CaseRepository
) {
    suspend operator fun invoke(recordId: Long, caseId: Long?): WorkMinutesRecord {
        if (recordId > 0L) {
            workMinutesRepository.getById(recordId)?.let { return it }
        }
        if (caseId != null && caseId > 0L) {
            workMinutesRepository.getForCase(caseId)?.let { return it }
            val case = caseRepository.getById(caseId)
            if (case != null) {
                val plaintiffs = case.parties.filter { it.role.isPlaintiff }
                val defendants = case.parties.filter { it.role.isDefendant }
                return WorkMinutesRecord(
                    caseId = case.id,
                    caseNo = case.caseNo,
                    caseYear = case.caseYear,
                    court = listOf(case.caseType, case.court).filter { it.isNotBlank() }.distinct().joinToString(" "),
                    plaintiffsSummary = ReportCoverFields.coverPartySummary(plaintiffs),
                    defendantsSummary = ReportCoverFields.coverPartySummary(defendants)
                )
            }
        }
        return WorkMinutesRecord()
    }
}
