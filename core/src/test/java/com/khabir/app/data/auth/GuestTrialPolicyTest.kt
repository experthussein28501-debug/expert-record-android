package com.khabir.app.data.auth

import org.junit.Assert.*
import org.junit.Test

class GuestTrialPolicyTest {
    @Test fun localUseContinuesAtSevenDaysAndAfterManyYears() {
        val start = 1_000_000L
        assertFalse(GuestTrialPolicy.expired(start, start + GuestTrialPolicy.DURATION_MILLIS - 1))
        assertFalse(GuestTrialPolicy.expired(start, start + GuestTrialPolicy.DURATION_MILLIS))
        assertFalse(GuestTrialPolicy.expired(start, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE, GuestTrialPolicy.remaining(start, start + GuestTrialPolicy.DURATION_MILLIS))
    }
    @Test fun legacyExpiryDoesNotBlockUnlimitedLocalUse() {
        assertTrue(GuestTrialPolicy.mayStart(123, false))
        assertTrue(GuestTrialPolicy.mayStart(0, true))
        assertFalse(GuestTrialPolicy.expired(123, 1, true))
        assertTrue(GuestTrialPolicy.mayStart(0, false))
    }
    @Test fun onlyAuthenticatedNonAnonymousGoogleUsersAreAccepted() {
        assertTrue(GoogleAccountGate.allows(true, false, listOf("firebase", "google.com")))
        assertFalse(GoogleAccountGate.allows(false, false, listOf("google.com")))
        assertFalse(GoogleAccountGate.allows(true, true, listOf("google.com")))
        assertFalse(GoogleAccountGate.allows(true, false, listOf("password")))
        assertFalse(GoogleAccountGate.allows(true, false, emptyList()))
    }
}
