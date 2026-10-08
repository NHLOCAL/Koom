package top.zekal.koom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * The alarm is accepted durably before audio starts. Even if the process exits,
 * the queued ring is still present when the user opens the app again.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        try {
            val store = AlarmStore(context)
            val alarm = store.acceptTrigger(id) ?: return
            if (alarm.daysMask != 0) {
                AlarmScheduler(context).scheduleFollowingDelivery(alarm)
            }
            RingService.start(context)
        } catch (e: Exception) {
            Log.e("KoomAlarm", "Unable to start delivered alarm", e)
        }
    }
}
