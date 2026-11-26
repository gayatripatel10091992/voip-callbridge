package com.gayatripatel.callbridge.ui.recents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.core.model.CallDirection
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.presentation.CallRowUi
import com.gayatripatel.callbridge.core.presentation.RecentsFilter
import com.gayatripatel.callbridge.core.presentation.RecentsPresenter
import com.gayatripatel.callbridge.data.CallLogRepository
import com.gayatripatel.callbridge.data.Clock
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.ui.components.Avatar
import com.gayatripatel.callbridge.ui.components.LineTag
import com.gayatripatel.callbridge.ui.components.TabScaffold
import com.gayatripatel.callbridge.ui.theme.CallGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class RecentsUiState(
    val rows: List<CallRowUi> = emptyList(),
    val filter: RecentsFilter = RecentsFilter.ALL,
    val activeLine: PhoneLine? = null,
)

@HiltViewModel
class RecentsViewModel @Inject constructor(
    callLog: CallLogRepository,
    lines: LinesRepository,
    clock: Clock,
    zone: ZoneId,
) : ViewModel() {
    private val filter = MutableStateFlow(RecentsFilter.ALL)
    private val allLines = lines.lines

    val uiState: StateFlow<RecentsUiState> = combine(callLog.calls, filter, lines.selected) { calls, f, line ->
        RecentsUiState(RecentsPresenter.present(calls, allLines, f, clock.now(), zone), f, line)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentsUiState())

    fun setFilter(value: RecentsFilter) {
        filter.value = value
    }
}

@Composable
fun RecentsRoute(onCall: (String) -> Unit, onOpenDialer: () -> Unit, viewModel: RecentsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecentsScreen(state, viewModel::setFilter, onCall, onOpenDialer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    state: RecentsUiState,
    onFilterChange: (RecentsFilter) -> Unit,
    onCall: (String) -> Unit,
    onOpenDialer: () -> Unit,
) {
    TabScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Calls")
                        state.activeLine?.let {
                            LineTag("${it.label} · ${com.gayatripatel.callbridge.core.phone.PhoneNumbers.display(it.number)}", it.colorIndex)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenDialer) { Icon(Icons.Filled.Dialpad, contentDescription = "Open keypad") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.filter == RecentsFilter.ALL, { onFilterChange(RecentsFilter.ALL) }, label = { Text("All") })
                    FilterChip(state.filter == RecentsFilter.MISSED, { onFilterChange(RecentsFilter.MISSED) }, label = { Text("Missed") })
                }
            }
            items(state.rows, key = { it.key }) { row -> CallRow(row, onCall) }
        }
    }
}

@Composable
private fun CallRow(row: CallRowUi, onCall: (String) -> Unit) {
    val missed = row.direction == CallDirection.MISSED
    val titleColor = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable { onCall(row.number) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(if (row.likelySpam) null else row.title, warning = row.likelySpam)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.title + if (row.count > 1) " (${row.count})" else "",
                    color = titleColor,
                    fontWeight = if (missed) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectionIcon(row.direction)
                Spacer(Modifier.width(4.dp))
                Text(
                    row.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.likelySpam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(row.timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (row.viaWifi) Icons.Filled.Wifi else Icons.Filled.SignalCellularAlt,
                    contentDescription = if (row.viaWifi) "Wi-Fi call" else "Mobile data call",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                LineTag(row.lineLabel, row.lineColorIndex)
            }
        }
        IconButton(onClick = { onCall(row.number) }) {
            Icon(Icons.Filled.Call, contentDescription = "Call ${row.title}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DirectionIcon(direction: CallDirection) {
    val (icon, tint, label) = when (direction) {
        CallDirection.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, CallGreen, "Incoming")
        CallDirection.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, MaterialTheme.colorScheme.primary, "Outgoing")
        CallDirection.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, MaterialTheme.colorScheme.error, "Missed")
        CallDirection.BLOCKED -> Triple(Icons.Filled.Block, MaterialTheme.colorScheme.onSurfaceVariant, "Blocked")
    }
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
}
