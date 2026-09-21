package com.khabir.app.presentation.reports
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ReportDocumentRequestTest {
    @Test fun independentInstructionsStayWithTheirDocuments() {
        val pages = (1..3).map { File("page-$it") }
        val groups = groupReportPages(pages, listOf(false, false, false), listOf("ملخص", "نتيجة", "تواريخ"))
        assertEquals(listOf("ملخص", "نتيجة", "تواريخ"), groups.map { it.instruction })
        assertEquals(pages, groups.flatMap { it.pages })
    }
    @Test fun continuationUsesFirstPagesInstruction() {
        val pages = (1..3).map { File("page-$it") }
        val groups = groupReportPages(pages, listOf(false, true, false), listOf("ملخص", "", "نتيجة"))
        assertEquals(2, groups.size)
        assertEquals(pages.take(2), groups.first().pages)
        assertEquals("ملخص", groups.first().instruction)
        assertEquals("نتيجة", groups.last().instruction)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyInstructionCannotStartAnalysis() {
        groupReportPages(listOf(File("1")), listOf(false), listOf(" "))
    }
}
