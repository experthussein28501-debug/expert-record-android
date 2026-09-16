package com.khabir.app.presentation.cases

/** Reads the numbered case line, not the court letterhead or a circuit number. */
object JudgmentCaseIdentity {
    data class Identity(val number: String, val year: String, val type: String, val court: String?)
    fun parse(raw: String): Identity? {
        val text = raw.map { c ->
            when (c) {
                in '٠'..'٩' -> '0' + (c - '٠')
                in '۰'..'۹' -> '0' + (c - '۰')
                else -> c
            }
        }.joinToString("").replace('ـ', ' ').replace(Regex("[\u064B-\u065F]"), "")
        val line = Regex("""(?:الدعوى|الدعوي|القضية)\s*رقم\s*[:：]?\s*(\d+)\s*(?:لسنة|لسنه|/)\s*(\d{4})([^\n،؛]*)""").find(text) ?: return null
        val descriptor = line.groupValues[3].trim()
        val kinds = listOf(
            "استئناف عالي" to """استئناف\s+عال[يىٍ]?""",
            "قضاء إداري" to """(?:القضاء\s+الإداري|القضاء\s+الاداري|قضاء\s+[إا]دار[يى])""",
            "مدني مستأنف" to """مدن[يى]\s+مست[أا]نف""",
            "مدني كلي" to """(?:مدن[يى]\s*(?:كل[يى]|ك)|م\s*\.?\s*ك)\.?""",
            "مدني جزئي" to """(?:مدن[يى]\s*(?:جزئ[يى]|ج)|م\s*\.?\s*ج)\.?"""
        )
        val match = kinds.firstNotNullOfOrNull { (type, pattern) ->
            Regex("^$pattern").find(descriptor)?.let { type to it }
        } ?: return null
        var court = descriptor.substring(match.second.range.last + 1)
            .substringBefore("الدائرة").substringBefore("الدائره")
            .substringBefore("المرفوعة").substringBefore("المرفوعه")
            .substringBefore("بجلسة").trim(' ', '.', ':', '-', '/')
            .removePrefix("محكمة ").trim()
        if (match.first == "استئناف عالي" || match.first == "قضاء إداري") {
            val heading = text.lineSequence().map { it.substringBefore("الدائرة").substringBefore("الدائره").trim() }
                .firstOrNull { it.startsWith("محكمة") && (it.contains("استئناف") || it.contains("قضاء") || it.contains("القضاء")) }
            if (heading != null) court = heading
        } else {
            court = court.substringBefore("محكمة").substringBefore("مأمورية").substringBefore("مامورية").trim()
        }
        return Identity(line.groupValues[1], line.groupValues[2], match.first, court.ifBlank { null })
    }
}
