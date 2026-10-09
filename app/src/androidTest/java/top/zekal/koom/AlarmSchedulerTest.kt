package top.zekal.koom

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class AlarmSchedulerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler get() = AlarmScheduler(context)
    private val id = "occurrence-regression"

    @Before fun prepare() {
        assumeTrue(scheduler.canSchedule())
        cleanup()
    }

    @After fun cleanup() {
        scheduler.cancel(id)
        scheduler.cancel(AlarmScheduler.TEST_ALARM_ID)
        context.stopService(Intent(context, RingService::class.java))
        context.createDeviceProtectedStorageContext().getSharedPreferences("koom_v2", 0)
            .edit().clear().commit()
        context.getSharedPreferences("koom_v2", 0).edit().clear().commit()
        context.getSharedPreferences("FlutterSharedPreferences", 0).edit().clear().commit()
    }

    @Test fun reconciliationRestoresThePersistedOccurrenceRatherThanRecalculatingIt() {
        val later = LocalTime.now().plusHours(1)
        val alarm = Alarm(id = id, hour = later.hour, minute = later.minute)
        AlarmStore(context).upsert(alarm)
        val savedTime = System.currentTimeMillis() + 180_000L
        // This is the disk snapshot that survives a process kill or reboot.
        val occurrence = JSONObject().put("at", savedTime).put("token", "saved-occurrence")
        context.createDeviceProtectedStorageContext().getSharedPreferences("koom_v2", 0)
            .edit().putString("pending_occurrences_v1", JSONObject().put(id, occurrence).toString())
            .commit()
        assertTrue(scheduler.reconcile())
        assertEquals("Restoration must preserve the accepted due time", savedTime,
            scheduler.nextSystemAlarmMillis())
    }

    @Test fun diagnosticAlarmSurvivesLossOfSystemRegistrationAtBoot() {
        assertTrue(scheduler.scheduleTest(120_000L))
        val expected = scheduler.nextSystemAlarmMillis()
        val token = AlarmStore(context).occurrence(AlarmScheduler.TEST_ALARM_ID)!!.token
        val pending = PendingIntent.getForegroundService(context, 0,
            Intent(context, RingService::class.java).apply {
                action = AlarmScheduler.ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(AlarmScheduler.TEST_ALARM_ID))
                    .buildUpon().appendQueryParameter("occurrence", token).build()
            }, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNotNull(pending)
        context.getSystemService(AlarmManager::class.java).cancel(pending!!)
        assertNull(scheduler.nextSystemAlarmMillis())
        SystemReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals("Reboot must restore the same test occurrence", expected,
            scheduler.nextSystemAlarmMillis())
    }

    @Test fun staleDeliveryCannotConsumeAnEditedAlarm() {
        val alarm = Alarm(id = id, hour = 7, minute = 0, daysMask = 127)
        AlarmStore(context).upsert(alarm)
        val occurrence = JSONObject().put("at", System.currentTimeMillis() + 180_000L)
            .put("token", "replacement-occurrence")
        context.createDeviceProtectedStorageContext().getSharedPreferences("koom_v2", 0)
            .edit().putString("pending_occurrences_v1", JSONObject().put(id, occurrence).toString())
            .commit()
        AlarmReceiver().onReceive(context, Intent(AlarmScheduler.ACTION_FIRE)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
            .putExtra("occurrence_token", "old-occurrence"))
        assertTrue("A stale delivery must not start ringing", AlarmStore(context).activeIds().isEmpty())
        assertTrue(AlarmStore(context).byId(id)!!.enabled)
    }

    @Test fun editingCancelsTheOldGenerationInsteadOfLeavingAPhantomAlarm() {
        val time = LocalTime.now().plusHours(1)
        val alarm = Alarm(id = id, hour = time.hour, minute = time.minute)
        assertTrue(scheduler.update(alarm))
        val previous = AlarmStore(context).occurrence(id)!!
        val oldIntent = PendingIntent.getForegroundService(context, 0,
            Intent(context, RingService::class.java).apply {
                action = AlarmScheduler.ACTION_FIRE
                data = Uri.parse("koom://alarm/" + Uri.encode(id)).buildUpon()
                    .appendQueryParameter("occurrence", previous.token).build()
            }, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNotNull(oldIntent)
        assertTrue(scheduler.update(alarm.copy(minute = (alarm.minute + 1) % 60)))
        val replacement = AlarmStore(context).occurrence(id)!!
        assertNotEquals(previous.token, replacement.token)
        assertEquals(replacement.atMillis, scheduler.nextSystemAlarmMillis())
        assertTrue("Old generation must be canceled in Android",
            runCatching { oldIntent!!.send() }.exceptionOrNull() is PendingIntent.CanceledException)
    }

    @Test fun expiredOneShotIsMarkedMissedInsteadOfRingingDaysLater() {
        val alarm = Alarm(id = id, hour = 7, minute = 0)
        AlarmStore(context).upsert(alarm)
        AlarmStore(context).rememberOccurrence(id,
            AlarmOccurrence(System.currentTimeMillis() - 600_001L, "expired-occurrence"))
        assertTrue(scheduler.reconcile())
        assertFalse(AlarmStore(context).byId(id)!!.enabled)
        assertNull(AlarmStore(context).occurrence(id))
        assertTrue(AlarmStore(context).activeIds().isEmpty())
        assertTrue(AlarmDiagnostics(context).hasEvent(id, "MISSED"))
    }
}
