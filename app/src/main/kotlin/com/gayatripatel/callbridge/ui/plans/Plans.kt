package com.gayatripatel.callbridge.ui.plans

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.billing.SubscriptionGateway
import com.gayatripatel.callbridge.core.billing.Entitlement
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.presentation.PlanCardUi
import com.gayatripatel.callbridge.core.presentation.PlansPresenter
import com.gayatripatel.callbridge.data.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlansUiState(
    val cards: List<PlanCardUi> = emptyList(),
    val entitlementLabel: String = "",
    val billingAvailable: Boolean = true,
    val signedInEmail: String? = null,
    val message: String? = null,
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val billing: SubscriptionGateway,
    private val account: AccountRepository,
) : ViewModel() {
    private val highlight: String? = savedStateHandle["highlight"]
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlansUiState> = combine(
        billing.purchases, billing.prices, billing.available, account.session, message,
    ) { purchases, prices, available, session, msg ->
        val entitlement = EntitlementResolver.resolve(purchases)
        PlansUiState(
            cards = PlansPresenter.present(entitlement, prices, highlight),
            entitlementLabel = PlansPresenter.entitlementLabel(entitlement),
            billingAvailable = available,
            signedInEmail = session?.email,
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    init {
        viewModelScope.launch { billing.refresh() }
    }

    fun select(activity: Activity, card: PlanCardUi) {
        val entitlement = EntitlementResolver.resolve(billing.purchases.value)
        val launched = billing.launchPurchase(
            activity = activity,
            productId = card.productId,
            change = card.change,
            oldPurchaseToken = (entitlement as? Entitlement.Active)?.purchaseToken,
            obfuscatedAccountId = account.obfuscatedAccountId(),
        )
        message.value = if (launched) null else "Google Play isn't available on this device, so checkout can't open."
    }
}

@Composable
fun PlansRoute(onBack: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    PlansScreen(state, onSelect = { card -> activity?.let { viewModel.select(it, card) } }, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(state: PlansUiState, onSelect: (PlanCardUi) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plans") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Pick the plan that fits how you call", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text(state.entitlementLabel) })
                        state.signedInEmail?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                    (state.message ?: if (!state.billingAvailable) "Prices shown in USD until Google Play responds." else null)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.cards, key = { it.productId }) { card -> PlanCard(card, onSelect) }
            item {
                Text(
                    "Subscriptions renew automatically through Google Play until cancelled. Manage or cancel any time in " +
                        "Play Store › Payments & subscriptions. Upgrades are charged a prorated amount now; downgrades start at your next renewal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(card: PlanCardUi, onSelect: (PlanCardUi) -> Unit) {
    val emphasised = card.highlighted || card.isCurrent
    Card(
        border = if (emphasised) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (emphasised) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(card.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(card.price, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(" ${card.periodLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (card.highlighted && !card.isCurrent) {
                Text("Selected on the web", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            card.features.forEach { feature ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(feature, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (emphasised) {
                Button(onClick = { onSelect(card) }, enabled = card.actionEnabled, modifier = Modifier.fillMaxWidth()) { Text(card.actionLabel) }
            } else {
                OutlinedButton(onClick = { onSelect(card) }, enabled = card.actionEnabled, modifier = Modifier.fillMaxWidth()) { Text(card.actionLabel) }
            }
        }
    }
}
