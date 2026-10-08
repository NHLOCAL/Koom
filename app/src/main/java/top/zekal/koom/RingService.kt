package top.zekal.koom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Starts only while ringing. Audio is owned by a foreground service so UI lifecycle,
 * screen lock, rotation and accidental back navigation cannot silence the alarm.
 */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ids = AlarmStore(this).activeIds()
        if (ids.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val label = AlarmStore(this).byId(ids.first())?.label ?: getString(R.string.ringing_title)
        try {
            startForeground(NOTIFICATION_ID, notification(label))
            if (player == null) startPlayback()
        } catch (e: Exception) {
            Log.e("KoomRing", "Could not start alarm foreground playback", e)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.alarm_channel), NotificationManager.IMPORTANCE_HIGH
        )
        // MediaPlayer owns the continuous sound; the notification must not double-play it.
        channel.setSound(null, null)
        channel.enableVibration(false)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    private fun notification(label: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, RingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(getString(R.string.ringing_content))
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    private fun startPlayback() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "koom:ringing").apply {
            setReferenceCounted(false)
            acquire(20 * 60 * 1000L)
        }
        try {
            val audio = MediaPlayer()
            audio.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            resources.openRawResourceFd(R.raw.alarm_sound).use { fd ->
                audio.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            audio.isLooping = true
            audio.prepare()
            audio.start()
            player = audio
        } catch (e: Exception) {
            Log.e("KoomRing", "Bundled alarm sound could not play", e)
        }
        try {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 650, 350), 0)
            )
        } catch (e: Exception) {
            Log.w("KoomRing", "Vibration unavailable", e)
        }
    }

    override fun onDestroy() {
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "koom_alarm_ring_v2"
        private const val NOTIFICATION_ID = 22001
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RingService::class.java)
            )
        }

        fun refresh(context: Context) {
            // Called from a visible activity, when the user solves a challenge.
            context.startService(Intent(context, RingService::class.java))
        }
    }
}
