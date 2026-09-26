package com.khabir.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import com.khabir.app.domain.repository.WorkMinutesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class AgendaUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate? = null,
    val days: Map<LocalDate, AgendaDaySummary> = emptyMap()
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val store: AgendaStore,
    workMinutesRepository: WorkMinutesRepository,
    notificationBatchRepository: NotificationBatchRepository,
    caseRepository: CaseRepository,
    private val holidays: EgyptOfficialHolidayProvider
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(null)
    private val sourceData = combine(
        workMinutesRepository.observeAll(),
        notificationBatchRepository.observeAll(),
        caseRepository.search("")
    ) { workMinutes, batches, cases ->
        Triple(workMinutes, batches, cases)
    }

    val uiState: StateFlow<AgendaUiState> = combine(
        month,
        selectedDate,
        store.records,
        sourceData
    ) { currentMonth, selected, manualNotes, sources ->
        val (workMinutes, batches, cases) = sources
        val holidayMap = holidays.holidaysFor(currentMonth.year).associateBy { it.date }
        val eventMap = buildList {
            cases.mapNotNull { it.toHearingAgendaEventOrNull() }.forEach(::add)
            batches.forEach { batch ->
                batch.recipients
                    .map { Triple(it.caseNo, it.caseYear, it.court) }
                    .filter { it.first.isNotBlank() || it.second.isNotBlank() || it.third.isNotBlank() }
                    .distinct()
                    .forEach { (caseNo, caseYear, court) ->
                        add(
                            AgendaEvent(
                                date = batch.appointmentDate,
                                title = caseLabel(caseNo, caseYear),
                                time = batch.appointmentTime,
                                location = batch.appointmentLocation,
                                details = listOf(court, batch.requestedDocuments.takeIf(String::isNotBlank)?.let { "مستندات: $it" })
                                    .filterNotNull().filter(String::isNotBlank).joinToString(" — "),
                                source = AgendaEventSource.NOTIFICATION_APPOINTMENT
                            )
                        )
                    }
            }
            workMinutes.forEach { record ->
                record.entries.forEach { entry ->
                    entry.scheduledFollowUpDate?.let { date ->
                        add(
                            AgendaEvent(
                                date = date,
                                title = caseLabel(record.caseNo, record.caseYear),
                                time = entry.scheduledFollowUpTime,
                                location = entry.scheduledFollowUpLocation.ifBlank { record.court },
                                details = "موعد تالٍ مثبت بمحضر الأعمال رقم ${entry.number}",
                                source = AgendaEventSource.WORK_MINUTES
                            )
                        )
                    }
                }
            }
            manualNotes.values.forEach { note ->
                note.manualAppointments.forEach { appointment ->
                    add(
                        AgendaEvent(
                            date = note.date,
                            title = appointment.title.ifBlank { "موعد يدوي" },
                            time = appointment.time,
                            location = appointment.location,
                            details = appointment.details,
                            source = AgendaEventSource.MANUAL
                        )
                    )
                }
            }
        }.distinctBy { listOf(it.date.toString(), it.title, it.time, it.location, it.source.name).joinToString("|") }
            .groupBy { it.date }

        val first = currentMonth.atDay(1)
        val last = currentMonth.atEndOfMonth()
        val dayMap = generateSequence(first) { it.plusDays(1).takeIf { d -> !d.isAfter(last) } }
            .associateWith { date ->
                AgendaDaySummary(
                    date = date,
                    holiday = holidayMap[date],
                    events = eventMap[date].orEmpty(),
                    note = manualNotes[date.toEpochDay()]
                )
            }
        AgendaUiState(month = currentMonth, selectedDate = selected, days = dayMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun goToday() { month.value = YearMonth.now(); selectedDate.value = LocalDate.now() }
    fun selectDate(date: LocalDate) { selectedDate.value = date }
    fun closeDay() { selectedDate.value = null }

    fun saveDay(
        date: LocalDate,
        text: String,
        strokes: List<AgendaStroke>,
        imagePaths: List<String>,
        manualAppointments: List<AgendaManualAppointment>
    ) {
        store.save(
            AgendaDayNote(
                date = date,
                text = text.trim(),
                strokes = strokes,
                imagePaths = imagePaths,
                manualAppointments = manualAppointments,
                updatedAt = System.currentTimeMillis()
            )
        )
        selectedDate.value = null
    }

    private fun caseLabel(caseNo: String, caseYear: String): String = when {
        caseNo.isNotBlank() && caseYear.isNotBlank() -> "الدعوى $caseNo لسنة $caseYear"
        caseNo.isNotBlank() -> "الدعوى $caseNo"
        else -> "موعد قضية"
    }
}
