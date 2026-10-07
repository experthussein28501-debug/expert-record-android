package com.khabir.app.domain.model

/** Conservative extraction from OCR text. Candidates still require the expert's review. */
object PetitionSubjectExtraction {
    data class Parts(val explanation: String = "", val requests: String = "", val warnings: List<String> = emptyList())

    private val conclusion = Regex("بناء[ًا]?\\s*عليه")
    private val hearing = Regex("(?:ل|لِ)سماع\\s+الحكم(?:\\s+علي(?:هم|هما|ه|ها|هن))?\\s*[:：،-]?\\s*")
    private val requestsLabel = Regex("(?m)^[ \\t]*(?:الطلبات(?: الختامية)?|لذلك يلتمس)\\s*[:：-]?[ \\t]*")
    private val explanationLabel = Regex("(?m)^[ \\t]*(?:موضوع الدعوى|موضوع القضية|شرح الدعوى|وقائع الدعوى|الشرح|الموضوع|الوقائع|و?[أا]علن(?:ته|تهم) بال[آا]تي)\\s*[:：-]?[ \\t]*")
    private val otherSection = Regex("(?m)^[ \\t]*(?:مأمورية الحكم التمهيدي|المأمورية|الخصم|المدعي|المدعى عليه|ملاحظات|مخاطبة المحامي|تاريخ الحكم التمهيدي|رقم الدعوى|نوع المستند|دليل إعادة الدعوى|دليل التقرير السابق|دليل تداول الدعوى|سجل مصادر الاستخراج|تاريخ الإجراء|تاريخ رفع الصحيفة|تاريخ الإعلان|تاريخ الحكم|منطوق الحكم|مقدم الإجراء|صفة مقدم الإجراء|ترتيب الإجراء|رقم الدعوى السابقة|سنة الدعوى السابقة|المحكمة السابقة|رقم الدعوى الحالية|سنة الدعوى الحالية|المحكمة الحالية|دليل الإحالة)\\s*[:：]")
    private val footer = Regex("(?:و?ل[أا]جل\\s+العلم|(?m)^[ \\t]*(?:توقيع المحضر|توقيع المحامي|تحرير[ًاا]|وتفضلوا))")

    fun extract(raw: String): Parts {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').replace("ـ", "").trim()
        val conclusionMatch = conclusion.find(text)
        val label = requestsLabel.find(text)
        val requestStart = conclusionMatch?.range?.last?.plus(1) ?: label?.range?.last?.plus(1)
        val tail = requestStart?.let { text.substring(it) }.orEmpty()
        val hearingMatch = hearing.find(tail)
        val labelledInTail = requestsLabel.find(tail)
        val hasService = Regex("(?:(?:أنا|انا)\\s+المحضر|كلفت(?:ه|هم|هما)\\s+بالحضور|سرايا المحكمة|انتقلت\\s+في\\s+تاريخه)").containsMatchIn(tail)
        val directRelief = Regex("^(?:الحكم|إلزام|الزام|يلتمس|يلتمسون|أول[ًاا]|اولا|[0-9٠-٩]+[.)-])(?:\\s|$)")
        val requestBody = when {
            hearingMatch != null -> tail.substring(hearingMatch.range.last + 1)
            labelledInTail != null -> tail.substring(labelledInTail.range.last + 1)
            label != null && conclusionMatch == null && !hasService -> tail
            !hasService && directRelief.containsMatchIn(tail.trim()) -> tail
            else -> ""
        }
        val requests = bounded(requestBody, includeFooter = true)
        val explanationMatch = explanationLabel.find(text)
        // A sentence stating a fact is a boundary; a bare party label/name/address is not.
        val factStart = Regex("(?m)(?:^|\\n)[ \\t]*(?:(?:الطالب|الطالبة|المدعي|المدعية)\\s+(?:يمتلك|تمتلك|يملك|تملك|يمتلكون|اشترى|اشترت|استأجر|تستأجر)|(?:يمتلك|تمتلك|يمتلكون|يملك)\\s+(?:الطالب|المدعي|الطالبة|المدعية|أطراف)|بموجب\\s+عقد)").find(text)
        val explanationStart = explanationMatch?.range?.last?.plus(1) ?: factStart?.range?.first
        val body = explanationStart?.let { start ->
            val end = listOfNotNull(conclusion.find(text, start)?.range?.first,
                requestsLabel.find(text, start)?.range?.first).minOrNull() ?: text.length
            text.substring(start, end)
        }.orEmpty()
        val explanation = tableToProse(bounded(body))
        val warnings = buildList {
            val alreadyComposed = explanation.startsWith("أقام المدعي") && explanation.contains("طلب في ختامها")
            if ((requestStart != null || explanation.isNotBlank()) && requests.isBlank() && !alreadyComposed) add("الطلبات الختامية غير مؤكدة؛ راجع خاتمة الصحيفة بعد لسماع الحكم.")
            if (explanation.isBlank() && (requestStart != null || explanationMatch != null)) add("شرح الدعوى يحتاج استكمالًا من الصحيفة.")
        }
        return Parts(explanation, requests, warnings)
    }

    /** Also cleans a labelled API result if the provider accidentally includes the service paragraph. */
    fun cleanRequests(raw: String): String {
        val text = raw.trim()
        val marker = hearing.find(text)
        if (marker != null) return bounded(text.substring(marker.range.last + 1), includeFooter = true)
        if (Regex("(?:(?:أنا|انا)\\s+المحضر|كلفت(?:ه|هم|هما)\\s+بالحضور|سرايا المحكمة|انتقلت\\s+في\\s+تاريخه)").containsMatchIn(text)) return extract("الطلبات الختامية:\n$text").requests
        return bounded(text, includeFooter = true)
    }

    private fun bounded(raw: String, includeFooter: Boolean = false): String {
        val ends = listOfNotNull(otherSection.find(raw)?.range?.first,
            if (includeFooter) footer.find(raw)?.range?.first else null,
            if (includeFooter) explanationLabel.find(raw)?.range?.first else null)
        return raw.substring(0, ends.minOrNull() ?: raw.length).trim().trimStart(':', '：', '،', ' ')
    }

    /** Only convert explicit table columns; ambiguous OCR rows remain intact for review. */
    fun tableToProse(raw: String): String {
        val lines = raw.lines()
        val output = mutableListOf<String>()
        var index = 0
        fun isTable(line: String) = line.contains('|') || line.contains('\t')
        fun cells(line: String) = line.trim().trim('|').split(Regex("\\s*\\|\\s*|\\t+")).map(String::trim)
        while (index < lines.size) {
            if (!isTable(lines[index])) { output += lines[index++]; continue }
            val start = index
            while (index < lines.size && isTable(lines[index])) index++
            val block = lines.subList(start, index)
            val headers = cells(block.first())
            val rows = block.drop(1).map(::cells).filterNot { row -> row.all { it.matches(Regex("[: -]+")) } }
            val knownHeader = headers.any { it in setOf("المساحة", "الحد البحري", "البحري", "الناحية", "رقم القطعة") }
            if (!knownHeader || rows.isEmpty() || rows.any { it.size != headers.size }) output += block
            else rows.forEach { row -> output += headers.zip(row).joinToString("، ") { (name, value) -> "$name: ${value.ifBlank { "[غير واضح]" }}" } + "." }
        }
        return output.joinToString("\n").trim()
    }
}
