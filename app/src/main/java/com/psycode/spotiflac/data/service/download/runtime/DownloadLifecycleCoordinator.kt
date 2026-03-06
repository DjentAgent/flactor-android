package com.psycode.spotiflac.data.service.download.runtime

import android.os.PowerManager
import android.net.wifi.WifiManager
import com.frostwire.jlibtorrent.SessionManager
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.service.download.notification.AppNotificationManager
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import com.psycode.spotiflac.data.service.download.session.TorrentManager
import com.psycode.spotiflac.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DownloadLifecycleCoordinator(
    private val dao: DownloadDao,
    private val config: DownloadConfig,
    private val isDestroyed: AtomicBoolean,
    private val semaphore: Semaphore,
    private val wakeLock: PowerManager.WakeLock,
    private val wifiLock: WifiManager.WifiLock?,
    private val session: SessionManager,
    private val scope: CoroutineScope,
    private val hasRunningTasks: () -> Boolean,
    private val cancelAllJobs: () -> Unit,
    private val stateStore: DownloadStateStore,
    private val notificationManager: AppNotificationManager,
    private val torrentManager: TorrentManager,
    private val speedCalculators: ConcurrentHashMap<String, SpeedCalculator>,
    private val stopService: () -> Unit
) {
    fun updateSummaryNotification() {
        val list = stateStore.snapshot()
        notificationManager.syncTorrentGroupSummaries(list)
        notificationManager.updateSummary(list)
    }

    fun stopIfNothingLeft() {
        val list = stateStore.snapshot()
        val anyActive = list.any {
            it.status in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED)
        }
        if (!anyActive && !isDestroyed.get()) {
            notificationManager.updateSummary(emptyList())
            stopService()
        }
    }

    suspend fun gracefulShutdown() {
        DownloadLog.d("Starting graceful shutdown")

        isDestroyed.set(true)

        repeat(config.maxParallelDownloads.coerceAtLeast(1)) {
            runCatching { semaphore.tryAcquire() }
        }

        withTimeoutOrNull(config.gracefulShutdownTimeoutMs) {
            while (hasRunningTasks()) {
                delay(100)
            }
        }

        stateStore.snapshot()
            .filter { it.status == DownloadStatus.RUNNING }
            .forEach {
                runCatching {
                    dao.upsert(it.copy(status = DownloadStatus.PAUSED, speedBytesPerSec = 0))
                }
            }

        stateStore.flushPendingUpdates()
        cancelAllJobs()

        torrentManager.cleanup()
        speedCalculators.clear()
        stateStore.clear()

        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock.release()
        }

        session.stop()
        scope.cancel()

        DownloadLog.d("Graceful shutdown completed")
    }
}




