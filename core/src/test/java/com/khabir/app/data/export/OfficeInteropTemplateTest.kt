package com.khabir.app.data.export

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class OfficeInteropTemplateTest {
    private fun service() = OfficeInteropService(RuntimeEnvironment.getApplication())
    @Test fun placeholderAcrossRunsPreservesParagraphStyle() {
        val xml = """<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t>{{المو</w:t></w:r><w:r><w:t>ضوع}}</w:t></w:r></w:p>"""
        val output = service().replaceTemplateTokensAcrossRuns(xml, mapOf("الموضوع" to "سطر أول\nسطر ثان"))
        assertTrue(output.contains("<w:bidi/>"))
        assertTrue(output.contains("سطر أول"))
        assertTrue(output.contains("<w:br/>"))
        assertTrue(output.contains("سطر ثان"))
        assertFalse(output.contains("{{"))
    }
    @Test(timeout = 1000) fun selfReferencingValueDoesNotLoopForever() {
        val xml = "<w:p><w:r><w:t>{{الموضوع}}</w:t></w:r></w:p>"
        assertEquals(xml, service().replaceTemplateTokensAcrossRuns(xml, mapOf("الموضوع" to "{{الموضوع}}")))
    }

    @Test fun blankParagraphAfterKnownHeadingIsFilledAndKeepsHeading() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>الموضوع :</w:t></w:r></w:p><w:p><w:pPr><w:spacing w:after="120"/></w:pPr></w:p></w:body></w:document>"""
        val (output, count) = service().injectReportFieldsAfterBlankHeadings(xml, mapOf("الموضوع" to "طلب صحة ونفاذ عقد بيع"))
        assertEquals(1, count)
        assertTrue(output.contains("الموضوع :"))
        assertTrue(output.contains("طلب صحة ونفاذ عقد بيع"))
        assertTrue(output.contains("<w:bidi/>"))
        assertTrue(output.contains("w:val=\"start\""))
    }

    @Test fun existingCaseTextIsNeverOverwrittenByAutomaticTemplateFill() {
        val xml = """<w:document><w:body><w:p><w:r><w:t>المأمورية</w:t></w:r></w:p><w:p><w:r><w:t>نص قضية قديمة</w:t></w:r></w:p></w:body></w:document>"""
        val (output, count) = service().injectReportFieldsAfterBlankHeadings(xml, mapOf("المأمورية" to "مأمورية جديدة"))
        assertEquals(0, count)
        assertEquals(xml, output)
    }
}
