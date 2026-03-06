package com.psycode.spotiflac.ui.screen.trackdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.TorrentResult
import com.psycode.spotiflac.ui.component.ShimmerListRows
import com.psycode.spotiflac.ui.screen.downloads.DM_RADIUS_CONTROL
import kotlinx.coroutines.launch

@Composable
fun ExpandableSection(
    title: String,
    isLoading: Boolean,
    items: List<TorrentResult>,
    losslessSelected: Boolean,
    onFilterChange: (Boolean) -> Unit,
    captchaState: CaptchaState,
    isCaptchaSubmitting: Boolean,
    onSubmitCaptcha: (String) -> Unit,
    onTorrentClick: (TorrentResult) -> Unit,
    
    parentListState: LazyListState,
    parentItemIndex: Int,
    parentViewportBottomPx: Int, 
    modifier: Modifier = Modifier,
    emptyContent: @Composable () -> Unit = {},
    statusTextOverride: String? = null,
    headerAction: (@Composable RowScope.() -> Unit)? = null,
    trailingFilters: (@Composable RowScope.() -> Unit)? = null,
    manualArtist: String? = null,
    manualTitle: String? = null,
    onManualChipClick: (() -> Unit)? = null,
    maxItemsPerPage: Int = 18,
    expandSignal: Int = 0,
    onExpandRequest: (() -> Boolean)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var captchaInput by remember { mutableStateOf("") }

    
    val orderedItems = remember(items) {
        items.sortedByDescending { it.seeders }
    }

    
    var page by rememberSaveable { mutableIntStateOf(0) }
    val totalPages = remember(orderedItems, maxItemsPerPage) {
        expandableTotalPages(
            totalItems = orderedItems.size,
            pageSize = maxItemsPerPage
        )
    }
    val pagedItems = remember(orderedItems, page, maxItemsPerPage) {
        expandablePageSlice(
            items = orderedItems,
            page = page,
            pageSize = maxItemsPerPage
        )
    }
    
    LaunchedEffect(orderedItems, maxItemsPerPage) {
        page = expandableNormalizePage(
            page = page,
            totalItems = orderedItems.size,
            pageSize = maxItemsPerPage
        )
    }
    

    
    LaunchedEffect(expandSignal) { if (expandSignal > 0) expanded = true }

    
    val scope = rememberCoroutineScope()
    LaunchedEffect(page, expanded) {
        if (expanded) {
            
            parentListState.animateScrollToItem(parentItemIndex)
        }
    }

    val outline = MaterialTheme.colorScheme.outlineVariant
    val containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val shape = RoundedCornerShape(18.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val filterControlHeight = 34.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .animateContentSize(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(if (expanded) 5.dp else 2.dp),
        border = BorderStroke(0.5.dp, outline.copy(alpha = 0.8f))
    ) {
        Column(Modifier.fillMaxWidth()) {

            
            val hasResults = items.isNotEmpty()
            val subtitle = when {
                isLoading -> stringResource(R.string.search_torrents_loading)
                !statusTextOverride.isNullOrBlank() -> statusTextOverride
                items.isEmpty() -> stringResource(R.string.search_torrents_not_found_compact)
                else -> stringResource(R.string.search_torrents_found_count, items.size)
            }
            val subtitleColor = if (hasResults && !isLoading) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                muted
            }
            val subtitleContainer = if (hasResults && !isLoading) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp).copy(alpha = 0.92f)
            }
            val statusPulse = rememberInfiniteTransition(label = "torrent-status-pulse")
            val loadingDotAlpha by statusPulse.animateFloat(
                initialValue = 0.38f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(860),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "torrent-status-dot-alpha"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 34.dp)
                            .clickable {
                                val allow = onExpandRequest?.invoke() ?: true
                                if (allow) expanded = !expanded
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (headerAction != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            headerAction.invoke(this)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val allow = onExpandRequest?.invoke() ?: true
                            if (allow) expanded = !expanded
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        shape = RoundedCornerShape(DM_RADIUS_CONTROL),
                        color = subtitleContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .height(26.dp)
                                .padding(horizontal = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLoading) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = loadingDotAlpha))
                                )
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (hasResults && !isLoading) FontWeight.SemiBold else FontWeight.Medium,
                                color = subtitleColor
                            )
                        }
                    }
                }
            }

            
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(clip = false) + fadeIn(),
                exit = ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (captchaState is CaptchaState.Required) {
                        AsyncImage(
                            model = captchaState.captchaImageUrl,
                            contentDescription = "Captcha",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                        OutlinedTextField(
                            value = captchaInput,
                            onValueChange = { captchaInput = it },
                            placeholder = { Text(stringResource(R.string.captcha_input_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                if (isCaptchaSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(onClick = {
                                        onSubmitCaptcha(captchaInput)
                                        captchaInput = ""
                                    }) {
                                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.captcha_submit))
                                    }
                                }
                            }
                        )
                    } else {
                        val lossyShape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp
                        )
                        val losslessShape = RoundedCornerShape(
                            topStart = 0.dp,
                            bottomStart = 0.dp,
                            topEnd = 12.dp,
                            bottomEnd = 12.dp
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (trailingFilters != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.defaultMinSize(minHeight = filterControlHeight),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        trailingFilters.invoke(this)
                                    }
                                }
                            }

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(filterControlHeight)
                            ) {
                                SegmentedButton(
                                    selected = !losslessSelected,
                                    onClick = { onFilterChange(false) },
                                    shape = lossyShape,
                                    modifier = Modifier.weight(1f),
                                    icon = {},
                                    label = { Text(stringResource(R.string.lossy_chip)) },
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                SegmentedButton(
                                    selected = losslessSelected,
                                    onClick = { onFilterChange(true) },
                                    shape = losslessShape,
                                    modifier = Modifier.weight(1f),
                                    icon = {},
                                    label = { Text(stringResource(R.string.lossless_chip)) },
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        
                        val showManual = !manualArtist.isNullOrBlank()
                        AnimatedVisibility(
                            visible = showManual,
                            enter = fadeIn() + expandVertically(clip = false)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AssistChip(
                                    onClick = { onManualChipClick?.invoke() },
                                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                                    label = {
                                        Text(
                                            manualArtist.orEmpty(),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                                if (!manualTitle.isNullOrBlank()) {
                                    AssistChip(
                                        onClick = { onManualChipClick?.invoke() },
                                        leadingIcon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                                        label = {
                                            Text(
                                                manualTitle,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }

                        when {
                            isLoading -> {
                                ShimmerListRows(rows = 3, rowHeight = 60, gap = 10)
                            }
                            orderedItems.isEmpty() -> {
                                emptyContent()
                            }
                            else -> {
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    pagedItems.forEach { result ->
                                        TorrentItemCard(
                                            result = result,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onTorrentClick(result) }
                                        )
                                    }
                                }

                                
                                Spacer(Modifier.height(14.dp))

                                if (totalPages > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                page = (page - 1).coerceIn(0, totalPages - 1)
                                                
                                                scope.launch { parentListState.animateScrollToItem(parentItemIndex) }
                                            },
                                            enabled = page > 0,
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text(stringResource(R.string.pagination_back))
                                        }
                                        Text(
                                            "${page + 1} / $totalPages",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                page = (page + 1).coerceIn(0, totalPages - 1)
                                                
                                                scope.launch { parentListState.animateScrollToItem(parentItemIndex) }
                                            },
                                            enabled = page < totalPages - 1,
                                            modifier = Modifier.height(36.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                        ) {
                                            Text(stringResource(R.string.pagination_forward))
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TorrentItemCard(result: TorrentResult, modifier: Modifier = Modifier) {
    val cardColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val outline   = MaterialTheme.colorScheme.outlineVariant
    val tint      = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(0.5.dp, outline.copy(alpha = 0.72f))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = result.title,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleSmall.copy(lineHeight = 22.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TorrentMetaChip(
                    text = result.seeders.toString(),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Upload,
                            contentDescription = null,
                            tint = tint.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                )
                TorrentMetaChip(text = result.size)
            }
        }
    }
}

@Composable
private fun TorrentMetaChip(
    text: String,
    icon: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



