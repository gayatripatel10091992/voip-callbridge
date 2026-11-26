package com.gayatripatel.callbridge.ui.incall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.core.call.CallEvent
import com.gayatripatel.callbridge.core.call.CallNetwork
import com.gayatripatel.callbridge.core.call.CallState
import com.gayatripatel.callbridge.core.call.CallStateMachine
import com.gayatripatel.callbridge.core.call.EndReason
import com.gayatripatel.callbridge.core.model.CallDirection
import com.gayatripatel.callbridge.core.model.CallRecord
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.core.presentation.Durations
import com.gayatripatel.callbridge.data.CallLogRepository
import com.gayatripatel.callbridge.data.Clock
import com.gayatripatel.callbridge.data.ContactsRepository
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.data.NetworkMonitor
import com.gayatripatel.callbridge.ui.components.Avatar
import com.gayatripatel.callbridge.ui.theme.CallRed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InCallUiState(
    val title: String,
    val subtitle: String,
    val status: String,
    val network: CallNetwork?,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val onHold: Boolean = false,
    val reconnecting: Boolean = false,
    val ended: Boolean = false,
    val canControl: Boolean = false,
)

/** Maps the pure call state to what the screen shows. Shared by the view model and the screenshot tests. */
fun inCallUiState(state: CallState, contactName: String?, number: String, nowMillis: Long): InCallUiState {
    val title = contactName ?: PhoneNumbers.display(number)
    val subtitle = if (contactName != null) PhoneNumbers.display(number) else "Unknown caller"
    return when (state) {
        CallState.Idle -> InCallUiState(title, subtitle, "Preparing…", null)
        is CallState.Dialing -> InCallUiState(title, subtitle, "Calling…", state.network)
        is CallState.Ringing -> InCallUiState(title, subtitle, "Incoming call", null)
        is CallState.Active -> InCallUiState(
            title, subtitle,
            status = if (state.onHold) "On hold" else Durations.format((nowMillis - state.startedAtMillis) / 1000),
            network = state.network, muted = state.muted, speaker = state.speaker, onHold = state.onHold, canControl = true,
        )
        is CallState.Reconnecting -> InCallUiState(
            title, subtitle, "Reconnecting…", null,
            muted = state.call.muted, speaker = state.call.speaker, reconnecting = true,
        )
        is CallState.Ended -> InCallUiState(
            title, subtitle,
            status = when (state.reason) {
                EndReason.COMPLETED -> "Call ended · ${Durations.format(state.durationMillis / 1000)}"
                EndReason.DECLINED -> "Call declined"
                EndReason.MISSED -> "Missed call"
                EndReason.DROPPED -> "Call dropped: no network"
                EndReason.FAILED -> "Call failed"
            },
            network = null, ended = true,
        )
    }
}

@HiltViewModel
class InCallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    contacts: ContactsRepository,
    private val clock: Clock,
    private val networkMonitor: NetworkMonitor,
    private val callLog: CallLogRepository,
    private val lines: LinesRepository,
) : ViewModel() {
    private val number: String = checkNotNull(savedStateHandle["number"])
    private val contactName = contacts.byNumber(number)?.name
    private val machine = MutableStateFlow<CallState>(CallState.Idle)
    private val now = MutableStateFlow(clock.now())

    val uiState: StateFlow<InCallUiState> = combine(machine, now) { state, t -> inCallUiState(state, contactName, number, t) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, inCallUiState(CallState.Idle, contactName, number, clock.now()))

    init {
        viewModelScope.launch {
            val network = networkMonitor.network.first() ?: CallNetwork.WIFI
            dispatch(CallEvent.Dial(number, network))
            // Demo signalling: the far end picks up after a short ring. The real app gets this from the SIP stack.
            delay(1_800)
            dispatch(CallEvent.RemoteAnswered(clock.now()))
        }
        viewModelScope.launch {
            networkMonitor.network.collect { dispatch(CallEvent.NetworkChanged(it, clock.now())) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                now.value = clock.now()
                dispatch(CallEvent.Tick(now.value))
            }
        }
    }

    fun dispatch(event: CallEvent) {
        val before = machine.value
        machine.update { CallStateMachine.reduce(it, event) }
        val after = machine.value
        if (before !is CallState.Ended && after is CallState.Ended) logCall(before, after)
    }

    fun hangUp() = dispatch(CallEvent.Hangup(clock.now()))

    private fun logCall(before: CallState, ended: CallState.Ended) {
        val network = when (before) {
            is CallState.Active -> before.network
            is CallState.Dialing -> before.network
            is CallState.Reconnecting -> before.call.network
            else -> CallNetwork.WIFI
        }
        callLog.add(
            CallRecord(
                id = callLog.nextId(),
                number = number,
                contactName = contactName,
                direction = CallDirection.OUTGOING,
                startedAtMillis = clock.now() - ended.durationMillis,
                durationSeconds = (ended.durationMillis / 1000).toInt(),
                lineId = lines.selected.value.id,
                viaWifi = network == CallNetwork.WIFI,
            ),
        )
    }
}

@Composable
fun InCallRoute(onFinished: () -> Unit, viewModel: InCallViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.ended) {
        if (state.ended) {
            delay(1_200)
            onFinished()
        }
    }
    InCallScreen(
        state,
        onMute = { viewModel.dispatch(CallEvent.ToggleMute) },
        onSpeaker = { viewModel.dispatch(CallEvent.ToggleSpeaker) },
        onHold = { viewModel.dispatch(CallEvent.ToggleHold) },
        onHangUp = viewModel::hangUp,
    )
}

@Composable
fun InCallScreen(
    state: InCallUiState,
    onMute: () -> Unit,
    onSpeaker: () -> Unit,
    onHold: () -> Unit,
    onHangUp: () -> Unit,
) {
    val background = Brush.verticalGradient(listOf(Color(0xFF0B2A55), Color(0xFF081A33), Color(0xFF050E1C)))
    Column(
        Modifier.fillMaxSize().background(background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        NetworkPill(state)
        Spacer(Modifier.height(28.dp))
        Avatar(state.title, size = 104.dp)
        Spacer(Modifier.height(20.dp))
        Text(state.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(state.subtitle, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        Text(state.status, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
        if (state.reconnecting) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(Modifier.width(140.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.2f))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ControlButton(if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic, if (state.muted) "Unmute" else "Mute", state.muted, state.canControl, onMute)
            ControlButton(Icons.Filled.Dialpad, "Keypad", false, state.canControl) {}
            ControlButton(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", state.speaker, state.canControl, onSpeaker)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ControlButton(Icons.Filled.Pause, if (state.onHold) "Resume" else "Hold", state.onHold, state.canControl, onHold)
            ControlButton(Icons.Filled.FiberManualRecord, "Record", false, state.canControl) {}
            Spacer(Modifier.size(72.dp))
        }
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(CallRed).clickable(enabled = !state.ended, onClick = onHangUp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun NetworkPill(state: InCallUiState) {
    val (icon, label) = when (state.network) {
        CallNetwork.WIFI -> Icons.Filled.Wifi to "Wi-Fi calling · HD voice"
        CallNetwork.MOBILE_DATA -> Icons.Filled.SignalCellularAlt to "Mobile data · HD voice"
        null -> Icons.Filled.SignalCellularAlt to if (state.reconnecting) "Looking for a network" else "CallBridge"
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape)
                .background(if (active) Color.White else Color.White.copy(alpha = if (enabled) 0.14f else 0.06f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = label,
                tint = if (active) Color(0xFF0B2A55) else Color.White.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f), style = MaterialTheme.typography.labelMedium)
    }
}
