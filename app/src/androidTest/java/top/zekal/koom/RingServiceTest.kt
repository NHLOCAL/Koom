package top.zekal.koom

import android.app.ActivityManager
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RingServiceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val id = "service-recovery-regression"
    private val scheduler get() = AlarmScheduler(context)
    private val store get() = AlarmStore(context)

    @Before fun prepare() {
        assumeTrue(scheduler.canSchedule())
        cleanup()
    }

    @After fun cleanup() {
        scheduler.cancel(id)
        scheduler.cancelRingingRecovery()
        context.stopService(Intent(context, RingService::class.java))
        instrumentation.waitForIdleSync()
        context.createDeviceProtectedStorageContext().getSharedPreferences("koom_v2", 0)
            .edit().clear().commit()
        context.getSharedPreferences("koom_v2", 0).edit().clear().commit()
        context.getSharedPreferences("FlutterSharedPreferences", 0).edit().clear().commit()
    }

    @Test fun queuedEmptyStartCannotStopTheFollowingValidAlarm() {
        store.upsert(Alarm(id = id, hour = 7, minute = 0))
        store.rememberOccurrence(id, AlarmOccurrence(System.currentTimeMillis() + 120_000, "valid"))
        val started = System.currentTimeMillis()
        // Both AMS start requests are registered before the main thread can process either.
        instrumentation.runOnMainSync {
            context.startService(fire("deleted-alarm", "stale"))
            context.startService(fire(id, "valid"))
        }
        await("The valid queued start must play") {
            AlarmDiagnostics(context).recent().any {
                it.alarmId == id && it.stage == "AUDIO_STARTED" && it.atMillis >= started
            }
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(1_000)
        @Suppress("DEPRECATION")
        val running = context.getSystemService(ActivityManager::class.java)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == RingService::class.java.name && it.foreground }
        assertTrue("The earlier empty start must not destroy newer playback", running)
        assertEquals(listOf(id), store.activeIds())
    }

    @Test fun retriedDeliveryCompletesRecurringScheduleAfterAcceptanceWasCommitted() {
        seedAcceptedRecurringAlarm()
        assertNull(store.occurrence(id))
        // Simulate Android retrying the FIRE after death between commit and registration.
        context.startService(fire(id, "accepted-before-death"))
        await("Service retry must register the next repetition without opening an activity") {
            store.occurrence(id)?.atMillis == scheduler.nextSystemAlarmMillis() &&
                store.occurrence(id) != null
        }
        assertEquals(listOf(id), store.activeIds())
    }

    @Test fun stickyRecoveryRestoresPersistedNextOccurrenceWithoutChangingItsIdentity() {
        seedAcceptedRecurringAlarm()
        val pending = AlarmOccurrence(System.currentTimeMillis() + 120_000, "persisted-next")
        // This disk write survived; the AlarmManager registration did not happen yet.
        store.rememberOccurrence(id, pending)
        assertNull(scheduler.nextSystemAlarmMillis())
        context.startService(Intent(context, RingService::class.java))
        await("Restored ringing must restore the same next OS alarm") {
            scheduler.nextSystemAlarmMillis() == pending.atMillis
        }
        assertEquals(pending, store.occurrence(id))
    }

    private fun seedAcceptedRecurringAlarm() {
        store.upsert(Alarm(id = id, hour = 7, minute = 0, daysMask = 127))
        store.rememberOccurrence(id,
            AlarmOccurrence(System.currentTimeMillis(), "accepted-before-death"))
        assertNotNull(store.acceptTrigger(id, "accepted-before-death"))
    }

    private fun fire(alarmId: String, token: String) = Intent(context, RingService::class.java)
        .setAction(AlarmScheduler.ACTION_FIRE)
        .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        .putExtra(AlarmScheduler.EXTRA_OCCURRENCE_TOKEN, token)

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }
}
