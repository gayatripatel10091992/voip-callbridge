package com.gayatripatel.callbridge.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gayatripatel.callbridge.core.model.DeliveryStatus
import com.gayatripatel.callbridge.core.model.Message
import com.gayatripatel.callbridge.core.model.MessageThread
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.presentation.TimeLabels
import com.gayatripatel.callbridge.data.Clock
import com.gayatripatel.callbridge.data.LinesRepository
import com.gayatripatel.callbridge.data.MessageRepository
import com.gayatripatel.callbridge.ui.components.Avatar
import com.gayatripatel.callbridge.ui.components.CountBadge
import com.gayatripatel.callbridge.ui.components.LineTag
import com.gayatripatel.callbridge.ui.components.TabScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class ThreadRowUi(
    val id: String,
    val title: String,
    val preview: String,
    val timeLabel: String,
    val unread: Int,
    val isGroup: Boolean,
    val hasAttachment: Boolean,
    val lineLabel: String,
    val lineColorIndex: Int,
)

fun threadRows(threads: List<MessageThread>, lines: List<PhoneLine>, now: Long, zone: ZoneId): List<ThreadRowUi> {
    val byId = lines.associateBy { it.id }
    return threads.sortedByDescending { it.lastAtMillis }.map {
        ThreadRowUi(
            it.id, it.title, it.lastMessage, TimeLabels.relative(it.lastAtMillis, now, zone), it.unreadCount, it.isGroup,
            it.lastHasAttachment, byId[it.lineId]?.label.orEmpty(), byId[it.lineId]?.colorIndex ?: 0,
        )
    }
}

@HiltViewModel
class MessagesViewModel @Inject constructor(repo: MessageRepository, lines: LinesRepository, clock: Clock, zone: ZoneId) : ViewModel() {
    val rows: StateFlow<List<ThreadRowUi>> = repo.threads
        .map { threadRows(it, lines.lines, clock.now(), zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun MessagesRoute(onOpen: (String) -> Unit, viewModel: MessagesViewModel = hiltViewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    MessagesScreen(rows, onOpen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(rows: List<ThreadRowUi>, onOpen: (String) -> Unit) {
    TabScaffold(
        topBar = { TopAppBar(title = { Text("Messages") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {}) { Icon(Icons.Filled.Edit, contentDescription = "New message") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(rows, key = { it.id }) { row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(row.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(row.title, isGroup = row.isGroup, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (row.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(row.timeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (row.hasAttachment) {
                                Icon(Icons.Filled.Image, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                row.preview, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (row.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (row.unread > 0) FontWeight.Medium else FontWeight.Normal,
                            )
                            if (row.unread > 0) CountBadge(row.unread)
                        }
                        LineTag(row.lineLabel, row.lineColorIndex)
                    }
                }
            }
        }
    }
}

data class ConversationUiState(
    val title: String = "",
    val subtitle: String = "",
    val isGroup: Boolean = false,
    val callNumber: String? = null,
    val messages: List<Message> = emptyList(),
    val draft: String = "",
)

@HiltViewModel
class ConversationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: MessageRepository,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val draft = MutableStateFlow("")

    val uiState: StateFlow<ConversationUiState> = combine(repo.messages(threadId), draft) { messages, text ->
        conversationUiState(repo.thread(threadId), messages, text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    init {
        repo.markRead(threadId)
    }

    fun onDraftChange(text: String) {
        draft.value = text
    }

    fun send() {
        val body = draft.value.trim()
        if (body.isEmpty()) return
        draft.value = ""
        val id = repo.send(threadId, body)
        viewModelScope.launch {
            delay(600)
            repo.updateStatus(threadId, id, DeliveryStatus.SENT)
            delay(900)
            repo.updateStatus(threadId, id, DeliveryStatus.DELIVERED)
        }
    }
}

fun conversationUiState(thread: MessageThread?, messages: List<Message>, draft: String) = ConversationUiState(
    title = thread?.title.orEmpty(),
    subtitle = when {
        thread == null -> ""
        thread.isGroup -> "Group · ${thread.participants.size + 1} people"
        else -> com.gayatripatel.callbridge.core.phone.PhoneNumbers.display(thread.participants.first().number)
    },
    isGroup = thread?.isGroup == true,
    callNumber = thread?.participants?.singleOrNull()?.number,
    messages = messages,
    draft = draft,
)

@Composable
fun ConversationRoute(onBack: () -> Unit, onCall: (String) -> Unit, viewModel: ConversationViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ConversationScreen(state, ZoneId.systemDefault(), viewModel::onDraftChange, viewModel::send, onBack, onCall)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    state: ConversationUiState,
    zone: ZoneId,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(state.title, isGroup = state.isGroup, size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(state.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    state.callNumber?.let { number ->
                        IconButton(onClick = { onCall(number) }) { Icon(Icons.Filled.Call, contentDescription = "Call") }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().imePadding().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Text message") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                IconButton(onClick = onSend, enabled = state.draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.messages, key = { it.id }) { Bubble(it, state.isGroup, zone) }
        }
    }
}

@Composable
private fun Bubble(message: Message, showSender: Boolean, zone: ZoneId) {
    val mine = message.fromMe
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (showSender && !mine && message.senderName != null) {
            Text(
                message.senderName!!, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                message.attachmentLabel?.let { label ->
                    Box(
                        Modifier.width(200.dp).height(120.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(message.body, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
            }
        }
        val status = when {
            !mine -> null
            message.status == DeliveryStatus.SENDING -> "Sending…"
            message.status == DeliveryStatus.SENT -> "Sent"
            message.status == DeliveryStatus.DELIVERED -> "Delivered"
            else -> "Not sent · tap to retry"
        }
        Text(
            listOfNotNull(TimeLabels.clockTime(message.sentAtMillis, zone), status).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = if (message.status == DeliveryStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
