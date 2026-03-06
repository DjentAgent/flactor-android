package com.psycode.spotiflac.ui.screen.downloads

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask

internal fun resolveFileOpMessage(
    fileOp: DownloadManagerViewModel.FileOpUiState,
    messageByRes: (Int) -> String
): String? = when (fileOp) {
    is DownloadManagerViewModel.FileOpUiState.Success -> messageByRes(fileOp.messageResId)
    is DownloadManagerViewModel.FileOpUiState.Error -> fileOp.message ?: messageByRes(fileOp.fallbackResId)
    DownloadManagerViewModel.FileOpUiState.Idle,
    DownloadManagerViewModel.FileOpUiState.Running -> null
}

internal fun toggleCollapsedDate(
    collapsedDates: Set<String>,
    dateKey: String
): Set<String> = if (dateKey in collapsedDates) {
    collapsedDates - dateKey
} else {
    collapsedDates + dateKey
}

internal fun buildDateHeaderItemKey(dateKey: String): String = "date-header-$dateKey"

internal fun buildGroupItemKey(dateKey: String, groupTitle: String): String = "group-$dateKey-$groupTitle"

internal fun countActiveRunningDownloads(tasks: List<DownloadTask>): Int =
    tasks.count { it.status == DownloadStatus.RUNNING }

internal fun countQueuedDownloads(tasks: List<DownloadTask>): Int =
    tasks.count { it.status == DownloadStatus.QUEUED }

internal fun shouldEnsureDownloadService(
    tasks: List<DownloadTask>,
    alreadyEnsuredInSession: Boolean
): Boolean {
    if (alreadyEnsuredInSession) return false
    return tasks.any { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
}

internal fun parseTopicIdFromTaskId(taskId: String?): Int? {
    if (taskId == null) return null
    val separatorIndex = taskId.indexOf('_')
    if (separatorIndex <= 0) return null
    return taskId.substring(0, separatorIndex).toIntOrNull()
}

internal fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return ""
    val kb = bytesPerSec / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format("%.1f MB/s", mb) else String.format("%.0f KB/s", kb)
}

internal fun estimateRemainingSeconds(
    totalBytes: Long,
    downloadedBytes: Long,
    speedBytesPerSec: Long
): Long? {
    if (totalBytes <= 0L || speedBytesPerSec <= 0L) return null
    val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0L)
    if (remainingBytes == 0L) return 0L
    return (remainingBytes + speedBytesPerSec - 1L) / speedBytesPerSec
}

internal fun formatEtaCompact(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0L)
    val hours = clamped / 3600L
    val minutes = (clamped % 3600L) / 60L
    val seconds = clamped % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m"
        else -> "${seconds}s"
    }
}

@Composable
internal fun EnsureNotificationsPermission() {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

internal fun isChannelDisabled(context: Context, channelId: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val channel = notificationManager.getNotificationChannel(channelId) ?: return false
    return channel.importance == NotificationManager.IMPORTANCE_NONE
}

internal fun openDownloadChannelSettings(context: Context, channelId: String) {
    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

internal fun safeOpenUri(context: Context, uriString: String): Boolean {
    val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false

    val canRead = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { } != null
    } catch (_: Exception) {
        false
    }
    if (!canRead) return false

    val mime = try {
        context.contentResolver.getType(uri)
            ?: android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                ?.let { android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    } catch (_: Exception) {
        null
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    return try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}

