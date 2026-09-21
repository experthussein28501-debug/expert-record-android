package com.khabir.app.presentation.reports

enum class FinalRequestsPlacement {
    START,
    END
}

/** Builds the report's "الموضوع" section from the reviewed extraction of a petition. */
internal object CaseSubjectFormatter {
    private const val PREFIX = "أقام المدعي دعواه بموجب صحيفة أودعت قلم المحكمة ومعلنة قانونًا"
    private const val REQUEST_MARKER = "وطلب في ختامها:"
    private const val LEGACY_REQUEST_MARKER = "طلب في ختامها:"
    private const val EXPLANATION_MARKER = "وحيث قال شارحًا دعواه:"
    private const val CLOSING = "مما حدا به إلى إقامة الدعوى الماثلة"

    fun format(
        extracted: String,
        placement: FinalRequestsPlacement = FinalRequestsPlacement.START
    ): String {
        val normalized = extracted.replace("\r\n", "\n").trim()
        val labelledRequests = sectionBodies(normalized, "الطلبات(?: الختامية)?")
        val rawRequests = extractRawFinalRequests(normalized)
        val requests = dedupeParagraphs(labelledRequests.ifBlank { rawRequests })

        val labelledExplanation = sectionBodies(normalized, "شرح الدعوى|وقائع الدعوى|الشرح")
        val rawExplanation = extractRawExplanation(normalized)
        val explanation = cleanExplanation(
            labelledExplanation.ifBlank { rawExplanation },
            requests
        )
        if (requests.isBlank() && explanation.isBlank()) return ""

        return compose(requests, explanation, placement)
    }

    fun reorderFormatted(
        current: String,
        placement: FinalRequestsPlacement
    ): String {
        val normalized = current.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return normalized

        val requestMarker = listOf(REQUEST_MARKER, LEGACY_REQUEST_MARKER)
            .map { it to normalized.indexOf(it) }
            .filter { it.second >= 0 }
            .minByOrNull { it.second } ?: return normalized
        val reqIndex = requestMarker.second
        val expIndex = normalized.indexOf(EXPLANATION_MARKER)
        if (expIndex < 0) return normalized

        val requests = if (reqIndex < expIndex) {
            normalized.substring(reqIndex + requestMarker.first.length, expIndex).trim()
        } else {
            normalized.substring(reqIndex + requestMarker.first.length).trim()
        }
        val explanation = if (expIndex < reqIndex) {
            normalized.substring(expIndex + EXPLANATION_MARKER.length, reqIndex).trim()
        } else {
            normalized.substring(expIndex + EXPLANATION_MARKER.length).trim()
        }

        return compose(dedupeParagraphs(requests), cleanExplanation(explanation, requests), placement)
    }

    private fun compose(
        requests: String,
        explanation: String,
        placement: FinalRequestsPlacement
    ): String = buildString {
        append(PREFIX)
        val finalExplanation = ensureClosing(explanation)
        when (placement) {
            FinalRequestsPlacement.START -> {
                if (requests.isNotBlank()) append("، ").append(REQUEST_MARKER).append("\n").append(requests)
                if (finalExplanation.isNotBlank()) append("\n\n").append(EXPLANATION_MARKER).append("\n").append(finalExplanation)
            }
            FinalRequestsPlacement.END -> {
                if (finalExplanation.isNotBlank()) append("، ").append(EXPLANATION_MARKER).append("\n").append(finalExplanation)
                if (requests.isNotBlank()) append("\n\n").append(REQUEST_MARKER).append("\n").append(requests)
            }
        }
    }

    /** Prefer the petition body after "الموضوع/وأعلنته بالآتي" and stop before "بناء عليه". */
    private fun extractRawExplanation(text: String): String {
        val match = Regex(
            "(?ims)(?:^|\\n)\\s*(?:الموضوع|وأعلنته بالآتي|الوقائع)\\s*[:：-]?\\s*(.*?)(?=بناء\\s*عليه|\\z)"
        ).find(text)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    /** In unlabelled petitions, the final relief commonly follows "بناء عليه". */
    private fun extractRawFinalRequests(text: String): String {
        val after = Regex("(?ims)بناء\\s*عليه\\s*[:：-]?\\s*(.+)$")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (after.isBlank()) return ""
        return after
            .replace(Regex("(?ims)^\\s*أنا\\s+المحضر.*?(?=(?:ولأجل العلم|لذلك|الطلبات|طلب))"), "")
            .replace(Regex("(?ims)^\\s*(?:الطلبات(?: الختامية)?|لذلك|وطلب|طلب)\\s*[:：-]?\\s*"), "")
            .trim()
    }

    private fun cleanExplanation(explanation: String, requests: String): String {
        var result = trimAtBinaaAlaih(explanation)
        if (requests.isNotBlank()) {
            val normalizedRequest = normalizeForComparison(requests)
            result = result
                .split(Regex("\\n\\s*\\n|(?<=[.!؟؛])\\s+(?=[^\\s])"))
                .map(String::trim)
                .filter(String::isNotBlank)
                .filterNot { paragraph ->
                    val normalizedParagraph = normalizeForComparison(paragraph)
                    normalizedParagraph == normalizedRequest ||
                        (normalizedRequest.length >= 24 && normalizedParagraph.contains(normalizedRequest))
                }
                .joinToString("\n\n")
                .replace(Regex("(?ims)\\s*(?:الطلبات(?: الختامية)?|وطلب في ختامها|طلب في ختامها)\\s*[:：-]?\\s*$"), "")
        }
        return dedupeParagraphs(result)
    }

    private fun ensureClosing(explanation: String): String {
        val clean = explanation.trim().trimEnd(' ', '،', '.', '؛')
        if (clean.isBlank()) return ""
        return if (normalizeForComparison(clean).contains(normalizeForComparison(CLOSING))) {
            clean + if (clean.endsWith(".")) "" else "."
        } else {
            "$clean، $CLOSING."
        }
    }

    private fun normalizeForComparison(text: String): String = text
        .replace(Regex("[\\u064B-\\u065F]"), "")
        .replace("ـ", "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun trimAtBinaaAlaih(text: String): String {
        val marker = Regex("(?i)بناء\\s*عليه").find(text)
        return if (marker == null) text.trim() else text.substring(0, marker.range.first).trim()
    }

    private fun dedupeParagraphs(text: String): String {
        val seen = linkedSetOf<String>()
        return text
            .split(Regex("\\n\\s*\\n|(?<=[.!؟؛])\\s+(?=[^\\s])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { seen.add(it.replace(Regex("\\s+"), " ")) }
            .joinToString("\n\n")
            .trim()
    }

    /** Collects repeated labelled sections when a long document was analyzed in several batches. */
    private fun sectionBodies(text: String, labelPattern: String): String =
        Regex("(?ims)^\\s*(?:$labelPattern)\\s*[:：]\\s*(.*?)(?=^\\s*(?:الطلبات(?: الختامية)?|شرح الدعوى|وقائع الدعوى|الشرح)\\s*[:：]|\\z)")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}
