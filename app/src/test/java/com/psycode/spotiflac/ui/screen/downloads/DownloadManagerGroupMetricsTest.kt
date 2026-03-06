package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadManagerGroupMetricsTest {

    @Test
    fun `buildGroupMetrics aggregates progress and speed`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.COMPLETED, progress = 100, speed = 0),
            task("2", size = 100, status = DownloadStatus.RUNNING, progress = 50, speed = 2000),
            task("3", size = 100, status = DownloadStatus.QUEUED, progress = 0, speed = 0)
        )

        val metrics = buildGroupMetrics(tasks)

        assertEquals(3, metrics.totalFiles)
        assertEquals(1, metrics.completedFiles)
        assertEquals(50, metrics.progressPercent)
        assertEquals(2000L, metrics.aggregateSpeedBytes)
        assertEquals(DownloadGroupState.DOWNLOADING, metrics.state)
    }

    @Test
    fun `buildGroupMetrics marks partial when completed and failed mixed`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.COMPLETED, progress = 100, speed = 0),
            task("2", size = 100, status = DownloadStatus.FAILED, progress = 10, speed = 0)
        )

        val metrics = buildGroupMetrics(tasks)

        assertEquals(DownloadGroupState.PARTIAL, metrics.state)
        assertEquals(1, metrics.failedFiles)
        assertEquals(55, metrics.progressPercent)
    }

    @Test
    fun `resolveGroupTransferAction prefers pause when active exists`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.PAUSED, progress = 0, speed = 0),
            task("2", size = 100, status = DownloadStatus.RUNNING, progress = 10, speed = 1)
        )

        assertEquals(GroupTransferAction.PAUSE, resolveGroupTransferAction(tasks))
    }

    @Test
    fun `resolveGroupTransferAction returns resume when only paused present`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.PAUSED, progress = 0, speed = 0),
            task("2", size = 100, status = DownloadStatus.COMPLETED, progress = 100, speed = 0)
        )

        assertEquals(GroupTransferAction.RESUME, resolveGroupTransferAction(tasks))
    }

    @Test
    fun `resolveGroupTransferAction returns resume when only failed present`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.FAILED, progress = 20, speed = 0),
            task("2", size = 100, status = DownloadStatus.COMPLETED, progress = 100, speed = 0)
        )

        assertEquals(GroupTransferAction.RESUME, resolveGroupTransferAction(tasks))
    }

    @Test
    fun `resolveGroupTransferAction returns null when only queued present`() {
        val tasks = listOf(
            task("1", size = 100, status = DownloadStatus.QUEUED, progress = 0, speed = 0),
            task("2", size = 100, status = DownloadStatus.QUEUED, progress = 0, speed = 0)
        )

        assertEquals(null, resolveGroupTransferAction(tasks))
    }

    @Test
    fun `resolveGroupTransferTaskIds targets only relevant statuses`() {
        val tasks = listOf(
            task("run", size = 100, status = DownloadStatus.RUNNING, progress = 10, speed = 1),
            task("queue", size = 100, status = DownloadStatus.QUEUED, progress = 0, speed = 0),
            task("pause", size = 100, status = DownloadStatus.PAUSED, progress = 10, speed = 0),
            task("fail", size = 100, status = DownloadStatus.FAILED, progress = 10, speed = 0),
            task("done", size = 100, status = DownloadStatus.COMPLETED, progress = 100, speed = 0)
        )

        assertEquals(
            listOf("run", "queue"),
            resolveGroupTransferTaskIds(tasks, GroupTransferAction.PAUSE)
        )
        assertEquals(
            listOf("pause", "fail"),
            resolveGroupTransferTaskIds(tasks, GroupTransferAction.RESUME)
        )
    }

    @Test
    fun `buildGroupMetrics marks empty state when only canceled tasks remain`() {
        val tasks = listOf(
            task("anchor", size = 0, status = DownloadStatus.CANCELED, progress = 0, speed = 0)
        )

        val metrics = buildGroupMetrics(tasks)

        assertEquals(0, metrics.totalFiles)
        assertEquals(DownloadGroupState.EMPTY, metrics.state)
    }

    private fun task(
        id: String,
        size: Long,
        status: DownloadStatus,
        progress: Int,
        speed: Long
    ) = DownloadTask(
        id = id,
        fileName = "f$id.flac",
        size = size,
        progress = progress,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "T",
        speedBytesPerSec = speed,
        createdAt = 1L
    )
}
