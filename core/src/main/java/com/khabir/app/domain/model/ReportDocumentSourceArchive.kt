package com.khabir.app.domain.model

import java.io.File

class ReportDocumentSourceArchive(private val root:File) {
    fun archive(reportId:Long,document:ExaminedDocument,pages:List<File>) {
        require(reportId>0)
        require(document.id.matches(Regex("[a-f0-9]{64}")))
        val dir=File(File(root,reportId.toString()),document.id).apply { check(mkdirs() || isDirectory) }
        File(dir,"source.txt").writeText(document.rawSource)
        File(dir,"approved.txt").writeText(DocumentExamination.encode(listOf(document)))
        pages.forEachIndexed { i,page ->
            require(page.isFile) { "صورة المصدر غير موجودة" }
            page.copyTo(File(dir,"page-${i+1}.jpg"),overwrite=true)
        }
    }
}
