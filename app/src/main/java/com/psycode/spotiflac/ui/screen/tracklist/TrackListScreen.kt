@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterialApi::class,
    ExperimentalMaterial3Api::class
)
package com.psycode.spotiflac.ui.screen.tracklist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.ui.component.AppCardTopBar
import com.psycode.spotiflac.ui.component.AppCardTopBarIconButton
import com.psycode.spotiflac.ui.component.DownloadsBottomBar
import com.psycode.spotiflac.ui.component.ShimmerListRows
import com.psycode.spotiflac.ui.screen.downloads.countActiveRunningDownloads
import com.psycode.spotiflac.ui.screen.downloads.countQueuedDownloads
import com.psycode.spotiflac.ui.screen.downloads.DownloadManagerViewModel
import kotlinx.coroutines.delay

@Composable
fun TrackListScreen(
    viewModel: TrackListViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPublicMode = uiState.isPublicMode
    
    var searchActive by rememberSaveable { mutableStateOf(false) }

    val query = uiState.query
    val pagingItems = viewModel.pagingData.collectAsLazyPagingItems()

    val listState = rememberLazyListState()
    val isRefreshing = pagingItems.loadState.refresh is LoadState.Loading
    val isInitialLoading =
        pagingItems.loadState.refresh is LoadState.Loading && pagingItems.itemCount == 0
    val refreshError = pagingItems.loadState.refresh as? LoadState.Error
    val showNoResults =
        pagingItems.loadState.refresh is LoadState.NotLoading &&
            pagingItems.itemCount == 0 &&
            query.isNotBlank()
    val pullState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { pagingItems.refresh() }
    )

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val closeSearch: () -> Unit = {
        searchActive = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    
    LaunchedEffect(searchActive) {
        if (searchActive) {
            
            delay(50)
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    val downloadVm: DownloadManagerViewModel = hiltViewModel()
    val downloadUiState by downloadVm.uiState.collectAsState()
    val tasks = downloadUiState.tasks
    val activeCount = remember(tasks) {
        countActiveRunningDownloads(tasks)
    }
    val queuedCount = remember(tasks) {
        countQueuedDownloads(tasks)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (searchActive) {
                ActiveSearchTopBar(
                    query = query,
                    onQueryChange = { viewModel.onAction(TrackListAction.QueryChanged(it)) },
                    onCloseSearch = closeSearch,
                    onClearQuery = { viewModel.onAction(TrackListAction.QueryChanged("")) },
                    onDone = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    focusRequester = focusRequester
                )
            } else {
                AppCardTopBar(
                    title = stringResource(R.string.search_title),
                    sideSlotWidth = 84.dp,
                    startAction = {
                        AppCardTopBarIconButton(
                            icon = if (isPublicMode) {
                                Icons.AutoMirrored.Filled.Login
                            } else {
                                Icons.AutoMirrored.Outlined.Logout
                            },
                            contentDescription = if (isPublicMode) "Login" else "Logout",
                            onClick = {
                                viewModel.onAction(TrackListAction.ResetModeClicked)
                                onLogout()
                            }
                        )
                    },
                    endAction = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AppCardTopBarIconButton(
                                icon = Icons.Filled.Search,
                                contentDescription = "Search",
                                onClick = { searchActive = true }
                            )
                            AppCardTopBarIconButton(
                                icon = Icons.Filled.Settings,
                                contentDescription = null,
                                onClick = onSettingsClick
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            DownloadsBottomBar(
                activeCount = activeCount,
                queuedCount = queuedCount,
                onClick = onDownloadsClick
            )
        }
    ) { innerPadding ->
        Box(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullRefresh(pullState)
        ) {
            when {
                
                isPublicMode && query.isBlank() -> {
                    Crossfade(
                        targetState = searchActive,
                        animationSpec = tween(durationMillis = 220),
                        label = "public-empty-mode"
                    ) { isSearchActive ->
                        if (isSearchActive) {
                            ActiveSearchEmptyState()
                        } else {
                            GuestEmptyState(
                                showOpenButton = true,
                                onStartSearch = { searchActive = true }
                            )
                        }
                    }
                }
                isInitialLoading -> {
                    TrackListLoadingSkeleton()
                }
                refreshError != null && query.isNotBlank() -> {
                    SearchErrorState(
                        message = refreshError.error.message,
                        onRetry = { pagingItems.retry() }
                    )
                }
                
                pagingItems.loadState.refresh is LoadState.NotLoading &&
                        pagingItems.itemCount == 0 &&
                        query.isNotBlank() -> {
                    EmptySearchState(query = query)
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            count = pagingItems.itemCount,
                            key = { index ->
                                val track = pagingItems[index]
                                if (track == null) {
                                    "track-$index"
                                } else {
                                    "${track.id}-$index"
                                }
                            }
                        ) { index ->
                            pagingItems[index]?.let { track ->
                                TrackCard(
                                    track = track
                                ) { onTrackClick(track) }
                            }
                        }
                        if (pagingItems.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }

            if (!isInitialLoading) {
                PullRefreshIndicator(
                    refreshing = isRefreshing,
                    state = pullState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }

    
    
    
    
    BackHandler(enabled = true) {
        when (resolveTrackListBackAction(searchActive = searchActive, query = query)) {
            TrackListBackAction.CLOSE_SEARCH -> {
                closeSearch()
            }
            TrackListBackAction.CLEAR_QUERY -> {
                viewModel.onAction(TrackListAction.QueryChanged(""))
                focusManager.clearFocus(force = true)
                keyboard?.hide()
            }
            TrackListBackAction.NAVIGATE_BACK -> onBack()
        }
    }
}

@Composable
private fun ActiveSearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester
) {
    val unifiedOutline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val fieldShape = RoundedCornerShape(18.dp)
    val fieldContainer = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        shape = fieldShape,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(58.dp)
            .border(
                width = 0.5.dp,
                color = unifiedOutline,
                shape = fieldShape
            )
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_tracks_title)) },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = fieldContainer,
            unfocusedContainerColor = fieldContainer,
            focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedLeadingIconColor = iconColor,
            unfocusedLeadingIconColor = iconColor,
            focusedTrailingIconColor = iconColor,
            unfocusedTrailingIconColor = iconColor
        )
    )
}

@Composable
private fun ActiveSearchEmptyState() {
    val cardShape = RoundedCornerShape(16.dp)
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp),
            shape = cardShape,
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            border = BorderStroke(0.5.dp, outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_active_hint_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.search_active_hint_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GuestEmptyState(
    showOpenButton: Boolean,
    onStartSearch: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "guest-empty-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "guest-empty-scale"
    )
    val containerShape = RoundedCornerShape(24.dp)
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    val subtitle = stringResource(R.string.start_searching_spotify).replace("\n", " ")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
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
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size((30 * scale).dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.track_search),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                if (showOpenButton) {
                    OutlinedButton(
                        onClick = onStartSearch,
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
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.open_search))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    val floatAnim = rememberInfiniteTransition(label = "empty-search-float")
    val iconOffset by floatAnim.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "empty-search-offset"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier
                .offset(y = iconOffset.dp)
                .size(42.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.nothing_found),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.try_to_change_query, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchErrorState(
    message: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_title),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message ?: stringResource(R.string.could_not_proceed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(R.string.retry_title))
        }
    }
}

@Composable
private fun TrackListLoadingSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column {
            ShimmerListRows(rows = 6, rowHeight = 88, gap = 12)
        }
    }
}


