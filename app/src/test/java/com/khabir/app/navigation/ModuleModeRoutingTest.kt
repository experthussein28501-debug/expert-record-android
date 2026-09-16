package com.khabir.app.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleModeRoutingTest {
    @Test
    fun combined_mode_exposes_notifications_and_reports() {
        assertTrue(notificationsEnabledFor("COMBINED"))
        assertTrue(reportsEnabledFor("COMBINED"))
    }

    @Test
    fun standalone_modes_do_not_leak_other_units() {
        assertTrue(reportsEnabledFor("REPORTS"))
        assertFalse(notificationsEnabledFor("REPORTS"))
        assertTrue(notificationsEnabledFor("NOTIFICATIONS"))
        assertFalse(reportsEnabledFor("NOTIFICATIONS"))
        assertTrue(notificationsEnabledFor("SIRKIS"))
        assertFalse(reportsEnabledFor("SIRKIS"))
    }
}
