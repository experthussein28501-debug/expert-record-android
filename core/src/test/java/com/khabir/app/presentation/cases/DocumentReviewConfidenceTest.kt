package com.khabir.app.presentation.cases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentReviewConfidenceTest {
    private fun doc(id: Int, raw: String, caseNo: String? = null, caseYear: String? = null) = ReviewedDocument(
        id = id, pageNumbers = listOf(id), type = if (id == 1) "عريضة دعوى" else "حكم تمهيدي",
        rawText = raw, caseNo = caseNo, caseYear = caseYear, court = null,
        primaryPartyNames = emptyList(), status = DocumentMatchStatus.UNCERTAIN, reason = ""
    )

    @Test fun `unnumbered petition matches judgment at seventy percent or more by parties and addresses`() {
        val petition = doc(1, """
            الخصم: أحمد علي محمود | العنوان: قرية النجاجرة كوم أمبو أسوان | الصفة: مدعي | الدعوى: أصلية
            الخصم: محمد حسن علي | العنوان: قرية النجاجرة كوم أمبو أسوان | الصفة: مدعى عليه | الدعوى: أصلية
        """.trimIndent())
        val judgment = doc(2, """
            الدعوى رقم 105 لسنة 2025 مدني كلي كوم امبو
            الخصم: أحمد علي محمود | العنوان: قرية النجاجرة كوم أمبو أسوان | الصفة: مدعي | الدعوى: أصلية
            الخصم: محمد حسن علي | العنوان: قرية النجاجرة كوم أمبو أسوان | الصفة: مدعى عليه | الدعوى: أصلية
        """.trimIndent(), caseNo = "105", caseYear = "2025")

        val (status, reason) = DocumentReviewParser.compare(petition, judgment)
        assertEquals(DocumentMatchStatus.MATCHED, status)
        assertTrue(reason.contains("70") || reason.contains("100") || reason.contains("نسبة"))
    }

    @Test fun `weak party overlap stays uncertain`() {
        val petition = doc(1, """
            الخصم: أحمد علي محمود | العنوان: كوم أمبو أسوان | الصفة: مدعي | الدعوى: أصلية
            الخصم: حسن سالم محمد | العنوان: دراو أسوان | الصفة: مدعى عليه | الدعوى: أصلية
        """.trimIndent())
        val judgment = doc(2, """
            الخصم: أحمد علي محمود | العنوان: كوم أمبو أسوان | الصفة: مدعي | الدعوى: أصلية
            الخصم: محمود إبراهيم حسن | العنوان: إدفو أسوان | الصفة: مدعى عليه | الدعوى: أصلية
        """.trimIndent())

        assertEquals(DocumentMatchStatus.UNCERTAIN, DocumentReviewParser.compare(petition, judgment).first)
    }

    @Test fun `different explicit case number is different document`() {
        val first = doc(1, "رقم الدعوى: 105\nسنة الدعوى: 2025", "105", "2025")
        val second = doc(2, "رقم الدعوى: 106\nسنة الدعوى: 2025", "106", "2025")
        assertEquals(DocumentMatchStatus.DIFFERENT, DocumentReviewParser.compare(first, second).first)
    }
}
