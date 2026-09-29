package com.khabir.agenda

import java.time.LocalDate

enum class AgendaEventSource { CASE_HEARING, NOTIFICATION_APPOINTMENT, WORK_MINUTES, MANUAL, HOLIDAY }

data class AgendaPoint(val x: Float, val y: Float)

enum class AgendaSketchTool { FREEHAND, LINE, ARROW, RECTANGLE, CIRCLE, TRIANGLE, SEMICIRCLE, ERASER }

data class AgendaStroke(
    val points: List<AgendaPoint>,
    val tool: AgendaSketchTool = AgendaSketchTool.FREEHAND,
    val colorArgb: Int = 0xFF1B1B1B.toInt(),
    val width: Float = 4f
)

/** موعد يدوي منظم يضيفه المستخدم داخل يوم الأجندة. */
data class AgendaManualAppointment(
    val title: String = "",
    val time: String = "",
    val location: String = "",
    val details: String = ""
)

data class AgendaDayNote(
    val date: LocalDate,
    val text: String = "",
    val strokes: List<AgendaStroke> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    val manualAppointments: List<AgendaManualAppointment> = emptyList(),
    val hiddenImportedKeys: Set<String> = emptySet(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class AgendaEvent(
    val date: LocalDate,
    val title: String,
    val time: String = "",
    val location: String = "",
    val details: String = "",
    val source: AgendaEventSource
)

internal fun AgendaEvent.importKey(): String = listOf(source.name, date.toString(), title, time, location, details).joinToString("\u001f")

internal fun AgendaEvent.toAgendaNoteText(): String = listOf(
    title, time.takeIf(String::isNotBlank)?.let { "الساعة: $it" },
    location.takeIf(String::isNotBlank)?.let { "المكان: $it" }, details
).filterNotNull().filter(String::isNotBlank).joinToString("\n")

data class AgendaHoliday(val date: LocalDate, val name: String)

data class AgendaDaySummary(
    val date: LocalDate,
    val holiday: AgendaHoliday? = null,
    val events: List<AgendaEvent> = emptyList(),
    val note: AgendaDayNote? = null
)
