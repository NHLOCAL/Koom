package top.zekal.koom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class AlarmStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clear() {
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("koom_v2", 0).edit().clear().commit()
        context.getSharedPreferences("koom_v2", 0).edit().clear().commit()
        context.getSharedPreferences("FlutterSharedPreferences", 0).edit().clear().commit()
    }
    @After fun cleanup() = clear()

    @Test fun persistsAlarmAndRoundTrips() {
        val alarm = Alarm(hour = 7, minute = 43, label = "לימוד", daysMask = 31, puzzle = PuzzleKind.SEQUENCE)
        AlarmStore(context).upsert(alarm)
        assertEquals(alarm, AlarmStore(context).byId(alarm.id))
    }

    @Test fun recurringAlarmRemainsEnabledOnDelivery() {
        val alarm = Alarm(hour = 6, minute = 0, daysMask = 127)
        val store = AlarmStore(context)
        store.upsert(alarm)
        assertNotNull(store.acceptTrigger(alarm.id))
        assertEquals(true, AlarmStore(context).byId(alarm.id)?.enabled)
        assertTrue(store.activeIds().contains(alarm.id))
        store.dismiss(alarm.id)
        assertFalse(store.activeIds().contains(alarm.id))
    }

    @Test fun oneShotAlarmDisablesAtomicallyOnDelivery() {
        val alarm = Alarm(hour = 6, minute = 0)
        val store = AlarmStore(context)
        store.upsert(alarm)
        assertNotNull(store.acceptTrigger(alarm.id))
        assertFalse(AlarmStore(context).byId(alarm.id)!!.enabled)
        assertNull(store.acceptTrigger(alarm.id))
    }

    @Test fun flutterJsonListMigratesWithIdAndDays() {
        val original = JSONObject()
            .put("id", "kept-id-123")
            .put("hour", 9).put("minute", 8)
            .put("label", "מבחן")
            .put("isActive", true)
            .put("days", JSONArray(listOf(true, false, false, true, false, false, false)))
            .put("puzzleType", "image")
        val legacyList = JSONArray(listOf(original.toString())).toString()
        context.getSharedPreferences("FlutterSharedPreferences", 0)
            .edit().putString("flutter.alarms",
                "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!" + legacyList).commit()
        val alarms = AlarmStore(context).all()
        assertEquals(1, alarms.size)
        assertEquals("kept-id-123", alarms[0].id)
        assertEquals((1 shl 0) or (1 shl 3), alarms[0].daysMask)
        assertEquals(PuzzleKind.IMAGE, alarms[0].puzzle)
    }

    @Test fun migratesExistingVersion2AlarmsToDirectBootStorage() {
        val alarm = Alarm(hour = 6, minute = 22, label = "אלול", soundLabel = "צלצול הטלפון")
        val array = org.json.JSONArray().put(alarm.toJson()).toString()
        context.getSharedPreferences("koom_v2", 0).edit().putString("alarms_v2", array).commit()
        assertEquals(alarm, AlarmStore(context).byId(alarm.id))
        assertEquals(array, context.createDeviceProtectedStorageContext()
            .getSharedPreferences("koom_v2", 0).getString("alarms_v2", null))
    }

    @Test fun customSoundFieldsRoundTrip() {
        val alarm = Alarm(hour = 10, minute = 9, soundFile = "abc123.audio",
            soundLabel = "צלצול עדין", puzzle = PuzzleKind.KNOWLEDGE)
        val store = AlarmStore(context)
        store.upsert(alarm)
        assertEquals(alarm, store.byId(alarm.id))
    }

    @Test fun deleteRemovesAlarmAndRingingQueue() {
        val store = AlarmStore(context)
        val alarm = Alarm(hour = 8, minute = 0)
        store.upsert(alarm)
        store.acceptTrigger(alarm.id)
        store.delete(alarm.id)
        assertNull(store.byId(alarm.id))
        assertEquals(emptyList<String>(), store.activeIds())
    }
}
