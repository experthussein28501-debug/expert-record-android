package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.nio.file.Files

class LegalMemoryAndSourcesTest {
    @Test fun editingVisibleNotesPreservesSourceAudit() {
        val audit = "${LegalSourceAudit.LABEL}: encoded-source"
        val notes = "ملاحظة سابقة\n$audit"
        assertEquals("ملاحظة سابقة", LegalSourceAudit.userNotes(notes))
        assertEquals("ملاحظة معدلة\n$audit", LegalSourceAudit.withUserNotes(notes, "ملاحظة معدلة"))
        assertEquals(audit, LegalSourceAudit.withUserNotes(notes, ""))
    }
    @Test fun styleRejectsNamesAddressesAndNumbersFromCase() {
        assertTrue(ApprovedStyleRules.validate("ابدأ بذكر أحمد علي",listOf("أحمد علي")).isNotEmpty())
        assertTrue(ApprovedStyleRules.validate("اذكر القطعة ١٩ والمساحة ٢٠",emptyList()).isNotEmpty())
        assertTrue(ApprovedStyleRules.validate("احفظ عنوان قرية أرمنا",listOf("قرية أرمنا")).isNotEmpty())
    }
    @Test fun genericApprovedStyleContainsNoPreviousCaseDataAndKeepsDate() {
        val text="استخدم عناوين واضحة وفقرات قصيرة. ميز الأقوال عن الوقائع."
        assertTrue(ApprovedStyleRules.validate(text,listOf("أحمد علي","أسوان")).isEmpty())
        val encoded=ApprovedStyleRules.encode(listOf(ApprovedStyleRules.Revision(1720000000L,text)))
        assertEquals(listOf(ApprovedStyleRules.Revision(1720000000L,text)),ApprovedStyleRules.decode(encoded))
        assertFalse(encoded.contains("أحمد"))
    }
    @Test fun styleHistoryIsBoundedAndCorruptRevisionIsIgnored() {
        val revisions=(1..30).map { ApprovedStyleRules.Revision(it.toLong(),"أسلوب عام") }
        val restored=ApprovedStyleRules.decode(ApprovedStyleRules.encode(revisions)+"\nmalformed")
        assertEquals(20,restored.size);assertEquals(30L,restored.last().approvedAt)
    }
    @Test fun cacheInvalidatesForPageInstructionsRuleVersionCredentialAndOrder() {
        val page=byteArrayOf(1,2,3);val other=byteArrayOf(2,3,4)
        val key=ExtractionResultCache.key(listOf(page,other),"PETITION","rules","scope")
        assertNotEquals(key,ExtractionResultCache.key(listOf(other,page),"PETITION","rules","scope"))
        assertNotEquals(key,ExtractionResultCache.key(listOf(page,other),"PETITION","changed","scope"))
        assertNotEquals(key,ExtractionResultCache.key(listOf(page,other),"PETITION","rules","other-account"))
        assertNotEquals(key,ExtractionResultCache.key(listOf(page,other),"PETITION","rules","scope","new-rules"))
    }
    @Test fun cacheKeepsSuccessAndEvictsOldEntriesWithoutSavingKeys() {
        val cache=ExtractionResultCache(2);cache.put("a","result A");cache.put("b","result B");cache.get("a");cache.put("c","result C")
        assertNull(cache.get("b"));assertEquals("result A",cache.get("a"));cache.put("failed","");assertNull(cache.get("failed"))
        cache.clear();assertNull(cache.get("a"))
    }
    @Test fun caseSourceAuditKeepsOriginalTextWithinOwnCase() {
        val r=LegalDocumentRecord(9,listOf(3,1),LegalProcedureType.ORIGINAL,procedureDate=LocalDate.of(2026,1,1),rawText="نص المصدر الأصلي ١٩ قيراطًا")
        val decoded=LegalSourceAudit.decode(LegalSourceAudit.encode(listOf(r)))
        assertTrue(decoded.contains("٣") || decoded.contains("3, 1"))
        assertTrue(decoded.contains(r.rawText));assertTrue(decoded.contains("2026-01-01"))
    }
    @Test fun sourceArchiveSurvivesTemporaryPageDeletionAndIsIsolatedByCase() {
        val dir=Files.createTempDirectory("case-sources-test").toFile()
        try {
            val page=java.io.File(dir,"temporary.jpg").apply { writeBytes(byteArrayOf(1,2,3)) }
            val archive=CaseSourceArchive(java.io.File(dir,"archive"))
            val r=LegalDocumentRecord(1,listOf(1),LegalProcedureType.ORIGINAL,rawText="مصدر القضية")
            archive.archive(5,listOf(r),listOf(page));page.delete()
            assertEquals(1,archive.pages(5).size);assertEquals(3,archive.pages(5).single().length().toInt())
            assertTrue(archive.text(5).contains("مصدر القضية"));assertTrue(archive.pages(6).isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun criminalAndEstateSummariesUseFirstAndOthersWithoutDeletingPartyRecords() {
        fun p(n:String,r:PartyRole,i:Int)=Party(firstName=n,restName="",role=r,address="",orderIndex=i)
        val parties=listOf(p("أحمد علي",PartyRole.DEFENDANT,0),p("حسن محمود",PartyRole.DEFENDANT,1))
        val case=Case(incomingNo="1",incomingDate=LocalDate.of(2026,1,1),caseNo="105",caseYear="2026",court="أسوان",caseType="جنح",parties=parties)
        assertEquals("النيابة العامة ضد أحمد علي وآخرين",PartySummaries.report(case))
        val cover=ReportCoverFields.from(case,ExpertProfile());assertEquals("النيابة العامة",cover.plaintiffsSummary)
        assertEquals("أحمد علي وآخرين",cover.defendantsSummary)
        assertEquals(2,case.parties.size)
        val estate=case.copy(caseType="تركات",parties=parties.map { it.copy(role=PartyRole.PLAINTIFF) })
        assertEquals("أحمد علي وآخرين",ReportCoverFields.from(estate,ExpertProfile()).plaintiffsSummary)
    }
}
