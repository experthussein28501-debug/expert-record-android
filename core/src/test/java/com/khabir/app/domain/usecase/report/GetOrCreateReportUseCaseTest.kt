package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.Report
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.ReportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GetOrCreateReportUseCaseTest {
    @Test
    fun `new linked report selects template and includes final requests`() = runBlocking {
        val case = Case(
            id = 7L,
            incomingNo = "1",
            incomingDate = LocalDate.of(2026, 9, 18),
            caseNo = "305",
            caseYear = "33ق",
            court = "أسوان",
            caseType = "مدني مستأنف",
            subjectOfCase = "شرح الدعوى الأصلي",
            finalRequests = "إلغاء الحكم المستأنف والقضاء مجددًا",
            preliminaryMission = "فحص المستندات والرد على المأمورية"
        )
        val useCase = GetOrCreateReportUseCase(
            reportRepository = EmptyReportRepository(),
            caseRepository = SingleCaseRepository(case)
        )

        val report = useCase(reportId = 0L, caseId = 7L)

        assertEquals("drive_civil_appeal", report.templateId)
        assertTrue(report.subjectOfCase.contains("شرح الدعوى الأصلي"))
        assertTrue(report.subjectOfCase.contains("إلغاء الحكم المستأنف"))
        assertTrue(report.subjectOfCase.contains("وطلب في ختامها:"))
        assertEquals("فحص المستندات والرد على المأمورية", report.assignment)
    }

    @Test
    fun `misdemeanor case selects misdemeanor drive template`() = runBlocking {
        val case = Case(
            id = 8L,
            incomingNo = "2",
            incomingDate = LocalDate.of(2026, 9, 18),
            caseNo = "1334",
            caseYear = "2014",
            court = "مركز دراو",
            caseType = "جنح"
        )
        val report = GetOrCreateReportUseCase(EmptyReportRepository(), SingleCaseRepository(case))(0L, 8L)
        assertEquals("drive_misdemeanor", report.templateId)
    }

    private class EmptyReportRepository : ReportRepository {
        override fun observeForCase(caseId: Long): Flow<Report?> = flowOf(null)
        override fun observeAll(): Flow<List<Report>> = flowOf(emptyList())
        override suspend fun getById(reportId: Long): Report? = null
        override suspend fun getForCase(caseId: Long): Report? = null
        override suspend fun save(report: Report): Long = report.id
    }

    private class SingleCaseRepository(private val value: Case) : CaseRepository {
        override fun search(query: String): Flow<List<Case>> = flowOf(listOf(value))
        override fun filterForRegister(caseType: String?, court: String?, fromEpochDay: Long?, toEpochDay: Long?): Flow<List<Case>> = flowOf(listOf(value))
        override fun observeById(caseId: Long): Flow<Case?> = flowOf(value.takeIf { it.id == caseId })
        override suspend fun getById(caseId: Long): Case? = value.takeIf { it.id == caseId }
        override suspend fun save(case: Case): Long = case.id
        override suspend fun moveToTrash(caseId: Long) = Unit
        override suspend fun restoreFromTrash(caseId: Long) = Unit
        override suspend fun archive(caseId: Long) = Unit
    }
}
