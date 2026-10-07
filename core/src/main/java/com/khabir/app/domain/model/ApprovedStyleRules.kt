package com.khabir.app.domain.model

import java.util.Base64

/** Explicitly approved style, with a date, never a saved sample or global case memory. */
object ApprovedStyleRules {
    data class Revision(val approvedAt: Long,val text: String)
    fun validate(rules: String, caseSpecificValues: List<String>): List<String> = buildList {
        if(rules.isBlank()) add("لا توجد قواعد لاعتمادها")
        if(rules.length > 12000) add("قواعد الصياغة أطول من الحد المسموح")
        if(Regex("[0-9٠-٩۰-۹]").containsMatchIn(rules)) add("احذف الأرقام والتواريخ والمبالغ والمساحات من قواعد الأسلوب")
        val normalized = legalNormalize(rules)
        if(caseSpecificValues.filter { it.trim().length >= 3 }.any { normalized.contains(legalNormalize(it)) })
            add("القواعد تتضمن اسمًا أو عنوانًا أو واقعة من القضية؛ احتفظ بقواعد الأسلوب العامة فقط")
        if(Regex("(?:رقم الدعوى|يمتلك .*قطعة|المقيم .*بناحية|أرض .*بحوض)").containsMatchIn(rules))
            add("بيانات القضية لا تُعمم ضمن أسلوب الصياغة")
    }
    fun encode(revisions: List<Revision>): String = revisions.takeLast(20).joinToString("\n") {
        "${it.approvedAt}|${Base64.getEncoder().encodeToString(it.text.toByteArray(Charsets.UTF_8))}"
    }
    fun decode(raw:String): List<Revision> = raw.lines().mapNotNull { line -> runCatching {
        val parts = line.split('|',limit=2)
        Revision(parts[0].toLong(),String(Base64.getDecoder().decode(parts[1]),Charsets.UTF_8))
    }.getOrNull() }
}
