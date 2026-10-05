package com.khabir.app.data.repository

import com.khabir.app.data.local.dao.CaseDao
import com.khabir.app.data.local.dao.PartyDao
import com.khabir.app.data.local.entity.CaseEntity
import com.khabir.app.data.local.entity.PartyEntity
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.repository.CaseRepository
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
import com.khabir.app.domain.model.AutomaticWorkMinutes

@OptIn(ExperimentalCoroutinesApi::class)
class CaseRepositoryImpl @Inject constructor(private val caseDao: CaseDao, private val partyDao: PartyDao, private val database: AppDatabase, private val workMinutes: WorkMinutesRepository) : CaseRepository {
    override fun search(query: String): Flow<List<Case>> {
        val byFields = caseDao.search(query)
        val byPartyName = if (query.isBlank()) caseDao.search("") else caseDao.searchByPartyName(query)
        return byFields.combine(byPartyName) { a, b -> (a + b).distinctBy { it.id }.sortedByDescending { it.updatedAtEpochMillis } }
            .flatMapLatest { attachParties(it) }
    }

    override fun filterForRegister(caseType: String?, court: String?, fromEpochDay: Long?, toEpochDay: Long?): Flow<List<Case>> =
        caseDao.filterForRegister(caseType.orEmpty(), court.orEmpty(), fromEpochDay, toEpochDay).flatMapLatest { attachParties(it) }

    override fun observeById(caseId: Long): Flow<Case?> = caseDao.observeById(caseId).flatMapLatest { entity ->
        if (entity == null) flowOf(null) else partyDao.observeForCase(caseId).map { ps -> entity.toDomain(ps.map { it.toDomain() }) }
    }

    override suspend fun getById(caseId: Long): Case? {
        val entity = caseDao.getById(caseId) ?: return null
        return entity.toDomain(partyDao.getForCase(caseId).map { it.toDomain() })
    }

    override suspend fun save(case: Case): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val caseId = if (case.id == 0L) caseDao.insert(case.toEntity(now, now)) else {
            val existing = caseDao.getById(case.id)
            caseDao.update(case.toEntity(existing?.createdAtEpochMillis ?: now, now).copy(id = case.id))
            case.id
        }
        partyDao.deleteAllForCase(caseId)
        if (case.parties.isNotEmpty()) partyDao.insertAll(case.parties.mapIndexed { i, p -> p.toEntity(caseId, i) })
        val existingMinutes=workMinutes.getForCase(caseId)
        val generated=AutomaticWorkMinutes.receipt(case.copy(id=caseId),existingMinutes)
        if((existingMinutes!=null || generated.entries.isNotEmpty()) && generated!=existingMinutes) workMinutes.save(generated)
        caseId
    }

    override suspend fun moveToTrash(caseId: Long) = caseDao.moveToTrash(caseId, System.currentTimeMillis())
    override suspend fun restoreFromTrash(caseId: Long) = caseDao.restoreFromTrash(caseId, System.currentTimeMillis())
    override suspend fun archive(caseId: Long) = caseDao.archive(caseId, System.currentTimeMillis())

    private fun attachParties(entities: List<CaseEntity>): Flow<List<Case>> {
        if (entities.isEmpty()) return flowOf(emptyList())
        return combine(entities.map { e -> partyDao.observeForCase(e.id).map { ps -> e.toDomain(ps.map { it.toDomain() }) } }) { it.toList() }
    }
}

private fun CaseEntity.toDomain(parties: List<Party>) = Case(
    id = id,
    incomingNo = incomingNo,
    incomingDate = LocalDate.ofEpochDay(incomingDateEpochDay),
    caseNo = caseNo,
    caseYear = caseYear,
    court = court,
    caseType = caseType,
    subjectOfCase = subjectOfCase,
    finalRequests = finalRequests,
    preliminaryMission = preliminaryMission,
    adminNotes = adminNotes,
    parties = parties,
    isArchived = isArchived,
    updatedAt = updatedAtEpochMillis,
    receiptDate = receiptDateEpochDay?.let(LocalDate::ofEpochDay),
    preliminaryJudgmentDate = preliminaryJudgmentDateEpochDay?.let(LocalDate::ofEpochDay),
    hearingDate = hearingDateEpochDay?.let(LocalDate::ofEpochDay),
    hearingTime = hearingTime
)

private fun Case.toEntity(created: Long, updated: Long) = CaseEntity(
    id = id,
    incomingNo = incomingNo,
    incomingDateEpochDay = incomingDate.toEpochDay(),
    caseNo = caseNo,
    caseYear = caseYear,
    court = court,
    caseType = caseType,
    subjectOfCase = subjectOfCase,
    finalRequests = finalRequests,
    preliminaryMission = preliminaryMission,
    adminNotes = adminNotes,
    createdAtEpochMillis = created,
    updatedAtEpochMillis = updated,
    isArchived = isArchived,
    isDeleted = false,
    receiptDateEpochDay = receiptDate?.toEpochDay(),
    preliminaryJudgmentDateEpochDay = preliminaryJudgmentDate?.toEpochDay(),
    hearingDateEpochDay = hearingDate?.toEpochDay(),
    hearingTime = hearingTime.trim()
)

private fun PartyEntity.toDomain(): Party {
    val (parsedRole, capacity) = PartyRole.parseStored(role)
    return Party(id, firstName, restName, parsedRole, address, orderIndex, capacity, role.substringAfter("|دعوى:", "أصلية"))
}
private fun Party.toEntity(caseId: Long, orderIndex: Int) = PartyEntity(if (id == 0L) 0L else id, caseId, firstName, restName, storedRoleValue, address, orderIndex)
