package top.zekal.koom

import android.content.Context
import android.os.UserManager
import android.provider.Settings
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
            reconcileBoot()
            if (app.getSystemService(UserManager::class.java).isUserUnlocked) migrateIfNeeded()
        }
    }

    fun all(): List<Alarm> = synchronized(lock) { readAlarms() }

    fun byId(id: String): Alarm? = synchronized(lock) { readAlarms().firstOrNull { it.id == id } }

    fun upsert(alarm: Alarm) = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val index = rows.indexOfFirst { it.id == alarm.id }
        if (index < 0) rows.add(alarm) else rows[index] = alarm
        val occurrences = readOccurrences().apply { remove(alarm.id) }
        save(prefs.edit().putString(ALARMS, encodeAlarms(rows))
            .putString(OCCURRENCES, occurrences.toString()))
    }

    fun delete(id: String) = synchronized(lock) {
        save(prefs.edit()
            .putString(ALARMS, encodeAlarms(readAlarms().filterNot { it.id == id }))
            .putString(RINGING, JSONArray(readRinging().filterNot { it == id }).toString())
            .putString(RING_STARTED, readRingingTimes().apply { remove(id) }.toString())
            .putString(OCCURRENCES, readOccurrences().apply { remove(id) }.toString()))
    }

    fun occurrence(id: String): AlarmOccurrence? = synchronized(lock) {
        readOccurrences().optJSONObject(id)?.let {
            AlarmOccurrence(it.getLong("at"), it.getString("token"))
        }
    }

    fun rememberOccurrence(id: String, occurrence: AlarmOccurrence) = synchronized(lock) {
        require(readAlarms().any { it.id == id && it.enabled }) { "Alarm is no longer enabled" }
        val rows = readOccurrences().put(id, JSONObject()
            .put("at", occurrence.atMillis).put("token", occurrence.token))
        save(prefs.edit().putString(OCCURRENCES, rows.toString()))
    }

    fun forgetOccurrence(id: String) = synchronized(lock) {
        val rows = readOccurrences()
        if (rows.has(id)) {
            rows.remove(id)
            save(prefs.edit().putString(OCCURRENCES, rows.toString()))
        }
    }

    /** One atomic transaction accepts the delivery, disabling one-shots and recording ringing. */
    fun acceptTrigger(id: String, token: String? = null): Alarm? = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val position = rows.indexOfFirst { it.id == id && it.enabled }
        if (position == -1) return@synchronized null
        val occurrences = readOccurrences()
        val pendingToken = occurrences.optJSONObject(id)?.optString("token")
        // A queued intent can outlive an edit or dismissal. It must not consume the new alarm.
        if (token != null && token != pendingToken) return@synchronized null
        if (token == null && (pendingToken != null || readRinging().contains(id))) {
            return@synchronized null
        }
        val alarm = rows[position]
        if (alarm.daysMask == 0) rows[position] = alarm.copy(enabled = false)
        val ids = readRinging().toMutableList()
        if (!ids.contains(id)) ids.add(id)
        save(prefs.edit()
            .putString(ALARMS, encodeAlarms(rows))
            .putString(RINGING, JSONArray(ids).toString())
            .putString(RING_STARTED, readRingingTimes().put(id, System.currentTimeMillis()).toString())
            .putString(OCCURRENCES, occurrences.apply { remove(id) }.toString()))
        alarm
    }

    fun activeIds(): List<String> = synchronized(lock) {
        val old = readRinging()
        val existing = readAlarms().map { it.id }.toSet()
        val valid = old.filter { it in existing }.distinct()
        if (valid != old) save(prefs.edit().putString(RINGING, JSONArray(valid).toString()))
        valid
    }

    fun dismiss(id: String) = synchronized(lock) {
        val remaining = readRinging().filterNot { it == id }
        val edit = prefs.edit().putString(RINGING, JSONArray(remaining).toString())
            .putString(RING_STARTED, readRingingTimes().apply { remove(id) }.toString())
        if (remaining.isEmpty()) edit.remove(RING_RECOVERY)
        save(edit)
    }

    fun ringingRecoveryAtMillis(): Long? = synchronized(lock) {
        prefs.getLong(RING_RECOVERY, 0L).takeIf { it > 0L }
    }

    fun finishRingingRecovery() = synchronized(lock) {
        if (prefs.contains(RING_RECOVERY)) save(prefs.edit().remove(RING_RECOVERY))
    }

    private fun reconcileBoot() {
        val boot = Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot < 0 || prefs.getInt(BOOT_COUNT, -1) == boot) return
        val previousBoot = prefs.getInt(BOOT_COUNT, -1)
        val edit = prefs.edit().putInt(BOOT_COUNT, boot)
        // Initial upgrade while unlocked has no earlier boot marker. Preserve its state.
        if (previousBoot == -1 && app.getSystemService(UserManager::class.java).isUserUnlocked) {
            save(edit)
            return
        }
        val now = System.currentTimeMillis()
        val times = readRingingTimes()
        val retained = readRinging().filter { id ->
            val started = times.optLong(id, 0L)
            val keep = started > 0L && now - started in 0..AlarmRules.RECOVERY_WINDOW_MILLIS
            if (!keep) {
                times.remove(id)
                AlarmDiagnostics(app).record("RINGING_EXPIRED_AFTER_BOOT", id)
            }
            keep
        }
        edit.putString(RINGING, JSONArray(retained).toString())
            .putString(RING_STARTED, times.toString())
        if (retained.isNotEmpty()) edit.putLong(RING_RECOVERY, now + 5_000L)
        else edit.remove(RING_RECOVERY)
        save(edit)
    }

    private fun readAlarms(): List<Alarm> {
        val rows = JSONArray(prefs.getString(ALARMS, "[]") ?: "[]")
        return (0 until rows.length()).map { Alarm.fromJson(rows.getJSONObject(it)) }
    }

    private fun readRinging(): List<String> {
        val rows = JSONArray(prefs.getString(RINGING, "[]") ?: "[]")
        return (0 until rows.length()).map { rows.getString(it) }
    }

    private fun readOccurrences(): JSONObject =
        JSONObject(prefs.getString(OCCURRENCES, "{}") ?: "{}")

    private fun readRingingTimes(): JSONObject =
        JSONObject(prefs.getString(RING_STARTED, "{}") ?: "{}")

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
                .putString(RINGING, if (prefs.contains(RINGING)) prefs.getString(RINGING, "[]")
                    else oldV2.getString(RINGING, "[]") ?: "[]"))
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
        private const val OCCURRENCES = "pending_occurrences_v1"
        private const val RING_STARTED = "ringing_started_v1"
        private const val RING_RECOVERY = "ringing_recovery_at_v1"
        private const val BOOT_COUNT = "boot_count_v1"
        private const val LIST_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"
        private const val JSON_PREFIX = LIST_PREFIX + "!"
    }
}
