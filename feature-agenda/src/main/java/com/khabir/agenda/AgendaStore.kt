package com.khabir.agenda

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** تخزين مستقل تمامًا عن قاعدة بيانات القضايا. */
@Singleton
class AgendaStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("khabir_agenda_days_v1", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(loadAll())
    val records: StateFlow<Map<Long, AgendaDayNote>> = _records

    fun get(date: LocalDate): AgendaDayNote? = _records.value[date.toEpochDay()]

    fun save(record: AgendaDayNote) {
        val key = record.date.toEpochDay().toString()
        if (record.text.isBlank() && record.strokes.isEmpty() && record.imagePaths.isEmpty()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, encode(record)).apply()
        }
        _records.value = loadAll()
    }

    fun delete(date: LocalDate) {
        prefs.edit().remove(date.toEpochDay().toString()).apply()
        _records.value = loadAll()
    }

    private fun loadAll(): Map<Long, AgendaDayNote> = prefs.all.mapNotNull { (key, value) ->
        val epoch = key.toLongOrNull() ?: return@mapNotNull null
        val spec = value as? String ?: return@mapNotNull null
        decode(LocalDate.ofEpochDay(epoch), spec)?.let { epoch to it }
    }.toMap()

    private fun encode(record: AgendaDayNote): String = listOf(
        b64(record.text),
        b64(encodeStrokes(record.strokes)),
        b64(record.imagePaths.joinToString("\n")),
        record.updatedAt.toString()
    ).joinToString("\t")

    private fun decode(date: LocalDate, spec: String): AgendaDayNote? = runCatching {
        val parts = spec.split("\t")
        AgendaDayNote(
            date = date,
            text = unb64(parts.getOrNull(0).orEmpty()),
            strokes = decodeStrokes(unb64(parts.getOrNull(1).orEmpty())),
            imagePaths = unb64(parts.getOrNull(2).orEmpty()).split("\n").filter(String::isNotBlank),
            updatedAt = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        )
    }.getOrNull()

    private fun encodeStrokes(strokes: List<AgendaStroke>): String = strokes.joinToString("|") { stroke ->
        stroke.points.joinToString(";") { point -> "${point.x},${point.y}" }
    }

    private fun decodeStrokes(spec: String): List<AgendaStroke> = spec
        .split("|")
        .filter(String::isNotBlank)
        .mapNotNull { stroke ->
            val points = stroke.split(";").mapNotNull { pair ->
                val values = pair.split(",")
                val x = values.getOrNull(0)?.toFloatOrNull()
                val y = values.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) AgendaPoint(x, y) else null
            }
            points.takeIf { it.isNotEmpty() }?.let(::AgendaStroke)
        }

    private fun b64(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun unb64(value: String): String = if (value.isBlank()) "" else
        String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
