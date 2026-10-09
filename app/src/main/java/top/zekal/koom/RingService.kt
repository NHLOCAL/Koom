package top.zekal.koom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
    private var destroyed = false
    private var audioFocus: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AlarmNotification.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val log = AlarmDiagnostics(this)
        try {
            val store = AlarmStore(this)
            val scheduler = AlarmScheduler(this)
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
                    val accepted = store.acceptTrigger(triggerId,
                        intent?.getStringExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN))
                    if (accepted == null) log.record("IGNORED", triggerId, "Stale alarm occurrence")
                } else {
                    log.record("IGNORED", triggerId, "Alarm was disabled or deleted")
                }
            }

            val ids = store.activeIds()
            if (ids.isEmpty()) {
                scheduler.cancelRingingRecovery()
                // A newer FIRE may already be queued in Android, before its callback runs.
                // Only this start request is allowed to tear down the foreground service.
                if (stopSelfResult(startId)) {
                    log.record("STOPPED")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    AlarmNotification.cancel(this)
                }
                return START_NOT_STICKY
            }

            val id = ids.first()
            val alarm = store.byId(id)
            val label = alarm?.label ?: getString(R.string.ringing_title)
            startForeground(AlarmNotification.NOTIFICATION_ID, AlarmNotification.build(this, label))
            log.record("SERVICE_FOREGROUND", id)
            if (intent?.action == AlarmScheduler.ACTION_RECOVER_RINGING) {
                log.record("RING_RECOVERED", id)
            }
            scheduler.cancelRingingRecovery()
            if (!scheduler.restoreActiveRecurringAlarms()) {
                log.record("RESCHEDULE_FAILED", id)
            }

            if (player == null || currentAlarmId != id) {
                releasePlayer()
                vibrator?.cancel()
                currentAlarmId = id
                requestAudioFocus()
                startPlayback(alarm, id)
            }
            return START_STICKY
        } catch (e: Exception) {
            log.record("SERVICE_FAILED", detail = e.javaClass.simpleName + ": " + e.message)
            Log.e("KoomRing", "Could not start alarm playback", e)
            // Detach before stopping: AMS otherwise asynchronously cancels this ID,
            // which could also erase a backup notification posted just after the stop.
            runCatching { stopForeground(STOP_FOREGROUND_DETACH) }
            if (stopSelfResult(startId)) {
                try {
                    val active = AlarmStore(this).activeIds().firstOrNull()
                    val label = active?.let { AlarmStore(this).byId(it)?.label }
                        ?: getString(R.string.ringing_title)
                    AlarmNotification.postFallback(this, label)
                } catch (_: Exception) { }
            }
            return START_NOT_STICKY
        }
    }

    private fun startPlayback(alarm: Alarm?, id: String) {
        val log = AlarmDiagnostics(this)
        if (alarm?.soundFile != null && AlarmSounds.file(this, alarm.soundFile) == null) {
            log.record("SOUND_FILE_MISSING", id, "Custom sound missing: using system ringtone")
        }
        startAudio(alarm, id, firstSource = 0)

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

    /** Both prepare failures and later decoder/server failures advance this bounded chain. */
    private fun startAudio(alarm: Alarm?, id: String, firstSource: Int) {
        val log = AlarmDiagnostics(this)
        if (destroyed || currentAlarmId != id) return
        for (source in firstSource..2) {
            try {
                val next = when (source) {
                    0 -> AlarmSounds.start(this, alarm?.soundFile)
                    1 -> playBundledChime()
                    else -> playSystemAlarm()
                }
                player = next
                next.setOnErrorListener { failed, what, extra ->
                    if (!destroyed && player === failed && currentAlarmId == id) {
                        log.record("AUDIO_ASYNC_FAILED", id, "source=$source what=$what extra=$extra")
                        releasePlayer()
                        startAudio(alarm, id, source + 1)
                    }
                    true
                }
                val label = when (source) {
                    0 -> if (alarm?.soundFile != null) alarm.soundLabel else "Phone ringtone"
                    1 -> "Bundled gentle chime"
                    else -> "System alarm fallback"
                }
                log.record("AUDIO_STARTED", id, label)
                return
            } catch (error: Exception) {
                releasePlayer()
                log.record("AUDIO_SOURCE_FAILED", id, "source=$source ${error.message}")
            }
        }
        log.record("AUDIO_FAILED", id, "All three sound sources failed")
        runCatching { AlarmNotification.postFallback(this, alarm?.label ?: getString(R.string.ringing_title)) }
    }

    private fun playSystemAlarm(): MediaPlayer {
        val result = MediaPlayer()
        try {
            result.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            result.setAudioAttributes(alarmAudioAttributes())
            result.setDataSource(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            result.isLooping = true
            result.prepare()
            result.start()
            return result
        } catch (error: Exception) {
            result.release()
            throw error
        }
    }

    private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

    private fun requestAudioFocus() {
        if (audioFocus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAudioAttributes())
            .setOnAudioFocusChangeListener { change ->
                AlarmDiagnostics(this).record("AUDIO_FOCUS_CHANGED", currentAlarmId.orEmpty(), change.toString())
            }.build()
        // On target 35+, this must happen after startForeground(). Alarm routing and DND
        // remain controlled by the system's alarm stream; do not change the user's volume.
        val result = getSystemService(AudioManager::class.java).requestAudioFocus(request)
        audioFocus = request
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            AlarmDiagnostics(this).record("AUDIO_FOCUS_DENIED", currentAlarmId.orEmpty())
        }
    }

    private fun releasePlayer() {
        val old = player
        player = null
        old?.setOnErrorListener(null)
        try { old?.stop() } catch (_: Exception) { }
        old?.release()
    }

    private fun playBundledChime(): MediaPlayer {
        val result = MediaPlayer()
        try {
            result.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            result.setAudioAttributes(alarmAudioAttributes())
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
        destroyed = true
        releasePlayer()
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocus = null
        currentAlarmId = null
        vibrator?.cancel()
        vibrator = null
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
