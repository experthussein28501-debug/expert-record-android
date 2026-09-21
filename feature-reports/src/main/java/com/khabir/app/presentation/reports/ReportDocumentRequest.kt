package com.khabir.app.presentation.reports

import java.io.File

data class ReportDocumentRequest(val pages: List<File>, val instruction: String)

internal fun groupReportPages(pages: List<File>, continuesPrevious: List<Boolean>, instructions: List<String>): List<ReportDocumentRequest> {
    require(pages.size in 1..10 && continuesPrevious.size == pages.size && instructions.size == pages.size)
    val groups = mutableListOf<ReportDocumentRequest>()
    pages.forEachIndexed { index, file ->
        if (index > 0 && continuesPrevious[index]) {
            groups[groups.lastIndex] = groups.last().copy(pages = groups.last().pages + file)
        } else {
            require(instructions[index].isNotBlank() && instructions[index].length <= 2000)
            groups += ReportDocumentRequest(listOf(file), instructions[index].trim())
        }
    }
    return groups
}
