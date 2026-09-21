package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.PartyRole
import java.text.Normalizer
import java.time.LocalDate

/**
 * Parser shared by camera OCR and Arabic voice intake.
 * It deliberately returns candidates for review instead of saving anything directly.
 */
object PetitionIntakeParser {
    data class ParsedParty(
        val role: PartyRole,
        val withCapacity: Boolean,
        val name: String,
        val address: String = "",
        val claimKind: String = "أصلية"
    )

    data class Result(
        val incomingNo: String? = null,
        val incomingDate: LocalDate? = null,
        val caseNo: String? = null,
        val caseYear: String? = null,
        val court: String? = null,
        val caseType: String? = null,
        val receiptDate: LocalDate? = null,
        val preliminaryJudgmentDate: LocalDate? = null,
        val subjectOfCase: String? = null,
        val finalRequests: String? = null,
        val preliminaryMission: String? = null,
        val parties: List<ParsedParty> = emptyList(),
        val lawyerContact: String? = null,
        val notes: String? = null
    )

    private data class CaseHeading(
        val caseType: String? = null,
        val court: String? = null
    )

    private val knownCaseTypes = listOf(
        "مدني مستأنف", "استئناف عالي", "قضاء إداري", "قضاء اداري", "مدني كلي", "مدني جزئي",
        "شؤون الأسرة", "شئون الأسرة", "أحوال شخصية", "جنح", "عمال", "تنفيذ", "مال"
    )

    private const val ADDRESS_LABEL =
        "(?:عنوانها|عنوانه|العنوان|المقيم(?:ة|ين|ون)?(?:\\s+(?:في|فى|ب|بـ))?|المقيم(?:ة|ين|ون)?\\s+بناحية|" +
        "موطنه|موطنها|مقره|مقرها)"

    fun parse(raw: String): Result {
        val originalText = normalize(raw)
        val lawyerContact = Regex("(?:و?محل(?:هم|ها|ه) المختار)[^\\n]*?(?=ضد|أنا المحضر|انا المحضر|\\n|$)(?:\\n[^\\n]*المحامي[^\\n]*)?").find(originalText)?.value
        val text = originalText.replace(Regex("(?:و?محل(?:هم|ها|ه) المختار)[^\\n]*?(?=ضد|أنا المحضر|انا المحضر|\\n|$)"), "")
            .replace(Regex("مخاطب[ًااً]*\\s+مع[^\\n]*"), "")
        if (text.isBlank()) return Result(parties = LawyerIntakeParser.parse(originalText), lawyerContact = lawyerContact)

        val incomingNo = captureNumber(text, "رقم الوارد", "الوارد رقم", "الوارد")
        val judgmentIdentity = JudgmentCaseIdentity.parse(text)
        val caseNo = captureNumber(text, "رقم الدعوى", "رقم الدعوي", "الدعوى رقم", "الدعوي رقم", "رقم القضية", "القضية رقم")
        val caseYear = captureYear(text)
        val heading = parseCaseHeading(text)
        val explicitCourt = captureValue(text, "المحكمة", "أمام محكمة", "محكمة")?.cleanField()
        val explicitType = captureValue(text, "نوع الدعوى", "نوع القضية", "النوع")?.cleanField()
        val inferredType = inferCaseType(text)

        val incomingDate = captureDate(text, "تاريخ الوارد", "تاريخ الإحالة", "تاريخ الاحالة", "أحيلت بتاريخ", "احيلت بتاريخ")
        val receiptDate = captureDate(text, "تاريخ الاستلام", "استلمت بتاريخ", "تاريخ استلام", "تاريخ استلام القضية")
        val preliminaryDate = captureDate(text, "تاريخ الحكم التمهيدي", "الحكم التمهيدي بتاريخ", "جلسة الحكم التمهيدي", "حكم الإحالة بجلسة", "بجلسة")
        val subjectOfCase = captureLegalSection(
            text = text,
            starts = listOf("موضوع الدعوى", "موضوع القضية", "الموضوع", "وأعلنته بالآتي", "واعلنته بالاتي"),
            stops = listOf("الطلبات الختامية", "الطلبات", "بناء عليه", "بناءً عليه", "المأمورية", "مأمورية الحكم التمهيدي")
        )
        val finalRequests = captureLegalSection(
            text = text,
            starts = listOf("الطلبات الختامية", "الطلبات", "لذلك يلتمس", "بناء عليه", "بناءً عليه"),
            stops = listOf("مأمورية الحكم التمهيدي", "المأمورية", "وتفضلوا", "تحريراً", "تحريرا")
        )
        val preliminaryMission = captureLegalSection(
            text = text,
            starts = listOf("مأمورية الحكم التمهيدي", "المأمورية", "تكون مهمته", "تكون مهمتها"),
            stops = listOf("مباشرة المأمورية", "النتيجة النهائية")
        )?.let { if (it.startsWith("يقضي حكم الإحالة") || it.startsWith("قضى حكم الإحالة")) it else IntakeNarrative.missionBody(it) ?: it } ?: IntakeNarrative.missionBody(text) ?: captureJudgmentMission(text)

        val plaintiffLabels = listOf(
            "المرفوعة من", "المرفوعه من", "المقامة من", "المقامه من", "مقامة من", "مرفوعة من",
            "المدعي", "المدعية", "المدعون", "المدعيون", "المدعيات", "بناء على طلب", "بناءً على طلب", "بناءا على طلب", "الطالب", "الطالبة", "المستأنف", "الطاعن"
        )
        val defendantLabels = listOf(
            "ضد", "ضــــد", "ضـد", "المدعى عليه", "المدعى عليهم", "المعلن إليه", "المعلن اليه", "المخاطبون", "المخاطبين", "حيث وجود كل من",
            "المستأنف ضده", "المطعون ضده"
        )
        val sectionStops = listOf(
                "الموضوع", "المأمورية", "مباشرة المأمورية", "الأقوال", "المعاينة", "بحث المستندات", "تحريراً", "تحريرا", "وتفضلوا", "أنا المحضر", "انتقلت في تاريخه",
            "النتيجة", "النتيجة النهائية", "الطلبات", "الوقائع", "بناء عليه", "لذلك", "المطعون ضده", "المستأنف ضده",
            "المخاطبون والعناوين", "المخاطبون", "المخاطبين", "العناوين"
        )

        val partyText = text.lines().filterNot { Regex("^(?:المحامي|المحامى|مخاطبة المحامي)\\s*:").containsMatchIn(it.trim()) }.joinToString("\n").substringBefore("موضوع الدعوى:").substringBefore("مأمورية الحكم التمهيدي:")
        val partyCandidates = mutableListOf<ParsedParty>()
        partyCandidates.addAll(parseExplicitPartyLines(partyText))
        partyCandidates.addAll(
            parseSideParties(partyText, plaintiffLabels, PartyRole.PLAINTIFF, defendantLabels + sectionStops)
        )
        partyCandidates.addAll(
            parseSideParties(partyText, defendantLabels, PartyRole.DEFENDANT, sectionStops)
        )
        val structured = parseStructuredParties(text)
        val serviceParties = parseServiceParties(text)
        val candidates = if (structured.isNotEmpty()) structured else
            partyCandidates.filterNot { it.role == PartyRole.DEFENDANT && serviceParties.isNotEmpty() } + serviceParties
        val mergedParties = linkedMapOf<String, ParsedParty>()
        (candidates + LawyerIntakeParser.parse(originalText)).forEach { party ->
            val key = listOf(party.role.name, normalizeName(party.name), party.claimKind).joinToString("|")
            val existing = mergedParties[key]
            mergedParties[key] = when {
                existing == null -> party
                existing.address.isBlank() && party.address.isNotBlank() ->
                    existing.copy(address = party.address, withCapacity = existing.withCapacity || party.withCapacity)
                party.address.length > existing.address.length ->
                    existing.copy(address = party.address, withCapacity = existing.withCapacity || party.withCapacity)
                existing.withCapacity || party.withCapacity -> existing.copy(withCapacity = true)
                else -> existing
            }
        }
        val parties = mergedParties.values.toList()
        val resolvedCaseType = judgmentIdentity?.type ?: explicitType ?: heading.caseType ?: inferredType
        val resolvedCaseYear = (caseYear ?: judgmentIdentity?.year)?.let { rawYear ->
            val cleaned = rawYear.trim()
            if (resolvedCaseType in setOf("استئناف عالي", "قضاء إداري", "قضاء اداري") && !cleaned.endsWith("ق")) "${cleaned}ق"
            else cleaned
        }

        return Result(
            incomingNo = incomingNo,
            incomingDate = incomingDate,
            caseNo = caseNo ?: judgmentIdentity?.number,
            caseYear = resolvedCaseYear,
            court = judgmentIdentity?.court ?: (explicitCourt ?: heading.court)?.substringBefore("الدائرة")?.substringBefore("الدائره")?.trim(),
            caseType = resolvedCaseType,
            receiptDate = receiptDate,
            preliminaryJudgmentDate = preliminaryDate,
            subjectOfCase = subjectOfCase,
            finalRequests = finalRequests,
            preliminaryMission = preliminaryMission,
            parties = parties,
            lawyerContact = lawyerContact ?: captureValue(text, "مخاطبة المحامي"),
            notes = captureValue(text, "ملاحظات", "ملاحظة")?.cleanField()
        )
    }

    private fun parseStructuredParties(text: String): List<ParsedParty> = text.lines().mapNotNull { line ->
        if (!line.trim().startsWith("الخصم:")) return@mapNotNull null
        val fields = line.substringAfter("الخصم:").split('|').map(String::trim)
        val name = fields.firstOrNull().orEmpty()
        if (name.isBlank()) return@mapNotNull null
        fun field(label: String) = fields.drop(1).firstOrNull { it.startsWith("$label:") }?.substringAfter(':')?.trim().orEmpty()
        val roleText = field("الصفة")
        ParsedParty(PartyRole.fromArabicLabel(roleText), roleText.contains("بصفته"), name,
            if (PartyRole.fromArabicLabel(roleText) == PartyRole.LAWYER) com.khabir.app.domain.model.LawyerNotification.address(field("العنوان")) else field("العنوان"), field("الدعوى").ifBlank { "أصلية" })
    }

    private fun parseServiceParties(text: String): List<ParsedParty> {
        val marker = Regex("(?:أنا|انا)\\s+[^\\n]*?المحضر[^\\n]*?(?:إلى|الى)\\s+ناحية\\s+([^\\n]+)")
        val matches = marker.findAll(text).toList()
        return matches.flatMapIndexed { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val block = text.substring(match.range.last + 1, end)
                .substringBefore("وأعلنته").substringBefore("الموضوع").substringBefore("بناء عليه")
            parseSideParties("ضد\n$block", listOf("ضد"), PartyRole.DEFENDANT, listOf("الوقائع", "الطلبات"))
                .map { it.copy(address = it.address.ifBlank { match.groupValues[1].trim() }) }
        }
    }

    private fun parseCaseHeading(text: String): CaseHeading {
        val match = Regex(
            "(?:^|\\n)\\s*(?:في|فى)?\\s*(?:الدعوى|الدعوي|القضية)\\s*رقم\\s*[0-9٠-٩]+\\s*(?:لسنة|لسنه|/)\\s*[0-9٠-٩]{2,4}\\s*([^\\n،؛]*)",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return CaseHeading()

        val descriptor = match.groupValues[1].trim().trim('：', ':', '-', '/', ' ')
        if (descriptor.isBlank()) return CaseHeading()
        val type = knownCaseTypes.firstOrNull { descriptor.startsWith(it, ignoreCase = true) }
            ?: knownCaseTypes.firstOrNull { descriptor.contains(it, ignoreCase = true) }
        if (type == null) return CaseHeading(court = descriptor.takeIf { it.isNotBlank() })

        val typeIndex = descriptor.indexOf(type, ignoreCase = true)
        val court = descriptor.removeRange(typeIndex, typeIndex + type.length)
            .trim().trim('：', ':', '-', '/', ' ')
            .removePrefix("محكمة ").trim().takeIf { it.isNotBlank() }
        return CaseHeading(caseType = type, court = court)
    }

    private fun parseExplicitPartyLines(text: String): List<ParsedParty> {
        val regex = Regex(
            "(?:^|\\n|،|؛)\\s*(المدعي(?:ة|ون|ين)?|المدعى عليه(?:ا|م)?|المدعى عليهم|الطالب(?:ة)?|بناء(?:ا|ً)? على طلب|المعلن إليه|المعلن اليه|المخاطبون|المخاطبين|المستأنف ضده|المستأنف(?:ة)?|المطعون ضده|الطاعن(?:ة)?|خصم)" +
                "(?:\\s+(بصفته|بصفتها))?\\s*[:：/\\-]?\\s*" +
                "([^\\n،؛]+?)(?:\\s+$ADDRESS_LABEL\\s*[:：/\\-]?\\s*([^\\n،؛]+))?(?=\\n|،|؛|$)",
            setOf(RegexOption.IGNORE_CASE)
        )
        return regex.findAll(text).mapNotNull { match ->
            val label = match.groupValues[1]
            val name = cleanPartyName(match.groupValues[3])
            if (name.isBlank()) return@mapNotNull null
            if (label.startsWith("المخاطب", ignoreCase = true) && name.removePrefix("و").trim() in setOf("العناوين", "العنوان")) {
                return@mapNotNull null
            }
            val role = when {
                label.contains("مدعى عليه") || label.contains("معلن") || label.contains("ضده") || label.contains("مطعون") -> PartyRole.DEFENDANT
                label.contains("مدعي") || label.contains("طالب") || label.contains("مستأنف") || label.contains("طاعن") || label.contains("بناء") -> PartyRole.PLAINTIFF
                else -> PartyRole.OTHER
            }
            ParsedParty(
                role = role,
                withCapacity = match.groupValues[2].isNotBlank() || containsCapacity(name),
                name = cleanPartyName(stripCapacity(name)),
                address = cleanAddress(match.groupValues.getOrNull(4).orEmpty())
            )
        }.toList()
    }

    private fun parseSideParties(
        text: String,
        labels: List<String>,
        role: PartyRole,
        stopLabels: List<String>
    ): List<ParsedParty> {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val marker = Regex("(?:$labelPattern)\\s*[:：/\\-]?\\s*", RegexOption.IGNORE_CASE)
            .find(text) ?: return emptyList()

        val tail = text.substring(marker.range.last + 1)
        val stopPattern = stopLabels.joinToString("|") { Regex.escape(it) }
        val stop = if (stopPattern.isBlank()) null else Regex(
            "(?:$stopPattern)\\s*[:：/\\-]?",
            RegexOption.IGNORE_CASE
        ).find(tail)
        val block = if (stop == null) tail else tail.substring(0, stop.range.first)

        val result = mutableListOf<ParsedParty>()
        block.replace('؛', '\n')
            .replace('،', '\n')
            .replace(Regex("(?<![0-9٠-٩])(?=[0-9٠-٩]+\\s*[.)/\\-])"), "\n")
            .lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { originalLine ->
            val line = originalLine
                .replace(Regex("^[\\-–—•*]+\\s*"), "")
                .replace(Regex("^[0-9٠-٩]+\\s*[.)\\-/ـ]*\\s*"), "")
                .trim()
            if (line.isBlank() || line == "وآخرين" || line == "واخرين") return@forEach

            val addressOnly = Regex(
                "^$ADDRESS_LABEL\\s*[:：/\\-]?\\s*(.+)$",
                RegexOption.IGNORE_CASE
            ).find(line)
            if (addressOnly != null && result.isNotEmpty()) {
                val sharedAddress = cleanAddress(addressOnly.groupValues[1])
                result.indices.forEach { index ->
                    if (result[index].address.isBlank()) result[index] = result[index].copy(address = sharedAddress)
                }
                return@forEach
            }

            val split = Regex(
                "\\s+$ADDRESS_LABEL\\s*[:：/\\-]?\\s*",
                RegexOption.IGNORE_CASE
            ).split(line, limit = 2)
            val rawName = cleanPartyName(split.firstOrNull().orEmpty())
            if (rawName.isBlank() || looksLikeSectionHeading(rawName)) return@forEach

            if (Regex("المقيم(?:ون|ين)").containsMatchIn(line) && split.size > 1) {
                val sharedAddress = cleanAddress(split[1])
                result.indices.forEach { index ->
                    if (result[index].address.isBlank()) result[index] = result[index].copy(address = sharedAddress)
                }
            }
            result += ParsedParty(
                role = role,
                withCapacity = containsCapacity(rawName),
                name = cleanPartyName(stripCapacity(rawName)),
                address = cleanAddress(split.getOrNull(1).orEmpty())
            )
        }
        return result
    }

    private fun captureNumber(text: String, vararg labels: String): String? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        return Regex("(?:$labelPattern)\\s*[:：/\\-]?\\s*([0-9٠-٩]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun captureYear(text: String): String? {
        fun value(match: MatchResult?): String? {
            val found = match ?: return null
            val year = found.groupValues.getOrNull(1)?.trim().orEmpty()
            if (year.isBlank()) return null
            val suffix = found.groupValues.getOrNull(2)?.trim().orEmpty()
            return year + if (suffix.isNotBlank()) "ق" else ""
        }
        value(Regex("(?:سنة الدعوى|لسنة|لسنه|سنة|السنة)\\s*[:：/\\-]?\\s*([0-9٠-٩]{2,4})\\s*(ق)?", RegexOption.IGNORE_CASE).find(text))?.let { return it }
        return value(Regex("(?:رقم الدعوى|رقم الدعوي|الدعوى رقم|الدعوي رقم|رقم القضية|القضية رقم)\\s*[0-9٠-٩]+\\s*(?:/|لسنة|لسنه)\\s*([0-9٠-٩]{2,4})\\s*(ق)?", RegexOption.IGNORE_CASE).find(text))
    }

    private fun captureValue(text: String, vararg labels: String): String? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        return Regex("(?:$labelPattern)[ \\t]*[:：/\\-]?[ \\t]*([^\\n،؛]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun captureLegalSection(text: String, starts: List<String>, stops: List<String>): String? {
        val startPattern = starts.joinToString("|") { Regex.escape(it) }
        val start = Regex("(?:^|\\n)(?:$startPattern)[ \\t]*[:：/\\-]?[ \\t]*", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val tail = text.substring(start.range.last + 1)
        if (tail.isBlank()) return null
        val stopPattern = (stops + listOf("الخصم:", "المدعي:", "المدعى عليه:", "ملاحظات:", "مخاطبة المحامي:", "تاريخ الحكم التمهيدي:", "رقم الدعوى:", "نوع المستند:", "دليل إعادة الدعوى:", "دليل التقرير السابق:", "دليل تداول الدعوى:")).joinToString("|") { Regex.escape(it) }
        val stop = Regex("(?:^|\\n)(?:$stopPattern)\\s*[:：/\\-]?", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .find(tail)
        return (if (stop == null) tail else tail.substring(0, stop.range.first))
            .trim().takeIf { it.length >= 8 }
    }

    private fun captureJudgmentMission(text: String): String? {
        val start = Regex(
            "(?:حكمت المحكمة|وقبل الفصل في الموضوع)[^\\n]{0,180}(?:بندب|يندب)\\s+(?:خبير|مكتب خبراء|أحد خبراء)",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null
        val mission = text.substring(start.range.first).trim()
        val inclusiveEnd = Regex(
            "(?:و?تحقيق\\s+كافة\\s+عناصر\\s+الدعوى|بذات\\s+الأمانة\\s+السابقة|بأمانة\\s+تكميلية)",
            RegexOption.IGNORE_CASE
        ).find(mission)
        val exclusiveEnd = Regex(
            "(?:وألزمت|والزمت|قدرت\\s+أمانة|وحددت\\s+جلسة|أمانة\\s+[0-9٠-٩])",
            RegexOption.IGNORE_CASE
        ).find(mission)
        val bounded = when {
            inclusiveEnd != null && (exclusiveEnd == null || inclusiveEnd.range.first < exclusiveEnd.range.first) ->
                mission.substring(0, inclusiveEnd.range.last + 1).trim()
            exclusiveEnd != null -> mission.substring(0, exclusiveEnd.range.first).trim()
            else -> mission
        }
        return bounded.takeIf { it.length >= 20 }
    }

    private fun captureDate(text: String, vararg labels: String): LocalDate? {
        val labelPattern = labels.joinToString("|") { Regex.escape(it) }
        val value = Regex("(?:$labelPattern)\\s*[:：/\\-]?\\s*([0-9٠-٩]{1,4}[./\\-][0-9٠-٩]{1,2}[./\\-][0-9٠-٩]{1,4})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.getOrNull(1) ?: return null
        return parseDate(value)
    }

    private fun parseDate(value: String): LocalDate? {
        val western = value.map { ch ->
            when (ch) {
                '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                else -> ch
            }
        }.joinToString("").replace('.', '/').replace('-', '/')
        val parts = western.split('/').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3) return null
        val (a, b, c) = parts
        val year: Int
        val month: Int
        val day: Int
        if (a >= 1900) { year = a; month = b; day = c } else { day = a; month = b; year = c }
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    private fun inferCaseType(text: String): String? {
        if (
            text.contains("القضاء الإداري", ignoreCase = true) ||
            text.contains("القضاء الاداري", ignoreCase = true) ||
            text.contains("قضاء إداري", ignoreCase = true) ||
            text.contains("قضاء اداري", ignoreCase = true)
        ) {
            return "قضاء إداري"
        }
        if (Regex("(?:^|\\n)\\s*محكمة\\s+استئناف(?:\\s|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)).containsMatchIn(text)) {
            return "استئناف عالي"
        }
        return knownCaseTypes.firstOrNull { candidate -> text.contains(candidate, ignoreCase = true) }
    }

    private fun looksLikeSectionHeading(value: String): Boolean {
        val normalized = value.trim().trim(':', '：', '/', '-', ' ')
        return listOf(
            "الموضوع", "المأمورية", "مباشرة المأمورية", "الأقوال", "المعاينة", "بحث المستندات",
            "النتيجة", "النتيجة النهائية", "الطلبات", "الوقائع", "بناء عليه", "لذلك",
            "المخاطبون والعناوين", "المخاطبون", "المخاطبين", "العناوين"
        ).any { normalized.equals(it, ignoreCase = true) }
    }

    private fun normalize(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .replace('\u00A0', ' ')
        .replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\u2066-\\u2069\\uFEFF]"), "")
        .replace("ـ", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("[:：]{2,}"), ":")
        .trim()

    private fun String.cleanField(): String = trim().trim('：', ':', '-', '/', ' ')
    private fun containsCapacity(value: String) = Regex("بصفت(?:ها|ه)(?![\\p{L}])").containsMatchIn(value)
    private fun stripCapacity(value: String) = value.replace(Regex("\\s*بصفت(?:ها|ه)(?![\\p{L}])\\s*"), " ").trim()
    private fun normalizeName(value: String) = value.replace(Regex("\\s+"), " ").trim()
    private fun cleanAddress(value: String): String = value
        .trim().trim('：', ':', '-', '/', ' ')
        .replace(Regex("^(?:في|فى)\\s+", RegexOption.IGNORE_CASE), "")
        .trim()
    private fun cleanPartyName(value: String): String = value
        .replace(Regex("^(?:السيد(?:ة)?|السادة)\\s*/?\\s*"), "")
        .replace(Regex("\\s+(?:وآخرين|واخرين)$"), "")
        .trim(' ', '/', '-', ':', '：')
}
