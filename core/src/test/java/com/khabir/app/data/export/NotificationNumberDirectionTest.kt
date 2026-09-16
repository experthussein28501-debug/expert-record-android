package com.khabir.app.data.export

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class NotificationNumberDirectionTest {
    private fun run(text: String) = "<w:r><w:rPr><w:b/><w:rtl/></w:rPr><w:t xml:space=\"preserve\">$text</w:t></w:r>"
    @Test fun preservesNumbersAndArabicTextInSeparateDirections() {
        val text = "الدعوى 105/2025 بتاريخ ١٩-٨-٢٠٢٦ الساعة 10:30 والمساحة 12.75"
        val output = NotificationNumberDirection.apply(run(text))
        assertEquals("الدعوى ١٠٥/٢٠٢٥ بتاريخ ١٩-٨-٢٠٢٦ الساعة ١٠:٣٠ والمساحة ١٢.٧٥", output.replace(Regex("<[^>]+>"), ""))
        listOf("١٠٥/٢٠٢٥", "١٩-٨-٢٠٢٦", "١٠:٣٠", "١٢.٧٥").forEach {
            assertTrue(output.contains("<w:t xml:space=\"preserve\">$it</w:t>"))
        }
        assertFalse(output.contains("<w:bdo"))
        assertTrue(output.contains("<w:rtl w:val=\"0\"/>"))
        assertTrue(output.contains("<w:b/><w:rtl/>"))
    }
    @Test fun preservesPageBreaksAndProducesWellFormedXml() {
        val pageBreak = "<w:r><w:rPr><w:rtl/></w:rPr><w:br w:type=\"page\"/></w:r>"
        val input = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p>$pageBreak${run("الدعوى ١٠٥")}</w:p></w:body></w:document>"
        val output = NotificationNumberDirection.apply(input)
        assertTrue(output.contains(pageBreak))
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(output.byteInputStream())
        assertEquals("الدعوى ١٠٥", doc.documentElement.textContent)
    }
    @Test fun leavesNonNumericRunsUntouched() {
        val input = run("الخبير المحال إليه المأمورية")
        assertEquals(input, NotificationNumberDirection.apply(input))
    }
}
