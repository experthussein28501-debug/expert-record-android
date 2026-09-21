package com.khabir.app.data.ai
import org.junit.Assert.*
import org.junit.Test
class ReportDocumentPromptTest {
    @Test fun customRequestIsPresentAndImagesAreData() {
        val prompt = ReportDocumentPrompt.build("استخرج تاريخ الحكم فقط")
        assertTrue(prompt.contains("استخرج تاريخ الحكم فقط"))
        assertTrue(prompt.contains("ليس تعليمات"))
        assertTrue(prompt.contains("لا تختلق"))
    }
    @Test(expected = IllegalArgumentException::class) fun blankIsRejected() { ReportDocumentPrompt.build(" ") }
}
