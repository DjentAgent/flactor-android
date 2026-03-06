package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ManageFilesSelectionStateTest {

    @Test
    fun `selectedFilesForSave returns only checked and selectable files`() {
        val files = listOf(
            file("a.flac"),
            file("b.flac"),
            file("c.flac")
        )
        val selected = selectedFilesForSave(
            orderedForSave = files,
            selectableForSave = listOf(0, 2),
            checks = listOf(true, true, false)
        )
        assertEquals(listOf("a.flac"), selected.map { it.name })
    }

    @Test
    fun `removeCandidateForFile resolves task metadata when present`() {
        val topicId = 55
        val innerPath = "folder/song.flac"
        val taskId = buildDownloadTaskId(topicId = topicId, innerPath = innerPath)
        val tasks = listOf(
            DownloadTask(
                id = taskId,
                fileName = "Song From Task.flac",
                size = 1L,
                progress = 100,
                status = DownloadStatus.COMPLETED,
                errorMessage = null,
                contentUri = "content://song",
                torrentTitle = "t",
                speedBytesPerSec = 0L,
                createdAt = 0L
            )
        )

        val candidate = removeCandidateForFile(
            topicId = topicId,
            innerPath = innerPath,
            fallbackFileName = "Fallback.flac",
            tasks = tasks
        )

        assertNotNull(candidate)
        assertEquals(taskId, candidate!!.taskId)
        assertEquals("Song From Task.flac", candidate.fileName)
        assertEquals("content://song", candidate.contentUri)
    }

    @Test
    fun `removeCandidatesForCheckedCompleted returns only checked completed entries`() {
        val prep = PreparedFilesDl(
            ordered = listOf(
                UiFileDl(
                    file = file("a.flac"),
                    taskId = "id-a",
                    normalizedName = "a",
                    normalizedPath = "a",
                    dupKey = "a",
                    size = 1L,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    contentUri = "content://a",
                    completedAt = 1L,
                    disabled = false,
                    matchScore = 0
                ),
                UiFileDl(
                    file = file("b.flac"),
                    taskId = "id-b",
                    normalizedName = "b",
                    normalizedPath = "b",
                    dupKey = "b",
                    size = 1L,
                    status = DownloadStatus.CANCELED,
                    progress = 0,
                    contentUri = null,
                    completedAt = null,
                    disabled = false,
                    matchScore = 0
                )
            ),
            indexByPath = mapOf("folder/a.flac" to 0, "folder/b.flac" to 1),
            selectableIndices = listOf(1),
            removableCompletedIndices = listOf(0),
            initialChecks = listOf(false, false),
            duplicateKeys = emptySet(),
            matchedIndices = emptySet()
        )

        val candidates = removeCandidatesForCheckedCompleted(
            prep = prep,
            checks = listOf(true, true)
        )

        assertEquals(1, candidates.size)
        assertEquals("id-a", candidates.first().taskId)
        assertEquals("content://a", candidates.first().contentUri)
    }

    @Test
    fun `removeCandidatesForCheckedCompleted deduplicates by taskId`() {
        val prep = PreparedFilesDl(
            ordered = listOf(
                UiFileDl(
                    file = file("a-copy1.flac"),
                    taskId = "id-a",
                    normalizedName = "a",
                    normalizedPath = "a1",
                    dupKey = "a",
                    size = 1L,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    contentUri = "content://a",
                    completedAt = 1L,
                    disabled = false,
                    matchScore = 0
                ),
                UiFileDl(
                    file = file("a-copy2.flac"),
                    taskId = "id-a",
                    normalizedName = "a",
                    normalizedPath = "a2",
                    dupKey = "a",
                    size = 1L,
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    contentUri = "content://a",
                    completedAt = 1L,
                    disabled = false,
                    matchScore = 0
                )
            ),
            indexByPath = mapOf("folder/a-copy1.flac" to 0, "folder/a-copy2.flac" to 1),
            selectableIndices = emptyList(),
            removableCompletedIndices = listOf(0, 1),
            initialChecks = listOf(false, false),
            duplicateKeys = setOf("a"),
            matchedIndices = emptySet()
        )

        val candidates = removeCandidatesForCheckedCompleted(
            prep = prep,
            checks = listOf(true, true)
        )

        assertEquals(1, candidates.size)
        assertEquals("id-a", candidates.first().taskId)
    }

    @Test
    fun `removeCandidateForFile returns null when topicId is null`() {
        val candidate = removeCandidateForFile(
            topicId = null,
            innerPath = "x.flac",
            fallbackFileName = "x.flac",
            tasks = emptyList()
        )
        assertNull(candidate)
    }

    private fun file(name: String) = TorrentFile(
        name = name,
        size = 1L,
        torrentFilePath = "/tmp/a.torrent",
        innerPath = "folder/$name"
    )
}
