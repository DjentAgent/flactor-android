package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask

internal enum class PendingOp { PAUSING, RESUMING, CANCELING }

internal fun reconcilePendingOps(
    current: Map<String, PendingOp>,
    tasks: List<DownloadTask>
): Map<String, PendingOp> {
    if (current.isEmpty()) return emptyMap()
    if (tasks.isEmpty()) return emptyMap()

    val taskById = tasks.associateBy { it.id }
    return buildMap {
        current.forEach { (taskId, op) ->
            val task = taskById[taskId] ?: return@forEach
            val shouldKeep = when (op) {
                PendingOp.PAUSING ->
                    task.status == DownloadStatus.RUNNING
                PendingOp.RESUMING ->
                    task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED
                PendingOp.CANCELING -> task.status != DownloadStatus.CANCELED
            }
            if (shouldKeep) put(taskId, op)
        }
    }
}
