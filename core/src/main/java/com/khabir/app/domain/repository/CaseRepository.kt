package com.khabir.app.domain.repository

import com.khabir.app.domain.model.Case
import kotlinx.coroutines.flow.Flow

interface CaseRepository {
    fun search(query: String): Flow<List<Case>>
    fun filterForRegister(caseType: String?, court: String?, fromEpochDay: Long?, toEpochDay: Long?): Flow<List<Case>>
    fun observeById(caseId: Long): Flow<Case?>
    suspend fun getById(caseId: Long): Case?
    suspend fun save(case: Case): Long
    suspend fun moveToTrash(caseId: Long)
    suspend fun restoreFromTrash(caseId: Long)
    suspend fun archive(caseId: Long)
}

