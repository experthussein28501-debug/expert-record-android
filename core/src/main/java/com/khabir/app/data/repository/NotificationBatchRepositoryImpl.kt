package com.khabir.app.data.repository

import com.khabir.app.data.local.dao.NotificationBatchDao
import com.khabir.app.data.local.entity.NotificationBatchEntity
import com.khabir.app.data.local.entity.NotificationRecipientEntity
import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.NotificationRecipient
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.repository.NotificationBatchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import androidx.room.withTransaction
import com.khabir.app.data.local.AppDatabase
import com.khabir.app.domain.repository.WorkMinutesRepository
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.model.AutomaticWorkMinutes
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBatchRepositoryImpl @Inject constructor(private val dao: NotificationBatchDao, private val database: AppDatabase, private val workMinutes: WorkMinutesRepository, private val cases: CaseRepository) : NotificationBatchRepository {
    override fun observeAll(): Flow<List<NotificationBatch>> = dao.observeAllBatches().flatMapLatest { batches ->
        if (batches.isEmpty()) return@flatMapLatest flowOf(emptyList())
        combine(batches.map { b -> dao.observeRecipients(b.id).map { rs -> b.toDomain(rs.map { it.toDomain() }) } }) {
            it.toList().sortedByDescending { batch -> batch.createdAt }
        }
    }

    override suspend fun getById(batchId: Long): NotificationBatch? {
        val batch = dao.getBatch(batchId) ?: return null
        return batch.toDomain(dao.getRecipients(batchId).map { it.toDomain() })
    }

    override suspend fun delete(batchId: Long) = dao.deleteBatch(batchId)

    override suspend fun save(batch: NotificationBatch): Long = database.withTransaction {
        val existing = batch.id.takeIf { it > 0L }?.let { dao.getBatch(it) }
        if (existing != null) dao.deleteRecipients(existing.id)
        val batchId = dao.insertBatch(
            NotificationBatchEntity(
                id = existing?.id ?: batch.id,
                appointmentDateEpochDay = batch.appointmentDate.toEpochDay(),
                appointmentTime = batch.appointmentTime,
                appointmentLocation = batch.appointmentLocation,
                requestedDocuments = batch.requestedDocuments,
                expertNameSnapshot = batch.expertName,
                officeAddressSnapshot = batch.officeAddress,
                ministryOrSectorSnapshot = batch.ministryOrSector,
                departmentSnapshot = batch.department,
                expertJobTitleSnapshot = batch.expertJobTitle,
                attendancePhraseSnapshot = batch.attendancePhrase,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: batch.createdAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                isReprint = batch.isReprint,
                sourceBatchId = batch.sourceBatchId
            )
        )
        dao.insertRecipients(batch.recipients.mapIndexed { index, r ->
            NotificationRecipientEntity(
                batchId = batchId,
                caseId = r.caseId,
                partyId = r.partyId,
                caseNoSnapshot = r.caseNo,
                caseYearSnapshot = r.caseYear,
                courtSnapshot = r.court,
                partyFirstNameSnapshot = r.partyFirstName,
                partyRestNameSnapshot = r.partyRestName,
                partyRoleSnapshot = r.storedRoleValue,
                partyAddressSnapshot = r.partyAddress,
                plaintiffsSummarySnapshot = r.plaintiffsSummary,
                defendantsSummarySnapshot = r.defendantsSummary,
                subjectOfCaseSnapshot = r.subjectOfCase,
                isAuthorityNotice = r.isAuthorityNotice,
                incomingNoSnapshot = r.incomingNo,
                preliminaryJudgmentDateEpochDay = r.preliminaryJudgmentDate?.toEpochDay(),
                orderInBatch = index
            )
        })
        val createdAt=existing?.createdAtEpochMillis ?: batch.createdAt.takeIf { it>0L } ?: System.currentTimeMillis()
        batch.recipients.filterNot {it.isAuthorityNotice || batch.isReprint}.mapNotNull {it.caseId}.distinct().forEach {caseId ->
            val case=cases.getById(caseId) ?: return@forEach
            val record=AutomaticWorkMinutes.receipt(case,workMinutes.getForCase(caseId))
            val generated=AutomaticWorkMinutes.scheduling(record,batch.copy(id=batchId),Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate())
            if(generated!=workMinutes.getForCase(caseId)) workMinutes.save(generated)
        }
        batchId
    }
}

private fun NotificationBatchEntity.toDomain(recipients: List<NotificationRecipient>) = NotificationBatch(
    id = id,
    appointmentDate = LocalDate.ofEpochDay(appointmentDateEpochDay),
    appointmentTime = appointmentTime,
    appointmentLocation = appointmentLocation,
    requestedDocuments = requestedDocuments,
    expertName = expertNameSnapshot,
    officeAddress = officeAddressSnapshot,
    ministryOrSector = ministryOrSectorSnapshot,
    department = departmentSnapshot,
    expertJobTitle = expertJobTitleSnapshot,
    attendancePhrase = attendancePhraseSnapshot,
    recipients = recipients,
    isReprint = isReprint,
    sourceBatchId = sourceBatchId,
    createdAt = createdAtEpochMillis
)

private fun NotificationRecipientEntity.toDomain(): NotificationRecipient {
    val (role, capacity) = PartyRole.parseStored(partyRoleSnapshot)
    return NotificationRecipient(
        id = id,
        caseId = caseId,
        partyId = partyId,
        caseNo = caseNoSnapshot,
        caseYear = caseYearSnapshot,
        court = courtSnapshot,
        partyFirstName = partyFirstNameSnapshot,
        partyRestName = partyRestNameSnapshot,
        partyRole = role,
        partyAddress = partyAddressSnapshot,
        plaintiffsSummary = plaintiffsSummarySnapshot,
        defendantsSummary = defendantsSummarySnapshot,
        subjectOfCase = subjectOfCaseSnapshot,
        isAuthorityNotice = isAuthorityNotice,
        incomingNo = incomingNoSnapshot,
        preliminaryJudgmentDate = preliminaryJudgmentDateEpochDay?.let(LocalDate::ofEpochDay),
        withCapacity = capacity
    )
}
