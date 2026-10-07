package com.khabir.app.presentation.notifications

import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.NotificationRecipient
import java.time.LocalDate

// Registered identities take precedence; manual cases include the court to avoid collisions.
fun notificationCaseKey(r: NotificationRecipient): String =
    r.caseId?.let { "id:$it" } ?: listOf(r.caseNo.trim(), r.caseYear.trim(), r.court.trim()).joinToString("\u001f")

fun sessionRecipients(batches: List<NotificationBatch>, caseKey: String): List<NotificationRecipient> =
    batches.sortedByDescending { it.createdAt }.flatMap { it.recipients }
        .filter { !it.isAuthorityNotice && notificationCaseKey(it) == caseKey }
        .distinctBy { it.partyId?.let { id -> "id:$id" } ?: listOf(it.rawFullName.trim(), it.storedRoleValue, it.partyAddress.trim()).joinToString("\u001f") }

fun newSessionBatch(source: NotificationBatch, recipients: List<NotificationRecipient>, date: LocalDate, time: String): NotificationBatch {
    require(recipients.isNotEmpty() && time.isNotBlank())
    require(recipients.none { it.isAuthorityNotice })
    require(recipients.map(::notificationCaseKey).distinct().size == 1)
    return source.copy(id = 0L, appointmentDate = date, appointmentTime = time.trim(),
        recipients = recipients.map { it.copy(id = 0L) }, createdAt = 0L,
        isReprint = false, sourceBatchId = source.id)
}
