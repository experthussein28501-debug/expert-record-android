package com.khabir.app.presentation.reports

import java.io.File

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
    val task: ReportDocumentTask = ReportDocumentTask.SUMMARY
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
