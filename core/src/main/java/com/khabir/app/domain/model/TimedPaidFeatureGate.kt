package com.khabir.app.domain.model

import java.time.LocalDate

data class TimedPaidFeatureGate(
    val featureId: String,
    val visible: Boolean = false,
    val unlockAfterDays: Int = 0,
    val requiredProductId: String? = null
) {
    init { require(unlockAfterDays >= 0) }

    fun evaluate(
        firstActivationDate: LocalDate,
        today: LocalDate,
        hasEntitlement: Boolean
    ): FeatureGateState {
        if (!visible) return FeatureGateState.Hidden
        val unlockDate = firstActivationDate.plusDays(unlockAfterDays.toLong())
        if (today.isBefore(unlockDate)) return FeatureGateState.TimeLocked(unlockDate)
        if (!requiredProductId.isNullOrBlank() && !hasEntitlement) {
            return FeatureGateState.PriceLocked(requiredProductId)
        }
        return FeatureGateState.Available
    }
}

sealed interface FeatureGateState {
    data object Hidden : FeatureGateState
    data class TimeLocked(val unlockDate: LocalDate) : FeatureGateState
    data class PriceLocked(val productId: String) : FeatureGateState
    data object Available : FeatureGateState
}

object FutureFeatureGates {
    val TASKS = TimedPaidFeatureGate(
        featureId = "tasks",
        // The module itself stays unavailable; the dashboard may show a
        // locked teaser after the same two-month trial used by the app.
        visible = true,
        unlockAfterDays = 60,
        requiredProductId = "khabir_ad_free"
    )
}
