package top.zekal.koom

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.ZonedDateTime

/** Uses OS alarm-clock scheduling, not timers, polling or an always-on service. */
class AlarmScheduler(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)

    fun canSchedule(): Boolean =
        Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    fun cancel(id: String) {
        val pending = alarmIntent(id, PendingIntent.FLAG_NO_CREATE)
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    fun update(alarm: Alarm): Boolean {
        cancel(alarm.id)
        return !alarm.enabled || schedule(alarm)
    }

    fun schedule(alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (!alarm.enabled) return true
        val next = AlarmRules.next(alarm, after) ?: return false
        if (!canSchedule()) return false
        return try {
            val open = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val trigger = requireNotNull(alarmIntent(alarm.id, PendingIntent.FLAG_UPDATE_CURRENT))
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(next.toInstant().toEpochMilli(), open), trigger)
            true
        } catch (e: SecurityException) {
            Log.e("KoomScheduler", "Exact alarm permission missing", e)
            false
        }
    }

    /** Called after delivery, with a guard against rescheduling the same minute. */
    fun scheduleFollowingDelivery(alarm: Alarm): Boolean =
        schedule(alarm, ZonedDateTime.now().plusMinutes(1))

    fun reconcile(): Boolean {
        if (!canSchedule()) return false
        var ok = true
        AlarmStore(app).all().forEach { alarm ->
            if (alarm.enabled) ok = schedule(alarm) && ok else cancel(alarm.id)
        }
        return ok
    }

    private fun alarmIntent(id: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app, 0,
            Intent(app, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(id))
                putExtra(EXTRA_ALARM_ID, id)
            },
            flag or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val ACTION_FIRE = "top.zekal.koom.action.FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
