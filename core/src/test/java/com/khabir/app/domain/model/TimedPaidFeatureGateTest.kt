package com.khabir.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimedPaidFeatureGateTest {
    private val firstActivation = LocalDate.of(2026, 9, 1)

    @Test
    fun `hidden feature never appears before release flag`() {
        val gate = TimedPaidFeatureGate(
            featureId = "tasks",
            visible = false,
            unlockAfterDays = 0,
            requiredProductId = "tasks_product"
        )

        assertEquals(
            FeatureGateState.Hidden,
            gate.evaluate(firstActivation, LocalDate.of(2026, 9, 30), hasEntitlement = true)
        )
    }

    @Test
    fun `time gate supports delayed unlock`() {
        val gate = TimedPaidFeatureGate(
            featureId = "tasks",
            visible = true,
            unlockAfterDays = 60,
            requiredProductId = null
        )

        val state = gate.evaluate(firstActivation, LocalDate.of(2026, 9, 20), hasEntitlement = false)
        assertTrue(state is FeatureGateState.TimeLocked)
        assertEquals(LocalDate.of(2026, 10, 31), (state as FeatureGateState.TimeLocked).unlockDate)
    }

    @Test
    fun `zero days can unlock immediately when feature is free`() {
        val gate = TimedPaidFeatureGate(
            featureId = "tasks",
            visible = true,
            unlockAfterDays = 0,
            requiredProductId = null
        )

        assertEquals(
            FeatureGateState.Available,
            gate.evaluate(firstActivation, firstActivation, hasEntitlement = false)
        )
    }

    @Test
    fun `paid feature stays price locked until entitlement exists`() {
        val gate = TimedPaidFeatureGate(
            featureId = "tasks",
            visible = true,
            unlockAfterDays = 0,
            requiredProductId = "tasks_product"
        )

        assertEquals(
            FeatureGateState.PriceLocked("tasks_product"),
            gate.evaluate(firstActivation, firstActivation, hasEntitlement = false)
        )
        assertEquals(
            FeatureGateState.Available,
            gate.evaluate(firstActivation, firstActivation, hasEntitlement = true)
        )
    }
}
