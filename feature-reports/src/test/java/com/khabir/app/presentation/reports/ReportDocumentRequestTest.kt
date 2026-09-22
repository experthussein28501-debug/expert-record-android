package com.khabir.app.presentation.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReportDocumentRequestTest {
    @Test
    fun independentInstructionsStayWithTheirDocuments() {
        val pages = (1..3).map { File("page-$it") }
        val groups = groupReportPages(
            pages,
            listOf(false, false, false),
            listOf("ملخص", "نتيجة", "تواريخ"),
            listOf(ReportDocumentTask.SUMMARY, ReportDocumentTask.CONCLUSION, ReportDocumentTask.CUSTOM)
        )
        assertEquals(listOf("ملخص", "نتيجة", "تواريخ"), groups.map { it.instruction })
        assertEquals(
            listOf(ReportDocumentTask.SUMMARY, ReportDocumentTask.CONCLUSION, ReportDocumentTask.CUSTOM),
            groups.map { it.task }
        )
        assertEquals(pages, groups.flatMap { it.pages })
    }

    @Test
    fun continuationUsesFirstPagesInstructionAndTask() {
        val pages = (1..3).map { File("page-$it") }
        val groups = groupReportPages(
            pages,
            listOf(false, true, false),
            listOf(ReportDocumentTask.SUBJECT.defaultInstruction, "", ReportDocumentTask.ASSIGNMENT.defaultInstruction),
            listOf(ReportDocumentTask.SUBJECT, ReportDocumentTask.CUSTOM, ReportDocumentTask.ASSIGNMENT)
        )
        assertEquals(2, groups.size)
        assertEquals(pages.take(2), groups.first().pages)
        assertEquals(ReportDocumentTask.SUBJECT, groups.first().task)
        assertEquals(ReportDocumentTask.SUBJECT.defaultInstruction, groups.first().instruction)
        assertEquals(ReportDocumentTask.ASSIGNMENT, groups.last().task)
    }

    @Test
    fun subjectTaskExplicitlyRequestsOneIntegratedSubjectWithoutSeparateFinalRequestsSection() {
        val instruction = ReportDocumentTask.SUBJECT.defaultInstruction
        org.junit.Assert.assertTrue(instruction.contains("موضوع"))
        org.junit.Assert.assertTrue(instruction.contains("إدماج الطلبات الختامية"))
        org.junit.Assert.assertTrue(instruction.contains("دون إنشاء بند مستقل"))
    }

    @Test
    fun allBuiltInTasksHaveReviewableInstructions() {
        ReportDocumentTask.entries
            .filter { it != ReportDocumentTask.CUSTOM }
            .forEach { task ->
                org.junit.Assert.assertTrue(task.name + " must have a prompt", task.defaultInstruction.isNotBlank())
                org.junit.Assert.assertTrue(task.defaultInstruction.length <= 2000)
            }
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyInstructionCannotStartAnalysis() {
        groupReportPages(listOf(File("1")), listOf(false), listOf(" "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun moreThanTenPagesAreRejectedByGroupingLayer() {
        val pages = (1..11).map { File("page-$it") }
        groupReportPages(
            pages,
            List(11) { false },
            List(11) { ReportDocumentTask.SUMMARY.defaultInstruction },
            List(11) { ReportDocumentTask.SUMMARY }
        )
    }
}
