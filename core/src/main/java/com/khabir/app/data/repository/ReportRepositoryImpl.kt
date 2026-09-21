package com.khabir.app.data.repository

import com.khabir.app.data.local.dao.ReportDao
import com.khabir.app.data.local.entity.ReportEntity
import com.khabir.app.domain.model.Report
import com.khabir.app.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

class ReportRepositoryImpl @Inject constructor(private val dao: ReportDao) : ReportRepository {
    override fun observeForCase(caseId: Long): Flow<Report?> = dao.observeForCase(caseId).map { it?.toDomain() }
    override fun observeAll(): Flow<List<Report>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun getById(reportId: Long): Report? = dao.getById(reportId)?.toDomain()
    override suspend fun getForCase(caseId: Long): Report? = dao.getForCase(caseId)?.toDomain()
    override suspend fun delete(reportId: Long) = dao.deleteById(reportId)

    override suspend fun save(report: Report): Long {
        val now = System.currentTimeMillis()
        val existing = when {
            report.id > 0L -> dao.getById(report.id)
            report.caseId != null -> dao.getForCase(report.caseId)
            else -> null
        }
        return dao.upsert(
            ReportEntity(
                id = existing?.id ?: report.id,
                caseId = report.caseId,
                caseNo = report.caseNo,
                caseYear = report.caseYear,
                court = report.court,
                manualHeader = report.manualHeader,
                templateId = report.templateId,
                templateName = report.templateName,
                templateSectionsSpec = report.templateSectionsSpec,
                customSectionContentsSpec = report.customSectionContentsSpec,
                partiesSummary = report.partiesSummary,
                subjectOfCase = report.subjectOfCase,
                assignment = report.assignment,
                proceedings = report.proceedings,
                partyStatements = report.partyStatements,
                witnessStatements = report.witnessStatements,
                inspection = report.inspection,
                documentsSubmitted = report.documentsSubmitted,
                facts = report.facts,
                research = report.research,
                technicalOpinion = report.technicalOpinion,
                calculationsTable = report.calculationsTable,
                siteSketchPath = report.siteSketchPath,
                conclusion = report.conclusion,
                attachmentsNote = report.attachmentsNote,
                depositDateEpochDay = report.depositDate?.toEpochDay(),
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now
            )
        )
    }
}

private fun ReportEntity.toDomain() = Report(
    id = id,
    caseId = caseId,
    caseNo = caseNo,
    caseYear = caseYear,
    court = court,
    manualHeader = manualHeader,
    templateId = templateId,
    templateName = templateName,
    templateSectionsSpec = templateSectionsSpec,
    customSectionContentsSpec = customSectionContentsSpec,
    partiesSummary = partiesSummary,
    subjectOfCase = subjectOfCase,
    assignment = assignment,
    proceedings = proceedings,
    partyStatements = partyStatements,
    witnessStatements = witnessStatements,
    inspection = inspection,
    documentsSubmitted = documentsSubmitted,
    facts = facts,
    research = research,
    technicalOpinion = technicalOpinion,
    calculationsTable = calculationsTable,
    siteSketchPath = siteSketchPath,
    conclusion = conclusion,
    attachmentsNote = attachmentsNote,
    depositDate = depositDateEpochDay?.let { LocalDate.ofEpochDay(it) },
    updatedAt = updatedAtEpochMillis
)
