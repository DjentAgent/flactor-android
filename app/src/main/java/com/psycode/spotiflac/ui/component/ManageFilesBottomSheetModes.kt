package com.psycode.spotiflac.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesExplorerRow
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesExplorerTree
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilter
import com.psycode.spotiflac.ui.component.managefiles.PreparedFilesDl
import com.psycode.spotiflac.ui.component.managefiles.RemoveCandidate
import com.psycode.spotiflac.ui.component.managefiles.UiFileDl
import com.psycode.spotiflac.ui.component.managefiles.buildVisibleExplorerRows
import com.psycode.spotiflac.ui.component.managefiles.parentDirectoryLabel
import com.psycode.spotiflac.ui.component.managefiles.toggleFileSelection

@Composable
fun ManageFilesListModeContent(
    modifier: Modifier = Modifier,
    prep: PreparedFilesDl,
    activeFilter: ManageFilesFilter,
    sortOption: ManageFilesSortOption,
    queryNorm: String,
    listModeState: LazyListState,
    checks: List<Boolean>,
    selectableIndices: Set<Int>,
    onChecksChange: (List<Boolean>) -> Unit,
    fileMatches: (UiFileDl) -> Boolean,
    onLog: (String) -> Unit,
    onRemoveCandidate: (RemoveCandidate) -> Unit,
    onRetryDownload: (Int) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onAlsoDeleteReset: () -> Unit
) {
    val filtered = remember(prep, queryNorm, fileMatches) {
        prep.ordered.filter { fileMatches(it) }
    }
    val shown = remember(filtered, prep.matchedIndices, prep.indexByPath) {
        if (prep.matchedIndices.isEmpty()) filtered
        else {
            val (matches, rest) = filtered.partition { ui ->
                val idx = prep.indexByPath[ui.file.innerPath] ?: -1
                idx in prep.matchedIndices
            }
            matches + rest
        }
    }
    val sortedShown = remember(shown, sortOption) {
        when (sortOption) {
            ManageFilesSortOption.RELEVANCE -> shown
            ManageFilesSortOption.SIZE -> shown.sortedByDescending { it.size }
            ManageFilesSortOption.NAME -> shown.sortedBy { it.file.name.lowercase() }
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        state = listModeState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            sortedShown,
            key = { _, ui -> ui.file.innerPath },
            contentType = { _, _ -> "file" }
        ) { _, ui ->
            val idx = prep.indexByPath[ui.file.innerPath] ?: -1
            val disabled = idx !in selectableIndices
            val checked = if (idx >= 0 && idx in selectableIndices) {
                checks.getOrNull(idx) ?: false
            } else {
                false
            }

            FileCardRow(
                ui = ui,
                idx = idx,
                checked = checked,
                disabled = disabled,
                preserveActiveToneWhenDisabled = disabled && ui.taskId != null && ui.status in setOf(
                    DownloadStatus.QUEUED,
                    DownloadStatus.RUNNING,
                    DownloadStatus.PAUSED,
                    DownloadStatus.FAILED
                ),
                baseCard = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                selectedCard = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                outerIndent = 0.dp,
                onToggle = { c ->
                    if (!disabled && idx >= 0) {
                        val updated = toggleFileSelection(checks = checks, index = idx, toChecked = c)
                        if (updated !== checks) {
                            onChecksChange(updated)
                            onLog("Toggle file idx=$idx -> $c; selected=${updated.count { it }}")
                        }
                    }
                },
                parentDir = parentDirectoryLabel(ui.file.innerPath),
                statusHint = statusHintText(ui),
                statusHintColor = statusHintColor(ui),
                statusProgress = statusProgressFraction(ui),
                statusActions = {
                    ManageFilesFileStatusActions(
                        activeFilter = activeFilter,
                        ui = ui,
                        onRemoveCandidate = onRemoveCandidate,
                        onRetryDownload = { onRetryDownload(idx) },
                        onPauseDownload = onPauseDownload,
                        onResumeDownload = onResumeDownload,
                        onCancelDownload = onCancelDownload,
                        onAlsoDeleteReset = onAlsoDeleteReset
                    )
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ManageFilesExplorerModeContent(
    modifier: Modifier = Modifier,
    prep: PreparedFilesDl,
    activeFilter: ManageFilesFilter,
    tree: ManageFilesExplorerTree,
    expandedDirs: Set<String>,
    onExpandedDirsChange: (Set<String>) -> Unit,
    queryNorm: String,
    explorerModeState: LazyListState,
    checks: List<Boolean>,
    selectableIndices: Set<Int>,
    onChecksChange: (List<Boolean>) -> Unit,
    fileMatches: (UiFileDl) -> Boolean,
    allDescendantIndices: (String) -> List<Int>,
    folderToggleState: (String) -> Pair<ToggleableState, Boolean>,
    applyFolderSelection: (String, Boolean) -> Unit,
    onLog: (String) -> Unit,
    onRemoveCandidate: (RemoveCandidate) -> Unit,
    onRetryDownload: (Int) -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onAlsoDeleteReset: () -> Unit
) {
    val rows = remember(prep, tree, expandedDirs, queryNorm, fileMatches) {
        buildVisibleExplorerRows(
            tree = tree,
            prep = prep,
            expandedDirs = expandedDirs,
            queryNorm = queryNorm,
            fileMatchesPredicate = fileMatches
        )
    }

    androidx.compose.foundation.lazy.LazyColumn(
        state = explorerModeState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(rows, key = { _, row ->
            when (row) {
                is ManageFilesExplorerRow.DirRow -> "dir:${row.node.key}"
                is ManageFilesExplorerRow.FileRow -> "file:${row.index}"
            }
        }) { _, row ->
            when (row) {
                is ManageFilesExplorerRow.DirRow -> {
                    val node = row.node
                    val (state, enabled) = folderToggleState(node.key)
                    val allIndices = allDescendantIndices(node.key)
                    val selectableInDir = allIndices.filter { it in selectableIndices }
                    val disabledIndices = allIndices.filter { it !in selectableIndices }
                    val selectedCount = selectableInDir.count { checks.getOrNull(it) == true }
                    val downloadedCount = disabledIndices.count { idx ->
                        prep.ordered.getOrNull(idx)?.status == DownloadStatus.COMPLETED
                    }
                    val totalSelectedOrDownloaded = selectedCount + downloadedCount
                    val totalFiles = allIndices.size
                    val indentCard = (node.depth * 14).dp

                    Card(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(MF_RADIUS_ITEM),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indentCard)
                            .combinedClickable(
                                onClick = {
                                    if (queryNorm.isBlank()) {
                                        val updated = if (expandedDirs.contains(node.key)) {
                                            expandedDirs - node.key
                                        } else {
                                            expandedDirs + node.key
                                        }
                                        onExpandedDirsChange(updated)
                                        onLog("Dir expand toggle '${node.key}' -> expanded=${updated.contains(node.key)}")
                                    }
                                },
                                onLongClick = {
                                    val toChecked = when (state) {
                                        ToggleableState.On -> false
                                        ToggleableState.Off -> true
                                        ToggleableState.Indeterminate -> true
                                    }
                                    if (enabled) applyFolderSelection(node.key, toChecked)
                                },
                                role = Role.Button
                            )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (queryNorm.isNotBlank() || expandedDirs.contains(node.key))
                                    Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp).padding(end = 4.dp)
                            )
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            TriStateCheckbox(
                                state = state,
                                onClick = {
                                    val toChecked = when (state) {
                                        ToggleableState.On -> false
                                        ToggleableState.Off -> true
                                        ToggleableState.Indeterminate -> true
                                    }
                                    if (enabled) applyFolderSelection(node.key, toChecked)
                                },
                                enabled = enabled
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (node.key.isEmpty()) "/" else node.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val info = when {
                                    totalFiles == 0 -> androidx.compose.ui.res.stringResource(R.string.no_available_files)
                                    selectableInDir.isEmpty() && downloadedCount > 0 ->
                                        androidx.compose.ui.res.stringResource(
                                            R.string.all_downloaded_ratio,
                                            downloadedCount,
                                            totalFiles
                                        )
                                    else -> "$totalSelectedOrDownloaded / $totalFiles"
                                }
                                Text(
                                    info,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                is ManageFilesExplorerRow.FileRow -> {
                    val ui = row.ui
                    val idx = row.index
                    val disabled = idx !in selectableIndices
                    val checked = idx in selectableIndices && checks.getOrNull(idx) == true

                    FileCardRow(
                        ui = ui,
                        idx = idx,
                        checked = checked,
                        disabled = disabled,
                        preserveActiveToneWhenDisabled = disabled && ui.taskId != null && ui.status in setOf(
                            DownloadStatus.QUEUED,
                            DownloadStatus.RUNNING,
                            DownloadStatus.PAUSED,
                            DownloadStatus.FAILED
                        ),
                        baseCard = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        selectedCard = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        outerIndent = (row.depth * 14).dp,
                        onToggle = { c ->
                            if (!disabled && idx >= 0) {
                                val updated = toggleFileSelection(checks = checks, index = idx, toChecked = c)
                                if (updated !== checks) {
                                    onChecksChange(updated)
                                    onLog("Toggle file idx=$idx -> $c; selected=${updated.count { it }}")
                                }
                            }
                        },
                        parentDir = "",
                        statusHint = statusHintText(ui),
                        statusHintColor = statusHintColor(ui),
                        statusProgress = statusProgressFraction(ui),
                        statusActions = {
                            ManageFilesFileStatusActions(
                                activeFilter = activeFilter,
                                ui = ui,
                                onRemoveCandidate = onRemoveCandidate,
                                onRetryDownload = { onRetryDownload(idx) },
                                onPauseDownload = onPauseDownload,
                                onResumeDownload = onResumeDownload,
                                onCancelDownload = onCancelDownload,
                                onAlsoDeleteReset = onAlsoDeleteReset
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun statusHintText(ui: UiFileDl): String? = when (ui.status) {
    DownloadStatus.QUEUED -> {
        ui.queuePosition?.let { position ->
            androidx.compose.ui.res.stringResource(R.string.task_queue_position, position)
        } ?: androidx.compose.ui.res.stringResource(R.string.in_queue)
    }
    DownloadStatus.RUNNING -> ui.progress?.let { progress ->
        if (ui.speedBytesPerSec > 0L) {
            "${androidx.compose.ui.res.stringResource(R.string.in_progresss)} $progress%"
        } else if (progress > 0) {
            "${androidx.compose.ui.res.stringResource(R.string.task_waiting_peers_status)} $progress%"
        } else {
            androidx.compose.ui.res.stringResource(R.string.task_starting_status)
        }
    } ?: androidx.compose.ui.res.stringResource(R.string.in_progresss)
    DownloadStatus.PAUSED -> androidx.compose.ui.res.stringResource(R.string.task_paused_resumable_status)
    DownloadStatus.FAILED -> ui.errorMessage ?: androidx.compose.ui.res.stringResource(R.string.in_error)
    DownloadStatus.CANCELED -> ui.errorMessage ?: androidx.compose.ui.res.stringResource(R.string.in_cancel)
    DownloadStatus.COMPLETED -> androidx.compose.ui.res.stringResource(R.string.completed_title)
    else -> null
}

@Composable
private fun statusHintColor(ui: UiFileDl): Color = when (ui.status) {
    DownloadStatus.FAILED,
    DownloadStatus.CANCELED -> MaterialTheme.colorScheme.error
    DownloadStatus.RUNNING -> MaterialTheme.colorScheme.primary
    DownloadStatus.PAUSED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusProgressFraction(ui: UiFileDl): Float? {
    if (ui.status != DownloadStatus.RUNNING) return null
    val progress = ui.progress ?: return null
    return (progress.coerceIn(0, 100) / 100f)
}

@Composable
private fun ManageFilesFileStatusActions(
    activeFilter: ManageFilesFilter,
    ui: UiFileDl,
    onRemoveCandidate: (RemoveCandidate) -> Unit,
    onRetryDownload: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onAlsoDeleteReset: () -> Unit
) {
    val actionSize = 36.dp
    val actionShape = androidx.compose.foundation.shape.RoundedCornerShape(MF_RADIUS_ITEM)
    val actionBorder = BorderStroke(
        0.6.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
    )
    val actionContainer = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val taskId = ui.taskId
    when (ui.status) {
        DownloadStatus.COMPLETED -> {
            val candidate = ui.taskId?.let { taskId ->
                RemoveCandidate(
                    taskId = taskId,
                    fileName = ui.file.name,
                    contentUri = ui.contentUri
                )
            }
            OutlinedIconButton(
                onClick = {
                    if (candidate != null) {
                        onRemoveCandidate(candidate)
                        onAlsoDeleteReset()
                    }
                },
                enabled = candidate != null,
                modifier = Modifier.size(actionSize),
                shape = actionShape,
                border = actionBorder,
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = actionContainer,
                    contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                    disabledContainerColor = actionContainer
                )
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.manage_action_remove_list)
                )
            }
        }

        DownloadStatus.RUNNING -> {
            OutlinedIconButton(
                onClick = { taskId?.let(onPauseDownload) },
                enabled = taskId != null,
                modifier = Modifier.size(actionSize),
                shape = actionShape,
                border = actionBorder,
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = actionContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = actionContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.pause_title)
                )
            }
        }

        DownloadStatus.PAUSED -> {
            OutlinedIconButton(
                onClick = { taskId?.let(onResumeDownload) },
                enabled = taskId != null,
                modifier = Modifier.size(actionSize),
                shape = actionShape,
                border = actionBorder,
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = actionContainer,
                    contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                    disabledContainerColor = actionContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.resume_title)
                )
            }
        }

        DownloadStatus.QUEUED -> {
            OutlinedIconButton(
                onClick = {
                    taskId?.let { id ->
                        if (activeFilter == ManageFilesFilter.ALL) {
                            onPauseDownload(id)
                        } else {
                            onCancelDownload(id)
                        }
                    }
                },
                enabled = taskId != null,
                modifier = Modifier.size(actionSize),
                shape = actionShape,
                border = actionBorder,
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = actionContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = actionContainer
                )
            ) {
                Icon(
                    imageVector = if (activeFilter == ManageFilesFilter.ALL) {
                        Icons.Filled.Pause
                    } else {
                        Icons.Filled.Close
                    },
                    contentDescription = androidx.compose.ui.res.stringResource(
                        if (activeFilter == ManageFilesFilter.ALL) {
                            R.string.pause_title
                        } else {
                            R.string.manage_action_remove_queue
                        }
                    )
                )
            }
        }

        DownloadStatus.CANCELED,
        DownloadStatus.FAILED -> {
            val candidate = ui.taskId?.let { taskIdValue ->
                RemoveCandidate(
                    taskId = taskIdValue,
                    fileName = ui.file.name,
                    contentUri = ui.contentUri
                )
            }
            OutlinedIconButton(
                onClick = onRetryDownload,
                modifier = Modifier.size(actionSize),
                shape = actionShape,
                border = actionBorder,
                colors = IconButtonDefaults.outlinedIconButtonColors(
                    containerColor = actionContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = actionContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.retry_title)
                )
            }
            if (activeFilter == ManageFilesFilter.FAILED) {
                OutlinedIconButton(
                    onClick = {
                        if (candidate != null) {
                            onRemoveCandidate(candidate)
                            onAlsoDeleteReset()
                        }
                    },
                    enabled = candidate != null,
                    modifier = Modifier.size(actionSize),
                    shape = actionShape,
                    border = actionBorder,
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        containerColor = actionContainer,
                        contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                        disabledContainerColor = actionContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.manage_action_remove_list)
                    )
                }
            }
        }

        null -> Unit
    }
}
