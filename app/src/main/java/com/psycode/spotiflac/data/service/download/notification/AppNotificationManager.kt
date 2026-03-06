package com.psycode.spotiflac.data.service.download.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.psycode.spotiflac.MainActivity
import com.psycode.spotiflac.R
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.preferences.DownloadNotificationPrefs
import com.psycode.spotiflac.data.service.download.service.DownloadService
import com.psycode.spotiflac.data.service.download.service.DownloadServiceRouter
import com.psycode.spotiflac.data.service.download.util.formatSpeed
import com.psycode.spotiflac.data.service.download.util.formatTime
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.navigation.Screen
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.abs

@ServiceScoped
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationPrefs = DownloadNotificationPrefs.prefs(context)
    private val progressUpdateThrottler = ConcurrentHashMap<String, Long>()
    private val lastTaskSnapshots = ConcurrentHashMap<String, TaskNotificationSnapshot>()
    private val visibleTaskProgressIds = ConcurrentHashMap.newKeySet<String>()

    private val minProgressNotificationInterval = 1_000L
    private val minSummaryNotificationInterval = 1_200L
    private val perTaskVisibilityThreshold = 2
    private val separator by lazy { " ${context.getString(R.string.list_separator_dot)} " }

    @Volatile
    private var perTaskNotificationsEnabled = false

    @Volatile
    private var lastSummaryFingerprint: String? = null

    @Volatile
    private var lastSummaryUpdateMs = 0L

    @Volatile
    private var lastSummaryNotification: Notification? = null

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val progressChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_download_channel_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(progressChannel)

        val eventsChannel = NotificationChannel(
            EVENTS_CHANNEL_ID,
            context.getString(R.string.notif_events_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notif_events_channel_description)
            setShowBadge(true)
        }
        nm.createNotificationChannel(eventsChannel)
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun serviceActionPendingIntent(action: String, taskId: String? = null): PendingIntent {
        val intent = Intent(context, DownloadService::class.java)
            .setAction(action)
            .apply {
                if (taskId != null) putExtra(DownloadServiceRouter.EXTRA_TASK_ID, taskId)
            }
        val requestCode = if (taskId != null) {
            action.hashCode() xor taskId.hashCode()
        } else {
            action.hashCode()
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun openDownloadsPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SCREEN_ROUTE, Screen.Downloads.route)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_DOWNLOADS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun openFilePendingIntent(taskId: String, contentUri: Uri): PendingIntent? {
        val mimeType = context.contentResolver.getType(contentUri) ?: "audio/*"
        val openIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(contentUri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN_FILE_BASE + (taskId.hashCode() and 0x7fff),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
        }.getOrNull()
    }

    fun updateSummary(tasks: List<DownloadEntity>): Notification {
        val activeTasks = tasks.filter {
            it.status == DownloadStatus.RUNNING ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.PAUSED
        }
        val runningCount = activeTasks.count { it.status == DownloadStatus.RUNNING }
        val queuedCount = activeTasks.count { it.status == DownloadStatus.QUEUED }
        val pausedCount = activeTasks.count { it.status == DownloadStatus.PAUSED }
        val totalSpeed = activeTasks
            .asSequence()
            .filter { it.status == DownloadStatus.RUNNING }
            .sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }
        val hasActive = activeTasks.isNotEmpty()
        val progressEnabled = isProgressNotificationsEnabled()

        if (!progressEnabled) {
            updatePerTaskNotificationMode(0)
            val compactText = if (hasActive) {
                context.getString(R.string.notif_progress_compact_active)
            } else {
                context.getString(R.string.no_active_downloads)
            }
            val compactNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(
                    if (hasActive) android.R.drawable.stat_sys_download
                    else android.R.drawable.stat_sys_download_done
                )
                .setContentTitle(context.getString(R.string.notif_summary_title))
                .setContentText(compactText)
                .setContentIntent(openDownloadsPendingIntent())
                .setOnlyAlertOnce(true)
                .setOngoing(hasActive)
                .setSilent(true)
                .setGroup(NOTIF_GROUP)
                .setGroupSummary(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            val compactFingerprint = "compact:$hasActive:${activeTasks.size}"
            if (shouldDispatchSummary(compactFingerprint) || lastSummaryNotification == null) {
                nm.notify(SUMMARY_ID, compactNotification)
                lastSummaryNotification = compactNotification
                return compactNotification
            }
            return lastSummaryNotification ?: compactNotification
        }

        updatePerTaskNotificationMode(activeTasks.size)
        syncTaskProgressNotifications(activeTasks.map { it.id }.toSet())

        val summaryText = if (hasActive) {
            buildSummaryText(runningCount, queuedCount, pausedCount, totalSpeed)
        } else {
            context.getString(R.string.no_active_downloads)
        }

        val inbox = NotificationCompat.InboxStyle().also { style ->
            if (activeTasks.isNotEmpty()) {
                activeTasks
                    .groupBy { it.torrentTitle.ifBlank { context.getString(R.string.notif_torrent_fallback_title) } }
                    .entries
                    .sortedByDescending { (_, list) -> list.count { it.status == DownloadStatus.RUNNING } }
                    .take(6)
                    .forEach { (title, list) ->
                        style.addLine(buildTorrentLine(title, list))
                    }
            }
            style.setSummaryText(summaryText)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(
                if (hasActive) android.R.drawable.stat_sys_download
                else android.R.drawable.stat_sys_download_done
            )
            .setContentTitle(context.getString(R.string.notif_summary_title))
            .setContentText(summaryText)
            .setStyle(inbox)
            .setContentIntent(openDownloadsPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(hasActive)
            .setSilent(true)
            .setGroup(NOTIF_GROUP)
            .setGroupSummary(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (runningCount + queuedCount > 0) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.pause_all_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_PAUSE_ALL)
            )
        }
        if (pausedCount > 0) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.resume_all_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_RESUME_ALL)
            )
        }

        val notification = builder.build()
        val fingerprint = buildSummaryFingerprint(activeTasks, runningCount, queuedCount, pausedCount, totalSpeed)
        if (shouldDispatchSummary(fingerprint) || lastSummaryNotification == null) {
            nm.notify(SUMMARY_ID, notification)
            lastSummaryNotification = notification
            return notification
        }
        return lastSummaryNotification ?: notification
    }

    fun updateTaskProgress(task: DownloadEntity, paused: Boolean, totalBytes: Long) {
        if (!isProgressNotificationsEnabled()) return
        if (!perTaskNotificationsEnabled) return
        if (!shouldEmitTaskProgressUpdate(task, paused)) return

        visibleTaskProgressIds.add(task.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.fileName)
            .setContentText(formatProgressText(task, totalBytes, paused))
            .setSubText(task.torrentTitle)
            .setContentIntent(openDownloadsPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(!paused)
            .setSilent(true)
            .setGroup(NOTIF_GROUP)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
            .setSortKey("${task.torrentTitle}|${task.id}")
            .setProgress(100, task.progress.coerceIn(0, 100), false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (paused) {
            builder.addAction(
                android.R.drawable.ic_media_play,
                context.getString(R.string.resume_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_RESUME, task.id)
            )
        } else {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.pause_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_PAUSE, task.id)
            )
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.cancel_title),
            serviceActionPendingIntent(DownloadServiceRouter.ACTION_CANCEL, task.id)
        )
        nm.notify(notificationIdFor(task.id), builder.build())
    }

    private fun formatProgressText(task: DownloadEntity, totalBytes: Long, paused: Boolean): String {
        val progress = task.progress.coerceIn(0, 100)
        if (paused) {
            return context.getString(R.string.notif_progress_paused_percent, progress)
        }
        val parts = mutableListOf(context.getString(R.string.notif_progress_percent, progress))
        if (task.speedBytesPerSec > 0L) {
            parts += formatSpeed(task.speedBytesPerSec)
            val remainingBytes = (totalBytes - (totalBytes * progress / 100L)).coerceAtLeast(0L)
            val etaSeconds = remainingBytes / task.speedBytesPerSec
            if (etaSeconds in 1..86_399) {
                parts += context.getString(R.string.notif_eta_value, formatTime(etaSeconds))
            }
        }
        return parts.joinToString(separator)
    }

    fun showTaskCompleted(task: DownloadEntity, contentUri: Uri) {
        cancelTaskNotification(task.id)
        cancelTaskEventNotifications(task.id)
        if (!isEventNotificationsEnabled()) return

        val openFileIntent = openFilePendingIntent(task.id, contentUri)
        val contentIntent = openFileIntent ?: openDownloadsPendingIntent()

        val builder = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.completed_title))
            .setContentText(task.fileName)
            .setSubText(task.torrentTitle)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    task.fileName
                )
            )
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_menu_view,
                context.getString(R.string.open_title),
                contentIntent
            )
            .addAction(
                android.R.drawable.ic_menu_agenda,
                context.getString(R.string.download_manager_screen_title),
                openDownloadsPendingIntent()
            )

        nm.notify(completedNotificationId(task.id), builder.build())
    }

    fun showTaskError(task: DownloadEntity, message: String?) {
        cancelTaskNotification(task.id)
        cancelTaskEventNotifications(task.id)
        if (!isEventNotificationsEnabled()) return

        val details = message
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.notif_failed_generic)

        val builder = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.error_title))
            .setContentText(task.fileName)
            .setSubText(task.torrentTitle)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    task.fileName + "\n" + details
                )
            )
            .setContentIntent(openDownloadsPendingIntent())
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                android.R.drawable.ic_popup_sync,
                context.getString(R.string.retry_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_RESUME, task.id)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.cancel_title),
                serviceActionPendingIntent(DownloadServiceRouter.ACTION_CANCEL, task.id)
            )

        nm.notify(errorNotificationId(task.id), builder.build())
    }

    fun cancelTaskNotification(taskId: String) {
        nm.cancel(notificationIdFor(taskId))
        progressUpdateThrottler.remove(taskId)
        lastTaskSnapshots.remove(taskId)
        visibleTaskProgressIds.remove(taskId)
    }

    fun cancelTaskEventNotifications(taskId: String) {
        nm.cancel(completedNotificationId(taskId))
        nm.cancel(errorNotificationId(taskId))
    }

    fun syncTorrentGroupSummaries(tasks: List<DownloadEntity>) {
        if (tasks.isEmpty()) {
            progressUpdateThrottler.clear()
            lastTaskSnapshots.clear()
            visibleTaskProgressIds.clear()
        }
    }

    private fun shouldEmitTaskProgressUpdate(task: DownloadEntity, paused: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val current = TaskNotificationSnapshot(
            progress = task.progress.coerceIn(0, 100),
            paused = paused,
            speedBucket = speedBucket(task.speedBytesPerSec)
        )
        val previous = lastTaskSnapshots[task.id]
        val lastTs = progressUpdateThrottler[task.id] ?: 0L
        val intervalPassed = now - lastTs >= minProgressNotificationInterval

        val isSignificant = previous == null ||
            previous.paused != current.paused ||
            current.progress == 0 ||
            current.progress == 100 ||
            abs(current.progress - previous.progress) >= 2 ||
            previous.speedBucket != current.speedBucket

        val shouldEmit = isSignificant && (intervalPassed || previous?.paused != current.paused)
        if (shouldEmit) {
            lastTaskSnapshots[task.id] = current
            progressUpdateThrottler[task.id] = now
        }
        return shouldEmit
    }

    private fun updatePerTaskNotificationMode(activeTasksCount: Int) {
        val shouldEnable = activeTasksCount in 1..perTaskVisibilityThreshold
        if (shouldEnable == perTaskNotificationsEnabled) return
        perTaskNotificationsEnabled = shouldEnable
        if (!shouldEnable) {
            visibleTaskProgressIds.forEach { taskId -> nm.cancel(notificationIdFor(taskId)) }
            visibleTaskProgressIds.clear()
            progressUpdateThrottler.clear()
            lastTaskSnapshots.clear()
        }
    }

    private fun syncTaskProgressNotifications(activeTaskIds: Set<String>) {
        if (!perTaskNotificationsEnabled) return
        val staleTaskIds = visibleTaskProgressIds.filter { it !in activeTaskIds }
        staleTaskIds.forEach { taskId ->
            nm.cancel(notificationIdFor(taskId))
            visibleTaskProgressIds.remove(taskId)
            progressUpdateThrottler.remove(taskId)
            lastTaskSnapshots.remove(taskId)
        }
    }

    private fun buildSummaryText(
        runningCount: Int,
        queuedCount: Int,
        pausedCount: Int,
        totalSpeed: Long
    ): String {
        val parts = mutableListOf(
            context.getString(R.string.notif_running_count, runningCount),
            context.getString(R.string.notif_queued_count, queuedCount),
            context.getString(R.string.notif_paused_count, pausedCount)
        )
        if (totalSpeed > 0L) {
            parts += context.getString(R.string.notif_speed_value, formatSpeed(totalSpeed))
        }
        return parts.joinToString(separator)
    }

    private fun buildTorrentLine(title: String, tasks: List<DownloadEntity>): String {
        val running = tasks.count { it.status == DownloadStatus.RUNNING }
        val queued = tasks.count { it.status == DownloadStatus.QUEUED }
        val paused = tasks.count { it.status == DownloadStatus.PAUSED }
        val speed = tasks
            .asSequence()
            .filter { it.status == DownloadStatus.RUNNING }
            .sumOf { it.speedBytesPerSec.coerceAtLeast(0L) }

        val details = mutableListOf<String>()
        if (running > 0) details += context.getString(R.string.notif_running_count, running)
        if (queued > 0) details += context.getString(R.string.notif_queued_count, queued)
        if (paused > 0) details += context.getString(R.string.notif_paused_count, paused)
        if (speed > 0L) details += context.getString(R.string.notif_speed_value, formatSpeed(speed))
        if (details.isEmpty()) details += context.getString(R.string.no_active_downloads)

        return "$title: ${details.joinToString(separator)}"
    }

    private fun buildSummaryFingerprint(
        activeTasks: List<DownloadEntity>,
        running: Int,
        queued: Int,
        paused: Int,
        totalSpeed: Long
    ): String {
        val top = activeTasks
            .groupBy { it.torrentTitle }
            .entries
            .sortedByDescending { it.value.count { item -> item.status == DownloadStatus.RUNNING } }
            .take(4)
            .joinToString(separator = "|") { (title, list) ->
                "$title:${list.count { it.status == DownloadStatus.RUNNING }}:" +
                    "${list.count { it.status == DownloadStatus.QUEUED }}:" +
                    "${list.count { it.status == DownloadStatus.PAUSED }}"
            }
        val speedBucket = speedBucket(totalSpeed)
        return "$running:$queued:$paused:$speedBucket:$top"
    }

    private fun shouldDispatchSummary(fingerprint: String): Boolean {
        val now = System.currentTimeMillis()
        val changed = fingerprint != lastSummaryFingerprint
        if (changed) {
            lastSummaryFingerprint = fingerprint
            lastSummaryUpdateMs = now
            return true
        }
        if (now - lastSummaryUpdateMs >= minSummaryNotificationInterval) {
            lastSummaryUpdateMs = now
            return true
        }
        return false
    }

    private fun speedBucket(speed: Long): Int = when {
        speed <= 0L -> 0
        speed < 256L * 1024L -> 1
        speed < 2L * 1024L * 1024L -> 2
        else -> 3
    }

    private fun isProgressNotificationsEnabled(): Boolean =
        notificationPrefs.getBoolean(DownloadNotificationPrefs.KEY_PROGRESS_NOTIFICATIONS_ENABLED, true)

    private fun isEventNotificationsEnabled(): Boolean =
        notificationPrefs.getBoolean(DownloadNotificationPrefs.KEY_EVENT_NOTIFICATIONS_ENABLED, true)

    private fun notificationIdFor(taskId: String): Int =
        2_000 + (taskId.hashCode() and 0x1fffffff)

    private fun completedNotificationId(taskId: String): Int =
        40_000 + (taskId.hashCode() and 0x1fffffff)

    private fun errorNotificationId(taskId: String): Int =
        80_000 + (taskId.hashCode() and 0x1fffffff)

    private data class TaskNotificationSnapshot(
        val progress: Int,
        val paused: Boolean,
        val speedBucket: Int
    )

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val EVENTS_CHANNEL_ID = "download_events_channel"
        const val NOTIF_GROUP = "spotiflac_downloads"
        const val SUMMARY_ID = 1001
        private const val REQUEST_OPEN_DOWNLOADS = 1201
        private const val REQUEST_OPEN_FILE_BASE = 2200
    }
}
