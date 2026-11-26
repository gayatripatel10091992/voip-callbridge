package com.gayatripatel.callbridge.ui.voicemail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.model.Voicemail
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import com.gayatripatel.callbridge.core.presentation.Durations
import com.gayatripatel.callbridge.core.presentation.TimeLabels
import com.gayatripatel.callbridge.data.Clock
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.data.VoicemailRepository
import com.gayatripatel.callbridge.ui.components.Avatar
import com.gayatripatel.callbridge.ui.components.CountBadge
import com.gayatripatel.callbridge.ui.components.LineTag
import com.gayatripatel.callbridge.ui.components.TabScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class VoicemailRowUi(
    val id: Long,
    val title: String,
    val number: String,
    val timeLabel: String,
    val duration: String,
    val transcript: String?,
    val isNew: Boolean,
    val lineLabel: String,
    val lineColorIndex: Int,
)

fun voicemailRows(list: List<Voicemail>, lines: List<PhoneLine>, now: Long, zone: ZoneId): List<VoicemailRowUi> {
    val byId = lines.associateBy { it.id }
    return list.sortedByDescending { it.receivedAtMillis }.map {
        VoicemailRowUi(
            it.id, it.contactName ?: PhoneNumbers.display(it.number), it.number,
            TimeLabels.relative(it.receivedAtMillis, now, zone), Durations.format(it.durationSeconds.toLong()),
            it.transcript, it.isNew, byId[it.lineId]?.label.orEmpty(), byId[it.lineId]?.colorIndex ?: 0,
        )
    }
}

@HiltViewModel
class VoicemailViewModel @Inject constructor(
    private val repo: VoicemailRepository,
    lines: LinesRepository,
    clock: Clock,
    zone: ZoneId,
) : ViewModel() {
    val rows: StateFlow<List<VoicemailRowUi>> = repo.voicemails
        .map { voicemailRows(it, lines.lines, clock.now(), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(id: Long) = repo.markHeard(id)
}

@Composable
fun VoicemailRoute(onCall: (String) -> Unit, viewModel: VoicemailViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    VoicemailScreen(rows, viewModel::play, onCall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoicemailScreen(rows: List<VoicemailRowUi>, onPlay: (Long) -> Unit, onCall: (String) -> Unit) {
    TabScaffold(topBar = { TopAppBar(title = { Text("Voicemail") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(rows, key = { it.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(row.title)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.title, fontWeight = if (row.isNew) FontWeight.SemiBold else FontWeight.Normal)
                                    if (row.isNew) {
                                        Spacer(Modifier.width(8.dp))
                                        CountBadge(1, color = MaterialTheme.colorScheme.tertiary)
                                    }
                                }
                                Text(
                                    "${row.timeLabel} · ${row.duration}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            LineTag(row.lineLabel, row.lineColorIndex)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            row.transcript?.let { "“$it”" } ?: "Transcription isn't available for this message.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (row.transcript == null) FontStyle.Italic else FontStyle.Normal,
                            color = if (row.transcript == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(onClick = { onPlay(row.id) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play")
                            }
                            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                            Text(row.duration, style = MaterialTheme.typography.labelMedium)
                            IconButton(onClick = { onCall(row.number) }) {
                                Icon(Icons.Filled.Call, contentDescription = "Call back", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item { Text("New messages are transcribed on the Pro plan.", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
