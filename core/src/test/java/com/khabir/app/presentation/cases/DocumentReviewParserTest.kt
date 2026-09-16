package com.khabir.app.presentation.cases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentReviewParserTest {
    @Test
    fun `matches petition and judgment and isolates different judgment`() {
        val documents = DocumentReviewParser.parse(
            """
            [[DOCUMENT 1]]
            الصفحات: 1,2
            نوع المستند: عريضة دعوى
            الدعوى رقم 105 لسنة 2025 مدني كلي أسوان
            المرفوعة من / أحمد محمد
            ضد / حسن محمود
            [[END DOCUMENT]]
            [[DOCUMENT 2]]
            الصفحات: 3
            نوع المستند: حكم تمهيدي
            الدعوى رقم 105 لسنة 2025 مدني كلي أسوان
            [[END DOCUMENT]]
            [[DOCUMENT 3]]
            الصفحات: 4
            نوع المستند: حكم تمهيدي
            الدعوى رقم 77 لسنة 2024 مدني كلي قنا
            [[END DOCUMENT]]
            """.trimIndent()
        )

        assertEquals(3, documents.size)
        assertEquals(DocumentMatchStatus.MATCHED, documents[1].status)
        assertEquals(DocumentMatchStatus.DIFFERENT, documents[2].status)
        assertTrue(documents[2].reason.contains("رقم الدعوى مختلف"))
    }

    @Test
    fun `keeps incomplete identity uncertain`() {
        val documents = DocumentReviewParser.parse(
            """
            [[DOCUMENT 1]]
            الصفحات: 1
            نوع المستند: عريضة دعوى
            المرفوعة من / أحمد محمد
            [[END DOCUMENT]]
            [[DOCUMENT 2]]
            الصفحات: 2
            نوع المستند: حكم تمهيدي
            المدعي: أحمد محمد
            [[END DOCUMENT]]
            """.trimIndent()
        )
        assertEquals(DocumentMatchStatus.UNCERTAIN, documents[1].status)
    }
}
