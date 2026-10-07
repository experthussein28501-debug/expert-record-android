package com.khabir.app.domain.model

import com.khabir.app.presentation.cases.DocumentExaminationLocalParser
import org.junit.Assert.*
import org.junit.Test

class ReportAiWorkflowTest {
    private val report=Report(caseId=9,caseNo="١٠",caseYear="٢٠٢٤",court="أسوان",assignment="١- بيان المالك وسنده.\n٢- بيان وضع اليد ومدته.",documentsSubmitted="عقد مشهر رقم ٣٠ لسنة ٢٠٢٠، القطعة ١٩.",inspection="وجدت العين يشغلها حسن.",witnessStatements="قال الشاهد أحمد إن حسن حائز من ٢٠١٠.",research="بحث يدوي للخبير.",conclusion="نتيجة يدوية محفوظة.")
    private fun reviewed(r:Report=report):ReportResearch.Review {
        val c=ReportResearch.parseContext(ReportResearch.context(r));val doc=c.evidence.first { it.kind=="مستند" }.id;val witness=c.evidence.first { it.kind=="قول شاهد" }.id
        val raw=c.tasks.mapIndexed { i,t -> """
            [[ANSWER ${t.id}]]
            الحالة: ${if(i==0) "INSUFFICIENT" else "REPORTED"}
            أدلة مؤيدة: ${if(i==0) doc else witness}
            أدلة معارضة:
            الرد: ${if(i==0) "لم تثبت مطابقة كامل العين للمشهر رقم ٣٠؛ يلزم سند المطابقة." else "بحسب أقوال الشاهد أحمد، حسن حائز من ٢٠١٠؛ لم تتأكد المدة كواقعة مستقلة."}
            التعارضات:
            النواقص: ${if(i==0) "مطابقة القطعة والحدود والموقع" else ""}
            [[END ANSWER]]
        """.trimIndent() }.joinToString("\n")
        return ReportResearch.review(raw,c).also { assertTrue(it.errors.toString(),it.valid) }
    }
    private fun approved():Report {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val review=reviewed()
        val proposal=ReportAiProposal(ReportAiKind.RESEARCH,review.reportText(c.tasks),review.render(c.tasks,c.evidence),ReportAiWorkflow.fingerprint(report,ReportAiKind.RESEARCH),review.answers.associate { it.taskId to it.status.name })
        return ReportAiWorkflow.apply(report,proposal,proposal.text,true)
    }
    @Test fun correctionRetainsOriginalProposalForLearningComparison() {
        val original=ReportAiProposal(ReportAiKind.RESEARCH,"النص المقترح","الدليل","hash")
        val corrected=original.copy(text="صياغة الخبير")
        assertEquals("النص المقترح",corrected.originalText)
        assertEquals("صياغة الخبير",corrected.text)
    }
    @Test fun heirCertificateHasCompleteDetailsAndDeathDateSeparateFromIssue() {
        val raw="نوع المستند: إعلام وراثة\nصفة النسخة: صورة ضوئية\nتاريخ المستند: 01/01/2024\nالمورث: علي حسين\nتاريخ الوفاة: 15/12/2023\nتفاصيل الورثة: زوجته منى: الثمن\nابنه أحمد وابنته فاطمة: الباقي للذكر مثل حظ الأنثيين، لا وارث غيرهم"
        val d=DocumentExamination.parse(raw);assertEquals(ExamDocumentKind.HEIR_CERTIFICATE,d.kind)
        assertTrue(d.render().startsWith("صورة ضوئية من إعلام وراثة ورد به وفاة المرحوم علي حسين بتاريخ 15/12/2023"))
        assertTrue(d.render().contains("ابنه أحمد وابنته فاطمة"));assertTrue(d.render().endsWith("لا وارث غيرهم"));assertEquals("2024-01-01",d.date.toString())
        assertEquals(d.copy(approvedText=d.render()),DocumentExamination.decode(DocumentExamination.encode(listOf(d))).single())
    }
    @Test fun localHeirCertificateKeepsAllHeirsWithoutCalculatingShares() {
        val d=DocumentExaminationLocalParser.parse("إعلام وراثة\nوفاة المرحوم علي حسين بتاريخ 15/12/2023\nوانحصر إرثه في زوجته منى وابنه أحمد وابنته فاطمة، ولا وارث سواهم")
        assertTrue(d.render().contains("علي حسين"));assertTrue(d.render().contains("فاطمة"));assertFalse(d.render().contains("الثمن"))
    }
    @Test fun unnumberedMissionIsSplitWithoutChangingAssignment() {
        val text="الاطلاع على الأوراق والانتقال إلى العين وبيان المالك وسنده وتقدير الريع"
        assertEquals(4,ReportResearch.tasks(text).size);assertEquals(text,report.copy(assignment=text).assignment)
    }
    @Test fun contextContainsAllEvidenceKindsAndWitnessTextIsNotChanged() {
        val r=report.copy(partyStatements="قال المدعي كذا",proceedings="انتقلنا في تاريخ ثابت",facts="ملاحظة",calculationsTable=ReportCalculationCodec.encode(listOf(ReportCalculationRow(1,"ريع","3","100","جنيه"))))
        val c=ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.RESEARCH))
        val withNames=r.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(mapOf(DocumentPartyLinks.KEY to DocumentPartyLinks.encode(listOf(DocumentPartyLink("حسن علي حسين","المدعي"),DocumentPartyLink("منى علي حسين","المدعى عليه"))))))
        assertTrue(ReportResearch.context(withNames).contains("حسن علي حسين (المدعي)"))
        assertTrue(c.evidence.map { it.kind }.containsAll(listOf("مستند","معاينة","قول شاهد","قول خصم","إجراء","ملاحظة","حساب")))
        assertTrue(c.evidence.first { it.kind=="حساب" }.text.contains("300.00"));assertEquals(report.witnessStatements,r.witnessStatements)
    }
    @Test fun approvedDocumentRecordsHaveSeparateSourceIdsAndDates() {
        val d=DocumentExamination.parse("نوع المستند: إعلام وراثة\nتاريخ المستند: 1/1/2024\nالمورث: علي\nتفاصيل الورثة: أحمد ومنى")
        val text=DocumentExamination.renderAll(listOf(d),"مدني",ReportListStyle.PLAIN)
        val r=report.copy(documentsSubmitted=text,customSectionContentsSpec=ReportCustomSectionCodec.encode(mapOf(DocumentExamination.KEY to DocumentExamination.encode(listOf(d)),DocumentExamination.SNAPSHOT to legalFingerprint(text))))
        val docs=ReportResearch.evidence(r).filter { it.kind=="مستند" }
        assertEquals(1,docs.size);assertTrue(docs.single().text.contains("تاريخ المستند: 2024-01-01"))
    }
    @Test fun reportedWitnessStatementsAreAttributedAndNeverBecomeAnInspection() {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val witness=c.evidence.first { it.kind=="قول شاهد" }.id;val task=c.tasks.last()
        val raw="[[ANSWER ${task.id}]]\nالحالة: REPORTED\nأدلة مؤيدة: $witness\nالرد: حسن حائز منذ زمن\n[[END ANSWER]]"
        assertFalse(ReportResearch.review(raw,c.copy(tasks=listOf(task))).valid)
        assertFalse(ReportResearch.review(raw.replace("حسن حائز منذ زمن","بحسب الشاهد حسن حائز"),c.copy(tasks=listOf(task.copy(text="إجراء معاينة")))).valid)
    }
    @Test fun reportNarrativeIsConciseButAuditKeepsFullEvidence() {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val review=reviewed()
        assertFalse(review.reportText(c.tasks).contains("الأدلة المؤيدة:"));assertTrue(review.render(c.tasks,c.evidence).contains("الأدلة المؤيدة:"))
        assertTrue(review.reportText(c.tasks).contains("لم تثبت"));assertTrue(review.reportText(c.tasks).contains("بحسب أقوال"))
    }
    @Test fun historicalDisplayHasEvidenceAndDoesNotAddMissionTask() {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val base=reviewed();val id=c.evidence.first().id
        val raw=c.tasks.map { t -> "[[ANSWER ${t.id}]]\nالحالة: INSUFFICIENT\nالرد: لم تثبت المطابقة\nالنواقص: سند المطابقة\n[[END ANSWER]]" }.joinToString("\n")+"\n[[HISTORY]]\nمصادر: $id\nالعرض: سبق صدور حكم بين أطراف غير ممثلين حسب المستند.\n[[END HISTORY]]"
        val review=ReportResearch.review(raw,c);assertTrue(review.errors.toString(),review.valid);assertEquals(c.tasks.size,review.answers.size)
        assertTrue(review.reportText(c.tasks).contains("عرض للأحكام والأحداث السابقة"));assertFalse(ReportResearch.review(raw.replace(id,"ev_fake"),c).valid)
    }
    @Test fun preparationDoesNotChangeManualResearchOrConclusion() {
        ReportAiWorkflow.context(report,ReportAiKind.RESEARCH);ReportAiWorkflow.context(report,ReportAiKind.CONCLUSION)
        assertEquals("بحث يدوي للخبير.",report.research);assertEquals("نتيجة يدوية محفوظة.",report.conclusion)
    }
    @Test fun approvedResearchCanAppendOrExplicitlyReplaceAndPreservesWitnesses() {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val review=reviewed();val p=ReportAiProposal(ReportAiKind.RESEARCH,review.reportText(c.tasks),"audit",ReportAiWorkflow.fingerprint(report,ReportAiKind.RESEARCH))
        assertTrue(ReportAiWorkflow.apply(report,p,p.text,false).research.startsWith(report.research));assertFalse(ReportAiWorkflow.apply(report,p,p.text,true).research.startsWith(report.research))
        assertEquals(report.witnessStatements,ReportAiWorkflow.apply(report,p,p.text,true).witnessStatements)
    }
    @Test(expected=IllegalArgumentException::class) fun staleSourcesRejectResearchApproval() {
        val c=ReportResearch.parseContext(ReportResearch.context(report));val p=ReportAiProposal(ReportAiKind.RESEARCH,reviewed().reportText(c.tasks),"audit",ReportAiWorkflow.fingerprint(report,ReportAiKind.RESEARCH))
        ReportAiWorkflow.apply(report.copy(inspection="تغيرت المعاينة"),p,p.text,false)
    }
    @Test(expected=IllegalArgumentException::class) fun removingAnAnswerInReviewCannotSilentlyApprove() {
        val p=ReportAiProposal(ReportAiKind.RESEARCH,"", "audit",ReportAiWorkflow.fingerprint(report,ReportAiKind.RESEARCH))
        ReportAiWorkflow.apply(report,p,"سطر واحد فقط",true)
    }
    private fun conclusionReply(r:Report=approved(),newNumber:Boolean=false,upgrade:Boolean=false):String {
        val c=ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.CONCLUSION))
        return c.tasks.mapIndexed { i,t ->
            val e=c.evidence.first { it.text.contains("بند المأمورية: ${t.text}") }
            val quote=e.text.substringAfter('\n').lineSequence().first()
            "[[RESULT ${t.id}]]\nالحالة: ${if(upgrade) "SUPPORTED" else if(i==0) "INSUFFICIENT" else "REPORTED"}\nالمصدر: ${e.id}\nالاقتباس: $quote\nالنتيجة: ${if(newNumber) "لم تثبت المطابقة للقطعة 999" else quote}\n[[END RESULT]]"
        }.joinToString("\n")
    }
    @Test fun conclusionOnlyUsesApprovedResearchAndRetainsMissingEvidence() {
        val r=approved();val c=ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.CONCLUSION))
        assertTrue(c.evidence.all { it.kind=="بحث معتمد" });val p=ReportAiWorkflow.reviewConclusion(conclusionReply(r),r,c)
        assertTrue(p.text.contains("لم تثبت"));assertTrue(p.text.contains("بحسب أقوال"))
        val updated=ReportAiWorkflow.apply(r,p,p.text,true);assertEquals(r.research,updated.research);assertFalse(ReportAiWorkflow.conclusionNeedsReview(updated))
        assertTrue(ReportAiWorkflow.conclusionNeedsReview(updated.copy(research=updated.research+"تغير")))
    }
    @Test(expected=IllegalArgumentException::class) fun conclusionCannotUpgradeUncertainResearch() {
        val r=approved();ReportAiWorkflow.reviewConclusion(conclusionReply(r,upgrade=true),r,ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.CONCLUSION)))
    }
    @Test(expected=IllegalArgumentException::class) fun conclusionCannotInventNewNumbers() {
        val r=approved();ReportAiWorkflow.reviewConclusion(conclusionReply(r,newNumber=true),r,ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.CONCLUSION)))
    }
    @Test(expected=IllegalArgumentException::class) fun conclusionCannotUseWrongVerbatimQuote() {
        val r=approved();ReportAiWorkflow.reviewConclusion(conclusionReply(r).replace("الاقتباس:","الاقتباس: نص مختلق "),r,ReportResearch.parseContext(ReportAiWorkflow.context(r,ReportAiKind.CONCLUSION)))
    }
    @Test fun approvedStateAndAuditSurviveReportMetadataCodec() {
        val r=approved();val rebuilt=r.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(ReportCustomSectionCodec.decode(r.customSectionContentsSpec)))
        assertEquals(ReportAiWorkflow.statuses(r),ReportAiWorkflow.statuses(rebuilt));assertEquals(2,ReportAiWorkflow.statuses(rebuilt).size)
        assertTrue(ReportCustomSectionCodec.decode(rebuilt.customSectionContentsSpec)[ReportAiWorkflow.HISTORY_KEY].orEmpty().contains("الأدلة المؤيدة"))
    }
    @Test fun explicitManualReviewResolvesStalenessWithoutGeneratingOrChangingText() {
        val r=approved().copy(inspection="معاينة محدثة")
        assertTrue(ReportResearch.needsReview(r))
        val updated=ReportAiWorkflow.confirmManualResearch(r)
        assertFalse(ReportResearch.needsReview(updated));assertEquals(r.research,updated.research);assertEquals(r.conclusion,updated.conclusion)
    }
    @Test fun propertyRulesCoverPartialPlotOwnershipHeirsPossessionAndRentWithoutShortcut() {
        val s=PropertyResearchRules.text
        listOf("ضمن مساحة","لا يكفي","إعلام الوراثة","اترك التكييف","تقاطع المدة","غير الممثل").forEach {assertTrue(it,s.contains(it))}
    }
}
