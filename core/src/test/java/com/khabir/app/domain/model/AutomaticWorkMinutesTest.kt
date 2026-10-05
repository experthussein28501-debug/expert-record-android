package com.khabir.app.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AutomaticWorkMinutesTest {
    private val date=LocalDate.of(2026,10,5)
    private val case=Case(7,"1",date,"123","2026","أسوان","مدني",receiptDate=date)
    private val batch=NotificationBatch(id=9,appointmentDate=date.plusDays(10),appointmentTime="9 صباحًا",appointmentLocation="المكتب",expertName="الخبير",officeAddress="المكتب",recipients=emptyList())
    @Test fun receiptGeneratedOnRealReceiptDate() {val r=AutomaticWorkMinutes.receipt(case,null);assertEquals(date,r.entries.single().openingDate);assertEquals(7L,r.caseId);assertEquals(WorkMinutesPhrases.RECEIVED_FILE_DEFERRED,r.entries.single().bodyText)}
    @Test fun missingReceiptNeverInventsIncomingReceipt() {assertTrue(AutomaticWorkMinutes.receipt(case.copy(receiptDate=null),null).entries.isEmpty())}
    @Test fun repeatedSaveDoesNotDuplicate() {val r=AutomaticWorkMinutes.receipt(case,null);assertEquals(r,AutomaticWorkMinutes.receipt(case,r))}
    @Test fun redistributionUpdatesSameReceiptNumberAndDate() {
        val original=AutomaticWorkMinutes.receipt(case,null);val updated=AutomaticWorkMinutes.receipt(case.copy(adminNotes="إعادة توزيع"),original)
        assertEquals(1,updated.entries.size);assertEquals(original.entries.single().number,updated.entries.single().number)
        assertEquals(date,updated.entries.single().openingDate);assertEquals(WorkMinutesPhrases.RECEIVED_AFTER_REASSIGNMENT_DEFERRED,updated.entries.single().bodyText)
    }
    @Test fun manualReceiptCorrectionSurvivesSourceChange() {
        val r=AutomaticWorkMinutes.receipt(case,null);val changed=r.copy(entries=r.entries.map {it.copy(bodyText="تصحيح الخبير")})
        assertEquals(changed,AutomaticWorkMinutes.receipt(case.copy(adminNotes="إعادة توزيع",receiptDate=date.plusDays(1)),changed))
    }
    @Test fun unrelatedEntriesAreKeptAndNumbersUnique() {
        val manual=WorkMinutesRecord(caseId=case.id,entries=listOf(WorkMinutesEntry(6,bodyText="محضر مكتوب")))
        val r=AutomaticWorkMinutes.receipt(case,manual);assertEquals(listOf(6,7),r.entries.map {it.number});assertEquals(manual.entries.first(),r.entries.first())
    }
    @Test fun notificationCreatesIssuedMinuteAndFutureAppointment() {
        val record=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date)
        assertEquals(2,record.entries.size);val entry=record.entries.last()
        assertEquals(date,entry.openingDate);assertEquals(batch.appointmentDate,entry.scheduledFollowUpDate)
        assertTrue(entry.bodyText.contains(batch.appointmentDate.toString()));assertTrue(entry.bodyText.contains("طبقًا للإخطار المحرر"))
    }
    @Test fun notificationResaveUpdatesRatherThanDuplicates() {
        val r=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date)
        val updated=AutomaticWorkMinutes.scheduling(r,batch.copy(appointmentDate=date.plusDays(20)),date)
        assertEquals(2,updated.entries.size);assertEquals(date.plusDays(20),updated.entries.last().scheduledFollowUpDate)
    }
    @Test fun editedSchedulingMinuteIsProtected() {val r=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date);val edited=r.copy(entries=r.entries.map {if(it.automaticSourceKey.startsWith("notification:")) it.copy(openingTime="10 ص") else it});assertEquals(edited,AutomaticWorkMinutes.scheduling(edited,batch.copy(appointmentTime="12 ص"),date))}
    @Test fun generationMetadataRoundTripsWithoutChangingLegacyCodec() {val entries=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date).entries;assertEquals(entries,WorkMinutesCodec.decode(WorkMinutesCodec.encode(entries)));val legacy="1\t\t\t\t\t\t\t\t";assertEquals("",WorkMinutesCodec.decode(legacy).single().automaticSourceKey)}
    @Test fun removalOfDateRemovesUneditedGeneratedReceiptOnly() {val r=AutomaticWorkMinutes.scheduling(AutomaticWorkMinutes.receipt(case,null),batch,date);val updated=AutomaticWorkMinutes.receipt(case.copy(receiptDate=null),r);assertEquals(1,updated.entries.size);assertTrue(updated.entries.single().automaticSourceKey.startsWith("notification:"))}
}
