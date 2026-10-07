package com.khabir.app.domain.model

import java.time.LocalDate
import java.util.Base64

enum class ExamDocumentKind(val label: String) {
    PETITION("عريضة الدعوى"), EXPERT_REPORT("تقرير خبير"), JUDGMENT("حكم محكمة"),
    PRIVATE_SALE("عقد بيع عرفي"), REGISTERED_SALE("عقد بيع مشهر"),
    CADASTRAL_CERTIFICATE("شهادة قيود"), PARTITION("عقد/شرط/محضر قسمة"), HEIR_CERTIFICATE("إعلام وراثة"), INHERITANCE("شهر إرث وبيع"), OTHER("مستند آخر");
}
enum class DocumentCopyKind(val label: String) {
    UNKNOWN("صفة النسخة تحتاج تأكيدًا"), PHOTOCOPY("صورة ضوئية"), CERTIFIED("صورة طبق الأصل"), ORIGINAL("أصل");
}
enum class DocumentSide {
    GENERAL, CLAIMANT, RESPONDENT, CIVIL_CLAIMANT, ACCUSED;
    fun label(caseType: String): String = when(this) {
        GENERAL -> "مستندات عامة"
        CLAIMANT -> if(legalNormalize(caseType).contains("استئناف")) "مستندات المستأنف" else "مستندات المدعي"
        RESPONDENT -> if(legalNormalize(caseType).contains("استئناف")) "مستندات المستأنف ضده" else "مستندات المدعى عليه"
        CIVIL_CLAIMANT -> "مستندات المدعي بالحق المدني"
        ACCUSED -> "مستندات المتهم"
    }
    companion object {
        fun available(caseType: String): List<DocumentSide> = if(Regex("جنح|جناي").containsMatchIn(legalNormalize(caseType)))
            listOf(GENERAL,CIVIL_CLAIMANT,ACCUSED) else listOf(GENERAL,CLAIMANT,RESPONDENT)
    }
}

data class ExaminedDocument(
    val id: String,
    val kind: ExamDocumentKind,
    val copyKind: DocumentCopyKind = DocumentCopyKind.UNKNOWN,
    val date: LocalDate? = null,
    val side: DocumentSide = DocumentSide.GENERAL,
    val fields: Map<String,String> = emptyMap(),
    val rawSource: String = "",
    val visualEvidence: String = "",
    val warnings: List<String> = emptyList(),
    val approvedText: String = ""
) {
    fun render(): String = if(approvedText.isNotBlank()) approvedText else DocumentExamination.render(this)
}

object DocumentExamination {
    const val KEY="_approved_document_examination_v1"
    const val SNAPSHOT="_approved_document_text_v1"
    const val VERSION="document-examination-v3"
    val labels=listOf("نوع المستند","صفة النسخة","دليل صفة النسخة","تاريخ المستند","رقم الدعوى","سنة الدعوى","المحكمة","المدعون","المدعى عليهم","نوع الدعوى","وصف الأعيان","الطلبات الختامية","منطوق الحكم","معاينة الخبير","النتيجة النهائية","البائع","المشتري","الثمن الإجمالي","مصدر ملكية البائع","المبلغ المتبقي","تأشيرات المحكمة","أطراف القسمة","اختصاصات القسمة","تاريخ الوفاة","تفاصيل الورثة","رقم الشهر","سنة الشهر","مكتب الشهر","المورث","وصف التركة","أنصبة الورثة","بيانات البيع","بيانات القطعة","إجمالي المسطح","قيود الملاك","البيانات الإخبارية","مراجع الصفحات","تحذيرات")
    private fun kind(raw:String): ExamDocumentKind {
        val t=legalNormalize(raw)
        return when {
            Regex("اعلام(?:ات)?(?: شرعي)? (?:ال)?وراث[ةه]|اعلان (?:ال)?وراثة").containsMatchIn(t) -> ExamDocumentKind.HEIR_CERTIFICATE
            Regex("(?:عقد|شرط|محضر) قسمة|(?:عقد|شرط|محضر) قسمه").containsMatchIn(t) -> ExamDocumentKind.PARTITION
            Regex("شهر ارث|شهر ميراث").containsMatchIn(t) -> ExamDocumentKind.INHERITANCE
            Regex("شهاده قيود|شهادة قيود").containsMatchIn(t) -> ExamDocumentKind.CADASTRAL_CERTIFICATE
            t.contains("تقرير") && (t.contains("خبير") || Regex("خبر[ةه]").containsMatchIn(t)) -> ExamDocumentKind.EXPERT_REPORT
            t.contains("عقد") && t.contains("مشهر") -> ExamDocumentKind.REGISTERED_SALE
            t.contains("عقد") && t.contains("بيع") -> ExamDocumentKind.PRIVATE_SALE
            t.contains("حكم") -> ExamDocumentKind.JUDGMENT
            Regex("عريض[ةه]|صحيف[ةه]").containsMatchIn(t) -> ExamDocumentKind.PETITION
            else -> ExamDocumentKind.OTHER
        }
    }
    fun date(value:String):LocalDate? {
        val t=legalNormalize(value)
        return runCatching { LocalDate.parse(t) }.getOrNull() ?: Regex("""(?<!\d)(\d{1,2})[/-](\d{1,2})[/-](\d{4})(?!\d)""").find(t)?.let {
            runCatching { LocalDate.of(it.groupValues[3].toInt(),it.groupValues[2].toInt(),it.groupValues[1].toInt()) }.getOrNull()
        }
    }
    fun parse(raw: String, side:DocumentSide = DocumentSide.GENERAL):ExaminedDocument {
        val body=raw.replace("[[DOCUMENT_EXAM]]", "").replace("[[END DOCUMENT_EXAM]]", "").trim()
        val fields=linkedMapOf<String,String>()
        val starts=Regex("(?m)^\\s*(${labels.joinToString("|") { Regex.escape(it) }})\\s*:[ \\t]*").findAll(body).toList()
        val duplicate=mutableListOf<String>()
        starts.forEachIndexed { i,m ->
            val value=body.substring(m.range.last+1,starts.getOrNull(i+1)?.range?.first ?: body.length).trim()
            val key=m.groupValues[1]
            if(fields.containsKey(key)) duplicate += "حقل مكرر يحتاج مراجعة: $key"
            else if(value.isNotBlank() && value !in listOf("غير موجود","غير واضح","غير مؤكد","UNKNOWN")) fields[key]=value
        }
        val k=kind(fields["نوع المستند"] ?: body)
        // A visible seal is a proposed clue; certification is confirmed by the reviewer only.
        val copy=when(fields["صفة النسخة"]) {
            "صورة ضوئية", "PHOTOCOPY" -> DocumentCopyKind.PHOTOCOPY
            "أصل", "ORIGINAL" -> DocumentCopyKind.ORIGINAL
            else -> DocumentCopyKind.UNKNOWN
        }
        val warnings=mutableListOf<String>().apply { addAll(duplicate) }
        val d=if(duplicate.any { it.endsWith("تاريخ المستند") }) null else date(fields["تاريخ المستند"].orEmpty())
        if(d==null) warnings += "تاريخ المستند غير مؤكد؛ لا يُستبدل بتاريخ تصويره أو بتاريخ مذكور عرضًا"
        if(copy==DocumentCopyKind.UNKNOWN) warnings += "أكد صفة النسخة من الأصل المعروض؛ لون الورقة وحده لا يكفي"
        val required=when(k) {
            ExamDocumentKind.PETITION -> listOf("الطلبات الختامية")
            ExamDocumentKind.JUDGMENT -> listOf("منطوق الحكم")
            ExamDocumentKind.EXPERT_REPORT -> listOf("النتيجة النهائية")
            ExamDocumentKind.CADASTRAL_CERTIFICATE -> listOf("قيود الملاك")
            ExamDocumentKind.INHERITANCE -> listOf("أنصبة الورثة")
            ExamDocumentKind.PARTITION -> listOf("أطراف القسمة","اختصاصات القسمة")
            ExamDocumentKind.HEIR_CERTIFICATE -> listOf("المورث","تاريخ الوفاة","تفاصيل الورثة")
            else -> emptyList()
        }
        required.filter { fields[it].isNullOrBlank() }.forEach { warnings += "لم يستخرج $it؛ راجع المصدر" }
        fields["تحذيرات"]?.let { warnings += it }
        if(fields.isEmpty()) warnings += "نص قراءة محلية غير مهيكل؛ راجعه دون افتراض معلومات ناقصة"
        return ExaminedDocument(legalFingerprint(body),k,copy,d,side,fields,raw,fields["دليل صفة النسخة"].orEmpty(),warnings)
    }
    fun confirmCopyPrefix(text:String, copy:DocumentCopyKind):String {
        val body=text.trim().replace(Regex("^(?:صورة ضوئية من |صورة طبق الأصل من |\\[صفة النسخة تحتاج تأكيدًا] )"), "")
        val prefix=when(copy) { DocumentCopyKind.PHOTOCOPY -> "صورة ضوئية من ";DocumentCopyKind.CERTIFIED -> "صورة طبق الأصل من ";DocumentCopyKind.ORIGINAL -> "";DocumentCopyKind.UNKNOWN -> "[صفة النسخة تحتاج تأكيدًا] " }
        return prefix+body
    }
    fun render(d:ExaminedDocument):String = buildString {
        when(d.copyKind) {
            DocumentCopyKind.PHOTOCOPY -> append("صورة ضوئية من ")
            DocumentCopyKind.CERTIFIED -> append("صورة طبق الأصل من ")
            DocumentCopyKind.UNKNOWN -> append("[صفة النسخة تحتاج تأكيدًا] ")
            DocumentCopyKind.ORIGINAL -> Unit
        }
        fun v(key:String)=d.fields[key].orEmpty()
        fun add(key:String,prefix:String) { if(v(key).isNotBlank()) append(prefix).append(v(key)) }
        fun identity() {
            add("رقم الدعوى"," في الدعوى رقم ");add("سنة الدعوى"," لسنة ");add("المحكمة"," أمام ")
            if(v("المدعون").isNotBlank()) append(" المرفوعة من ").append(PartySummaries.names(v("المدعون").split(Regex("[،,;؛|\\n]+"))))
            if(v("المدعى عليهم").isNotBlank()) append(" ضد ").append(PartySummaries.names(v("المدعى عليهم").split(Regex("[،,;؛|\\n]+"))))
        }
        fun property(key:String="وصف الأعيان") { add(key,"، عن ") }
        when(d.kind) {
            ExamDocumentKind.PETITION -> {
                append("عريضة الدعوى");identity();add("نوع الدعوى","، موضوعها ");property()
                add("الطلبات الختامية","، وطلب في ختامها:\n")
            }
            ExamDocumentKind.JUDGMENT -> {
                append("الحكم");identity();d.date?.let { append("، قضي فيه بجلسة ").append(it) }
                add("منطوق الحكم","، بمنطوقه:\n")
            }
            ExamDocumentKind.EXPERT_REPORT -> {
                append("تقرير الخبير");identity();add("معاينة الخبير","\nأسفرت معاينة السيد الخبير عن:\n")
                add("النتيجة النهائية","\nوانتهى السيد الخبير في نتيجته النهائية إلى:\n")
            }
            ExamDocumentKind.PRIVATE_SALE,ExamDocumentKind.REGISTERED_SALE -> {
                append(d.kind.label)
                if(d.kind==ExamDocumentKind.REGISTERED_SALE) { add("رقم الشهر"," رقم ");add("سنة الشهر"," لسنة ");add("مكتب الشهر"," بمكتب ") }
                d.date?.let { append(" مؤرخ ").append(it) }
                add("تأشيرات المحكمة","، ومؤشر عليه: ");add("البائع"," منسوب صدوره من ");add("المشتري"," إلى ");property()
                add("الثمن الإجمالي","، نظير ثمن إجمالي قدره ")
                add("مصدر ملكية البائع","، وقد ورد بشأن أيلولة الملكية للبائع: ")
                add("المبلغ المتبقي","، والمبلغ المتبقي: ")
            }
            ExamDocumentKind.HEIR_CERTIFICATE -> {
                append("إعلام وراثة");add("المورث"," ورد به وفاة المرحوم ");add("تاريخ الوفاة"," بتاريخ ")
                add("تفاصيل الورثة","، وانحصر إرثه في:\n")
            }
            ExamDocumentKind.PARTITION -> {
                append("عقد قسمة");d.date?.let { append(" مؤرخ ").append(it) }
                add("المورث"," عن تركة ");add("أطراف القسمة"," بين كل من:\n")
                property();add("اختصاصات القسمة","\nوقد ورد اختصاص كل طرف كالآتي:\n")
                add("تأشيرات المحكمة","\nومؤشر عليه: ")
            }
            ExamDocumentKind.INHERITANCE -> {
                append("محرر شهر إرث");add("رقم الشهر"," مشهر برقم ");add("سنة الشهر"," لسنة ");add("مكتب الشهر"," بمكتب ")
                add("المورث"," عن تركة ");property("وصف التركة");add("أنصبة الورثة","\nوقد وردت أنصبة الورثة كالآتي:\n")
                add("بيانات البيع","\nوبعد شهر الإرث ورد البيع كالآتي:\n")
            }
            ExamDocumentKind.CADASTRAL_CERTIFICATE -> {
                append("شهادة قيود");property("بيانات القطعة");add("إجمالي المسطح","، ورد بها إجمالي مسطح القطعة ")
                add("قيود الملاك","\nوقيود الملاك ومساحات كل منهم:\n");add("البيانات الإخبارية","\nوالبيانات الإخبارية والقيود الواردة:\n")
            }
            ExamDocumentKind.OTHER -> append(d.rawSource)
        }
        if(d.fields.isEmpty() && d.kind!=ExamDocumentKind.OTHER) append("\nالنص المقروء للمراجعة:\n").append(d.rawSource)
    }.trim()
    fun ordered(docs:List<ExaminedDocument>) = docs.distinctBy { it.id }.sortedBy { it.date ?: LocalDate.MAX }
    fun renderAll(docs:List<ExaminedDocument>,caseType:String,style:ReportListStyle):String = DocumentSide.entries.mapNotNull { side ->
        val group=ordered(docs.filter { it.side==side })
        if(group.isEmpty()) null else side.label(caseType)+":\n"+group.mapIndexed { i,d -> ReportLists.marker(style,i+1)+d.render() }.joinToString("\n\n")
    }.joinToString("\n\n")
    private fun enc(s:String)=Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun dec(s:String)=String(Base64.getDecoder().decode(s),Charsets.UTF_8)
    fun encode(docs:List<ExaminedDocument>):String = docs.joinToString("\n") { d ->
        listOf(d.id,d.kind.name,d.copyKind.name,d.date?.toString().orEmpty(),d.side.name,
            enc(d.render()),enc(d.rawSource),enc(d.fields.entries.joinToString("\n") { enc(it.key)+":"+enc(it.value) }),enc(d.visualEvidence),enc(d.warnings.joinToString("\n"))).joinToString("|")
    }
    fun decode(value:String?):List<ExaminedDocument> = value.orEmpty().lines().mapNotNull { line -> runCatching {
        val p=line.split('|');require(p.size==10)
        val fields=dec(p[7]).lines().filter(String::isNotBlank).associate { val row=it.split(':',limit=2);dec(row[0]) to dec(row[1]) }
        ExaminedDocument(p[0],ExamDocumentKind.valueOf(p[1]),DocumentCopyKind.valueOf(p[2]),date(p[3]),DocumentSide.valueOf(p[4]),fields,dec(p[6]),dec(p[8]),dec(p[9]).lines().filter(String::isNotBlank),dec(p[5]))
    }.getOrNull() }
}
