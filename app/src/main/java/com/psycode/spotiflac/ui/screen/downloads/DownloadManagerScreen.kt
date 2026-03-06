package com.psycode.spotiflac.ui.screen.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.data.service.download.service.DownloadServiceRouter
import com.psycode.spotiflac.ui.common.SaveLocationViewModel
import com.psycode.spotiflac.ui.component.AppCardTopBar
import com.psycode.spotiflac.ui.component.AppCardTopBarIconButton
import com.psycode.spotiflac.ui.component.ManageFilesBottomSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val DOWNLOAD_CHANNEL_ID = "download_channel"
private const val TASK_OP_UI_TTL_MS = 10_000L

data class TaskGroup(
    val title: String,
    val tasks: List<DownloadTask>,
    val date: LocalDate,
    val latestTs: Long,
    val topicId: Int?
)

data class DateSection(
    val date: LocalDate,
    val groups: List<TaskGroup>,
    val dateLabel: String
)

private data class DownloadManagerScreenCallbacks(
    val onOpenManageFiles: (Int, String) -> Unit,
    val onRequestDeleteGroup: (Int, String) -> Unit,
    val onToggleGroupTransfer: (Int, GroupTransferAction) -> Unit,
    val onPauseTask: (String) -> Unit,
    val onResumeTask: (String) -> Unit,
    val onCancelTask: (String) -> Unit,
    val onOpenFile: (String) -> Unit,
    val onAddSelectedToQueue: (files: List<TorrentFile>, saveOption: SaveOption, folderUri: String?) -> Unit,
    val onHideManageFiles: () -> Unit,
    val onRemoveCompletedEntry: (taskId: String, alsoDeleteLocal: Boolean, contentUri: String?) -> Unit,
    val onCancelDeleteGroup: () -> Unit,
    val onSetDeleteGroupAlsoDeleteLocalFiles: (Boolean) -> Unit,
    val onConfirmDeleteGroup: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DownloadManagerScreen(
    onBack: () -> Unit,
    onOpenTorrentSearch: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    viewModel: DownloadManagerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val saveLocationViewModel: SaveLocationViewModel = hiltViewModel()
    val defaultSaveLocation by saveLocationViewModel.defaultSaveLocation.collectAsState()
    val tasks = uiState.tasks
    val lifecycleOwner = LocalLifecycleOwner.current
    val renderedTasks = rememberDebouncedDownloadTasks(tasks)
    var serviceEnsuredThisSession by remember { mutableStateOf(false) }

    LaunchedEffect(tasks) {
        if (shouldEnsureDownloadService(tasks, serviceEnsuredThisSession)) {
            DownloadServiceRouter.ensureStarted(context)
            serviceEnsuredThisSession = true
        }
    }

    val latestTasks by rememberUpdatedState(tasks)
    val latestContext by rememberUpdatedState(context)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                val hasActive = latestTasks.any {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED
                }
                if (hasActive) {
                    DownloadServiceRouter.ensureStarted(latestContext)
                    DownloadServiceRouter.dispatchAppForeground(latestContext)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)

    val dateSections by remember(renderedTasks, todayLabel, yesterdayLabel) {
        derivedStateOf { buildDateSections(renderedTasks, todayLabel, yesterdayLabel) }
    }

    EnsureNotificationsPermission()
    var channelDisabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { channelDisabled = isChannelDisabled(context, DOWNLOAD_CHANNEL_ID) }

    val manageUi = uiState.manageFilesState
    val currentTopicId = uiState.currentTopicId
    val deleteDialog = uiState.deleteDialog
    val groupControls = uiState.groupControls
    val fileOp = uiState.fileOp
    var fileOpBannerMessage by remember { mutableStateOf<String?>(null) }
    var fileOpBannerIsError by remember { mutableStateOf(false) }

    LaunchedEffect(fileOp) {
        val message = resolveFileOpMessage(fileOp) { resId -> context.getString(resId) }
        if (message != null) {
            fileOpBannerIsError = fileOp is DownloadManagerViewModel.FileOpUiState.Error
            fileOpBannerMessage = message
            delay(2400)
            fileOpBannerMessage = null
            viewModel.onAction(DownloadManagerAction.ResetFileOpState)
        }
    }

    val outline = MaterialTheme.colorScheme.outlineVariant
    val opInFlight = remember { mutableStateMapOf<String, PendingOp>() }
    val opStartedAt = remember { mutableStateMapOf<String, Long>() }
    val callbacks = remember(
        viewModel,
        context,
        snackbar,
        scope,
        opInFlight,
        opStartedAt,
        groupControls
    ) {
        DownloadManagerScreenCallbacks(
            onOpenManageFiles = { topicId, torrentTitle ->
                viewModel.onAction(DownloadManagerAction.OpenManageFiles(topicId, torrentTitle))
            },
            onRequestDeleteGroup = { topicId, title ->
                viewModel.onAction(DownloadManagerAction.RequestDeleteGroup(topicId, title))
            },
            onToggleGroupTransfer = { topicId, action ->
                when (action) {
                    GroupTransferAction.PAUSE -> {
                        viewModel.onAction(DownloadManagerAction.PauseGroup(topicId))
                    }
                    GroupTransferAction.RESUME -> {
                        viewModel.onAction(DownloadManagerAction.ResumeGroup(topicId))
                    }
                }
            },
            onPauseTask = { id ->
                val topicId = parseTopicIdFromTaskId(id)
                val lockUntil = topicId?.let { groupControls[it]?.lockedUntilMs } ?: 0L
                if (lockUntil > System.currentTimeMillis()) return@DownloadManagerScreenCallbacks
                opInFlight[id] = PendingOp.PAUSING
                opStartedAt[id] = System.currentTimeMillis()
                scope.launch {
                    delay(TASK_OP_UI_TTL_MS)
                    if (opInFlight[id] == PendingOp.PAUSING) {
                        opInFlight.remove(id)
                        opStartedAt.remove(id)
                    }
                }
                viewModel.onAction(DownloadManagerAction.Pause(id))
            },
            onResumeTask = { id ->
                val topicId = parseTopicIdFromTaskId(id)
                val lockUntil = topicId?.let { groupControls[it]?.lockedUntilMs } ?: 0L
                if (lockUntil > System.currentTimeMillis()) return@DownloadManagerScreenCallbacks
                opInFlight[id] = PendingOp.RESUMING
                opStartedAt[id] = System.currentTimeMillis()
                scope.launch {
                    delay(TASK_OP_UI_TTL_MS)
                    if (opInFlight[id] == PendingOp.RESUMING) {
                        opInFlight.remove(id)
                        opStartedAt.remove(id)
                    }
                }
                viewModel.onAction(DownloadManagerAction.Resume(id))
            },
            onCancelTask = { id ->
                val topicId = parseTopicIdFromTaskId(id)
                val lockUntil = topicId?.let { groupControls[it]?.lockedUntilMs } ?: 0L
                if (lockUntil > System.currentTimeMillis()) return@DownloadManagerScreenCallbacks
                opInFlight[id] = PendingOp.CANCELING
                opStartedAt[id] = System.currentTimeMillis()
                scope.launch {
                    delay(TASK_OP_UI_TTL_MS)
                    if (opInFlight[id] == PendingOp.CANCELING) {
                        opInFlight.remove(id)
                        opStartedAt.remove(id)
                    }
                }
                viewModel.onAction(DownloadManagerAction.Cancel(id))
            },
            onOpenFile = { uri ->
                if (!safeOpenUri(context, uri)) {
                    scope.launch {
                        snackbar.showSnackbar(
                            message = context.getString(R.string.cannot_open_file_error),
                            withDismissAction = true
                        )
                    }
                }
            },
            onAddSelectedToQueue = { files, saveOption, folderUri ->
                viewModel.onAction(
                    DownloadManagerAction.AddSelectedToQueue(
                        files = files,
                        saveOption = saveOption,
                        folderUri = folderUri
                    )
                )
            },
            onHideManageFiles = {
                viewModel.onAction(DownloadManagerAction.HideManageFiles)
            },
            onRemoveCompletedEntry = { taskId, alsoDeleteLocal, contentUri ->
                viewModel.onAction(
                    DownloadManagerAction.RemoveCompletedEntry(
                        taskId = taskId,
                        alsoDeleteLocal = alsoDeleteLocal,
                        contentUri = contentUri
                    )
                )
            },
            onCancelDeleteGroup = {
                viewModel.onAction(DownloadManagerAction.CancelDeleteGroup)
            },
            onSetDeleteGroupAlsoDeleteLocalFiles = { value ->
                viewModel.onAction(DownloadManagerAction.SetDeleteGroupAlsoDeleteLocalFiles(value))
            },
            onConfirmDeleteGroup = {
                viewModel.onAction(DownloadManagerAction.ConfirmDeleteGroup)
            }
        )
    }
    val refreshChannelDisabled = remember(context) {
        { channelDisabled = isChannelDisabled(context, DOWNLOAD_CHANNEL_ID) }
    }
    val openDownloadNotificationsSettings = remember(context) {
        { openDownloadChannelSettings(context, DOWNLOAD_CHANNEL_ID) }
    }

    LaunchedEffect(tasks) {
        val reconciled = reconcilePendingOps(current = opInFlight, tasks = tasks)
        if (reconciled != opInFlight) {
            opInFlight.clear()
            opInFlight.putAll(reconciled)
        }
        val now = System.currentTimeMillis()
        val staleOps = opStartedAt.filter { (taskId, startedAt) ->
            (now - startedAt) > TASK_OP_UI_TTL_MS || opInFlight[taskId] == null
        }.keys.toList()
        staleOps.forEach { taskId ->
            opStartedAt.remove(taskId)
            opInFlight.remove(taskId)
        }

    }

    Scaffold(
        topBar = {
            DownloadManagerTopBar(
                onBack = onBack,
                onOpenSettings = onOpenSettings
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { paddingValues ->
        if (renderedTasks.isEmpty()) {
            DownloadManagerEmptyHero(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onOpenTorrentSearch = onOpenTorrentSearch
            )
        } else {
            var collapsedDates by rememberSaveable { mutableStateOf(setOf<String>()) }
            val showTopListFade by remember(listState) {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                }
            }
            val showBottomListFade by remember(listState) {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val totalItems = layoutInfo.totalItemsCount
                    if (totalItems == 0) {
                        false
                    } else {
                        val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        lastVisibleIndex < totalItems - 1
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (fileOpBannerMessage != null) {
                        item(key = "file-op-banner") {
                            FileOperationBanner(
                                visible = true,
                                message = fileOpBannerMessage.orEmpty(),
                                isError = fileOpBannerIsError
                            )
                        }
                    }

                    if (channelDisabled) {
                        item(key = "notif-warning") {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
                                exit = fadeOut()
                            ) {
                                NotificationChannelBanner(
                                    onOpenSettings = openDownloadNotificationsSettings,
                                    onRefresh = refreshChannelDisabled
                                )
                            }
                        }
                    }

                    dateSections.forEach { section ->
                        val dateKey = section.date.toString()
                        val isCollapsed = collapsedDates.contains(dateKey)

                        item(key = buildDateHeaderItemKey(dateKey)) {
                            DateSectionHeader(
                                dateLabel = section.dateLabel,
                                groupsCount = section.groups.size,
                                isCollapsed = isCollapsed,
                                onToggle = {
                                    collapsedDates = toggleCollapsedDate(
                                        collapsedDates = collapsedDates,
                                        dateKey = dateKey
                                    )
                                },
                                outline = outline
                            )
                        }

                        section.groups.forEach { group ->
                            item(key = buildGroupItemKey(dateKey = dateKey, groupTitle = group.title)) {
                                AnimatedVisibility(
                                    visible = !isCollapsed,
                                    enter = fadeIn(animationSpec = tween(220)),
                                    exit = fadeOut(animationSpec = tween(120))
                                ) {
                                    TaskGroupCard(
                                        group = group,
                                        outline = outline,
                                        opInFlight = opInFlight,
                                        onManageFiles = { topicId ->
                                            callbacks.onOpenManageFiles(topicId, group.title)
                                        },
                                        onDeleteGroup = { topicId ->
                                            callbacks.onRequestDeleteGroup(topicId, group.title)
                                        },
                                        onToggleGroupTransfer = callbacks.onToggleGroupTransfer,
                                        groupControl = group.topicId?.let { topicId -> groupControls[topicId] },
                                        interactionsLocked = group.topicId?.let { topicId ->
                                            (groupControls[topicId]?.lockedUntilMs ?: 0L) > System.currentTimeMillis()
                                        } == true,
                                        onPauseTask = callbacks.onPauseTask,
                                        onResumeTask = callbacks.onResumeTask,
                                        onCancelTask = callbacks.onCancelTask,
                                        onOpenFile = callbacks.onOpenFile
                                    )
                                }
                            }
                        }
                    }
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
                                        MaterialTheme.colorScheme.background,
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
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                }
            }
        }
    }

    ManageFilesBottomSheet(
        visible = manageUi !is ManageFilesUiState.Hidden,
        uiState = manageUi,
        tasks = tasks,
        topicId = currentTopicId,
        groupTitle = uiState.currentTitle,
        onAddToQueue = callbacks.onAddSelectedToQueue,
        onDismissRequest = callbacks.onHideManageFiles,
        onRetryLoadTorrentFiles = {
            val topicId = currentTopicId ?: return@ManageFilesBottomSheet
            callbacks.onOpenManageFiles(topicId, uiState.currentTitle)
        },
        onPauseDownload = callbacks.onPauseTask,
        onResumeDownload = callbacks.onResumeTask,
        onCancelDownload = callbacks.onCancelTask,
        onRemoveCompletedEntry = callbacks.onRemoveCompletedEntry,
        defaultSaveLocation = defaultSaveLocation,
        onSaveDefaultSaveLocation = { saveOption, customFolderUri ->
            saveLocationViewModel.persistDefault(saveOption, customFolderUri)
        }
    )

    DeleteGroupDialog(
        deleteDialog = deleteDialog,
        onCancel = callbacks.onCancelDeleteGroup,
        onAlsoDeleteLocalFilesChange = callbacks.onSetDeleteGroupAlsoDeleteLocalFiles,
        onConfirm = callbacks.onConfirmDeleteGroup
    )
}

@Composable
private fun DownloadManagerEmptyHero(
    modifier: Modifier = Modifier,
    onOpenTorrentSearch: (() -> Unit)?
) {
    val pulse = rememberInfiniteTransition(label = "downloads-empty-hero")
    val iconScale by pulse.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "downloads-empty-hero-scale"
    )
    val containerShape = RoundedCornerShape(24.dp)
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Box(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = containerShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            border = BorderStroke(0.5.dp, outline),
            shadowElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(84.dp),
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
                            .size(60.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((30 * iconScale).dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.no_active_downloads),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.downloads_empty_hero_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { onOpenTorrentSearch?.invoke() },
                    enabled = onOpenTorrentSearch != null,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_torrents_cta))
                }
            }
        }
    }
}

@Composable
private fun DownloadManagerTopBar(
    onBack: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    AppCardTopBar(
        title = stringResource(R.string.download_manager_screen_title),
        startAction = {
            AppCardTopBarIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                onClick = onBack
            )
        },
        endAction = {
            AppCardTopBarIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title),
                onClick = { onOpenSettings?.invoke() },
                enabled = onOpenSettings != null
            )
        }
    )
}




