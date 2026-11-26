package com.gayatripatel.callbridge.core.model

/** One of the user's virtual numbers. A single device can hold several lines. */
data class PhoneLine(
    val id: String,
    val label: String,
    /** E.164, e.g. +14155550134 */
    val number: String,
    val colorIndex: Int,
)

enum class CallDirection { INCOMING, OUTGOING, MISSED, BLOCKED }

data class CallRecord(
    val id: Long,
    /** E.164 */
    val number: String,
    val contactName: String?,
    val direction: CallDirection,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val lineId: String,
    val viaWifi: Boolean,
    val hasRecording: Boolean = false,
    val likelySpam: Boolean = false,
)

data class Contact(val name: String, val number: String)

data class MessageThread(
    val id: String,
    val participants: List<Contact>,
    val lastMessage: String,
    val lastAtMillis: Long,
    val unreadCount: Int,
    val lineId: String,
    val lastHasAttachment: Boolean = false,
) {
    val isGroup: Boolean get() = participants.size > 1
    // First names for people; numbers without a saved name stay whole ("+1 (312) 555-0199", not "+1").
    val title: String get() = participants.joinToString(", ") { if (it.name.first().isLetter()) it.name.substringBefore(' ') else it.name }
}

enum class DeliveryStatus { SENDING, SENT, DELIVERED, FAILED }

data class Message(
    val id: Long,
    val threadId: String,
    val fromMe: Boolean,
    val senderName: String?,
    val body: String,
    val sentAtMillis: Long,
    val status: DeliveryStatus = DeliveryStatus.DELIVERED,
    /** MMS: a picture shown as a tile in the conversation. */
    val attachmentLabel: String? = null,
)

data class Voicemail(
    val id: Long,
    val number: String,
    val contactName: String?,
    val receivedAtMillis: Long,
    val durationSeconds: Int,
    val transcript: String?,
    val isNew: Boolean,
    val lineId: String,
)

data class CallSettings(
    val wifiCallingPreferred: Boolean = true,
    val forwardingEnabled: Boolean = false,
    val forwardingNumber: String = "",
    val screenUnknownCallers: Boolean = false,
    val spamThreshold: Int = 70,
    val recordCalls: Boolean = false,
    val blockedNumbers: List<String> = emptyList(),
)
