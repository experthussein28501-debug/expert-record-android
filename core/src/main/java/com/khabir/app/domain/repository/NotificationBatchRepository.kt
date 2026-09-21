package com.khabir.app.domain.repository

import com.khabir.app.domain.model.NotificationBatch
import kotlinx.coroutines.flow.Flow

interface NotificationBatchRepository {
    fun observeAll(): Flow<List<NotificationBatch>>
    suspend fun getById(batchId: Long): NotificationBatch?
    suspend fun save(batch: NotificationBatch): Long
    suspend fun delete(batchId: Long) { }
}

