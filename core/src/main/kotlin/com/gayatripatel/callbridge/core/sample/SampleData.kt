package com.gayatripatel.callbridge.core.sample

import com.gayatripatel.callbridge.core.model.CallDirection
import com.gayatripatel.callbridge.core.model.CallRecord
import com.gayatripatel.callbridge.core.model.Contact
import com.gayatripatel.callbridge.core.model.DeliveryStatus
import com.gayatripatel.callbridge.core.model.Message
import com.gayatripatel.callbridge.core.model.MessageThread
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.model.Voicemail
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic demo data. Everything is relative to [now] so the app looks alive at any time,
 * while screenshot tests pass [FIXED_NOW] for stable images. All numbers use the 555-01xx fictional range.
 */
object SampleData {
    val ZONE: ZoneId = ZoneId.of("America/New_York")
    val FIXED_NOW: Long = ZonedDateTime.of(2026, 11, 20, 15, 30, 0, 0, ZONE).toInstant().toEpochMilli()

    val lines = listOf(
        PhoneLine("work", "Work", "+14155550100", 0),
        PhoneLine("personal", "Personal", "+16465550111", 1),
    )

    val contacts = listOf(
        Contact("Maya Chen", "+14155550134"),
        Contact("Daniel Brooks", "+12125550147"),
        Contact("Priya Raman", "+13125550162"),
        Contact("Lucas Moreau", "+16175550178"),
        Contact("Sofia Alvarez", "+13055550189"),
        Contact("Ethan Walker", "+17185550193"),
    )

    private const val MIN = 60_000L
    private const val HOUR = 60 * MIN
    private const val DAY = 24 * HOUR

    fun calls(now: Long): List<CallRecord> {
        val c = contacts
        return listOf(
            CallRecord(1, c[0].number, c[0].name, CallDirection.INCOMING, now - 12 * MIN, 754, "work", viaWifi = true, hasRecording = true),
            CallRecord(2, "+18885550123", null, CallDirection.MISSED, now - 48 * MIN, 0, "work", viaWifi = true, likelySpam = true),
            CallRecord(3, c[1].number, c[1].name, CallDirection.OUTGOING, now - 2 * HOUR, 312, "work", viaWifi = false),
            CallRecord(4, c[2].number, c[2].name, CallDirection.MISSED, now - 3 * HOUR, 0, "personal", viaWifi = true),
            CallRecord(5, c[2].number, c[2].name, CallDirection.MISSED, now - 3 * HOUR - 6 * MIN, 0, "personal", viaWifi = true),
            CallRecord(6, c[3].number, c[3].name, CallDirection.OUTGOING, now - DAY - 2 * HOUR, 1890, "work", viaWifi = true),
            CallRecord(7, "+15125550175", null, CallDirection.INCOMING, now - DAY - 5 * HOUR, 95, "personal", viaWifi = false),
            CallRecord(8, c[4].number, c[4].name, CallDirection.INCOMING, now - 2 * DAY, 421, "personal", viaWifi = true),
            CallRecord(9, "+19005550110", null, CallDirection.BLOCKED, now - 3 * DAY, 0, "work", viaWifi = true, likelySpam = true),
            CallRecord(10, c[5].number, c[5].name, CallDirection.OUTGOING, now - 4 * DAY, 66, "work", viaWifi = false),
            CallRecord(11, c[0].number, c[0].name, CallDirection.OUTGOING, now - 9 * DAY, 1204, "work", viaWifi = true),
        )
    }

    fun threads(now: Long): List<MessageThread> = listOf(
        MessageThread("t-maya", listOf(contacts[0]), "Sounds good, see you at 4!", now - 5 * MIN, 2, "work"),
        MessageThread(
            "t-launch", listOf(contacts[1], contacts[2], contacts[3]),
            "Priya: Shared the final deck", now - 40 * MIN, 5, "work", lastHasAttachment = true,
        ),
        MessageThread("t-sofia", listOf(contacts[4]), "Thanks for the quick call", now - 6 * HOUR, 0, "personal"),
        MessageThread("t-ethan", listOf(contacts[5]), "Can you send me the address?", now - DAY - 3 * HOUR, 0, "work"),
        MessageThread("t-pharmacy", listOf(Contact("+1 (312) 555-0199", "+13125550199")), "Your order is ready for pickup", now - 3 * DAY, 0, "personal"),
    )

    fun messages(threadId: String, now: Long): List<Message> = when (threadId) {
        "t-launch" -> listOf(
            Message(1, threadId, false, "Daniel", "Are we still on for the launch review?", now - 2 * HOUR),
            Message(2, threadId, true, null, "Yes, 4pm. I'll dial everyone in from my work line.", now - 2 * HOUR + 3 * MIN),
            Message(3, threadId, false, "Lucas", "Works for me 👍", now - HOUR - 20 * MIN),
            Message(4, threadId, false, "Priya", "Shared the final deck", now - 40 * MIN, attachmentLabel = "launch-deck.png"),
            Message(5, threadId, true, null, "Got it, looks great", now - 38 * MIN, DeliveryStatus.SENT),
        )
        else -> listOf(
            Message(1, threadId, false, "Maya", "Hey! Did the new number port over?", now - 3 * HOUR),
            Message(2, threadId, true, null, "Yes, it finished this morning. Calls and texts both work.", now - 3 * HOUR + 2 * MIN),
            Message(3, threadId, false, "Maya", "Perfect. Can we talk through the contract later?", now - 20 * MIN),
            Message(4, threadId, true, null, "Sure, 4pm?", now - 8 * MIN),
            Message(5, threadId, false, "Maya", "Sounds good, see you at 4!", now - 5 * MIN),
        )
    }

    fun voicemails(now: Long): List<Voicemail> = listOf(
        Voicemail(
            1, contacts[2].number, contacts[2].name, now - 3 * HOUR, 34,
            "Hi, it's Priya. I tried you twice, call me back when you get this. It's about Friday's demo.", true, "personal",
        ),
        Voicemail(
            2, "+15125550175", null, now - DAY - 4 * HOUR, 22,
            "Hello, this is a reminder about your appointment tomorrow at 10 AM. Please call to confirm.", true, "personal",
        ),
        Voicemail(3, contacts[5].number, contacts[5].name, now - 5 * DAY, 12, null, false, "work"),
    )
}
