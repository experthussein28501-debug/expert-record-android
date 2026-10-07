package com.khabir.agenda

import com.khabir.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgendaSourceEventsTest {
    private val date=LocalDate.of(2026,10,5)
    private val case=Case(7,"1",date,"123","2026","أسوان","مدني",receiptDate=date)
    private val recipient=NotificationRecipient(caseId=7,partyId=2,caseNo="123",caseYear="2026",court="أسوان",partyFirstName="أحمد",partyRestName="علي",partyRole=PartyRole.PLAINTIFF,partyAddress="العنوان")
    private val batch=NotificationBatch(id=9,appointmentDate=date.plusDays(10),appointmentTime="9 صباحًا",appointmentLocation="المكتب",expertName="الخبير",officeAddress="المكتب",recipients=listOf(recipient,recipient.copy(partyId=3)))
    @Test fun notificationOneAppointmentPerCaseDespiteMultipleRecipients() {assertEquals(1,AgendaSourceEvents.from(emptyList(),listOf(batch),emptyList()).size)}
    @Test fun reprintDoesNotCreateNewAppointment() {assertEquals(1,AgendaSourceEvents.from(emptyList(),listOf(batch,batch.copy(id=10,isReprint=true)),emptyList()).size)}
    @Test fun automaticSchedulingDoesNotDuplicateFutureNotification() {
        val minutes=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date).copy(id=3)
        val events=AgendaSourceEvents.from(listOf(case),listOf(batch),listOf(minutes))
        assertEquals(1,events.count {it.date==batch.appointmentDate});assertEquals(2,events.count {it.date==date})
    }
    @Test fun expertsDifferentFollowUpFromAutomaticMinuteAppears() {
        val base=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date).copy(id=3)
        val changed=base.copy(entries=base.entries.map {if(it.automaticSourceKey.startsWith("notification:")) it.copy(scheduledFollowUpDate=date.plusDays(12)) else it})
        val events=AgendaSourceEvents.from(listOf(case),listOf(batch),listOf(changed))
        assertEquals(1,events.count {it.date==date.plusDays(12)})
        assertEquals(1,events.count {it.date==batch.appointmentDate})
    }
    @Test fun manualDatedMinuteAndNextAppointmentBothAppear() {
        val record=WorkMinutesRecord(id=3,caseNo="123",entries=listOf(WorkMinutesEntry(1,openingDate=date,scheduledFollowUpDate=date.plusDays(2),scheduledFollowUpTime="10 ص")))
        val events=AgendaSourceEvents.from(emptyList(),emptyList(),listOf(record));assertEquals(2,events.size);assertEquals("10 ص",events.last().time)
    }
    @Test fun stableNotificationKeyDoesNotChangeWhenTimeEdited() {
        val first=AgendaSourceEvents.from(emptyList(),listOf(batch),emptyList()).single()
        val changed=AgendaSourceEvents.from(emptyList(),listOf(batch.copy(appointmentTime="11 ص")),emptyList()).single()
        assertEquals(first.importKey(),changed.importKey());assertTrue(changed.isHidden(setOf(first.importKey())))
    }
    @Test fun authorityCoverNoticeDoesNotCreateExtraCaseAppointment() {assertTrue(AgendaSourceEvents.from(emptyList(),listOf(batch.copy(recipients=listOf(recipient.copy(isAuthorityNotice=true)))),emptyList()).isEmpty())}
}
