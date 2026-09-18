package com.khabir.app.presentation.reports

import org.junit.Assert.assertTrue
import org.junit.Test

class CaseSubjectFormatterTest {
    private val extracted = """
        شرح الدعوى:
        يمتلك المدعي العين محل التداعي بموجب عقد بيع.
        الطلبات الختامية:
        الحكم بصحة ونفاذ العقد وإلزام المدعى عليهم بالمصاريف.
    """.trimIndent()

    @Test
    fun `places final requests at start when selected`() {
        val result = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        assertTrue(result.indexOf("وطلب في ختامها:") < result.indexOf("وحيث قال شارحًا دعواه:"))
    }

    @Test
    fun `places final requests at end when selected`() {
        val result = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.END)
        assertTrue(result.indexOf("وحيث قال شارحًا دعواه:") < result.indexOf("وطلب في ختامها:"))
    }

    @Test
    fun `reorders an existing formatted subject without losing text`() {
        val start = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        val end = CaseSubjectFormatter.reorderFormatted(start, FinalRequestsPlacement.END)
        assertTrue(end.contains("يمتلك المدعي العين محل التداعي"))
        assertTrue(end.contains("الحكم بصحة ونفاذ العقد"))
        assertTrue(end.indexOf("وحيث قال شارحًا دعواه:") < end.indexOf("وطلب في ختامها:"))
    }
}
