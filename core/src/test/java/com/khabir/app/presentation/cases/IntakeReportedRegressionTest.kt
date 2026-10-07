package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.*
import org.junit.Test

class IntakeReportedRegressionTest {
    @Test fun assignmentSurvivesReviewedDocumentWithoutJudgmentInTitle() {
        val docs = DocumentReviewParser.parse("""
            [[DOCUMENT 1]]
            نوع المستند: صحيفة دعوى
            الدعوى رقم 48 لسنة 2025 مدني كلي أسوان
            موضوع الدعوى: يطلب الطالب تسليم العين التي اشتراها.
            [[END DOCUMENT]]
            [[DOCUMENT 2]]
            نوع المستند: قرار ندب خبير
            الدعوى رقم 48 لسنة 2025 مدني كلي أسوان
            مأمورية الحكم التمهيدي: معاينة العين وبيان حدودها وتحقيق كافة عناصر الدعوى
            [[END DOCUMENT]]
        """.trimIndent())
        val group = DocumentReviewParser.matchingGroup(docs, docs.first())
        assertEquals(2, group.size)
        val data = PetitionIntakeParser.parse(DocumentReviewParser.combinedText(group))
        assertTrue(data.preliminaryMission.orEmpty().contains("معاينة العين"))
    }

    @Test fun missingAssignmentPlaceholderDoesNotHideOperativeText() {
        val data = PetitionIntakeParser.parse("""
            نوع المستند: حكم تمهيدي
            مأمورية الحكم التمهيدي: غير مذكور
            منطوق الحكم: حكمت المحكمة بندب خبير تكون مهمته معاينة العين وبيان حدودها وتحقيق كافة عناصر الدعوى
        """.trimIndent())
        assertEquals("معاينة العين وبيان حدودها وتحقيق كافة عناصر الدعوى", data.preliminaryMission)
    }

    @Test fun assignmentStopsBeforeDepositAndHearingWithoutInclusiveEnding() {
        val data = PetitionIntakeParser.parse("""
            حكمت المحكمة بندب خبير تكون مهمته معاينة الأرض وبيان مالكها
            وألزمت المدعي بإيداع أمانة قدرها ١٠٠٠ جنيه وحددت جلسة ١/١/٢٠٢٧
        """.trimIndent())
        assertEquals("معاينة الأرض وبيان مالكها", data.preliminaryMission)
    }

    @Test fun emptyAssignmentRemainsEmptyInsteadOfConsumingFollowingField() {
        val data = PetitionIntakeParser.parse("""
            مأمورية الحكم التمهيدي:
            ملاحظات: لا توجد مهمة ظاهرة في هذه الصورة
        """.trimIndent())
        assertNull(data.preliminaryMission)
    }

    @Test fun sharedPlaintiffAddressWithWawIsAssignedToBothNames() {
        val parties = PetitionIntakeParser.parse("""
            بناء على طلب
            ١- أحمد محمد علي
            ٢- حسن محمود سالم
            والمقيمان بناحية الكوبانية - مركز أسوان
            ومحلهم المختار مكتب الأستاذ محمد عبد الله المحامي بأسوان
            الموضوع
        """.trimIndent()).parties.filter { it.role == PartyRole.PLAINTIFF }
        assertEquals(listOf("أحمد محمد علي", "حسن محمود سالم"), parties.map { it.name })
        assertTrue(parties.all { it.address == "ناحية الكوبانية - مركز أسوان" })
    }

    @Test fun multilineChosenOfficeDoesNotBecomePlaintiffOrResidentialAddress() {
        val parties = PetitionIntakeParser.parse("""
            بناء على طلب
            ١- أحمد محمد علي المقيم في دراو
            ومحلهم المختار
            مكتب الأستاذ محمد عبد الله المحامي بأسوان
            أنا محضر محكمة أسوان انتقلت إلى ناحية كوم أمبو
            ١- حسن محمود سالم
            الموضوع
        """.trimIndent()).parties
        assertEquals(listOf("أحمد محمد علي"), parties.filter { it.role == PartyRole.PLAINTIFF }.map { it.name })
        assertEquals("دراو", parties.first { it.role == PartyRole.PLAINTIFF }.address)
        assertTrue(parties.any { it.role == PartyRole.LAWYER && it.name == "محمد عبد الله" })
    }

    @Test fun multilineBailiffTransitionsKeepSeparateAddressGroups() {
        val parties = PetitionIntakeParser.parse("""
            بناء على طلب أحمد محمد علي المقيم في أسوان
            أنا محضر محكمة أسوان
            انتقلت في تاريخه إلى ناحية
            دراو - شارع النيل
            حيث إقامة كل من
            ١- حسن محمود سالم
            ٢- مصطفى علي حسن
            مخاطبًا مع / شقيقه
            ثم انتقلت إلى ناحية
            كوم أمبو - شارع السوق
            حيث إقامة
            ٣- خالد أحمد محمود
            مخاطبًا مع / شخصه
            وأعلنتهم بالآتي
            الموضوع
        """.trimIndent()).parties.filter { it.role == PartyRole.DEFENDANT }
        assertEquals(listOf("حسن محمود سالم", "مصطفى علي حسن", "خالد أحمد محمود"), parties.map { it.name })
        assertEquals(listOf("دراو - شارع النيل", "دراو - شارع النيل", "كوم أمبو - شارع السوق"), parties.map { it.address })
    }
}
