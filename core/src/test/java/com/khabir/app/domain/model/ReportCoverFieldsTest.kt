package com.khabir.app.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCoverFieldsTest {

    private fun party(
        name: String,
        role: PartyRole,
        orderIndex: Int,
        withCapacity: Boolean = false
    ) = Party(
        firstName = "",
        restName = name,
        role = role,
        address = "",
        orderIndex = orderIndex,
        withCapacity = withCapacity
    )

    @Test
    fun `capacity is a status on the same plaintiff or defendant`() {
        val plaintiff = party("رئيس مجلس إدارة شركة النيل", PartyRole.PLAINTIFF, 0, withCapacity = true)
        val defendant = party("مدير الشركة", PartyRole.DEFENDANT, 0, withCapacity = true)

        assertTrue(plaintiff.role.isPlaintiff)
        assertTrue(defendant.role.isDefendant)
        assertEquals("رئيس مجلس إدارة شركة النيل بصفته", plaintiff.reportDisplayName)
        assertEquals("مدير الشركة بصفته", defendant.reportDisplayName)
    }

    @Test
    fun `stored role keeps side and capacity together without changing meaning`() {
        assertEquals("مدعي|بصفته", party("أحمد", PartyRole.PLAINTIFF, 0, true).storedRoleValue)
        assertEquals("مدعى عليه|بصفته", party("محمد", PartyRole.DEFENDANT, 0, true).storedRoleValue)
        assertEquals(PartyRole.PLAINTIFF to true, PartyRole.parseStored("مدعي بصفته"))
        assertEquals(PartyRole.DEFENDANT to true, PartyRole.parseStored("مدعى عليه بصفته"))
    }

    @Test
    fun `cover uses only first plaintiff and others and preserves first capacity`() {
        val parties = listOf(
            party("أحمد محمد علي", PartyRole.PLAINTIFF, 0, withCapacity = true),
            party("محمود حسن سالم", PartyRole.PLAINTIFF, 1),
            party("سيد عبد الله", PartyRole.PLAINTIFF, 2, withCapacity = true)
        )

        assertEquals("أحمد محمد علي بصفته وآخرين", ReportCoverFields.coverPartySummary(parties))
    }

    @Test
    fun `capacity of middle or last party never appears on ordinary cover`() {
        val parties = listOf(
            party("أحمد محمد علي", PartyRole.DEFENDANT, 0),
            party("محمود حسن سالم", PartyRole.DEFENDANT, 1, withCapacity = true),
            party("سيد عبد الله", PartyRole.DEFENDANT, 2, withCapacity = true)
        )

        assertEquals("أحمد محمد علي وآخرين", ReportCoverFields.coverPartySummary(parties))
    }

    @Test
    fun `cover shows one name only when there are no others`() {
        assertEquals(
            "أحمد محمد علي",
            ReportCoverFields.coverPartySummary(listOf(party("أحمد محمد علي", PartyRole.PLAINTIFF, 0)))
        )
    }

    @Test
    fun `cover respects party order index`() {
        val parties = listOf(
            party("الاسم الثاني", PartyRole.DEFENDANT, 2),
            party("الاسم الأول", PartyRole.DEFENDANT, 1)
        )

        assertEquals("الاسم الأول وآخرين", ReportCoverFields.coverPartySummary(parties))
    }

    @Test
    fun `cover does not duplicate and others when already written`() {
        val parties = listOf(
            party("محمد علي وآخرين", PartyRole.DEFENDANT, 0),
            party("خصم إضافي", PartyRole.DEFENDANT, 1)
        )

        assertEquals("محمد علي وآخرين", ReportCoverFields.coverPartySummary(parties))
    }

    @Test
    fun `report summary is compact while individual party statuses remain in the case`() {
        val case = Case(
            incomingNo = "15",
            incomingDate = LocalDate.of(2026, 8, 23),
            caseNo = "100",
            caseYear = "2026",
            court = "محكمة كوم أمبو",
            caseType = "مدني كلي",
            parties = listOf(
                party("أحمد", PartyRole.PLAINTIFF, 0),
                party("محمود", PartyRole.PLAINTIFF, 1, true),
                party("محمد", PartyRole.DEFENDANT, 2, true)
            )
        )
        val profile = ExpertProfile(expertName = "خبير", specialization = "هندسي")
        val cover = ReportCoverFields.from(case, profile)

        assertEquals("أحمد وآخرين ضد محمد بصفته", cover.partiesSummary)
        assertTrue(case.parties.any { it.reportDisplayName == "محمود بصفته" })
        assertTrue(cover.partiesSummary.contains("محمد بصفته"))
        assertFalse(cover.plaintiffsSummary.contains("محمود بصفته"))
    }

    @Test
    fun `estate cover keeps all stored names without civil abbreviation`() {
        val parties = listOf(
            party("المرحوم أحمد محمد", PartyRole.PLAINTIFF, 0),
            party("المرحوم محمود حسن", PartyRole.PLAINTIFF, 1)
        )

        assertEquals("المرحوم أحمد محمد، المرحوم محمود حسن", ReportCoverFields.fullPartySummary(parties))
    }

    @Test
    fun `estate and guardianship detection is separate from ordinary civil cases`() {
        assertTrue(ReportCoverFields.isEstateOrGuardianship("تركات", "نيابة شئون الأسرة"))
        assertTrue(ReportCoverFields.isEstateOrGuardianship("وصاية", "محكمة الأسرة"))
        assertTrue(ReportCoverFields.isEstateOrGuardianship("", "نيابة شؤون الأسرة"))
        assertFalse(ReportCoverFields.isEstateOrGuardianship("مدني كلي", "محكمة كوم أمبو"))
        assertFalse(ReportCoverFields.isEstateOrGuardianship("استئناف عالي", "محكمة استئناف قنا"))
    }
}

