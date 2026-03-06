package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask

internal enum class DownloadGroupState {
    EMPTY,
    DOWNLOADING,
    QUEUED,
    PAUSED,
    PARTIAL,
    FAILED,
    COMPLETED
}

internal enum class GroupTransferAction {
    PAUSE,
    RESUME
}

internal data class DownloadGroupMetrics(
    val totalFiles: Int,
    val completedFiles: Int,
    val failedFiles: Int,
    val queuedFiles: Int,
    val runningFiles: Int,
    val pausedFiles: Int,
    val progressPercent: Int,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val aggregateSpeedBytes: Long,
    val state: DownloadGroupState
)

internal fun resolveGroupTransferAction(tasks: List<DownloadTask>): GroupTransferAction? {
    val visible = tasks.filter { it.status != DownloadStatus.CANCELED }
    val hasRunning = visible.any { it.status == DownloadStatus.RUNNING }
    if (hasRunning) return GroupTransferAction.PAUSE

    val hasResumable = visible.any {
        it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED
    }
    return if (hasResumable) GroupTransferAction.RESUME else null
}

internal fun resolveGroupTransferTaskIds(
    tasks: List<DownloadTask>,
    action: GroupTransferAction
): List<String> = tasks
    .asSequence()
    .filter { task ->
        when (action) {
            GroupTransferAction.PAUSE ->
                task.status == DownloadStatus.RUNNING || task.status == DownloadStatus.QUEUED
            GroupTransferAction.RESUME ->
                task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED
        }
    }
    .map { it.id }
    .toList()

internal fun buildGroupMetrics(tasks: List<DownloadTask>): DownloadGroupMetrics {
    val visible = tasks.filter { it.status != DownloadStatus.CANCELED }
    val totalFiles = visible.size
    if (totalFiles == 0) {
        return DownloadGroupMetrics(
            totalFiles = 0,
            completedFiles = 0,
            failedFiles = 0,
            queuedFiles = 0,
            runningFiles = 0,
            pausedFiles = 0,
            progressPercent = 0,
            totalBytes = 0L,
            downloadedBytes = 0L,
            aggregateSpeedBytes = 0L,
            state = DownloadGroupState.EMPTY
        )
    }

    val completedFiles = visible.count { it.status == DownloadStatus.COMPLETED }
    val failedFiles = visible.count { it.status == DownloadStatus.FAILED }
    val queuedFiles = visible.count { it.status == DownloadStatus.QUEUED }
    val runningFiles = visible.count { it.status == DownloadStatus.RUNNING }
    val pausedFiles = visible.count { it.status == DownloadStatus.PAUSED }
    val totalBytes = visible.sumOf { it.size.coerceAtLeast(0L) }
    val downloadedBytes = visible.sumOf { task ->
        when (task.status) {
            DownloadStatus.COMPLETED -> task.size.coerceAtLeast(0L)
            else -> ((task.size.coerceAtLeast(0L) * task.progress.coerceIn(0, 100)) / 100L)
        }
    }
    val progressPercent = if (totalBytes <= 0L) {
        ((visible.sumOf { it.progress.coerceIn(0, 100) }) / totalFiles).coerceIn(0, 100)
    } else {
        ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }
    val aggregateSpeed = visible.sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }

    val state = when {
        completedFiles == totalFiles -> DownloadGroupState.COMPLETED
        runningFiles > 0 -> DownloadGroupState.DOWNLOADING
        pausedFiles > 0 -> DownloadGroupState.PAUSED
        failedFiles > 0 && completedFiles == 0 && queuedFiles == 0 -> DownloadGroupState.FAILED
        completedFiles > 0 || failedFiles > 0 -> DownloadGroupState.PARTIAL
        else -> DownloadGroupState.QUEUED
    }

    return DownloadGroupMetrics(
        totalFiles = totalFiles,
        completedFiles = completedFiles,
        failedFiles = failedFiles,
        queuedFiles = queuedFiles,
        runningFiles = runningFiles,
        pausedFiles = pausedFiles,
        progressPercent = progressPercent,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        aggregateSpeedBytes = aggregateSpeed,
        state = state
    )
}
