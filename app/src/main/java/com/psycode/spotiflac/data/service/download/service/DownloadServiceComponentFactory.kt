package com.psycode.spotiflac.data.service.download.service

import android.os.PowerManager
import android.net.wifi.WifiManager
import com.frostwire.jlibtorrent.SessionManager
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.service.download.notification.AppNotificationManager
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import com.psycode.spotiflac.data.service.download.runtime.DownloadLifecycleCoordinator
import com.psycode.spotiflac.data.service.download.orchestration.DownloadOrchestrator
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.orchestration.DownloadTaskProcessor
import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import com.psycode.spotiflac.data.service.download.session.TorrentManager
import com.psycode.spotiflac.data.service.download.storage.FileManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class DownloadServiceComponentFactory @Inject constructor(
    private val dao: DownloadDao,
    private val stateStore: DownloadStateStore,
    private val notificationManager: AppNotificationManager,
    private val fileManager: FileManager,
) {
    data class Components(
        val torrentManager: TorrentManager,
        val taskProcessor: DownloadTaskProcessor,
        val orchestrator: DownloadOrchestrator,
        val lifecycleCoordinator: DownloadLifecycleCoordinator,
    )

    data class CreateParams(
        val config: DownloadConfig,
        val isDestroyed: AtomicBoolean,
        val semaphore: Semaphore,
        val wakeLock: PowerManager.WakeLock,
        val wifiLock: WifiManager.WifiLock?,
        val session: SessionManager,
        val scope: CoroutineScope,
        val filesDir: File,
        val speedCalculators: ConcurrentHashMap<String, SpeedCalculator>,
        val stopService: () -> Unit,
    )

    fun create(params: CreateParams): Components {
        val torrentManager = TorrentManager(params.session, params.filesDir)
        val taskProcessor = DownloadTaskProcessor(
            config = params.config,
            isDestroyed = params.isDestroyed,
            downloadRootDir = params.filesDir,
            torrentManager = torrentManager,
            notificationManager = notificationManager,
            fileManager = fileManager,
            stateStore = stateStore,
            speedCalculators = params.speedCalculators,
        )
        val orchestrator = DownloadOrchestrator(
            scope = params.scope,
            semaphore = params.semaphore,
            wakeLock = params.wakeLock,
            wifiLock = params.wifiLock,
            speedCalculators = params.speedCalculators,
            taskProcessor = taskProcessor,
            maxParallelDownloads = params.config.maxParallelDownloads,
            maxParallelPerTorrent = params.config.maxParallelPerTorrent,
            maxRetryAttempts = params.config.maxRetryAttempts,
        )
        val lifecycleCoordinator = DownloadLifecycleCoordinator(
            dao = dao,
            config = params.config,
            isDestroyed = params.isDestroyed,
            semaphore = params.semaphore,
            wakeLock = params.wakeLock,
            wifiLock = params.wifiLock,
            session = params.session,
            scope = params.scope,
            hasRunningTasks = { orchestrator.hasRunningTasks() },
            cancelAllJobs = { orchestrator.cancelAllJobs() },
            stateStore = stateStore,
            notificationManager = notificationManager,
            torrentManager = torrentManager,
            speedCalculators = params.speedCalculators,
            stopService = params.stopService,
        )
        orchestrator.setOnTaskSettled {
            orchestrator.processTaskList(stateStore.snapshot())
            lifecycleCoordinator.updateSummaryNotification()
            lifecycleCoordinator.stopIfNothingLeft()
        }

        return Components(
            torrentManager = torrentManager,
            taskProcessor = taskProcessor,
            orchestrator = orchestrator,
            lifecycleCoordinator = lifecycleCoordinator,
        )
    }
}

