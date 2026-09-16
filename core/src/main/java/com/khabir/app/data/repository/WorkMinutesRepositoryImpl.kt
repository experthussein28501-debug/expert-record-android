package com.khabir.app.data.repository

import com.khabir.app.data.local.dao.WorkMinutesDao
import com.khabir.app.data.local.entity.WorkMinutesEntity
import com.khabir.app.domain.model.WorkMinutesCodec
import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.repository.WorkMinutesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkMinutesRepositoryImpl @Inject constructor(private val dao: WorkMinutesDao) : WorkMinutesRepository {
    override fun observeForCase(caseId: Long): Flow<WorkMinutesRecord?> = dao.observeForCase(caseId).map { it?.toDomain() }
    override fun observeAll(): Flow<List<WorkMinutesRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(id: Long): WorkMinutesRecord? = dao.getById(id)?.toDomain()
    override suspend fun getForCase(caseId: Long): WorkMinutesRecord? = dao.getForCase(caseId)?.toDomain()

    override suspend fun save(record: WorkMinutesRecord): Long {
        val now = System.currentTimeMillis()
        val existing = when {
            record.id > 0L -> dao.getById(record.id)
            record.caseId != null -> dao.getForCase(record.caseId)
            else -> null
        }
        return dao.upsert(
            WorkMinutesEntity(
                id = existing?.id ?: record.id,
                caseId = record.caseId,
                caseNo = record.caseNo,
                caseYear = record.caseYear,
                court = record.court,
                plaintiffsSummary = record.plaintiffsSummary,
                defendantsSummary = record.defendantsSummary,
                entriesSpec = WorkMinutesCodec.encode(record.entries),
                copiesCount = record.copiesCount,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now
            )
        )
    }
}

private fun WorkMinutesEntity.toDomain() = WorkMinutesRecord(
    id = id,
    caseId = caseId,
    caseNo = caseNo,
    caseYear = caseYear,
    court = court,
    plaintiffsSummary = plaintiffsSummary,
    defendantsSummary = defendantsSummary,
    entries = WorkMinutesCodec.decode(entriesSpec),
    copiesCount = copiesCount,
    updatedAt = updatedAtEpochMillis
)
