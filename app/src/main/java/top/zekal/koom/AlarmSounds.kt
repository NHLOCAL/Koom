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
    private val ownedFileName = Regex("[a-zA-Z0-9_-]{1,70}\\.audio")
    private val lock = Any()
    // Keep every successful selection for the draft's lifetime: an activity's
    // last saved snapshot may refer to a sound superseded by a background import.
    // Tokens survive recreation; finishDraft releases them after save/discard.
    private val drafts = mutableMapOf<String, MutableSet<String>>()
    private val copying = mutableSetOf<String>()

    private fun dir(context: Context): File =
        File(context.applicationContext.createDeviceProtectedStorageContext().filesDir, "alarm_sounds")
            .apply { check(isDirectory || mkdirs() || isDirectory) { "Could not create sound directory" } }

    fun file(context: Context, name: String?): File? {
        if (name.isNullOrBlank() || !name.matches(ownedFileName)) return null
        return File(dir(context), name).takeIf { it.isFile }
    }

    fun beginDraft(id: String, selectedFile: String? = null) = synchronized(lock) {
        val files = drafts.getOrPut(id) { mutableSetOf() }
        if (selectedFile != null && selectedFile.matches(ownedFileName)) files.add(selectedFile)
    }

    /** Release only a failed/cancelled result that never became an editor selection. */
    fun discardImport(context: Context, draftId: String, name: String?, savedFiles: Set<String>) {
        if (name == null) return
        synchronized(lock) {
            drafts[draftId]?.remove(name)
            deleteIfUnused(context, name, savedFiles)
        }
    }

    fun finishDraft(context: Context, id: String, savedFiles: Set<String>) = synchronized(lock) {
        drafts.remove(id)
        prune(context, savedFiles)
    }

    /** Call with references from every saved alarm, including disabled alarms. */
    fun prune(context: Context, savedFiles: Set<String>) = synchronized(lock) {
        dir(context).listFiles()?.forEach { candidate ->
            if (candidate.isFile && candidate.name.matches(ownedFileName)) {
                deleteIfUnused(context, candidate.name, savedFiles)
            }
        }
    }

    private fun deleteIfUnused(context: Context, name: String, savedFiles: Set<String>) {
        if (!name.matches(ownedFileName) || name in savedFiles || name in copying ||
            drafts.values.any { name in it }) return
        val candidate = File(dir(context), name)
        if (candidate.isFile && !candidate.delete()) Log.w("KoomSounds", "Could not remove unused sound")
    }

    fun copyFromUri(context: Context, uri: Uri, displayLabel: String? = null,
                    draftId: String): ChosenSound {
        require(uri.scheme == "content" || uri.scheme == "android.resource") {
            "Please select audio using the system picker"
        }
        val resolver = context.contentResolver
        val label = displayLabel ?: runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: "מנגינה שבחרת"

        val destination = synchronized(lock) {
            val files = checkNotNull(drafts[draftId]) { "Sound editor was closed" }
            File(dir(context), UUID.randomUUID().toString() + ".audio").also {
                files.add(it.name)
                copying.add(it.name)
            }
        }
        var completed = false
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
            synchronized(lock) {
                check(drafts[draftId]?.contains(destination.name) == true) { "Sound editor was closed" }
                completed = true
            }
            return ChosenSound(destination.name, label.take(65))
        } catch (error: Exception) {
            Log.e("KoomSounds", "Sound import failed", error)
            throw error
        } finally {
            synchronized(lock) {
                copying.remove(destination.name)
                if (!completed || drafts[draftId]?.contains(destination.name) != true) {
                    drafts[draftId]?.remove(destination.name)
                    destination.delete()
                }
            }
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
