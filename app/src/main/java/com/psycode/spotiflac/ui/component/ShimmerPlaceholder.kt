package com.psycode.spotiflac.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-offset"
    )

    val surface = MaterialTheme.colorScheme.surfaceVariant
    val bright = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val brush = Brush.linearGradient(
        colors = listOf(surface.copy(alpha = 0.45f), bright, surface.copy(alpha = 0.45f)),
        start = Offset(x - 300f, 0f),
        end = Offset(x, 300f)
    )

    Box(
        modifier = modifier
            .background(
                brush = brush,
                shape = RoundedCornerShape(12.dp)
            )
    )
}

@Composable
fun ShimmerListRows(
    rows: Int,
    rowHeight: Int = 84,
    gap: Int = 12
) {
    repeat(rows.coerceAtLeast(1)) { index ->
        ShimmerPlaceholder(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight.dp)
        )
        if (index < rows - 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gap.dp)
            )
        }
    }
}
