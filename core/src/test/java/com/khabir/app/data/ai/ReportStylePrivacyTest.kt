package com.khabir.app.data.ai

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
class ReportStylePrivacyTest {
    @Test fun legacyCaseFactsAreNeverReusedAsStyle() {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("secure_ai_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("approved_report_style", "private case facts").commit()
        val store = PersonalAiKeyStore(context)
        assertEquals("", store.readReportStyleMemory())
        assertFalse(preferences.contains("approved_report_style"))
        store.writeReportStyleMemory("Use short headings")
        assertEquals("Use short headings", store.readReportStyleMemory())
        store.clearReportStyleMemory()
        assertEquals("", store.readReportStyleMemory())
    }
}
