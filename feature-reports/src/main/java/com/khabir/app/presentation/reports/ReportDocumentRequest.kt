package com.khabir.app.presentation.reports

import java.io.File

enum class ReportReviewDestination {
    SUBJECT, ASSIGNMENT, DOCUMENTS, CONCLUSION
}

internal fun defaultReviewDestination(task: ReportDocumentTask): ReportReviewDestination = when (task) {
    ReportDocumentTask.SUBJECT -> ReportReviewDestination.SUBJECT
    ReportDocumentTask.ASSIGNMENT -> ReportReviewDestination.ASSIGNMENT
    ReportDocumentTask.CONCLUSION -> ReportReviewDestination.CONCLUSION
    ReportDocumentTask.RESEARCH,
    ReportDocumentTask.SUMMARY,
    ReportDocumentTask.CUSTOM -> ReportReviewDestination.DOCUMENTS
}

enum class ReportDocumentTask(val defaultInstruction: String) {
    SUMMARY("لخص هذا المستند مع الحفاظ على الأسماء والأرقام والتواريخ والوقائع المهمة دون إضافة معلومات"),
    SUBJECT("استخرج موضوع الدعوى وصغه كموضوع تقرير واحد متكامل، مع إدماج الطلبات الختامية داخل الموضوع دون إنشاء بند مستقل لها"),
    ASSIGNMENT("استخرج مأمورية الحكم التمهيدي فقط مع تاريخ الحكم ورقم الدعوى إن وجدا، دون نسخ الحكم كاملًا"),
    RESEARCH("افحص المستند لبحث المستندات وفق نوعه وأعد الحقول في [[DOCUMENT_EXAM]] مع مصادرها للتحقق والمراجعة"),
    CONCLUSION("استخرج النتيجة النهائية الواردة في المستند فقط، واذكر بوضوح إذا لم توجد نتيجة صريحة"),
    CUSTOM("")
}

data class ReportDocumentRequest(
    val pages: List<File>,
    val instruction: String,
    val task: ReportDocumentTask = ReportDocumentTask.SUMMARY,
    val destinationField: String? = null,
    val customSectionId: String? = null
)

internal fun groupReportPages(
    pages: List<File>,
    continuesPrevious: List<Boolean>,
    instructions: List<String>,
    tasks: List<ReportDocumentTask> = List(pages.size) { ReportDocumentTask.SUMMARY }
): List<ReportDocumentRequest> {
    require(
        pages.size in 1..10 &&
            continuesPrevious.size == pages.size &&
            instructions.size == pages.size &&
            tasks.size == pages.size
    )
    val groups = mutableListOf<ReportDocumentRequest>()
    pages.forEachIndexed { index, file ->
        if (index > 0 && continuesPrevious[index]) {
            groups[groups.lastIndex] = groups.last().copy(pages = groups.last().pages + file)
        } else {
            require(instructions[index].isNotBlank() && instructions[index].length <= 2000)
            groups += ReportDocumentRequest(
                pages = listOf(file),
                instruction = instructions[index].trim(),
                task = tasks[index]
            )
        }
    }
    return groups
}

/** A group may contain non-adjacent camera pages; each page is analyzed exactly once. */
internal fun groupReportPagesBySelection(
    pages: List<File>, groupIds: List<Int>, instructions: List<String>,
    tasks: List<ReportDocumentTask>, destinations: List<String>
): List<ReportDocumentRequest> {
    require(pages.size in 1..10 && listOf(groupIds, instructions, tasks, destinations).all { it.size == pages.size })
    require(groupIds.all { it in pages.indices })
    return groupIds.distinct().map { groupId ->
        val first = groupIds.indexOf(groupId)
        require(instructions[first].isNotBlank() && instructions[first].length <= 2000)
        ReportDocumentRequest(
            pages = pages.filterIndexed { index, _ -> groupIds[index] == groupId },
            instruction = instructions[first].trim(), task = tasks[first],
            destinationField = destinations[first]
        )
    }
}


internal data class ReportPageGroupingState(
    val groupIds: List<Int>,
    val instructions: List<String>,
    val tasks: List<ReportDocumentTask>,
    val destinations: List<String>
)

internal fun reassignReportPageGroup(
    state: ReportPageGroupingState,
    pageIndex: Int,
    newGroupId: Int
): ReportPageGroupingState {
    require(pageIndex in state.groupIds.indices)
    require(newGroupId in state.groupIds.indices)
    require(
        state.instructions.size == state.groupIds.size &&
            state.tasks.size == state.groupIds.size &&
            state.destinations.size == state.groupIds.size
    )

    val oldGroupId = state.groupIds[pageIndex]
    if (oldGroupId == newGroupId) return state

    val oldLeader = state.groupIds.indexOf(oldGroupId)
    val targetLeader = state.groupIds.indexOf(newGroupId)
    val nextGroupIds = state.groupIds.toMutableList().also { it[pageIndex] = newGroupId }
    val nextInstructions = state.instructions.toMutableList()
    val nextTasks = state.tasks.toMutableList()
    val nextDestinations = state.destinations.toMutableList()

    // If the page being moved was the old group's leader, keep that group's
    // settings with the first remaining page so they are not silently lost.
    if (oldLeader == pageIndex) {
        val replacementLeader = nextGroupIds.indexOf(oldGroupId)
        if (replacementLeader >= 0) {
            nextInstructions[replacementLeader] = state.instructions[pageIndex]
            nextTasks[replacementLeader] = state.tasks[pageIndex]
            nextDestinations[replacementLeader] = state.destinations[pageIndex]
        }
    }

    // Joining an existing group must inherit that group's leader settings.
    // This is essential when an earlier page becomes the new visual leader.
    if (targetLeader >= 0) {
        nextInstructions[pageIndex] = state.instructions[targetLeader]
        nextTasks[pageIndex] = state.tasks[targetLeader]
        nextDestinations[pageIndex] = state.destinations[targetLeader]
    }

    return ReportPageGroupingState(
        groupIds = nextGroupIds,
        instructions = nextInstructions,
        tasks = nextTasks,
        destinations = nextDestinations
    )
}
