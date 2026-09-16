package com.khabir.app.presentation.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.model.Case
import com.khabir.app.domain.model.Report
import com.khabir.app.domain.usecase.cases.SearchCasesUseCase
import com.khabir.app.domain.usecase.report.ListReportsUseCase
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

data class ReportsHubUiState(
    val query: String = "",
    val cases: List<Case> = emptyList(),
    val reports: List<Report> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class ReportsHubViewModel @Inject constructor(
    private val searchCases: SearchCasesUseCase,
    private val listReports: ListReportsUseCase
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val casesFlow = query.debounce(200).flatMapLatest { searchCases(it) }

    val uiState: StateFlow<ReportsHubUiState> = combine(query, casesFlow, listReports()) { q, cases, reports ->
        val normalized = q.trim()
        val filteredReports = if (normalized.isBlank()) reports else reports.filter { report ->
            listOf(report.caseNo, report.caseYear, report.court, report.partiesSummary, report.subjectOfCase)
                .any { it.contains(normalized, ignoreCase = true) }
        }
        ReportsHubUiState(q, cases, filteredReports, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsHubUiState())

    fun onQueryChanged(value: String) { query.value = value }
}

