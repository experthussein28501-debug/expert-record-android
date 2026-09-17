package com.khabir.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EgyptOfficialHolidayProviderTest {
    private val provider = EgyptOfficialHolidayProvider()

    @Test
    fun official2026ContainsPublishedPresidencyDates() {
        val holidays = provider.holidaysFor(2026).associateBy { it.date }
        assertEquals("عيد الميلاد المجيد", holidays[LocalDate.of(2026, 1, 7)]?.name)
        assertEquals("عيد الفطر المبارك", holidays[LocalDate.of(2026, 3, 19)]?.name)
        assertEquals("عيد الفطر المبارك", holidays[LocalDate.of(2026, 3, 23)]?.name)
        assertEquals("عيد الأضحى المبارك", holidays[LocalDate.of(2026, 5, 31)]?.name)
        assertEquals("عيد القوات المسلحة", holidays[LocalDate.of(2026, 10, 6)]?.name)
    }

    @Test
    fun fixedFallbackKeepsKnownNationalDays() {
        val holidays = provider.holidaysFor(2027)
        assertTrue(holidays.any { it.date == LocalDate.of(2027, 1, 7) })
        assertTrue(holidays.any { it.date == LocalDate.of(2027, 7, 23) })
    }
}
