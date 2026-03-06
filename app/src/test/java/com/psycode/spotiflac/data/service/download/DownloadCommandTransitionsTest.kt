package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.orchestration.applyCancelTransition
import com.psycode.spotiflac.data.service.download.orchestration.applyPauseTransition
import com.psycode.spotiflac.data.service.download.orchestration.applyResumeTransition
import com.psycode.spotiflac.data.service.download.orchestration.selectTasksToSchedule
import com.psycode.spotiflac.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCommandTransitionsTest {

    @Test
    fun `pause transition allowed only for running or queued`() {
        val running = applyPauseTransition(entity("r", DownloadStatus.RUNNING))
        val queued = applyPauseTransition(entity("q", DownloadStatus.QUEUED))
        val paused = applyPauseTransition(entity("p", DownloadStatus.PAUSED))
        val failed = applyPauseTransition(entity("f", DownloadStatus.FAILED))

        assertEquals(DownloadStatus.PAUSED, running?.status)
        assertEquals(DownloadStatus.PAUSED, queued?.status)
        assertNull(paused)
        assertNull(failed)
    }

    @Test
    fun `resume transition from paused goes to queued and refreshes timestamp`() {
        val now = 10_000L
        val source = entity("p", DownloadStatus.PAUSED, createdAt = 1L, error = "x")

        val resumed = applyResumeTransition(source, nowMs = now)

        assertNotNull(resumed)
        assertEquals(DownloadStatus.QUEUED, resumed?.status)
        assertEquals(0L, resumed?.speedBytesPerSec)
        assertEquals(now, resumed?.createdAt)
        assertEquals("x", resumed?.errorMessage)
    }

    @Test
    fun `resume transition from failed clears error and queues`() {
        val now = 20_000L
        val source = entity("f", DownloadStatus.FAILED, createdAt = 2L, error = "network")

        val resumed = applyResumeTransition(source, nowMs = now)

        assertNotNull(resumed)
        assertEquals(DownloadStatus.QUEUED, resumed?.status)
        assertEquals(0L, resumed?.speedBytesPerSec)
        assertEquals(now, resumed?.createdAt)
        assertEquals(null, resumed?.errorMessage)
    }

    @Test
    fun `resume transition rejects non paused and non failed statuses`() {
        val running = applyResumeTransition(entity("r", DownloadStatus.RUNNING), nowMs = 1L)
        val queued = applyResumeTransition(entity("q", DownloadStatus.QUEUED), nowMs = 1L)
        val completed = applyResumeTransition(entity("c", DownloadStatus.COMPLETED), nowMs = 1L)

        assertNull(running)
        assertNull(queued)
        assertNull(completed)
    }

    @Test
    fun `cancel transition always produces canceled with zero speed`() {
        DownloadStatus.values().forEach { status ->
            val canceled = applyCancelTransition(entity("x_$status", status, speed = 999L))
            assertEquals(DownloadStatus.CANCELED, canceled.status)
            assertEquals(0L, canceled.speedBytesPerSec)
        }
    }

    @Test
    fun `resumed queued task is considered by scheduler without affecting running peer`() {
        val running = entity("running", DownloadStatus.RUNNING, createdAt = 100L)
        val resumed = applyResumeTransition(
            current = entity("paused", DownloadStatus.PAUSED, createdAt = 10L),
            nowMs = 1_000L
        )
        assertNotNull(resumed)

        val selected = selectTasksToSchedule(
            snapshot = listOf(running, resumed!!),
            scheduledTaskIds = setOf("running"),
            maxParallelDownloads = 2,
            reservedPausedTaskIds = emptySet()
        )

        assertTrue(selected.any { it.id == "paused" })
        assertTrue(selected.none { it.id == "running" })
    }

    private fun entity(
        id: String,
        status: DownloadStatus,
        createdAt: Long = 1L,
        speed: Long = 123L,
        error: String? = null
    ): DownloadEntity = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 30,
        status = status,
        errorMessage = error,
        contentUri = null,
        torrentTitle = "Torrent",
        torrentFilePath = "/tmp/$id.torrent",
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = speed,
        createdAt = createdAt
    )
}
