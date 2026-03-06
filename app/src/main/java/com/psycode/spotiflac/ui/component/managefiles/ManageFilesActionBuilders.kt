package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile

data class QueueSelectionAction(
    val files: List<TorrentFile>,
    val saveOption: SaveOption,
    val customUri: String?
)

data class RemoveCompletedAction(
    val taskId: String,
    val alsoDeleteLocal: Boolean,
    val contentUri: String?
)

fun buildQueueSelectionAction(
    orderedForSave: List<TorrentFile>,
    selectableForSave: List<Int>,
    checks: List<Boolean>,
    saveOption: SaveOption,
    customUri: String?
): QueueSelectionAction? {
    val selected = selectedFilesForSave(
        orderedForSave = orderedForSave,
        selectableForSave = selectableForSave,
        checks = checks
    )
    if (selected.isEmpty()) return null
    return QueueSelectionAction(
        files = selected,
        saveOption = saveOption,
        customUri = customUri
    )
}

fun buildRemoveCompletedAction(
    candidate: RemoveCandidate,
    alsoDeleteLocal: Boolean
): RemoveCompletedAction = RemoveCompletedAction(
    taskId = candidate.taskId,
    alsoDeleteLocal = alsoDeleteLocal,
    contentUri = candidate.contentUri
)
