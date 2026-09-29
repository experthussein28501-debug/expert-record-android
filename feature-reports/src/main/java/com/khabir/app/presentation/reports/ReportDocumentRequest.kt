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
    RESEARCH("استخرج بيانات هذا المستند ورتبها لتضاف إلى بحث المستندات مع الحفاظ على الأسماء والأرقام والتواريخ"),
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
