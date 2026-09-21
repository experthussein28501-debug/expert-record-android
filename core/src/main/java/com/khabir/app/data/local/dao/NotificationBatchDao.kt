package com.khabir.app.data.local.dao

import androidx.room.*
import com.khabir.app.data.local.entity.NotificationBatchEntity
import com.khabir.app.data.local.entity.NotificationRecipientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBatch(batch: NotificationBatchEntity): Long
    @Insert suspend fun insertRecipients(recipients: List<NotificationRecipientEntity>): List<Long>
    @Query("SELECT * FROM notification_batches ORDER BY createdAtEpochMillis DESC") fun observeAllBatches(): Flow<List<NotificationBatchEntity>>
    @Query("SELECT * FROM notification_batches WHERE id = :batchId LIMIT 1") suspend fun getBatch(batchId: Long): NotificationBatchEntity?
    @Query("SELECT * FROM notification_recipients WHERE batchId = :batchId ORDER BY orderInBatch ASC") fun observeRecipients(batchId: Long): Flow<List<NotificationRecipientEntity>>
    @Query("SELECT * FROM notification_recipients WHERE batchId = :batchId ORDER BY orderInBatch ASC") suspend fun getRecipients(batchId: Long): List<NotificationRecipientEntity>
    @Query("DELETE FROM notification_recipients WHERE batchId = :batchId") suspend fun deleteRecipients(batchId: Long)
    @Query("DELETE FROM notification_batches WHERE id = :batchId") suspend fun deleteBatch(batchId: Long)
}

