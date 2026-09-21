package com.khabir.app.domain.model

/** A notification goes to the bar at the explicitly supplied city, never to an office. */
object LawyerNotification {
    fun city(value: String): String {
        val raw = value.trim().replace("ـ", "")
        val candidate = when {
            raw.startsWith("محكمة ") -> raw.removePrefix("محكمة ").substringBefore("—").substringBefore(" - ")
            raw.contains("نقابة المحامين بمحكمة ") -> raw.substringAfter("نقابة المحامين بمحكمة ")
            raw.contains("نقابة المحامين ب") -> raw.substringAfter("نقابة المحامين ب")
            else -> raw
        }.trim().trim('،', '.', ':')
        if (candidate.isBlank() || candidate.length > 60 || candidate.split(Regex("\\s+")).size > 5 ||
            Regex("[0-9٠-٩|\n]|شارع|مكتب|عقار|عمارة|عنوان|المقيم|غير مذكور|غير محدد").containsMatchIn(candidate)) return ""
        return candidate.replace(Regex("\\s+"), " ")
    }

    fun address(cityOrAddress: String): String = city(cityOrAddress).let {
        if (it.isBlank()) "" else "محكمة $it — نقابة المحامين ب$it"
    }
}
