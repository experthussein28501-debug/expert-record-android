package com.khabir.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaseValidationTest {
    private fun sample(year: String) = Case(
        incomingNo = "1",
        incomingDate = LocalDate.of(2026, 1, 1),
        caseNo = "100",
        caseYear = year,
        court = "مدني جزئي أسوان",
        caseType = "مدني جزئي"
    )

    @Test
    fun `accepts Arabic and Western four digit years`() {
        assertTrue(sample("2026").validate().isEmpty())
        assertTrue(sample("٢٠٢٦").validate().isEmpty())
    }

    @Test
    fun `rejects incomplete or unreasonable year`() {
        assertTrue(CaseValidationError.InvalidCaseYear in sample("26").validate())
        assertTrue(CaseValidationError.InvalidCaseYear in sample("1800").validate())
        assertFalse(CaseValidationError.MissingCaseYear in sample("26").validate())
    }
    @Test
    fun `accepts judicial year for high appeal and administrative judiciary`() {
        val highAppeal = sample("21ق").copy(caseType = "استئناف عالي", court = "محكمة استئناف قنا")
        val admin = sample("٢١ ق").copy(caseType = "قضاء إداري", court = "محكمة القضاء الإداري")
        assertTrue(highAppeal.validate().isEmpty())
        assertTrue(admin.validate().isEmpty())
    }

    @Test
    fun `civil appeal still requires Gregorian year`() {
        val civilAppeal = sample("21ق").copy(caseType = "مدني مستأنف", court = "مدني مستأنف أسوان")
        assertTrue(CaseValidationError.InvalidCaseYear in civilAppeal.validate())
    }

}
