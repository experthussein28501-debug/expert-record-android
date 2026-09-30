package com.khabir.agenda

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgendaExportTest {
    @Test fun spreadsheetDoesNotExecuteImportedFormulaText() {
        assertEquals("\"'=SUM(1,2)\"", agendaCsvCell("=SUM(1,2)"))
        assertEquals("\"'  @SUM(A1)\"", agendaCsvCell("  @SUM(A1)"))
        assertEquals("\"جلسة \"\"القضية\"\"\"", agendaCsvCell("جلسة \"القضية\""))
    }

    @Test fun handwrittenAndPhotographedDaysAreNotReportedAsEmpty() {
        val date = LocalDate.of(2026, 9, 20)
        assertFalse(AgendaDaySummary(date).hasRecordedWork())
        assertFalse(AgendaDaySummary(date, note = AgendaDayNote(date, hiddenImportedKeys = setOf("deleted"))).hasRecordedWork())
        assertTrue(AgendaDaySummary(date, note = AgendaDayNote(date, imagePaths = listOf("photo.jpg"))).hasRecordedWork())
        assertTrue(AgendaDaySummary(date, note = AgendaDayNote(date, strokes = listOf(AgendaStroke(listOf(AgendaPoint(1f, 2f)))))).hasRecordedWork())
    }
}
