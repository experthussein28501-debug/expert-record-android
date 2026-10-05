package com.khabir.app.presentation.reports

import com.khabir.app.domain.model.UnifiedCaseSubject
import com.khabir.app.domain.model.PetitionSubjectExtraction

enum class FinalRequestsPlacement { START, END }

/** Intake/report share extraction and wording; the existing placement choice remains supported. */
internal object CaseSubjectFormatter {
    fun format(extracted: String, placement: FinalRequestsPlacement = FinalRequestsPlacement.START): String {
        val normalized = extracted.replace("\r\n", "\n").trim()
        if(normalized.contains("[[DOCUMENT")) {
            val assembled = com.khabir.app.presentation.cases.DocumentReviewParser.combinedText(
                com.khabir.app.presentation.cases.DocumentReviewParser.parse(normalized))
            val subject = com.khabir.app.presentation.cases.PetitionIntakeParser.parse(assembled).subjectOfCase.orEmpty()
            return reorderFormatted(subject,placement)
        }
        val title = com.khabir.app.presentation.cases.LegalProcedureParser.field(normalized,"نوع المستند")
        val kind = com.khabir.app.domain.model.LegalProcedureType.classify(title)
        if(kind.subsidiary || kind in setOf(com.khabir.app.domain.model.LegalProcedureType.REFERRAL,com.khabir.app.domain.model.LegalProcedureType.APPEAL)) {
            return com.khabir.app.presentation.cases.LegalCaseAssembly.assemble(listOf(
                com.khabir.app.presentation.cases.LegalProcedureParser.parse(1,listOf(1),normalized,title))).subject
        }
        if (normalized.startsWith("أقام المدعي")) return reorderFormatted(normalized, placement)
        val result = PetitionSubjectExtraction.extract(normalized)
        val requests = result.requests
        var explanation = result.explanation
        if (requests.isNotBlank()) {
            explanation = explanation.lines().filterNot { it.trim() == requests.trim() }.joinToString("\n").trim()
        }
        if(explanation.startsWith("أقام المدعي")) return reorderFormatted(explanation,placement)
        if(explanation.startsWith("النيابة العامة ضد")) return explanation
        if (requests.isBlank() && explanation.isBlank()) return ""
        return UnifiedCaseSubject.compose(explanation,
            requests.ifBlank { "[الطلبات الختامية تحتاج استكمالًا]" }, placement == FinalRequestsPlacement.START)
    }

    fun reorderFormatted(current: String, placement: FinalRequestsPlacement): String {
        val text = current.replace("\r\n", "\n").trim()
        val request = Regex("و?طلب في ختامها\\s*:").find(text) ?: return text
        val explanation = Regex("(?:و?حيث قال شارح[ًاا]* دعواه|و?على سند من القول)\\s*:").find(text) ?: return text
        // Do not move another party's requests into the original when reordering procedures.
        if (text.contains("وأثناء سير الدعوى") || text.contains("وتداولت الدعوى") || text.contains("وقُضي فيها") || text.contains("ثم نُظر الاستئناف")) return text
        val relief = if (request.range.first < explanation.range.first)
            text.substring(request.range.last + 1, explanation.range.first).trim()
        else text.substring(request.range.last + 1).trim()
        val body = if (explanation.range.first < request.range.first)
            text.substring(explanation.range.last + 1, request.range.first).trim()
        else text.substring(explanation.range.last + 1).trim()
        val prefix = text.substring(0, minOf(request.range.first, explanation.range.first)).trimEnd(' ', '،', '\n')
        return if (placement == FinalRequestsPlacement.START)
            "$prefix، وطلب في ختامها:\n$relief\n\nوحيث قال شارحًا دعواه:\n$body"
        else "$prefix، وحيث قال شارحًا دعواه:\n$body\n\nوطلب في ختامها:\n$relief"
    }
}
