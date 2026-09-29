package com.khabir.agenda

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Export every day, including days with no work, for a complete monthly register. */
internal fun exportAgendaCsv(context: Context, uri: Uri, days: List<AgendaDaySummary>) {
    fun cell(value: String) = "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ؛ ") + "\""
    val rows = buildList {
        add(listOf("التاريخ", "الحالة", "القضية أو الموعد", "الساعة", "المكان", "التفاصيل", "ملاحظات اليوم"))
        days.sortedBy { it.date }.forEach { day ->
            val items: List<AgendaEvent?> = if (day.events.isEmpty()) listOf(null) else day.events
            items.forEach { event ->
                add(listOf(day.date.toString(), if (day.events.isEmpty() && day.note?.text.isNullOrBlank()) "لا توجد أعمال" else "عمل",
                    event?.title.orEmpty(), event?.time.orEmpty(), event?.location.orEmpty(),
                    event?.details.orEmpty(), day.note?.text.orEmpty()))
            }
        }
    }
    context.contentResolver.openOutputStream(uri)?.use { out ->
        out.write(("\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::cell) }).toByteArray(Charsets.UTF_8))
    } ?: error("تعذر إنشاء الملف")
}

internal fun exportAgendaPdf(context: Context, uri: Uri, days: List<AgendaDaySummary>) {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14f; textAlign = Paint.Align.RIGHT; color = android.graphics.Color.BLACK }
    val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale("ar", "EG"))
    var page: PdfDocument.Page? = null
    var pageNo = 0
    var y = 0f
    fun startPage() {
        page?.let(document::finishPage)
        pageNo++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        y = 54f
    }
    fun line(value: String, heading: Boolean = false) {
        if (page == null || y > 785f) startPage()
        paint.textSize = if (heading) 17f else 13f
        val words = value.replace('\n', ' ').split(' ')
        var current = ""
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) > 505f && current.isNotEmpty()) {
                if (y > 785f) startPage()
                page!!.canvas.drawText(current, 555f, y, paint)
                y += 23f
                current = word
            } else current = candidate
        }
        if (y > 785f) startPage()
        page!!.canvas.drawText(current, 555f, y, paint)
        y += if (heading) 30f else 23f
    }
    try {
        line("سجل الأجندة", heading = true)
        days.sortedBy { it.date }.forEach { day ->
            line(day.date.format(dateFormat), heading = true)
            day.holiday?.let { line("إجازة: ${it.name}") }
            if (day.events.isEmpty() && day.note?.text.isNullOrBlank()) line("لا توجد أعمال مسجلة")
            day.events.forEach { event ->
                line(listOf(event.title, event.time.takeIf(String::isNotBlank)?.let { "الساعة $it" },
                    event.location, event.details).filterNotNull().filter(String::isNotBlank).joinToString(" — "))
            }
            day.note?.text?.takeIf(String::isNotBlank)?.let { line("ملاحظات: $it") }
            y += 9f
        }
        page?.let(document::finishPage)
        context.contentResolver.openOutputStream(uri)?.use(document::writeTo) ?: error("تعذر إنشاء الملف")
    } finally { document.close() }
}
