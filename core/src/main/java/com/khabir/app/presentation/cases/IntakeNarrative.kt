package com.khabir.app.presentation.cases

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Wording approved by the expert; factual content remains reviewable. */
object IntakeNarrative {
    fun subject(summary: String?, requests: String?): String {
        if (summary.isNullOrBlank()) return ""
        if (summary.trim().startsWith("أقام المدعي") || summary.trim().startsWith("النيابة العامة ضد")) return summary.trim()
        val claims = requests?.takeIf { it.isNotBlank() } ?: "[الطلبات الختامية تحتاج استكمالًا]"
        return com.khabir.app.domain.model.UnifiedCaseSubject.compose(summary, claims)
    }

    fun counterclaim(names: List<String>, requests: String?, incidental: Boolean, summary: String? = null): String {
        val claimant = com.khabir.app.domain.model.PartySummaries.names(names).ifBlank { "[اسم مقدم الطلب يحتاج استكمالًا]" }
        val action = if (incidental) "بتقديم طلب عارض" else "بإقامة دعوى فرعية"
        val claims = requests?.takeIf(String::isNotBlank) ?: "[الطلبات الختامية تحتاج استكمالًا]"
        val explanation = summary?.takeIf(String::isNotBlank)?.let { " وقال شرحًا لها: ${it.trim()}" }.orEmpty()
        return "وأثناء سير الدعوى، وبموجب صحيفة، قام $claimant $action، وطلب في ختامها: ${claims.trim().trimEnd('.')} .".replace(" .", ".") + explanation
    }

    fun returnedHistory(returnEvidence: String?, reportEvidence: String?, hearingEvidence: String?): String {
        if (returnEvidence.isNullOrBlank()) return ""
        val report = if (!reportEvidence.isNullOrBlank())
            "وسبق أن أحيلت الدعوى إلى مكتب الخبراء، وأودع الخبير تقريره الذي انتهى إلى نتيجة نحيل إليها منعًا للتكرار. " else ""
        val hearings = if (!hearingEvidence.isNullOrBlank()) "ثم تداولت الدعوى بالجلسات أمام المحكمة، " else ""
        return report + hearings + "ثم أعيدت الدعوى إلى مكتب الخبراء مرة أخرى." 
    }

    fun missionBody(text: String): String? {
        val start = Regex("(?:(?:ل?تكون\\s+)?مهمت(?:ه|ها))\\s*[:：]?\\s*").find(text)
            ?: Regex("بعد\\s+مطالعة\\s+أوراق\\s+الدعوى").find(text)
            ?: return null
        val body = text.substring(if (start.value.startsWith("بعد")) start.range.first else start.range.last + 1)
        val end = Regex("(?:و?تحقيق\\s+كافة\\s+عناصر\\s+الدعوى|بذات\\s+الأمانة\\s+السابقة|بأمانة\\s+تكميلية)").find(body)
        return (if (end == null) body else body.substring(0, end.range.last + 1)).trim().takeIf(String::isNotBlank)
    }

    fun mission(body: String?, date: LocalDate?): String {
        if (body.isNullOrBlank()) return ""
        if (body.trim().startsWith("يقضي حكم الإحالة") || body.trim().startsWith("قضى حكم الإحالة")) return body.trim()
        val day = date?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "[تاريخ الحكم يحتاج استكمالًا]"
        return "قضى حكم الإحالة الصادر بجلسة $day بالآتي:\n${body.trim()}"
    }
}
