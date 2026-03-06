package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.orchestration.reconcileOrphanRunningSnapshot
import com.psycode.spotiflac.data.service.download.orchestration.reconcileRestartSnapshot
import com.psycode.spotiflac.domain.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DownloadBootstrapperRecoveryTest {

    @Test
    fun `reconcileRestartSnapshot converts running tasks to queued and resets volatile fields`() {
        val snapshot = listOf(
            entity(
                id = "run",
                status = DownloadStatus.RUNNING,
                speedBytesPerSec = 1_234_567L,
                error = "stale"
            ),
            entity(
                id = "paused",
                status = DownloadStatus.PAUSED,
                speedBytesPerSec = 222L,
                error = null
            ),
            entity(
                id = "completed",
                status = DownloadStatus.COMPLETED,
                speedBytesPerSec = 0L,
                error = null
            )
        )

        val reconciled = reconcileRestartSnapshot(snapshot).associateBy { it.id }

        val run = reconciled.getValue("run")
        assertEquals(DownloadStatus.QUEUED, run.status)
        assertEquals(0L, run.speedBytesPerSec)
        assertNull(run.errorMessage)

        val paused = reconciled.getValue("paused")
        assertEquals(DownloadStatus.PAUSED, paused.status)
        assertEquals(0L, paused.speedBytesPerSec)

        val completed = reconciled.getValue("completed")
        assertEquals(DownloadStatus.COMPLETED, completed.status)
    }

    @Test
    fun `reconcileOrphanRunningSnapshot converts running to queued when runtime has no running tasks`() {
        val snapshot = listOf(
            entity(id = "run", status = DownloadStatus.RUNNING, speedBytesPerSec = 777L, error = "stale"),
            entity(id = "queued", status = DownloadStatus.QUEUED, speedBytesPerSec = 0L, error = null)
        )

        val reconciled = reconcileOrphanRunningSnapshot(
            snapshot = snapshot,
            hasRuntimeRunningTasks = false
        ).associateBy { it.id }

        val run = reconciled.getValue("run")
        assertEquals(DownloadStatus.QUEUED, run.status)
        assertEquals(0L, run.speedBytesPerSec)
        assertNull(run.errorMessage)
        assertEquals(DownloadStatus.QUEUED, reconciled.getValue("queued").status)
    }

    @Test
    fun `reconcileOrphanRunningSnapshot keeps snapshot untouched when runtime has running tasks`() {
        val snapshot = listOf(
            entity(id = "run", status = DownloadStatus.RUNNING, speedBytesPerSec = 777L, error = null)
        )

        val reconciled = reconcileOrphanRunningSnapshot(
            snapshot = snapshot,
            hasRuntimeRunningTasks = true
        )

        assertSame(snapshot, reconciled)
    }

    private fun entity(
        id: String,
        status: DownloadStatus,
        speedBytesPerSec: Long,
        error: String?
    ) = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 50,
        status = status,
        errorMessage = error,
        contentUri = null,
        torrentTitle = "torrent",
        torrentFilePath = "/tmp/file.torrent",
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = speedBytesPerSec,
        createdAt = 1L
    )
}
