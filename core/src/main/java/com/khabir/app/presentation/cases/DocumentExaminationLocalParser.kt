package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.*

/** Conservative fallback: copies anchored source spans and keeps uncertain raw material for review. */
object DocumentExaminationLocalParser {
    fun parse(raw:String,side:DocumentSide=DocumentSide.GENERAL):ExaminedDocument {
        val candidate=DocumentExamination.parse(raw,side)
        if(raw.contains("[[DOCUMENT_EXAM]]")) return candidate
        val fields=candidate.fields.toMutableMap()
        val text=raw.replace("\r\n","\n")
        fun line(key:String,vararg labels:String) {
            if(fields[key].isNullOrBlank()) Regex("(?m)^\\s*(?:${labels.joinToString("|") { Regex.escape(it) }})\\s*[:：]\\s*([^\\n]+)").find(text)?.groupValues?.get(1)?.trim()?.let { fields[key]=it }
        }
        fun span(key:String,start:String,stop:String?=null) {
            if(!fields[key].isNullOrBlank()) return
            val m=Regex(start).find(text) ?: return
            val tail=text.substring(m.range.first)
            val end=stop?.let { Regex(it).find(tail.substring(m.value.length))?.range?.first?.plus(m.value.length) } ?: tail.length
            fields[key]=tail.substring(0,end).trim()
        }
        when(candidate.kind) {
            ExamDocumentKind.PETITION -> {
                val parsed=PetitionIntakeParser.parse(text)
                parsed.caseNo?.let { fields.putIfAbsent("رقم الدعوى",it) };parsed.caseYear?.let { fields.putIfAbsent("سنة الدعوى",it) }
                parsed.court?.let { fields.putIfAbsent("المحكمة",it) }
                val plaintiffs=parsed.parties.filter { it.role==PartyRole.PLAINTIFF }.joinToString("، ") { it.name }
                val defendants=parsed.parties.filter { it.role==PartyRole.DEFENDANT }.joinToString("، ") { it.name }
                if(plaintiffs.isNotBlank()) fields.putIfAbsent("المدعون",plaintiffs)
                if(defendants.isNotBlank()) fields.putIfAbsent("المدعى عليهم",defendants)
                parsed.finalRequests?.let { fields.putIfAbsent("الطلبات الختامية",it) }
                parsed.subjectOfCase?.let { fields.putIfAbsent("وصف الأعيان",it) }
            }
            ExamDocumentKind.JUDGMENT -> {
                span("منطوق الحكم","حكمت\\s+المحكمة[\\s\\S]*")
                Regex("(?:بجلسة|جلسة الحكم|تاريخ الحكم)\\s*[:：]?\\s*([0-9٠-٩]{1,2}[/-][0-9٠-٩]{1,2}[/-][0-9٠-٩]{4})").find(text)?.groupValues?.get(1)?.let { fields.putIfAbsent("تاريخ المستند",it) }
                val parsed=PetitionIntakeParser.parse(text)
                parsed.caseNo?.let { fields.putIfAbsent("رقم الدعوى",it) };parsed.caseYear?.let { fields.putIfAbsent("سنة الدعوى",it) }
                parsed.court?.let { fields.putIfAbsent("المحكمة",it) }
            }
            ExamDocumentKind.EXPERT_REPORT -> {
                span("معاينة الخبير","(?m)^\\s*(?:المعاينة|معاينة الخبير)\\s*[:：]?","(?m)^\\s*النتيجة النهائية")
                span("النتيجة النهائية","(?m)^\\s*النتيجة النهائية\\s*[:：]?")
            }
            ExamDocumentKind.PRIVATE_SALE,ExamDocumentKind.REGISTERED_SALE -> {
                line("البائع","البائع","الطرف الأول");line("المشتري","المشتري","الطرف الثاني")
                line("الثمن الإجمالي","الثمن","الثمن الإجمالي");line("المبلغ المتبقي","المبلغ المتبقي")
                line("مصدر ملكية البائع","مصدر ملكية البائع","أيلولة الملكية")
                Regex("(?:مؤرخ في|مؤرخ|تاريخ العقد)\\s*[:：]?\\s*([0-9٠-٩]{1,2}[/-][0-9٠-٩]{1,2}[/-][0-9٠-٩]{4})").find(text)?.groupValues?.get(1)?.let { fields.putIfAbsent("تاريخ المستند",it) }
            }
            ExamDocumentKind.HEIR_CERTIFICATE -> {
                line("المورث","المرحوم","المورث","المتوفى")
                line("تاريخ الوفاة","تاريخ الوفاة")
                Regex("(?:وفاة المرحوم|توفي المرحوم|وفاة)\\s+([^\\n]+?)\\s+بتاريخ\\s+([0-9٠-٩]{1,2}[/-][0-9٠-٩]{1,2}[/-][0-9٠-٩]{4})").find(text)?.let {
                    fields.putIfAbsent("المورث",it.groupValues[1].trim());fields.putIfAbsent("تاريخ الوفاة",it.groupValues[2])
                }
                span("تفاصيل الورثة","(?:وانحصر|انحصر|انحصار)\\s+(?:إرثه|ارثه|الإرث|الارث)")
            }
            ExamDocumentKind.PARTITION -> {
                line("المورث","المورث","عن تركة")
                line("أطراف القسمة","أطراف القسمة","أطراف العقد","بين الورثة")
                span("اختصاصات القسمة","(?m)^\\s*(?:اختصاصات القسمة|اختص|وقد اختص)\\s*[:：]?")
                if(fields["اختصاصات القسمة"].isNullOrBlank()) {
                    val allocations=Regex("(?m)^.*(?:اختص|اختصت|نصيب).*\\n?(?:.*(?:مساحة|مساحته|حدود|البحري|القبلي|الشرقي|الغربي).*\\n?)*").findAll(text).map { it.value.trim() }.toList()
                    if(allocations.isNotEmpty()) fields["اختصاصات القسمة"]=allocations.joinToString("\n")
                }
            }
            else -> Unit
        }
        line("تأشيرات المحكمة","تأشيرات المحكمة","تأشيرة المحكمة")
        if(fields["تأشيرات المحكمة"].isNullOrBlank()) Regex("(?m)^.*(?:نظر في الدعوى|نُظر في الدعوى|نظرت في الدعوى).*$").find(text)?.value?.let { fields["تأشيرات المحكمة"]=it.trim() }
        // Explicit tables become one faithful line per row; missing headings are never guessed.
        val table=text.lines().filter { it.count { c->c=='|' || c=='\t' }>=2 }
        if(table.isNotEmpty()) {
            val rows=table.map { it.split(Regex("[|\\t]")).map(String::trim).filter(String::isNotBlank) }
            val header=rows.first()
            val rendered=rows.drop(1).map { row -> if(row.size==header.size) header.zip(row).joinToString("، ") { (h,v)->"$h: $v" } else row.joinToString("، ") }.joinToString("\n")
            val key=when(candidate.kind) { ExamDocumentKind.CADASTRAL_CERTIFICATE -> "قيود الملاك";ExamDocumentKind.INHERITANCE -> "أنصبة الورثة";ExamDocumentKind.PARTITION -> "اختصاصات القسمة";else -> "وصف الأعيان" }
            if(fields[key].isNullOrBlank()) fields[key]=rendered
        }
        if(candidate.kind==ExamDocumentKind.HEIR_CERTIFICATE) fields["تفاصيل الورثة"]?.let { fields["تفاصيل الورثة"]=it.replace(Regex("^(?:وانحصر|انحصر|انحصار)\\s+(?:إرثه|ارثه|الإرث|الارث)\\s+(?:في|فى)\\s*[:：]?\\s*"),"") }
        val date=DocumentExamination.date(fields["تاريخ المستند"].orEmpty())
        val stillMissing=candidate.warnings.filterNot { warning -> (date!=null && warning.startsWith("تاريخ المستند غير مؤكد")) || fields.any { (key,value)->value.isNotBlank() && warning=="لم يستخرج $key؛ راجع المصدر" } }
        return candidate.copy(fields=fields,date=date,rawSource=raw,warnings=stillMissing + "قراءة محلية: طابق كامل النص مع المصدر قبل الاعتماد، خصوصًا الجداول والنسخة والتواريخ")
    }
}
