package com.khabir.app.data.export

/** Keep numeric tokens in their entered order inside Arabic Word paragraphs. */
internal object NotificationNumberDirection {
    private val run = Regex("<w:r>(<w:rPr>(?:(?!</w:rPr>).)*</w:rPr>)(<w:t[^>]*>)([^<]*)</w:t></w:r>", RegexOption.DOT_MATCHES_ALL)
    private val number = Regex("[0-9٠-٩۰-۹]+(?:[./:٫٬-][0-9٠-٩۰-۹]+)*")

    private fun arabicDigits(text: String) = text.map { c -> when(c) {
        in '0'..'9' -> "٠١٢٣٤٥٦٧٨٩"[c - '0']
        in '۰'..'۹' -> "٠١٢٣٤٥٦٧٨٩"[c - '۰']
        else -> c
    } }.joinToString("")

    // Word repairs out-of-order properties on edit; emit their schema order ourselves.
    private fun orderProperties(xml: String): String {
        var result = xml
        val orders = mapOf(
            "rPr" to "rStyle rFonts b bCs i iCs caps smallCaps strike dstrike outline shadow emboss imprint noProof snapToGrid vanish webHidden color spacing w kern position sz szCs highlight u effect bdr shd fitText vertAlign rtl cs em lang eastAsianLayout specVanish oMath",
            "pPr" to "pStyle keepNext keepLines pageBreakBefore framePr widowControl numPr suppressLineNumbers pBdr shd tabs suppressAutoHyphens kinsoku wordWrap overflowPunct topLinePunct autoSpaceDE autoSpaceDN bidi adjustRightInd snapToGrid spacing ind contextualSpacing mirrorIndents suppressOverlap jc textDirection textAlignment textboxTightWrap outlineLvl divId cnfStyle"
        )
        for ((tag, names) in orders) {
            val ranks = names.split(" ").withIndex().associate { it.value to it.index }
            result = Regex("<w:$tag>(.*?)</w:$tag>", RegexOption.DOT_MATCHES_ALL).replace(result) { block ->
                val inner = block.groupValues[1]
                val children = Regex("<w:([A-Za-z]+)(?:\\s+[^<>]*?)?/>").findAll(inner).toList()
                if (children.joinToString("") { it.value } != inner) block.value
                else "<w:$tag>" + children.sortedBy { ranks[it.groupValues[1]] ?: 999 }.joinToString("") { it.value } + "</w:$tag>"
            }
        }
        return result
    }

    fun apply(xml: String): String = orderProperties(run.replace(xml) { match ->
        val properties = match.groupValues[1]
        val textTag = match.groupValues[2]
        val text = arabicDigits(match.groupValues[3])
        val tokens = number.findAll(text).toList()
        if (tokens.isEmpty()) match.value else buildString {
            var offset = 0
            fun appendText(value: String) {
                if (value.isNotEmpty()) append("<w:r>$properties$textTag$value</w:t></w:r>")
            }
            tokens.forEach { token ->
                appendText(text.substring(offset, token.range.first))
                val numericProperties = properties.replace(Regex("<w:rtl(?:\\s+[^>]*)?/>") , "")
                    .replace("</w:rPr>", "<w:rtl w:val=\"0\"/></w:rPr>")
                append("<w:r>$numericProperties$textTag${token.value}</w:t></w:r>")
                offset = token.range.last + 1
            }
            appendText(text.substring(offset))
        }
    })
}
