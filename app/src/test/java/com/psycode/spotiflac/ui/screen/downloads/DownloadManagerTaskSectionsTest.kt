package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadManagerTaskSectionsTest {

    @Test
    fun `partition puts paused tasks into paused section and queued remains only queued`() {
        val tasks = listOf(
            task("run", DownloadStatus.RUNNING, progress = 37, createdAt = 1),
            task("p0", DownloadStatus.PAUSED, progress = 0, createdAt = 2),
            task("p80", DownloadStatus.PAUSED, progress = 80, createdAt = 3),
            task("q", DownloadStatus.QUEUED, progress = 0, createdAt = 4)
        )

        val sections = partitionTasksForSections(tasks)

        assertEquals(listOf("run"), sections.active.map { it.id })
        assertEquals(listOf("q"), sections.queued.map { it.id })
        assertEquals(listOf("p0", "p80"), sections.paused.map { it.id })
    }

    @Test
    fun `partition excludes canceled and sorts completed by recency`() {
        val tasks = listOf(
            task("done_old", DownloadStatus.COMPLETED, progress = 100, createdAt = 1),
            task("done_new", DownloadStatus.COMPLETED, progress = 100, createdAt = 10),
            task("cancel", DownloadStatus.CANCELED, progress = 0, createdAt = 20)
        )

        val sections = partitionTasksForSections(tasks)

        assertEquals(emptyList<String>(), sections.active.map { it.id })
        assertEquals(emptyList<String>(), sections.queued.map { it.id })
        assertEquals(emptyList<String>(), sections.paused.map { it.id })
        assertEquals(emptyList<String>(), sections.failed.map { it.id })
        assertEquals(listOf("done_new", "done_old"), sections.completed.map { it.id })
    }

    private fun task(
        id: String,
        status: DownloadStatus,
        progress: Int,
        createdAt: Long
    ): DownloadTask = DownloadTask(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = progress,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "Album",
        speedBytesPerSec = 0L,
        createdAt = createdAt
    )
}
