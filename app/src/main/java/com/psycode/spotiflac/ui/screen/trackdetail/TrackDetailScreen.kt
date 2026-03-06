package com.psycode.spotiflac.ui.screen.trackdetail

import com.psycode.spotiflac.ui.component.ManageFilesAutoMatch
import com.psycode.spotiflac.ui.component.ManageFilesBottomSheet
import com.psycode.spotiflac.ui.component.ManageFilesFilterScope
import com.psycode.spotiflac.ui.component.AppCardTopBar
import com.psycode.spotiflac.ui.component.AppCardTopBarIconButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.ui.common.SaveLocationViewModel
import com.psycode.spotiflac.ui.common.SystemBarOverride
import com.psycode.spotiflac.ui.common.SystemBarOverrideStore
import com.psycode.spotiflac.ui.component.DownloadsBottomBar
import com.psycode.spotiflac.ui.screen.downloads.DownloadManagerViewModel
import com.psycode.spotiflac.ui.screen.downloads.DownloadManagerAction
import com.psycode.spotiflac.ui.screen.downloads.countActiveRunningDownloads
import com.psycode.spotiflac.ui.screen.downloads.countQueuedDownloads
import com.psycode.spotiflac.ui.screen.downloads.ManageFilesUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackDetailScreen(
    trackId: String,
    viewModel: TrackDetailViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToDownloads: (topicId: Int, torrentTitle: String) -> Unit,
    manualMode: Boolean = false,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    LaunchedEffect(trackId, manualMode) {
        if (!manualMode) viewModel.onAction(TrackDetailAction.LoadTrack(trackId))
    }

    val uiState by viewModel.uiState.collectAsState()
    val trackState = uiState.trackState
    val torrents = uiState.torrentState
    val lossless = uiState.losslessSelected
    val captcha = uiState.captchaState
    val submitting = uiState.isCaptchaSubmitting
    val filesState = uiState.torrentFilesState
    val manualQuery = uiState.manualQuery
    val manualSearchHistory = uiState.manualSearchHistory

    BackHandler(enabled = manualMode) { }

    
    var sheetVisible by remember { mutableStateOf(false) }
    var activeTopicId by remember { mutableStateOf<Int?>(null) }
    var pendingTitle by remember { mutableStateOf("") }

    
    var showManualDialog by rememberSaveable(trackId, manualMode) { mutableStateOf(false) }
    var manualArtistInput by rememberSaveable(trackId, manualMode) { mutableStateOf("") }
    var manualTitleInput by rememberSaveable(trackId, manualMode) { mutableStateOf("") }

    var expandSignal by rememberSaveable(trackId, manualMode) { mutableIntStateOf(0) }

    
    var stickyTrack by remember { mutableStateOf<TrackDetail?>(null) }
    val currentTrack = (trackState as? TrackDetailUiState.Success)?.track

    LaunchedEffect(currentTrack, manualMode) {
        if (!manualMode && currentTrack != null) {
            stickyTrack = currentTrack
            if (manualQuery == null && manualArtistInput.isBlank() && manualTitleInput.isBlank()) {
                manualArtistInput = currentTrack.artist
                manualTitleInput = currentTrack.title
            }
        } else if (manualMode) {
            stickyTrack = null
        }
    }

    LaunchedEffect(manualQuery, manualMode) {
        if (manualMode) return@LaunchedEffect
        manualQuery?.let { (a, t) ->
            manualArtistInput = a
            manualTitleInput = t
        }
        if (manualQuery == null && stickyTrack != null) {
            manualArtistInput = stickyTrack!!.artist
            manualTitleInput = stickyTrack!!.title
        }
    }

    var showTorrentsSection by rememberSaveable(trackId, manualMode) { mutableStateOf(false) }
    LaunchedEffect(stickyTrack, manualMode) {
        if (manualMode) {
            showTorrentsSection = true
        } else {
            if (stickyTrack != null) {
                showTorrentsSection = false
                delay(220)
                showTorrentsSection = true
            } else {
                showTorrentsSection = false
            }
        }
    }

    val hasSearchInput = remember(manualMode, manualQuery) {
        if (!manualMode) {
            true
        } else {
            manualQuery?.first?.trim().orEmpty().isNotBlank()
        }
    }
    val torrentItems = (torrents as? TorrentUiState.Success)?.results.orEmpty()
    val actuallyLoading = torrents is TorrentUiState.Loading && (!manualMode || hasSearchInput)
    val torrentErrorReason = (torrents as? TorrentUiState.Error)?.reason
    val showRetryButton = !actuallyLoading && torrentItems.isEmpty() && hasSearchInput
    val showManualSetupState = manualMode &&
        !hasSearchInput &&
        !actuallyLoading &&
        torrentItems.isEmpty() &&
        captcha !is CaptchaState.Required
    var previousTorrentState by remember(trackId, manualMode) {
        mutableStateOf<TorrentUiState?>(null)
    }
    LaunchedEffect(torrents, manualMode) {
        if (!manualMode) {
            val becameSuccessful = torrents is TorrentUiState.Success && torrents.results.isNotEmpty()
            val wasLoading = previousTorrentState is TorrentUiState.Loading
            if (becameSuccessful && wasLoading) {
                expandSignal++
            }
        }
        previousTorrentState = torrents
    }

    val onRetryClick: () -> Unit = remember(
        trackId, manualMode, manualQuery, manualArtistInput, manualTitleInput
    ) {
        {
            if (!manualMode) {
                viewModel.onAction(TrackDetailAction.LoadTrack(trackId))
            } else {
                val (a, t) = manualQuery ?: (manualArtistInput to manualTitleInput)
                if (a.isNotBlank()) {
                    viewModel.onAction(TrackDetailAction.SearchTorrentsManual(a, t))
                    expandSignal++
                } else {
                    showManualDialog = true
                }
            }
        }
    }

    val listState = rememberLazyListState()
    var viewportBottomPx by remember { mutableIntStateOf(0) }

    val hasTrackCard = !manualMode && stickyTrack != null && manualQuery == null
    val listBottomPadding = 16.dp

    
    val downloadVm: DownloadManagerViewModel = hiltViewModel()
    val downloadUiState by downloadVm.uiState.collectAsState()
    val saveLocationViewModel: SaveLocationViewModel = hiltViewModel()
    val defaultSaveLocation by saveLocationViewModel.defaultSaveLocation.collectAsState()
    val tasks = downloadUiState.tasks
    val activeCount = remember(tasks) { countActiveRunningDownloads(tasks) }
    val queuedCount = remember(tasks) { countQueuedDownloads(tasks) }

    
    val manageFilesState: ManageFilesUiState = remember(filesState) {
        mapTorrentFilesUiStateToManageFilesUiState(filesState)
    }

    val topBarTitle = remember(manualMode, manualQuery, stickyTrack) {
        when {
            manualMode -> null
            manualQuery != null -> {
                val artist = manualQuery.first.trim()
                val title = manualQuery.second.trim()
                when {
                    artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
                    artist.isNotBlank() -> artist
                    title.isNotBlank() -> title
                    else -> stickyTrack?.title.orEmpty()
                }
            }
            else -> stickyTrack?.title.orEmpty()
        }
    }
    val dismissManualParams: () -> Unit = {
        showManualDialog = false
        if (manualMode && manualQuery == null) {
            viewModel.onAction(TrackDetailAction.ClearManualOverrides)
        }
    }

    val manualTopBarMode = if (showManualDialog) {
        ManualTopBarMode.Params
    } else {
        ManualTopBarMode.Search
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (manualMode) {
                ManualModeTopBar(
                    mode = manualTopBarMode,
                    onLogout = { onLogout?.invoke() },
                    onCloseParams = dismissManualParams,
                    onOpenSettings = onOpenSettings
                )
            } else {
                AppCardTopBar(
                    title = topBarTitle.orEmpty(),
                    subtitle = stickyTrack
                        ?.artist
                        ?.takeIf { manualQuery == null && it.isNotBlank() },
                    startAction = {
                        AppCardTopBarIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pagination_back),
                            onClick = onBack
                        )
                    }
                )
            }
        },
        bottomBar = {
            if (onOpenDownloads != null && !showManualDialog) {
                DownloadsBottomBar(
                    activeCount = activeCount,
                    queuedCount = queuedCount,
                    onClick = onOpenDownloads
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
            if (showManualSetupState) {
                ManualSearchSetupState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    onOpenParams = { showManualDialog = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coords ->
                            viewportBottomPx = coords.boundsInWindow().bottom.roundToInt()
                        },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = listBottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                if (hasTrackCard) {
                    item(key = "track_card") {
                        val t = stickyTrack!!
                        val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)
                        val cardShape = RoundedCornerShape(18.dp)
                        val coverShape = RoundedCornerShape(14.dp)
                        val albumChipShape = RoundedCornerShape(10.dp)
                        val albumChipBorder = BorderStroke(0.5.dp, outline.copy(alpha = 0.6f))

                        Card(
                            Modifier.fillMaxWidth(),
                            shape = cardShape,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(0.5.dp, outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(coverShape)
                                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                ) {
                                    AsyncImage(
                                        model = t.albumCoverUrl,
                                        contentDescription = t.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = t.title,
                                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = t.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = albumChipShape,
                                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                        border = albumChipBorder
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Album,
                                                contentDescription = null,
                                                modifier = Modifier.size(13.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = t.albumName,
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.widthIn(max = 210.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "torrents_section") {
                    AnimatedVisibility(
                        visible = showTorrentsSection,
                        enter = fadeIn(tween(250, delayMillis = if (manualMode) 0 else 100)) +
                                slideInVertically(tween(250, delayMillis = if (manualMode) 0 else 100)) { it / 3 }
                    ) {
                        ExpandableSection(
                            title = stringResource(R.string.torrent_results_title),
                            isLoading = torrents is TorrentUiState.Loading,
                            items = (torrents as? TorrentUiState.Success)?.results.orEmpty(),
                            losslessSelected = lossless,
                            onFilterChange = { viewModel.onAction(TrackDetailAction.SetLosslessFilter(it)) },
                            captchaState = captcha,
                            isCaptchaSubmitting = submitting,
                            onSubmitCaptcha = { viewModel.onAction(TrackDetailAction.SubmitCaptcha(it)) },
                            onTorrentClick = { result ->
                                activeTopicId = result.topicId
                                pendingTitle = result.title
                                sheetVisible = true
                                viewModel.onAction(TrackDetailAction.LoadTorrentFiles(result.topicId))
                            },
                            emptyContent = {
                                TorrentSearchStateInline(
                                    errorReason = torrentErrorReason,
                                    isLossless = lossless,
                                    hasSearchInput = hasSearchInput,
                                    showRetry = showRetryButton,
                                    onRetry = onRetryClick,
                                    onOpenSearchParams = { showManualDialog = true }
                                )
                            },
                            statusTextOverride = if (manualMode && !hasSearchInput) {
                                stringResource(R.string.search_params_short)
                            } else {
                                null
                            },
                            headerAction = {
                                OutlinedButton(
                                    onClick = { showManualDialog = true },
                                    modifier = Modifier.height(34.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                                    ),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.search_params_short),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            manualArtist = manualQuery?.first?.takeIf { it.isNotBlank() },
                            manualTitle = manualQuery?.second?.takeIf { it.isNotBlank() },
                            onManualChipClick = { showManualDialog = true },
                            maxItemsPerPage = 18,
                            parentListState = listState,
                            parentItemIndex = if (hasTrackCard) 1 else 0,
                            parentViewportBottomPx = viewportBottomPx,
                            expandSignal = expandSignal,
                            onExpandRequest = {
                                if (!manualMode) return@ExpandableSection true
                                val hasArtist = manualArtistInput.trim().isNotEmpty() || (manualQuery?.first?.isNotBlank() == true)
                                if (!hasArtist) {
                                    showManualDialog = true
                                    false
                                } else true
                            }
                        )
                    }
                }
            }
            }

            
            val (effectiveArtist, effectiveTitle) = remember(
                stickyTrack,
                manualMode,
                manualQuery,
                manualArtistInput,
                manualTitleInput
            ) {
                resolveAutoMatchInputs(
                    stickyTrack = stickyTrack,
                    manualMode = manualMode,
                    manualQuery = manualQuery,
                    manualArtistInput = manualArtistInput,
                    manualTitleInput = manualTitleInput
                )
            }

            ManageFilesBottomSheet(
                visible = sheetVisible,
                uiState = manageFilesState,
                tasks = tasks,
                topicId = activeTopicId,
                groupTitle = pendingTitle,
                onAddToQueue = { files: List<TorrentFile>, save: SaveOption, folderUri: String? ->
                    val tid = activeTopicId ?: return@ManageFilesBottomSheet
                    viewModel.onAction(TrackDetailAction.StartDownloads(
                        topicId = tid,
                        torrentTitle = pendingTitle,
                        files = files,
                        saveOption = save,
                        folderUri = folderUri
                    ))
                    onNavigateToDownloads(tid, pendingTitle)
                },
                onDismissRequest = {
                    sheetVisible = false
                    viewModel.onAction(TrackDetailAction.HideTorrentFiles)
                },
                onRetryLoadTorrentFiles = {
                    viewModel.onAction(TrackDetailAction.RetryLoadTorrentFiles)
                },
                onPauseDownload = { id ->
                    downloadVm.onAction(DownloadManagerAction.Pause(id))
                },
                onResumeDownload = { id ->
                    downloadVm.onAction(DownloadManagerAction.Resume(id))
                },
                onCancelDownload = { id ->
                    downloadVm.onAction(DownloadManagerAction.Cancel(id))
                },
                onRemoveCompletedEntry = { id, alsoDelete, contentUri ->
                    downloadVm.onAction(
                        DownloadManagerAction.RemoveCompletedEntry(
                            taskId = id,
                            alsoDeleteLocal = alsoDelete,
                            contentUri = contentUri
                        )
                    )
                },
                defaultSaveLocation = defaultSaveLocation,
                onSaveDefaultSaveLocation = { saveOption, customFolderUri ->
                    saveLocationViewModel.persistDefault(saveOption, customFolderUri)
                },
                autoMatch = ManageFilesAutoMatch(
                    artist = effectiveArtist,
                    title = effectiveTitle,
                    enabled = true
                ),
                filterScope = ManageFilesFilterScope.PICKER
            )
            }

            if (showManualDialog) {
                ManualSearchDialog(
                    modifier = if (manualMode) Modifier.padding(innerPadding) else Modifier,
                    artist = manualArtistInput,
                    title = manualTitleInput,
                    initialIsLossless = lossless,
                    showHeader = !manualMode,
                    history = manualSearchHistory,
                    onArtistChange = { manualArtistInput = it },
                    onTitleChange = { manualTitleInput = it },
                    onClearHistory = { viewModel.onAction(TrackDetailAction.ClearManualSearchHistory) },
                    onDismiss = dismissManualParams,
                    onApply = { artistValue, titleValue, selectedIsLossless ->
                        val trimmedArtist = artistValue.trim()
                        val trimmedTitle = titleValue.trim()
                        if (trimmedArtist.isNotBlank()) {
                            viewModel.onAction(
                                TrackDetailAction.SaveManualSearchHistory(
                                    ManualSearchHistoryEntry(
                                        artist = trimmedArtist,
                                        title = trimmedTitle,
                                        isLossless = selectedIsLossless
                                    )
                                )
                            )
                        }
                        showManualDialog = false
                        manualArtistInput = trimmedArtist
                        manualTitleInput = trimmedTitle
                        viewModel.onAction(TrackDetailAction.SetLosslessFilter(selectedIsLossless))
                        viewModel.onAction(TrackDetailAction.SearchTorrentsManual(trimmedArtist, trimmedTitle))
                        expandSignal++
                    }
                )
            }
        }
    }
}

private enum class ManualTopBarMode {
    Search,
    Params
}

@Composable
private fun ManualModeTopBar(
    mode: ManualTopBarMode,
    onLogout: () -> Unit,
    onCloseParams: () -> Unit,
    onOpenSettings: (() -> Unit)?
) {
    val startIcon = if (mode == ManualTopBarMode.Params) {
        Icons.Filled.Close
    } else {
        Icons.AutoMirrored.Outlined.Logout
    }
    val startContentDescription = if (mode == ManualTopBarMode.Params) {
        stringResource(R.string.close_title)
    } else {
        "Logout"
    }
    val onStartClick = if (mode == ManualTopBarMode.Params) onCloseParams else onLogout

    AppCardTopBar(
        title = stringResource(R.string.manual_torrent_search_title),
        startAction = {
            AppCardTopBarIconButton(
                icon = startIcon,
                contentDescription = startContentDescription,
                onClick = onStartClick
            )
        },
        endAction = {
            AppCardTopBarIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = null,
                onClick = { onOpenSettings?.invoke() },
                enabled = onOpenSettings != null
            )
        }
    )
}

@Composable
private fun ManualSearchSetupState(
    modifier: Modifier = Modifier,
    onOpenParams: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
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
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
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
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Text(
                        text = stringResource(R.string.search_params),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                Text(
                    text = stringResource(R.string.set_search_params_hint).replace("\n", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = onOpenParams,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.search_params_short))
                }
            }
        }
    }
}

@Composable
private fun TorrentSearchStateInline(
    errorReason: TorrentSearchErrorReason?,
    isLossless: Boolean,
    hasSearchInput: Boolean,
    showRetry: Boolean,
    onRetry: () -> Unit,
    onOpenSearchParams: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!hasSearchInput) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.search_params),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.set_search_params_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onOpenSearchParams != null) {
                    OutlinedButton(
                        onClick = onOpenSearchParams,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                        )
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.search_params_short))
                    }
                }
            }
        }
        return
    }

    val isError = errorReason != null
    val title = when {
        errorReason == TorrentSearchErrorReason.SERVER_UNREACHABLE ||
                errorReason == TorrentSearchErrorReason.TIMEOUT -> {
            stringResource(R.string.torrent_error_connection_title)
        }

        isError -> stringResource(R.string.error_title)
        isLossless -> stringResource(R.string.no_lossless_found)
        else -> stringResource(R.string.torrents_not_found)
    }
    val description = when {
        errorReason == TorrentSearchErrorReason.SERVER_UNREACHABLE -> {
            stringResource(R.string.torrent_error_server_unreachable)
        }

        errorReason == TorrentSearchErrorReason.TIMEOUT -> {
            stringResource(R.string.torrent_error_timeout)
        }

        isError -> stringResource(R.string.torrent_error_unknown)
        else -> stringResource(R.string.try_to_change_filter)
    }
    val icon = when {
        isError -> Icons.Filled.Warning
        else -> Icons.Filled.Search
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.76f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (isError) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.88f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f)
                    },
                    border = BorderStroke(
                        0.5.dp,
                        if (isError) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        }
                    )
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(7.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (showRetry) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.retry_title))
                }
            }
        }
    }
}



@Composable
private fun ManualSearchDialog(
    modifier: Modifier = Modifier,
    artist: String,
    title: String,
    initialIsLossless: Boolean,
    showHeader: Boolean,
    history: List<ManualSearchHistoryEntry>,
    onArtistChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    onApply: (artist: String, title: String, isLossless: Boolean) -> Unit
) {
    val artistTrimmed = artist.trim()
    val titleTrimmed = title.trim()
    val titleWithoutArtist = titleTrimmed.isNotBlank() && artistTrimmed.isBlank()
    val valid = artistTrimmed.isNotBlank()
    var pendingIsLossless by rememberSaveable { mutableStateOf(initialIsLossless) }
    var showAllHistory by rememberSaveable { mutableStateOf(false) }
    val collapsedHistoryCount = 4
    val visibleHistory = if (showAllHistory) history else history.take(collapsedHistoryCount)
    val sectionShape = RoundedCornerShape(14.dp)
    val controlShape = RoundedCornerShape(12.dp)
    val dialogSurfaceColor = MaterialTheme.colorScheme.background
    val dialogDockColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val outline = MaterialTheme.colorScheme.outlineVariant
    val sectionBorder = BorderStroke(0.5.dp, outline.copy(alpha = 0.9f))
    val controlBorder = BorderStroke(1.dp, outline.copy(alpha = 0.75f))
    val neutralControlColors = ButtonDefaults.outlinedButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    )
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
        unfocusedBorderColor = outline.copy(alpha = 0.7f),
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        errorSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    BackHandler(enabled = true, onBack = onDismiss)

    SyncSystemBarsWithDialog(
        statusBarColor = dialogSurfaceColor,
        navigationBarColor = dialogDockColor,
        navigationBarIconBasisColor = dialogDockColor
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = dialogSurfaceColor,
        tonalElevation = 2.dp
    ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                if (showHeader) {
                    AppCardTopBar(
                        title = stringResource(R.string.search_params),
                        borderAlpha = 0.62f,
                        startAction = {
                            AppCardTopBarIconButton(
                                icon = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.close_title),
                                onClick = onDismiss
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            top = if (showHeader) 0.dp else 10.dp,
                            end = 16.dp
                        )
                ) {
            val dialogListState = rememberLazyListState()
            val showTopDialogFade by remember(dialogListState) {
                derivedStateOf {
                    dialogListState.firstVisibleItemIndex > 0 ||
                        dialogListState.firstVisibleItemScrollOffset > 0
                }
            }
            val showBottomDialogFade by remember(dialogListState) {
                derivedStateOf {
                    val layoutInfo = dialogListState.layoutInfo
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
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    state = dialogListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(top = 0.dp, bottom = 14.dp)
                ) {
                    item {
                    Surface(
                        shape = sectionShape,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        border = sectionBorder
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = artist,
                                onValueChange = onArtistChange,
                                singleLine = true,
                                label = { Text(stringResource(R.string.artist_title)) },
                                leadingIcon = { Icon(Icons.Filled.Person, null) },
                                shape = controlShape,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = onTitleChange,
                                singleLine = true,
                                label = { Text(stringResource(R.string.track_desc)) },
                                leadingIcon = { Icon(Icons.Filled.MusicNote, null) },
                                isError = titleWithoutArtist,
                                supportingText = {
                                    if (titleWithoutArtist) {
                                        Text(
                                            text = stringResource(R.string.manual_search_artist_required),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                shape = controlShape,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { if (valid) onApply(artistTrimmed, titleTrimmed, pendingIsLossless) }
                                )
                            )

                            val startSegmentShape = RoundedCornerShape(
                                topStart = 12.dp,
                                bottomStart = 12.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp
                            )
                            val endSegmentShape = RoundedCornerShape(
                                topStart = 0.dp,
                                bottomStart = 0.dp,
                                topEnd = 12.dp,
                                bottomEnd = 12.dp
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    SingleChoiceSegmentedButtonRow(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(34.dp)
                                    ) {
                                        SegmentedButton(
                                            selected = pendingIsLossless,
                                            onClick = { if (!pendingIsLossless) pendingIsLossless = true },
                                            shape = startSegmentShape,
                                            modifier = Modifier.weight(1f),
                                            icon = {},
                                            label = { Text(stringResource(R.string.lossless_chip)) },
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.64f),
                                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        SegmentedButton(
                                            selected = !pendingIsLossless,
                                            onClick = { if (pendingIsLossless) pendingIsLossless = false },
                                            shape = endSegmentShape,
                                            modifier = Modifier.weight(1f),
                                            icon = {},
                                            label = { Text(stringResource(R.string.lossy_chip)) },
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.64f),
                                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }

                    if (history.isNotEmpty()) {
                        item {
                        Surface(
                            shape = sectionShape,
                            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            border = sectionBorder
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.recent_searches_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    OutlinedIconButton(
                                        onClick = onClearHistory,
                                        modifier = Modifier.size(32.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, outline.copy(alpha = 0.62f)),
                                        colors = IconButtonDefaults.outlinedIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.clear_history_title),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    visibleHistory.forEach { entry ->
                                        OutlinedButton(
                                            onClick = {
                                                pendingIsLossless = entry.isLossless
                                                onApply(entry.artist, entry.title, entry.isLossless)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = controlShape,
                                            border = controlBorder,
                                            colors = neutralControlColors,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Search,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    text = entry.displayTitle(),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (entry.isLossless) {
                                                        stringResource(R.string.lossless_chip)
                                                    } else {
                                                        stringResource(R.string.lossy_chip)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                if (history.size > collapsedHistoryCount) {
                                    TextButton(
                                        onClick = { showAllHistory = !showAllHistory },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(
                                            text = if (showAllHistory) {
                                                stringResource(R.string.show_less_recent_searches_title)
                                            } else {
                                                stringResource(R.string.show_all_recent_searches_title)
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
                        shape = sectionShape,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        border = BorderStroke(0.5.dp, outline.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = stringResource(R.string.pick_artist_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                    }
                }

                if (showTopDialogFade) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        dialogSurfaceColor,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
                if (showBottomDialogFade) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(20.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        dialogSurfaceColor
                                    )
                                )
                            )
                    )
                }
            }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                    border = BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                    )
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .heightIn(min = 54.dp),
                        onClick = { onApply(artistTrimmed, titleTrimmed, pendingIsLossless) },
                        enabled = valid,
                        shape = RoundedCornerShape(14.dp),
                        color = if (valid) {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                        } else {
                            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                        },
                        border = BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = if (valid) 0.58f else 0.5f
                            )
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.search_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (valid) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
                                },
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
}

@Composable
private fun SyncSystemBarsWithDialog(
    statusBarColor: Color,
    navigationBarColor: Color,
    navigationBarIconBasisColor: Color = navigationBarColor
) {
    val statusColorArgb = statusBarColor.toArgb()
    val navigationColorArgb = navigationBarColor.toArgb()
    val useLightStatusIcons = statusBarColor.luminance() > 0.5f
    val useLightNavigationIcons = navigationBarIconBasisColor.luminance() > 0.5f

    DisposableEffect(statusColorArgb, navigationColorArgb, useLightStatusIcons, useLightNavigationIcons) {
        val previous = SystemBarOverrideStore.current
        val override = SystemBarOverride(
            statusBarColorArgb = statusColorArgb,
            navigationBarColorArgb = navigationColorArgb,
            lightStatusBarIcons = useLightStatusIcons,
            lightNavigationBarIcons = useLightNavigationIcons
        )
        SystemBarOverrideStore.current = override

        onDispose {
            if (SystemBarOverrideStore.current == override) {
                SystemBarOverrideStore.current = previous
            }
        }
    }
}
