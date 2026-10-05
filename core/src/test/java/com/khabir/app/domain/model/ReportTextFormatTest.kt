package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ReportTextFormatTest {
    @Test fun `format survives metadata persistence`() {
        val format = ReportTextFormat("left", "Times New Roman", 18, true, 200)
        val metadata = ReportCustomSectionCodec.encode(mapOf(ReportTextFormat.KEY to format.encode(), "custom" to "نص"))
        assertEquals(format, ReportTextFormat.decode(ReportCustomSectionCodec.decode(metadata)[ReportTextFormat.KEY]))
    }
    @Test fun `old and malformed values have safe defaults`() {
        assertEquals(ReportTextFormat(), ReportTextFormat.decode(null))
        assertEquals(ReportTextFormat(), ReportTextFormat.decode("bad|bad|bad|bad|bad"))
        assertEquals(24, ReportTextFormat.decode("right|Arial|999|false|300").size)
    }
    @Test fun `legacy subject combines requests before explanation without duplication`() {
        val combined = UnifiedCaseSubject.compose("شرح الدعوى", "إلزام المدعى عليه")
        assertTrue(combined.indexOf("إلزام المدعى عليه") < combined.indexOf("شرح الدعوى"))
        assertEquals(combined, UnifiedCaseSubject.compose(combined, "إلزام المدعى عليه"))
        assertEquals(combined, UnifiedCaseSubject.compose(combined, ""))
        assertEquals("شرح الدعوى", UnifiedCaseSubject.compose("شرح الدعوى", ""))
    }
}
