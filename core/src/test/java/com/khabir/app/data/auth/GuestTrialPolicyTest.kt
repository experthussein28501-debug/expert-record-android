package com.khabir.app.data.auth

import org.junit.Assert.*
import org.junit.Test

class GuestTrialPolicyTest {
    @Test fun sevenCompleteDaysAreAllowedButTheBoundaryExpires() {
        val start = 1_000_000L
        assertFalse(GuestTrialPolicy.expired(start, start + GuestTrialPolicy.DURATION_MILLIS - 1))
        assertTrue(GuestTrialPolicy.expired(start, start + GuestTrialPolicy.DURATION_MILLIS))
        assertEquals(0L, GuestTrialPolicy.remaining(start, start + GuestTrialPolicy.DURATION_MILLIS))
    }
    @Test fun expiredTrialCannotBeStartedOrRevivedByChangingTheClock() {
        assertFalse(GuestTrialPolicy.mayStart(123, false))
        assertFalse(GuestTrialPolicy.mayStart(0, true))
        assertTrue(GuestTrialPolicy.expired(123, 1, true))
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
