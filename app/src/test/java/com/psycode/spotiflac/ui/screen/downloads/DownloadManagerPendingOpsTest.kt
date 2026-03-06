package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadManagerPendingOpsTest {

    @Test
    fun `reconcile removes operations for missing tasks`() {
        val current = mapOf("a" to PendingOp.PAUSING, "b" to PendingOp.CANCELING)
        val tasks = listOf(task("a", DownloadStatus.RUNNING))

        val result = reconcilePendingOps(current, tasks)

        assertEquals(mapOf("a" to PendingOp.PAUSING), result)
    }

    @Test
    fun `reconcile removes pausing when task is paused`() {
        val current = mapOf("a" to PendingOp.PAUSING)
        val tasks = listOf(task("a", DownloadStatus.PAUSED))

        val result = reconcilePendingOps(current, tasks)

        assertEquals(emptyMap<String, PendingOp>(), result)
    }

    @Test
    fun `reconcile removes pausing when task moved to terminal state`() {
        val current = mapOf("a" to PendingOp.PAUSING, "b" to PendingOp.PAUSING)
        val tasks = listOf(
            task("a", DownloadStatus.FAILED),
            task("b", DownloadStatus.COMPLETED)
        )

        val result = reconcilePendingOps(current, tasks)

        assertEquals(emptyMap<String, PendingOp>(), result)
    }

    @Test
    fun `reconcile removes resuming when task reached queued or running`() {
        val current = mapOf("a" to PendingOp.RESUMING, "b" to PendingOp.RESUMING)
        val tasks = listOf(
            task("a", DownloadStatus.QUEUED),
            task("b", DownloadStatus.RUNNING)
        )

        val result = reconcilePendingOps(current, tasks)

        assertEquals(emptyMap<String, PendingOp>(), result)
    }

    @Test
    fun `reconcile removes canceling when task canceled`() {
        val current = mapOf("a" to PendingOp.CANCELING, "b" to PendingOp.CANCELING)
        val tasks = listOf(
            task("a", DownloadStatus.CANCELED),
            task("b", DownloadStatus.RUNNING)
        )

        val result = reconcilePendingOps(current, tasks)

        assertEquals(mapOf("b" to PendingOp.CANCELING), result)
    }

    @Test
    fun `reconcile keeps unresolved operations`() {
        val current = mapOf(
            "a" to PendingOp.PAUSING,
            "b" to PendingOp.RESUMING,
            "c" to PendingOp.CANCELING
        )
        val tasks = listOf(
            task("a", DownloadStatus.RUNNING),
            task("b", DownloadStatus.PAUSED),
            task("c", DownloadStatus.QUEUED)
        )

        val result = reconcilePendingOps(current, tasks)

        assertEquals(current, result)
    }

    @Test
    fun `reconcile removes pausing when task is queued`() {
        val current = mapOf("a" to PendingOp.PAUSING)
        val tasks = listOf(task("a", DownloadStatus.QUEUED))

        val result = reconcilePendingOps(current, tasks)

        assertEquals(emptyMap<String, PendingOp>(), result)
    }

    @Test
    fun `reconcile keeps resuming while failed task waits for retry resume`() {
        val current = mapOf("a" to PendingOp.RESUMING)
        val tasks = listOf(task("a", DownloadStatus.FAILED))

        val result = reconcilePendingOps(current, tasks)

        assertEquals(current, result)
    }

    private fun task(id: String, status: DownloadStatus): DownloadTask = DownloadTask(
        id = id,
        fileName = "track.flac",
        size = 123L,
        progress = 50,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "Album",
        speedBytesPerSec = 0L,
        createdAt = 1L
    )
}
