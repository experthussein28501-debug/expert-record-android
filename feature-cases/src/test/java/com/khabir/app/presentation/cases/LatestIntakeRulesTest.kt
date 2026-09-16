package com.khabir.app.presentation.cases

import org.junit.Assert.*
import org.junit.Test

class LatestIntakeRulesTest {
    @Test fun missionStartsAtBareTaskAndStopsBeforeFees() {
        assertEquals("معاينة الأرض وتحقيق كافة عناصر الدعوى",
            IntakeNarrative.missionBody("ندب مكتب الخبراء مهمته معاينة الأرض وتحقيق كافة عناصر الدعوى وألزمت بأمانة 500 جنيه"))
    }
    @Test fun counterclaimKeepsItsExplanationAndArea() {
        val text = IntakeNarrative.counterclaim(listOf("أحمد"), "التسليم", false, "أرض مساحتها فدان وحدها البحري طريق")
        assertTrue(text.contains("أحمد"))
        assertTrue(text.contains("التسليم"))
        assertTrue(text.contains("فدان وحدها البحري طريق"))
    }
    @Test fun noReturnHistoryWithoutEvidence() {
        assertEquals("", IntakeNarrative.returnedHistory(null, null, null))
    }
}
