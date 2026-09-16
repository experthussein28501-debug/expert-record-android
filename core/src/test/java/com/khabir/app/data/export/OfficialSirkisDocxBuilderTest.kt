package com.khabir.app.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class OfficialSirkisDocxBuilderTest {

    @Test
    fun `sirkis keeps Arabic horizontal RTL and 31 rows per page`() {
        val rows = (1..32).map { index ->
            listOf(
                index.toString(),
                "23/8/2026",
                "$index لسنة 2026",
                "30/8/2026",
                "المدعي رقم $index ضد المدعى عليه",
                "صادر $index",
                ""
            )
        }

        val bytes = OfficialSirkisDocxBuilder().build(
            expertName = "خبير وزارة العدل",
            rows = rows
        )
        val documentXml = unzipEntry(bytes, "word/document.xml")
        val stylesXml = unzipEntry(bytes, "word/styles.xml")

        // 32 records must produce two pages; every page is padded to 31 body rows.
        assertEquals(2, Regex("سركي إخطارات").findAll(documentXml).count())
        assertEquals(2, Regex("الخبير / خبير وزارة العدل").findAll(documentXml).count())
        assertEquals(1, Regex("<w:br w:type=\"page\"/>").findAll(documentXml).count())

        // Seven columns: one header row + 31 body rows on each page = 64 rows total.
        assertEquals(64, Regex("<w:tr>").findAll(documentXml).count())
        assertTrue(documentXml.contains("اسماء الخصوم"))
        assertTrue(documentXml.contains("تاريخ الجلسة"))
        assertTrue(documentXml.contains("التوقيع"))

        // Normal Arabic must stay horizontal. RTL is expressed at paragraph/run/table level.
        assertFalse(documentXml.contains("<w:textDirection"))
        assertTrue(documentXml.contains("<w:bidiVisual/>"))
        assertTrue(documentXml.contains("<w:bidi/>"))
        assertTrue(documentXml.contains("<w:rtl/>"))
        assertTrue(stylesXml.contains("w:bidi=\"ar-EG\""))
        assertTrue(stylesXml.contains("Traditional Arabic"))
    }

    private fun unzipEntry(bytes: ByteArray, path: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == path) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("Missing DOCX entry: $path")
    }
}
