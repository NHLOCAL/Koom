package top.zekal.koom

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AlarmSoundsTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val testRoot = File(target.createDeviceProtectedStorageContext().filesDir,
        "sound-test-${UUID.randomUUID()}")
    // Use the real Android resolver and player with a separate directory per test.
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun createDeviceProtectedStorageContext(): Context = this
        override fun getFilesDir(): File = testRoot
    }
    private val directory get() = File(
        context.createDeviceProtectedStorageContext().filesDir, "alarm_sounds"
    )
    private val drafts = mutableListOf<String>()

    @Before fun prepare() {
        directory.mkdirs()
    }

    @After fun cleanup() {
        drafts.forEach { AlarmSounds.finishDraft(context, it, emptySet()) }
        testRoot.deleteRecursively()
    }

    private fun sound(name: String): File = File(directory, "$name.audio").apply {
        writeText("test sound storage")
    }

    private fun draft(selectedFile: String? = null): String = UUID.randomUUID().toString().also {
        drafts.add(it)
        AlarmSounds.beginDraft(it, selectedFile)
    }

    @Test fun pruneKeepsEverySavedReferenceAndOnlyDeletesOwnedAudioFiles() {
        val saved = sound("saved")
        val abandoned = sound("abandoned")
        val unrelated = File(directory, "other.data").apply { writeText("leave alone") }

        AlarmSounds.prune(context, setOf(saved.name))

        assertTrue(saved.isFile)
        assertFalse(abandoned.exists())
        assertTrue(unrelated.isFile)
    }

    @Test fun draftSurvivesPruningAndConfigurationReattachment() {
        val selected = sound("selected")
        val owner = draft(selected.name)

        AlarmSounds.prune(context, emptySet())
        AlarmSounds.beginDraft(owner, selected.name)
        AlarmSounds.prune(context, emptySet())

        assertTrue("Unsaved selection must survive picker resume or rotation", selected.isFile)
    }

    @Test fun supersededSelectionSurvivesUntilDraftFinishes() {
        val previous = sound("previous")
        val selected = sound("selected")
        val owner = draft(previous.name)
        AlarmSounds.beginDraft(owner, selected.name)

        AlarmSounds.prune(context, emptySet())
        assertTrue("A saved activity snapshot can still refer to the previous selection", previous.isFile)
        assertTrue(selected.isFile)

        AlarmSounds.finishDraft(context, owner, setOf(selected.name))

        assertFalse(previous.exists())
        assertTrue("The committed alarm still owns this sound", selected.isFile)
    }

    @Test fun selectingDefaultKeepsEarlierSnapshotUntilDraftFinishes() {
        val previous = sound("previous")
        val owner = draft(previous.name)

        AlarmSounds.beginDraft(owner, selectedFile = null)
        AlarmSounds.prune(context, emptySet())

        assertTrue("Changing to default must not invalidate an older saved snapshot", previous.isFile)
        AlarmSounds.finishDraft(context, owner, emptySet())
        assertFalse(previous.exists())
    }

    @Test fun cancellingImportKeepsSuccessfulSelectionsAndOtherEditorsSound() {
        val previous = sound("previous")
        val cancelled = sound("cancelled")
        val otherSelection = sound("other-selection")
        val owner = draft(previous.name)
        AlarmSounds.beginDraft(owner, cancelled.name)
        draft(otherSelection.name)

        AlarmSounds.discardImport(context, owner, cancelled.name, emptySet())
        AlarmSounds.prune(context, emptySet())

        assertFalse(cancelled.exists())
        assertTrue(previous.isFile)
        assertTrue(otherSelection.isFile)
    }

    @Test fun discardedSelectionCannotDeleteASavedReference() {
        val shared = sound("shared")
        val owner = draft(shared.name)

        AlarmSounds.discardImport(context, owner, shared.name, setOf(shared.name))

        assertTrue(shared.isFile)
    }

    @Test fun importedAudioStaysPendingUntilDraftIsDiscarded() {
        val owner = draft()
        val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.koom_chime}")
        val imported = AlarmSounds.copyFromUri(context, uri, "Test chime", owner)
        val local = requireNotNull(AlarmSounds.file(context, imported.fileName))

        AlarmSounds.prune(context, emptySet())
        assertTrue("A successful import is not an abandoned file", local.isFile)
        AlarmSounds.finishDraft(context, owner, emptySet())

        assertFalse("Leaving without saving releases the imported file", local.exists())
    }
}
