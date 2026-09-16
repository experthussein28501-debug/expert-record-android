package com.khabir.app.domain.usecase.notification

/** Conservative, editable suggestion from the requested relief; never invent a cause. */
object NoticeSubject {
    fun suggest(finalRequests: String, petition: String): String {
        val cleanPetition = petition.replace(Regex("\\s+"), " ").trim()
        val source = finalRequests.ifBlank {
            Regex("(?:طلب(?:وا)? في ختامها|وطلب في ختامها|الطلبات الختامية|بناء عليه)[:：]?\\s*(.*)", RegexOption.DOT_MATCHES_ALL)
                .find(cleanPetition)?.groupValues?.get(1).orEmpty()
        }.replace(Regex("\\s+"), " ").trim()
        if (source.isBlank()) return ""
        val labels = mutableListOf<String>()
        fun add(pattern: String, label: String) { if (Regex(pattern).containsMatchIn(source)) labels += label }
        add("صح[ةه]\\s*و?\\s*نفاذ", if (Regex("عقد.*بيع|عقد البيع").containsMatchIn(source)) "صحة ونفاذ عقد بيع" else "صحة ونفاذ")
        add("طرد.{0,16}غصب", "طرد للغصب")
        if (source.contains("تعويض")) {
            labels += when {
                Regex("نزع\\s+الملكي[ةه]").containsMatchIn(source) -> "تعويض عن نزع الملكية"
                Regex("استيلاء|استولى|الاستيلاء").containsMatchIn(source) -> "تعويض عن الاستيلاء"
                else -> "تعويض"
            }
        }
        add("ريع", "ريع")
        add("مقابل\\s+(?:عدم\\s+)?الانتفاع", if (source.contains("عدم الانتفاع")) "مقابل عدم الانتفاع" else "مقابل الانتفاع")
        add("فرز.{0,8}تجنيب", "فرز وتجنيب")
        add("تسليم", "تسليم")
        if (labels.isNotEmpty()) return labels.distinct().take(3).joinToString(" و")
        val sentence = source.split(Regex("[.!؟\\n]"), limit = 2).first().trim()
        return sentence.takeIf { it.length <= 180 }.orEmpty()
    }
}
