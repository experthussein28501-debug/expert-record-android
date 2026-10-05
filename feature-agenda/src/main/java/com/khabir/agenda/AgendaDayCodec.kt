package com.khabir.agenda

import java.time.LocalDate
import java.util.Base64
import java.nio.charset.StandardCharsets

/**
 * ترميز مستقل قابل للاختبار. العمود الخامس للمواعيد اليدوية والسادس
 * للمواعيد المستوردة المخفية؛ السجلات القديمة ذات 4 أو 5 أعمدة تظل قابلة للقراءة.
 */
internal object AgendaDayCodec {
    fun encode(record: AgendaDayNote): String = listOf(
        b64(record.text),
        b64(encodeStrokes(record.strokes)),
        b64(record.imagePaths.joinToString("\n")),
        record.updatedAt.toString(),
        b64(encodeManualAppointments(record.manualAppointments)),
        b64(encodeHiddenImportedKeys(record.hiddenImportedKeys))
    ).joinToString("\t")

    fun decode(date: LocalDate, spec: String): AgendaDayNote? = runCatching {
        val parts = spec.split("\t")
        AgendaDayNote(
            date = date,
            text = unb64(parts.getOrNull(0).orEmpty()),
            strokes = decodeStrokes(unb64(parts.getOrNull(1).orEmpty())),
            imagePaths = unb64(parts.getOrNull(2).orEmpty()).split("\n").filter(String::isNotBlank),
            updatedAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
            manualAppointments = decodeManualAppointments(unb64(parts.getOrNull(4).orEmpty())),
            hiddenImportedKeys = decodeHiddenImportedKeys(unb64(parts.getOrNull(5).orEmpty()))
        )
    }.getOrNull()

    private fun encodeStrokes(strokes: List<AgendaStroke>): String = strokes.joinToString("|") { stroke ->
        val points = stroke.points.joinToString(";") { point -> "${point.x},${point.y}" }
        "v2~${stroke.tool.name}~${stroke.colorArgb}~${stroke.width}~$points"
    }

    private fun decodeStrokes(spec: String): List<AgendaStroke> = spec
        .split("|")
        .filter(String::isNotBlank)
        .mapNotNull { encoded ->
            val v2 = encoded.split("~", limit = 5)
            val tool = if (v2.size == 5) runCatching { AgendaSketchTool.valueOf(v2[1]) }.getOrNull() else null
            val color = if (v2.size == 5) v2[2].toIntOrNull() else null
            val width = if (v2.size == 5) v2[3].toFloatOrNull() else null
            val pointSpec = if (v2.size == 5) v2[4] else encoded
            val points = pointSpec.split(";").mapNotNull { pair ->
                val values = pair.split(",")
                val x = values.getOrNull(0)?.toFloatOrNull()
                val y = values.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) AgendaPoint(x, y) else null
            }
            points.takeIf { it.isNotEmpty() }?.let {
                AgendaStroke(
                    points = it,
                    tool = tool ?: AgendaSketchTool.FREEHAND,
                    colorArgb = color ?: 0xFF1B1B1B.toInt(),
                    width = width?.coerceIn(2f, 16f) ?: 4f
                )
            }
        }

    private fun encodeManualAppointments(items: List<AgendaManualAppointment>): String =
        items.joinToString("\n") { item ->
            listOf(
                b64(item.title),
                b64(item.time),
                b64(item.location),
                b64(item.details)
            ).joinToString("|")
        }

    private fun decodeManualAppointments(spec: String): List<AgendaManualAppointment> =
        spec.split("\n")
            .filter(String::isNotBlank)
            .mapNotNull { line ->
                val parts = line.split("|")
                val appointment = AgendaManualAppointment(
                    title = unb64(parts.getOrNull(0).orEmpty()),
                    time = unb64(parts.getOrNull(1).orEmpty()),
                    location = unb64(parts.getOrNull(2).orEmpty()),
                    details = unb64(parts.getOrNull(3).orEmpty())
                )
                appointment.takeIf {
                    it.title.isNotBlank() || it.time.isNotBlank() || it.location.isNotBlank() || it.details.isNotBlank()
                }
            }

    private fun encodeHiddenImportedKeys(keys: Set<String>): String =
        if (keys.isEmpty()) "" else "v2|" + keys.joinToString("|") { b64(it) }

    private fun decodeHiddenImportedKeys(spec: String): Set<String> {
        if (spec.isBlank()) return emptySet()
        return if (spec.startsWith("v2|")) {
            spec.removePrefix("v2|").split("|").filter(String::isNotBlank).mapNotNull { encoded ->
                runCatching { unb64(encoded) }.getOrNull()?.takeIf(String::isNotBlank)
            }.toSet()
        } else {
            // Backward compatibility with early 0.9.14 preview records where
            // raw keys were joined by newlines. Details themselves may contain
            // newlines, so continuation lines are reattached to the preceding key.
            val rebuilt = mutableListOf<String>()
            spec.split("\n").forEach { line ->
                if (line.isBlank()) return@forEach
                if (line.count { it == '\u001f' } >= 5 || rebuilt.isEmpty()) {
                    rebuilt += line
                } else {
                    rebuilt[rebuilt.lastIndex] = rebuilt.last() + "\n" + line
                }
            }
            rebuilt.filter(String::isNotBlank).toSet()
        }
    }

    private fun b64(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String = if (value.isBlank()) "" else
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
