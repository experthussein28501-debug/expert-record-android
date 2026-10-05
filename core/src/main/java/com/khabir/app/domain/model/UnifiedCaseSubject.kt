package com.khabir.app.domain.model

object UnifiedCaseSubject {
    fun compose(explanation: String, requests: String, requestsFirst: Boolean = true): String {
        val body = explanation.trim()
        val relief = PetitionSubjectExtraction.cleanRequests(requests)
        if (relief.isBlank()) return body
        // Reviewed/manual subjects are authoritative, including legacy wording and later procedures.
        if (body.startsWith("أقام المدعي") && body.contains("طلب في ختامها")) return body
        return buildString {
            append("أقام المدعي دعواه بموجب صحيفة معلنة قانونًا")
            if (requestsFirst) {
                append("، وطلب في ختامها:\n").append(relief)
                if (body.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(body)
            } else {
                if (body.isNotBlank()) append("، وحيث قال شارحًا دعواه:\n").append(body)
                append("\n\nوطلب في ختامها:\n").append(relief)
            }
        }
    }
}
