package com.psycode.spotiflac.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psycode.spotiflac.ui.component.managefiles.UiFileDl
import com.psycode.spotiflac.ui.component.managefiles.humanReadableSize

@Composable
internal fun FileCardRow(
    ui: UiFileDl,
    idx: Int,
    checked: Boolean,
    disabled: Boolean,
    preserveActiveToneWhenDisabled: Boolean = false,
    baseCard: androidx.compose.ui.graphics.Color,
    selectedCard: androidx.compose.ui.graphics.Color,
    outerIndent: Dp,
    onToggle: (Boolean) -> Unit,
    parentDir: String,
    statusHint: String?,
    statusHintColor: Color,
    statusProgress: Float?,
    statusActions: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = outerIndent)
            .clickable(enabled = !disabled) { if (idx >= 0) onToggle(!checked) },
        shape = RoundedCornerShape(MF_RADIUS_ITEM),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                checked -> selectedCard
                disabled && !preserveActiveToneWhenDisabled -> MaterialTheme.colorScheme.surfaceVariant
                else -> baseCard
            }
        ),
        border = BorderStroke(
            0.5.dp,
            if (checked) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked && !disabled,
                onCheckedChange = { c -> if (!disabled && idx >= 0) onToggle(c) },
                enabled = !disabled
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = ui.file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val meta = if (parentDir.isBlank() || parentDir == "/") {
                    humanReadableSize(ui.size)
                } else {
                    "${humanReadableSize(ui.size)} · $parentDir"
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!statusHint.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusHintColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (statusProgress != null) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { statusProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = statusActions
            )
        }
    }
}

