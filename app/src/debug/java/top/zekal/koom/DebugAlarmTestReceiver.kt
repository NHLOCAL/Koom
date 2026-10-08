package top.zekal.koom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lives under src/debug: cannot be used by release APKs.
 * Triggered from adb to schedule a real AlarmManager alarm, then the process
 * is killed before its due time. This tests the exact user-reported failure.
 */
class DebugAlarmTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // Exact alarm scheduling itself records success/failure in KoomDelivery.
        // Do not rely on ordered-broadcast result codes from adb. 
        AlarmScheduler(context).scheduleTest(17_000L)
    }

    companion object {
        const val ACTION = "top.zekal.koom.DEBUG_SCHEDULE_ALARM"
    }
}
