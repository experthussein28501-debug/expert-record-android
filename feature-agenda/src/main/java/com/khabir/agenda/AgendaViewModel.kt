package com.khabir.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import com.khabir.app.domain.repository.WorkMinutesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
            addAll(AgendaSourceEvents.from(cases,batches,workMinutes))
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
        }.distinctBy { it.importKey() }.groupBy { it.date }


        val first = currentMonth.atDay(1)
        val last = currentMonth.atEndOfMonth()
        val dayMap = generateSequence(first) { it.plusDays(1).takeIf { d -> !d.isAfter(last) } }
            .associateWith { date ->
                AgendaDaySummary(
                    date = date,
                    holiday = holidayMap[date],
                    events = eventMap[date].orEmpty().filterNot {it.source!=AgendaEventSource.MANUAL && it.isHidden(manualNotes[date.toEpochDay()]?.hiddenImportedKeys.orEmpty())},
                    hiddenEvents = eventMap[date].orEmpty().filter {it.source!=AgendaEventSource.MANUAL && it.isHidden(manualNotes[date.toEpochDay()]?.hiddenImportedKeys.orEmpty())},
                    note = manualNotes[date.toEpochDay()]
                )
            }
        AgendaUiState(month = currentMonth, selectedDate = selected, days = dayMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUiState())

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    internal var draft: AgendaDraft? = null
        private set
    fun goToday() { month.value = YearMonth.now(); selectDate(LocalDate.now()) }
    fun selectDate(date: LocalDate) {
        if (isSaving.value) return
        draft = AgendaDraft(store.get(date))
        selectedDate.value = date
    }
    fun closeDay() { if (!isSaving.value) { selectedDate.value = null; draft = null } }

    val saveError = MutableStateFlow<String?>(null)
    val isSaving = MutableStateFlow(false)

    fun saveDay(
        date: LocalDate,
        text: String,
        strokes: List<AgendaStroke>,
        imagePaths: List<String>,
        manualAppointments: List<AgendaManualAppointment>
    ) {
        if (isSaving.value) return
        isSaving.value = true
        val transfers = draft?.noteTransfers?.toList().orEmpty()
        val hiddenImported = draft?.hiddenImportedKeys?.toSet().orEmpty()
        viewModelScope.launch {
        try {
        withContext(Dispatchers.IO) {
            val primary = AgendaDayNote(
                date = date,
                text = text.trim(),
                strokes = strokes,
                imagePaths = imagePaths,
                manualAppointments = manualAppointments,
                hiddenImportedKeys = hiddenImported,
                updatedAt = System.currentTimeMillis()
            )
            store.saveAll(AgendaNoteAppointments.transferRecords(primary,store.records.value,transfers))
        }
        selectedDate.value = null
        draft = null
        com.khabir.app.data.monetization.WorkAdEvents.finished()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (error: Exception) { saveError.value = "تعذر حفظ اليوم. حاول مرة أخرى." }
        finally { isSaving.value = false }
        }
    }

    private fun caseLabel(caseNo: String, caseYear: String): String = when {
        caseNo.isNotBlank() && caseYear.isNotBlank() -> "الدعوى $caseNo لسنة $caseYear"
        caseNo.isNotBlank() -> "الدعوى $caseNo"
        else -> "موعد قضية"
    }
}
