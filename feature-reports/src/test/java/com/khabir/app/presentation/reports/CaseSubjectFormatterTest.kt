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
        assertTrue(result.startsWith("أقام المدعي دعواه بموجب صحيفة معلنة قانونًا، وطلب في ختامها:"))
        assertTrue(result.indexOf("وطلب في ختامها:") < result.indexOf("وحيث قال شارحًا دعواه:"))
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
        assertTrue(result.indexOf("وحيث قال شارحًا دعواه:") < result.indexOf("وطلب في ختامها:"))
    }

    @Test
    fun `reorders an existing formatted subject without losing text`() {
        val start = CaseSubjectFormatter.format(extracted, FinalRequestsPlacement.START)
        val end = CaseSubjectFormatter.reorderFormatted(start, FinalRequestsPlacement.END)
        assertTrue(end.contains("يمتلك المدعي العين محل التداعي"))
        assertTrue(end.contains("الحكم بصحة ونفاذ العقد"))
        assertTrue(end.indexOf("وحيث قال شارحًا دعواه:") < end.indexOf("طلب في ختامها:"))
    }
    @Test
    fun `does not dump a whole unstructured document into subject`() {
        val raw = "محكمة أسوان\nأنا المحضر بمحكمة أسوان\nنص غير مصنف لا يحتوي موضوعًا أو طلبات"
        assertEquals("", CaseSubjectFormatter.format(raw))
    }

    @Test
    fun `does not invent a closing fact absent from the source`() {
        val result = CaseSubjectFormatter.format(extracted)
        assertFalse(result.contains("مما حدا به إلى إقامة الدعوى الماثلة"))
    }


    @Test fun `reviewed subject still honors the old placement setting`() {
        val current=CaseSubjectFormatter.format(extracted)
        val wrapped="موضوع الدعوى: $current\nالطلبات الختامية:\nمأمورية الحكم التمهيدي: المعاينة"
        val result=CaseSubjectFormatter.format(wrapped,FinalRequestsPlacement.END)
        assertTrue(result.indexOf("وحيث قال") < result.indexOf("طلب في ختامها"))
    }
    @Test fun `criminal reviewed subject does not acquire a civil opening`() {
        val result=CaseSubjectFormatter.format("موضوع الدعوى: النيابة العامة ضد أحمد وآخرين\nالطلبات الختامية:")
        assertEquals("النيابة العامة ضد أحمد وآخرين",result)
    }
}
