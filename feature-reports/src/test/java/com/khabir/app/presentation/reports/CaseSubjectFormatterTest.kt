package com.khabir.app.presentation.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseSubjectFormatterTest {
    private val extracted = """
        شرح الدعوى:
        يمتلك المدعي العين محل التداعي بموجب عقد بيع.
        والحد البحري طريق بعرض 6 أمتار، ومساحة العين 240 مترًا.
        الطلبات الختامية:
        الحكم بصحة ونفاذ العقد وإلزام المدعى عليهم بالمصاريف.
    """.trimIndent()

    @Test
    fun `places final requests at start when selected`() {
        val result = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        assertTrue(result.startsWith("أقام المدعي دعواه بموجب صحيفة معلنة للمدعى عليهم قانونًا طلب في ختامها:"))
        assertTrue(result.indexOf("طلب في ختامها:") < result.indexOf("وحيث قال شارحًا دعواه:"))
    }

    @Test
    fun `keeps area and boundaries inside explanation`() {
        val result = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        val explanation = result.substringAfter("وحيث قال شارحًا دعواه:")
        assertTrue(explanation.contains("الحد البحري"))
        assertTrue(explanation.contains("مساحة العين"))
    }

    @Test
    fun `removes duplicated final requests from explanation`() {
        val duplicated = """
            شرح الدعوى:
            يمتلك المدعي العين محل النزاع.
            الحكم بصحة ونفاذ العقد وإلزام المدعى عليهم بالمصاريف.
            الطلبات الختامية:
            الحكم بصحة ونفاذ العقد وإلزام المدعى عليهم بالمصاريف.
        """.trimIndent()
        val result = CaseSubjectFormatter.format(duplicated)
        assertEquals(1, Regex("الحكم بصحة ونفاذ العقد").findAll(result).count())
    }

    @Test
    fun `raw petition explanation stops before binaa alaih`() {
        val raw = """
            وأعلنته بالآتي:
            يمتلك الطالب قطعة الأرض محل النزاع ومساحتها 300 متر وحدودها ثابتة بالعقد.
            وقد امتنع المعلن إليهم عن التنفيذ.
            بناء عليه
            الحكم بصحة ونفاذ العقد وإلزام المدعى عليهم بالمصاريف.
        """.trimIndent()
        val result = CaseSubjectFormatter.format(raw)
        val explanation = result.substringAfter("وحيث قال شارحًا دعواه:")
        assertTrue(explanation.contains("يمتلك الطالب"))
        assertTrue(explanation.contains("مساحتها 300 متر"))
        assertFalse(explanation.contains("بناء عليه"))
        assertEquals(1, Regex("الحكم بصحة ونفاذ العقد").findAll(result).count())
    }

    @Test
    fun `places final requests at end when selected`() {
        val result = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.END)
        assertTrue(result.indexOf("وحيث قال شارحًا دعواه:") < result.indexOf("طلب في ختامها:"))
    }

    @Test
    fun `reorders an existing formatted subject without losing text`() {
        val start = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        val end = CaseSubjectFormatter.reorderFormatted(start, FinalRequestsPlacement.END)
        assertTrue(end.contains("يمتلك المدعي العين محل التداعي"))
        assertTrue(end.contains("الحكم بصحة ونفاذ العقد"))
        assertTrue(end.indexOf("وحيث قال شارحًا دعواه:") < end.indexOf("طلب في ختامها:"))
    }
}
