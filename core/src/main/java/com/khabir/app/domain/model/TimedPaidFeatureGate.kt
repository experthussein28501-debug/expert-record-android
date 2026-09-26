package com.khabir.app.domain.model

import java.time.LocalDate

/**
 * بوابة عامة للميزات المستقبلية المدفوعة/المؤجلة.
 *
 * لا تضع سعرًا ثابتًا داخل الكود؛ requiredProductId يربط الميزة بمنتج Billing
 * والسعر المعروض يأتي من المتجر. unlockAfterDays يقبل 0 للتفعيل الفوري.
 */
data class TimedPaidFeatureGate(
    val featureId: String,
    val visible: Boolean = false,
    val unlockAfterDays: Int = 0,
    val requiredProductId: String? = null
) {
    init {
        require(unlockAfterDays >= 0) { "unlockAfterDays must be >= 0" }
    }

    fun evaluate(
        firstActivationDate: LocalDate,
        today: LocalDate,
        hasEntitlement: Boolean
    ): FeatureGateState {
        if (!visible) return FeatureGateState.Hidden

        val unlockDate = firstActivationDate.plusDays(unlockAfterDays.toLong())
        if (today.isBefore(unlockDate)) {
            return FeatureGateState.TimeLocked(unlockDate)
        }

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

/**
 * المهام مبنية خلف البوابة ولكنها مخفية حاليًا من الواجهة.
 * عند الإطلاق: visible=true، ثم اضبط unlockAfterDays (0 = فورًا)
 * واربط requiredProductId بمنتج Google Play Billing المطلوب.
 */
object FutureFeatureGates {
    val TASKS = TimedPaidFeatureGate(
        featureId = "tasks",
        visible = false,
        unlockAfterDays = 0,
        requiredProductId = "khabir_tasks_access"
    )
}
