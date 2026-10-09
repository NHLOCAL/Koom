package top.zekal.koom

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AlarmManager → PendingIntent.getForegroundService, without a visible Activity. */
@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {
    @Test fun bundledDirectBootMelodyIsPackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.resources.openRawResourceFd(R.raw.koom_chime).use { resource ->
            assertTrue("Chime resource must be playable offline", resource.length > 5000)
        }
    }

    @Test fun exactAlarmDeliversWithNoVisibleActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AlarmScheduler(context)
        assumeTrue("Exact alarms unavailable on this device", scheduler.canSchedule())
        val store = AlarmStore(context)
        val id = AlarmScheduler.TEST_ALARM_ID
        // Drain any old test alarm but leave user's normal alarms untouched.
        store.dismiss(id)
        scheduler.cancel(id)
        val startedAt = System.currentTimeMillis()
        assertTrue(scheduler.scheduleTest(7_000L))
        val deadline = SystemClock.elapsedRealtime() + 25_000L
        var received = false
        while (SystemClock.elapsedRealtime() < deadline) {
            if (AlarmDiagnostics(context).recent().any {
                    it.alarmId == id && it.stage == "RECEIVED" && it.atMillis >= startedAt
                }) {
                received = true
                break
            }
            SystemClock.sleep(250L)
        }
        store.dismiss(id)
        context.startService(Intent(context, RingService::class.java))
        assertTrue("AlarmManager never reached RingService", received)
    }
}
