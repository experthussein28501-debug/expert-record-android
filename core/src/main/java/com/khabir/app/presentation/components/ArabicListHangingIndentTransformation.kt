package com.khabir.app.presentation.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.OffsetMapping
import androidx.compose.ui.text.ParagraphStyle
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
            if (LIST_PREFIX.containsMatchIn(line)) {
                builder.addStyle(
                    ParagraphStyle(
                        textDirection = TextDirection.Rtl,
                        textIndent = TextIndent(firstLine = 0.sp, restLine = restLineIndent)
                    ),
                    lineStart,
                    lineEnd
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
