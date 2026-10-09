package top.zekal.koom

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * No always-on service or periodic wakeups. Android broadcasts reschedule
 * alarm clock intents when the device is rebooted or its wall-clock changes.
 */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
                else -> return
            }
            val successful = AlarmScheduler(context).reconcile(
                recalculateWallTime = intent.action == Intent.ACTION_TIME_CHANGED ||
                    intent.action == Intent.ACTION_TIMEZONE_CHANGED
            )
            AlarmDiagnostics(context).record(
                if (successful) "SYSTEM_RESCHEDULED" else "RESCHEDULE_FAILED",
                detail = intent.action.orEmpty()
            )
        } catch (e: Exception) {
            AlarmDiagnostics(context).record("RESCHEDULE_FAILED", detail = e.message.orEmpty())
            Log.e("KoomSystem", "Unable to restore scheduled alarms", e)
        }
    }
}
