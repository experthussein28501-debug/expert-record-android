package com.khabir.app.presentation.cases

import com.khabir.app.domain.model.LawyerNotification
import com.khabir.app.domain.model.PartyRole

internal object LawyerIntakeParser {
    fun parse(text: String): List<PetitionIntakeParser.ParsedParty> = text.lines().mapNotNull { raw ->
        val line = raw.trim()
        val structured = Regex("^(?:المحامي|المحامى|مخاطبة المحامي)\\s*:").find(line)
        val name: String
        val city: String
        if (structured != null) {
            val body = line.substring(structured.range.last + 1).trim()
            name = body.substringBefore('|').substringBefore('—').trim()
            city = Regex("(?:المدينة|مدينة المحامي)\\s*:\\s*([^|]+)").find(body)?.groupValues?.get(1)
                ?: body.substringAfter('—', "").trim()
        } else {
            val match = Regex("(?:مكتب\\s+)?(?:الأستاذ|الاستاذ|الأستاذة|الاستاذة|أ\\.)\\s*[/：:]?\\s*(.+?)\\s+المحام[يى](?:\\s*(?:ب|في|فى)\\s*([^،؛.\\n]+))?(?:$|[،؛.])").find(line) ?: return@mapNotNull null
            name = match.groupValues[1].trim()
            city = match.groupValues[2].substringBefore(" والكائن").substringBefore(" الكائن").substringBefore(" وشارع").substringBefore(" شارع")
        }
        val cleanName = name.replace(Regex("^(?:الأستاذ|الاستاذ|الأستاذة|أ\\.)\\s*[/：:]?\\s*"), "").trim()
        if (cleanName.isBlank() || cleanName.contains("غير مذكور")) null
        else PetitionIntakeParser.ParsedParty(PartyRole.LAWYER, false, cleanName, LawyerNotification.address(city))
    }.distinctBy { it.name to it.address }
}
