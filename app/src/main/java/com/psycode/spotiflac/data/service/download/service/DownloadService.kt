package com.psycode.spotiflac.data.service.download.service

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import com.frostwire.jlibtorrent.SessionManager
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.service.download.notification.AppNotificationManager
import com.psycode.spotiflac.data.service.download.orchestration.DownloadBootstrapper
import com.psycode.spotiflac.data.service.download.orchestration.DownloadCommandHandler
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import com.psycode.spotiflac.data.service.download.runtime.DownloadLifecycleCoordinator
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.orchestration.DownloadOrchestrator
import com.psycode.spotiflac.data.service.download.runtime.DownloadRuntimeInfraFactory
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.session.DownloadNetworkMonitor
import com.psycode.spotiflac.data.service.download.session.DownloadSessionAlertMonitor
import com.psycode.spotiflac.data.service.download.session.DownloadSessionFactory
import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import com.psycode.spotiflac.data.service.download.session.TorrentManager
import com.psycode.spotiflac.data.service.download.watchdog.DownloadWatchdogScheduler
import com.psycode.spotiflac.data.service.download.watchdog.hasActiveTransfers
import com.psycode.spotiflac.domain.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var dao: DownloadDao
    @Inject
    lateinit var stateStore: DownloadStateStore
    @Inject
    lateinit var notificationManager: AppNotificationManager
    @Inject
    lateinit var commandHandler: DownloadCommandHandler
    @Inject
    lateinit var componentFactory: DownloadServiceComponentFactory
    @Inject
    lateinit var serviceRouter: DownloadServiceRouter
    @Inject
    lateinit var runtimeInfraFactory: DownloadRuntimeInfraFactory
    @Inject
    lateinit var sessionFactory: DownloadSessionFactory
    @Inject
    lateinit var watchdogScheduler: DownloadWatchdogScheduler

    private lateinit var config: DownloadConfig
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var session: SessionManager
    private lateinit var semaphore: Semaphore
    private lateinit var wakeLock: PowerManager.WakeLock
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var torrentManager: TorrentManager
    private lateinit var orchestrator: DownloadOrchestrator
    private lateinit var lifecycleCoordinator: DownloadLifecycleCoordinator
    private lateinit var bootstrapper: DownloadBootstrapper
    private lateinit var networkMonitor: DownloadNetworkMonitor
    private lateinit var alertMonitor: DownloadSessionAlertMonitor

    private val isDestroyed = AtomicBoolean(false)
    private val speedCalculators = ConcurrentHashMap<String, SpeedCalculator>()
    private val lastSessionRecoverAtMs = AtomicLong(0L)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        config = DownloadConfig.Companion.fromPreferences(this)
        session = sessionFactory.create(config)
        alertMonitor = DownloadSessionAlertMonitor(session) { reason ->
            scope.launch { recoverSession(reason) }
        }
        networkMonitor = DownloadNetworkMonitor(applicationContext) { reason ->
            scope.launch { recoverSession("network_$reason") }
        }
        alertMonitor.start()
        networkMonitor.start()
        DownloadLog.d(
            "Service onCreate; maxParallel=${config.maxParallelDownloads}, " +
                "maxPerTorrent=${config.maxParallelPerTorrent}, " +
                "batch=${config.batchUpdateIntervalMs}ms, retry=${config.maxRetryAttempts}"
        )
        val infra = runtimeInfraFactory.create(config.maxParallelDownloads)
        semaphore = infra.semaphore
        wakeLock = infra.wakeLock
        wifiLock = infra.wifiLock

        val components = componentFactory.create(
            params = DownloadServiceComponentFactory.CreateParams(
                config = config,
                isDestroyed = isDestroyed,
                semaphore = semaphore,
                wakeLock = wakeLock,
                wifiLock = wifiLock,
                session = session,
                scope = scope,
                filesDir = filesDir,
                speedCalculators = speedCalculators,
                stopService = {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                },
            ),
        )
        torrentManager = components.torrentManager
        orchestrator = components.orchestrator
        lifecycleCoordinator = components.lifecycleCoordinator
        bootstrapper = DownloadBootstrapper(
            deps = DownloadBootstrapper.Dependencies(
                dao = dao,
                config = config,
                isDestroyed = isDestroyed,
                orchestrator = orchestrator,
                stateStore = stateStore,
                torrentManager = torrentManager,
                onSnapshotObserved = { snapshot ->
                    watchdogScheduler.sync(snapshot)
                    lifecycleCoordinator.updateSummaryNotification()
                },
            ),
        )

        startForeground(
            AppNotificationManager.Companion.SUMMARY_ID,
            notificationManager.updateSummary(emptyList())
        )
        bootstrapper.start()
        DownloadLog.d("Service started and bootstrapper initialized")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadLog.d("onStartCommand action=${intent?.action ?: "null"} startId=$startId")
        serviceRouter.dispatch(
            intent = intent,
            handlers = DownloadServiceRouter.ActionHandlers(
                onPause = ::handlePauseAction,
                onResume = ::handleResumeAction,
                onCancel = ::handleCancelAction,
                onPauseGroup = ::handlePauseGroupAction,
                onResumeGroup = ::handleResumeGroupAction,
                onPauseAll = ::handlePauseAllAction,
                onResumeAll = ::handleResumeAllAction,
                onAppForeground = ::handleAppForegroundAction,
            ),
        )
        return START_STICKY
    }

    override fun onDestroy() {
        DownloadLog.d("Service onDestroy requested")
        isDestroyed.set(true)
        networkMonitor.stop()
        alertMonitor.stop()
        scope.launch { lifecycleCoordinator.gracefulShutdown() }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        DownloadLog.d("onTaskRemoved received")
        scope.launch {
            val hasActive = runCatching {
                hasActiveTransfers(dao.getAllSnapshot())
            }.getOrDefault(false)
            if (hasActive) {
                DownloadLog.d("onTaskRemoved: active downloads detected, ensuring service restart")
                watchdogScheduler.ensureScheduled()
                DownloadServiceRouter.Companion.ensureStarted(applicationContext)
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun handlePauseAction(taskId: String?) {
        taskId ?: return
        DownloadLog.d("Pause action taskId=$taskId")
        orchestrator.launch("pause-$taskId") {
            val updated = commandHandler.pause(taskId)
            if (updated) {
                orchestrator.onUserPaused(taskId)
            }
            DownloadLog.t(
                scope = "Service",
                message = "pauseAction taskId=$taskId updated=$updated"
            )
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private suspend fun recoverSession(reason: String) {
        if (isDestroyed.get()) return
        val now = System.currentTimeMillis()
        val cooldown = if (reason.startsWith("network_")) NETWORK_RECOVERY_COOLDOWN_MS else SESSION_RECOVERY_COOLDOWN_MS
        val last = lastSessionRecoverAtMs.get()
        if (now - last < cooldown) {
            DownloadLog.t(scope = "Service", message = "skip recover reason=$reason cooldownMs=$cooldown")
            return
        }
        if (!lastSessionRecoverAtMs.compareAndSet(last, now)) return
        DownloadLog.w("SESSION_RECOVER reason=$reason")
        runCatching { session.reopenNetworkSockets() }
            .onFailure { DownloadLog.e("reopenNetworkSockets failed reason=$reason", it) }
        if (!runCatching { session.isDhtRunning() }.getOrDefault(false)) {
            runCatching { session.startDht() }
                .onFailure { DownloadLog.e("startDht failed during recover reason=$reason", it) }
        }
    }

    private fun handleResumeAction(taskId: String?) {
        taskId ?: return
        DownloadLog.d("Resume action taskId=$taskId")
        orchestrator.launch("resume-$taskId") {
            val updated = commandHandler.resume(taskId)
            if (updated) {
                orchestrator.onUserResumed(taskId)
            } else {
                val cacheStatus = stateStore.getCachedEntity(taskId)?.status
                val dbStatus = runCatching { dao.getById(taskId)?.status }.getOrNull()
                DownloadLog.d(
                    "resumeAction not applied taskId=$taskId cacheStatus=$cacheStatus dbStatus=$dbStatus"
                )
            }
            DownloadLog.t(
                scope = "Service",
                message = "resumeAction taskId=$taskId updated=$updated"
            )
            if (updated) {
                DownloadServiceRouter.Companion.ensureStarted(this@DownloadService)
            }
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private fun handleCancelAction(taskId: String?) {
        taskId ?: return
        DownloadLog.d("Cancel action taskId=$taskId")
        orchestrator.launch("cancel-$taskId") {
            val updated = commandHandler.cancel(taskId)
            DownloadLog.t(
                scope = "Service",
                message = "cancelAction taskId=$taskId updated=$updated"
            )
            lifecycleCoordinator.updateSummaryNotification()
            lifecycleCoordinator.stopIfNothingLeft()
        }
    }

    private fun handlePauseAllAction() {
        DownloadLog.d("Pause all action")
        orchestrator.launch("pause-all") {
            val targets = stateStore.snapshot()
                .asSequence()
                .filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                .map { it.id }
                .distinct()
                .toList()
            targets.forEach { taskId ->
                if (commandHandler.pause(taskId)) {
                    orchestrator.onUserPaused(taskId)
                }
            }
            DownloadLog.t(scope = "Service", message = "pauseAll targets=${targets.size}")
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private fun handleResumeAllAction() {
        DownloadLog.d("Resume all action")
        orchestrator.launch("resume-all") {
            val targets = stateStore.snapshot()
                .asSequence()
                .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                .map { it.id }
                .distinct()
                .toList()
            targets.forEach { taskId ->
                if (commandHandler.resume(taskId)) {
                    orchestrator.onUserResumed(taskId)
                }
            }
            if (targets.isNotEmpty()) {
                DownloadServiceRouter.Companion.ensureStarted(this@DownloadService)
            }
            DownloadLog.t(scope = "Service", message = "resumeAll targets=${targets.size}")
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private fun handlePauseGroupAction(topicId: Int) {
        if (topicId <= 0) return
        DownloadLog.d("Pause group action topicId=$topicId")
        orchestrator.launch("pause-group-$topicId") {
            val prefix = "${topicId}_"
            val taskIds = dao.getByTopicPrefix(prefix)
                .asSequence()
                .filter { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED }
                .map { it.id }
                .distinct()
                .toList()
            DownloadLog.t(
                scope = "Service",
                message = "pauseGroupAction topicId=$topicId targets=${taskIds.size}"
            )
            taskIds.forEach { taskId ->
                if (commandHandler.pause(taskId)) {
                    orchestrator.onUserPaused(taskId)
                }
            }
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private fun handleResumeGroupAction(topicId: Int) {
        if (topicId <= 0) return
        DownloadLog.d("Resume group action topicId=$topicId")
        orchestrator.launch("resume-group-$topicId") {
            val prefix = "${topicId}_"
            val taskIds = dao.getByTopicPrefix(prefix)
                .asSequence()
                .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
                .map { it.id }
                .distinct()
                .toList()
            DownloadLog.t(
                scope = "Service",
                message = "resumeGroupAction topicId=$topicId targets=${taskIds.size}"
            )
            taskIds.forEach { taskId ->
                if (commandHandler.resume(taskId)) {
                    orchestrator.onUserResumed(taskId)
                }
            }
            if (taskIds.isNotEmpty()) {
                DownloadServiceRouter.Companion.ensureStarted(this@DownloadService)
            }
            lifecycleCoordinator.updateSummaryNotification()
        }
    }

    private fun handleAppForegroundAction() {
        DownloadLog.d("App foreground action received")
        orchestrator.launch("app-foreground-sync") {
            val snapshot = dao.getAllSnapshot()
            val runningCount = snapshot.count { it.status == DownloadStatus.RUNNING }
            val queuedCount = snapshot.count { it.status == DownloadStatus.QUEUED }
            if (!hasActiveTransfers(snapshot)) {
                DownloadLog.t(scope = "Service", message = "appForeground skip reason=no_active")
                return@launch
            }
            DownloadLog.d(
                "appForeground sync running=$runningCount queued=$queuedCount " +
                    "runtimeRunning=${orchestrator.runningTaskIds().size}"
            )
            stateStore.updateTaskCache(snapshot)
            orchestrator.processTaskList(snapshot)
            recoverSession("app_foreground")
            lifecycleCoordinator.updateSummaryNotification()
        }
    }
}

private const val SESSION_RECOVERY_COOLDOWN_MS = 20_000L
private const val NETWORK_RECOVERY_COOLDOWN_MS = 60_000L

internal fun isUiForegroundImportance(importance: Int?): Boolean =
    importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
        importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE

