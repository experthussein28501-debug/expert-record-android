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
class PersonalWorkMinutesWordTemplateStoreTest {
    @Test
    fun work_minutes_word_template_is_saved_and_cleared_independently_of_report_templates() {
        val store = PersonalAiKeyStore(RuntimeEnvironment.getApplication())
        store.clearWorkMinutesTemplate()
        store.clearReportTemplate("built_in_civil")

        store.writeWorkMinutesTemplateUri("content://work_minutes.docx")
        store.writeWorkMinutesTemplateMapping(mapOf("النص القديم" to "محاضر الأعمال"))
        store.writeReportTemplateUri("built_in_civil", "content://civil.docx")

        assertEquals("content://work_minutes.docx", store.readWorkMinutesTemplateUri())
        assertEquals("محاضر الأعمال", store.readWorkMinutesTemplateMapping()["النص القديم"])
        assertEquals("content://civil.docx", store.readReportTemplateUri("built_in_civil"))

        store.clearWorkMinutesTemplate()
        assertTrue(store.readWorkMinutesTemplateUri().isBlank())
        assertTrue(store.readWorkMinutesTemplateMapping().isEmpty())
        assertEquals("content://civil.docx", store.readReportTemplateUri("built_in_civil"))
    }
}
