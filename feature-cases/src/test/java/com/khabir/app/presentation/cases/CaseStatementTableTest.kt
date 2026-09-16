package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.Party
import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaseStatementTableTest {
    @Test
    fun `civil statement filters by date and sorts by incoming number`() {
        val cases = listOf(
            sampleCase("١٢", "مدني كلي", LocalDate.of(2026, 8, 10), "12"),
            sampleCase("٣", "مدني جزئي", LocalDate.of(2026, 8, 8), "3"),
            sampleCase("٧", "مدني مستأنف", LocalDate.of(2026, 8, 9), "7"),
            sampleCase("٥", "جنح", LocalDate.of(2026, 8, 9), "5"),
            sampleCase("٢", "مدني كلي", LocalDate.of(2026, 7, 1), "2")
        )

        val table = CaseStatementTableBuilder.build(
            cases,
            CaseStatementType.CIVIL,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31)
        )

        assertEquals(3, table.rows.size)
        assertEquals("٣", table.rows[0][1])
        assertEquals("٧", table.rows[1][1])
        assertEquals("١٢", table.rows[2][1])
        assertTrue(table.title.contains("بيان القضايا المدنية طرف السيد الخبير"))
        assertTrue(table.title.contains("01/08/2026"))
        assertTrue(table.title.contains("31/08/2026"))
        assertFalse(table.headers.contains("نوع الدعوى"))
    }

    @Test
    fun `statement keeps registered data and capacity without type column`() {
        val party = Party(
            firstName = "أحمد",
            restName = "محمد علي",
            role = PartyRole.PLAINTIFF,
            address = "كوم أمبو - أسوان",
            orderIndex = 0,
            withCapacity = true
        )
        val case = Case(
            incomingNo = "10",
            incomingDate = LocalDate.of(2026, 8, 4),
            caseNo = "272",
            caseYear = "2025",
            court = "كوم أمبو",
            caseType = "مدني كلي",
            parties = listOf(party),
            receiptDate = LocalDate.of(2026, 8, 5),
            preliminaryJudgmentDate = LocalDate.of(2026, 7, 15)
        )

        val table = CaseStatementTableBuilder.build(
            listOf(case),
            CaseStatementType.CIVIL,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31)
        )

        val row = table.rows.single()
        assertEquals("272", row[3])
        assertEquals("2025", row[4])
        assertEquals("كوم أمبو", row[5])
        assertTrue(row[6].contains("أحمد محمد علي بصفته"))
        assertTrue(row[7].contains("كوم أمبو - أسوان"))
        assertEquals("05/08/2026", row[8])
        assertEquals("15/07/2026", row[9])
    }

    @Test
    fun `high appeal statement excludes civil appealed cases`() {
        val cases = listOf(
            sampleCase("1", "استئناف عالي", LocalDate.of(2026, 8, 1), "1"),
            sampleCase("2", "مدني مستأنف", LocalDate.of(2026, 8, 2), "2"),
            sampleCase("3", "مدني كلي", LocalDate.of(2026, 8, 3), "3")
        )
        val table = CaseStatementTableBuilder.build(
            cases,
            CaseStatementType.HIGH_APPEAL,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31)
        )
        assertEquals(1, table.rows.size)
        assertEquals("1", table.rows.single()[1])
    }

    @Test
    fun `civil appealed remains in civil statement`() {
        val table = CaseStatementTableBuilder.build(
            listOf(sampleCase("22", "مدني مستأنف", LocalDate.of(2026, 8, 12), "220")),
            CaseStatementType.CIVIL,
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 31)
        )
        assertEquals(1, table.rows.size)
        assertEquals("22", table.rows.single()[1])
    }

    private fun sampleCase(incoming: String, type: String, date: LocalDate, caseNo: String) = Case(
        incomingNo = incoming,
        incomingDate = date,
        caseNo = caseNo,
        caseYear = "2026",
        court = "أسوان",
        caseType = type
    )
}

