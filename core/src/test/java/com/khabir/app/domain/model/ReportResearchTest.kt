package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class ReportResearchTest {
    private val report = Report(caseId=5,caseNo="105",caseYear="2026",assignment="١- بيان ملكية الأرض.\nأ- بيان المساحة.\n٢- إجراء معاينة.",
        documentsSubmitted="عقد بيع يتضمن المساحة والحدود.",inspection="أجرينا المعاينة وثبتت الحدود.",partyStatements="قال المدعي إنه مالك.")
    private fun context(r:Report=report)=ReportResearch.parseContext(ReportResearch.context(r))
    private fun reply(c:ReportResearch.Context=context(),status:String="SUPPORTED",support:String=c.evidence.filter { it.kind != "قول خصم" }.joinToString(",") { it.id },
        opposing:String="",missing:String="",conflicts:String="") = c.tasks.joinToString("\n") { task -> """
        [[ANSWER ${task.id}]]
        الحالة: $status
        أدلة مؤيدة: $support
        أدلة معارضة: $opposing
        الرد: بيان مقترح من الدليل.
        التعارضات: $conflicts
        النواقص: $missing
        [[END ANSWER]]
    """.trimIndent() }
    @Test fun tasksPreserveSubItemsAndOriginalAssignment() {
        val tasks=ReportResearch.tasks(report.assignment)
        assertEquals(3,tasks.size)
        assertEquals(report.assignment,tasks.joinToString("\n") { it.text })
        assertEquals(tasks,ReportResearch.tasks(report.assignment))
    }
    @Test fun unchangedTaskKeepsIdWhenAnotherTaskChanges() {
        val first=ReportResearch.tasks(report.assignment).first()
        assertEquals(first,ReportResearch.tasks(report.assignment.replace("إجراء معاينة","فحص المستندات")).first())
    }
    @Test fun approvedContextRoundTripRetainsEveryTaskAndEvidenceType() {
        val c=context()
        assertEquals(ReportResearch.tasks(report.assignment),c.tasks)
        assertEquals(3,c.evidence.size)
        assertTrue(c.evidence.any { it.kind=="قول خصم" })
    }
    @Test fun validAnswersIncludeAllTasksAndRenderStatuses() {
        val c=context();val reviewed=ReportResearch.review(reply(),c)
        assertTrue(reviewed.errors.toString(),reviewed.valid)
        assertEquals(3,reviewed.answers.size)
        assertTrue(reviewed.render(c.tasks).contains("رد مسند"))
    }
    @Test fun omittedTaskIsRejected() {
        val c=context();val raw=reply(c.copy(tasks=c.tasks.dropLast(1)))
        assertFalse(ReportResearch.review(raw,c).valid)
    }
    @Test fun duplicatedTaskIsRejected() {
        val c=context();val raw=reply()+"\n"+reply(c.copy(tasks=listOf(c.tasks.first())))
        assertFalse(ReportResearch.review(raw,c).valid)
    }
    @Test fun fabricatedEvidenceReferenceIsRejected() { assertFalse(ReportResearch.review(reply(support="ev_fake"),context()).valid) }
    @Test fun statementAloneCannotBeReportedAsProvenFact() {
        val c=context();assertFalse(ReportResearch.review(reply(support=c.evidence.first { it.kind=="قول خصم" }.id),c).valid)
    }
    @Test fun unknownStatusIsRejected() { assertFalse(ReportResearch.review(reply(status="COMPLETE"),context()).valid) }
    @Test fun insufficientEvidenceMustNameMissingMaterial() {
        assertFalse(ReportResearch.review(reply(status="INSUFFICIENT",support=""),context()).valid)
        assertTrue(ReportResearch.review(reply(status="INSUFFICIENT",support="",missing="يلزم أصل العقد وإجراء المعاينة"),context()).valid)
    }
    @Test fun unperformedActionIsAnExplicitIncompleteStatus() {
        val r=report.copy(inspection="");val c=context(r)
        val review=ReportResearch.review(reply(c,status="UNPERFORMED",support="",missing="إجراء المعاينة لم يثبت"),c)
        assertTrue(review.valid)
        assertTrue(review.render(c.tasks).contains("إجراء لم يثبت تنفيذه"))
    }
    @Test fun conflictingEvidenceRequiresBothSidesAndDescription() {
        val c=context()
        assertFalse(ReportResearch.review(reply(status="CONFLICTING"),c).valid)
        assertTrue(ReportResearch.review(reply(status="CONFLICTING",opposing=c.evidence.last().id,conflicts="قول الخصم يخالف العقد"),c).valid)
    }
    @Test fun blankAssignmentCannotGenerateCompletedResearch() {
        val c=context(report.copy(assignment=""));assertFalse(ReportResearch.review("",c).valid)
    }
    @Test fun approvalAppendsWithoutReplacingExpertsPreviousResearchAndKeepsFormatting() {
        val r=report.copy(research="بحث الخبير السابق.",customSectionContentsSpec=ReportCustomSectionCodec.encode(mapOf(ReportTextFormat.KEY to ReportTextFormat(font="Traditional Arabic",size=18).encode())))
        val fingerprint=ReportResearch.fingerprint(r)
        val accepted=ReportResearch.appendApproved(r,"بحث مقترح معتمد.",fingerprint)
        assertEquals("بحث الخبير السابق.\n\nبحث مقترح معتمد.",accepted.research)
        assertEquals(ReportTextFormat.decode(ReportCustomSectionCodec.decode(r.customSectionContentsSpec)[ReportTextFormat.KEY]),
            ReportTextFormat.decode(ReportCustomSectionCodec.decode(accepted.customSectionContentsSpec)[ReportTextFormat.KEY]))
        assertFalse(ReportResearch.needsReview(accepted))
    }
    @Test fun approvingSameProposalTwiceDoesNotDuplicate() {
        val first=ReportResearch.appendApproved(report,"نص البحث",ReportResearch.fingerprint(report))
        assertEquals(first,ReportResearch.appendApproved(first,"نص البحث",ReportResearch.fingerprint(first)))
    }
    @Test fun changingEvidenceOrAssignmentAfterApprovalMakesResearchStale() {
        val accepted=ReportResearch.appendApproved(report,"بحث",ReportResearch.fingerprint(report))
        assertTrue(ReportResearch.needsReview(accepted.copy(assignment="مأمورية جديدة")))
        assertTrue(ReportResearch.needsReview(accepted.copy(inspection="معاينة معدلة")))
        assertFalse(ReportResearch.needsReview(accepted.copy(research="تحرير الخبير للبحث")))
    }
    @Test fun cannotApproveAProposalAfterSourceChanged() {
        try { ReportResearch.appendApproved(report.copy(documentsSubmitted="دليل جديد"),"بحث",ReportResearch.fingerprint(report));fail() }
        catch(expected:IllegalArgumentException) { assertTrue(expected.message!!.contains("تغيرت")) }
    }
    @Test fun persistenceReopenRetainsReviewSignatureAndText() {
        val accepted=ReportResearch.appendApproved(report,"بحث",ReportResearch.fingerprint(report))
        val decoded=ReportCustomSectionCodec.decode(accepted.customSectionContentsSpec)
        assertTrue(decoded.containsKey(ReportResearch.CONTEXT_KEY))
        assertFalse(ReportResearch.needsReview(accepted.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(decoded))))
    }

    @Test fun researchCommandsDoNotCaptureOrdinaryDocumentEditing() {
        assertTrue(ReportResearch.isResearchCommand("اعمل بحث"))
        assertTrue(ReportResearch.isResearchCommand("اعمل فحص"))
        assertFalse(ReportResearch.isResearchCommand("رتب بحث المستندات"))
    }
    @Test fun unsupportedInspectionCannotBeMarkedAsPerformed() {
        val r=report.copy(inspection="لم تتم المعاينة حتى الآن.")
        val c=context(r)
        assertFalse(ReportResearch.review(reply(c),c).valid)
    }
}
