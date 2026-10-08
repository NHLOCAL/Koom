package top.zekal.koom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Android delivers the alarm-clock PendingIntent even after normal process termination. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val log = AlarmDiagnostics(context)
        log.record("RECEIVED", id)
        try {
            val alarm = AlarmStore(context).acceptTrigger(id)
            if (alarm == null) {
                log.record("IGNORED", id, "Alarm was disabled or already delivered")
                return
            }
            try {
                if (AlarmNotification.post(context, alarm.label)) log.record("NOTIFIED", id)
                else log.record("NOTIFICATION_BLOCKED", id)
            } catch (e: Exception) {
                log.record("NOTIFICATION_FAILED", id, e.javaClass.simpleName)
            }

            if (alarm.daysMask != 0 &&
                !AlarmScheduler(context).scheduleFollowingDelivery(alarm)) {
                log.record("RESCHEDULE_FAILED", id)
            }

            try {
                RingService.start(context)
                log.record("SERVICE_REQUESTED", id)
            } catch (e: Exception) {
                log.record("SERVICE_FAILED", id, e.javaClass.simpleName + ": " + e.message)
                Log.e("KoomAlarm", "FGS start was rejected", e)
                try { AlarmNotification.postFallback(context, alarm.label) }
                catch (_: Exception) { }
            }
        } catch (e: Exception) {
            log.record("RECEIVER_FAILED", id, e.javaClass.simpleName + ": " + e.message)
            Log.e("KoomAlarm", "Alarm delivery failed", e)
        }
    }
}
