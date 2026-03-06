package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId

data class RemoveCandidate(
    val taskId: String,
    val fileName: String,
    val contentUri: String?
)

fun selectedFilesForSave(
    orderedForSave: List<TorrentFile>,
    selectableForSave: List<Int>,
    checks: List<Boolean>
): List<TorrentFile> =
    orderedForSave.withIndex()
        .filter { (i, _) -> i in selectableForSave && checks.getOrNull(i) == true }
        .map { it.value }

fun removeCandidateForFile(
    topicId: Int?,
    innerPath: String,
    fallbackFileName: String,
    tasks: List<DownloadTask>
): RemoveCandidate? {
    if (topicId == null) return null
    val taskId = buildDownloadTaskId(topicId = topicId, innerPath = innerPath)
    val task = tasks.firstOrNull { it.id == taskId }
    return RemoveCandidate(
        taskId = taskId,
        fileName = task?.fileName ?: fallbackFileName,
        contentUri = task?.contentUri
    )
}

fun removeCandidatesForCheckedCompleted(
    prep: PreparedFilesDl,
    checks: List<Boolean>,
    candidateIndices: List<Int> = prep.removableCompletedIndices
): List<RemoveCandidate> =
    candidateIndices
        .filter { idx -> checks.getOrNull(idx) == true }
        .mapNotNull { idx ->
            val ui = prep.ordered.getOrNull(idx) ?: return@mapNotNull null
            val taskId = ui.taskId ?: return@mapNotNull null
            RemoveCandidate(
                taskId = taskId,
                fileName = ui.file.name,
                contentUri = ui.contentUri
            )
        }
        .distinctBy { it.taskId }
