package com.khabir.app.domain.model

object UnifiedCaseSubject {
    fun compose(explanation: String, requests: String): String {
        val body = explanation.trim()
        val relief = requests.trim()
        if (relief.isBlank()) return body
        if (body.contains("وطلب في ختامها:") && body.contains(relief)) return body
        return buildString {
            append("أقام المدعي دعواه بموجب صحيفة أودعت قلم كتاب المحكمة وأعلنت قانونًا، وطلب في ختامها:\n")
            append(relief)
            if (body.isNotBlank()) append("\n\nوحيث قال شارحًا دعواه:\n").append(body)
        }
    }
}
