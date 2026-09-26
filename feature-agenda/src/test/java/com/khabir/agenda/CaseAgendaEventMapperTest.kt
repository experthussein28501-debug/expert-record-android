package com.khabir.agenda

import com.khabir.app.domain.model.Case
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CaseAgendaEventMapperTest {
    private fun sampleCase(
        hearingDate: LocalDate? = LocalDate.of(2026, 10, 5),
        archived: Boolean = false
    ) = Case(
        incomingNo = "12",
        incomingDate = LocalDate.of(2026, 9, 26),
        caseNo = "345",
        caseYear = "2026",
        court = "محكمة كوم أمبو",
        caseType = "جنح",
        hearingDate = hearingDate,
        hearingTime = "٩ صباحًا",
        isArchived = archived
    )

    @Test
    fun `case hearing becomes agenda event with court and time`() {
        val event = sampleCase().toHearingAgendaEventOrNull()!!
        assertEquals(LocalDate.of(2026, 10, 5), event.date)
        assertEquals("الدعوى 345 لسنة 2026", event.title)
        assertEquals("٩ صباحًا", event.time)
        assertEquals("محكمة كوم أمبو", event.location)
        assertEquals(AgendaEventSource.CASE_HEARING, event.source)
    }

    @Test
    fun `case without hearing date does not create agenda event`() {
        assertNull(sampleCase(hearingDate = null).toHearingAgendaEventOrNull())
    }

    @Test
    fun `archived case does not create agenda event`() {
        assertNull(sampleCase(archived = true).toHearingAgendaEventOrNull())
    }
}
