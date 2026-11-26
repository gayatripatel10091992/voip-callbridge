package com.gayatripatel.callbridge.ui.dialer

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.core.model.Contact
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.data.ContactsRepository
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.ui.components.Avatar
import com.gayatripatel.callbridge.ui.components.LineDot
import com.gayatripatel.callbridge.ui.theme.CallGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class DialerUiState(
    val input: String = "",
    val suggestion: Contact? = null,
    val lines: List<PhoneLine> = emptyList(),
    val selectedLineId: String = "",
) {
    val formatted: String get() = PhoneNumbers.formatAsYouType(input)
    val dialable: String? get() = PhoneNumbers.toE164(input)
}

@HiltViewModel
class DialerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val contacts: ContactsRepository,
    private val lines: LinesRepository,
) : ViewModel() {
    private val input = MutableStateFlow(savedStateHandle.get<String>("number").orEmpty())

    val uiState: StateFlow<DialerUiState> = combine(input, lines.selected) { text, line ->
        DialerUiState(text, contacts.suggest(text.filter(Char::isDigit)), lines.lines, line.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialerUiState())

    fun press(key: Char) = input.update { if (it.length < 16) it + key else it }
    fun backspace() = input.update { it.dropLast(1) }
    fun clear() = input.update { "" }
    fun selectLine(id: String) = lines.select(id)
}

@Composable
fun DialerRoute(onBack: () -> Unit, onCall: (String) -> Unit, viewModel: DialerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DialerScreen(state, viewModel::press, viewModel::backspace, viewModel::clear, viewModel::selectLine, onCall, onBack)
}

private val keys = listOf(
    '1' to "", '2' to "ABC", '3' to "DEF",
    '4' to "GHI", '5' to "JKL", '6' to "MNO",
    '7' to "PQRS", '8' to "TUV", '9' to "WXYZ",
    '*' to "", '0' to "+", '#' to "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    state: DialerUiState,
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSelectLine: (String) -> Unit,
    onCall: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Keypad") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Call from", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically))
                state.lines.forEach { line ->
                    FilterChip(
                        selected = line.id == state.selectedLineId,
                        onClick = { onSelectLine(line.id) },
                        label = { Text(line.label) },
                        leadingIcon = { LineDot(line.colorIndex) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                state.formatted.ifEmpty { " " },
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
                state.suggestion?.let { contact ->
                    Row(
                        Modifier.clip(CircleShape).clickable { onCall(contact.number) }.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(contact.name, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("${contact.name} · ${PhoneNumbers.display(contact.number)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (digit, letters) -> DialKey(digit, letters) { onKey(digit) } }
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(56.dp))
                val dialable = state.dialable
                Box(
                    Modifier.size(72.dp).clip(CircleShape)
                        .background(if (dialable != null) CallGreen else CallGreen.copy(alpha = 0.4f))
                        .clickable(enabled = dialable != null) { dialable?.let(onCall) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = onBackspace, enabled = state.input.isNotEmpty(), modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
                }
            }
            if (state.input.isNotEmpty()) {
                Text(
                    "Clear",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onClear).padding(bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun DialKey(digit: Char, letters: String, onClick: () -> Unit) {
    Column(
        Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("$digit", fontSize = 28.sp)
        if (letters.isNotEmpty()) {
            Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
