package top.zekal.koom

import android.content.Context
import android.app.ActivityManager
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Durable on-device trace. Nothing is sent to the internet. */
data class AlarmEvent(val atMillis: Long, val stage: String, val alarmId: String, val detail: String)

class AlarmDiagnostics(context: Context) {
    private val app = context.applicationContext
    private val prefs = context.applicationContext.createDeviceProtectedStorageContext()
        .getSharedPreferences("koom_diagnostics_v1", Context.MODE_PRIVATE)

    fun record(stage: String, id: String = "", detail: String = "") {
        Log.i("KoomDelivery", "$stage ($id) " + detail.take(200))
        try {
            synchronized(lock) {
                val old = readArray()
                val updated = JSONArray()
                updated.put(JSONObject().put("at", System.currentTimeMillis())
                    .put("stage", stage).put("id", id).put("detail", detail.take(250)))
                for (i in 0 until minOf(79, old.length())) updated.put(old.getJSONObject(i))
                check(prefs.edit().putString("events", updated.toString()).commit())
            }
        } catch (e: Exception) {
            Log.e("KoomDelivery", "Failed to persist local event", e)
        }
    }

    fun recent(): List<AlarmEvent> = synchronized(lock) {
        val list = readArray()
        (0 until list.length()).mapNotNull { i -> runCatching {
            val row = list.getJSONObject(i)
            AlarmEvent(row.optLong("at"), row.optString("stage"),
                row.optString("id"), row.optString("detail"))
        }.getOrNull() }
    }

    fun hasEvent(id: String, stage: String) =
        recent().any { it.alarmId == id && it.stage == stage }

    fun recordDeviceState() {
        runCatching {
            val activity = app.getSystemService(ActivityManager::class.java)
            val audio = app.getSystemService(AudioManager::class.java)
            val battery = app.getSystemService(PowerManager::class.java)
            val restricted = Build.VERSION.SDK_INT >= 28 && activity.isBackgroundRestricted
            record("DEVICE_STATE", detail = "${Build.MANUFACTURER} ${Build.MODEL}; API ${Build.VERSION.SDK_INT}; " +
                "restricted=$restricted; batteryExempt=${battery.isIgnoringBatteryOptimizations(app.packageName)}; " +
                "alarmVolume=${audio.getStreamVolume(AudioManager.STREAM_ALARM)}/${audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)}")
            if (Build.VERSION.SDK_INT >= 30) {
                activity.getHistoricalProcessExitReasons(app.packageName, 0, 1).firstOrNull()?.let {
                    record("LAST_PROCESS_EXIT", detail = "at=${it.timestamp}; reason=${it.reason}; ${it.description.orEmpty()}")
                }
            }
        }.onFailure { Log.w("KoomDelivery", "Unable to read device diagnostics", it) }
    }

    private fun readArray() = runCatching {
        JSONArray(prefs.getString("events", "[]") ?: "[]")
    }.getOrElse { JSONArray() }

    companion object { private val lock = Any() }
}
