package com.khabir.app.domain.model

import java.util.Base64

enum class ReportAiKind(val label:String) { RESEARCH("إعداد البحث من بيانات القضية"), CONCLUSION("إعداد النتيجة من البحث المعتمد") }
data class ReportAiProposal(val kind:ReportAiKind,val text:String,val audit:String,val fingerprint:String,val statuses:Map<String,String> = emptyMap(),val targetSection:String="",val originalText:String=text)

/** Explicit prepare/generate/review/apply operations; observing/editing a report never generates content. */
object ReportAiWorkflow {
    const val STATUS_KEY="_research_task_statuses_v1"
    const val APPROVED_TEXT_KEY="_research_approved_text_v1"
    const val CONCLUSION_KEY="_conclusion_approved_context_v1"
    const val HISTORY_KEY="_research_review_audit_v1"
    fun fingerprint(report:Report,kind:ReportAiKind):String = legalFingerprint(ReportResearch.fingerprint(report)+"\u001f"+kind.name+if(kind==ReportAiKind.CONCLUSION) "\u001f"+report.research else "")
    fun conclusionEvidence(report:Report):List<ReportResearch.Evidence> = report.research.split(Regex("""\n\s*\n""")).map(String::trim).filter(String::isNotBlank).distinct().map {
        ReportResearch.Evidence("ev_${legalFingerprint("research\u001f"+it).take(16)}","بحث معتمد",it)
    }
    fun context(report:Report,kind:ReportAiKind):String = if(kind==ReportAiKind.RESEARCH) ReportResearch.context(report) else buildString {
        appendLine("القضية: ${report.caseNo} لسنة ${report.caseYear} — ${report.court}")
        ReportResearch.tasks(report.assignment).forEach { appendLine("[[TASK ${it.id}]]\n${it.text}\n[[END TASK]]") }
        conclusionEvidence(report).forEach { appendLine("[[EVIDENCE ${it.id} ${it.kind}]]\n${it.text}\n[[END EVIDENCE]]") }
    }
    fun statuses(report:Report):Map<String,String> {
        val m=ReportCustomSectionCodec.decode(report.customSectionContentsSpec)
        return if(m[APPROVED_TEXT_KEY]==legalFingerprint(report.research)) decodeStatuses(m[STATUS_KEY]) else emptyMap()
    }
    fun conclusionContract(report:Report):String = """
أعد نتيجة نهائية تلخص البحث المعتمد المرفق فقط، بندًا مقابل كل TASK، ولا تنسخ قالب قضية أخرى ولا تضف واقعة أو حسابًا أو أسماء أو تاريخًا أو نتيجة جديدة.
اختصر دون إخلال، واحتفظ بالنص إذا كان الاختصار يسقط قيدًا. حافظ على النقص والتعارض والأقوال المنسوبة، ولا تحول رأيًا أو مدة وضع يد إلى ملكية ثابتة.
لكل بند أعد: [[RESULT <task_id>]] ثم أسطر:
الحالة: <SUPPORTED أو INSUFFICIENT أو CONFLICTING أو UNPERFORMED أو REPORTED>
المصدر: <ev_id موجود من البحث المعتمد>
الاقتباس: <عبارة حرفية من المصدر تثبت مضمون الخلاصة، مع القيد أو النقص عند وجوده>
النتيجة: <الخلاصة العربية دون إضافة>
[[END RESULT]]
إذا لا توجد إجابة لهذا البند بالبحث، الحالة INSUFFICIENT والمصدر والاقتباس فارغان والنتيجة تذكر أن البحث لا يتضمن ردًا كافيًا. الأحكام التاريخية غير المجيبة لا تصبح جوابًا على المأمورية.
حالات البحث التي لا يجوز ترقيتها: ${statuses(report)}
    """.trimIndent()
    private fun value(body:String,key:String):String {
        val start=Regex("(?m)^\\s*${Regex.escape(key)}\\s*:[ \\t]*").find(body) ?: return ""
        val tail=body.substring(start.range.last+1)
        val end=Regex("(?m)^\\s*(?:الحالة|المصدر|الاقتباس|النتيجة)\\s*:").find(tail)
        return tail.take(end?.range?.first ?: tail.length).trim()
    }
    fun reviewConclusion(raw:String,report:Report,context:ReportResearch.Context):ReportAiProposal {
        val errors=mutableListOf<String>();val prior=statuses(report)
        val blocks=Regex("\\[\\[RESULT (task_[a-zA-Z0-9_]+)]]\\s*([\\s\\S]*?)\\[\\[END RESULT]]").findAll(raw).toList()
        val paragraphs=blocks.map { b ->
            val id=b.groupValues[1];val body=b.groupValues[2];val status=value(body,"الحالة")
            val ref=value(body,"المصدر");val quote=value(body,"الاقتباس");val result=value(body,"النتيجة")
            val evidence=context.evidence.firstOrNull { it.id==ref }
            if(ReportResearch.Status.entries.none { it.name==status }) errors += "حالة غير صالحة $id"
            val previous=prior[id]
            if(previous!=null && previous!="SUPPORTED" && previous!=status) errors += "لا يجوز تغيير حالة نقص/تعارض/قول منسوب للبند $id"
            if(result.isBlank()) errors += "خلاصة فارغة $id"
            if(status!="INSUFFICIENT" && (evidence==null || quote.isBlank() || !evidence.text.contains(quote))) errors += "خلاصة بلا اقتباس صحيح من البحث $id"
            if(ref.isNotBlank() && evidence==null) errors += "مرجع خلاصة غير موجود $id"
            if(quote.isNotBlank() && evidence?.text?.contains(quote)!=true) errors += "اقتباس غير مطابق $id"
            if(status in setOf("INSUFFICIENT","UNPERFORMED","CONFLICTING","REPORTED") && !Regex("(?:لم|لا |غير|نقص|ناقص|يتعذر|تعذر|يلزم|متعارض|تعارض|بحسب|وفق|أقوال|اقوال)").containsMatchIn(result)) errors += "اختصار أسقط قيد الحالة $id"
            val digits=Regex("[0-9]+(?:[./-][0-9]+)*").findAll(legalNormalize(result)).map { it.value }.toSet()
            val sourceDigits=Regex("[0-9]+(?:[./-][0-9]+)*").findAll(legalNormalize(evidence?.text.orEmpty())).map { it.value }.toSet()
            if(!sourceDigits.containsAll(digits)) errors += "رقم أو تاريخ جديد في الخلاصة $id"
            "بند المأمورية: ${context.tasks.firstOrNull { it.id==id }?.text.orEmpty()}\n$result"
        }
        if(blocks.map { it.groupValues[1] }.toSet()!=context.tasks.map { it.id }.toSet() || blocks.size!=context.tasks.size) errors += "النتيجة لا تغطي كل بنود المأمورية مرة واحدة"
        require(errors.isEmpty()) { errors.distinct().joinToString("؛ ") }
        return ReportAiProposal(ReportAiKind.CONCLUSION,paragraphs.joinToString("\n\n"),raw,fingerprint(report,ReportAiKind.CONCLUSION))
    }
    fun apply(report:Report,proposal:ReportAiProposal,edited:String,replace:Boolean):Report {
        require(proposal.fingerprint==fingerprint(report,proposal.kind)) { "تغيرت بيانات القضية أو البحث؛ أعد إعداد الاقتراح قبل اعتماده" }
        require(edited.isNotBlank()) { "النص فارغ" }
        require(ReportResearch.tasks(report.assignment).all { edited.contains("بند المأمورية: ${it.text}") }) { "حذفت إجابة بند مأمورية؛ احتفظ بجميع البنود" }
        val old=if(proposal.kind==ReportAiKind.RESEARCH) report.research else report.conclusion
        var metadata=ReportCustomSectionCodec.decode(report.customSectionContentsSpec)
        val section=if(proposal.kind==ReportAiKind.RESEARCH) "research" else "conclusion"
        val style=ReportListStyle.decode(metadata[ReportListStyle.key(section)])
        val formatted=if(style==ReportListStyle.PLAIN) edited.trim() else ReportLists.apply(edited.trim(),style)
        val text=if(replace) formatted else if(old.contains(formatted)) old else listOf(old,formatted).filter(String::isNotBlank).joinToString("\n\n")
        metadata=if(proposal.kind==ReportAiKind.RESEARCH) metadata+mapOf(STATUS_KEY to encodeStatuses(proposal.statuses),APPROVED_TEXT_KEY to legalFingerprint(text),ReportResearch.CONTEXT_KEY to ReportResearch.fingerprint(report),HISTORY_KEY to proposal.audit) else metadata+(CONCLUSION_KEY to proposal.fingerprint)
        val updated=report.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(metadata))
        return if(proposal.kind==ReportAiKind.RESEARCH) updated.copy(research=text) else updated.copy(conclusion=text)
    }
    fun confirmManualResearch(report:Report):Report {
        require(report.research.isNotBlank()) { "لا يوجد بحث لمراجعته" }
        var metadata=ReportCustomSectionCodec.decode(report.customSectionContentsSpec)
        if(metadata[APPROVED_TEXT_KEY]!=legalFingerprint(report.research)) metadata=metadata-STATUS_KEY
        metadata=metadata+mapOf(ReportResearch.CONTEXT_KEY to ReportResearch.fingerprint(report),APPROVED_TEXT_KEY to legalFingerprint(report.research))
        return report.copy(customSectionContentsSpec=ReportCustomSectionCodec.encode(metadata))
    }
    fun conclusionNeedsReview(report:Report):Boolean = ReportCustomSectionCodec.decode(report.customSectionContentsSpec)[CONCLUSION_KEY]?.let { it!=fingerprint(report,ReportAiKind.CONCLUSION) } ?: false
    fun encodeStatuses(map:Map<String,String>):String = map.entries.joinToString("\n") { it.key+"|"+it.value }
    fun decodeStatuses(value:String?):Map<String,String> = value.orEmpty().lines().mapNotNull { val p=it.split('|');if(p.size==2) p[0] to p[1] else null }.toMap()
}
