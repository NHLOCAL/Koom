package top.zekal.koom

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

/**
 * A tiny, synchronously durable store. Receiver reads must never depend on a running UI,
 * an async cache, or an open database transaction.
 *
 * A one-time migration reads Flutter's SharedPreferences data in-place. It never deletes
 * the original, and marks migration complete only after successfully committing the copy.
 */
class AlarmStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("koom_v2", Context.MODE_PRIVATE)
    private val flutter = context.applicationContext.getSharedPreferences(
        "FlutterSharedPreferences", Context.MODE_PRIVATE
    )

    init { synchronized(lock) { migrateIfNeeded() } }

    fun all(): List<Alarm> = synchronized(lock) { readAlarms() }

    fun byId(id: String): Alarm? = synchronized(lock) { readAlarms().firstOrNull { it.id == id } }

    fun upsert(alarm: Alarm) = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val index = rows.indexOfFirst { it.id == alarm.id }
        if (index < 0) rows.add(alarm) else rows[index] = alarm
        writeAlarms(rows)
    }

    fun delete(id: String) = synchronized(lock) {
        writeAlarms(readAlarms().filterNot { it.id == id })
        writeRinging(readRinging().filterNot { it == id })
    }

    /** Atomically accepts a delivered alarm, one-shot disable + active alarm queue. */
    fun acceptTrigger(id: String): Alarm? = synchronized(lock) {
        val rows = readAlarms().toMutableList()
        val index = rows.indexOfFirst { it.id == id && it.enabled }
        if (index == -1) return@synchronized null
        val alarm = rows[index]
        if (alarm.daysMask == 0) rows[index] = alarm.copy(enabled = false)
        val active = readRinging().toMutableList()
        if (!active.contains(id)) active.add(id)
        commit(
            prefs.edit()
                .putString(ALARMS, encodeAlarms(rows))
                .putString(RINGING, JSONArray(active).toString())
        )
        alarm
    }

    fun activeIds(): List<String> = synchronized(lock) { readRinging() }

    fun dismiss(id: String) = synchronized(lock) {
        writeRinging(readRinging().filterNot { it == id })
    }

    fun clearStaleRingingAfterBoot() = synchronized(lock) { writeRinging(emptyList()) }

    private fun readAlarms(): List<Alarm> {
        val rows = JSONArray(prefs.getString(ALARMS, "[]") ?: "[]")
        return (0 until rows.length()).map { Alarm.fromJson(rows.getJSONObject(it)) }
    }

    private fun encodeAlarms(rows: List<Alarm>): String =
        JSONArray().apply { rows.forEach { put(it.toJson()) } }.toString()

    private fun readRinging(): List<String> {
        val items = JSONArray(prefs.getString(RINGING, "[]") ?: "[]")
        return (0 until items.length()).map { items.getString(it) }
    }

    private fun writeRinging(ids: List<String>) =
        commit(prefs.edit().putString(RINGING, JSONArray(ids).toString()))

    private fun writeAlarms(rows: List<Alarm>) =
        commit(prefs.edit().putString(ALARMS, encodeAlarms(rows)))

    private fun commit(editor: android.content.SharedPreferences.Editor) {
        check(editor.commit()) { "Unable to save alarms to device storage" }
    }

    private fun migrateIfNeeded() {
        if (prefs.contains(ALARMS)) return
        val raw = flutter.all["flutter.alarms"]
        val old = when (raw) {
            null -> emptyList()
            is Set<*> -> raw.map { it as String }
            is String -> decodeFlutterList(raw)
            else -> error("Unknown Flutter alarm format; refusing to overwrite it")
        }
        val converted = old.map { value ->
            val json = JSONObject(value)
            val days = json.getJSONArray("days")
            var mask = 0
            for (i in 0 until minOf(7, days.length())) {
                if (days.getBoolean(i)) mask = mask or (1 shl i)
            }
            Alarm(
                id = json.getString("id"),
                hour = json.getInt("hour"),
                minute = json.getInt("minute"),
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
        commit(prefs.edit().putString(ALARMS, encodeAlarms(converted)))
    }

    private fun decodeFlutterList(stored: String): List<String> {
        if (stored.startsWith(JSON_PREFIX)) {
            val array = JSONArray(stored.substring(JSON_PREFIX.length))
            return (0 until array.length()).map { array.getString(it) }
        }
        if (stored.startsWith(LIST_PREFIX)) {
            val bytes = Base64.decode(stored.substring(LIST_PREFIX.length), Base64.DEFAULT)
            val decoded = RestrictedObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }
            require(decoded is List<*> && decoded.all { it is String }) {
                "Invalid legacy alarm list"
            }
            return decoded.map { it as String }
        }
        // Support already-decoded JSON arrays from test and hand-migrated builds.
        val array = JSONArray(stored)
        return (0 until array.length()).map { array.getString(it) }
    }

    private class RestrictedObjectInputStream(stream: ByteArrayInputStream) :
        ObjectInputStream(stream) {
        override fun resolveClass(desc: java.io.ObjectStreamClass): Class<*> {
            require(desc.name in setOf(
                "java.util.ArrayList", "java.lang.String", "[Ljava.lang.Object;",
                "[Ljava.lang.String;"
            )) { "Unexpected serialized class" }
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
