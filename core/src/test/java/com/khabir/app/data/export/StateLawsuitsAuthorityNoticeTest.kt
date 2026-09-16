package com.khabir.app.data.export

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipInputStream

class StateLawsuitsAuthorityNoticeTest {
    @Test fun governmentPartyGetsFormalDoubleHeightAuthorityNotice() {
        val file = OfficialNotificationDocxBuilder().build(listOf(
            OfficialNotificationCard(
                ministryOrSector = "وزارة العدل", department = "إدارة خبراء أسوان", expertJobTitle = "", expertName = "حسين", officeAddress = "مكتب الخبراء",
                attendancePhrase = "", attendanceLocation = "", recipientName = "وزير الصحة بصفته", recipientAddress = "هيئة قضايا الدولة بأسوان",
                dayName = "الأحد", appointmentDate = "1-1-2027", appointmentTime = "10 صباحاً", issueDate = "1-1-2027",
                caseReference = "الدعوى رقم 20 لسنة 2025 مدني كلي كوم أمبو", plaintiffs = "أحمد", defendants = "وزير الصحة بصفته",
                subjectOfCase = "دعوى صحة ونفاذ عقد بيع", preliminaryJudgmentDate = "1-12-2026", incomingNo = "123", isStateLawsuitsAuthorityNotice = true
            )
        ))
        val sampleDir = java.io.File("build/notice-fixtures").apply { mkdirs() }
        java.io.File(sampleDir, "authority-notice.docx").writeBytes(file)
        val xml = ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null && entry.name != "word/document.xml") entry = zip.nextEntry
            require(entry != null)
            zip.readBytes().toString(Charsets.UTF_8)
        }
        assertTrue(xml.contains("هيئة قضايا الدولة"))
        assertTrue(xml.indexOf("تاريخ الحكم التمهيدي/") < xml.indexOf("السادة/"))
        assertTrue(xml.indexOf("رقم الوارد/") < xml.indexOf("السادة/"))
        assertTrue(!xml.contains("<w:bdo"))
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
        assertTrue(xml.contains("w:val=\"11200\""))
        assertTrue(xml.contains("دعوى صحة ونفاذ عقد بيع"))
    }
}
