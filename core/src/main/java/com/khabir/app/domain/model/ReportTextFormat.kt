package com.khabir.app.domain.model

/** Stored with the report metadata, so backup and reopening preserve export formatting. */
data class ReportTextFormat(
    val alignment: String = "both",
    val font: String = "Arial",
    val size: Int = 14,
    val bold: Boolean = false,
    val linePercent: Int = 150
) {
    fun encode(): String = listOf(alignment, font, size, bold, linePercent).joinToString("|")
    companion object {
        const val KEY = "__report_text_format"
        val fonts = listOf("Arial", "Traditional Arabic", "Times New Roman")
        fun decode(value: String?): ReportTextFormat {
            val p = value.orEmpty().split('|')
            return ReportTextFormat(
                p.getOrNull(0)?.takeIf { it in listOf("right", "left", "center", "both") } ?: "both",
                p.getOrNull(1)?.takeIf { it in fonts } ?: "Arial",
                p.getOrNull(2)?.toIntOrNull()?.coerceIn(10, 24) ?: 14,
                p.getOrNull(3) == "true",
                p.getOrNull(4)?.toIntOrNull()?.coerceIn(100, 200) ?: 150
            )
        }
    }
}
