package com.khabir.app.presentation.components
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class ArabicListHangingIndentTransformationTest {
    @Test fun `numbered line keeps text and gets hanging indent`() { val i=AnnotatedString("١- بند طويل يمكن أن يلتف إلى سطر تالٍ"); val o=ArabicListHangingIndentTransformation().filter(i).text; assertEquals(i.text,o.text); assertTrue(o.paragraphStyles.isNotEmpty()); assertEquals(24.sp,o.paragraphStyles.first().item.textIndent?.restLine) }
    @Test fun `plain paragraph remains without list indentation`() { val i=AnnotatedString("فقرة عادية بدون ترقيم"); val o=ArabicListHangingIndentTransformation().filter(i).text; assertEquals(i.text,o.text); assertTrue(o.paragraphStyles.isEmpty()) }
}
