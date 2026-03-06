package com.psycode.spotiflac.ui.screen.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.psycode.spotiflac.R

@Composable
internal fun NotificationChannelBanner(
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        border = BorderStroke(0.5.dp, outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.notif_disabled),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.notif_disable_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_title)) }
            Spacer(Modifier.width(4.dp))
            OutlinedButton(
                onClick = onRefresh,
                shape = RoundedCornerShape(DM_RADIUS_CONTROL)
            ) { Text(stringResource(R.string.check_title)) }
        }
    }
}

@Composable
internal fun FileOperationBanner(
    visible: Boolean,
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        val outline = MaterialTheme.colorScheme.outlineVariant
        val iconTint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        Surface(
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            border = BorderStroke(0.5.dp, outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun DeleteGroupDialog(
    deleteDialog: DeleteGroupUiState,
    onCancel: () -> Unit,
    onAlsoDeleteLocalFilesChange: (Boolean) -> Unit,
    onConfirm: () -> Unit
) {
    when (val dialog = deleteDialog) {
        DeleteGroupUiState.Hidden -> Unit
        is DeleteGroupUiState.Confirm -> {
            val sectionShape = RoundedCornerShape(DM_RADIUS_CONTROL)
            val sectionBorder = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            )
            AlertDialog(
                onDismissRequest = { if (!dialog.inProgress) onCancel() },
                shape = RoundedCornerShape(DM_RADIUS_CONTAINER),
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
                            imageVector = Icons.Filled.DeleteForever,
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
                            text = stringResource(R.string.delete_torrent_question),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.delete_torrent_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = sectionShape,
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            border = sectionBorder
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.torrent_title, dialog.title),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = stringResource(R.string.torrent_elements, dialog.itemsCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(sectionShape)
                                .clickable(
                                    enabled = !dialog.inProgress,
                                    role = Role.Checkbox
                                ) {
                                    onAlsoDeleteLocalFilesChange(!dialog.alsoDeleteLocalFiles)
                                },
                            shape = sectionShape,
                            color = if (dialog.alsoDeleteLocalFiles) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            } else {
                                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                            },
                            border = BorderStroke(
                                if (dialog.alsoDeleteLocalFiles) 1.dp else 0.5.dp,
                                if (dialog.alsoDeleteLocalFiles) {
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
                                    checked = dialog.alsoDeleteLocalFiles,
                                    onCheckedChange = null,
                                    enabled = !dialog.inProgress
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = stringResource(R.string.delete_group_files_too),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        text = stringResource(R.string.delete_group_files_from_uri),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (dialog.error != null) {
                            Surface(
                                shape = sectionShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    0.5.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                )
                            ) {
                                Text(
                                    text = dialog.error ?: stringResource(R.string.could_not_delete_torrent),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { if (!dialog.inProgress) onConfirm() },
                        enabled = !dialog.inProgress,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (dialog.inProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.delete_title))
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !dialog.inProgress,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
                        )
                    ) {
                        Text(stringResource(R.string.cancel_title))
                    }
                }
            )
        }
    }
}

