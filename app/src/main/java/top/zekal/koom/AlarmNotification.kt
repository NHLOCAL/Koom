package top.zekal.koom

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/**
 * Channel v4 avoids the legacy siren. Normal ringing uses MediaPlayer;
 * a separate audible backup channel is only used when playback cannot start.
 */
object AlarmNotification {
    const val CHANNEL_ID = "koom_alarm_visible_v4"
    private const val BACKUP_CHANNEL = "koom_alarm_backup_v4"
    const val NOTIFICATION_ID = 22001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "קום: התראות השכמה",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "התראה במסך נעילה. המנגינה מתנגנת מתוך האפליקציה."
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
        if (manager.getNotificationChannel(BACKUP_CHANNEL) == null) {
            val backup = NotificationChannel(BACKUP_CHANNEL, "קום: התראות גיבוי",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "צלצול גיבוי במקרה שמערכת ההפעלה מגבילה הפעלת שמע"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            manager.createNotificationChannel(backup)
        }
    }

    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return false
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel(context)
        return manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun build(context: Context, label: String, backup: Boolean = false): Notification {
        ensureChannel(context)
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, RingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(context, if (backup) BACKUP_CHANNEL else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(context.getString(R.string.ringing_content))
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(!backup)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()
    }

    fun post(context: Context, label: String): Boolean {
        if (!notificationsAllowed(context)) return false
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, build(context, label))
        return true
    }

    fun postFallback(context: Context, label: String) {
        if (!notificationsAllowed(context)) return
        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, build(context, label, backup = true))
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    fun postMissed(context: Context, alarm: Alarm) {
        if (!notificationsAllowed(context)) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ההשכמה הוחמצה: ${alarm.label}")
            .setContentText("המכשיר או האפליקציה חזרו לפעול יותר מ-10 דקות אחרי מועד ההשכמה.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify("missed:${alarm.id}", NOTIFICATION_ID + 1, notification)
    }
}
