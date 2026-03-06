package com.psycode.spotiflac.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.DialogProperties
import com.psycode.spotiflac.R
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilter
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilterStats
import com.psycode.spotiflac.ui.component.managefiles.RemoveCandidate

@Composable
fun ManageFilesTopHeader(
    viewMode: FilesViewMode,
    onViewModeChange: (FilesViewMode) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MF_RADIUS_CONTAINER),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.choose_files_from_torrent),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManageFilesModeSwitcher(mode = viewMode, onChange = onViewModeChange)
                Surface(
                    shape = RoundedCornerShape(MF_RADIUS_ITEM),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close_title),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageFilesModeSwitcher(mode: FilesViewMode, onChange: (FilesViewMode) -> Unit) {
    val startSegmentShape = RoundedCornerShape(
        topStart = MF_RADIUS_ITEM,
        bottomStart = MF_RADIUS_ITEM,
        topEnd = 0.dp,
        bottomEnd = 0.dp
    )
    val endSegmentShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = MF_RADIUS_ITEM,
        bottomEnd = MF_RADIUS_ITEM
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.height(34.dp)) {
        SegmentedButton(
            selected = mode == FilesViewMode.LIST,
            onClick = { onChange(FilesViewMode.LIST) },
            shape = startSegmentShape,
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                contentDescription = stringResource(R.string.files_title),
                modifier = Modifier.size(17.dp)
            )
        }

        SegmentedButton(
            selected = mode == FilesViewMode.EXPLORER,
            onClick = { onChange(FilesViewMode.EXPLORER) },
            shape = endSegmentShape,
            colors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = stringResource(R.string.choose_folder),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
fun ManageFilesSearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(20.dp)) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.reset_title),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else null,
        placeholder = {
            Text(
                stringResource(R.string.search_file),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        shape = RoundedCornerShape(MF_RADIUS_ITEM),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun ManageFilesFilterBar(
    selected: ManageFilesFilter,
    stats: ManageFilesFilterStats,
    allowedFilters: List<ManageFilesFilter> = listOf(
        ManageFilesFilter.ALL,
        ManageFilesFilter.DOWNLOADED,
        ManageFilesFilter.DOWNLOADABLE,
        ManageFilesFilter.DOWNLOADING,
        ManageFilesFilter.FAILED
    ),
    onSelected: (ManageFilesFilter) -> Unit
) {
    val filters = allowedFilters
    val filterRowState = rememberLazyListState()
    val showStartFade by remember(filterRowState) {
        derivedStateOf { filterRowState.canScrollBackward }
    }
    val showEndFade by remember(filterRowState) {
        derivedStateOf { filterRowState.canScrollForward }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = filterRowState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 0.dp, end = 20.dp)
        ) {
            items(filters, key = { it.name }) { filter ->
                val labelRes = when (filter) {
                    ManageFilesFilter.ALL -> R.string.filter_all_count
                    ManageFilesFilter.DOWNLOADABLE -> R.string.filter_downloadable_count
                    ManageFilesFilter.DOWNLOADING -> R.string.filter_downloading_count
                    ManageFilesFilter.DOWNLOADED -> R.string.filter_downloaded_count
                    ManageFilesFilter.FAILED -> R.string.filter_failed_count
                }
                val count = when (filter) {
                    ManageFilesFilter.ALL -> stats.all
                    ManageFilesFilter.DOWNLOADABLE -> stats.downloadable
                    ManageFilesFilter.DOWNLOADING -> stats.downloading
                    ManageFilesFilter.DOWNLOADED -> stats.downloaded
                    ManageFilesFilter.FAILED -> stats.failed
                }
                FilterChip(
                    selected = selected == filter,
                    onClick = { onSelected(filter) },
                    shape = RoundedCornerShape(MF_RADIUS_ITEM),
                    modifier = Modifier.height(34.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    label = { Text(stringResource(labelRes, count)) }
                )
            }
        }

        if (showStartFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(34.dp)
                    .width(20.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        if (showEndFade) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(34.dp)
                    .width(24.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun ManageFilesBottomActionBar(
    availableCount: Int,
    allOn: Boolean,
    selectedCount: Int,
    selectedSizeLabel: String,
    primaryEnabled: Boolean,
    primaryLabel: String,
    primaryIcon: ImageVector,
    showDeleteToggle: Boolean,
    alsoDelete: Boolean,
    onAlsoDeleteChange: (Boolean) -> Unit,
    onToggleAll: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MF_RADIUS_CONTAINER),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) {
        Column {
            Text(
                text = stringResource(
                    R.string.manage_action_selection_state_with_size,
                    selectedCount,
                    availableCount,
                    selectedSizeLabel
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Row(
                Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onToggleAll,
                    modifier = Modifier.weight(1f).height(MF_ACTION_HEIGHT),
                    shape = RoundedCornerShape(MF_RADIUS_ITEM),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (allOn) stringResource(R.string.unchoose_all) else stringResource(R.string.choose_all),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    enabled = primaryEnabled,
                    onClick = onPrimaryAction,
                    modifier = Modifier.weight(1f).height(MF_ACTION_HEIGHT),
                    shape = RoundedCornerShape(MF_RADIUS_ITEM),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(primaryIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        primaryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showDeleteToggle) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MF_RADIUS_ITEM))
                        .clickable(role = Role.Checkbox) { onAlsoDeleteChange(!alsoDelete) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = alsoDelete,
                        onCheckedChange = { checked -> onAlsoDeleteChange(checked) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.delete_file_too),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun ManageFilesSortBar(
    selected: ManageFilesSortOption,
    onSelected: (ManageFilesSortOption) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == ManageFilesSortOption.RELEVANCE,
            onClick = { onSelected(ManageFilesSortOption.RELEVANCE) },
            shape = RoundedCornerShape(MF_RADIUS_ITEM),
            modifier = Modifier.height(34.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            label = { Text(stringResource(R.string.sort_relevance)) }
        )
        FilterChip(
            selected = selected == ManageFilesSortOption.SIZE,
            onClick = { onSelected(ManageFilesSortOption.SIZE) },
            shape = RoundedCornerShape(MF_RADIUS_ITEM),
            modifier = Modifier.height(34.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            label = { Text(stringResource(R.string.sort_size)) }
        )
        FilterChip(
            selected = selected == ManageFilesSortOption.NAME,
            onClick = { onSelected(ManageFilesSortOption.NAME) },
            shape = RoundedCornerShape(MF_RADIUS_ITEM),
            modifier = Modifier.height(34.dp),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            label = { Text(stringResource(R.string.sort_name)) }
        )
    }
}

internal fun primaryActionIconByLabel(label: String): ImageVector = when {
    "pause" in label.lowercase() -> Icons.Filled.Pause
    "resume" in label.lowercase() -> Icons.Filled.PlayArrow
    "remove" in label.lowercase() || "delete" in label.lowercase() -> Icons.Filled.DeleteSweep
    else -> Icons.Filled.Download
}

@Composable
fun RemoveEntryDialog(
    candidate: RemoveCandidate,
    alsoDelete: Boolean,
    onAlsoDeleteChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogShape = RoundedCornerShape(MF_RADIUS_CONTAINER)
    val sectionShape = RoundedCornerShape(MF_RADIUS_ITEM)
    val sectionBorder = BorderStroke(
        0.5.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.delete_from_list),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.delete_from_list_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoveFileOptionRow(
                    checked = alsoDelete,
                    onCheckedChange = onAlsoDeleteChange
                )
                if (candidate.fileName.isNotBlank()) {
                    Surface(
                        shape = sectionShape,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        border = sectionBorder
                    ) {
                        Text(
                            text = candidate.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.delete_title))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            ) {
                Text(stringResource(R.string.cancel_title))
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    )
}

@Composable
fun RemoveEntriesDialog(
    count: Int,
    alsoDelete: Boolean,
    onAlsoDeleteChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogShape = RoundedCornerShape(MF_RADIUS_CONTAINER)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = dialogShape,
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.delete_selected_downloads_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.delete_selected_downloads_desc, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RemoveFileOptionRow(
                    checked = alsoDelete,
                    onCheckedChange = onAlsoDeleteChange
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(stringResource(R.string.delete_title))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            ) {
                Text(stringResource(R.string.cancel_title))
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    )
}

@Composable
fun SaveDestinationDialog(
    onDismiss: () -> Unit,
    onIntoMedia: (rememberChoice: Boolean) -> Unit,
    onChooseFolder: (rememberChoice: Boolean) -> Unit
) {
    var rememberChoice by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(MF_RADIUS_CONTAINER),
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        tonalElevation = 2.dp,
        icon = {
            Surface(
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.where_to_save),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.where_to_save_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(MF_RADIUS_ITEM))
                        .clickable(role = Role.Checkbox) { rememberChoice = !rememberChoice },
                    shape = RoundedCornerShape(MF_RADIUS_ITEM),
                    color = if (rememberChoice) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    },
                    border = BorderStroke(
                        if (rememberChoice) 1.dp else 0.5.dp,
                        if (rememberChoice) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberChoice,
                            onCheckedChange = { checked -> rememberChoice = checked }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.save_choice_as_default))
                            Text(
                                text = stringResource(R.string.save_choice_as_default_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onIntoMedia(rememberChoice) },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.into_media))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onChooseFolder(rememberChoice) },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                )
            ) {
                Text(stringResource(R.string.choose_folder))
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true)
    )
}

@Composable
private fun RemoveFileOptionRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MF_RADIUS_ITEM))
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(MF_RADIUS_ITEM),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        },
        border = BorderStroke(
            if (checked) 1.dp else 0.5.dp,
            if (checked) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.delete_file_too))
                Text(
                    text = stringResource(R.string.delete_from_uri),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
