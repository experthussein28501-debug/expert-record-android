package com.khabir.app.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayReleaseControlsTest {
    @Test
    fun securePlayUrlsRequireHttpsHostAndNoEmbeddedCredentials() {
        assertTrue(isSecurePlayUrl("https://example.com/privacy"))
        assertTrue(isSecurePlayUrl("https://example.com/delete-account?lang=ar"))
        assertFalse(isSecurePlayUrl("http://example.com/privacy"))
        assertFalse(isSecurePlayUrl("https://user:pass@example.com/privacy"))
        assertFalse(isSecurePlayUrl("javascript:alert(1)"))
        assertFalse(isSecurePlayUrl(""))
    }

    @Test
    fun subscriptionPlansUseUniqueSupportedBasePlans() {
        assertEquals(subscriptionPlans.size, subscriptionPlans.map { it.basePlanId }.toSet().size)
        assertEquals(setOf("monthly", "quarterly", "halfyear", "annual"), subscriptionPlans.map { it.basePlanId }.toSet())
        assertTrue(subscriptionPlans.all { it.intendedEgp > 0 })
        assertTrue(subscriptionPlans.all { it.period.matches(Regex("P(?:1M|3M|6M|1Y)")) })
    }

    @Test
    fun playSubscriptionProductIdIsStableAndStoreSafe() {
        assertEquals("khabir_ad_free", PlayBillingManager.PRODUCT_ID)
        assertTrue(PlayBillingManager.PRODUCT_ID.matches(Regex("[a-z0-9_]+")))
    }
}
