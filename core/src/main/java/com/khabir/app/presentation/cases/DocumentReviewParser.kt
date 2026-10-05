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
    val reason: String,
    val record: com.khabir.app.domain.model.LegalDocumentRecord? = null
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
                id = declaredId.takeIf { id -> blocks.count { it.first == id } == 1 } ?: index + 1,
                pageNumbers = parsePages(raw).ifEmpty { listOf(index + 1) },
                type = captureLine(raw, "نوع المستند") ?: "مستند غير معروف",
                rawText = raw.trim(),
                caseNo = parsed.caseNo,
                caseYear = parsed.caseYear,
                court = parsed.court,
                primaryPartyNames = parsed.parties.map { it.name }.filter(String::isNotBlank),
                status = DocumentMatchStatus.UNCERTAIN,
                reason = "لم تُحسب المطابقة بعد",
                record = LegalProcedureParser.parse(declaredId, parsePages(raw).ifEmpty { listOf(index + 1) },raw)
            )
        }
        val primary = provisional.firstOrNull { it.record?.type == com.khabir.app.domain.model.LegalProcedureType.ORIGINAL } ?: provisional.first()
        return provisional.map { document ->
            if (document.id == primary.id) {
                document.copy(status = DocumentMatchStatus.UNCERTAIN, reason = "مستند مرجعي؛ راجع بياناته واعتمدها")
            } else {
                val (status, reason) = compare(primary, document)
                document.copy(status = status, reason = reason)
            }
        }
    }

    fun normalizedExtraction(text:String):String =
        if(text.contains("[[DOCUMENT") || LegalProcedureParser.field(text,"نوع المستند").isNotBlank()) combinedText(parse(text)) else text

    fun matchingGroup(documents: List<ReviewedDocument>, selected: ReviewedDocument): List<ReviewedDocument> {
        val group = mutableListOf(selected)
        documents.filter { it.id != selected.id }.forEach { candidate ->
            if (group.all { compare(it, candidate).first == DocumentMatchStatus.MATCHED }) group += candidate
        }
        return group
    }

    fun combinedText(documents: List<ReviewedDocument>): String {
        if (documents.isEmpty()) return ""
        val parsed = documents.map { it to PetitionIntakeParser.parse(it.rawText) }
        val assembly = LegalCaseAssembly.assemble(documents.map { doc ->
            LegalProcedureParser.parse(doc.id,doc.pageNumbers,doc.rawText,doc.type)
        }, parsed.firstNotNullOfOrNull { it.second.caseType }.orEmpty(),
            parsed.flatMap { it.second.parties }.filter { it.role.isDefendant }.map { it.name })
        val identity = assembly.current
        val base = parsed.firstOrNull { it.second.caseNo == identity?.number && it.second.caseYear == identity?.year }?.second
            ?: PetitionIntakeParser.Result()
        fun isCounterclaim(doc: ReviewedDocument) = com.khabir.app.domain.model.LegalProcedureType.classify(doc.type).subsidiary
        val petitions = parsed.filter { com.khabir.app.domain.model.LegalProcedureType.classify(it.first.type).let { kind ->
            kind == com.khabir.app.domain.model.LegalProcedureType.ORIGINAL || kind.subsidiary } }
        val judgments = parsed.filter { it.first.type.contains("حكم") }
        fun evidence(label: String): String? = judgments.mapNotNull { captureLine(it.first.rawText, label) }
            .firstOrNull { it !in listOf("غير مذكور", "غير موجود", "لا", "...", "[غير واضح]") }
        val history = IntakeNarrative.returnedHistory(evidence("دليل إعادة الدعوى"),
            evidence("دليل التقرير السابق"), evidence("دليل تداول الدعوى"))
        val subject = listOf(assembly.subject,history).filter(String::isNotBlank).joinToString("\n\n")
        val mission = judgments.mapNotNull { (_, data) -> data.preliminaryMission?.let {
            IntakeNarrative.mission(it, data.preliminaryJudgmentDate)
        } }.joinToString("\n\n")
        return buildString {
            appendLine("رقم الدعوى: ${identity?.number.orEmpty()}")
            appendLine("سنة الدعوى: ${identity?.year.orEmpty()}")
            appendLine("المحكمة: ${identity?.court.orEmpty()}")
            appendLine("نوع الدعوى: ${identity?.type?.takeIf(String::isNotBlank) ?: base.caseType.orEmpty()}")
            parsed.forEach { (_, data) ->
                data.incomingNo?.let { appendLine("رقم الوارد: $it") }
                data.incomingDate?.let { appendLine("تاريخ الوارد: $it") }
                data.receiptDate?.let { appendLine("تاريخ استلام القضية: $it") }
                data.preliminaryJudgmentDate?.let { appendLine("تاريخ الحكم التمهيدي: $it") }
            }
            val mergedParties = linkedMapOf<String, PetitionIntakeParser.ParsedParty>()
            parsed.flatMap { (doc, data) -> data.parties.map { party ->
                if (isCounterclaim(doc)) party.copy(claimKind = com.khabir.app.domain.model.LegalProcedureType.classify(doc.type).label) else party
            } }.forEach { party ->
                val key = listOf(party.role.name, normalize(party.name), party.claimKind).joinToString("|")
                val existing = mergedParties[key]
                mergedParties[key] = when {
                    existing == null -> party
                    existing.address.isBlank() && party.address.isNotBlank() -> existing.copy(address = party.address)
                    party.address.length > existing.address.length -> existing.copy(address = party.address)
                    else -> existing
                }
            }
            mergedParties.values.forEach { party ->
                appendLine("الخصم: ${party.name} | العنوان: ${party.address} | الصفة: ${party.role.arabicLabel}${if (party.withCapacity) " بصفته" else ""} | الدعوى: ${party.claimKind}")
            }
            appendLine("موضوع الدعوى: $subject")
            // Each procedure already carries its requests inside the unified subject.
            appendLine("الطلبات الختامية:")
            appendLine("مأمورية الحكم التمهيدي: $mission")
            appendLine("ملاحظات: " + (parsed.mapNotNull { it.second.notes } + petitions.flatMap { (doc, data) ->
                data.subjectWarnings.map { "المستند ${doc.id}: $it" }
            } + assembly.warnings).distinct().joinToString("؛ "))
            appendLine("${com.khabir.app.domain.model.LegalSourceAudit.LABEL}: ${com.khabir.app.domain.model.LegalSourceAudit.encode(assembly.records)}")
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
        val a = LegalProcedureParser.parse(primary.id,primary.pageNumbers,primary.rawText,primary.type)
        val b = LegalProcedureParser.parse(other.id,other.pageNumbers,other.rawText,other.type)
        fun linked(ref: com.khabir.app.domain.model.LegalDocumentRecord, candidate: com.khabir.app.domain.model.LegalDocumentRecord): Boolean =
            ref.provenReferral && listOfNotNull(ref.previousIdentity,ref.currentIdentity).any { identity ->
                candidate.identity?.let(identity::matches) == true || candidate.currentIdentity?.let(identity::matches) == true }
        if(linked(a,b) || linked(b,a)) return DocumentMatchStatus.MATCHED to "ربط مثبت بحكم الإحالة بين هوية الدعوى السابقة والحالية؛ راجع النص المصدر"
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
        val primaryParsed = PetitionIntakeParser.parse(primary.rawText)
        val otherParsed = PetitionIntakeParser.parse(other.rawText)
        val primaryNames = primaryParsed.parties.map { normalize(it.name) }.filter(String::isNotBlank).toSet()
        val otherNames = otherParsed.parties.map { normalize(it.name) }.filter(String::isNotBlank).toSet()
        val primaryAddresses = primaryParsed.parties.map { normalize(it.address) }.filter(String::isNotBlank).toSet()
        val otherAddresses = otherParsed.parties.map { normalize(it.address) }.filter(String::isNotBlank).toSet()

        fun overlap(left: Set<String>, right: Set<String>): Int {
            if (left.isEmpty() || right.isEmpty()) return 0
            val common = left.intersect(right).size
            return ((common.toDouble() / maxOf(left.size, right.size)) * 100.0).toInt()
        }

        val nameScore = overlap(primaryNames, otherNames)
        val addressScore = overlap(primaryAddresses, otherAddresses)
        val confidence = (nameScore * 0.8 + addressScore * 0.2).toInt().coerceIn(0, 100)
        return when {
            primaryAddresses.isNotEmpty() && otherAddresses.isNotEmpty() && addressScore == 0 ->
                DocumentMatchStatus.UNCERTAIN to "الأسماء متشابهة لكن العناوين مختلفة؛ راجع يدويًا"
            confidence >= 70 -> DocumentMatchStatus.UNCERTAIN to "الخصوم والعناوين متشابهة لكن رقم الدعوى ناقص؛ التشابه لا يثبت الربط ويلزم قرار المستخدم"
            nameScore > 0 -> DocumentMatchStatus.UNCERTAIN to "يوجد تشابه في الخصوم؛ راجع المستندين قبل الدمج"
            else -> DocumentMatchStatus.UNCERTAIN to "البيانات غير كافية لإثبات المطابقة؛ يلزم قرار المستخدم"
        }
    }

    private fun normalize(value: String): String = value.toWesternDigits().trim().lowercase()
        .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        .replace(Regex("[أإآ]"), "ا").replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun String.toWesternDigits(): String = map { char ->
        when (char) {
            in '٠'..'٩' -> ('0'.code + (char.code - '٠'.code)).toChar()
            else -> char
        }
    }.joinToString("")
}
