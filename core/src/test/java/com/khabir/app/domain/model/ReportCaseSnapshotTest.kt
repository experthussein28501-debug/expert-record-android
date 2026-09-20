package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReportCaseSnapshotTest {
    private val original = Case(incomingNo = "1", incomingDate = LocalDate.of(2026, 9, 20), caseNo = "10", caseYear = "2026", court = "أسوان", caseType = "مدني",
        parties = listOf(Party(firstName = "مدع", restName = "أول", role = PartyRole.PLAINTIFF, address = "", orderIndex = 0),
            Party(firstName = "محام", restName = "أول", role = PartyRole.LAWYER, address = LawyerNotification.address("إدفو"), orderIndex = 1)))
    @Test fun caseEditsDoNotSilentlyChangeAnExistingReportCover() {
        val report = Report(caseNo = "10", caseYear = "2026", court = "مدني أسوان", partiesSummary = "مدع أول",
            customSectionContentsSpec = ReportCustomSectionCodec.encode(ReportCaseSnapshot.parties(original)))
        val changed = original.copy(caseNo = "99", parties = original.parties.map { it.copy(firstName = "اسم جديد") })
        val cover = ReportCaseSnapshot.cover(report, changed, ExpertProfile())
        assertEquals("10", cover.caseNo)
        assertEquals("مدع أول", cover.plaintiffsSummary)
        assertFalse(cover.partiesSummary.contains("محام"))
    }
    @Test fun partyDataRefreshDoesNotTreatLawyerAsLitigant() {
        assertEquals("مدع أول", CaseDocumentFields.report(original)["الخصوم"])
        assertEquals("مدع أول", CaseDocumentFields.minutes(original)["المدعون"])
    }
}
