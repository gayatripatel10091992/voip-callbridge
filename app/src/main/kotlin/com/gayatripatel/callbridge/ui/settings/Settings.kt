package com.gayatripatel.callbridge.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.billing.SubscriptionGateway
import com.gayatripatel.callbridge.core.billing.EntitlementResolver
import com.gayatripatel.callbridge.core.model.CallSettings
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.core.presentation.PlansPresenter
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.data.SettingsRepository
import com.gayatripatel.callbridge.ui.components.LineDot
import com.gayatripatel.callbridge.ui.components.SectionHeader
import com.gayatripatel.callbridge.ui.components.TabScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SettingsUiState(
    val settings: CallSettings = CallSettings(),
    val planLabel: String = "",
    val lines: List<PhoneLine> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    billing: SubscriptionGateway,
    lines: LinesRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(repo.settings, billing.purchases) { s, purchases ->
        SettingsUiState(s, PlansPresenter.entitlementLabel(EntitlementResolver.resolve(purchases)), lines.lines)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun update(transform: (CallSettings) -> CallSettings) = repo.update(transform)
}

@Composable
fun SettingsRoute(onOpenPlans: () -> Unit, onSignup: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state, viewModel::update, onOpenPlans, onSignup)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onUpdate: ((CallSettings) -> CallSettings) -> Unit,
    onOpenPlans: () -> Unit,
    onSignup: () -> Unit,
) {
    val s = state.settings
    TabScaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Your plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(state.planLabel, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Button(onClick = onOpenPlans) { Text("Plans") }
                }
            }

            SectionHeader("Your numbers")
            state.lines.forEach { line ->
                ListItem(
                    headlineContent = { Text(line.label) },
                    supportingContent = { Text(PhoneNumbers.display(line.number)) },
                    leadingContent = { LineDot(line.colorIndex) },
                )
            }
            ListItem(
                headlineContent = { Text("Add a number or port one in", color = MaterialTheme.colorScheme.primary) },
                modifier = Modifier.padding(start = 8.dp),
            )

            SectionHeader("Calling")
            SwitchRow("Prefer Wi-Fi calling", "Hands calls over to mobile data when Wi-Fi drops", s.wifiCallingPreferred) {
                onUpdate { c -> c.copy(wifiCallingPreferred = it) }
            }
            SwitchRow("Record calls", "Both sides hear an announcement first", s.recordCalls) {
                onUpdate { c -> c.copy(recordCalls = it) }
            }
            SwitchRow(
                "Forward calls",
                if (s.forwardingEnabled && s.forwardingNumber.isNotEmpty()) "To ${PhoneNumbers.display(s.forwardingNumber)}" else "Ring another phone when you're away",
                s.forwardingEnabled,
            ) { onUpdate { c -> c.copy(forwardingEnabled = it, forwardingNumber = c.forwardingNumber.ifEmpty { "+12125550147" }) } }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionHeader("Spam & blocking")
            SwitchRow("Screen unknown callers", "Send numbers that aren't in your contacts to voicemail", s.screenUnknownCallers) {
                onUpdate { c -> c.copy(screenUnknownCallers = it) }
            }
            ListItem(
                headlineContent = { Text("Spam sensitivity") },
                supportingContent = { Text("Calls scoring ${s.spamThreshold}+ go to voicemail, 95+ are blocked") },
            )
            ListItem(
                headlineContent = { Text("Blocked numbers") },
                supportingContent = { Text("${s.blockedNumbers.size} numbers and patterns") },
            )

            HorizontalDivider(Modifier.padding(top = 8.dp))
            SectionHeader("Account")
            ListItem(
                headlineContent = { Text("Create an account on the web") },
                supportingContent = { Text("Web signup that hands over to Google Play checkout") },
                modifier = Modifier.padding(bottom = 24.dp),
                trailingContent = { Button(onClick = onSignup) { Text("Start") } },
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
