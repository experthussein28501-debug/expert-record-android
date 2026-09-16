package com.khabir.app.data.export

import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialNotificationDocxBuilderTest {
    @Test
    fun `notification output is landscape four-up and follows official wording`() {
        val card = OfficialNotificationCard(
            ministryOrSector = "وزارة العدل",
            department = "إدارة خبراء أسوان",
            expertJobTitle = "الخبير المحال إليه المأمورية",
            expertName = "حسين الطيب حسين",
            officeAddress = "أسوان شارع أبطال التحرير",
            attendancePhrase = "الرجاء الحضور إلى مكتب خبراء وزارة العدل",
            attendanceLocation = "أسوان شارع أبطال التحرير عمارة الأوقاف الدور الخامس",
            recipientName = "محمد أحمد علي",
            recipientAddress = "كوم أمبو",
            dayName = "الأربعاء",
            appointmentDate = "19-8-2026",
            appointmentTime = "10:00 ص",
            issueDate = "5-8-2026",
            caseReference = "الدعوى رقم 105 لسنة 2025 مدني جزئي كوم أمبو",
            plaintiffs = "محمد أمين عبده فضل وآخرين",
            defendants = "إسحاق فهمي عبده فضل وآخرين",
            requestedDocuments = "خريطة وكشف تحديد وسند ملكية"
        )
        val sample = OfficialNotificationDocxBuilder().build(listOf(card, card, card, card))
        val sampleDir = java.io.File("build/notice-fixtures").apply { mkdirs() }
        java.io.File(sampleDir, "ordinary-notices.docx").writeBytes(sample)
        val xml = documentXml(sample)

        assertTrue(xml.contains("w:orient=\"landscape\""))
        assertEquals(4, Regex("السيد/").findAll(xml).count())
        assertTrue(xml.contains("الخبير المحال إليه المأمورية"))
        assertTrue(xml.contains("الرجاء الحضور إلى مكتب خبراء وزارة العدل"))
        assertTrue(xml.replace(Regex("<[^>]+>"), "").contains("الدعوى رقم ١٠٥ لسنة ٢٠٢٥"))
        assertTrue(xml.contains("في حالة عدم حضوركم سيتم مباشرة المأمورية غيابياً"))
        assertTrue(xml.contains("w:jc w:val=\"right\""))
        assertTrue(xml.contains("w:sz w:val=\"22\""))
        assertTrue(xml.contains("w:line=\"220\""))
        assertTrue(xml.contains("w:u w:val=\"single\""))
        // Main 2x2 sheet plus the eight header/footer tables must all use visual RTL.
        assertEquals(9, Regex("<w:bidiVisual/>").findAll(xml).count())
        assertFalse(xml.contains("w:textDirection"))
        listOf("١", "٢", "٣", "٤").forEach { assertTrue(xml.contains(">$it<")) }
        assertFalse(xml.contains("w:type=\"page\""))
        assertFalse(xml.contains("${'$'}{card"))
    }

    @Test
    fun `more than four selected notifications continue on a second landscape page`() {
        val card = OfficialNotificationCard(
            ministryOrSector = "وزارة العدل", department = "إدارة خبراء أسوان",
            expertJobTitle = "الخبير المحال إليه المأمورية", expertName = "خبير اختبار",
            officeAddress = "عنوان المكتب", attendancePhrase = "الرجاء الحضور إلى مكتب الخبراء",
            attendanceLocation = "عنوان المكتب", recipientName = "اسم الخصم", recipientAddress = "عنوان الخصم",
            dayName = "الأحد", appointmentDate = "1-9-2026", appointmentTime = "10 ص",
            issueDate = "27-8-2026", caseReference = "الدعوى رقم 1 لسنة 2026",
            plaintiffs = "مدع", defendants = "مدعى عليه"
        )
        val xml = documentXml(OfficialNotificationDocxBuilder().build(List(5) { card }))

        assertEquals(5, Regex("السيد/").findAll(xml).count())
        assertEquals(1, Regex("w:type=\"page\"").findAll(xml).count())
    }

    private fun documentXml(bytes: ByteArray): String {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("word/document.xml missing")
    }
}
