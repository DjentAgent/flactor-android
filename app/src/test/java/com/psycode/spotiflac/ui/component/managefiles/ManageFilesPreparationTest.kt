package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageFilesPreparationTest {

    @Test
    fun `auto match accepts exact title candidate`() {
        val files = listOf(
            torrentFile("01 - One.flac", 120L * 1024 * 1024, "Album/01 - One.flac"),
            torrentFile("02 - Two.flac", 120L * 1024 * 1024, "Album/02 - Two.flac")
        )

        val result = prepareFilesDl(
            files = files,
            tasks = emptyList(),
            topicId = 123,
            groupTitle = "Metallica - One",
            groupNorm = normalizeCore("Metallica - One"),
            autoMatchEnabled = true,
            autoMatchArtist = "Metallica",
            autoMatchTitle = "One",
            autoMatchMinFuzzy = 0.84
        )

        assertEquals(1, result.matchedIndices.size)
        val matched = result.matchedIndices.first()
        assertEquals("01 - One.flac", result.ordered[matched].file.name)
        assertTrue(result.initialChecks[matched])
    }

    @Test
    fun `artist only candidate is rejected when score is below threshold`() {
        val files = listOf(
            torrentFile("Metallica rehearsal.mp3", 5L * 1024 * 1024, "bootlegs/Metallica rehearsal.mp3")
        )

        val result = prepareFilesDl(
            files = files,
            tasks = emptyList(),
            topicId = 123,
            groupTitle = "Bootleg",
            groupNorm = normalizeCore("Bootleg"),
            autoMatchEnabled = true,
            autoMatchArtist = "Metallica",
            autoMatchTitle = "",
            autoMatchMinFuzzy = 0.84
        )

        assertTrue(result.matchedIndices.isEmpty())
    }

    @Test
    fun `completed task is excluded from selectable indices`() {
        val files = listOf(
            torrentFile("track.flac", 50L * 1024 * 1024, "album/track.flac")
        )
        val topicId = 777
        val completedTask = DownloadTask(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[0].innerPath),
            fileName = files[0].name,
            size = files[0].size,
            progress = 100,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            contentUri = "content://test",
            torrentTitle = "Album",
            speedBytesPerSec = 0,
            createdAt = 0L
        )

        val result = prepareFilesDl(
            files = files,
            tasks = listOf(completedTask),
            topicId = topicId,
            groupTitle = "Album",
            groupNorm = normalizeCore("Album"),
            autoMatchEnabled = false,
            autoMatchArtist = null,
            autoMatchTitle = null,
            autoMatchMinFuzzy = 0.84
        )

        assertTrue(result.selectableIndices.isEmpty())
        assertTrue(!result.initialChecks.first())
        assertEquals(listOf(0), result.removableCompletedIndices)
    }

    @Test
    fun `completed files are sorted by completion time descending`() {
        val files = listOf(
            torrentFile("old.flac", 50L, "album/old.flac"),
            torrentFile("new.flac", 50L, "album/new.flac")
        )
        val topicId = 778
        val oldTask = DownloadTask(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[0].innerPath),
            fileName = files[0].name,
            size = files[0].size,
            progress = 100,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            contentUri = "content://old",
            torrentTitle = "Album",
            speedBytesPerSec = 0,
            createdAt = 1_000L
        )
        val newTask = oldTask.copy(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[1].innerPath),
            fileName = files[1].name,
            contentUri = "content://new",
            createdAt = 2_000L
        )

        val result = prepareFilesDl(
            files = files,
            tasks = listOf(oldTask, newTask),
            topicId = topicId,
            groupTitle = "Album",
            groupNorm = normalizeCore("Album"),
            autoMatchEnabled = false,
            autoMatchArtist = null,
            autoMatchTitle = null,
            autoMatchMinFuzzy = 0.84
        )

        assertEquals("new.flac", result.ordered.first().file.name)
        assertEquals("old.flac", result.ordered[1].file.name)
    }

    @Test
    fun `completed task is resolved by topic and file identity fallback when taskId path differs`() {
        val files = listOf(
            torrentFile("Track.flac", 50L * 1024 * 1024, "album/path-from-torrent.flac")
        )
        val topicId = 779
        val completedTaskWithDifferentId = DownloadTask(
            id = "${topicId}_deadbeefdeadbeef",
            fileName = "Track.flac",
            size = files[0].size,
            progress = 100,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            contentUri = "content://fallback",
            torrentTitle = "Album",
            speedBytesPerSec = 0,
            createdAt = 3_000L
        )

        val result = prepareFilesDl(
            files = files,
            tasks = listOf(completedTaskWithDifferentId),
            topicId = topicId,
            groupTitle = "Album",
            groupNorm = normalizeCore("Album"),
            autoMatchEnabled = false,
            autoMatchArtist = null,
            autoMatchTitle = null,
            autoMatchMinFuzzy = 0.84
        )

        assertEquals(DownloadStatus.COMPLETED, result.ordered.first().status)
        assertEquals(completedTaskWithDifferentId.id, result.ordered.first().taskId)
        assertEquals(listOf(0), result.removableCompletedIndices)
        assertTrue(result.selectableIndices.isEmpty())
    }

    @Test
    fun `queued tasks expose queue position and transfer metadata`() {
        val files = listOf(
            torrentFile("first.flac", 10L, "album/first.flac"),
            torrentFile("second.flac", 20L, "album/second.flac"),
            torrentFile("third.flac", 30L, "album/third.flac")
        )
        val topicId = 780
        val firstQueued = DownloadTask(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[0].innerPath),
            fileName = files[0].name,
            size = files[0].size,
            progress = 0,
            status = DownloadStatus.QUEUED,
            errorMessage = null,
            contentUri = null,
            torrentTitle = "Album",
            speedBytesPerSec = 0L,
            createdAt = 1_000L
        )
        val secondQueued = DownloadTask(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[1].innerPath),
            fileName = files[1].name,
            size = files[1].size,
            progress = 0,
            status = DownloadStatus.QUEUED,
            errorMessage = null,
            contentUri = null,
            torrentTitle = "Album",
            speedBytesPerSec = 0L,
            createdAt = 2_000L
        )
        val runningWithError = DownloadTask(
            id = buildDownloadTaskId(topicId = topicId, innerPath = files[2].innerPath),
            fileName = files[2].name,
            size = files[2].size,
            progress = 12,
            status = DownloadStatus.RUNNING,
            errorMessage = "peer timeout",
            contentUri = null,
            torrentTitle = "Album",
            speedBytesPerSec = 1234L,
            createdAt = 3_000L
        )

        val result = prepareFilesDl(
            files = files,
            tasks = listOf(firstQueued, secondQueued, runningWithError),
            topicId = topicId,
            groupTitle = "Album",
            groupNorm = normalizeCore("Album"),
            autoMatchEnabled = false,
            autoMatchArtist = null,
            autoMatchTitle = null,
            autoMatchMinFuzzy = 0.84
        )

        val byName = result.ordered.associateBy { it.file.name }
        assertEquals(2, byName["first.flac"]?.queuePosition)
        assertEquals(1, byName["second.flac"]?.queuePosition)
        assertEquals(1234L, byName["third.flac"]?.speedBytesPerSec)
        assertEquals("peer timeout", byName["third.flac"]?.errorMessage)
    }

    private fun torrentFile(name: String, size: Long, innerPath: String) = TorrentFile(
        name = name,
        size = size,
        torrentFilePath = "/tmp/test.torrent",
        innerPath = innerPath
    )
}
