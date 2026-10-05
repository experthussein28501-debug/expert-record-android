package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.*

object LegalCaseAssembly {
    data class Result(val current: LegalCaseIdentity?, val previous: List<LegalCaseIdentity>,
        val subject: String, val records: List<LegalDocumentRecord>, val warnings: List<String>)

    fun assemble(input: List<LegalDocumentRecord>, caseType: String = "", defendants: List<String> = emptyList()): Result {
        val records = input.distinctBy { it.fingerprint }
        val warnings = records.flatMap { it.warnings }.toMutableList()
        fun distinctIdentities(values: List<LegalCaseIdentity>) = values.distinctBy {
            listOf(it.number,it.year,it.court).joinToString("|") { v -> legalNormalize(v) }
        }
        val historical = distinctIdentities(records.mapNotNull { it.previousIdentity })
        val explicitCurrent = distinctIdentities(records.mapNotNull { it.currentIdentity })
        val possibleCurrent = distinctIdentities(records.filter { it.type != LegalProcedureType.REFERRAL && it.type != LegalProcedureType.PREVIOUS_JUDGMENT }
            .mapNotNull { it.identity }.filter { candidate -> historical.none { it.matches(candidate) } })
        val current = when {
            explicitCurrent.size == 1 -> explicitCurrent.single().also { explicit ->
                if (possibleCurrent.any { !it.matches(explicit) }) warnings += "بيانات الدعوى الحالية تتعارض مع مستند مختار؛ راجع الرقم والسنة والمحكمة"
            }
            explicitCurrent.size > 1 -> { warnings += "هوية الدعوى الحالية متعارضة؛ اخترها يدويًا قبل الحفظ"; null }
            possibleCurrent.size == 1 -> possibleCurrent.single()
            possibleCurrent.size > 1 -> { warnings += "أرقام الدعاوى أو محاكمها مختلفة دون هوية حالية مؤكدة؛ لا يُعتمد أول رقم"; null }
            records.any { it.type == LegalProcedureType.REFERRAL || it.type == LegalProcedureType.PREVIOUS_JUDGMENT } -> {
                warnings += "بيانات الدعوى الحالية غير متاحة؛ بيانات الحكم السابق تاريخية فقط"; null
            }
            else -> records.mapNotNull { it.identity }.firstOrNull()
        }
        val original = records.filter { it.type == LegalProcedureType.ORIGINAL }
        val referral = records.firstOrNull { it.provenReferral && it.previousIdentity != null }
        val originalText = when {
            PartySummaries.misdemeanor(caseType) -> "النيابة العامة ضد " + PartySummaries.names(defendants).ifBlank { "[المتهم يحتاج مراجعة]" } +
                original.map { it.explanation }.filter(String::isNotBlank).distinct().joinToString("\n\n",prefix = if(original.any { it.explanation.isNotBlank() }) "\n\n" else "")
            referral != null -> referralOpening(referral, original.firstOrNull())
            else -> original.map { IntakeNarrative.subject(it.explanation.takeIf(String::isNotBlank),it.requests) }.filter(String::isNotBlank).distinct().joinToString("\n\n")
        }
        val events = records.filter { it.type.subsidiary || it.type in setOf(LegalProcedureType.REFERRAL,LegalProcedureType.APPEAL) }
        if (events.any { it.manualOrder == null && (if(it.type.subsidiary) it.procedureDate else it.judgmentDate) == null }) {
            warnings += "إجراء بلا تاريخ مثبت: ترتيبه غير مؤكد. عدّل التاريخ أو الترتيب يدويًا؛ ترتيب التصوير ليس ترتيبًا زمنيًا"
        }
        if (events.mapNotNull { it.manualOrder }.let { it.size != it.distinct().size }) warnings += "ترتيب يدوي متكرر؛ راجع ترتيب الإجراءات"
        val ordered = events.sortedWith(compareBy<LegalDocumentRecord> { if(it.manualOrder != null) 0 else 1 }
            .thenBy { it.manualOrder ?: Int.MAX_VALUE }
            .thenBy { (if(it.type.subsidiary) it.procedureDate else it.judgmentDate) ?: java.time.LocalDate.MAX })
        val eventText = ordered.map { r -> when {
            r.type.subsidiary -> subsidiary(r)
            r.type == LegalProcedureType.APPEAL -> appeal(r)
            r.provenReferral -> referralEvent(r,current)
            else -> "[حكم يحتاج إثبات الإحالة ومراجعة تسلسله — المستند ${r.id}]"
        } }
        return Result(current,historical,(listOf(originalText)+eventText).filter(String::isNotBlank).joinToString("\n\n"),records,warnings.distinct())
    }
    private fun subsidiary(r: LegalDocumentRecord): String {
        val claimant = r.claimant.ifBlank { "[اسم مقدم الإجراء يحتاج استكمالًا]" }
        val action = when(r.type) {
            LegalProcedureType.INTERVENTION -> "تدخل $claimant هجوميًا"
            LegalProcedureType.INCIDENTAL -> "قدم $claimant طلبًا عارضًا"
            else -> "أقام $claimant دعوى فرعية"
        }
        val date = r.procedureDate?.let { " بتاريخ $it" }.orEmpty()
        val service = if(r.serviceDate != null) "معلنة بتاريخ ${r.serviceDate}" else "[إعلانها يحتاج تحققًا]"
        val requests = r.requests.ifBlank { "[الطلبات الختامية تحتاج استكمالًا]" }
        return "وأثناء سير الدعوى، $action$date بموجب صحيفة $service، طلب في ختامها:\n$requests" +
            if(r.explanation.isNotBlank()) "\nوعلى سند من القول:\n${r.explanation}" else ""
    }
    private fun referralOpening(r: LegalDocumentRecord, original: LegalDocumentRecord?): String {
        val previous = r.previousIdentity ?: return ""
        val requests = original?.requests?.takeIf(String::isNotBlank) ?: r.requests
        val explanation = original?.explanation?.takeIf(String::isNotBlank) ?: r.explanation
        val source = if(original == null) "[موضوع الدعوى من حيثيات الحكم — المستند ${r.id}، الصفحات ${r.pages.joinToString()}]" else ""
        return "أقام المدعي الدعوى رقم ${previous.number.ifBlank { "[غير مؤكد]" }} لسنة ${previous.year.ifBlank { "[غير مؤكد]" }} أمام ${previous.court.ifBlank { "[المحكمة السابقة غير مؤكدة]" }}، " +
            (if(original != null) "بموجب صحيفة معلنة قانونًا، " else "وفق ما أثبته الحكم، ") +
            "طلب في ختامها:\n${requests.ifBlank { "[الطلبات غير مؤكدة في المصدر]" }}\n\nوعلى سند من القول:\n${explanation.ifBlank { "[شرح الدعوى يحتاج استكمالًا]" }}\n$source"
    }
    private fun referralEvent(r: LegalDocumentRecord,current: LegalCaseIdentity?): String = buildString {
        append("وتداولت الدعوى بالجلسات، وقُضي فيها بجلسة ${r.judgmentDate ?: "[تاريخ الحكم يحتاج مراجعة]"} بالآتي:\n")
        append(r.dispositive.ifBlank { "[منطوق الإحالة يحتاج استكمالًا من الحكم]" })
        val target = r.currentIdentity ?: current
        if(target != null) append("\nوأُحيلت إلى ${target.court.ifBlank { "[المحكمة الحالية غير مؤكدة]" }} وقُيدت برقم ${target.number.ifBlank { "[غير مؤكد]" }} لسنة ${target.year.ifBlank { "[غير مؤكد]" }}.")
        else append("\n[محكمة الإحالة ورقم الدعوى الحالي يحتاجان مراجعة؛ لم يُستخدم الرقم السابق بدلًا منهما]")
    }
    private fun appeal(r: LegalDocumentRecord): String = "ثم نُظر الاستئناف وفق المستند ${r.id}، وصدر الحكم بجلسة ${r.judgmentDate ?: "[تاريخ غير مؤكد]"} بالآتي:\n" +
        r.dispositive.ifBlank { "[المنطوق يحتاج مراجعة]" }
}
