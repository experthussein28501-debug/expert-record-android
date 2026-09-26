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

data class AgendaHoliday(val date: LocalDate, val name: String)

data class AgendaDaySummary(
    val date: LocalDate,
    val holiday: AgendaHoliday? = null,
    val events: List<AgendaEvent> = emptyList(),
    val note: AgendaDayNote? = null
)
