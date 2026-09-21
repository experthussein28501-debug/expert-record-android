package com.khabir.app.presentation.reports

enum class FinalRequestsPlacement {
    START,
    END
}

/** Builds the report's "الموضوع" section from the reviewed extraction of a petition. */
internal object CaseSubjectFormatter {
    private const val PREFIX = "أقام المدعي دعواه بموجب صحيفة معلنة للمدعى عليهم قانونًا"

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
            labelledExplanation.ifBlank { rawExplanation.ifBlank { normalized } },
            requests
        )

        return compose(requests, explanation, normalized, placement)
    }

    fun reorderFormatted(
        current: String,
        placement: FinalRequestsPlacement
    ): String {
        val normalized = current.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return normalized

        val requestsMarker = "طلب في ختامها:"
        val explanationMarker = "وحيث قال شارحًا دعواه:"
        val reqIndex = normalized.indexOf(requestsMarker)
        val expIndex = normalized.indexOf(explanationMarker)
        if (reqIndex < 0 || expIndex < 0) return normalized

        val requests = if (reqIndex < expIndex) {
            normalized.substring(reqIndex + requestsMarker.length, expIndex).trim()
        } else {
            normalized.substring(reqIndex + requestsMarker.length).trim()
        }
        val explanation = if (expIndex < reqIndex) {
            normalized.substring(expIndex + explanationMarker.length, reqIndex).trim()
        } else {
            normalized.substring(expIndex + explanationMarker.length).trim()
        }

        return compose(dedupeParagraphs(requests), cleanExplanation(explanation, requests), "", placement)
    }

    private fun compose(
        requests: String,
        explanation: String,
        fallback: String,
        placement: FinalRequestsPlacement
    ): String = buildString {
        append(PREFIX)
        when (placement) {
            FinalRequestsPlacement.START -> {
                if (requests.isNotBlank()) append(" طلب في ختامها:\n").append(requests)
                if (explanation.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(explanation)
            }
            FinalRequestsPlacement.END -> {
                if (explanation.isNotBlank()) append("، وحيث قال شارحًا دعواه:\n").append(explanation)
                if (requests.isNotBlank()) append("\n\nطلب في ختامها:\n").append(requests)
            }
        }
        if (requests.isBlank() && explanation.isBlank() && fallback.isNotBlank()) {
            append("، وحيث قال شارحًا دعواه:\n").append(trimAtBinaaAlaih(fallback))
        }
    }

    /** Prefer the petition body after "الموضوع/وأعلنته بالآتي" and stop before "بناء عليه". */
    private fun extractRawExplanation(text: String): String {
        val match = Regex(
            "(?ims)(?:^|\\n)\\s*(?:الموضوع|وأعلنته بالآتي|الوقائع)\\s*[:：-]?\\s*(.*?)(?=\\bبناء\\s*عليه\\b|\\z)"
        ).find(text)
        return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
    }

    /** In unlabelled petitions, the final relief commonly follows "بناء عليه". */
    private fun extractRawFinalRequests(text: String): String {
        val after = Regex("(?ims)\\bبناء\\s*عليه\\b\\s*[:：-]?\\s*(.+)$")
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
            val req = requests.trim()
            result = result
                .replace(req, "", ignoreCase = false)
                .replace(Regex("(?ims)\\s*(?:الطلبات(?: الختامية)?|وطلب في ختامها|طلب في ختامها)\\s*[:：-]?\\s*$"), "")
        }
        return dedupeParagraphs(result)
    }

    private fun trimAtBinaaAlaih(text: String): String {
        val marker = Regex("(?i)\\bبناء\\s*عليه\\b").find(text)
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
