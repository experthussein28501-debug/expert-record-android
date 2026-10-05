package com.khabir.app.presentation.reports

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.khabir.app.domain.model.ReportTextFormat

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportFormattingToolbar(format: ReportTextFormat, onChange: (ReportTextFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "إخفاء تنسيق التقرير" else "تنسيق التقرير: الخط والمحاذاة")
            }
            if (expanded) {
                Text("تُحفظ هذه الاختيارات لنص التقرير كله وتُستخدم عند تصدير Word وPDF.", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("right" to "يمين", "left" to "يسار", "center" to "توسيط", "both" to "ضبط").forEach { (value, label) ->
                        FilterChip(selected = format.alignment == value, onClick = { onChange(format.copy(alignment = value)) }, label = { Text(label) })
                    }
                }
                Text("الخط")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReportTextFormat.fonts.forEach { font ->
                        FilterChip(selected = format.font == font, onClick = { onChange(format.copy(font = font)) }, label = { Text(font) })
                    }
                }
                Text("حجم الخط")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                        FilterChip(selected = format.size == size, onClick = { onChange(format.copy(size = size)) }, label = { Text(size.toString()) })
                    }
                    FilterChip(selected = format.bold, onClick = { onChange(format.copy(bold = !format.bold)) }, label = { Text("عريض", fontWeight = FontWeight.Bold) })
                }
                Text("تباعد السطور")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(100 to "مفرد", 150 to "١٫٥", 200 to "مزدوج").forEach { (value, label) ->
                        FilterChip(selected = format.linePercent == value, onClick = { onChange(format.copy(linePercent = value)) }, label = { Text(label) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun reportEditorTextStyle(format: ReportTextFormat): TextStyle = TextStyle(
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = format.size.sp,
    lineHeight = (format.size * format.linePercent / 100f).sp,
    fontFamily = if (format.font == "Arial") FontFamily.SansSerif else FontFamily.Serif,
    fontWeight = if (format.bold) FontWeight.Bold else FontWeight.Normal,
    textDirection = TextDirection.Rtl,
    textAlign = when (format.alignment) {
        "left" -> TextAlign.Left
        "center" -> TextAlign.Center
        "both" -> TextAlign.Justify
        else -> TextAlign.Right
    }
)
