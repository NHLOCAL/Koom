package top.zekal.koom

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Exact OS alarm clock with explicit unique PendingIntent identity per alarm. */
class AlarmScheduler(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(AlarmManager::class.java)
    private val diagnostics = AlarmDiagnostics(app)

    fun canSchedule(): Boolean = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()

    /** The next alarm is reported by Android's AlarmManager, not calculated by our UI. */
    fun nextSystemAlarmMillis(): Long? = manager.nextAlarmClock?.triggerTime

    fun cancel(id: String) {
        // Invalidate before canceling Android's registration: delivery may already be queued.
        val store = AlarmStore(app)
        val previous = store.occurrence(id)
        store.forgetOccurrence(id)
        if (previous != null) cancelPending(alarmIntent(id, PendingIntent.FLAG_NO_CREATE, previous.token))
        cancelPending(alarmIntent(id, PendingIntent.FLAG_NO_CREATE))
        // Remove alarms registered by v2.1's BroadcastReceiver before this upgrade.
        cancelLegacyBroadcast(id)
        diagnostics.record("CANCELLED", id)
    }

    private fun cancelPending(pending: PendingIntent?) {
        if (pending != null) {
            manager.cancel(pending)
            pending.cancel()
        }
    }

    private fun cancelLegacyBroadcast(id: String) {
        cancelPending(legacyBroadcast(id, PendingIntent.FLAG_NO_CREATE))
    }

    fun update(alarm: Alarm): Boolean {
        cancel(alarm.id)
        AlarmStore(app).upsert(alarm)
        return !alarm.enabled || schedule(alarm)
    }

    fun schedule(alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (!alarm.enabled) return true
        val next = AlarmRules.next(alarm, after) ?: return false
        return scheduleAt(alarm.id, next.toInstant().toEpochMilli())
    }

    /** Exact alarm self-test: scheduled through the SAME path as a real alarm. */
    fun scheduleTest(delayMillis: Long = 20_000L): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", TEST_ALARM_ID, "Exact alarm permission")
            return false
        }
        val due = ZonedDateTime.now().plus(delayMillis.coerceAtLeast(5_000), ChronoUnit.MILLIS)
        val alarm = Alarm(
            id = TEST_ALARM_ID, hour = due.hour, minute = due.minute,
            label = "בדיקת התעוררות", puzzle = PuzzleKind.MATH, enabled = true
        )
        cancel(TEST_ALARM_ID)
        AlarmStore(app).upsert(alarm)
        val success = scheduleAt(TEST_ALARM_ID, due.toInstant().toEpochMilli())
        if (!success) AlarmStore(app).upsert(alarm.copy(enabled = false))
        return success
    }

    fun scheduleFollowingDelivery(alarm: Alarm): Boolean =
        schedule(alarm, ZonedDateTime.now().plusSeconds(2))

    /**
     * Acceptance and AlarmManager registration cannot be one transaction. A service retry
     * must finish this work even when its delivered token was already consumed on disk.
     */
    fun restoreActiveRecurringAlarms(): Boolean {
        val store = AlarmStore(app)
        var ok = true
        for (id in store.activeIds()) {
            val alarm = store.byId(id) ?: continue
            if (!alarm.enabled || alarm.daysMask == 0) continue
            val pending = store.occurrence(id)
            ok = if (pending != null) {
                scheduleAt(id, pending.atMillis, pending.token) && ok
            } else {
                scheduleFollowingDelivery(alarm) && ok
            }
        }
        return ok
    }

    fun reconcile(recalculateWallTime: Boolean = false): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", detail = "Exact alarm permission missing")
            return false
        }
        var ok = true
        val store = AlarmStore(app)
        val nowMillis = System.currentTimeMillis()
        for (alarm in store.all()) {
            if (alarm.enabled) {
                val pending = store.occurrence(alarm.id)
                // Diagnostic alarms have an absolute, short deadline, not a daily clock time.
                if (alarm.id == TEST_ALARM_ID && pending == null) continue
                if (pending != null && !recalculateWallTime &&
                    AlarmRules.occurrenceExpired(pending.atMillis, nowMillis)) {
                    cancel(alarm.id)
                    diagnostics.record("MISSED", alarm.id, "Recovery deadline exceeded")
                    runCatching { AlarmNotification.postMissed(app, alarm) }
                    if (alarm.daysMask == 0) store.upsert(alarm.copy(enabled = false))
                    else ok = schedule(alarm) && ok
                    continue
                }
                ok = if (pending != null && (alarm.id == TEST_ALARM_ID ||
                        AlarmRules.keepOccurrence(pending.atMillis, nowMillis, recalculateWallTime))) {
                    // This includes just-overdue alarms. setAlarmClock delivers a past deadline
                    // promptly instead of silently postponing it until tomorrow on app resume.
                    scheduleAt(alarm.id, pending.atMillis, pending.token) && ok
                } else {
                    schedule(alarm) && ok
                }
            } else {
                cancel(alarm.id)
            }
        }
        val recoveryAt = store.ringingRecoveryAtMillis()
        if (recoveryAt != null && store.activeIds().isNotEmpty()) {
            ok = scheduleRingingRecovery(recoveryAt) && ok
        }
        return ok
    }

    /** Resume a recent, already accepted alarm without re-enabling its one-shot definition. */
    private fun scheduleRingingRecovery(atMillis: Long): Boolean = try {
        val show = PendingIntent.getActivity(app, 0, Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show),
            requireNotNull(recoveryIntent(PendingIntent.FLAG_UPDATE_CURRENT)))
        diagnostics.record("RING_RECOVERY_SCHEDULED", detail = atMillis.toString())
        true
    } catch (error: Exception) {
        diagnostics.record("RESCHEDULE_FAILED", detail = "Ringing recovery: ${error.message}")
        false
    }

    fun cancelRingingRecovery() {
        AlarmStore(app).finishRingingRecovery()
        cancelPending(recoveryIntent(PendingIntent.FLAG_NO_CREATE))
    }

    private fun recoveryIntent(flag: Int): PendingIntent? = PendingIntent.getForegroundService(
        app, 1, Intent(app, RingService::class.java).apply {
            action = ACTION_RECOVER_RINGING
            data = Uri.parse("koom://ring-recovery")
        }, flag or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleAt(id: String, timeMillis: Long, token: String = UUID.randomUUID().toString()): Boolean {
        if (!canSchedule()) {
            diagnostics.record("SCHEDULE_BLOCKED", id, "Exact alarm permission missing")
            return false
        }
        val store = AlarmStore(app)
        return try {
            val previous = store.occurrence(id)
            val show = PendingIntent.getActivity(app, 0,
                Intent(app, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            // Prevent a previously registered v2 BroadcastReceiver from firing as well.
            cancelLegacyBroadcast(id)
            // Persist before registration, because a past-due alarm may be delivered immediately.
            store.rememberOccurrence(id, AlarmOccurrence(timeMillis, token))
            if (previous != null && previous.token != token) {
                cancelPending(alarmIntent(id, PendingIntent.FLAG_NO_CREATE, previous.token))
            }
            // Cancel v2.2's service PendingIntent, whose identity did not include an occurrence.
            cancelPending(alarmIntent(id, PendingIntent.FLAG_NO_CREATE))
            val trigger = requireNotNull(alarmIntent(id, PendingIntent.FLAG_UPDATE_CURRENT, token))
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, show), trigger)
            diagnostics.record("SCHEDULED", id, timeMillis.toString())
            true
        } catch (e: Exception) {
            diagnostics.record("SCHEDULE_FAILED", id,
                e.javaClass.simpleName + ": " + e.message)
            Log.e("KoomScheduler", "Failed to register OS alarm", e)
            false
        }
    }

    /**
     * The OS itself creates the foreground service at alarm time.
     * Android 12's user-requested exact-alarm exemption applies to this start.
     * No broadcast → startForegroundService trampoline is needed.
     */
    private fun alarmIntent(id: String, flag: Int, token: String? = null): PendingIntent? =
        PendingIntent.getForegroundService(app, 0,
            Intent(app, RingService::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(id)).buildUpon().apply {
                    if (token != null) appendQueryParameter("occurrence", token)
                }.build()
                putExtra(EXTRA_ALARM_ID, id)
                if (token != null) putExtra(EXTRA_OCCURRENCE_TOKEN, token)
            }, flag or PendingIntent.FLAG_IMMUTABLE)

    private fun legacyBroadcast(id: String, flag: Int): PendingIntent? =
        PendingIntent.getBroadcast(app, 0,
            Intent(app, AlarmReceiver::class.java).apply {
                action = ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(id))
                putExtra(EXTRA_ALARM_ID, id)
            }, flag or PendingIntent.FLAG_IMMUTABLE)

    companion object {
        const val ACTION_FIRE = "top.zekal.koom.action.FIRE"
        const val ACTION_RECOVER_RINGING = "top.zekal.koom.action.RECOVER_RINGING"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_OCCURRENCE_TOKEN = "occurrence_token"
        const val TEST_ALARM_ID = "__koom_diagnostic_test__"
    }
}
