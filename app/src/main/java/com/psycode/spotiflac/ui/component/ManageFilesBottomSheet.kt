package com.psycode.spotiflac.ui.component

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DefaultSaveLocation
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.ui.common.AnimatedModalBottomSheet
import com.psycode.spotiflac.ui.common.isWritableTreeUriAccessible
import com.psycode.spotiflac.ui.common.persistTreePermission
import com.psycode.spotiflac.ui.component.managefiles.PreparedFilesDl
import com.psycode.spotiflac.ui.component.managefiles.UiFileDl
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesExplorerTree
import com.psycode.spotiflac.ui.component.managefiles.applyFolderSelectionForKey
import com.psycode.spotiflac.ui.component.managefiles.areAllSelectableChecked
import com.psycode.spotiflac.ui.component.managefiles.buildManageFilesExplorerTree
import com.psycode.spotiflac.ui.component.managefiles.buildQueueSelectionAction
import com.psycode.spotiflac.ui.component.managefiles.buildRemoveCompletedAction
import com.psycode.spotiflac.ui.component.managefiles.humanReadableSize
import com.psycode.spotiflac.ui.component.managefiles.folderToggleStateForKey
import com.psycode.spotiflac.ui.component.managefiles.buildManageFilesFilterStats
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilter
import com.psycode.spotiflac.ui.component.managefiles.ManageFilesFilterStats
import com.psycode.spotiflac.ui.component.managefiles.manageFilesFilterMatches
import com.psycode.spotiflac.ui.component.managefiles.normalizeCore
import com.psycode.spotiflac.ui.component.managefiles.prepareFilesDl
import com.psycode.spotiflac.ui.component.managefiles.QueueSelectionAction
import com.psycode.spotiflac.ui.component.managefiles.recommendedManageFilesFilter
import com.psycode.spotiflac.ui.component.managefiles.removeCandidatesForCheckedCompleted
import com.psycode.spotiflac.ui.component.managefiles.selectedSelectableCount
import com.psycode.spotiflac.ui.component.managefiles.selectedSelectableSizeBytes
import com.psycode.spotiflac.ui.component.managefiles.toggleAllSelectable
import com.psycode.spotiflac.ui.screen.downloads.ManageFilesUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ManageFilesBottomSheet"
private const val FETCHING_TORRENT_BACK_CTA_DELAY_MS = 10_000L

private enum class ManageFilesFilterAction {
    ENQUEUE,
    PAUSE,
    RESUME,
    REMOVE_COMPLETED
}

private enum class ManageFilesLoadingStage {
    FETCHING_TORRENT,
    PREPARING_FILES
}

data class ManageFilesAutoMatch(
    val artist: String? = null,
    val title: String? = null,
    val enabled: Boolean = false,
    val minFuzzy: Double = 0.84
)

enum class FilesViewMode { LIST, EXPLORER }
enum class ManageFilesFilterScope { FULL, PICKER }

private data class ManageFilesContentCallbacks(
    val fileMatches: (UiFileDl) -> Boolean,
    val allDescendantIndices: (String) -> List<Int>,
    val folderToggleState: (String) -> Pair<ToggleableState, Boolean>,
    val applyFolderSelection: (String, Boolean) -> Unit,
    val onLog: (String) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFilesBottomSheet(
    visible: Boolean,
    uiState: ManageFilesUiState,
    tasks: List<DownloadTask>,
    topicId: Int?,
    groupTitle: String,
    onAddToQueue: (List<TorrentFile>, SaveOption, String?) -> Unit,
    onDismissRequest: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveCompletedEntry: (taskId: String, alsoDeleteLocal: Boolean, contentUri: String?) -> Unit,
    defaultSaveLocation: DefaultSaveLocation,
    onSaveDefaultSaveLocation: (SaveOption, String?) -> Unit,
    autoMatch: ManageFilesAutoMatch? = null,
    filterScope: ManageFilesFilterScope = ManageFilesFilterScope.FULL,
    onRetryLoadTorrentFiles: (() -> Unit)? = null
) {
    AnimatedModalBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest
    ) {
        when (uiState) {
            ManageFilesUiState.Hidden -> Unit
            ManageFilesUiState.Loading -> ManageFilesLoadingState(
                groupTitle = groupTitle,
                stage = ManageFilesLoadingStage.FETCHING_TORRENT,
                onDismissRequest = onDismissRequest
            )
            is ManageFilesUiState.Error -> ManageFilesErrorState(
                message = uiState.message,
                onDismissRequest = onDismissRequest,
                onRetryLoadTorrentFiles = onRetryLoadTorrentFiles
            )

            is ManageFilesUiState.Success -> {
                ManageFilesContent(
                    files = uiState.files,
                    tasks = tasks,
                    topicId = topicId,
                    groupTitle = groupTitle,
                    onAddToQueue = onAddToQueue,
                    onHideSheet = onDismissRequest,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onCancelDownload = onCancelDownload,
                    onRemoveCompletedEntry = onRemoveCompletedEntry,
                    defaultSaveLocation = defaultSaveLocation,
                    onSaveDefaultSaveLocation = onSaveDefaultSaveLocation,
                    autoMatch = autoMatch,
                    filterScope = filterScope
                )
            }
        }
    }
}

@Composable
private fun ManageFilesLoadingState(
    groupTitle: String,
    stage: ManageFilesLoadingStage,
    onDismissRequest: () -> Unit
) {
    val titleRes = when (stage) {
        ManageFilesLoadingStage.FETCHING_TORRENT -> R.string.manage_files_loading_fetch_title
        ManageFilesLoadingStage.PREPARING_FILES -> R.string.manage_files_loading_prepare_title
    }
    val subtitleRes = when (stage) {
        ManageFilesLoadingStage.FETCHING_TORRENT -> R.string.manage_files_loading_fetch_subtitle
        ManageFilesLoadingStage.PREPARING_FILES -> R.string.manage_files_loading_prepare_subtitle
    }
    var showBackToResultsCta by remember(stage) {
        mutableStateOf(stage == ManageFilesLoadingStage.PREPARING_FILES)
    }
    LaunchedEffect(stage) {
        if (stage == ManageFilesLoadingStage.FETCHING_TORRENT) {
            showBackToResultsCta = false
            delay(FETCHING_TORRENT_BACK_CTA_DELAY_MS)
            showBackToResultsCta = true
        } else {
            showBackToResultsCta = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            border = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            ),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(78.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Downloading,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                if (groupTitle.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.manage_files_loading_torrent_title, groupTitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                )
                if (showBackToResultsCta) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDismissRequest,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Text(text = stringResource(R.string.back_to_results_title))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageFilesErrorState(
    message: String,
    onDismissRequest: () -> Unit,
    onRetryLoadTorrentFiles: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            border = BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
            ),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(78.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                shape = CircleShape
                            )
                    )
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.manage_files_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val actionShape = RoundedCornerShape(12.dp)
                val hasRetry = onRetryLoadTorrentFiles != null
                val backLabelRes = if (hasRetry) {
                    R.string.back_to_results_compact_title
                } else {
                    R.string.back_to_results_title
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasRetry) {
                        Button(
                            onClick = { onRetryLoadTorrentFiles?.invoke() },
                            modifier = Modifier
                                .weight(1f)
                                .height(MF_ACTION_HEIGHT),
                            shape = actionShape
                        ) {
                            Text(stringResource(R.string.retry_title))
                        }
                    }
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(MF_ACTION_HEIGHT),
                        shape = actionShape,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Text(stringResource(backLabelRes))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ManageFilesContent(
    files: List<TorrentFile>,
    tasks: List<DownloadTask>,
    topicId: Int?,
    groupTitle: String,
    onAddToQueue: (List<TorrentFile>, SaveOption, String?) -> Unit,
    onHideSheet: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemoveCompletedEntry: (taskId: String, alsoDeleteLocal: Boolean, contentUri: String?) -> Unit,
    defaultSaveLocation: DefaultSaveLocation,
    onSaveDefaultSaveLocation: (SaveOption, String?) -> Unit,
    autoMatch: ManageFilesAutoMatch?,
    filterScope: ManageFilesFilterScope
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val groupNorm = remember(groupTitle) { normalizeCore(groupTitle) }
    val tasksSnapshot = remember(tasks) {
        tasks.map { task -> listOf(task.id, task.status.name, task.progress.toString(), task.contentUri.orEmpty()) }
    }
    var prepared by remember { mutableStateOf<PreparedFilesDl?>(null) }
    val state = rememberManageFilesState()
    var autoFilterAppliedContentKey by remember { mutableStateOf<String?>(null) }
    var rememberSaveChoiceForFolderPick by remember { mutableStateOf(false) }

    fun logSelection(message: String) {
        Log.d(TAG, "$message; selected=${state.checks.count { it }}/${state.checks.size}")
    }

    fun dispatchQueueActionAndHide(action: QueueSelectionAction?) {
        if (action != null) {
            onAddToQueue(action.files, action.saveOption, action.customUri)
        }
        onHideSheet()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val shouldRememberChoice = rememberSaveChoiceForFolderPick
        rememberSaveChoiceForFolderPick = false
        if (uri == null) return@rememberLauncherForActivityResult
        persistTreePermission(context = context, uri = uri)
        if (shouldRememberChoice) {
            onSaveDefaultSaveLocation(SaveOption.CUSTOM_FOLDER, uri.toString())
        }
        dispatchQueueActionAndHide(
            buildCustomFolderQueueAction(
                state = state,
                uri = uri
            )
        )
    }

    fun triggerSaveWithDefaultsOrDialog() {
        when (defaultSaveLocation.saveOption) {
            null -> {
                state.showSaveDialog = true
            }
            SaveOption.MUSIC_LIBRARY -> {
                dispatchQueueActionAndHide(buildMusicLibraryQueueAction(state))
            }
            SaveOption.CUSTOM_FOLDER -> {
                val customFolderUri = defaultSaveLocation.customFolderUri
                if (isWritableTreeUriAccessible(context, customFolderUri)) {
                    val parsedUri = customFolderUri?.let { Uri.parse(it) }
                    if (parsedUri != null) {
                        dispatchQueueActionAndHide(buildCustomFolderQueueAction(state, parsedUri))
                    } else {
                        state.showSaveDialog = true
                    }
                } else {
                    state.showSaveDialog = true
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.default_save_folder_access_lost)
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(files, tasksSnapshot, groupTitle, autoMatch, topicId) {
        Log.d(TAG, "---- ManageFilesContent start ----")
        val newPrepared = withContext(Dispatchers.Default) {
            prepareFilesDl(
                files = files,
                tasks = tasks,
                topicId = topicId,
                groupTitle = groupTitle,
                groupNorm = groupNorm,
                autoMatchEnabled = autoMatch?.enabled == true,
                autoMatchArtist = autoMatch?.artist,
                autoMatchTitle = autoMatch?.title,
                autoMatchMinFuzzy = autoMatch?.minFuzzy ?: 0.84
            )
        }
        prepared = newPrepared
    }

    val prep = prepared
    if (prep == null) {
        ManageFilesLoadingState(
            groupTitle = groupTitle,
            stage = ManageFilesLoadingStage.PREPARING_FILES,
            onDismissRequest = onHideSheet
        )
        return
    }

    val tree = remember(prep) { buildManageFilesExplorerTree(prep) }
    val contentKey = remember(topicId, files, groupTitle) {
        buildString {
            append(topicId ?: -1)
            append('|')
            append(files.size)
            append('|')
            append(groupTitle)
            append('|')
            append(files.firstOrNull()?.torrentFilePath.orEmpty())
        }
    }
    LaunchedEffect(prep) {
        state.applyPreparedData(
            prep = prep,
            initialExpandedDirs = tree.initialExpandedDirs,
            contentKey = contentKey
        )
        if (
            filterScope == ManageFilesFilterScope.FULL &&
            autoFilterAppliedContentKey != contentKey
        ) {
            state.fileFilter = recommendedManageFilesFilter(buildManageFilesFilterStats(prep))
            autoFilterAppliedContentKey = contentKey
        }
        logSelection("Initial checks set")
    }

    fun allDescendantIndices(key: String): List<Int> = tree.descendantIndices[key].orEmpty()

    val allowedFilters = remember(filterScope) {
        when (filterScope) {
            ManageFilesFilterScope.FULL -> listOf(
                ManageFilesFilter.ALL,
                ManageFilesFilter.DOWNLOADED,
                ManageFilesFilter.DOWNLOADABLE,
                ManageFilesFilter.DOWNLOADING,
                ManageFilesFilter.FAILED
            )
            ManageFilesFilterScope.PICKER -> listOf(ManageFilesFilter.ALL)
        }
    }
    val normalizedFilter = remember(state.fileFilter, allowedFilters) {
        if (state.fileFilter in allowedFilters) state.fileFilter else ManageFilesFilter.ALL
    }
    if (state.fileFilter != normalizedFilter) {
        state.fileFilter = normalizedFilter
    }

    val queryNorm = remember(state.fileQuery) { normalizeCore(state.fileQuery) }
    fun fileMatches(ui: UiFileDl): Boolean {
        return manageFilesFilterMatches(
            ui = ui,
            filter = state.fileFilter,
            queryNorm = queryNorm
        )
    }

    val filteredEnqueueIndices = remember(prep, state.fileFilter, queryNorm) {
        prep.selectableIndices.filter { idx ->
            prep.ordered.getOrNull(idx)?.let(::fileMatches) == true
        }
    }
    val filteredResumeIndices = remember(prep, state.fileFilter, queryNorm) {
        prep.ordered.indices.filter { idx ->
            val ui = prep.ordered.getOrNull(idx) ?: return@filter false
            fileMatches(ui) &&
                ui.taskId != null &&
                (ui.status == DownloadStatus.PAUSED || ui.status == DownloadStatus.FAILED)
        }
    }
    val filteredPauseIndices = remember(prep, state.fileFilter, queryNorm) {
        prep.ordered.indices.filter { idx ->
            val ui = prep.ordered.getOrNull(idx) ?: return@filter false
            fileMatches(ui) &&
                ui.taskId != null &&
                (ui.status == DownloadStatus.QUEUED || ui.status == DownloadStatus.RUNNING)
        }
    }
    val filteredCompletedIndices = remember(prep, state.fileFilter, queryNorm) {
        prep.removableCompletedIndices.filter { idx ->
            prep.ordered.getOrNull(idx)?.let(::fileMatches) == true
        }
    }

    val filterAction = remember(
        filterScope,
        state.fileFilter,
        filteredEnqueueIndices,
        filteredPauseIndices,
        filteredResumeIndices,
        filteredCompletedIndices
    ) {
        when {
            filterScope == ManageFilesFilterScope.PICKER -> ManageFilesFilterAction.ENQUEUE
            state.fileFilter == ManageFilesFilter.ALL && filteredEnqueueIndices.isNotEmpty() -> {
                ManageFilesFilterAction.ENQUEUE
            }
            state.fileFilter == ManageFilesFilter.ALL && filteredCompletedIndices.isNotEmpty() -> {
                ManageFilesFilterAction.REMOVE_COMPLETED
            }
            state.fileFilter == ManageFilesFilter.DOWNLOADABLE -> ManageFilesFilterAction.ENQUEUE
            state.fileFilter == ManageFilesFilter.DOWNLOADING && filteredPauseIndices.isNotEmpty() -> {
                ManageFilesFilterAction.PAUSE
            }
            state.fileFilter == ManageFilesFilter.DOWNLOADING && filteredResumeIndices.isNotEmpty() -> {
                ManageFilesFilterAction.RESUME
            }
            state.fileFilter == ManageFilesFilter.FAILED && filteredResumeIndices.isNotEmpty() -> {
                ManageFilesFilterAction.RESUME
            }
            state.fileFilter == ManageFilesFilter.DOWNLOADED && filteredCompletedIndices.isNotEmpty() -> {
                ManageFilesFilterAction.REMOVE_COMPLETED
            }
            else -> null
        }
    }

    val actionSelectableIndices = remember(
        filterAction,
        filteredPauseIndices,
        filteredEnqueueIndices,
        filteredResumeIndices,
        filteredCompletedIndices
    ) {
        when (filterAction) {
            ManageFilesFilterAction.ENQUEUE -> filteredEnqueueIndices
            ManageFilesFilterAction.PAUSE -> filteredPauseIndices
            ManageFilesFilterAction.RESUME -> filteredResumeIndices
            ManageFilesFilterAction.REMOVE_COMPLETED -> filteredCompletedIndices
            null -> emptyList()
        }
    }

    val callbacks = remember(
        prep,
        tree,
        queryNorm,
        state.checks,
        state.expandedDirs,
        state.fileFilter,
        actionSelectableIndices
    ) {
        ManageFilesContentCallbacks(
            fileMatches = ::fileMatches,
            allDescendantIndices = ::allDescendantIndices,
            folderToggleState = { key ->
                folderToggleStateForKey(
                    tree = tree,
                    key = key,
                    selectableIndices = actionSelectableIndices,
                    checks = state.checks
                )
            },
            applyFolderSelection = { key, toChecked ->
                val updated = applyFolderSelectionForKey(
                    tree = tree,
                    key = key,
                    selectableIndices = actionSelectableIndices,
                    checks = state.checks,
                    toChecked = toChecked
                )
                if (updated !== state.checks) {
                    state.checks = updated
                    logSelection("Folder toggle '$key' -> toChecked=$toChecked")
                }
            },
            onLog = { msg -> Log.d(TAG, msg) }
        )
    }

    Box(Modifier.fillMaxHeight()) {
        ManageFilesContentBody(
            state = state,
            prep = prep,
            tree = tree,
            queryNorm = queryNorm,
            filterStats = buildManageFilesFilterStats(prep),
            allowedFilters = allowedFilters,
            filterScope = filterScope,
            filterAction = filterAction,
            actionSelectableIndices = actionSelectableIndices,
            callbacks = callbacks,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onCancelDownload = onCancelDownload,
            onHideSheet = onHideSheet,
            onRequestSaveSelection = ::triggerSaveWithDefaultsOrDialog,
            snackbarHostState = snackbarHostState,
            scope = scope
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }

    ManageFilesDialogsHost(
        state = state,
        prep = prep,
        onRemoveCompletedEntry = onRemoveCompletedEntry,
        onDispatchQueueActionAndHide = ::dispatchQueueActionAndHide,
        onSaveDefaultSaveLocation = onSaveDefaultSaveLocation,
        onChooseFolder = { rememberChoice ->
            state.showSaveDialog = false
            rememberSaveChoiceForFolderPick = rememberChoice
            folderPicker.launch(null)
        }
    )
}

@Composable
private fun ManageFilesContentBody(
    state: ManageFilesState,
    prep: PreparedFilesDl,
    tree: ManageFilesExplorerTree,
    queryNorm: String,
    filterStats: ManageFilesFilterStats,
    allowedFilters: List<ManageFilesFilter>,
    filterScope: ManageFilesFilterScope,
    filterAction: ManageFilesFilterAction?,
    actionSelectableIndices: List<Int>,
    callbacks: ManageFilesContentCallbacks,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onHideSheet: () -> Unit,
    onRequestSaveSelection: () -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxHeight()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        ManageFilesTopHeader(
            viewMode = state.viewMode,
            onViewModeChange = { state.viewMode = it },
            onClose = onHideSheet
        )
        Spacer(Modifier.height(12.dp))
        ManageFilesSearchField(
            query = state.fileQuery,
            onQueryChange = {
                state.fileQuery = it
                Log.d(TAG, "Search query changed -> '$it' (norm='${queryNorm}')")
            }
        )
        Spacer(Modifier.height(12.dp))
        if (allowedFilters.size > 1) {
            ManageFilesFilterBar(
                selected = state.fileFilter,
                stats = filterStats,
                allowedFilters = allowedFilters,
                onSelected = { state.onFilterSelected(it) }
            )
            Spacer(Modifier.height(12.dp))
        }
        if (state.viewMode == FilesViewMode.LIST) {
            ManageFilesSortBar(
                selected = state.sortOption,
                onSelected = { state.sortOption = it }
            )
            Spacer(Modifier.height(12.dp))
        }

        LaunchedEffect(state.fileFilter, state.viewMode) {
            if (state.viewMode == FilesViewMode.LIST) {
                val (index, offset) = state.listScrollAnchorForCurrentFilter()
                runCatching {
                    state.listModeState.scrollToItem(index = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0))
                }.onFailure {
                    state.listModeState.scrollToItem(index = 0, scrollOffset = 0)
                }
            } else {
                val (index, offset) = state.explorerScrollAnchorForCurrentFilter()
                runCatching {
                    state.explorerModeState.scrollToItem(index = index.coerceAtLeast(0), scrollOffset = offset.coerceAtLeast(0))
                }.onFailure {
                    state.explorerModeState.scrollToItem(index = 0, scrollOffset = 0)
                }
            }
        }

        val showTopListFade by remember(state.viewMode, state.listModeState, state.explorerModeState) {
            derivedStateOf {
                val activeListState = if (state.viewMode == FilesViewMode.LIST) {
                    state.listModeState
                } else {
                    state.explorerModeState
                }
                activeListState.canScrollBackward
            }
        }
        val showBottomListFade by remember(state.viewMode, state.listModeState, state.explorerModeState) {
            derivedStateOf {
                val activeListState = if (state.viewMode == FilesViewMode.LIST) {
                    state.listModeState
                } else {
                    state.explorerModeState
                }
                activeListState.canScrollForward
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.viewMode == FilesViewMode.LIST) {
                ManageFilesListModeContent(
                    modifier = Modifier.fillMaxSize(),
                    prep = prep,
                    activeFilter = state.fileFilter,
                    sortOption = state.sortOption,
                    queryNorm = queryNorm,
                    listModeState = state.listModeState,
                    checks = state.checks,
                    selectableIndices = actionSelectableIndices.toSet(),
                    onChecksChange = { state.checks = it },
                    fileMatches = callbacks.fileMatches,
                    onLog = callbacks.onLog,
                    onRemoveCandidate = { candidate -> state.removeCandidate = candidate },
                    onRetryDownload = { retryIdx ->
                        if (retryIdx in prep.selectableIndices && retryIdx in state.checks.indices) {
                            val updated = List(state.checks.size) { idx -> idx == retryIdx }
                            state.checks = updated
                            onRequestSaveSelection()
                        }
                    },
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onCancelDownload = onCancelDownload,
                    onAlsoDeleteReset = { state.alsoDelete = false }
                )
            } else {
                ManageFilesExplorerModeContent(
                    modifier = Modifier.fillMaxSize(),
                    prep = prep,
                    activeFilter = state.fileFilter,
                    tree = tree,
                    expandedDirs = state.expandedDirs,
                    onExpandedDirsChange = { state.expandedDirs = it },
                    queryNorm = queryNorm,
                    explorerModeState = state.explorerModeState,
                    checks = state.checks,
                    selectableIndices = actionSelectableIndices.toSet(),
                    onChecksChange = { state.checks = it },
                    fileMatches = callbacks.fileMatches,
                    allDescendantIndices = callbacks.allDescendantIndices,
                    folderToggleState = callbacks.folderToggleState,
                    applyFolderSelection = callbacks.applyFolderSelection,
                    onLog = callbacks.onLog,
                    onRemoveCandidate = { candidate -> state.removeCandidate = candidate },
                    onRetryDownload = { retryIdx ->
                        if (retryIdx in prep.selectableIndices && retryIdx in state.checks.indices) {
                            val updated = List(state.checks.size) { idx -> idx == retryIdx }
                            state.checks = updated
                            onRequestSaveSelection()
                        }
                    },
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onCancelDownload = onCancelDownload,
                    onAlsoDeleteReset = { state.alsoDelete = false }
                )
            }

            if (showTopListFade) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(16.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            if (showBottomListFade) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        val allOn = areAllSelectableChecked(
            checks = state.checks,
            selectableIndices = actionSelectableIndices
        )
        val selectedForActionCount = selectedSelectableCount(
            checks = state.checks,
            selectableIndices = actionSelectableIndices
        )
        val selectedSizeBytes = selectedSelectableSizeBytes(
            checks = state.checks,
            selectableIndices = actionSelectableIndices,
            sizesByIndex = prep.ordered.map { it.size }
        )
        val selectedSizeLabel = humanReadableSize(selectedSizeBytes)
        state.selectableForSave = actionSelectableIndices

        val primaryLabel = when (filterAction) {
            ManageFilesFilterAction.ENQUEUE -> stringResource(R.string.download_count, selectedForActionCount)
            ManageFilesFilterAction.PAUSE -> stringResource(R.string.manage_action_pause_count, selectedForActionCount)
            ManageFilesFilterAction.RESUME -> stringResource(R.string.manage_action_resume_count, selectedForActionCount)
            ManageFilesFilterAction.REMOVE_COMPLETED -> stringResource(R.string.manage_action_remove_list_count, selectedForActionCount)
            null -> ""
        }
        val primaryIcon: ImageVector = when (filterAction) {
            ManageFilesFilterAction.ENQUEUE -> Icons.Filled.Download
            ManageFilesFilterAction.PAUSE -> Icons.Filled.Pause
            ManageFilesFilterAction.RESUME -> Icons.Filled.PlayArrow
            ManageFilesFilterAction.REMOVE_COMPLETED -> Icons.Filled.DeleteSweep
            null -> Icons.Filled.Download
        }

        if (filterAction != null) {
            ManageFilesBottomActionBar(
                availableCount = actionSelectableIndices.size,
                selectedCount = selectedForActionCount,
                selectedSizeLabel = selectedSizeLabel,
                allOn = allOn,
                primaryEnabled = selectedForActionCount > 0,
                primaryLabel = primaryLabel,
                primaryIcon = primaryIcon,
                showDeleteToggle = filterAction == ManageFilesFilterAction.REMOVE_COMPLETED,
                alsoDelete = state.alsoDelete,
                onAlsoDeleteChange = { state.alsoDelete = it },
                onToggleAll = {
                    val updated = toggleAllSelectable(
                        checks = state.checks,
                        selectableIndices = actionSelectableIndices
                    )
                    if (updated !== state.checks) {
                        state.checks = updated
                        callbacks.onLog("SelectAll toggled action=$filterAction")
                    }
                },
                onPrimaryAction = {
                    val selectedTaskIds = actionSelectableIndices
                        .filter { idx -> state.checks.getOrNull(idx) == true }
                        .mapNotNull { idx -> prep.ordered.getOrNull(idx)?.taskId }
                        .distinct()
                    when (filterAction) {
                        ManageFilesFilterAction.ENQUEUE -> onRequestSaveSelection()
                        ManageFilesFilterAction.PAUSE -> {
                            selectedTaskIds.forEach(onPauseDownload)
                            if (selectedTaskIds.isNotEmpty()) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.manage_bulk_paused_count,
                                            selectedTaskIds.size
                                        ),
                                        actionLabel = context.getString(R.string.undo_title),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        selectedTaskIds.forEach(onResumeDownload)
                                    }
                                }
                            }
                        }
                        ManageFilesFilterAction.RESUME -> {
                            selectedTaskIds.forEach(onResumeDownload)
                            if (selectedTaskIds.isNotEmpty()) {
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = context.getString(
                                            R.string.manage_bulk_resumed_count,
                                            selectedTaskIds.size
                                        ),
                                        actionLabel = context.getString(R.string.undo_title),
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        selectedTaskIds.forEach(onPauseDownload)
                                    }
                                }
                            }
                        }
                        ManageFilesFilterAction.REMOVE_COMPLETED -> {
                            val candidates = removeCandidatesForCheckedCompleted(
                                prep = prep,
                                checks = state.checks,
                                candidateIndices = actionSelectableIndices
                            )
                            if (candidates.isNotEmpty()) {
                                state.bulkRemoveCandidates = candidates
                            }
                        }
                    }
                }
            )
        }

        val hasFilteredItems = remember(prep, queryNorm, state.fileFilter) {
            prep.ordered.any { callbacks.fileMatches(it) }
        }
        if (
            filterScope == ManageFilesFilterScope.FULL &&
            !hasFilteredItems
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.manage_files_empty_filtered),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (state.fileQuery.isNotBlank()) {
                    OutlinedButton(
                        onClick = { state.fileQuery = "" },
                        shape = RoundedCornerShape(MF_RADIUS_ITEM)
                    ) {
                        Text(stringResource(R.string.reset_title))
                    }
                } else if (state.fileFilter != ManageFilesFilter.ALL) {
                    OutlinedButton(
                        onClick = { state.onFilterSelected(ManageFilesFilter.ALL) },
                        shape = RoundedCornerShape(MF_RADIUS_ITEM)
                    ) {
                        Text(stringResource(R.string.show_all_filter))
                    }
                }
            }
        } else if (
            filterScope == ManageFilesFilterScope.FULL &&
            filterAction == ManageFilesFilterAction.ENQUEUE &&
            actionSelectableIndices.isEmpty()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.no_available_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (
                    state.fileFilter == ManageFilesFilter.DOWNLOADABLE &&
                    filterStats.downloaded > 0
                ) {
                    OutlinedButton(
                        onClick = { state.onFilterSelected(ManageFilesFilter.DOWNLOADED) },
                        shape = RoundedCornerShape(MF_RADIUS_ITEM)
                    ) {
                        Text(stringResource(R.string.show_downloaded_filter))
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageFilesDialogsHost(
    state: ManageFilesState,
    prep: PreparedFilesDl,
    onRemoveCompletedEntry: (taskId: String, alsoDeleteLocal: Boolean, contentUri: String?) -> Unit,
    onDispatchQueueActionAndHide: (QueueSelectionAction?) -> Unit,
    onSaveDefaultSaveLocation: (SaveOption, String?) -> Unit,
    onChooseFolder: (rememberChoice: Boolean) -> Unit
) {
    val candidate = state.removeCandidate
    if (candidate != null) {
        RemoveEntryDialog(
            candidate = candidate,
            alsoDelete = state.alsoDelete,
            onAlsoDeleteChange = { state.alsoDelete = it },
            onConfirm = {
                val action = buildRemoveCompletedAction(
                    candidate = candidate,
                    alsoDeleteLocal = state.alsoDelete
                )
                onRemoveCompletedEntry(action.taskId, action.alsoDeleteLocal, action.contentUri)
                state.removeCandidate = null
                state.alsoDelete = false
            },
            onDismiss = { state.removeCandidate = null }
        )
    }

    val bulkCandidates = state.bulkRemoveCandidates
    if (bulkCandidates.isNotEmpty()) {
        RemoveEntriesDialog(
            count = bulkCandidates.size,
            alsoDelete = state.alsoDelete,
            onAlsoDeleteChange = { state.alsoDelete = it },
            onConfirm = {
                val candidateTaskIds = bulkCandidates.mapTo(mutableSetOf()) { it.taskId }
                bulkCandidates.forEach { candidateItem ->
                    onRemoveCompletedEntry(
                        candidateItem.taskId,
                        state.alsoDelete,
                        candidateItem.contentUri
                    )
                }
                if (candidateTaskIds.isNotEmpty()) {
                    state.checks = state.checks.mapIndexed { idx, isChecked ->
                        val taskId = prep.ordered.getOrNull(idx)?.taskId
                        if (isChecked && taskId != null && taskId in candidateTaskIds) false else isChecked
                    }
                }
                state.bulkRemoveCandidates = emptyList()
                state.alsoDelete = false
            },
            onDismiss = { state.bulkRemoveCandidates = emptyList() }
        )
    }

    if (state.showSaveDialog) {
        SaveDestinationDialog(
            onDismiss = { state.showSaveDialog = false },
            onIntoMedia = { rememberChoice ->
                state.showSaveDialog = false
                if (rememberChoice) {
                    onSaveDefaultSaveLocation(SaveOption.MUSIC_LIBRARY, null)
                }
                onDispatchQueueActionAndHide(buildMusicLibraryQueueAction(state))
            },
            onChooseFolder = onChooseFolder
        )
    }
}

private fun buildCustomFolderQueueAction(
    state: ManageFilesState,
    uri: Uri
): QueueSelectionAction? = buildQueueSelectionAction(
    orderedForSave = state.orderedForSave,
    selectableForSave = state.selectableForSave,
    checks = state.checks,
    saveOption = SaveOption.CUSTOM_FOLDER,
    customUri = uri.toString()
)

private fun buildMusicLibraryQueueAction(
    state: ManageFilesState
): QueueSelectionAction? = buildQueueSelectionAction(
    orderedForSave = state.orderedForSave,
    selectableForSave = state.selectableForSave,
    checks = state.checks,
    saveOption = SaveOption.MUSIC_LIBRARY,
    customUri = null
)
