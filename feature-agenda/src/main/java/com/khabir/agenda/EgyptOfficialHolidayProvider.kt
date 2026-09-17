package com.khabir.agenda

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * تقويم الإجازات الرسمية المصرية. قائمة 2026 مأخوذة من الجدول الرسمي المنشور
 * لرئاسة الجمهورية، وتعمل دون إنترنت. السنوات التالية يمكن تحديثها في هذا الملف
 * وحده دون لمس شاشة الأجندة أو بيانات المستخدم.
 */
@Singleton
class EgyptOfficialHolidayProvider @Inject constructor() {
    fun holidaysFor(year: Int): List<AgendaHoliday> = when (year) {
        2026 -> official2026()
        else -> fixedNationalDays(year)
    }

    private fun official2026(): List<AgendaHoliday> = buildList {
        add(AgendaHoliday(LocalDate.of(2026, 1, 7), "عيد الميلاد المجيد"))
        add(AgendaHoliday(LocalDate.of(2026, 1, 29), "ثورة 25 يناير وعيد الشرطة"))
        addRange(2026, 3, 19, 23, "عيد الفطر المبارك")
        add(AgendaHoliday(LocalDate.of(2026, 4, 13), "شم النسيم"))
        add(AgendaHoliday(LocalDate.of(2026, 4, 25), "عيد تحرير سيناء"))
        add(AgendaHoliday(LocalDate.of(2026, 5, 7), "عيد العمال"))
        add(AgendaHoliday(LocalDate.of(2026, 5, 26), "وقفة عرفات"))
        addRange(2026, 5, 27, 31, "عيد الأضحى المبارك")
        add(AgendaHoliday(LocalDate.of(2026, 6, 18), "رأس السنة الهجرية"))
        add(AgendaHoliday(LocalDate.of(2026, 7, 2), "ذكرى ثورة 30 يونيو"))
        add(AgendaHoliday(LocalDate.of(2026, 7, 23), "عيد ثورة 23 يوليو"))
        add(AgendaHoliday(LocalDate.of(2026, 8, 27), "المولد النبوي الشريف"))
        add(AgendaHoliday(LocalDate.of(2026, 10, 6), "عيد القوات المسلحة"))
    }

    /** الأيام القومية الثابتة فقط عندما لا توجد قائمة سنوية موثقة داخل الإصدار. */
    private fun fixedNationalDays(year: Int): List<AgendaHoliday> = listOf(
        AgendaHoliday(LocalDate.of(year, 1, 7), "عيد الميلاد المجيد"),
        AgendaHoliday(LocalDate.of(year, 1, 25), "ثورة 25 يناير وعيد الشرطة"),
        AgendaHoliday(LocalDate.of(year, 4, 25), "عيد تحرير سيناء"),
        AgendaHoliday(LocalDate.of(year, 5, 1), "عيد العمال"),
        AgendaHoliday(LocalDate.of(year, 6, 30), "ذكرى ثورة 30 يونيو"),
        AgendaHoliday(LocalDate.of(year, 7, 23), "عيد ثورة 23 يوليو"),
        AgendaHoliday(LocalDate.of(year, 10, 6), "عيد القوات المسلحة")
    )

    private fun MutableList<AgendaHoliday>.addRange(
        year: Int,
        month: Int,
        startDay: Int,
        endDay: Int,
        name: String
    ) {
        for (day in startDay..endDay) add(AgendaHoliday(LocalDate.of(year, month, day), name))
    }
}
