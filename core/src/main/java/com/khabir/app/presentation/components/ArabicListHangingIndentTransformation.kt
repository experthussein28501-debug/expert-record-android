package com.khabir.app.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

class ArabicListHangingIndentTransformation(
    private val restLineIndent: TextUnit = 24.sp
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.isBlank()) return TransformedText(text, OffsetMapping.Identity)
        val source = text.text
        val builder = AnnotatedString.Builder(source.length).apply { append(source) }
        var lineStart = 0
        while (lineStart <= source.length) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline >= 0) newline else source.length
            val line = source.substring(lineStart, lineEnd)
            val prefix=com.khabir.app.domain.model.ReportLists.prefix.find(line.trimStart())
            if (LIST_PREFIX.containsMatchIn(line) || prefix!=null) {
                if(line.trimStart().startsWith("● ") || line.trimStart().startsWith("■ ")) {
                    val markerStart=lineStart+line.indexOfFirst { !it.isWhitespace() }
                    builder.addStyle(SpanStyle(fontSize=24.sp),markerStart,markerStart+1)
                }
                builder.addStyle(
                    ParagraphStyle(
                        textDirection = TextDirection.Rtl,
                        textIndent = TextIndent(firstLine = 0.sp, restLine = restLineIndent)
                    ),
                    lineStart,
                    // Include the line break so the paragraph style applies to
                    // wrapped lines consistently while the user is editing.
                    (lineEnd + if (newline >= 0) 1 else 0).coerceAtMost(source.length)
                )
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
    private companion object {
        val LIST_PREFIX = Regex(
            """^\s*(?:(?:[0-9٠-٩]+)|(?:[أ-ي]))\s*[\-–—ـ\.)/:：]\s+|^\s*[•●▪◦]\s+"""
        )
    }
}
