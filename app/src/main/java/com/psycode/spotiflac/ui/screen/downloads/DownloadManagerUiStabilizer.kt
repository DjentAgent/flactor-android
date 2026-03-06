package com.psycode.spotiflac.ui.screen.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.psycode.spotiflac.domain.model.DownloadTask
import kotlinx.coroutines.delay

internal object DownloadManagerUiStabilizer {
    const val TASK_LIST_DEBOUNCE_MS = 220L
}

@Composable
internal fun rememberDebouncedDownloadTasks(
    tasks: List<DownloadTask>,
    debounceMs: Long = DownloadManagerUiStabilizer.TASK_LIST_DEBOUNCE_MS
): List<DownloadTask> {
    var rendered by remember { mutableStateOf(tasks) }
    LaunchedEffect(tasks, debounceMs) {
        if (tasks.isEmpty() || rendered.isEmpty()) {
            rendered = tasks
            return@LaunchedEffect
        }
        delay(debounceMs)
        rendered = tasks
    }
    return rendered
}
