package com.khabir.app.data.local.dao

import androidx.room.*
import com.khabir.app.data.local.entity.CaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Insert suspend fun insert(entity: CaseEntity): Long
    @Update suspend fun update(entity: CaseEntity)
    @Query("UPDATE cases SET isDeleted = 1, updatedAtEpochMillis = :nowMillis WHERE id = :caseId") suspend fun moveToTrash(caseId: Long, nowMillis: Long)
    @Query("UPDATE cases SET isDeleted = 0, updatedAtEpochMillis = :nowMillis WHERE id = :caseId") suspend fun restoreFromTrash(caseId: Long, nowMillis: Long)
    @Query("UPDATE cases SET isArchived = 1, updatedAtEpochMillis = :nowMillis WHERE id = :caseId") suspend fun archive(caseId: Long, nowMillis: Long)
    @Query("SELECT * FROM cases WHERE id = :caseId LIMIT 1") fun observeById(caseId: Long): Flow<CaseEntity?>
    @Query("SELECT * FROM cases WHERE id = :caseId LIMIT 1") suspend fun getById(caseId: Long): CaseEntity?
    @Query("SELECT * FROM cases WHERE isDeleted = 0 AND (:query = '' OR caseNo LIKE '%' || :query || '%' OR caseYear LIKE '%' || :query || '%' OR incomingNo LIKE '%' || :query || '%' OR court LIKE '%' || :query || '%') ORDER BY updatedAtEpochMillis DESC")
    fun search(query: String): Flow<List<CaseEntity>>
    @Query("SELECT DISTINCT c.* FROM cases c INNER JOIN parties p ON p.caseId = c.id WHERE c.isDeleted = 0 AND (p.firstName LIKE '%' || :name || '%' OR p.restName LIKE '%' || :name || '%') ORDER BY c.updatedAtEpochMillis DESC")
    fun searchByPartyName(name: String): Flow<List<CaseEntity>>
    @Query("SELECT * FROM cases WHERE isDeleted = 0 AND (:caseType = '' OR caseType = :caseType) AND (:court = '' OR court = :court) AND (:fromEpochDay IS NULL OR incomingDateEpochDay >= :fromEpochDay) AND (:toEpochDay IS NULL OR incomingDateEpochDay <= :toEpochDay) ORDER BY incomingDateEpochDay ASC")
    fun filterForRegister(caseType: String, court: String, fromEpochDay: Long?, toEpochDay: Long?): Flow<List<CaseEntity>>
}

