@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.psycode.spotiflac.ui.screen.auth

import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.mode.AppMode
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposeRenderEffect
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin



@Composable
private fun rememberIndigoWaveBrush(
    durationMs: Int = 2670
): Brush {
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.primary)

    val t by rememberInfiniteTransition(label = "indigoBrush")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )

    
    val startX = -600f + 1200f * t
    val endX   = startX + 1000f

    return Brush.linearGradient(
        colors = colors,
        start = Offset(startX, 0f),
        end   = Offset(endX,   0f),
        tileMode = TileMode.Clamp
    )
}


private fun Modifier.gradientTint(brush: Brush) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
        }
    }


@Composable
private fun GlowyGradientLogo(
    painter: Painter,
    brush: Brush,
    modifier: Modifier = Modifier,
    glowScale: Float = 1.06f,
    glowAlphaMin: Float = 0.18f,
    glowAlphaMax: Float = 0.34f,
    blur: Dp = 18.dp
) {
    val density = LocalDensity.current
    val blurPx = with(density) { blur.toPx() }

    val beatPhase by rememberInfiniteTransition(label = "logoBeat")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2180, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "beatPhase"
        )

    val primaryBeat = exp(-((beatPhase - 0.17f) * (beatPhase - 0.17f)) / (2f * 0.038f * 0.038f))
    val secondaryBeat = exp(-((beatPhase - 0.31f) * (beatPhase - 0.31f)) / (2f * 0.046f * 0.046f))
    val beatEnvelope = (primaryBeat + (secondaryBeat * 0.78f)).coerceIn(0f, 1.78f)
    val beatEnergy = beatEnvelope / 1.78f
    val breathe = (0.5f + 0.5f * sin((beatPhase * 2f * PI).toFloat())).coerceIn(0f, 1f)
    val smoothEnergy = ((beatEnergy * 0.8f) + (breathe * 0.2f)).coerceIn(0f, 1f)

    val beatScale = 1f + (0.013f * breathe) + (0.056f * smoothEnergy)
    val glowAlpha = (
        glowAlphaMin +
            ((glowAlphaMax - glowAlphaMin) * (0.30f * breathe + 0.70f * smoothEnergy))
        ).coerceIn(glowAlphaMin, glowAlphaMax)
    val dynamicGlowScale = glowScale + (0.022f * smoothEnergy)

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = beatScale
            scaleY = beatScale
        },
        contentAlignment = Alignment.Center
    ) {
        
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = dynamicGlowScale
                    scaleY = dynamicGlowScale
                    alpha  = glowAlpha
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = android.graphics.RenderEffect
                            .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
                }
                .gradientTint(brush),
            contentScale = ContentScale.Fit
        )
        
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .gradientTint(brush),
            contentScale = ContentScale.Fit
        )
    }
}


@Composable
private fun WordmarkFlacTor(
    fontSize: TextUnit,
    brush: Brush,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme

    val styled = remember(brush) {
        buildAnnotatedString {
            withStyle(SpanStyle(brush = brush)) { append("FlacTor") }
        }
    }

    Text(
        text = styled,
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize,
            letterSpacing = 0.3.sp,
            fontWeight = FontWeight.Bold,
            shadow = Shadow(
                color = cs.primary.copy(alpha = 0.28f),
                offset = Offset.Zero,
                blurRadius = 18f
            ),
            color = Color.Unspecified
        ),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SearchModeOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeText: String? = null
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (selected) cs.primary.copy(alpha = 0.54f) else cs.outlineVariant.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 180),
        label = "modeBorderColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
        animationSpec = tween(durationMillis = 180),
        label = "modeIconTint"
    )
    val iconContainerColor = cs.surfaceVariant.copy(alpha = 0.62f)
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.988f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "modePressScale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceColorAtElevation(1.dp),
        border = BorderStroke(if (selected) 1.dp else 0.6.dp, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconContainerColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = cs.onSurface
                    )

                    if (!badgeText.isNullOrBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = cs.primary.copy(alpha = if (selected) 0.18f else 0.11f),
                            border = BorderStroke(0.6.dp, cs.primary.copy(alpha = 0.26f))
                        ) {
                            Text(
                                text = badgeText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.primary
                            )
                        }
                    }
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}


@Composable
fun AuthScreen(
    onSpotifyPublicSearch: () -> Unit,
    onManualSearch: () -> Unit
) {
    val viewModel: AuthViewModel = hiltViewModel()

    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showContent = true }

    var savingGuest by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<AppMode>(AppMode.SpotifyPublic) }

    val outline      = MaterialTheme.colorScheme.outlineVariant
    val surfaceElev1 = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    val bg = MaterialTheme.colorScheme.background
    val bgBrush = remember(surfaceElev1, bg) { Brush.verticalGradient(listOf(surfaceElev1, bg)) }

    
    val cfg = LocalConfiguration.current
    val logoSize = remember(cfg) { (cfg.screenWidthDp.dp * 0.38f).coerceIn(140.dp, 200.dp) }
    val wordmarkSize = 48.sp

    
    val animatedBrush = rememberIndigoWaveBrush()

    val logoPainter = painterResource(R.drawable.flactor_logo)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(bgBrush)
                .systemBarsPadding()
        ) {
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
                exit = ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GlowyGradientLogo(
                        painter = logoPainter,
                        brush   = animatedBrush,
                        modifier = Modifier
                            .size(logoSize)
                            .padding(bottom = 8.dp)
                    )

                    WordmarkFlacTor(
                        fontSize = wordmarkSize,
                        brush    = animatedBrush,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    
                    val rawTitle = stringResource(R.string.search_type_title)
                    val titleLine = remember(rawTitle) {
                        rawTitle
                            .replace("The Pirate Bay", "The\u00A0Pirate\u00A0Bay")
                    }
                    Text(
                        text = titleLine,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            letterSpacing = 0.1.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Spacer(Modifier.height(22.dp))

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(0.5.dp, outline),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SearchModeOptionCard(
                                title = stringResource(R.string.spotify_search_title),
                                description = stringResource(R.string.search_via_spotify_desc),
                                icon = Icons.Filled.Person,
                                selected = selectedMode == AppMode.SpotifyPublic,
                                enabled = !savingGuest,
                                onClick = { selectedMode = AppMode.SpotifyPublic }
                            )

                            SearchModeOptionCard(
                                title = stringResource(R.string.manual_search_title),
                                description = stringResource(R.string.search_torrents_manual_desc),
                                icon = Icons.Filled.Search,
                                selected = selectedMode == AppMode.ManualTorrent,
                                enabled = !savingGuest,
                                onClick = { selectedMode = AppMode.ManualTorrent }
                            )

                            Spacer(Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = {
                                    if (selectedMode == AppMode.SpotifyPublic) {
                                        savingGuest = true
                                        viewModel.setMode(AppMode.SpotifyPublic) {
                                            onSpotifyPublicSearch()
                                        }
                                    } else {
                                        viewModel.setMode(AppMode.ManualTorrent) {
                                            onManualSearch()
                                        }
                                    }
                                },
                                enabled = !savingGuest,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text(stringResource(R.string.auth_continue_title))
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        stringResource(R.string.search_mod_tip),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}






