package com.khabir.app.data.export

import com.khabir.app.domain.model.WorkMinutesEntry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class ExportVisualFixtureTest {
    @Test fun writesLongArabicMinutesFixtureForVisualInspection() {
        val text = (1..18).joinToString("\n") { index ->
            "س/ السؤال رقم $index عن المستندات المقدمة وحدود المعاينة وبيانات الأطراف\nج/ تمت مراجعة المستندات ومطابقة بياناتها مع محضر المعاينة، وتثبت هذه العبارة التجريبية التفاف السطور داخل هامش الكتابة واستمرار النص عبر الصفحات."
        }
        val output = WorkMinutesDocxBuilder().buildToFile(File("build/visual-evidence/work-minutes-long.docx"),
            "وزارة العدل", "قطاع الخبراء", "إدارة خبراء أسوان", "20/2026", "فى الدعوى رقم", "100", "2026", "مدني جزئي كوم أمبو",
            "اسم تجريبي أول", "اسم تجريبي ثان", listOf(WorkMinutesEntry(number = 1, openingDate = LocalDate.of(2026, 9, 20), bodyText = text, expertName = "خبير تجريبي")), null)
        assertTrue(output.length() > 1000)
    }
}
