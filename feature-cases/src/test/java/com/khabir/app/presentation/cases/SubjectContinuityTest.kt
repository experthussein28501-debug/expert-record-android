package com.khabir.app.presentation.cases

import org.junit.Assert.*
import org.junit.Test

class SubjectContinuityTest {
    private fun document(id: Int, type: String, body: String) = """
        [[DOCUMENT $id]]
        نوع المستند: $type
        الصفحات: $id
        رقم الدعوى: 105
        سنة الدعوى: 2025
        المحكمة: أسوان
        $body
        [[END DOCUMENT]]
    """.trimIndent()

    private fun subject(vararg documents: String): String = PetitionIntakeParser.parse(
        DocumentReviewParser.combinedText(DocumentReviewParser.parse(documents.joinToString("\n")))
    ).subjectOfCase.orEmpty()

    private val original = document(1, "عريضة دعوى", "موضوع الدعوى: يدعي ملكية مساحة 15 قيراطًا حدها البحري الطريق.\nالطلبات الختامية: فرز وتجنيب نصيبه في العين")
    private val counter = document(2, "صحيفة دعوى فرعية", "الخصم: حسن علي | العنوان: أسوان | الصفة: مدعي | الدعوى: فرعية\nموضوع الدعوى: يطلب التعويض عن أرض مساحتها فدان وحدها البحري طريق\nالطلبات الختامية: إلزام المدعي أصليًا بالتعويض")

    @Test fun counterclaimFollowsOriginalWithNamesRequestsAndExplanation() {
        val result = subject(counter, original)
        assertTrue(result.indexOf("مما حدا به") < result.indexOf("وأثناء سير الدعوى"))
        assertTrue(result.contains("قام حسن علي بإقامة دعوى فرعية"))
        assertTrue(result.contains("إلزام المدعي أصليًا بالتعويض"))
        assertTrue(result.contains("وقال شرحًا لها: يطلب التعويض عن أرض مساحتها فدان وحدها البحري طريق"))
        assertTrue(result.indexOf("إلزام المدعي أصليًا بالتعويض") < result.indexOf("وقال شرحًا لها"))
        assertEquals(1, Regex("أقام المدعي دعواه").findAll(result).count())
    }

    @Test fun incidentalRequestKeepsItsTypeAndWorksWithoutSummary() {
        val request = document(2, "صحيفة طلب عارض", "الخصم: محمود سالم | العنوان: دراو | الصفة: مدعي | الدعوى: طلب عارض\nالطلبات الختامية: إلزام الخصم بتقديم المستندات")
        val result = subject(original, request)
        assertTrue(result.contains("قام محمود سالم بتقديم طلب عارض"))
        assertFalse(result.contains("بإقامة دعوى فرعية"))
    }

    @Test fun returnedHistoryWorksWithAndWithoutCounterclaim() {
        val judgment = document(3, "حكم تمهيدي", "دليل إعادة الدعوى: حكمت المحكمة بإعادة الدعوى إلى مكتب الخبراء\nدليل التقرير السابق: أودع الخبير تقريره\nدليل تداول الدعوى: تداولت الدعوى بالجلسات")
        listOf(subject(original, judgment), subject(original, counter, judgment)).forEach { result ->
            assertTrue(result.contains("نحيل إليها منعًا للتكرار"))
            assertTrue(result.contains("ثم تداولت الدعوى بالجلسات أمام المحكمة"))
            assertTrue(result.endsWith("ثم أعيدت الدعوى إلى مكتب الخبراء مرة أخرى."))
        }
    }

    @Test fun returnAloneDoesNotInventReportOrHearings() {
        val judgment = document(2, "حكم تمهيدي", "دليل إعادة الدعوى: إعادة المأمورية للخبير مرة أخرى\nدليل التقرير السابق:\nدليل تداول الدعوى:")
        val result = subject(original, judgment)
        assertTrue(result.contains("أعيدت الدعوى إلى مكتب الخبراء"))
        assertFalse(result.contains("أودع الخبير"))
        assertFalse(result.contains("تداولت الدعوى"))
    }

    @Test fun reportOrDepositWithoutReturnDoesNotAddReturnedHistory() {
        val judgment = document(2, "حكم تمهيدي", "مأمورية الحكم التمهيدي: بحث الاعتراضات بأمانة تكميلية\nدليل إعادة الدعوى:\nدليل التقرير السابق: أودع الخبير تقريره\nدليل تداول الدعوى: تداولت الدعوى بالجلسات")
        assertFalse(subject(original, judgment).contains("أعيدت الدعوى"))
    }

    @Test fun returnedJudgmentAloneDoesNotInventOriginalPetition() {
        val judgment = document(1, "حكم تمهيدي", "دليل إعادة الدعوى: أعادت المحكمة المأمورية إلى الخبير")
        val result = subject(judgment)
        assertTrue(result.contains("أعيدت الدعوى"))
        assertFalse(result.contains("أقام المدعي"))
    }
}
