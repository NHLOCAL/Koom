package top.zekal.koom

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.PowerManager
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.UUID

data class ChosenSound(val fileName: String, val label: String)

/** Own a durable local copy: SAF and ringtone-provider permissions can expire at boot. */
object AlarmSounds {
    private const val MAX_BYTES = 25L * 1024L * 1024L

    private fun dir(context: Context): File =
        File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, "alarm_sounds")
            .apply { if (!exists()) check(mkdirs()) { "Could not create sound directory" } }

    fun file(context: Context, name: String?): File? {
        if (name.isNullOrBlank() || !name.matches(Regex("[a-zA-Z0-9_-]{1,70}\\.audio"))) return null
        return File(dir(context), name).takeIf { it.isFile }
    }

    fun copyFromUri(context: Context, uri: Uri, displayLabel: String? = null): ChosenSound {
        require(uri.scheme == "content" || uri.scheme == "android.resource") {
            "Please select audio using the system picker"
        }
        val resolver = context.contentResolver
        val label = displayLabel ?: runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "מנגינה שבחרת"

        val destination = File(dir(context), UUID.randomUUID().toString() + ".audio")
        try {
            val source = resolver.openInputStream(uri) ?: error("Sound file cannot be opened")
            source.use { input ->
                destination.outputStream().buffered().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > MAX_BYTES) throw IOException("קובץ גדול מדי (מעל 25MB)")
                        output.write(buffer, 0, read)
                    }
                    if (total == 0L) throw IOException("קובץ שמע ריק")
                }
            }
            // Verify the file is decodable BEFORE storing its reference.
            val probe = MediaPlayer()
            try {
                probe.setDataSource(destination.absolutePath)
                probe.prepare()
            } finally { probe.release() }
            return ChosenSound(destination.name, label.take(65))
        } catch (error: Exception) {
            destination.delete()
            Log.e("KoomSounds", "Sound import failed", error)
            throw error
        }
    }

    fun defaultUri() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun start(context: Context, soundFile: String?, loop: Boolean = true): MediaPlayer {
        val player = MediaPlayer()
        try {
            // Android holds the CPU awake only while the ringtone is actually playing.
            player.setWakeMode(context.applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            player.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            val localFile = file(context, soundFile)
            if (localFile != null) {
                player.setDataSource(localFile.absolutePath)
            } else {
                // A pleasant phone ringtone rather than the former emergency siren.
                player.setDataSource(context, defaultUri())
            }
            player.isLooping = loop
            player.prepare()
            player.start()
            return player
        } catch (error: Exception) {
            player.release()
            throw error
        }
    }
}
