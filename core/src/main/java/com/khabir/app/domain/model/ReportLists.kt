package com.khabir.app.domain.model

enum class ReportListStyle(val label: String) {
    PLAIN("بدون ترقيم"), WESTERN("1، 2، 3"), ARABIC_INDIC("١، ٢، ٣"),
    ARABIC_LETTERS("أ، ب، ج"), LATIN_UPPER("A، B، C"), LATIN_LOWER("a، b، c"),
    BULLET("• نقطة"), LARGE_BULLET("● نقطة كبيرة"), SQUARE("■ مربع"), DASH("– شرطة"), X_MARK("X");
    companion object {
        fun key(section: String) = "_list_style_$section"
        fun decode(value: String?) = entries.firstOrNull { it.name == value } ?: PLAIN
    }
}

object ReportLists {
    private val arabic = listOf("أ", "ب", "ج", "د", "هـ", "و", "ز", "ح", "ط", "ي", "ك", "ل", "م", "ن", "س", "ع", "ف", "ص", "ق", "ر", "ش", "ت", "ث", "خ", "ذ", "ض", "ظ", "غ")
    private fun letters(index: Int, alphabet: List<String>): String {
        var n=index; var result=""
        while(n>0) { n--;result=alphabet[n % alphabet.size]+result;n/=alphabet.size }
        return result
    }
    fun marker(style: ReportListStyle, index: Int): String = when(style) {
        ReportListStyle.PLAIN -> ""
        ReportListStyle.WESTERN -> "$index. "
        ReportListStyle.ARABIC_INDIC -> index.toString().toArabicIndicDigits()+". "
        ReportListStyle.ARABIC_LETTERS -> letters(index,arabic)+". "
        ReportListStyle.LATIN_UPPER -> letters(index,('A'..'Z').map { it.toString() })+". "
        ReportListStyle.LATIN_LOWER -> letters(index,('a'..'z').map { it.toString() })+". "
        ReportListStyle.BULLET -> "• "
        ReportListStyle.LARGE_BULLET -> "● "
        ReportListStyle.SQUARE -> "■ "
        ReportListStyle.DASH -> "– "
        ReportListStyle.X_MARK -> "X "
    }
    /** Only explicit list prefixes; digits inside a parcel, date or amount are never removed. */
    val prefix = Regex("""^(?:[0-9٠-٩]+[.)-]|[A-Za-z]+[.)]|[أبجدهـوزحطيكلمنسعفصقرشتثخذضظغ]+[.)]|[•●■–]|X)\s+""")
    fun split(line: String): Pair<String,String>? = prefix.find(line)?.let { it.value to line.substring(it.range.last+1) }
    fun apply(text: String, style: ReportListStyle): String {
        if(text.isBlank()) return marker(style,1)
        var index=0
        return text.lines().joinToString("\n") { line ->
            if(line.isBlank() || line.trimEnd().endsWith(":")) line
            else { index++; marker(style,index)+(split(line)?.second ?: line) }
        }
    }
    fun continueOnEnter(old: String, incoming: String, style: ReportListStyle): String {
        if(style == ReportListStyle.PLAIN || incoming != old+"\n") return incoming
        val last=old.substringAfterLast('\n')
        val parts=split(last) ?: return incoming
        if(parts.second.isBlank()) return old.dropLast(last.length)
        val index=old.lines().count { split(it)!=null }+1
        return incoming+marker(style,index)
    }
}
