package top.zekal.koom

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** One-shot alarms are recomputed after reboot, app updates, clock and zone changes. */
class SystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> AlarmStore(context).clearStaleRingingAfterBoot()
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> Unit
                else -> return
            }
            AlarmScheduler(context).reconcile()
        } catch (e: Exception) {
            Log.e("KoomSystem", "Unable to reschedule alarms", e)
        }
    }
}
