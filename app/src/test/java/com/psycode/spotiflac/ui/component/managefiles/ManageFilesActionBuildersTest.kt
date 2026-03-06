package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManageFilesActionBuildersTest {

    @Test
    fun `buildQueueSelectionAction returns null when nothing selected`() {
        val files = listOf(file("a.flac"), file("b.flac"))
        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = listOf(0, 1),
            checks = listOf(false, false),
            saveOption = SaveOption.MUSIC_LIBRARY,
            customUri = null
        )
        assertNull(action)
    }

    @Test
    fun `buildQueueSelectionAction returns selected files and params`() {
        val files = listOf(file("a.flac"), file("b.flac"), file("c.flac"))
        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = listOf(0, 2),
            checks = listOf(true, true, true),
            saveOption = SaveOption.CUSTOM_FOLDER,
            customUri = "content://tree/music"
        )

        requireNotNull(action)
        assertEquals(listOf(files[0], files[2]), action.files)
        assertEquals(SaveOption.CUSTOM_FOLDER, action.saveOption)
        assertEquals("content://tree/music", action.customUri)
    }

    @Test
    fun `buildRemoveCompletedAction maps candidate and delete flag`() {
        val candidate = RemoveCandidate(
            taskId = "42_hash",
            fileName = "track.flac",
            contentUri = "content://media/1"
        )
        val action = buildRemoveCompletedAction(candidate, alsoDeleteLocal = true)
        assertEquals("42_hash", action.taskId)
        assertEquals(true, action.alsoDeleteLocal)
        assertEquals("content://media/1", action.contentUri)
    }

    @Test
    fun `reducers and builder keep only selectable files in queue action`() {
        val files = listOf(file("a.flac"), file("b.flac"), file("c.flac"))
        val selectable = listOf(0, 2)
        val checks = listOf(false, false, false)
        val afterToggleAll = toggleAllSelectable(checks = checks, selectableIndices = selectable)

        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = selectable,
            checks = afterToggleAll,
            saveOption = SaveOption.MUSIC_LIBRARY,
            customUri = null
        )

        requireNotNull(action)
        assertEquals(listOf(true, false, true), afterToggleAll)
        assertEquals(listOf(files[0], files[2]), action.files)
    }

    @Test
    fun `builder ignores stale selectable indices`() {
        val files = listOf(file("a.flac"))
        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = listOf(0, 10),
            checks = listOf(true),
            saveOption = SaveOption.CUSTOM_FOLDER,
            customUri = "content://tree/music"
        )
        requireNotNull(action)
        assertEquals(1, action.files.size)
        assertEquals(files[0], action.files.first())
    }

    @Test
    fun `builder is stable with duplicate selectable indices`() {
        val files = listOf(file("a.flac"), file("b.flac"))
        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = listOf(0, 0, 1),
            checks = listOf(true, true),
            saveOption = SaveOption.MUSIC_LIBRARY,
            customUri = null
        )
        requireNotNull(action)
        assertEquals(listOf(files[0], files[1]), action.files)
    }

    @Test
    fun `builder handles checks shorter than ordered files`() {
        val files = listOf(file("a.flac"), file("b.flac"), file("c.flac"))
        val action = buildQueueSelectionAction(
            orderedForSave = files,
            selectableForSave = listOf(0, 1, 2),
            checks = listOf(true),
            saveOption = SaveOption.MUSIC_LIBRARY,
            customUri = null
        )
        requireNotNull(action)
        assertEquals(listOf(files[0]), action.files)
    }

    private fun file(name: String): TorrentFile = TorrentFile(
        name = name,
        size = 100L,
        torrentFilePath = "/tmp/test.torrent",
        innerPath = name
    )
}
