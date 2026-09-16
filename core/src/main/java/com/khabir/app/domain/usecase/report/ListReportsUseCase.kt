package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.Report
import com.khabir.app.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ListReportsUseCase @Inject constructor(
    private val repository: ReportRepository
) {
    operator fun invoke(): Flow<List<Report>> = repository.observeAll()
}

