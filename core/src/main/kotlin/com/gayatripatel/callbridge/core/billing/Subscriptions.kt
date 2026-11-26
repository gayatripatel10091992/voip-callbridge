package com.gayatripatel.callbridge.core.billing

enum class BillingPeriod { MONTHLY, ANNUAL }

data class Plan(
    val productId: String,
    val name: String,
    val tier: Int,
    val period: BillingPeriod,
    val numbersIncluded: Int,
    val features: List<String>,
    /** Shown only until Play returns the localised price from ProductDetails. */
    val fallbackPrice: String,
)

object PlanCatalog {
    val plans = listOf(
        Plan(
            "callbridge_basic_monthly", "Basic", 1, BillingPeriod.MONTHLY, 1,
            listOf("1 phone number", "Unlimited US & Canada calls", "SMS & MMS"), "$9.99",
        ),
        Plan(
            "callbridge_pro_monthly", "Pro", 2, BillingPeriod.MONTHLY, 2,
            listOf("2 phone numbers", "Voicemail transcription", "Call recording", "Spam screening"), "$15.99",
        ),
        Plan(
            "callbridge_pro_annual", "Pro annual", 2, BillingPeriod.ANNUAL, 2,
            listOf("Everything in Pro", "2 months free"), "$159.99",
        ),
        Plan(
            "callbridge_business_monthly", "Business", 3, BillingPeriod.MONTHLY, 5,
            listOf("5 phone numbers", "Call forwarding rules", "Group texts up to 50"), "$29.99",
        ),
    )

    val productIds: List<String> get() = plans.map { it.productId }
    fun find(productId: String): Plan? = plans.firstOrNull { it.productId == productId }
}

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Billing-library-agnostic copy of a Play `Purchase`, so entitlement rules survive library upgrades. */
data class PurchaseSnapshot(
    val productId: String,
    val purchaseToken: String,
    val state: PurchaseState,
    val acknowledged: Boolean,
    val autoRenewing: Boolean,
    val purchaseTimeMillis: Long,
)

sealed interface Entitlement {
    data object None : Entitlement
    data class Pending(val plan: Plan) : Entitlement

    /** Access granted. `needsAcknowledgement` purchases are refunded by Play after 3 days if never acknowledged. */
    data class Active(
        val plan: Plan,
        val purchaseToken: String,
        val autoRenewing: Boolean,
        val needsAcknowledgement: Boolean,
    ) : Entitlement
}

/** How a plan change should be charged when calling `setSubscriptionReplacementMode`. */
enum class PlanChange { NEW_PURCHASE, UPGRADE_CHARGE_PRORATED, DOWNGRADE_DEFERRED, SAME_TIER_SWITCH, NO_CHANGE }

object EntitlementResolver {

    fun resolve(purchases: List<PurchaseSnapshot>, catalog: PlanCatalog = PlanCatalog): Entitlement {
        val known = purchases.mapNotNull { p -> catalog.find(p.productId)?.let { p to it } }
        // During an upgrade Play can briefly report both the old and new purchase: the highest tier wins.
        val active = known
            .filter { (p, _) -> p.state == PurchaseState.PURCHASED }
            .maxWithOrNull(compareBy({ it.second.tier }, { it.first.purchaseTimeMillis }))
        if (active != null) {
            val (purchase, plan) = active
            return Entitlement.Active(plan, purchase.purchaseToken, purchase.autoRenewing, !purchase.acknowledged)
        }
        val pending = known.firstOrNull { (p, _) -> p.state == PurchaseState.PENDING }
        return if (pending != null) Entitlement.Pending(pending.second) else Entitlement.None
    }

    /** Tokens of purchased-but-unacknowledged subscriptions. Pending purchases must not be acknowledged. */
    fun tokensToAcknowledge(purchases: List<PurchaseSnapshot>): List<String> =
        purchases.filter { it.state == PurchaseState.PURCHASED && !it.acknowledged }.map { it.purchaseToken }

    fun planChange(current: Entitlement, target: Plan): PlanChange {
        if (current !is Entitlement.Active) return PlanChange.NEW_PURCHASE
        return when {
            current.plan.productId == target.productId -> PlanChange.NO_CHANGE
            target.tier > current.plan.tier -> PlanChange.UPGRADE_CHARGE_PRORATED
            target.tier < current.plan.tier -> PlanChange.DOWNGRADE_DEFERRED
            else -> PlanChange.SAME_TIER_SWITCH
        }
    }
}
