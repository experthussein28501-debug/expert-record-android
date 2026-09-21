package com.khabir.app.presentation.cases

enum class DocumentMatchStatus(val arabicLabel: String) {
    MATCHED("مطابق"),
    UNCERTAIN("المطابقة غير مؤكدة"),
    DIFFERENT("مستند مخالف")
}

data class ReviewedDocument(
    val id: Int,
    val pageNumbers: List<Int>,
    val type: String,
    val rawText: String,
    val caseNo: String?,
    val caseYear: String?,
    val court: String?,
    val primaryPartyNames: List<String>,
    val status: DocumentMatchStatus,
    val reason: String
)

object DocumentReviewParser {
    private val startMarker = Regex("(?m)^\\s*\\[\\[DOCUMENT\\s+([0-9]+)]]\\s*$", RegexOption.IGNORE_CASE)
    private val endMarker = Regex("(?m)^\\s*\\[\\[END DOCUMENT]]\\s*$", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<ReviewedDocument> {
        val blocks = parseBlocks(text)
        if (blocks.isEmpty()) return emptyList()
        val provisional = blocks.mapIndexed { index, pair ->
            val (declaredId, raw) = pair
            val parsed = PetitionIntakeParser.parse(raw)
            ReviewedDocument(
                id = index + 1,
                pageNumbers = parsePages(raw).ifEmpty { listOf(index + 1) },
                type = captureLine(raw, "نوع المستند") ?: "مستند غير معروف",
                rawText = raw.trim(),
                caseNo = parsed.caseNo,
                caseYear = parsed.caseYear,
                court = parsed.court,
                primaryPartyNames = parsed.parties.map { it.name }.filter(String::isNotBlank),
                status = DocumentMatchStatus.UNCERTAIN,
                reason = "لم تُحسب المطابقة بعد"
            )
        }
        val primary = provisional.firstOrNull { it.type.contains("عريضة") } ?: provisional.first()
        return provisional.map { document ->
            if (document.id == primary.id) {
                document.copy(status = DocumentMatchStatus.UNCERTAIN, reason = "مستند مرجعي؛ راجع بياناته واعتمدها")
            } else {
                val (status, reason) = compare(primary, document)
                document.copy(status = status, reason = reason)
            }
        }
    }

    fun matchingGroup(documents: List<ReviewedDocument>, selected: ReviewedDocument): List<ReviewedDocument> {
        val group = mutableListOf(selected)
        documents.filter { it.id != selected.id }.forEach { candidate ->
            if (group.all { compare(it, candidate).first == DocumentMatchStatus.MATCHED }) group += candidate
        }
        return group
    }

    fun combinedText(documents: List<ReviewedDocument>): String {
        val parsed = documents.map { it to PetitionIntakeParser.parse(it.rawText) }
        val base = parsed.firstOrNull { !it.second.caseNo.isNullOrBlank() }?.second ?: parsed.first().second
        fun isCounterclaim(doc: ReviewedDocument) = doc.type.contains("فرعية") || doc.type.contains("طلب عارض")
        val petitions = parsed.filter { it.first.type.contains("عريضة") || it.first.type.contains("صحيفة") || isCounterclaim(it.first) }
        val judgments = parsed.filter { it.first.type.contains("حكم") }
        val originalSubject = petitions.filterNot { isCounterclaim(it.first) }.mapNotNull { (_, data) ->
            data.subjectOfCase?.let { IntakeNarrative.subject(it, data.finalRequests) }
        }.joinToString("\n\n")
        val counterSubjects = petitions.filter { isCounterclaim(it.first) }.map { (doc, data) ->
            IntakeNarrative.counterclaim(data.parties.filter { it.role.isPlaintiff }.map { it.name },
                data.finalRequests, doc.type.contains("طلب عارض"), data.subjectOfCase)
        }.distinct()
        fun evidence(label: String): String? = judgments.mapNotNull { captureLine(it.first.rawText, label) }
            .firstOrNull { it !in listOf("غير مذكور", "غير موجود", "لا", "...", "[غير واضح]") }
        val history = IntakeNarrative.returnedHistory(evidence("دليل إعادة الدعوى"),
            evidence("دليل التقرير السابق"), evidence("دليل تداول الدعوى"))
        val subject = (listOf(originalSubject) + counterSubjects + history).filter(String::isNotBlank).joinToString("\n\n")
        val mission = judgments.mapNotNull { (_, data) -> data.preliminaryMission?.let {
            IntakeNarrative.mission(it, data.preliminaryJudgmentDate)
        } }.joinToString("\n\n")
        return buildString {
            appendLine("رقم الدعوى: ${base.caseNo.orEmpty()}")
            appendLine("سنة الدعوى: ${base.caseYear.orEmpty()}")
            appendLine("المحكمة: ${base.court.orEmpty()}")
            appendLine("نوع الدعوى: ${base.caseType.orEmpty()}")
            parsed.forEach { (_, data) ->
                data.incomingNo?.let { appendLine("رقم الوارد: $it") }
                data.incomingDate?.let { appendLine("تاريخ الوارد: $it") }
                data.receiptDate?.let { appendLine("تاريخ استلام القضية: $it") }
                data.preliminaryJudgmentDate?.let { appendLine("تاريخ الحكم التمهيدي: $it") }
            }
            parsed.flatMap { (doc, data) -> data.parties.map { party ->
                if (isCounterclaim(doc)) party.copy(claimKind = if (doc.type.contains("طلب عارض")) "طلب عارض" else "فرعية") else party
            } }.distinct().forEach { party ->
                appendLine("الخصم: ${party.name} | العنوان: ${party.address} | الصفة: ${party.role.arabicLabel}${if (party.withCapacity) " بصفته" else ""} | الدعوى: ${party.claimKind}")
            }
            appendLine("موضوع الدعوى: $subject")
            appendLine("الطلبات الختامية: " + petitions.mapNotNull { it.second.finalRequests }.joinToString("\n"))
            appendLine("مأمورية الحكم التمهيدي: $mission")
            appendLine("ملاحظات: " + parsed.mapNotNull { it.second.notes }.joinToString("\n"))
        }
    }

    private fun parseBlocks(text: String): List<Pair<Int, String>> {
        val starts = startMarker.findAll(text).toList()
        if (starts.isEmpty()) return if (text.isBlank()) emptyList() else listOf(1 to text.trim())
        return starts.mapIndexedNotNull { index, marker ->
            val endLimit = starts.getOrNull(index + 1)?.range?.first ?: text.length
            val bodyStart = marker.range.last + 1
            val raw = text.substring(bodyStart, endLimit).replace(endMarker, "").trim()
            if (raw.isBlank()) null else marker.groupValues[1].toIntOrNull().orEmptyId(index) to raw
        }
    }

    private fun Int?.orEmptyId(index: Int): Int = this ?: index + 1

    private fun parsePages(text: String): List<Int> {
        val line = captureLine(text, "الصفحات") ?: return emptyList()
        return Regex("[0-9٠-٩]+").findAll(line).mapNotNull { it.value.toWesternDigits().toIntOrNull() }.distinct().toList()
    }

    private fun captureLine(text: String, label: String): String? = Regex(
        "(?m)^[ \\t]*${Regex.escape(label)}[ \\t]*[:：][ \\t]*([^\\r\\n]+?)[ \\t]*$",
        RegexOption.IGNORE_CASE
    ).find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)

    fun compare(primary: ReviewedDocument, other: ReviewedDocument): Pair<DocumentMatchStatus, String> {
        if (!primary.caseNo.isNullOrBlank() && !other.caseNo.isNullOrBlank() && normalize(primary.caseNo) != normalize(other.caseNo)) {
            return DocumentMatchStatus.DIFFERENT to "رقم الدعوى مختلف: ${primary.caseNo} مقابل ${other.caseNo}"
        }
        if (!primary.caseYear.isNullOrBlank() && !other.caseYear.isNullOrBlank() && normalize(primary.caseYear) != normalize(other.caseYear)) {
            return DocumentMatchStatus.DIFFERENT to "سنة الدعوى مختلفة: ${primary.caseYear} مقابل ${other.caseYear}"
        }
        val sameCaseIdentity = !primary.caseNo.isNullOrBlank() && !other.caseNo.isNullOrBlank() &&
            normalize(primary.caseNo) == normalize(other.caseNo) &&
            !primary.caseYear.isNullOrBlank() && !other.caseYear.isNullOrBlank() &&
            normalize(primary.caseYear) == normalize(other.caseYear)
        if (sameCaseIdentity) {
            val courtMatches = !primary.court.isNullOrBlank() && !other.court.isNullOrBlank() && normalize(primary.court) == normalize(other.court)
            return if (courtMatches) DocumentMatchStatus.MATCHED to "رقم الدعوى والسنة متطابقان"
            else DocumentMatchStatus.UNCERTAIN to "رقم الدعوى والسنة متطابقان لكن المحكمة ناقصة أو مختلفة؛ تحتاج مراجعة"
        }
        val primaryNames = primary.primaryPartyNames.map(::normalize).filter(String::isNotBlank).toSet()
        val otherNames = other.primaryPartyNames.map(::normalize).filter(String::isNotBlank).toSet()
        val sharedNames = primaryNames.intersect(otherNames)
        return if (sharedNames.isNotEmpty()) {
            DocumentMatchStatus.UNCERTAIN to "يوجد خصوم مشتركون لكن رقم الدعوى أو السنة غير مكتمل"
        } else {
            DocumentMatchStatus.UNCERTAIN to "البيانات غير كافية لإثبات المطابقة؛ يلزم قرار المستخدم"
        }
    }

    private fun normalize(value: String): String = value.toWesternDigits().trim().lowercase().replace(Regex("\\s+"), " ")

    private fun String.toWesternDigits(): String = map { char ->
        when (char) {
            in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
            else -> char
        }
    }.joinToString("")
}
