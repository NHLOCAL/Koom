package top.zekal.koom

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmRulesTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    @Test fun oneShotLaterToday() {
        assertEquals(at(2026, 10, 8, 7, 35),
            AlarmRules.next(Alarm(hour = 7, minute = 35), at(2026, 10, 8, 7, 34)))
    }

    @Test fun oneShotAtExactMinuteIsTomorrow() {
        assertEquals(at(2026, 10, 9, 7, 35),
            AlarmRules.next(Alarm(hour = 7, minute = 35), at(2026, 10, 8, 7, 35)))
    }

    @Test fun sundayMaskMeansSunday() {
        // Thursday October 8, 2026. Next Sunday is October 11.
        assertEquals(at(2026, 10, 11, 8, 0),
            AlarmRules.next(Alarm(hour = 8, minute = 0, daysMask = 1 shl 0),
                at(2026, 10, 8, 12, 0)))
    }

    @Test fun weekdayMaskMatchesBothDays() {
        val mask = (1 shl 0) or (1 shl 4)
        assertEquals(at(2026, 10, 11, 6, 30),
            AlarmRules.next(Alarm(hour = 6, minute = 30, daysMask = mask),
                at(2026, 10, 8, 7, 0)))
    }

    @Test fun todayWithRepeatMaskIsChosen() {
        assertEquals(at(2026, 10, 8, 18, 45),
            AlarmRules.next(Alarm(hour = 18, minute = 45, daysMask = 1 shl 4),
                at(2026, 10, 8, 11, 0)))
    }

    @Test fun disabledAlarmNotScheduled() {
        assertNull(AlarmRules.next(Alarm(hour = 8, minute = 0, enabled = false),
            at(2026, 10, 8, 7, 0)))
    }

    @Test fun everyDayTomorrowAfterTodayPassed() {
        assertEquals(at(2026, 10, 9, 7, 0),
            AlarmRules.next(Alarm(hour = 7, minute = 0, daysMask = 127),
                at(2026, 10, 8, 23, 0)))
    }

    @Test fun daylightSavingGapResolvesToValidTime() {
        val paris = ZoneId.of("Europe/Paris")
        val now = ZonedDateTime.of(2026, 3, 28, 20, 0, 0, 0, paris)
        val next = AlarmRules.next(
            Alarm(hour = 2, minute = 30, daysMask = 1 shl 0), now
        )!!
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(29, next.dayOfMonth)
    }

    @Test fun displayedDaysAreSundayFirst() {
        assertEquals("חד פעמי", AlarmRules.daysText(0))
        assertEquals("כל יום", AlarmRules.daysText(127))
        assertEquals("א׳ · ש׳", AlarmRules.daysText((1 shl 0) or (1 shl 6)))
    }

    @Test fun timeAlways24Hours() {
        assertEquals("05:07", AlarmRules.clock(Alarm(hour = 5, minute = 7)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHourRejected() { Alarm(hour = 24, minute = 0) }

    @Test fun timeCorrectionKeepsAnAlarmWhoseDeliveryIsAlreadyDue() {
        assertTrue(AlarmRules.keepOccurrence(100_000L, 100_001L, true))
        assertTrue(AlarmRules.keepOccurrence(100_000L, 115_000L, true))
        assertFalse(AlarmRules.keepOccurrence(100_000L, 115_001L, true))
        assertFalse(AlarmRules.keepOccurrence(100_000L, 99_000L, true))
        assertTrue(AlarmRules.keepOccurrence(100_000L, 99_000L, false))
    }

    @Test fun recoveryWindowDoesNotReplayOldAlarmsDaysLater() {
        assertFalse(AlarmRules.occurrenceExpired(100_000L, 100_000L + 600_000L))
        assertTrue(AlarmRules.occurrenceExpired(100_000L, 100_000L + 600_001L))
        assertFalse(AlarmRules.occurrenceExpired(100_000L, 90_000L))
    }
}
