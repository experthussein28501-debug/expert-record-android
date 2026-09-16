package com.khabir.app.presentation.reports

/** Builds the report's "الموضوع" section from the reviewed extraction of a petition. */
internal object CaseSubjectFormatter {
    fun format(extracted: String): String {
        val normalized = extracted.replace("\r\n", "\n").trim()
        val requests = sectionBodies(normalized, "الطلبات(?: الختامية)?")
        val explanation = sectionBodies(normalized, "شرح الدعوى|وقائع الدعوى|الشرح")
        return buildString {
            append("أقام المدعي دعواه بموجب صحيفة أودعت قلم كتاب المحكمة وأعلنت قانونًا")
            if (requests.isNotBlank()) append("، وطلب في ختامها:\n").append(requests)
            if (explanation.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(explanation)
            else if (requests.isBlank() && normalized.isNotBlank()) {
                append("، وحيث قال شارحًا دعواه:\n").append(normalized)
            }
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
