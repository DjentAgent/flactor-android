package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask

internal data class TaskSections(
    val active: List<DownloadTask>,
    val queued: List<DownloadTask>,
    val paused: List<DownloadTask>,
    val failed: List<DownloadTask>,
    val completed: List<DownloadTask>
)

internal fun partitionTasksForSections(tasks: List<DownloadTask>): TaskSections {
    val visible = tasks.filter { it.status != DownloadStatus.CANCELED }
    val active = visible.filter { it.status == DownloadStatus.RUNNING }
    val queued = visible.filter { it.status == DownloadStatus.QUEUED }
    val paused = visible.filter { it.status == DownloadStatus.PAUSED }
    val failed = visible.filter { it.status == DownloadStatus.FAILED }
    val completed = visible
        .filter { it.status == DownloadStatus.COMPLETED }
        .sortedByDescending { it.createdAt }
    return TaskSections(
        active = active,
        queued = queued,
        paused = paused,
        failed = failed,
        completed = completed
    )
}
