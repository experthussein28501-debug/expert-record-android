package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.repository.ExpertProfileRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import java.time.LocalDate
import javax.inject.Inject

class ReuseBatchAsNewUseCase @Inject constructor(private val batchRepository: NotificationBatchRepository, private val expertProfileRepository: ExpertProfileRepository) {
    sealed class Result { data class Success(val newBatchId: Long): Result(); data object SourceBatchNotFound: Result(); data object MissingExpertProfile: Result() }
    suspend operator fun invoke(sourceBatchId: Long,newAppointmentDate: LocalDate,newAppointmentTime: String,newAppointmentLocation: String): Result {
        val source = batchRepository.getById(sourceBatchId) ?: return Result.SourceBatchNotFound
        val p = expertProfileRepository.get() ?: return Result.MissingExpertProfile
        val newBatch = source.copy(id=0L,appointmentDate=newAppointmentDate,appointmentTime=newAppointmentTime,appointmentLocation=newAppointmentLocation,expertName=p.expertName,officeAddress=p.officeAddress,isReprint=true,sourceBatchId=sourceBatchId,recipients=source.recipients.map { it.copy(id=0L) })
        return Result.Success(batchRepository.save(newBatch))
    }
}

