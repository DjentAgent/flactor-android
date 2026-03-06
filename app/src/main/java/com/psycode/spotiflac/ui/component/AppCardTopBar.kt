package com.psycode.spotiflac.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppCardTopBar(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    startAction: @Composable () -> Unit,
    centerContent: (@Composable () -> Unit)? = null,
    sideSlotWidth: Dp = 38.dp,
    reserveEndSpaceWhenNoAction: Boolean = true,
    centerHorizontalPadding: Dp = 10.dp,
    borderAlpha: Float = 0.75f,
    endAction: (@Composable () -> Unit)? = null
) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
        border = BorderStroke(0.5.dp, outline),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.width(sideSlotWidth),
                contentAlignment = Alignment.CenterStart
            ) {
                startAction()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = centerHorizontalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (centerContent != null) {
                    centerContent()
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (endAction != null) {
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    endAction()
                }
            } else if (reserveEndSpaceWhenNoAction) {
                Spacer(Modifier.width(sideSlotWidth))
            } else {
                Spacer(Modifier.width(0.dp))
            }
        }
    }
}

@Composable
fun AppCardTopBarIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badgeCount: Int = 0
) {
    val outline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        border = BorderStroke(0.5.dp, outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(38.dp)
            ) {
                if (badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = contentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
