package com.khabir.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTemplateTest {

    @Test
    fun `ordered export keeps enabled blank sections in selected template order`() {
        val template = ReportTemplateCatalog.free.copy(
            sections = listOf(
                ReportSectionDefinition("subject", "أولاً", orderIndex = 1),
                ReportSectionDefinition("facts", "ثانياً", orderIndex = 0),
                ReportSectionDefinition("hidden", "لا يظهر", enabled = false, orderIndex = 2)
            )
        )
        val report = Report(
            templateId = template.id,
            templateName = template.name,
            templateSectionsSpec = ReportTemplateCodec.encode(template),
            subjectOfCase = "موضوع مختصر"
        )

        assertEquals(listOf("ثانياً", "أولاً"), report.orderedTemplateSections().map { it.first })
        assertEquals("", report.orderedTemplateSections().first().second)
        assertEquals("موضوع مختصر", report.orderedTemplateSections()[1].second)
    }
    @Test
    fun `civil template follows supplied report structure and keeps witnesses available`() {
        val template = ReportTemplateCatalog.civil
        assertTrue(template.verifiedFromUserReports)
        assertEquals("الموضوع", template.orderedSections().first().title)
        assertTrue(template.sections.any { it.title == "سماع الشهود" && !it.enabled })
        assertEquals("النتيجة النهائية", template.orderedSections().last { it.enabled }.title)
        assertFalse(template.sections.any { it.id == "opinion" || it.title == "الرأي الفني" })
    }

    @Test
    fun `family court template follows estate and minors reports`() {
        val family = ReportTemplateCatalog.family
        assertEquals(ReportTemplateKind.FAMILY, family.kind)
        assertTrue(family.verifiedFromUserReports)
        assertEquals(
            listOf("الموضوع", "المأمورية", "مباشرة المأمورية", "الأقوال", "المعاينة على الطبيعة", "بحث المستندات", "البحث", "النتيجة النهائية"),
            family.orderedSections().filter { it.enabled }.map { it.title }
        )
        assertEquals(
            listOf("أقوال الوصي/الوصية أو الولي", "أقوال باقي أصحاب الشأن"),
            family.sections.first { it.id == "statements" }.headingLines
        )
        assertFalse(family.sections.first { it.id == "calculations" }.enabled)
        assertFalse(family.sections.any { it.id == "opinion" || it.title == "الرأي الفني" })
    }

    @Test
    fun `drive templates preserve each report type structure`() {
        val misdemeanor = ReportTemplateCatalog.misdemeanor
        assertTrue(misdemeanor.verifiedFromUserReports)
        assertEquals(
            listOf("الموضوع", "المأمورية", "مباشرة المأمورية", "أقوال المتهم", "أقوال محرر محضر المخالفة", "المعاينة على الطبيعة", "أقوال الشهود", "النتيجة النهائية"),
            misdemeanor.orderedSections().filter { it.enabled }.map { it.title }
        )
        assertFalse(misdemeanor.sections.first { it.id == "documents" }.enabled)
        assertFalse(misdemeanor.sections.first { it.id == "research" }.enabled)

        val appeal = ReportTemplateCatalog.appeal
        assertTrue(appeal.verifiedFromUserReports)
        assertEquals(listOf("أقوال المستأنفين", "أقوال المستأنف ضدهم"), appeal.sections.first { it.id == "statements" }.headingLines)
        assertEquals("الحكم المستأنف والتقارير السابقة", appeal.sections.first { it.id == "facts" }.title)
        assertTrue(appeal.sections.first { it.id == "documents" }.headingLines.contains("المستندات المرفقة بملف الدعوى"))
        assertEquals("البحث والرد على المأمورية", appeal.sections.first { it.id == "research" }.title)

        val highAppeal = ReportTemplateCatalog.highAppeal
        assertEquals(ReportTemplateKind.HIGH_APPEAL, highAppeal.kind)
        assertTrue(highAppeal.verifiedFromUserReports)
        assertEquals("الحكم المستأنف ومسار الطعن والتقارير السابقة", highAppeal.sections.first { it.id == "facts" }.title)
        assertEquals("بحث المستندات والتقارير السابقة", highAppeal.sections.first { it.id == "documents" }.title)
        assertNotEquals(appeal.sections, highAppeal.sections)
    }

    @Test
    fun `deprecated technical opinion is removed from saved templates`() {
        val old = ReportTemplateCatalog.civil.copy(
            sections = ReportTemplateCatalog.civil.sections + ReportSectionDefinition("opinion", "الرأي الفني", orderIndex = 99)
        )
        val decoded = ReportTemplateCodec.decode(old.id, old.name, ReportTemplateCodec.encode(old))
        assertFalse(decoded.sections.any { it.id == "opinion" || it.title == "الرأي الفني" })
    }

    @Test
    fun `heading lines are editable addable and removable`() {
        val original = ReportTemplateCatalog.civil
        val edited = original
            .renameSection("assignment", "المأمورية المعدلة")
            .addHeadingLine("assignment", "السطر الأول")
            .addHeadingLine("assignment", "السطر الثاني")
            .updateHeadingLine("assignment", 1, "سطر ثان معدل")
            .removeHeadingLine("assignment", 0)

        val assignment = edited.sections.first { it.id == "assignment" }
        assertEquals("المأمورية المعدلة", assignment.title)
        assertEquals(listOf("سطر ثان معدل"), assignment.headingLines)
    }

    @Test
    fun `sections can be hidden deleted and reordered while final result is protected`() {
        val original = ReportTemplateCatalog.civil
        val edited = original
            .setSectionEnabled("inspection", false)
            .deleteSection("documents")
            .deleteSection("conclusion")
            .moveSection("witnesses", 0)

        assertFalse(edited.sections.first { it.id == "inspection" }.enabled)
        assertFalse(edited.sections.any { it.id == "documents" })
        assertTrue(edited.sections.any { it.id == "conclusion" })
        assertEquals("witnesses", edited.orderedSections().first().id)
    }

    @Test
    fun `custom copy no longer shares built in section identity`() {
        val copy = ReportTemplateCatalog.civil.duplicateAsCustom("mine", "قالب خاص")
        assertEquals(ReportTemplateKind.CUSTOM, copy.kind)
        assertFalse(copy.isBuiltIn)
        assertNotEquals(ReportTemplateCatalog.civil.sections.first().id, copy.sections.first().id)
    }

    @Test
    fun `editable template snapshot survives database text encoding`() {
        val edited = ReportTemplateCatalog.civil
            .renameSection("assignment", "مأمورية مخصصة")
            .addHeadingLine("assignment", "السطر الأول")
            .setSectionEnabled("witnesses", true)
            .moveSection("witnesses", 2)
        val decoded = ReportTemplateCodec.decode(edited.id, "قالب القضية", ReportTemplateCodec.encode(edited))

        assertEquals("قالب القضية", decoded.name)
        assertEquals("مأمورية مخصصة", decoded.sections.first { it.id == "assignment" }.title)
        assertEquals(listOf("السطر الأول"), decoded.sections.first { it.id == "assignment" }.headingLines)
        assertTrue(decoded.sections.first { it.id == "witnesses" }.enabled)
        assertEquals("witnesses", decoded.orderedSections()[2].id)
    }
}
