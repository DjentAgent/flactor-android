package com.psycode.spotiflac.data.service.download.core

import android.content.Context

data class DownloadConfig(
    val maxParallelDownloads: Int = 5,
    val maxParallelPerTorrent: Int = 5,
    val progressUpdateIntervalMs: Long = 1000,
    val speedSmoothingFactor: Double = 0.3,
    val maxRetryAttempts: Int = 3,
    val batchUpdateIntervalMs: Long = 2000,
    val minNotificationInterval: Long = 1000,
    val speedWindowSize: Int = 10,
    val gracefulShutdownTimeoutMs: Long = 15000,
    val fileWaitTimeoutMs: Long = 30000,
    val flushCacheIntervalMs: Long = 5000
) {
    companion object {
        private fun defaultPrefs(context: Context) =
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)

        fun fromPreferences(context: Context): DownloadConfig {
            val prefs = defaultPrefs(context)
            val maxParallel = prefs.getInt("max_parallel_downloads", 5).coerceAtLeast(1)
            val maxPerTorrent = prefs
                .getInt("max_parallel_per_torrent", 5)
                .coerceAtLeast(1)
                .coerceAtMost(maxParallel)
            val progressInterval = prefs.getLong("progress_update_interval", 1000).coerceAtLeast(250)
            val retryAttempts = prefs.getInt("max_retry_attempts", 3).coerceAtLeast(1)
            val batchInterval = prefs.getLong("batch_update_interval", 2000).coerceAtLeast(500)
            val notifInterval = prefs.getLong("min_notification_interval", 1000).coerceAtLeast(250)
            return DownloadConfig(
                maxParallelDownloads = maxParallel,
                maxParallelPerTorrent = maxPerTorrent,
                progressUpdateIntervalMs = progressInterval,
                maxRetryAttempts = retryAttempts,
                batchUpdateIntervalMs = batchInterval,
                minNotificationInterval = notifInterval
            )
        }
    }
}




