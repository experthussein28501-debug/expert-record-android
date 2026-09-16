package com.khabir.app.presentation.cases
import org.junit.Assert.*
import org.junit.Test

class JudgmentCaseIdentityTest {
    @Test fun abbreviatedCaseOverridesLetterheadAndCircuit() {
        val result = PetitionIntakeParser.parse("محكمة أسوان الابتدائية مأمورية دراو\nالدائرة الثالثة\nالدعوى رقم20لسنة2025م.ك كوم امبو")
        assertEquals("20", result.caseNo)
        assertEquals("2025", result.caseYear)
        assertEquals("مدني كلي", result.caseType)
        assertEquals("كوم امبو", result.court)
    }
    @Test fun fullCivilTypeAndArabicDigits() {
        val result = JudgmentCaseIdentity.parse("الدعوى رقم ٢٠ لسنة ٢٠٢٥ مدنى كلى كوم امبو الدائرة الخامسة")!!
        assertEquals("20", result.number)
        assertEquals("مدني كلي", result.type)
        assertEquals("كوم امبو", result.court)
    }
    @Test fun civilAppealDoesNotUseHighAppealLetterhead() {
        val result = JudgmentCaseIdentity.parse("محكمة أسوان الابتدائية\nالدعوى رقم 30 لسنة 2025 مدنى مستانف كوم امبو")!!
        assertEquals("مدني مستأنف", result.type)
        assertEquals("كوم امبو", result.court)
    }
    @Test fun highAppealKeepsCourtButNeverCircuit() {
        val result = JudgmentCaseIdentity.parse("محكمة استئناف قنا الدائرة الخامسة\nالدعوى رقم 20 لسنة 2025 استئناف عالي قنا")!!
        assertEquals("محكمة استئناف قنا", result.court)
    }
}
