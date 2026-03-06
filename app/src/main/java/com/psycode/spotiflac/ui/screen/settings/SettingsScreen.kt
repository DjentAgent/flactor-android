package com.psycode.spotiflac.ui.screen.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.scale
import androidx.hilt.navigation.compose.hiltViewModel
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.ui.common.SaveLocationViewModel
import com.psycode.spotiflac.ui.common.formatTreeUriForUi
import com.psycode.spotiflac.ui.common.isWritableTreeUriAccessible
import com.psycode.spotiflac.ui.common.persistTreePermission
import com.psycode.spotiflac.ui.component.AppCardTopBar
import com.psycode.spotiflac.ui.component.AppCardTopBarIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SaveLocationViewModel = hiltViewModel(),
    notificationSettingsViewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val defaultLocation by viewModel.defaultSaveLocation.collectAsState()
    val notificationPreferences by notificationSettingsViewModel.notificationPreferences.collectAsState()
    val context = LocalContext.current
    val folderUri = defaultLocation.customFolderUri.orEmpty()
    val hasFolderUri = !folderUri.isNullOrBlank()
    val hasFolderAccess = if (hasFolderUri) {
        isWritableTreeUriAccessible(context, folderUri)
    } else {
        false
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            persistTreePermission(context = context, uri = uri)
            viewModel.setDefaultCustomFolder(uri.toString())
        }
    }
    val onSelectCustomFolder: () -> Unit = {
        when {
            !hasFolderUri -> folderPicker.launch(null)
            !hasFolderAccess -> folderPicker.launch(null)
            else -> viewModel.setDefaultCustomFolder(folderUri)
        }
    }
    val sectionShape = RoundedCornerShape(18.dp)
    val sectionOutline = BorderStroke(
        width = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f)
    )

    Scaffold(
        topBar = {
            AppCardTopBar(
                title = stringResource(R.string.settings_title),
                startAction = {
                    AppCardTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.pagination_back),
                        onClick = onBack
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = sectionOutline
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.settings_default_save_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.settings_default_save_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        SaveLocationOptionCard(
                            icon = Icons.Filled.MusicNote,
                            title = stringResource(R.string.into_media),
                            subtitle = null,
                            selected = defaultLocation.saveOption == SaveOption.MUSIC_LIBRARY,
                            onClick = { viewModel.setDefaultMediaLibrary() }
                        )

                        SaveLocationOptionCard(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.choose_folder),
                            subtitle = if (hasFolderUri) {
                                formatTreeUriForUi(folderUri)
                            } else {
                                stringResource(R.string.settings_folder_not_selected)
                            },
                            selected = defaultLocation.saveOption == SaveOption.CUSTOM_FOLDER,
                            onClick = onSelectCustomFolder
                        ) {
                            if (hasFolderUri && !hasFolderAccess) {
                                Text(
                                    text = stringResource(R.string.default_save_folder_access_lost),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { folderPicker.launch(null) },
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = if (hasFolderUri) {
                                            stringResource(R.string.settings_change_folder)
                                        } else {
                                            stringResource(R.string.choose_folder)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = sectionShape,
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = sectionOutline
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_notifications_title),
                            style = MaterialTheme.typography.titleMedium
                        )

                        NotificationToggleRow(
                            title = stringResource(R.string.settings_notifications_progress_title),
                            subtitle = stringResource(R.string.settings_notifications_progress_desc),
                            checked = notificationPreferences.progressEnabled,
                            onCheckedChange = notificationSettingsViewModel::setProgressEnabled
                        )

                        NotificationToggleRow(
                            title = stringResource(R.string.settings_notifications_events_title),
                            subtitle = stringResource(R.string.settings_notifications_events_desc),
                            checked = notificationPreferences.eventsEnabled,
                            onCheckedChange = notificationSettingsViewModel::setEventsEnabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveLocationOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null
) {
    val border = if (selected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.52f))
    } else {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }
    val iconContainer = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconContainer,
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (extraContent != null) {
                extraContent()
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val border = if (checked) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
    }
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .scale(0.86f)
            )
        }
    }
}
