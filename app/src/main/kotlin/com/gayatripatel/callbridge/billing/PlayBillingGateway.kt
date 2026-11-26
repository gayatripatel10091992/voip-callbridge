package com.gayatripatel.callbridge.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.SubscriptionUpdateParams.ReplacementMode
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.billing.PlanCatalog
import com.gayatripatel.callbridge.core.billing.PlanChange
import com.gayatripatel.callbridge.core.billing.PurchaseSnapshot
import com.gayatripatel.callbridge.core.billing.PurchaseState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** What the UI needs from billing. Lets screens and tests run without Google Play. */
interface SubscriptionGateway {
    val purchases: StateFlow<List<PurchaseSnapshot>>

    /** Localised recurring price per product id. */
    val prices: StateFlow<Map<String, String>>
    val available: StateFlow<Boolean>

    suspend fun refresh()

    fun launchPurchase(
        activity: Activity,
        productId: String,
        change: PlanChange,
        oldPurchaseToken: String?,
        obfuscatedAccountId: String?,
    ): Boolean
}

/**
 * Google Play Billing Library 7 wrapper.
 *
 * Everything Play-specific stops here: purchases are converted to [PurchaseSnapshot] and the entitlement
 * rules live in the pure `core` module, which is what kept subscriptions working across library upgrades.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext context: Context,
) : SubscriptionGateway, PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val _purchases = MutableStateFlow<List<PurchaseSnapshot>>(emptyList())
    override val purchases: StateFlow<List<PurchaseSnapshot>> = _purchases.asStateFlow()

    private val _prices = MutableStateFlow(PlanCatalog.plans.associate { it.productId to it.fallbackPrice })
    override val prices: StateFlow<Map<String, String>> = _prices.asStateFlow()

    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().enablePrepaidPlans().build(),
        )
        .build()

    override suspend fun refresh() {
        val connected = connect()
        _available.value = connected
        if (!connected) return

        val productParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                PlanCatalog.productIds.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val details = client.queryProductDetails(productParams)
        details.productDetailsList.orEmpty().forEach { pd ->
            productDetails[pd.productId] = pd
            recurringPrice(pd)?.let { price -> _prices.update { it + (pd.productId to price) } }
        }

        val owned = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (owned.billingResult.responseCode == BillingResponseCode.OK) {
            onPurchases(owned.purchasesList, replace = true)
        }
    }

    override fun launchPurchase(
        activity: Activity,
        productId: String,
        change: PlanChange,
        oldPurchaseToken: String?,
        obfuscatedAccountId: String?,
    ): Boolean {
        val details = productDetails[productId] ?: return false
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false

        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build(),
            ),
        )
        if (obfuscatedAccountId != null) builder.setObfuscatedAccountId(obfuscatedAccountId)

        val mode = when (change) {
            PlanChange.UPGRADE_CHARGE_PRORATED -> ReplacementMode.CHARGE_PRORATED_PRICE
            PlanChange.DOWNGRADE_DEFERRED -> ReplacementMode.DEFERRED
            PlanChange.SAME_TIER_SWITCH -> ReplacementMode.WITH_TIME_PRORATION
            PlanChange.NEW_PURCHASE, PlanChange.NO_CHANGE -> null
        }
        if (oldPurchaseToken != null && mode != null) {
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(oldPurchaseToken)
                    .setSubscriptionReplacementMode(mode)
                    .build(),
            )
        }
        return client.launchBillingFlow(activity, builder.build()).responseCode == BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> onPurchases(purchases.orEmpty(), replace = false)
            BillingResponseCode.USER_CANCELED -> Log.i(TAG, "Purchase cancelled by user")
            // Renewals and state changes that happened while the app was closed are picked up on the next refresh.
            else -> Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun onPurchases(purchases: List<Purchase>, replace: Boolean) {
        val snapshots = purchases.flatMap { p ->
            p.products.map { productId ->
                PurchaseSnapshot(
                    productId = productId,
                    purchaseToken = p.purchaseToken,
                    state = when (p.purchaseState) {
                        Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
                        Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
                        else -> PurchaseState.UNSPECIFIED
                    },
                    acknowledged = p.isAcknowledged,
                    autoRenewing = p.isAutoRenewing,
                    purchaseTimeMillis = p.purchaseTime,
                )
            }
        }
        _purchases.update { current ->
            if (replace) snapshots else (current.filterNot { old -> snapshots.any { it.purchaseToken == old.purchaseToken } } + snapshots)
        }
        // Production: verify the token on the backend first; the server grants access and acknowledges.
        EntitlementResolver.tokensToAcknowledge(snapshots).forEach { token -> acknowledge(token) }
    }

    private fun acknowledge(token: String) = scope.launch {
        val result = client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build())
        if (result.responseCode == BillingResponseCode.OK) {
            _purchases.update { list -> list.map { if (it.purchaseToken == token) it.copy(acknowledged = true) else it } }
        }
    }

    private suspend fun connect(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    _available.value = false
                }
            })
        }
    }

    private fun recurringPrice(pd: ProductDetails): String? =
        pd.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice

    private companion object {
        const val TAG = "PlayBilling"
    }
}
