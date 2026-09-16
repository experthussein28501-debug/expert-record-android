package com.khabir.app.presentation.reports

import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveMapCaptureTest {
    @Test
    fun `google maps is the default report map provider`() {
        val url = reportMapUrl("أسوان")
        assertTrue(url.startsWith("https://www.google.com/maps/"))
        assertTrue(url.contains("query="))
    }

    @Test
    fun `open street map remains an explicit fallback`() {
        val url = reportMapUrl("دراو", ReportMapProvider.OPEN_STREET_MAP)
        assertTrue(url.startsWith("https://www.openstreetmap.org/"))
    }
}
