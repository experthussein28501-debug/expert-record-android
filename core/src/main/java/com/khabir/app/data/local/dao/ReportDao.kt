package com.khabir.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.khabir.app.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ReportEntity): Long

    @Query("SELECT * FROM reports WHERE id = :reportId LIMIT 1")
    suspend fun getById(reportId: Long): ReportEntity?

    @Query("SELECT * FROM reports WHERE caseId = :caseId LIMIT 1")
    fun observeForCase(caseId: Long): Flow<ReportEntity?>

    @Query("SELECT * FROM reports WHERE caseId = :caseId LIMIT 1")
    suspend fun getForCase(caseId: Long): ReportEntity?

    @Query("SELECT * FROM reports ORDER BY updatedAtEpochMillis DESC")
    fun observeAll(): Flow<List<ReportEntity>>
}

