package com.khabir.app.domain.usecase.cases

import com.khabir.app.domain.model.Case
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class CaseImportDuplicatePolicyTest {
    private fun case(
        incomingNo: String = "5373",
        incomingDate: LocalDate = LocalDate.of(2025, 5, 1),
        caseNo: String = "272",
        caseYear: String = "2025",
        court: String = "مدني جزئي كوم أمبو"
    ) = Case(
        incomingNo = incomingNo,
        incomingDate = incomingDate,
        caseNo = caseNo,
        caseYear = caseYear,
        court = court,
        caseType = "مدني جزئي"
    )

    @Test
    fun `same imported case stays duplicate even if incoming date differs`() {
        assertEquals(
            caseImportDuplicateKey(case(incomingDate = LocalDate.of(2025, 5, 1))),
            caseImportDuplicateKey(case(incomingDate = LocalDate.of(2025, 5, 2)))
        )
    }

    @Test
    fun `arabic spelling and extra spaces do not create false new case`() {
        assertEquals(
            caseImportDuplicateKey(case(court = "مدني جزئي كوم أمبو")),
            caseImportDuplicateKey(case(court = "  مدني   جزئي كوم امبو "))
        )
    }

    @Test
    fun `different incoming number remains a distinct office record`() {
        assertNotEquals(
            caseImportDuplicateKey(case(incomingNo = "5373")),
            caseImportDuplicateKey(case(incomingNo = "6001"))
        )
    }
}

