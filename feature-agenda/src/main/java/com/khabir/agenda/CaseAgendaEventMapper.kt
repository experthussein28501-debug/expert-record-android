package com.khabir.agenda

import com.khabir.app.domain.model.Case

internal fun Case.toHearingAgendaEventOrNull(): AgendaEvent? {
    if (isArchived) return null
    val date = hearingDate ?: return null
    val title = when {
        caseNo.isNotBlank() && caseYear.isNotBlank() -> "الدعوى $caseNo لسنة $caseYear"
        caseNo.isNotBlank() -> "الدعوى $caseNo"
        else -> "موعد قضية"
    }
    return AgendaEvent(
        date = date,
        title = title,
        time = hearingTime,
        location = court,
        details = listOf(
            "موعد جلسة مثبت ببيانات القضية",
            caseType.takeIf(String::isNotBlank)
        ).filterNotNull().joinToString(" — "),
        source = AgendaEventSource.CASE_HEARING,
        sourceId = "case:${id}:hearing"
    )
}
