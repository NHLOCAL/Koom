package top.zekal.koom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * A short-lived mediaPlayback foreground service runs ONLY during ringing.
 * It does not depend on MainActivity or keeping the process in recent apps.
 */
class RingService : Service() {
    private var player: MediaPlayer? = null
    private var currentAlarmId: String? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val log = AlarmDiagnostics(this)
        try {
            val store = AlarmStore(this)
            val triggerId = if (intent?.action == AlarmScheduler.ACTION_FIRE)
                intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) else null

            if (triggerId != null) {
                val candidate = store.byId(triggerId)
                if (candidate?.enabled == true) {
                    // The system started this service via PendingIntent.getForegroundService.
                    // Promote it immediately, BEFORE any durable writes or rescheduling.
                    startForeground(
                        AlarmNotification.NOTIFICATION_ID,
                        AlarmNotification.build(this, candidate.label)
                    )
                    log.record("SERVICE_FOREGROUND", triggerId)
                    // The alarm was delivered by Android even if our entire process was absent.
                    log.record("RECEIVED", triggerId)
                    val accepted = store.acceptTrigger(triggerId)
                    if (accepted != null && accepted.daysMask != 0) {
                        if (!AlarmScheduler(this).scheduleFollowingDelivery(accepted)) {
                            log.record("RESCHEDULE_FAILED", triggerId)
                        }
                    }
                } else {
                    log.record("IGNORED", triggerId, "Alarm was disabled or deleted")
                }
            }

            val ids = store.activeIds()
            if (ids.isEmpty()) {
                log.record("STOPPED")
                stopForeground(STOP_FOREGROUND_REMOVE)
                AlarmNotification.cancel(this)
                stopSelf()
                return START_NOT_STICKY
            }

            val id = ids.first()
            val alarm = store.byId(id)
            val label = alarm?.label ?: getString(R.string.ringing_title)
            startForeground(AlarmNotification.NOTIFICATION_ID, AlarmNotification.build(this, label))
            log.record("SERVICE_FOREGROUND", id)

            if (player == null || currentAlarmId != id) {
                try { player?.stop() } catch (_: Exception) { }
                player?.release()
                player = null
                vibrator?.cancel()
                if (wakeLock?.isHeld == true) wakeLock?.release()
                wakeLock = null
                currentAlarmId = id
                startPlayback(alarm, id)
            }
            return START_STICKY
        } catch (e: Exception) {
            log.record("SERVICE_FAILED", detail = e.javaClass.simpleName + ": " + e.message)
            Log.e("KoomRing", "Could not start alarm playback", e)
            try {
                val active = AlarmStore(this).activeIds().firstOrNull()
                val label = active?.let { AlarmStore(this).byId(it)?.label }
                    ?: getString(R.string.ringing_title)
                AlarmNotification.postFallback(this, label)
                // Keep the fallback notification when the service is removed.
                stopForeground(STOP_FOREGROUND_DETACH)
            } catch (_: Exception) { }
            stopSelf()
            return START_NOT_STICKY
        }
    }

    private fun startPlayback(alarm: Alarm?, id: String) {
        val log = AlarmDiagnostics(this)
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "koom:ring").apply {
                setReferenceCounted(false)
                acquire(20 * 60 * 1000L)
            }
        } catch (e: Exception) {
            log.record("WAKE_LOCK_FAILED", id, e.message.orEmpty())
        }

        try {
            if (alarm?.soundFile != null && AlarmSounds.file(this, alarm.soundFile) == null) {
                log.record("SOUND_FILE_MISSING", id, "Custom sound missing: using system ringtone")
            }
            player = AlarmSounds.start(this, alarm?.soundFile)
            log.record("AUDIO_STARTED", id,
                if (alarm?.soundFile != null) alarm.soundLabel else "Phone ringtone")
        } catch (e: Exception) {
            log.record("AUDIO_PRIMARY_FAILED", id, e.message.orEmpty())
            // System ringtone providers can be locked before first unlock after reboot.
            // Our own soft melody is in APK resources, available in Direct Boot.
            try {
                player = playBundledChime()
                log.record("AUDIO_STARTED", id, "Bundled gentle chime")
            } catch (chimeError: Exception) {
                log.record("AUDIO_CHIME_FAILED", id, chimeError.message.orEmpty())
                try {
                    val fallback = MediaPlayer()
                    try {
                        fallback.setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        fallback.setDataSource(this,
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                        fallback.isLooping = true
                        fallback.prepare()
                        fallback.start()
                        player = fallback
                        log.record("AUDIO_STARTED", id, "System alarm fallback")
                    } catch (error: Exception) {
                        fallback.release()
                        throw error
                    }
                } catch (fallback: Exception) {
                    log.record("AUDIO_FAILED", id,
                        fallback.javaClass.simpleName + ": " + fallback.message)
                    Log.e("KoomRing", "Unable to play any alarm sound", fallback)
                    try {
                        AlarmNotification.postFallback(this,
                            alarm?.label ?: getString(R.string.ringing_title))
                    } catch (_: Exception) { }
                }
            }
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 450, 600), 0))
        } catch (e: Exception) {
            log.record("VIBRATION_FAILED", id, e.message.orEmpty())
        }
    }

    private fun playBundledChime(): MediaPlayer {
        val result = MediaPlayer()
        try {
            result.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            resources.openRawResourceFd(R.raw.koom_chime).use { afd ->
                result.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            result.isLooping = true
            result.prepare()
            result.start()
            return result
        } catch (error: Exception) {
            result.release()
            throw error
        }
    }

    override fun onDestroy() {
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        currentAlarmId = null
        vibrator?.cancel()
        vibrator = null
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, RingService::class.java))
        }
        fun refresh(context: Context) {
            context.startService(Intent(context, RingService::class.java))
        }
    }
}
