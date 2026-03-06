package com.psycode.spotiflac.ui.screen.downloads

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask

@Composable
internal fun DateSectionHeader(
    dateLabel: String,
    groupsCount: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    outline: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(0.5.dp, outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (isCollapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            DateCountChip(
                label = pluralStringResource(
                    id = R.plurals.torrents_count,
                    count = groupsCount,
                    groupsCount
                ),
                onClick = onToggle
            )
        }
    }
}

@Composable
private fun DateCountChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun TaskGroupCard(
    group: TaskGroup,
    outline: Color,
    opInFlight: Map<String, *>,
    onManageFiles: (Int) -> Unit,
    onDeleteGroup: (Int) -> Unit,
    onToggleGroupTransfer: (Int, GroupTransferAction) -> Unit,
    groupControl: GroupControlUiState?,
    interactionsLocked: Boolean,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = remember(group.tasks) { buildGroupMetrics(group.tasks) }
    val groupTransferAction = remember(group.tasks) { resolveGroupTransferAction(group.tasks) }
    val groupTransferTaskIds = remember(group.tasks, groupTransferAction) {
        groupTransferAction?.let { resolveGroupTransferTaskIds(group.tasks, it) }.orEmpty()
    }
    val failedTaskIds = remember(group.tasks) {
        group.tasks
            .asSequence()
            .filter { it.status == DownloadStatus.FAILED }
            .map { it.id }
            .toList()
    }
    val showRetryFailedInHeader = failedTaskIds.isNotEmpty() &&
        groupTransferAction != GroupTransferAction.RESUME

    Card(
        shape = RoundedCornerShape(DM_RADIUS_CARD),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        ),
        border = BorderStroke(0.5.dp, outline),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GroupStatusChip(state = metrics.state)
                        groupTransferAction?.let { action ->
                            val label = when (action) {
                                GroupTransferAction.PAUSE -> stringResource(
                                    R.string.group_transfer_pause_count,
                                    groupTransferTaskIds.size
                                )
                                GroupTransferAction.RESUME -> stringResource(
                                    R.string.group_transfer_resume_count,
                                    groupTransferTaskIds.size
                                )
                            }
                            val icon = when (action) {
                                GroupTransferAction.PAUSE -> Icons.Filled.Pause
                                GroupTransferAction.RESUME -> Icons.Filled.PlayArrow
                            }
                            HeaderActionPillButton(
                                label = label,
                                icon = icon,
                                onClick = {
                                    group.topicId?.let { topicId ->
                                        onToggleGroupTransfer(topicId, action)
                                    }
                                },
                                enabled = group.topicId != null &&
                                    groupTransferTaskIds.isNotEmpty() &&
                                    !interactionsLocked
                            )
                        }
                    }

                    if (showRetryFailedInHeader) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            HeaderActionPillButton(
                                label = stringResource(
                                    R.string.retry_failed_count,
                                    failedTaskIds.size
                                ),
                                icon = Icons.Filled.Refresh,
                                onClick = { failedTaskIds.forEach(onResumeTask) },
                                enabled = !interactionsLocked
                            )
                        }
                    }

                    groupControl?.action?.let { action ->
                        val targets = groupControl.targetTaskIds
                        val total = targets.size
                        val done = when (action) {
                            GroupControlAction.PAUSE -> group.tasks.count { task ->
                                task.id in targets &&
                                    task.status != DownloadStatus.RUNNING &&
                                    task.status != DownloadStatus.QUEUED
                            }
                            GroupControlAction.RESUME -> group.tasks.count { task ->
                                task.id in targets &&
                                    task.status != DownloadStatus.PAUSED &&
                                    task.status != DownloadStatus.FAILED
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = buildString {
                                    append(
                                        stringResource(
                                            when (action) {
                                                GroupControlAction.PAUSE -> R.string.group_pausing_status
                                                GroupControlAction.RESUME -> R.string.group_resuming_status
                                            }
                                        )
                                    )
                                    if (total > 0) {
                                        append(" ")
                                        append(
                                            stringResource(
                                                R.string.group_action_progress,
                                                done.coerceAtMost(total),
                                                total
                                            )
                                        )
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val hasMeta = metrics.queuedFiles > 0 || metrics.failedFiles > 0 || metrics.aggregateSpeedBytes > 0
                    val etaSeconds = estimateRemainingSeconds(
                        totalBytes = metrics.totalBytes,
                        downloadedBytes = metrics.downloadedBytes,
                        speedBytesPerSec = metrics.aggregateSpeedBytes
                    )
                    val etaLabel = etaSeconds
                        ?.takeIf { it > 0L }
                        ?.let { formatEtaCompact(it) }
                    Surface(
                        shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        border = BorderStroke(0.5.dp, outline.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (metrics.totalFiles > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(
                                            id = R.string.group_files_progress,
                                            metrics.completedFiles,
                                            metrics.totalFiles
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${metrics.progressPercent}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        etaLabel?.let { eta ->
                                            Text(
                                                text = stringResource(R.string.group_eta, eta),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                if (metrics.state != DownloadGroupState.COMPLETED) {
                                    LinearProgressIndicator(
                                        progress = { metrics.progressPercent / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.group_no_files),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (hasMeta) {
                                GroupMetaChips(metrics = metrics)
                            }
                        }
                    }

                    if (group.topicId != null) {
                        val primaryActionHeight = 40.dp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onManageFiles(group.topicId) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(primaryActionHeight),
                                shape = RoundedCornerShape(DM_RADIUS_CONTROL),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.files_title))
                            }
                            LongPressDeleteGroupButton(
                                modifier = Modifier
                                    .size(primaryActionHeight)
                                    .widthIn(min = primaryActionHeight),
                                onLongPress = { onDeleteGroup(group.topicId) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            val sectionsData = remember(group.tasks) {
                partitionTasksForSections(group.tasks)
            }
            var activeExpanded by rememberSaveable(group.title) { mutableStateOf(true) }
            var queuedExpanded by rememberSaveable(group.title) { mutableStateOf(true) }
            var pausedExpanded by rememberSaveable(group.title) { mutableStateOf(true) }
            var failedExpanded by rememberSaveable(group.title) { mutableStateOf(true) }
            var completedExpanded by rememberSaveable(group.title) { mutableStateOf(false) }

            val activeTasks = sectionsData.active
            val queuedTasks = sectionsData.queued
            val pausedTasks = sectionsData.paused
            val failedTasks = sectionsData.failed
            val completedTasks = sectionsData.completed

            val sections = listOfNotNull(
                activeTasks.takeIf { it.isNotEmpty() }?.let {
                    TaskSectionModel(
                        kind = TaskSectionKind.ACTIVE,
                        titleRes = R.string.group_section_active,
                        count = it.size,
                        tasks = it,
                        expanded = activeExpanded,
                        onToggle = { activeExpanded = !activeExpanded }
                    )
                },
                pausedTasks.takeIf { it.isNotEmpty() }?.let {
                    TaskSectionModel(
                        kind = TaskSectionKind.PAUSED,
                        titleRes = R.string.group_section_paused,
                        count = it.size,
                        tasks = it,
                        expanded = pausedExpanded,
                        onToggle = { pausedExpanded = !pausedExpanded }
                    )
                },
                queuedTasks.takeIf { it.isNotEmpty() }?.let {
                    TaskSectionModel(
                        kind = TaskSectionKind.QUEUED,
                        titleRes = R.string.group_section_queued,
                        count = it.size,
                        tasks = it,
                        expanded = queuedExpanded,
                        onToggle = { queuedExpanded = !queuedExpanded }
                    )
                },
                failedTasks.takeIf { it.isNotEmpty() }?.let {
                    TaskSectionModel(
                        kind = TaskSectionKind.FAILED,
                        titleRes = R.string.group_section_failed,
                        count = it.size,
                        tasks = it,
                        expanded = failedExpanded,
                        onToggle = { failedExpanded = !failedExpanded }
                    )
                },
                completedTasks.takeIf { it.isNotEmpty() }?.let {
                    TaskSectionModel(
                        kind = TaskSectionKind.COMPLETED,
                        titleRes = R.string.group_section_completed,
                        count = it.size,
                        tasks = it,
                        expanded = completedExpanded,
                        onToggle = { completedExpanded = !completedExpanded }
                    )
                }
            )

            if (sections.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    sections.forEach { section ->
                        TaskSection(
                            section = section,
                            opInFlight = opInFlight,
                            interactionsLocked = interactionsLocked,
                            onPauseTask = onPauseTask,
                            onResumeTask = onResumeTask,
                            onCancelTask = onCancelTask,
                            onOpenFile = onOpenFile
                        )
                    }
                }
            }
        }
    }
}

private data class TaskSectionModel(
    val kind: TaskSectionKind,
    val titleRes: Int,
    val count: Int,
    val tasks: List<DownloadTask>,
    val expanded: Boolean,
    val onToggle: () -> Unit
)

private enum class TaskSectionKind {
    ACTIVE,
    PAUSED,
    QUEUED,
    FAILED,
    COMPLETED
}

@Composable
private fun TaskSection(
    section: TaskSectionModel,
    opInFlight: Map<String, *>,
    interactionsLocked: Boolean,
    onPauseTask: (String) -> Unit,
    onResumeTask: (String) -> Unit,
    onCancelTask: (String) -> Unit,
    onOpenFile: (String) -> Unit
) {
    val accent = sectionAccentColor(section.kind)
    val headerBackground = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val headerBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.74f)

    Surface(
        shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
        color = headerBackground,
        border = BorderStroke(0.5.dp, headerBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { section.onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold
            )
            SectionCountChip(count = section.count, accent = accent)
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = if (section.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (!section.expanded) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        section.tasks.forEach { task ->
            val (rowBackground, rowBorder) = taskRowPalette(task.status)
            Surface(
                shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
                color = rowBackground,
                border = BorderStroke(0.5.dp, rowBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                DownloadTaskItem(
                    task = task,
                    inFlight = opInFlight[task.id] != null,
                    interactionsLocked = interactionsLocked,
                    onPause = { onPauseTask(task.id) },
                    onResume = { onResumeTask(task.id) },
                    onCancel = { onCancelTask(task.id) },
                    onOpen = onOpenFile,
                    showResumeForPaused = section.kind == TaskSectionKind.PAUSED || section.kind == TaskSectionKind.ACTIVE
                )
            }
        }
    }
}

@Composable
private fun sectionAccentColor(kind: TaskSectionKind): Color = when (kind) {
    TaskSectionKind.ACTIVE -> MaterialTheme.colorScheme.primary
    TaskSectionKind.QUEUED -> MaterialTheme.colorScheme.tertiary
    TaskSectionKind.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    TaskSectionKind.FAILED -> MaterialTheme.colorScheme.error
    TaskSectionKind.COMPLETED -> MaterialTheme.colorScheme.secondary
}

@Composable
private fun SectionCountChip(count: Int, accent: Color) {
    Surface(
        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.92f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun taskRowPalette(status: DownloadStatus): Pair<Color, Color> {
    val colorScheme = MaterialTheme.colorScheme
    return when (status) {
        DownloadStatus.RUNNING -> colorScheme.primary.copy(alpha = 0.05f) to colorScheme.primary.copy(alpha = 0.14f)
        DownloadStatus.QUEUED -> colorScheme.tertiary.copy(alpha = 0.05f) to colorScheme.tertiary.copy(alpha = 0.14f)
        DownloadStatus.PAUSED -> colorScheme.onSurfaceVariant.copy(alpha = 0.04f) to colorScheme.outline.copy(alpha = 0.22f)
        DownloadStatus.FAILED -> colorScheme.error.copy(alpha = 0.07f) to colorScheme.error.copy(alpha = 0.19f)
        DownloadStatus.COMPLETED -> colorScheme.secondary.copy(alpha = 0.05f) to colorScheme.secondary.copy(alpha = 0.14f)
        DownloadStatus.CANCELED -> colorScheme.surfaceColorAtElevation(2.dp) to colorScheme.outline.copy(alpha = 0.2f)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LongPressDeleteGroupButton(
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(DM_RADIUS_CONTROL)
    Surface(
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onLongPress,
                onLongClick = onLongPress,
                role = Role.Button
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = stringResource(R.string.delete_title),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun HeaderActionPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GroupStatusChip(
    state: DownloadGroupState,
    modifier: Modifier = Modifier
) {
    val (labelRes, color) = when (state) {
        DownloadGroupState.EMPTY -> R.string.group_state_empty to MaterialTheme.colorScheme.outline
        DownloadGroupState.DOWNLOADING -> R.string.in_progresss to MaterialTheme.colorScheme.primary
        DownloadGroupState.QUEUED -> R.string.in_queue to MaterialTheme.colorScheme.tertiary
        DownloadGroupState.PAUSED -> R.string.in_pause to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadGroupState.PARTIAL -> R.string.group_state_partial to MaterialTheme.colorScheme.secondary
        DownloadGroupState.FAILED -> R.string.in_error to MaterialTheme.colorScheme.error
        DownloadGroupState.COMPLETED -> R.string.completed_title to MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
        color = color.copy(alpha = 0.12f),
        modifier = modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(labelRes),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GroupMetaChips(metrics: DownloadGroupMetrics) {
    val chips = buildList {
        if (metrics.queuedFiles > 0) {
            add(
                MetaChipModel(
                    text = stringResource(R.string.group_meta_queued_short, metrics.queuedFiles),
                    tone = MetaChipTone.NEUTRAL
                )
            )
        }
        if (metrics.failedFiles > 0) {
            add(
                MetaChipModel(
                    text = stringResource(R.string.group_meta_failed_short, metrics.failedFiles),
                    tone = MetaChipTone.ERROR
                )
            )
        }
        val speed = formatSpeed(metrics.aggregateSpeedBytes)
        if (speed.isNotEmpty()) {
            add(
                MetaChipModel(
                    text = stringResource(R.string.group_meta_speed_short, speed),
                    tone = MetaChipTone.NEUTRAL
                )
            )
        }
    }
    if (chips.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { chip ->
            MetaInfoChip(text = chip.text, tone = chip.tone)
        }
    }
}

@Composable
private fun MetaInfoChip(
    text: String,
    tone: MetaChipTone
) {
    val borderColor = when (tone) {
        MetaChipTone.NEUTRAL -> MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
        MetaChipTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
    }
    val backgroundColor = when (tone) {
        MetaChipTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        MetaChipTone.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
    }
    val textColor = when (tone) {
        MetaChipTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        MetaChipTone.ERROR -> MaterialTheme.colorScheme.error
    }

    Surface(
        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
        border = BorderStroke(0.5.dp, borderColor),
        color = backgroundColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private data class MetaChipModel(
    val text: String,
    val tone: MetaChipTone
)

private enum class MetaChipTone {
    NEUTRAL,
    ERROR
}

@Composable
private fun DownloadTaskItem(
    task: DownloadTask,
    inFlight: Boolean,
    interactionsLocked: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (String) -> Unit,
    showResumeForPaused: Boolean
) {
    val trailingSize = 36.dp

    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            val (icon, tint) = when (task.status) {
                DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                DownloadStatus.FAILED -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                DownloadStatus.RUNNING -> Icons.Filled.CloudDownload to MaterialTheme.colorScheme.primary
                DownloadStatus.PAUSED -> Icons.Filled.PauseCircle to MaterialTheme.colorScheme.onSurfaceVariant
                DownloadStatus.CANCELED -> Icons.Filled.Close to MaterialTheme.colorScheme.onSurfaceVariant
                DownloadStatus.QUEUED -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, contentDescription = null, tint = tint)
        },
        headlineContent = {
            Text(
                text = task.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = when (task.status) {
            DownloadStatus.RUNNING,
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED -> {
                {
                    val speed = formatSpeed(task.speedBytesPerSec)
                    val label = when (task.status) {
                        DownloadStatus.QUEUED -> stringResource(R.string.in_queue)
                        DownloadStatus.RUNNING -> {
                            if (speed.isNotEmpty()) {
                                "${task.progress}% - $speed"
                            } else {
                                if (task.progress > 0) {
                                    "${task.progress}% - ${stringResource(R.string.task_waiting_peers_status)}"
                                } else {
                                    stringResource(R.string.task_starting_status)
                                }
                            }
                        }

                        DownloadStatus.PAUSED -> {
                            if (task.progress > 0) "${task.progress}%"
                            else stringResource(R.string.in_pause)
                        }

                        else -> ""
                    }

                    Column {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { task.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            DownloadStatus.FAILED -> {
                {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.errorMessage ?: stringResource(R.string.in_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            DownloadStatus.COMPLETED,
            DownloadStatus.CANCELED -> null
        },
        trailingContent = {
            val showBusy = inFlight && task.status != DownloadStatus.COMPLETED
            if (showBusy) {
                CircularProgressIndicator(modifier = Modifier.size(trailingSize), strokeWidth = 2.dp)
            } else {
                when (task.status) {
                    DownloadStatus.RUNNING -> IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(trailingSize),
                        enabled = !interactionsLocked
                    ) { Icon(Icons.Filled.Pause, contentDescription = null) }

                    DownloadStatus.QUEUED -> IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(trailingSize),
                        enabled = !interactionsLocked
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.cancel_title)
                        )
                    }

                    DownloadStatus.PAUSED -> {
                        if (showResumeForPaused) {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(trailingSize),
                                enabled = !interactionsLocked
                            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null) }
                        } else {
                            Spacer(modifier = Modifier.size(trailingSize))
                        }
                    }

                    DownloadStatus.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onResume,
                            modifier = Modifier.size(trailingSize),
                            enabled = !interactionsLocked
                        ) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.retry_title)) }
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.size(trailingSize),
                            enabled = !interactionsLocked
                        ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cancel_title)) }
                    }

                    DownloadStatus.COMPLETED -> task.contentUri?.let { uri ->
                        OutlinedIconButton(
                            onClick = { onOpen(uri) },
                            modifier = Modifier.size(trailingSize),
                            shape = RoundedCornerShape(DM_RADIUS_CONTROL),
                            colors = IconButtonDefaults.outlinedIconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.open_title)
                            )
                        }
                    }

                    DownloadStatus.CANCELED -> IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(trailingSize),
                        enabled = !interactionsLocked
                    ) { Icon(Icons.Filled.Delete, contentDescription = null) }
                }
            }
        }
    )
}



