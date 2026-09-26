package com.khabir.app.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicListHangingIndentTransformationTest {
    @Test fun `numbered line keeps text and gets hanging indent`() {
        val input = AnnotatedString("١- بند طويل يمكن أن يلتف إلى سطر تالٍ")
        val output = ArabicListHangingIndentTransformation().filter(input).text
        assertEquals(input.text, output.text)
        assertTrue(output.paragraphStyles.isNotEmpty())
        assertEquals(24.sp, output.paragraphStyles.first().item.textIndent?.restLine)
    }

    @Test fun `plain paragraph remains without list indentation`() {
        val input = AnnotatedString("فقرة عادية بدون ترقيم")
        val output = ArabicListHangingIndentTransformation().filter(input).text
        assertEquals(input.text, output.text)
        assertTrue(output.paragraphStyles.isEmpty())
    }
}
