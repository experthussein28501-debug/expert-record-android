package com.khabir.app.data.local.dao

import androidx.room.*
import com.khabir.app.data.local.entity.PartyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartyDao {
    @Insert suspend fun insertAll(parties: List<PartyEntity>): List<Long>
    @Update suspend fun update(party: PartyEntity)
    @Query("DELETE FROM parties WHERE id = :partyId") suspend fun delete(partyId: Long)
    @Query("DELETE FROM parties WHERE caseId = :caseId") suspend fun deleteAllForCase(caseId: Long)
    @Query("SELECT * FROM parties WHERE caseId = :caseId ORDER BY orderIndex ASC") fun observeForCase(caseId: Long): Flow<List<PartyEntity>>
    @Query("SELECT * FROM parties WHERE caseId = :caseId ORDER BY orderIndex ASC") suspend fun getForCase(caseId: Long): List<PartyEntity>
}

