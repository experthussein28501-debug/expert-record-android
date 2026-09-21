package com.khabir.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khabir.app.data.local.entity.WorkMinutesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkMinutesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WorkMinutesEntity): Long

    @Query("SELECT * FROM work_minutes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WorkMinutesEntity?

    @Query("SELECT * FROM work_minutes WHERE caseId = :caseId LIMIT 1")
    fun observeForCase(caseId: Long): Flow<WorkMinutesEntity?>

    @Query("SELECT * FROM work_minutes WHERE caseId = :caseId LIMIT 1")
    suspend fun getForCase(caseId: Long): WorkMinutesEntity?

    @Query("SELECT * FROM work_minutes ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<WorkMinutesEntity>>

    @Query("DELETE FROM work_minutes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
