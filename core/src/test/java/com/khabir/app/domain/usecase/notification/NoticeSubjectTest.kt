package com.khabir.app.domain.usecase.notification
import org.junit.Assert.*
import org.junit.Test
class NoticeSubjectTest {
    @Test fun requestedReliefWinsOverNarrative() {
        assertEquals("صحة ونفاذ عقد بيع", NoticeSubject.suggest("الحكم بصحة ونفاذ عقد البيع المؤرخ وتسجيله", "ذكر تعويض وريع في الوقائع"))
        assertEquals("تعويض عن نزع الملكية", NoticeSubject.suggest("تعويض عن نزع الملكية", ""))
    }
    @Test fun extractsFromPetitionAndDoesNotGuessMissingSubject() {
        assertEquals("ريع", NoticeSubject.suggest("", "تفاصيل الدعوى وطلب في ختامها إلزام الخصم بأداء الريع"))
        assertEquals("", NoticeSubject.suggest("", "الدعوى فيها مستندات كثيرة"))
        assertEquals("مقابل الانتفاع", NoticeSubject.suggest("مقابل الانتفاع", ""))
    }
}
