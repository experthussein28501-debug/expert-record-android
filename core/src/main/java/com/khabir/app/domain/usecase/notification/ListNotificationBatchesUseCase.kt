package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.repository.NotificationBatchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ListNotificationBatchesUseCase @Inject constructor(private val repository: NotificationBatchRepository) {
    operator fun invoke(): Flow<List<NotificationBatch>> = repository.observeAll()
}

