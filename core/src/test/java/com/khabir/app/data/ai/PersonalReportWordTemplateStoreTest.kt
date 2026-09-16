package com.khabir.app.data.ai

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class PersonalReportWordTemplateStoreTest {
    @Test
    fun personal_word_templates_are_isolated_per_report_type() {
        val store = PersonalAiKeyStore(RuntimeEnvironment.getApplication())
        store.clearReportTemplate("built_in_civil")
        store.clearReportTemplate("drive_family_estate")

        store.writeReportTemplateUri("built_in_civil", "content://civil.docx")
        store.writeReportTemplateMapping("built_in_civil", mapOf("الموضوع القديم" to "الموضوع"))
        store.writeReportTemplateUri("drive_family_estate", "content://family.docx")
        store.writeReportTemplateMapping("drive_family_estate", mapOf("التركة القديمة" to "الموضوع"))

        assertEquals("content://civil.docx", store.readReportTemplateUri("built_in_civil"))
        assertEquals("content://family.docx", store.readReportTemplateUri("drive_family_estate"))
        assertEquals("الموضوع", store.readReportTemplateMapping("built_in_civil")["الموضوع القديم"])
        assertEquals("الموضوع", store.readReportTemplateMapping("drive_family_estate")["التركة القديمة"])

        store.clearReportTemplate("drive_family_estate")
        assertTrue(store.readReportTemplateUri("drive_family_estate").isBlank())
        assertEquals("content://civil.docx", store.readReportTemplateUri("built_in_civil"))
    }
}
