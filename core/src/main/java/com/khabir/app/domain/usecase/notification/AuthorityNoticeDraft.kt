package com.khabir.app.domain.usecase.notification

import com.khabir.app.domain.model.NotificationRecipient
import com.khabir.app.domain.model.PartyRole
import java.time.LocalDate

/** One explicitly approved extra notice per case. No administrative address is inferred. */
data class AuthorityNoticeDraft(
    val key: String,
    val caseNo: String,
    val caseYear: String,
    val court: String,
    val address: String = "",
    val subject: String = "",
    val judgmentDate: String = "",
    val incomingNo: String = "",
    val plaintiffs: String = "",
    val defendants: String = ""
) {
    fun valid(): Boolean = address.isNotBlank() && subject.isNotBlank() && subject.length <= 180 &&
        caseNo.isNotBlank() && caseYear.isNotBlank() && court.isNotBlank() &&
        (judgmentDate.isBlank() || runCatching { LocalDate.parse(judgmentDate) }.isSuccess)

    fun toRecipient(): NotificationRecipient {
        require(valid()) { "راجع عنوان الهيئة وموضوع الدعوى وتاريخ الحكم (سنة-شهر-يوم)" }
        return NotificationRecipient(caseId = null, partyId = null, caseNo = caseNo, caseYear = caseYear,
            court = court, partyFirstName = "هيئة قضايا الدولة", partyRestName = "",
            partyRole = PartyRole.OTHER, partyAddress = address.trim(),
            plaintiffsSummary = plaintiffs, defendantsSummary = defendants, subjectOfCase = subject.trim(),
            preliminaryJudgmentDate = judgmentDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
            isAuthorityNotice = true, incomingNo = incomingNo)
    }
}
