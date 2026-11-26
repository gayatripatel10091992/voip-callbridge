package com.gayatripatel.callbridge.data

import com.gayatripatel.callbridge.core.link.HandoffResult
import com.gayatripatel.callbridge.core.model.CallRecord
import com.gayatripatel.callbridge.core.model.CallSettings
import com.gayatripatel.callbridge.core.model.Contact
import com.gayatripatel.callbridge.core.model.DeliveryStatus
import com.gayatripatel.callbridge.core.model.Message
import com.gayatripatel.callbridge.core.model.MessageThread
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.model.Voicemail
import com.gayatripatel.callbridge.core.sample.SampleData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Wall-clock abstraction so view models can be driven by a fake clock in tests. */
fun interface Clock {
    fun now(): Long
}

/*
 * In-memory repositories seeded with demo data. In the production app these sit on top of the
 * VoIP/SMS backend APIs and a local cache; the view models only depend on these flows.
 */

@Singleton
class LinesRepository @Inject constructor() {
    val lines: List<PhoneLine> = SampleData.lines
    private val _selected = MutableStateFlow(lines.first())
    val selected: StateFlow<PhoneLine> = _selected.asStateFlow()

    fun select(lineId: String) {
        lines.firstOrNull { it.id == lineId }?.let { _selected.value = it }
    }
}

@Singleton
class ContactsRepository @Inject constructor() {
    val contacts: List<Contact> = SampleData.contacts

    fun byNumber(e164: String): Contact? = contacts.firstOrNull { it.number == e164 }

    /** Best match for what's typed on the keypad (digits only). */
    fun suggest(digits: String): Contact? =
        if (digits.length < 3) null else contacts.firstOrNull { it.number.filter(Char::isDigit).contains(digits) }
}

@Singleton
class CallLogRepository @Inject constructor(clock: Clock) {
    private val _calls = MutableStateFlow(SampleData.calls(clock.now()))
    val calls: StateFlow<List<CallRecord>> = _calls.asStateFlow()

    fun add(record: CallRecord) = _calls.update { listOf(record) + it }

    fun nextId(): Long = (_calls.value.maxOfOrNull { it.id } ?: 0) + 1
}

@Singleton
class MessageRepository @Inject constructor(private val clock: Clock) {
    private val _threads = MutableStateFlow(SampleData.threads(clock.now()))
    val threads: StateFlow<List<MessageThread>> = _threads.asStateFlow()
    private val _messages = MutableStateFlow(
        _threads.value.associate { it.id to SampleData.messages(it.id, clock.now()) },
    )

    fun thread(id: String): MessageThread? = _threads.value.firstOrNull { it.id == id }

    fun messages(threadId: String): Flow<List<Message>> = _messages.map { it[threadId].orEmpty() }

    fun markRead(threadId: String) = _threads.update { list ->
        list.map { if (it.id == threadId) it.copy(unreadCount = 0) else it }
    }

    fun send(threadId: String, body: String): Long {
        val id = clock.now()
        val message = Message(id, threadId, fromMe = true, senderName = null, body = body, sentAtMillis = id, status = DeliveryStatus.SENDING)
        _messages.update { it + (threadId to it[threadId].orEmpty() + message) }
        _threads.update { list ->
            list.map { if (it.id == threadId) it.copy(lastMessage = body, lastAtMillis = id) else it }
                .sortedByDescending { it.lastAtMillis }
        }
        return id
    }

    fun updateStatus(threadId: String, messageId: Long, status: DeliveryStatus) = _messages.update { all ->
        all + (threadId to all[threadId].orEmpty().map { if (it.id == messageId) it.copy(status = status) else it })
    }
}

@Singleton
class VoicemailRepository @Inject constructor(clock: Clock) {
    private val _voicemails = MutableStateFlow(SampleData.voicemails(clock.now()))
    val voicemails: StateFlow<List<Voicemail>> = _voicemails.asStateFlow()

    fun markHeard(id: Long) = _voicemails.update { list -> list.map { if (it.id == id) it.copy(isNew = false) else it } }
}

@Singleton
class SettingsRepository @Inject constructor() {
    private val _settings = MutableStateFlow(CallSettings(blockedNumbers = listOf("+19005550110", "+1900*")))
    val settings: StateFlow<CallSettings> = _settings.asStateFlow()

    fun update(transform: (CallSettings) -> CallSettings) = _settings.update(transform)
}

/** Holds the account session handed over from the web signup funnel. */
@Singleton
class AccountRepository @Inject constructor() {
    private val _session = MutableStateFlow<HandoffResult.Valid?>(null)
    val session: StateFlow<HandoffResult.Valid?> = _session.asStateFlow()

    fun startSession(handoff: HandoffResult.Valid) {
        _session.value = handoff
    }

    /**
     * Sent to Play as `obfuscatedAccountId` so the backend can match the purchase to the web account.
     * Play limits it to 64 characters and it must not contain personal data, so it's a hash, never the email.
     */
    fun obfuscatedAccountId(): String? = _session.value?.sessionToken?.let { token ->
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
