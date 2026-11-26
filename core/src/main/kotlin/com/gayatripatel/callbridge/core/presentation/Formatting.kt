package com.gayatripatel.callbridge.core.presentation

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Timestamp labels used in the call log, message list and voicemail list. */
object TimeLabels {
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val date = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun relative(thenMillis: Long, nowMillis: Long, zone: ZoneId): String {
        val diff = nowMillis - thenMillis
        if (diff in 0 until 60_000) return "Just now"
        if (diff in 0 until 60 * 60_000) return "${diff / 60_000} min ago"
        val then = Instant.ofEpochMilli(thenMillis).atZone(zone)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val days = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
        return when {
            days == 0L -> clock.format(then)
            days == 1L -> "Yesterday"
            days in 2..6 -> weekday.format(then)
            else -> date.format(then)
        }
    }

    fun clockTime(millis: Long, zone: ZoneId): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))
}

object Durations {
    /** 65 -> `1:05`, 3725 -> `1:02:05` */
    fun format(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
