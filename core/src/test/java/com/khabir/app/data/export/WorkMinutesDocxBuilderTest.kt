package com.khabir.app.data.export

import com.khabir.app.domain.model.WorkMinutesEntry
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream

class WorkMinutesDocxBuilderTest {
    @Test
    fun `exports follow up appointment arabic numerals and left expert signature`() {
        val bytes = WorkMinutesDocxBuilder().build(
            ministry = "وزارة العدل",
            sector = "قطاع الخبراء",
            department = "إدارة خبراء أسوان",
            incomingNo = "5830/2025",
            caseIntro = "فى الدعوى رقم",
            caseNo = "146",
            caseYear = "2025",
            court = "مدني جزئي نصر النوبة",
            plaintiffs = "حسن محمد",
            defendants = "سيد أحمد وآخرين",
            entries = listOf(
                WorkMinutesEntry(
                    number = 1,
                    openingDate = LocalDate.of(2026, 9, 18),
                    openingTime = "9 صباحًا",
                    bodyText = "حضر الخصوم وتمت مناقشتهم",
                    closingTime = "10 صباحًا",
                    expertName = "حسين الطيب",
                    scheduledFollowUpDate = LocalDate.of(2026, 9, 25),
                    scheduledFollowUpTime = "11 صباحًا",
                    scheduledFollowUpLocation = "بالمكتب"
                )
            ),
            logoBytes = null
        )

        val xml = readEntry(bytes, "word/document.xml")
        assertTrue(xml.contains("١٤٦"))
        assertTrue(xml.contains("٢٠٢٥"))
        assertTrue(xml.contains("٢٥/٩/٢٠٢٦"))
        assertTrue(xml.contains("١١ صباحًا"))
        assertTrue(xml.contains("موعدًا لمتابعة مباشرة المأمورية"))
        assertTrue(xml.contains("الخبير/ حسين الطيب"))
        assertTrue(xml.contains("w:val=\"end\""))
    }


    @Test
    fun `keeps numbered work minute lines as separate Word paragraphs`() {
        val bytes = WorkMinutesDocxBuilder().build(
            ministry = "وزارة العدل",
            sector = "قطاع الخبراء",
            department = "إدارة خبراء أسوان",
            incomingNo = "",
            caseIntro = "فى الدعوى رقم",
            caseNo = "1",
            caseYear = "2026",
            court = "مدني",
            plaintiffs = "",
            defendants = "",
            entries = listOf(
                WorkMinutesEntry(
                    number = 2,
                    openingDate = LocalDate.of(2026, 9, 18),
                    bodyText = "1. مستند أول\n2. مستند ثان\n• ملاحظة"
                )
            ),
            logoBytes = null
        )

        val xml = readEntry(bytes, "word/document.xml")
        assertTrue(xml.contains("١. مستند أول"))
        assertTrue(xml.contains("٢. مستند ثان"))
        assertTrue(xml.contains("• ملاحظة"))
        assertTrue(xml.indexOf("١. مستند أول") < xml.indexOf("</w:p>", xml.indexOf("١. مستند أول")))
        assertTrue(xml.indexOf("</w:p>", xml.indexOf("١. مستند أول")) < xml.indexOf("٢. مستند ثان"))
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
