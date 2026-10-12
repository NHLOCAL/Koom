package top.zekal.koom

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.UserManager
import android.util.Log

/**
 * Lives under src/debug: cannot be used by release APKs.
 * Explicit adb hooks for disposable-emulator lifecycle tests. STATUS only reads
 * device-protected state: querying it must never restore or reschedule an alarm.
 */
class DebugAlarmTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(ACTION, STATUS, CLEANUP)) return
        val request = intent.getStringExtra("request_id").orEmpty()
            .replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)
        try {
            val scheduler = AlarmScheduler(context)
            val store = AlarmStore(context)
            val ok = when (intent.action) {
                ACTION -> scheduler.scheduleTest(
                    intent.getLongExtra("delay_ms", 17_000L).coerceIn(5_000L, 180_000L)
                )
                CLEANUP -> {
                    scheduler.cancel(AlarmScheduler.TEST_ALARM_ID)
                    store.dismiss(AlarmScheduler.TEST_ALARM_ID)
                    store.delete(AlarmScheduler.TEST_ALARM_ID)
                    if (store.activeIds().isEmpty()) {
                        // onDestroy releases audio, focus and vibration. Do not force-stop.
                        context.stopService(Intent(context, RingService::class.java))
                        AlarmNotification.cancel(context)
                    } else {
                        RingService.refresh(context)
                    }
                    true
                }
                else -> true
            }
            val occurrence = store.occurrence(AlarmScheduler.TEST_ALARM_ID)
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val fullScreen = if (Build.VERSION.SDK_INT >= 34) {
                notificationManager.canUseFullScreenIntent()
            } else {
                context.checkSelfPermission(Manifest.permission.USE_FULL_SCREEN_INTENT) ==
                    PackageManager.PERMISSION_GRANTED
            }
            val uri = occurrence?.let {
                Uri.parse("koom://alarm/" + Uri.encode(AlarmScheduler.TEST_ALARM_ID))
                    .buildUpon().appendQueryParameter("occurrence", it.token).build().toString()
            } ?: "none"
            val power = context.getSystemService(PowerManager::class.java)
            val status = listOf(
                "request=$request", "ok=$ok",
                "unlocked=${context.getSystemService(UserManager::class.java).isUserUnlocked}",
                "secure=${context.getSystemService(KeyguardManager::class.java).isDeviceSecure}",
                "interactive=${power.isInteractive}", "idle=${power.isDeviceIdleMode}",
                "exact=${scheduler.canSchedule()}",
                "notifications=${notificationManager.areNotificationsEnabled()}",
                "full_screen=$fullScreen",
                "enabled=${store.byId(AlarmScheduler.TEST_ALARM_ID)?.enabled == true}",
                "active=${AlarmScheduler.TEST_ALARM_ID in store.activeIds()}",
                "due_ms=${occurrence?.atMillis ?: 0}",
                "token=${occurrence?.token ?: "none"}", "uri=$uri"
            ).joinToString(" ")
            // No 200-character truncation: CI must inspect the complete occurrence identity.
            Log.i(TAG, "STATUS $status")
            resultData = status
        } catch (error: Exception) {
            Log.e(TAG, "Debug action failed: ${intent.action}", error)
            resultData = "request=$request ok=false error=${error.javaClass.simpleName}"
        }
    }

    companion object {
        const val ACTION = "top.zekal.koom.DEBUG_SCHEDULE_ALARM"
        const val STATUS = "top.zekal.koom.DEBUG_ALARM_STATUS"
        const val CLEANUP = "top.zekal.koom.DEBUG_CLEANUP_ALARM"
        private const val TAG = "KoomDebug"
    }
}
