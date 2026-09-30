package com.khabir.app.presentation.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ReportDocumentRequestTest {
    @Test
    fun nonAdjacentPagesCanShareOneDestinationWithoutStealingOtherPages() {
        val pages = (1..4).map { File("page-$it") }
        val grouped = groupReportPagesBySelection(
            pages, listOf(0, 1, 0, 2),
            listOf("استخرج الموضوع", "استخرج المأمورية", "", "حلل مستند المدعي"),
            listOf(ReportDocumentTask.SUBJECT, ReportDocumentTask.ASSIGNMENT, ReportDocumentTask.CUSTOM, ReportDocumentTask.CUSTOM),
            listOf("SUBJECT", "ASSIGNMENT", "DOCUMENTS", "DOCUMENTS")
        )
        assertEquals(3, grouped.size)
        assertEquals(listOf(pages[0], pages[2]), grouped[0].pages)
        assertEquals("SUBJECT", grouped[0].destinationField)
        assertEquals(listOf(pages[1]), grouped[1].pages)
        assertEquals("ASSIGNMENT", grouped[1].destinationField)
        assertEquals(listOf(pages[3]), grouped[2].pages)
        assertEquals(pages.toSet(), grouped.flatMap { it.pages }.toSet())
    }
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

    @Test
    fun `each report image task keeps its own default review destination`() {
        assertEquals(ReportReviewDestination.SUBJECT, defaultReviewDestination(ReportDocumentTask.SUBJECT))
        assertEquals(ReportReviewDestination.ASSIGNMENT, defaultReviewDestination(ReportDocumentTask.ASSIGNMENT))
        assertEquals(ReportReviewDestination.DOCUMENTS, defaultReviewDestination(ReportDocumentTask.RESEARCH))
        assertEquals(ReportReviewDestination.DOCUMENTS, defaultReviewDestination(ReportDocumentTask.SUMMARY))
        assertEquals(ReportReviewDestination.DOCUMENTS, defaultReviewDestination(ReportDocumentTask.CUSTOM))
        assertEquals(ReportReviewDestination.CONCLUSION, defaultReviewDestination(ReportDocumentTask.CONCLUSION))
    }


    @Test
    fun earlierPageJoiningExistingGroupKeepsExistingGroupConfiguration() {
        val state = ReportPageGroupingState(
            groupIds = listOf(0, 1, 2),
            instructions = listOf("ملخص", "استخرج المأمورية", "نتيجة"),
            tasks = listOf(ReportDocumentTask.SUMMARY, ReportDocumentTask.ASSIGNMENT, ReportDocumentTask.CONCLUSION),
            destinations = listOf("DOCUMENTS", "ASSIGNMENT", "CONCLUSION")
        )

        val moved = reassignReportPageGroup(state, pageIndex = 0, newGroupId = 1)

        assertEquals(listOf(1, 1, 2), moved.groupIds)
        assertEquals("استخرج المأمورية", moved.instructions[0])
        assertEquals(ReportDocumentTask.ASSIGNMENT, moved.tasks[0])
        assertEquals("ASSIGNMENT", moved.destinations[0])
    }

    @Test
    fun movingGroupLeaderPreservesOldGroupConfigurationOnReplacementLeader() {
        val state = ReportPageGroupingState(
            groupIds = listOf(0, 0, 2),
            instructions = listOf("موضوع", "", "نتيجة"),
            tasks = listOf(ReportDocumentTask.SUBJECT, ReportDocumentTask.CUSTOM, ReportDocumentTask.CONCLUSION),
            destinations = listOf("SUBJECT", "DOCUMENTS", "CONCLUSION")
        )

        val moved = reassignReportPageGroup(state, pageIndex = 0, newGroupId = 2)

        assertEquals(listOf(2, 0, 2), moved.groupIds)
        assertEquals("موضوع", moved.instructions[1])
        assertEquals(ReportDocumentTask.SUBJECT, moved.tasks[1])
        assertEquals("SUBJECT", moved.destinations[1])
        assertEquals("نتيجة", moved.instructions[0])
        assertEquals(ReportDocumentTask.CONCLUSION, moved.tasks[0])
        assertEquals("CONCLUSION", moved.destinations[0])
    }

}
