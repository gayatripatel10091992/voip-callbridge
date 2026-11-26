package com.gayatripatel.callbridge.core.presentation

import com.gayatripatel.callbridge.core.model.CallDirection
import com.gayatripatel.callbridge.core.model.CallRecord
import com.gayatripatel.callbridge.core.model.PhoneLine
import com.gayatripatel.callbridge.core.phone.PhoneNumbers
import java.time.Instant
import java.time.ZoneId

enum class RecentsFilter { ALL, MISSED }

/** One row in the call log: consecutive calls with the same number on the same day collapse into one. */
data class CallRowUi(
    val key: Long,
    val title: String,
    val subtitle: String,
    val number: String,
    val direction: CallDirection,
    val count: Int,
    val timeLabel: String,
    val lineLabel: String,
    val lineColorIndex: Int,
    val viaWifi: Boolean,
    val likelySpam: Boolean,
)

object RecentsPresenter {

    fun present(
        records: List<CallRecord>,
        lines: List<PhoneLine>,
        filter: RecentsFilter,
        nowMillis: Long,
        zone: ZoneId,
    ): List<CallRowUi> {
        val visible = records
            .sortedByDescending { it.startedAtMillis }
            .filter { filter == RecentsFilter.ALL || it.direction == CallDirection.MISSED }
        val lineById = lines.associateBy { it.id }
        return group(visible, zone).map { group ->
            val first = group.first()
            val line = lineById[first.lineId]
            CallRowUi(
                key = first.id,
                title = first.contactName ?: PhoneNumbers.display(first.number),
                subtitle = when {
                    first.likelySpam -> "Likely spam · ${PhoneNumbers.display(first.number)}"
                    first.contactName != null -> PhoneNumbers.display(first.number)
                    else -> "Unknown"
                },
                number = first.number,
                direction = first.direction,
                count = group.size,
                timeLabel = TimeLabels.relative(first.startedAtMillis, nowMillis, zone),
                lineLabel = line?.label ?: "",
                lineColorIndex = line?.colorIndex ?: 0,
                viaWifi = first.viaWifi,
                likelySpam = first.likelySpam,
            )
        }
    }

    /** Groups consecutive (already sorted) calls with the same number, direction class and calendar day. */
    fun group(sorted: List<CallRecord>, zone: ZoneId): List<List<CallRecord>> {
        val result = mutableListOf<MutableList<CallRecord>>()
        for (record in sorted) {
            val last = result.lastOrNull()?.last()
            if (last != null &&
                last.number == record.number &&
                (last.direction == CallDirection.MISSED) == (record.direction == CallDirection.MISSED) &&
                day(last, zone) == day(record, zone)
            ) {
                result.last().add(record)
            } else {
                result.add(mutableListOf(record))
            }
        }
        return result
    }

    private fun day(r: CallRecord, zone: ZoneId) = Instant.ofEpochMilli(r.startedAtMillis).atZone(zone).toLocalDate()
}
