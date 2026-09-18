package com.khabir.app.domain.usecase.report

import com.khabir.app.domain.model.ReportCoverFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCoverWordingTest {
    private fun cover(
        caseType: String = "",
        court: String = "",
        caseNo: String = "1",
        caseYear: String = "2026"
    ) = ReportCoverFields(
        court = court,
        caseNo = caseNo,
        caseYear = caseYear,
        incomingNo = "",
        incomingYear = "",
        expertName = "خبير",
        ministryOrSector = "قطاع الخبراء",
        department = "إدارة خبراء أسوان",
        plaintiffsSummary = "أ",
        defendantsSummary = "ب",
        partiesSummary = "",
        caseType = caseType
    )

    @Test
    fun `all report types use الدعوى in case line`() {
        listOf(
            reportCoverWording(cover("جنح", "مركز كوم أمبو")),
            reportCoverWording(cover("تركات", "شؤون الأسرة")),
            reportCoverWording(cover("وصاية", "نيابة شئون الأسرة")),
            reportCoverWording(cover("مدني كلي", "كوم أمبو")),
            reportCoverWording(cover("مدني مستأنف", "أسوان"))
        ).forEach { wording ->
            assertEquals("فى الدعوى رقم", wording.caseIntro)
            assertEquals("الدعوى", wording.headerCaseLabel)
        }
    }

    @Test
    fun `criminal keeps prosecution against accused style and compact cover`() {
        val wording = reportCoverWording(cover("جنح", "مركز كوم أمبو"))
        assertEquals(ReportCoverKind.CRIMINAL, wording.kind)
        assertTrue(wording.criminal)
        assertTrue(wording.compactCover)
        assertEquals("جنح مركز كوم أمبو", reportCaseDescriptor(cover("جنح", "مركز كوم أمبو")))
    }

    @Test
    fun `civil type is printed before court after number and year`() {
        assertEquals("مدني جزئي أسوان", reportCaseDescriptor(cover("مدني جزئي", "أسوان")))
        assertEquals("مدني كلي كوم أمبو", reportCaseDescriptor(cover("مدني كلي", "كوم أمبو")))
        assertEquals("مدني مستأنف أسوان", reportCaseDescriptor(cover("مدني مستأنف", "أسوان")))
        assertEquals("شؤون الأسرة نصر النوبة", reportCaseDescriptor(cover("شؤون الأسرة", "نصر النوبة")))
    }

    @Test
    fun `descriptor does not duplicate type already contained in court field`() {
        assertEquals("جنح كوم أمبو", reportCaseDescriptor(cover("جنح", "محكمة جنح كوم أمبو")))
        assertEquals("مدني كلي كوم أمبو", reportCaseDescriptor(cover("مدني كلي", "محكمة مدني كلي كوم أمبو")))
    }

    @Test
    fun `final result heading repeats the same full case identity`() {
        val civil = cover("مدني كلي", "كوم أمبو", caseNo = "272", caseYear = "2025")
        assertEquals(
            "الدعوى رقم 272 لسنة 2025 مدني كلي كوم أمبو",
            reportCaseIdentity(civil)
        )
        assertEquals(
            "النتيجة النهائية في الدعوى رقم 272 لسنة 2025 مدني كلي كوم أمبو",
            reportFinalResultHeading(civil)
        )

        val criminal = cover("جنح", "مركز دراو", caseNo = "1334", caseYear = "2014")
        assertEquals(
            "النتيجة النهائية في الدعوى رقم 1334 لسنة 2014 جنح مركز دراو",
            reportFinalResultHeading(criminal)
        )
    }

    @Test
    fun `estate stays separate from guardianship`() {
        val estate = reportCoverWording(cover("تركات", "محكمة الأسرة"))
        assertEquals(ReportCoverKind.ESTATE, estate.kind)
        assertEquals("تركة المرحوم", estate.firstPartyLabel)
        assertEquals("ضـــــد", estate.secondPartyLabel)

        val guardianship = reportCoverWording(cover("وصاية", "نيابة شئون الأسرة"))
        assertEquals(ReportCoverKind.GUARDIANSHIP, guardianship.kind)
        assertEquals("تركة المرحوم", guardianship.firstPartyLabel)
        assertEquals("بوصاية", guardianship.secondPartyLabel)
    }


    @Test
    fun `selected misdemeanor template forces criminal cover for independent report`() {
        val wording = reportCoverWording(
            cover(caseType = "", court = "مركز كوم أمبو"),
            templateId = "drive_misdemeanor",
            templateName = "جنح — من تقارير الخبير"
        )
        assertEquals(ReportCoverKind.CRIMINAL, wording.kind)
        assertTrue(wording.criminal)
        assertTrue(wording.compactCover)
    }

    @Test
    fun `selected appeal templates keep civil full cover`() {
        val appeal = reportCoverWording(
            cover(caseType = "", court = "استئناف أسوان"),
            templateId = "drive_civil_appeal",
            templateName = "مدني مستأنف — من تقارير الخبير"
        )
        val high = reportCoverWording(
            cover(caseType = "", court = "استئناف أسوان"),
            templateId = "drive_high_appeal",
            templateName = "استئناف عالٍ — من تقارير الخبير"
        )
        assertEquals(ReportCoverKind.CIVIL, appeal.kind)
        assertEquals(ReportCoverKind.CIVIL, high.kind)
        assertTrue(!appeal.compactCover)
        assertTrue(!high.compactCover)
    }

    @Test
    fun `other prosecution is compact but does not inherit estate wording`() {
        val wording = reportCoverWording(cover("مأمورية نيابة", "نيابة أسوان"))
        assertEquals(ReportCoverKind.PROSECUTION, wording.kind)
        assertEquals("المرفوعــة من", wording.firstPartyLabel)
        assertEquals("ضـــــد", wording.secondPartyLabel)
        assertTrue(wording.compactCover)
    }
}

