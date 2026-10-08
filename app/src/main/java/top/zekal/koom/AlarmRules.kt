package top.zekal.koom

import java.time.LocalTime
import java.time.ZonedDateTime

/** Pure scheduling rules, expressed in local wall time, including DST boundaries. */
object AlarmRules {
    fun next(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val sundayZero = date.dayOfWeek.value % 7
            if (alarm.daysMask != 0 && (alarm.daysMask and (1 shl sundayZero)) == 0) {
                continue
            }
            val candidate = ZonedDateTime.of(date, LocalTime.of(alarm.hour, alarm.minute), now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    fun daysText(mask: Int): String {
        if (mask == 0) return "חד פעמי"
        if (mask == 127) return "כל יום"
        val labels = listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
        return labels.indices.filter { mask and (1 shl it) != 0 }
            .joinToString(" · ") { labels[it] }
    }

    fun clock(alarm: Alarm): String = "%02d:%02d".format(alarm.hour, alarm.minute)
}
