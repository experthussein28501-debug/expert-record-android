package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.Report
import com.khabir.app.domain.repository.ReportRepository
import javax.inject.Inject

class SaveReportUseCase @Inject constructor(private val repository: ReportRepository) {
    suspend operator fun invoke(report: Report): Long = repository.save(report)
}

