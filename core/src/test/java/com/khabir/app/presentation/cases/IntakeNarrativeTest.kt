package com.khabir.app.presentation.cases

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class IntakeNarrativeTest {
    @Test fun subjectKeepsAreaAndBounds() {
        val subject = IntakeNarrative.subject("مساحتها ١٥ قيراطًا، الحد البحري طريق والقبلي ترعة.", "فرز وتجنيب النصيب")
        assertTrue(subject.startsWith("أقام المدعي دعواه بموجب صحيفة أودعت قلم المحكمة ومعلنة قانونًا"))
        assertTrue(subject.contains("١٥ قيراطًا"))
        assertTrue(subject.indexOf("فرز وتجنيب") < subject.indexOf("وحيث قال"))
        assertTrue(subject.endsWith("مما حدا به إلى إقامة الدعوى الماثلة."))
        assertEquals("", IntakeNarrative.subject(null, "طلبات من حكم"))
    }
    @Test fun missionStopsBeforeDepositAndKeepsDate() {
        val body = IntakeNarrative.missionBody("حكمت المحكمة بندب خبير تكون مهمته بعد مطالعة أوراق الدعوى بيان المساحة وتحقيق كافة عناصر الدعوى وألزمت المدعي بأمانة ٥٠٠ جنيه")
        assertEquals("بعد مطالعة أوراق الدعوى بيان المساحة وتحقيق كافة عناصر الدعوى", body)
        assertTrue(IntakeNarrative.mission(body, LocalDate.of(2025, 7, 15)).startsWith("قضى حكم الإحالة الصادر بجلسة 15/07/2025 بالآتي:"))
        assertFalse(body.orEmpty().contains("٥٠٠"))
    }
    @Test fun returnedMissionStopsAtPreviousDeposit() {
        assertEquals("بحث الاعتراضات بذات الأمانة السابقة", IntakeNarrative.missionBody("تكون مهمته بحث الاعتراضات بذات الأمانة السابقة وحددت جلسة"))
        assertEquals("بحث الاعتراضات بأمانة تكميلية", IntakeNarrative.missionBody("تكون مهمته بحث الاعتراضات بأمانة تكميلية قدرها ألف جنيه"))
    }
    @Test fun counterclaimRolesRemainIndependent() {
        val result = PetitionIntakeParser.parse("""
            الخصم: أحمد علي | العنوان: أسوان | الصفة: مدعي | الدعوى: أصلية
            الخصم: أحمد علي | العنوان: أسوان | الصفة: مدعى عليه | الدعوى: فرعية
        """.trimIndent())
        assertEquals(2, result.parties.size)
        assertEquals("فرعية", result.parties[1].claimKind)
        assertEquals("أحمد علي", result.parties[1].name)
    }
    @Test fun serviceRecipientsAreNotPartiesAndEachLocationIsIndependent() {
        val result = PetitionIntakeParser.parse("""
            أنا المحضر انتقلت في تاريخه إلى ناحية دراو
            1- حسن علي
            مخاطبًا مع أحمد مستلم الإعلان
            أنا المحضر انتقلت في تاريخه إلى ناحية كوم أمبو
            2- محمود سالم
            مخاطبًا مع سعيد مستلم الإعلان
            الموضوع
        """.trimIndent())
        assertEquals(listOf("حسن علي", "محمود سالم"), result.parties.map { it.name })
        assertEquals(listOf("دراو", "كوم أمبو"), result.parties.map { it.address })
    }
    @Test fun judgmentAloneKeepsSubjectEmpty() {
        val docs = DocumentReviewParser.parse("""
            [[DOCUMENT 1]]
            نوع المستند: حكم تمهيدي
            الصفحات: 1
            رقم الدعوى: 105
            سنة الدعوى: 2025
            المحكمة: أسوان
            موضوع الدعوى:
            الطلبات الختامية:
            مأمورية الحكم التمهيدي: معاينة العين وبيان حدودها
            [[END DOCUMENT]]
        """.trimIndent())
        val result = PetitionIntakeParser.parse(DocumentReviewParser.combinedText(docs))
        assertTrue(result.subjectOfCase.isNullOrBlank())
        assertEquals("105", result.caseNo)
        assertEquals("2025", result.caseYear)
        assertTrue(result.preliminaryMission.orEmpty().contains("معاينة العين"))
    }
    @Test fun matchesBothDocumentsButNeverDifferentCase() {
        fun document(id: Int, no: String, type: String) = """
            [[DOCUMENT $id]]
            نوع المستند: $type
            الصفحات: $id
            رقم الدعوى: $no
            سنة الدعوى: 2025
            المحكمة: أسوان
            [[END DOCUMENT]]
        """.trimIndent()
        val docs = DocumentReviewParser.parse(document(1, "105", "عريضة") + "\n" + document(2, "105", "حكم") + "\n" + document(3, "77", "حكم"))
        assertEquals(listOf(1, 2), DocumentReviewParser.matchingGroup(docs, docs[0]).map { it.id })
        assertEquals(listOf(3), DocumentReviewParser.matchingGroup(docs, docs[2]).map { it.id })
    }
    @Test fun combinedPetitionAndJudgmentRetainBothNarratives() {
        val documents = DocumentReviewParser.parse("""
            [[DOCUMENT 1]]
            نوع المستند: عريضة دعوى
            الصفحات: 1
            رقم الدعوى: 105
            سنة الدعوى: 2025
            المحكمة: أسوان
            موضوع الدعوى: يدعي ملكية مساحة 15 قيراطًا حدها البحري الطريق.
            الطلبات الختامية: فرز وتجنيب نصيبه في العين
            [[END DOCUMENT]]
            [[DOCUMENT 2]]
            نوع المستند: حكم تمهيدي
            الصفحات: 2
            رقم الدعوى: 105
            سنة الدعوى: 2025
            المحكمة: أسوان
            تاريخ الحكم التمهيدي: 15/07/2025
            مأمورية الحكم التمهيدي: بعد مطالعة أوراق الدعوى بيان الحدود وتحقيق كافة عناصر الدعوى
            [[END DOCUMENT]]
        """.trimIndent())
        val combined = PetitionIntakeParser.parse(DocumentReviewParser.combinedText(documents))
        assertTrue(combined.subjectOfCase.orEmpty().contains("15 قيراطًا"))
        assertTrue(combined.subjectOfCase.orEmpty().contains("فرز وتجنيب"))
        assertTrue(combined.preliminaryMission.orEmpty().startsWith("قضى حكم الإحالة"))
        assertTrue(combined.preliminaryMission.orEmpty().contains("15/07/2025"))
        assertTrue(combined.parties.isEmpty())
    }
}
