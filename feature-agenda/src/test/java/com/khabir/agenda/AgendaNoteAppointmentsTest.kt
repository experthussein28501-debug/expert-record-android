package com.khabir.agenda

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AgendaNoteAppointmentsTest {
    private val selected=LocalDate.of(2026,10,5)
    @Test fun extractsArabicDatedTimedLineAndPreservesOriginal() {
        val raw="جلسة الدعوى ١٢٣ يوم ١٢/١١/٢٠٢٦ الساعة ٩ صباحًا"
        val result=AgendaNoteAppointments.parse(raw,selected)
        assertEquals(LocalDate.of(2026,11,12),result.candidates.single().date)
        assertTrue(result.candidates.single().appointment.title.contains("١٢٣"))
        assertEquals(raw,result.candidates.single().appointment.details)
        assertTrue(result.candidates.single().appointment.time.contains("٩"))
    }
    @Test fun caseNumberAndCivilLabelAreNotMistakenForTime() {
        assertTrue(AgendaNoteAppointments.parse("راجع الدعوى 123 مدني",selected).candidates.isEmpty())
        assertEquals(1,AgendaNoteAppointments.parse("الدعوى 123 مدني 12/11/2026",selected).candidates.size)
    }
    @Test fun isoDateWithClock() {assertEquals(LocalDate.of(2026,12,2),AgendaNoteAppointments.parse("مراجعة 2026-12-02 14:30",selected).candidates.single().date)}
    @Test fun noYearUsesSelectedYearWithVisibleWarning() {
        val item=AgendaNoteAppointments.parse("جلسة 12/11",selected).candidates.single()
        assertEquals(2026,item.date.year);assertTrue(item.usedSelectedDate)
    }
    @Test fun timeOnlyUsesSelectedDayWithVisibleWarning() {val c=AgendaNoteAppointments.parse("مراجعة الساعة 9",selected).candidates.single();assertEquals(selected,c.date);assertTrue(c.usedSelectedDate)}
    @Test fun undatedUntimedProseIsNotAppointment() {assertTrue(AgendaNoteAppointments.parse("راجع مستندات الدعوى 123 لسنة 2024",selected).candidates.isEmpty())}
    @Test fun invalidDateNeverFallsBackToSelectedDay() {val r=AgendaNoteAppointments.parse("جلسة 31/2/2026 الساعة 9 ص",selected);assertTrue(r.candidates.isEmpty());assertEquals(1,r.warnings.size)}
    @Test fun multipleDatesRequireManualChoice() {val r=AgendaNoteAppointments.parse("من 1/10/2026 إلى 5/10/2026",selected);assertTrue(r.candidates.isEmpty());assertEquals(1,r.warnings.size)}
    @Test fun invalidClockRejected() {assertTrue(AgendaNoteAppointments.parse("جلسة 12/11/2026 25:99",selected).candidates.isEmpty())}
    @Test fun duplicateImportIsIdempotent() {val a=AgendaNoteAppointments.parse("جلسة 12/11/2026",selected).candidates.single().appointment;assertEquals(listOf(a),AgendaNoteAppointments.merge(listOf(a),listOf(a)))}
    @Test fun existingTargetNotesImagesAndPenSurviveTransfer() {
        val date=selected.plusDays(10);val prior=AgendaDayNote(date,"نص قديم",listOf(AgendaStroke(listOf(AgendaPoint(1f,2f)))),listOf("صورة"),hiddenImportedKeys=setOf("hidden"))
        val primary=AgendaDayNote(selected,"الملاحظات الأصلية")
        val candidate=AgendaNoteCandidate(date,AgendaManualAppointment("موعد"))
        val result=AgendaNoteAppointments.transferRecords(primary,mapOf(date.toEpochDay() to prior),listOf(candidate)).first {it.date==date}
        assertEquals(prior.text,result.text);assertEquals(prior.strokes,result.strokes);assertEquals(prior.imagePaths,result.imagePaths);assertEquals(prior.hiddenImportedKeys,result.hiddenImportedKeys)
        assertEquals(1,result.manualAppointments.size)
    }
    @Test fun sameDayMergedOnceAndSourceTextPreserved() {
        val a=AgendaManualAppointment("جلسة");val primary=AgendaDayNote(selected,"نص الأصل",manualAppointments=listOf(a))
        val r=AgendaNoteAppointments.transferRecords(primary,emptyMap(),listOf(AgendaNoteCandidate(selected,a)))
        assertEquals(1,r.size);assertEquals(listOf(a),r.single().manualAppointments);assertEquals("نص الأصل",r.single().text)
    }
    @Test fun hiddenSourceSurvivesEditsAndCanBeRestored() {
        val event=AgendaEvent(selected,"جلسة",source=AgendaEventSource.NOTIFICATION_APPOINTMENT,sourceId="notification:1:case")
        val edited=event.copy(time="10 ص",details="تعديل المستندات")
        assertTrue(edited.isHidden(setOf(event.importKey())))
        assertFalse(edited.isHidden(emptySet()))
        assertTrue(event.isHidden(setOf(event.legacyImportKey())))
    }
    @Test fun swipeThresholdAndArabicDirection() {assertEquals(1,agendaMonthSwipe(-90f,64f));assertEquals(-1,agendaMonthSwipe(90f,64f));assertEquals(0,agendaMonthSwipe(30f,64f))}
}
