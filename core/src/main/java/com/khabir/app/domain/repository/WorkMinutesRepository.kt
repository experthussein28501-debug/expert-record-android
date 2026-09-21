package com.khabir.app.domain.repository

import com.khabir.app.domain.model.WorkMinutesRecord
import kotlinx.coroutines.flow.Flow

interface WorkMinutesRepository {
    fun observeForCase(caseId: Long): Flow<WorkMinutesRecord?>
    fun observeAll(): Flow<List<WorkMinutesRecord>>
    suspend fun getById(id: Long): WorkMinutesRecord?
    suspend fun getForCase(caseId: Long): WorkMinutesRecord?
    suspend fun save(record: WorkMinutesRecord): Long
    suspend fun delete(id: Long)
}
