package top.zekal.koom

import org.json.JSONObject
import java.util.UUID

enum class PuzzleKind { MATH, SEQUENCE, IMAGE, KNOWLEDGE, LOGIC }

/**
 * Bit 0 is Sunday, bit 6 is Saturday. Zero means a one-time alarm.
 * soundFile is an app-owned filename, never an untrusted external absolute path.
 */
data class Alarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val label: String = "שעון מעורר",
    val enabled: Boolean = true,
    val daysMask: Int = 0,
    val puzzle: PuzzleKind = PuzzleKind.KNOWLEDGE,
    val soundFile: String? = null,
    val soundLabel: String = "צלצול הטלפון"
) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Invalid alarm time" }
        require(daysMask in 0..127) { "Invalid days mask" }
        require(id.isNotBlank()) { "Missing alarm identifier" }
        require(soundFile == null || soundFile.matches(Regex("[a-zA-Z0-9_-]{1,70}\\.audio"))) {
            "Invalid sound file name"
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("hour", hour)
        .put("minute", minute)
        .put("label", label)
        .put("enabled", enabled)
        .put("daysMask", daysMask)
        .put("puzzle", puzzle.name)
        .put("soundFile", soundFile)
        .put("soundLabel", soundLabel)

    companion object {
        fun fromJson(json: JSONObject): Alarm = Alarm(
            id = json.getString("id"),
            hour = json.getInt("hour"),
            minute = json.getInt("minute"),
            label = json.optString("label", "שעון מעורר"),
            enabled = json.optBoolean("enabled", true),
            daysMask = json.optInt("daysMask", 0),
            puzzle = PuzzleKind.entries.firstOrNull { it.name == json.optString("puzzle") }
                ?: PuzzleKind.MATH,
            soundFile = json.optString("soundFile").takeIf { it.isNotBlank() && it != "null" },
            soundLabel = json.optString("soundLabel", "צלצול הטלפון")
        )
    }
}
