package com.gayatripatel.callbridge.core.presentation

import com.gayatripatel.callbridge.core.billing.BillingPeriod
import com.gayatripatel.callbridge.core.billing.Entitlement
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.billing.PlanCatalog
import com.gayatripatel.callbridge.core.billing.PlanChange

data class PlanCardUi(
    val productId: String,
    val name: String,
    val price: String,
    val periodLabel: String,
    val features: List<String>,
    val actionLabel: String,
    val actionEnabled: Boolean,
    val isCurrent: Boolean,
    val highlighted: Boolean,
    val change: PlanChange,
)

object PlansPresenter {

    fun present(entitlement: Entitlement, prices: Map<String, String>, highlightProductId: String?): List<PlanCardUi> =
        PlanCatalog.plans.map { plan ->
            val change = EntitlementResolver.planChange(entitlement, plan)
            val (label, enabled) = when {
                entitlement is Entitlement.Pending -> "Purchase pending" to false
                change == PlanChange.NEW_PURCHASE -> "Subscribe" to true
                change == PlanChange.NO_CHANGE -> "Current plan" to false
                change == PlanChange.UPGRADE_CHARGE_PRORATED -> "Upgrade" to true
                change == PlanChange.DOWNGRADE_DEFERRED -> "Switch at renewal" to true
                else -> "Switch" to true
            }
            PlanCardUi(
                productId = plan.productId,
                name = plan.name,
                price = prices[plan.productId] ?: plan.fallbackPrice,
                periodLabel = if (plan.period == BillingPeriod.MONTHLY) "/ month" else "/ year",
                features = plan.features,
                actionLabel = label,
                actionEnabled = enabled,
                isCurrent = change == PlanChange.NO_CHANGE,
                highlighted = plan.productId == highlightProductId,
                change = change,
            )
        }

    fun entitlementLabel(entitlement: Entitlement): String = when (entitlement) {
        Entitlement.None -> "No active plan"
        is Entitlement.Pending -> "Payment pending for ${entitlement.plan.name}"
        is Entitlement.Active ->
            if (entitlement.autoRenewing) "${entitlement.plan.name} · renews automatically"
            else "${entitlement.plan.name} · cancelled, active until the period ends"
    }
}
