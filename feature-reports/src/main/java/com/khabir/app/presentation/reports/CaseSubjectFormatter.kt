package com.khabir.app.presentation.reports

internal enum class FinalRequestsPlacement {
    START,
    END
}

/** Builds the report's "الموضوع" section from the reviewed extraction of a petition. */
internal object CaseSubjectFormatter {
    fun format(
        extracted: String,
        placement: FinalRequestsPlacement = FinalRequestsPlacement.START
    ): String {
        val normalized = extracted.replace("\r\n", "\n").trim()
        val requests = sectionBodies(normalized, "الطلبات(?: الختامية)?")
        val explanation = sectionBodies(normalized, "شرح الدعوى|وقائع الدعوى|الشرح")
        return compose(requests, explanation, normalized, placement)
    }

    fun reorderFormatted(
        current: String,
        placement: FinalRequestsPlacement
    ): String {
        val normalized = current.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return normalized
        val prefix = "أقام المدعي دعواه بموجب صحيفة أودعت قلم كتاب المحكمة وأعلنت قانونًا"
        val requestsMarker = "وطلب في ختامها:"
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
        return compose(requests, explanation, "", placement, prefix)
    }

    private fun compose(
        requests: String,
        explanation: String,
        fallback: String,
        placement: FinalRequestsPlacement,
        prefix: String = "أقام المدعي دعواه بموجب صحيفة أودعت قلم كتاب المحكمة وأعلنت قانونًا"
    ): String = buildString {
        append(prefix)
        when (placement) {
            FinalRequestsPlacement.START -> {
                if (requests.isNotBlank()) append("، وطلب في ختامها:\n").append(requests)
                if (explanation.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(explanation)
            }
            FinalRequestsPlacement.END -> {
                if (explanation.isNotBlank()) append("، وحيث قال شارحًا دعواه:\n").append(explanation)
                if (requests.isNotBlank()) append("\n\nوطلب في ختامها:\n").append(requests)
            }
        }
        if (requests.isBlank() && explanation.isBlank() && fallback.isNotBlank()) {
            append("، وحيث قال شارحًا دعواه:\n").append(fallback)
        }
    }

    /** Collects repeated labelled sections when a long document was analyzed in several batches. */
    private fun sectionBodies(text: String, labelPattern: String): String =
        Regex("(?ims)^\\s*(?:$labelPattern)\\s*[:：]\\s*(.*?)(?=^\\s*(?:الطلبات(?: الختامية)?|شرح الدعوى|وقائع الدعوى|الشرح)\\s*[:：]|\\z)")
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}
