package top.zekal.koom

import android.content.Context
import android.os.UserManager
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

/**
 * The scheduler must read alarms before the user unlocks the device after reboot.
 * Saved alarms live in device-protected storage; Flutter/v2 credential-protected
 * records are migrated once the user unlocks, without deleting their source.
 */
class AlarmStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.createDeviceProtectedStorageContext()
        .getSharedPreferences("koom_v2", Context.MODE_PRIVATE)
    // Credential-protected preferences must not be opened during direct boot.
    private val oldV2 by lazy { app.getSharedPreferences("koom_v2", Context.MODE_PRIVATE) }
    private val flutter by lazy { app.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE) }

    init {
        synchronized(lock) {
            if (app.getSystemService(UserManager::class.java).isUserUnlocked) migrateIfNeeded()
        }
    }

    fun all(): List<Alarm> = synchronized(lock) { readAlarms() }

    fun byId(id: String): Alarm? = synchronized(lock) { readAlarms().firstOrNull { it.id == id } }

    fun upsert(alarm: Alarm) = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val index = rows.indexOfFirst { it.id == alarm.id }
        if (index < 0) rows.add(alarm) else rows[index] = alarm
        save(prefs.edit().putString(ALARMS, encodeAlarms(rows)))
    }

    fun delete(id: String) = synchronized(lock) {
        save(prefs.edit()
            .putString(ALARMS, encodeAlarms(readAlarms().filterNot { it.id == id }))
            .putString(RINGING, JSONArray(readRinging().filterNot { it == id }).toString()))
    }

    /** One atomic transaction accepts the delivery, disabling one-shots and recording ringing. */
    fun acceptTrigger(id: String): Alarm? = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val position = rows.indexOfFirst { it.id == id && it.enabled }
        if (position == -1) return@synchronized null
        val alarm = rows[position]
        if (alarm.daysMask == 0) rows[position] = alarm.copy(enabled = false)
        val ids = readRinging().toMutableList()
        if (!ids.contains(id)) ids.add(id)
        save(prefs.edit()
            .putString(ALARMS, encodeAlarms(rows))
            .putString(RINGING, JSONArray(ids).toString()))
        alarm
    }

    fun activeIds(): List<String> = synchronized(lock) { readRinging() }

    fun dismiss(id: String) = synchronized(lock) {
        save(prefs.edit().putString(RINGING, JSONArray(readRinging().filterNot { it == id }).toString()))
    }

    fun clearStaleRingingAfterBoot() = synchronized(lock) {
        save(prefs.edit().putString(RINGING, "[]"))
    }

    private fun readAlarms(): List<Alarm> {
        val rows = JSONArray(prefs.getString(ALARMS, "[]") ?: "[]")
        return (0 until rows.length()).map { Alarm.fromJson(rows.getJSONObject(it)) }
    }

    private fun readRinging(): List<String> {
        val rows = JSONArray(prefs.getString(RINGING, "[]") ?: "[]")
        return (0 until rows.length()).map { rows.getString(it) }
    }

    private fun encodeAlarms(alarms: List<Alarm>): String =
        JSONArray().apply { alarms.forEach { put(it.toJson()) } }.toString()

    private fun save(editor: android.content.SharedPreferences.Editor) {
        check(editor.commit()) { "Could not persist alarms" }
    }

    private fun migrateIfNeeded() {
        if (prefs.contains(ALARMS)) return
        if (oldV2.contains(ALARMS)) {
            // Preserve already-upgraded Koom 2.0 installations.
            val encoded = oldV2.getString(ALARMS, "[]") ?: "[]"
            // Validate before committing to the new location.
            val rows = JSONArray(encoded)
            for (i in 0 until rows.length()) Alarm.fromJson(rows.getJSONObject(i))
            save(prefs.edit().putString(ALARMS, encoded)
                .putString(RINGING, oldV2.getString(RINGING, "[]") ?: "[]"))
            return
        }

        val raw = flutter.all["flutter.alarms"]
        val old = when (raw) {
            null -> emptyList()
            is Set<*> -> raw.map { it as String }
            is String -> decodeFlutterList(raw)
            else -> error("Unsupported Flutter alarms format; source remains untouched")
        }
        val converted = old.map { entry ->
            val json = JSONObject(entry)
            val days = json.getJSONArray("days")
            var mask = 0
            for (index in 0 until minOf(7, days.length())) {
                if (days.getBoolean(index)) mask = mask or (1 shl index)
            }
            Alarm(
                id = json.getString("id"),
                hour = json.getInt("hour"), minute = json.getInt("minute"),
                label = json.optString("label", "שעון מעורר"),
                enabled = json.optBoolean("isActive", true),
                daysMask = mask,
                puzzle = when (json.optString("puzzleType")) {
                    "sequence" -> PuzzleKind.SEQUENCE
                    "image" -> PuzzleKind.IMAGE
                    else -> PuzzleKind.MATH
                }
            )
        }
        save(prefs.edit().putString(ALARMS, encodeAlarms(converted)))
    }

    private fun decodeFlutterList(value: String): List<String> {
        if (value.startsWith(JSON_PREFIX)) {
            val array = JSONArray(value.substring(JSON_PREFIX.length))
            return (0 until array.length()).map { array.getString(it) }
        }
        if (value.startsWith(LIST_PREFIX)) {
            val serialized = Base64.decode(value.substring(LIST_PREFIX.length), Base64.DEFAULT)
            val obj = RestrictedInput(ByteArrayInputStream(serialized)).use { it.readObject() }
            require(obj is List<*> && obj.all { it is String })
            return obj.map { it as String }
        }
        val array = JSONArray(value)
        return (0 until array.length()).map { array.getString(it) }
    }

    private class RestrictedInput(input: ByteArrayInputStream) : ObjectInputStream(input) {
        override fun resolveClass(desc: java.io.ObjectStreamClass): Class<*> {
            require(desc.name in setOf("java.util.ArrayList", "java.lang.String",
                "[Ljava.lang.Object;", "[Ljava.lang.String;")) { "Unexpected serialized class" }
            return super.resolveClass(desc)
        }
    }

    companion object {
        private val lock = Any()
        private const val ALARMS = "alarms_v2"
        private const val RINGING = "ringing_v2"
        private const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
        private const val JSON_PREFIX = LIST_PREFIX + "!"
    }
}
