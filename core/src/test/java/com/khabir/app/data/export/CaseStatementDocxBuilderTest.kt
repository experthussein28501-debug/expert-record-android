package com.khabir.app.data.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class CaseStatementDocxBuilderTest {
    @Test
    fun `builds landscape rtl statement with title headers rows and total`() {
        val headers = listOf(
            "م", "رقم الوارد", "تاريخ الإحالة / الوارد", "رقم الدعوى", "السنة",
            "المحكمة / المأمورية", "أسماء الخصوم", "العناوين", "تاريخ استلام القضية", "تاريخ الحكم التمهيدي"
        )
        val bytes = CaseStatementDocxBuilder().build(
            title = "بيان القضايا المدنية طرف السيد الخبير مرتبة بترتيب الوارد من تاريخ 01/08/2026 حتى تاريخ 31/08/2026",
            headers = headers,
            rows = listOf(
                listOf("1", "10", "04/08/2026", "272", "2025", "كوم أمبو", "مدعي: أحمد محمد", "أحمد محمد: أسوان", "05/08/2026", "15/07/2026")
            )
        )

        val xml = readEntry(bytes, "word/document.xml")
        assertTrue(xml.contains("بيان القضايا المدنية طرف السيد الخبير"))
        assertTrue(xml.contains("رقم الوارد"))
        assertTrue(xml.contains("٢٧٢"))
        assertTrue(xml.contains("٠٤/٠٨/٢٠٢٦"))
        assertTrue(xml.contains("أسماء الخصوم"))
        assertTrue(xml.contains("إجمالي عدد القضايا: ١"))
        assertTrue(xml.contains("w:orient=\"landscape\""))
        assertTrue(xml.contains("<w:bidiVisual/>"))
        assertTrue(xml.contains("<w:tblHeader/>"))
    }

    private fun readEntry(bytes: ByteArray, path: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == path) return zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        error("Missing DOCX entry: $path")
    }
}

