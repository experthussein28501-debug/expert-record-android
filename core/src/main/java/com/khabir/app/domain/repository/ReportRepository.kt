package com.khabir.app.domain.repository

import com.khabir.app.domain.model.Report
import kotlinx.coroutines.flow.Flow

interface ReportRepository {
    fun observeForCase(caseId: Long): Flow<Report?>
    fun observeAll(): Flow<List<Report>>
    suspend fun getById(reportId: Long): Report?
    suspend fun getForCase(caseId: Long): Report?
    suspend fun save(report: Report): Long
}

