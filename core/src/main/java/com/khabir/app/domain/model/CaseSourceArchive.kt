package com.khabir.app.domain.model

import java.io.File

/** Copies reviewed sources into app-private storage before temporary camera pages are removed. */
class CaseSourceArchive(private val root:File) {
    fun archive(caseId:Long,records:List<LegalDocumentRecord>,pages:List<File>) {
        require(caseId > 0)
        for(record in records) {
            val dir = File(File(root,caseId.toString()),record.fingerprint).apply { mkdirs() }
            File(dir,"source.txt").writeText(LegalSourceAudit.decode(LegalSourceAudit.encode(listOf(record))))
            for(number in record.pages) {
                val source = pages.getOrNull(number-1) ?: continue
                require(source.isFile) { "صورة المصدر $number غير موجودة؛ لم تُحذف بقية الصور" }
                val target = File(dir,"page-$number.jpg")
                if(!target.isFile || target.length() != source.length()) source.copyTo(target,overwrite=true)
            }
        }
    }
    fun pages(caseId:Long):List<File> = File(root,caseId.toString()).takeIf(File::isDirectory)
        ?.walkTopDown()?.filter { it.isFile && it.name.startsWith("page-") && it.extension == "jpg" }?.toList().orEmpty()
    fun text(caseId:Long):String = File(root,caseId.toString()).takeIf(File::isDirectory)
        ?.walkTopDown()?.filter { it.isFile && it.name == "source.txt" }?.joinToString("\n\n") { file -> file.readText().lines().filterNot { it.startsWith("بصمة المصدر:") }.joinToString("\n").replace(Regex("\\[\\[SOURCE ([0-9]+)]]"),"المستند $1") }.orEmpty()
}
