package com.khabir.app.domain.model
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
class TimedPaidFeatureGateTest {
    private val first = LocalDate.of(2026, 9, 1)
    @Test fun `hidden feature stays invisible`() {
        val gate = TimedPaidFeatureGate("tasks", visible = false, requiredProductId = "khabir_ad_free")
        assertEquals(FeatureGateState.Hidden, gate.evaluate(first, first, true))
    }
    @Test fun `delayed feature remains time locked before date`() {
        val gate = TimedPaidFeatureGate("tasks", visible = true, unlockAfterDays = 60)
        val state = gate.evaluate(first, LocalDate.of(2026, 9, 20), false)
        assertTrue(state is FeatureGateState.TimeLocked)
        assertEquals(LocalDate.of(2026, 10, 31), (state as FeatureGateState.TimeLocked).unlockDate)
    }
    @Test fun `zero day paid feature requires entitlement`() {
        val gate = TimedPaidFeatureGate("tasks", visible = true, unlockAfterDays = 0, requiredProductId = "khabir_ad_free")
        assertEquals(FeatureGateState.PriceLocked("khabir_ad_free"), gate.evaluate(first, first, false))
        assertEquals(FeatureGateState.Available, gate.evaluate(first, first, true))
    }
    @Test fun `future tasks stay unavailable regardless of subscription or date`() {
        val gate = FutureFeatureGates.TASKS
        assertEquals(0, gate.unlockAfterDays)
        assertEquals("khabir_ad_free", gate.requiredProductId)
        assertEquals(false, gate.visible)
        assertEquals(FeatureGateState.Hidden, gate.evaluate(first, LocalDate.of(2026, 9, 20), false))
        assertEquals(FeatureGateState.Hidden, gate.evaluate(first, LocalDate.of(2030, 1, 1), true))
    }
}
