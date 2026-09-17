package com.khabir.app.presentation.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.khabir.app.domain.model.PartyRole
import com.khabir.app.domain.usecase.notification.AuthorityNoticeDraft
import com.khabir.app.domain.usecase.notification.NoticeSubject

/** Keeps the State Lawsuits Authority action visible instead of hiding it behind auto-detection. */
@Composable
fun CompleteNotificationBatchScreen(
    onBack: () -> Unit,
    viewModel: NotificationBatchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val candidate = visibleAuthorityDraft(state)
    val approved = candidate?.let { state.approvedAuthorityNotices[it.key] }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            OutlinedButton(
                onClick = { candidate?.let(viewModel::onOpenAuthorityNotice) },
                enabled = state.mode == NotificationScreenUiState.Mode.BuildingNew && candidate != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        approved != null -> "تعديل إخطار هيئة قضايا الدولة"
                        state.mode != NotificationScreenUiState.Mode.BuildingNew -> "إخطار هيئة قضايا الدولة — ابدأ دفعة إخطارات"
                        candidate == null -> "إخطار هيئة قضايا الدولة — اختر/أدخل بيانات الدعوى"
                        else -> "إضافة إخطار هيئة قضايا الدولة"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "الزر ظاهر دائمًا؛ اعتماد الإضافة يتم يدويًا بعد مراجعة العنوان والموضوع وبيانات الدعوى.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            NotificationBatchScreen(onBack = onBack, viewModel = viewModel)
        }
    }
}

private fun visibleAuthorityDraft(state: NotificationScreenUiState): AuthorityNoticeDraft? {
    if (state.mode != NotificationScreenUiState.Mode.BuildingNew) return null

    val selectedCase = state.selectedRegisteredCase
    if (state.caseSource == NotificationScreenUiState.CaseSource.REGISTERED_CASES && selectedCase != null) {
        val plaintiffs = selectedCase.parties
            .filter { it.role == PartyRole.PLAINTIFF }
            .joinToString("، ") { it.reportDisplayName }
        val defendants = selectedCase.parties
            .filter { it.role == PartyRole.DEFENDANT }
            .joinToString("، ") { it.reportDisplayName }
        return AuthorityNoticeDraft(
            key = "case:${selectedCase.id}",
            caseNo = selectedCase.caseNo,
            caseYear = selectedCase.caseYear,
            court = selectedCase.court,
            subject = NoticeSubject.suggest(selectedCase.finalRequests, selectedCase.subjectOfCase),
            judgmentDate = selectedCase.preliminaryJudgmentDate?.toString().orEmpty(),
            incomingNo = selectedCase.incomingNo,
            plaintiffs = plaintiffs,
            defendants = defendants
        )
    }

    if (state.manualCaseNo.isBlank() || state.manualCaseYear.isBlank() || state.manualCourt.isBlank()) return null
    val plaintiffs = state.manualRecipients
        .filter { it.role == PartyRole.PLAINTIFF }
        .joinToString("، ") { listOf(it.firstName, it.restName).filter(String::isNotBlank).joinToString(" ") }
    val defendants = state.manualRecipients
        .filter { it.role == PartyRole.DEFENDANT }
        .joinToString("، ") { listOf(it.firstName, it.restName).filter(String::isNotBlank).joinToString(" ") }
    return AuthorityNoticeDraft(
        key = "manual:${state.manualCaseNo}/${state.manualCaseYear}/${state.manualCourt}",
        caseNo = state.manualCaseNo,
        caseYear = state.manualCaseYear,
        court = state.manualCourt,
        subject = NoticeSubject.suggest("", state.manualPetition),
        plaintiffs = plaintiffs,
        defendants = defendants
    )
}
