package com.khabir.app.presentation.workminutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.WorkMinutesRecord
import com.khabir.app.domain.usecase.cases.SearchCasesUseCase
import com.khabir.app.domain.usecase.workminutes.ListWorkMinutesUseCase
import com.khabir.app.domain.repository.WorkMinutesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WorkMinutesHubUiState(
    val query: String = "",
    val cases: List<Case> = emptyList(),
    val records: List<WorkMinutesRecord> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WorkMinutesHubViewModel @Inject constructor(
    private val searchCases: SearchCasesUseCase,
    private val listWorkMinutes: ListWorkMinutesUseCase,
    private val workMinutesRepository: WorkMinutesRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val casesFlow = query.debounce(200).flatMapLatest { searchCases(it) }

    val uiState: StateFlow<WorkMinutesHubUiState> = combine(query, casesFlow, listWorkMinutes()) { q, cases, records ->
        val normalized = q.trim()
        val filteredRecords = if (normalized.isBlank()) records else records.filter { record ->
            listOf(record.caseNo, record.caseYear, record.court, record.plaintiffsSummary, record.defendantsSummary)
                .any { it.contains(normalized, ignoreCase = true) }
        }
        WorkMinutesHubUiState(q, cases, filteredRecords, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkMinutesHubUiState())

    fun onQueryChanged(value: String) { query.value = value }
    fun onDeleteRecord(id: Long) { viewModelScope.launch { workMinutesRepository.delete(id) } }
}
