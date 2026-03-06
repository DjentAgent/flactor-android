package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadManagerUiUtilsTest {

    @Test
    fun `resolveFileOpMessage returns success resource message`() {
        val op = DownloadManagerViewModel.FileOpUiState.Success(R.string.file_delisted)
        val message = resolveFileOpMessage(op) { resId -> "res:$resId" }
        assertEquals("res:${R.string.file_delisted}", message)
    }

    @Test
    fun `resolveFileOpMessage returns explicit error message when present`() {
        val op = DownloadManagerViewModel.FileOpUiState.Error(
            message = "Network failed",
            fallbackResId = R.string.could_not_delist
        )
        val message = resolveFileOpMessage(op) { "unused" }
        assertEquals("Network failed", message)
    }

    @Test
    fun `resolveFileOpMessage returns fallback resource when error message missing`() {
        val op = DownloadManagerViewModel.FileOpUiState.Error(
            message = null,
            fallbackResId = R.string.could_not_delist
        )
        val message = resolveFileOpMessage(op) { resId -> "res:$resId" }
        assertEquals("res:${R.string.could_not_delist}", message)
    }

    @Test
    fun `resolveFileOpMessage returns null for idle and running`() {
        assertNull(resolveFileOpMessage(DownloadManagerViewModel.FileOpUiState.Idle) { "x" })
        assertNull(resolveFileOpMessage(DownloadManagerViewModel.FileOpUiState.Running) { "x" })
    }

    @Test
    fun `toggleCollapsedDate adds key when absent`() {
        val result = toggleCollapsedDate(
            collapsedDates = setOf("2026-02-07"),
            dateKey = "2026-02-08"
        )
        assertEquals(setOf("2026-02-07", "2026-02-08"), result)
    }

    @Test
    fun `toggleCollapsedDate removes key when present`() {
        val result = toggleCollapsedDate(
            collapsedDates = setOf("2026-02-07", "2026-02-08"),
            dateKey = "2026-02-08"
        )
        assertEquals(setOf("2026-02-07"), result)
    }

    @Test
    fun `buildDateHeaderItemKey uses stable prefix`() {
        assertEquals("date-header-2026-02-08", buildDateHeaderItemKey("2026-02-08"))
    }

    @Test
    fun `buildGroupItemKey uses stable concatenation`() {
        assertEquals(
            "group-2026-02-08-Album Title",
            buildGroupItemKey(dateKey = "2026-02-08", groupTitle = "Album Title")
        )
    }

    @Test
    fun `countActiveRunningDownloads counts only running statuses`() {
        val tasks = listOf(
            task("a", DownloadStatus.RUNNING),
            task("b", DownloadStatus.QUEUED),
            task("c", DownloadStatus.RUNNING),
            task("d", DownloadStatus.PAUSED)
        )
        assertEquals(2, countActiveRunningDownloads(tasks))
    }

    @Test
    fun `shouldEnsureDownloadService is true for queued or running when not ensured yet`() {
        val tasks = listOf(
            task("a", DownloadStatus.PAUSED),
            task("b", DownloadStatus.QUEUED)
        )
        assertTrue(shouldEnsureDownloadService(tasks, alreadyEnsuredInSession = false))
    }

    @Test
    fun `shouldEnsureDownloadService is false when already ensured in session`() {
        val tasks = listOf(task("a", DownloadStatus.RUNNING))
        assertFalse(shouldEnsureDownloadService(tasks, alreadyEnsuredInSession = true))
    }

    @Test
    fun `shouldEnsureDownloadService is false without active queued or running tasks`() {
        val tasks = listOf(
            task("a", DownloadStatus.PAUSED),
            task("b", DownloadStatus.COMPLETED),
            task("c", DownloadStatus.FAILED)
        )
        assertFalse(shouldEnsureDownloadService(tasks, alreadyEnsuredInSession = false))
    }

    @Test
    fun `estimateRemainingSeconds returns null for invalid inputs`() {
        assertNull(estimateRemainingSeconds(totalBytes = 0, downloadedBytes = 0, speedBytesPerSec = 1))
        assertNull(estimateRemainingSeconds(totalBytes = 10, downloadedBytes = 5, speedBytesPerSec = 0))
    }

    @Test
    fun `estimateRemainingSeconds returns rounded up seconds`() {
        assertEquals(
            6L,
            estimateRemainingSeconds(totalBytes = 1000, downloadedBytes = 500, speedBytesPerSec = 90)
        )
    }

    @Test
    fun `formatEtaCompact formats hours minutes and seconds`() {
        assertEquals("1h 1m", formatEtaCompact(3661))
        assertEquals("12m", formatEtaCompact(721))
        assertEquals("7s", formatEtaCompact(7))
    }

    private fun task(id: String, status: DownloadStatus): DownloadTask = DownloadTask(
        id = id,
        fileName = "$id.flac",
        size = 1L,
        progress = 0,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "Album",
        speedBytesPerSec = 0L,
        createdAt = 1L
    )
}
