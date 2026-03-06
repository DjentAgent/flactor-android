package com.psycode.spotiflac.data.service

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.orchestration.selectTasksToSchedule
import com.psycode.spotiflac.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadOrchestratorSelectionTest {

    @Test
    fun `selectTasksToSchedule picks queued only within free slots`() {
        val snapshot = listOf(
            entity("q1", DownloadStatus.QUEUED, createdAt = 1),
            entity("q2", DownloadStatus.QUEUED, createdAt = 2),
            entity("q3", DownloadStatus.QUEUED, createdAt = 3),
            entity("q4", DownloadStatus.QUEUED, createdAt = 4)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = setOf("already-1", "already-2"),
            maxParallelDownloads = 3,
            reservedPausedTaskIds = emptySet()
        )

        assertEquals(listOf("q4"), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule always includes unscheduled running tasks`() {
        val snapshot = listOf(
            entity("run", DownloadStatus.RUNNING, createdAt = 1),
            entity("q1", DownloadStatus.QUEUED, createdAt = 10),
            entity("q2", DownloadStatus.QUEUED, createdAt = 11)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 1,
            reservedPausedTaskIds = emptySet()
        )

        assertEquals(listOf("run"), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule prioritizes most recently resumed queued task`() {
        val snapshot = listOf(
            entity("old", DownloadStatus.QUEUED, createdAt = 100),
            entity("new", DownloadStatus.QUEUED, createdAt = 200)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 1,
            reservedPausedTaskIds = emptySet()
        )

        assertEquals(listOf("new"), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule respects paused slot reservation`() {
        val snapshot = listOf(
            entity("paused", DownloadStatus.PAUSED, createdAt = 10),
            entity("q1", DownloadStatus.QUEUED, createdAt = 20),
            entity("q2", DownloadStatus.QUEUED, createdAt = 30)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 1,
            reservedPausedTaskIds = setOf("paused")
        )

        assertEquals(emptyList<String>(), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule ignores reservation when task is no longer paused`() {
        val snapshot = listOf(
            entity("resumed", DownloadStatus.QUEUED, createdAt = 50),
            entity("q2", DownloadStatus.QUEUED, createdAt = 40)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 1,
            reservedPausedTaskIds = setOf("resumed")
        )

        assertEquals(listOf("resumed"), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule accounts multiple paused reservations`() {
        val snapshot = listOf(
            entity("p1", DownloadStatus.PAUSED, createdAt = 1),
            entity("p2", DownloadStatus.PAUSED, createdAt = 2),
            entity("q1", DownloadStatus.QUEUED, createdAt = 10),
            entity("q2", DownloadStatus.QUEUED, createdAt = 20)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 2,
            reservedPausedTaskIds = setOf("p1", "p2")
        )

        assertEquals(emptyList<String>(), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule allows multiple tasks from same torrent up to per torrent limit`() {
        val torrentPath = "/tmp/shared.torrent"
        val snapshot = listOf(
            entity("q1", DownloadStatus.QUEUED, createdAt = 40, torrentFilePath = torrentPath),
            entity("q2", DownloadStatus.QUEUED, createdAt = 30, torrentFilePath = torrentPath),
            entity("q3", DownloadStatus.QUEUED, createdAt = 20, torrentFilePath = torrentPath)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 3,
            maxParallelPerTorrent = 2,
            reservedPausedTaskIds = emptySet()
        )

        assertEquals(listOf("q1", "q2"), selected.map { it.id })
    }

    @Test
    fun `selectTasksToSchedule includes already scheduled same torrent when counting per torrent cap`() {
        val torrentPath = "/tmp/shared.torrent"
        val snapshot = listOf(
            entity("run", DownloadStatus.RUNNING, createdAt = 50, torrentFilePath = torrentPath),
            entity("q1", DownloadStatus.QUEUED, createdAt = 40, torrentFilePath = torrentPath),
            entity("q2", DownloadStatus.QUEUED, createdAt = 30, torrentFilePath = torrentPath)
        )

        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = setOf("run"),
            maxParallelDownloads = 3,
            maxParallelPerTorrent = 2,
            reservedPausedTaskIds = emptySet()
        )

        assertEquals(listOf("q1"), selected.map { it.id })
    }

    private fun entity(
        id: String,
        status: DownloadStatus,
        createdAt: Long,
        torrentFilePath: String = "/tmp/file.torrent"
    ) = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 0,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "torrent",
        torrentFilePath = torrentFilePath,
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = 0L,
        createdAt = createdAt
    )
}
