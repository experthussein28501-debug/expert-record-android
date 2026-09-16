package com.khabir.app.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.khabir.app.domain.repository.CaseRepository
import com.khabir.app.domain.repository.NotificationBatchRepository
import com.khabir.app.domain.repository.ReportRepository
import com.khabir.app.domain.repository.ExpertProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val casesCount: Int = 0,
    val reportsCount: Int = 0,
    val notificationBatchesCount: Int = 0,
    val recipientsCount: Int = 0,
    val expertName: String = "",
    val department: String = "",
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    caseRepository: CaseRepository,
    reportRepository: ReportRepository,
    notificationBatchRepository: NotificationBatchRepository,
    expertProfileRepository: ExpertProfileRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        caseRepository.search(""),
        reportRepository.observeAll(),
        notificationBatchRepository.observeAll(),
        expertProfileRepository.observe()
    ) { cases, reports, batches, profile ->
        HomeUiState(
            casesCount = cases.size,
            reportsCount = reports.size,
            notificationBatchesCount = batches.size,
            recipientsCount = batches.sumOf { it.recipients.size },
            expertName = profile?.expertName.orEmpty(),
            department = profile?.department.orEmpty(),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )
}
