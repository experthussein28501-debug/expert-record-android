package com.khabir.app.data.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class OfficialMailCoverDocxBuilderTest {

    @Test
    fun `mail cover keeps Arabic table horizontal and rtl`() {
        val bytes = OfficialMailCoverDocxBuilder().build(
            expertName = "خبير اختبار",
            officeAddress = "عنوان المكتب",
            rows = listOf(
                listOf("1", "أحمد محمد", "أسوان", "272 لسنة 2025", "مدني كلي كوم أمبو")
            )
        )

        val documentXml = unzipEntry(bytes, "word/document.xml")

        assertTrue(documentXml.contains("حافظة بريد المكتب"))
        assertTrue(documentXml.contains("اسم المرسل إليه"))
        assertTrue(documentXml.contains("<w:bidiVisual/>"))
        assertTrue(documentXml.contains("<w:bidi/>"))
        assertTrue(documentXml.contains("<w:rtl/>"))
        assertTrue(documentXml.contains("w:bidi=\"ar-EG\""))
        assertFalse(documentXml.contains("<w:textDirection"))
        assertFalse(documentXml.contains("w:val=\"rlTb\""))
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

