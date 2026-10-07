package com.khabir.app.presentation.notifications

import com.khabir.app.domain.model.NotificationBatch
import com.khabir.app.domain.model.NotificationRecipient
import com.khabir.app.domain.model.PartyRole
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class NotificationSessionsTest {
    private val day = LocalDate.of(2026, 10, 5)
    private fun person(id: Long = 1L) = NotificationRecipient(id = id, caseId = 42L, partyId = id,
        caseNo = "123", caseYear = "2026", court = "أسوان", partyFirstName = "خصم $id",
        partyRestName = "", partyRole = PartyRole.DEFENDANT, partyAddress = "عنوان $id")
    private fun batch() = NotificationBatch(id = 10L, appointmentDate = day,
        appointmentTime = "10 صباحًا", appointmentLocation = "المكتب", expertName = "خبير",
        officeAddress = "أسوان", recipients = listOf(person(), person(2)), createdAt = 100L)

    @Test fun secondSessionKeepsFirstAndOnlySelectedRecipients() {
        val original = batch()
        val next = newSessionBatch(original, listOf(person(2)), day.plusDays(10), "11 صباحًا")
        assertEquals(day, original.appointmentDate)
        assertEquals(2, original.recipients.size)
        assertEquals(0L, next.id)
        assertEquals(listOf(2L), next.recipients.map { it.partyId })
        assertEquals(42L, next.recipients.single().caseId)
        assertEquals(0L, next.recipients.single().id)
        assertEquals(10L, next.sourceBatchId)
        assertFalse(next.isReprint)
    }
    @Test fun repeatedSessionsRetainEveryoneFromHistory() {
        val first = batch()
        val second = newSessionBatch(first, listOf(person(2)), day.plusDays(2), "12").copy(id = 11L, createdAt = 200L)
        val third = newSessionBatch(second, listOf(person()), day.plusDays(3), "13").copy(id = 12L)
        val all = listOf(first, second, third)
        assertEquals(2, sessionRecipients(all, notificationCaseKey(person())).size)
        assertEquals(listOf(first), all.filter { it.appointmentDate == day })
        assertEquals(listOf(second), all.filter { it.appointmentDate == day.plusDays(2) })
    }
    @Test fun differentCourtsDoNotMergeManualCases() {
        val a = person().copy(caseId = null)
        val b = a.copy(court = "إدفو")
        assertNotEquals(notificationCaseKey(a), notificationCaseKey(b))
        assertEquals(listOf(a), sessionRecipients(listOf(batch().copy(recipients = listOf(a,b))), notificationCaseKey(a)))
    }
    @Test fun newestAddressWinsForRegisteredParty() {
        val old = batch()
        val newer = old.copy(id = 11L, createdAt = 200L, recipients = listOf(person().copy(partyAddress = "العنوان الجديد")))
        assertEquals("العنوان الجديد", sessionRecipients(listOf(old, newer), notificationCaseKey(person())).first { it.partyId == 1L }.partyAddress)
    }
    @Test fun authorityNoticesAreNotOrdinaryRecipients() {
        val b = batch().copy(recipients = listOf(person(), person(3).copy(isAuthorityNotice = true)))
        assertEquals(1, sessionRecipients(listOf(b), notificationCaseKey(person())).size)
    }
    @Test(expected = IllegalArgumentException::class) fun emptySelectionRejected() {
        newSessionBatch(batch(), emptyList(), day, "10")
    }
    @Test(expected = IllegalArgumentException::class) fun missingTimeRejected() {
        newSessionBatch(batch(), listOf(person()), day, " ")
    }
    @Test(expected = IllegalArgumentException::class) fun mixedCasesRejected() {
        newSessionBatch(batch(), listOf(person(), person(2).copy(caseId = 99)), day, "10")
    }
}
