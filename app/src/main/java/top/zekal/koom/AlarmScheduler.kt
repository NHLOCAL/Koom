package top.zekal.koom

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Exact OS alarm clock with explicit unique PendingIntent identity per alarm. */
class AlarmScheduler(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)
    private val diagnostics = AlarmDiagnostics(app)

    fun canSchedule(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    /** The next alarm is reported by Android's AlarmManager, not calculated by our UI. */
    fun nextSystemAlarmMillis(): Long? = manager.nextAlarmClock?.triggerTime

    fun cancel(id: String) {
        val pending = alarmIntent(id, PendingIntent.FLAG_NO_CREATE)
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
        diagnostics.record("CANCELLED", id)
    }

    fun update(alarm: Alarm): Boolean {
        cancel(alarm.id)
        return !alarm.enabled || schedule(alarm)
    }

    fun schedule(alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (!alarm.enabled) return true
        val next = AlarmRules.next(alarm, after) ?: return false
        return scheduleAt(alarm.id, next.toInstant().toEpochMilli())
    }

    /** Exact alarm self-test: scheduled through the SAME path as a real alarm. */
    fun scheduleTest(delayMillis: Long = 20_000L): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", TEST_ALARM_ID, "Exact alarm permission")
            return false
        }
        val now = LocalTime.now()
        val alarm = Alarm(
            id = TEST_ALARM_ID, hour = now.hour, minute = now.minute,
            label = "בדיקת התעוררות", puzzle = PuzzleKind.MATH, enabled = true
        )
        AlarmStore(app).upsert(alarm)
        cancel(TEST_ALARM_ID)
        val success = scheduleAt(TEST_ALARM_ID,
            System.currentTimeMillis() + delayMillis.coerceAtLeast(5_000))
        if (!success) AlarmStore(app).upsert(alarm.copy(enabled = false))
        return success
    }

    fun scheduleFollowingDelivery(alarm: Alarm): Boolean =
        schedule(alarm, ZonedDateTime.now().plusSeconds(2))

    fun reconcile(): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", detail = "Exact alarm permission missing")
            return false
        }
        var ok = true
        for (alarm in AlarmStore(app).all()) {
            if (alarm.id == TEST_ALARM_ID) continue
            if (alarm.enabled) {
                ok = schedule(alarm) && ok
            } else {
                cancel(alarm.id)
            }
        }
        return ok
    }

    private fun scheduleAt(id: String, timeMillis: Long): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", id, "Exact alarm permission missing")
            return false
        }
        return try {
            val show = PendingIntent.getActivity(app, 0,
                Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val trigger = requireNotNull(alarmIntent(id, PendingIntent.FLAG_UPDATE_CURRENT))
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, show), trigger)
            diagnostics.record("SCHEDULED", id, timeMillis.toString())
            true
        } catch (e: Exception) {
            diagnostics.record("SCHEDULE_FAILED", id,
                e.javaClass.simpleName + ": " + e.message)
            Log.e("KoomScheduler", "Failed to register OS alarm", e)
            false
        }
    }

    private fun alarmIntent(id: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(app, 0,
            Intent(app, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(id))
                putExtra(EXTRA_ALARM_ID, id)
            }, flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_FIRE = "top.zekal.koom.action.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val TEST_ALARM_ID = "__koom_diagnostic_test__"
    }
}
