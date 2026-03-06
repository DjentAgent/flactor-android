package com.psycode.spotiflac.data.service.download.orchestration

import com.frostwire.jlibtorrent.TorrentInfo
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.session.TorrentManager
import com.psycode.spotiflac.domain.model.DownloadStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class DownloadBootstrapper(
    deps: Dependencies,
) {
    data class Dependencies(
        val dao: DownloadDao,
        val config: DownloadConfig,
        val isDestroyed: AtomicBoolean,
        val orchestrator: DownloadOrchestrator,
        val stateStore: DownloadStateStore,
        val torrentManager: TorrentManager,
        val onSnapshotObserved: suspend (List<DownloadEntity>) -> Unit,
    )

    private val dao = deps.dao
    private val config = deps.config
    private val isDestroyed = deps.isDestroyed
    private val orchestrator = deps.orchestrator
    private val stateStore = deps.stateStore
    private val torrentManager = deps.torrentManager
    private val onSnapshotObserved = deps.onSnapshotObserved

    fun start() {
        DownloadLog.d("Bootstrapper start")
        launchBatchUpdater()
        launchSchedulerTick()
        launchCacheFlusher()
        launchMainObserver()
    }

    private fun launchBatchUpdater() {
        orchestrator.launch("batch-updater") {
            while (isActive && !isDestroyed.get()) {
                delay(config.batchUpdateIntervalMs)
                stateStore.flushPendingUpdates()
            }
        }
    }

    private fun launchSchedulerTick() {
        orchestrator.launch("scheduler-tick") {
            while (isActive && !isDestroyed.get()) {
                delay(SCHEDULER_TICK_MS)
                val rawSnapshot = dao.getAllSnapshot()
                val reconciledSnapshot = reconcileOrphanRunningSnapshot(
                    snapshot = rawSnapshot,
                    hasRuntimeRunningTasks = orchestrator.hasRunningTasks()
                )
                if (reconciledSnapshot !== rawSnapshot) {
                    val changed = reconciledSnapshot.filter { candidate ->
                        rawSnapshot.any { it.id == candidate.id && it != candidate }
                    }
                    changed.forEach { dao.upsert(it) }
                    DownloadLog.w(
                        "Recovered ${changed.size} orphan RUNNING task(s): RUNNING->QUEUED"
                    )
                }
                val snapshot = if (reconciledSnapshot === rawSnapshot) rawSnapshot else dao.getAllSnapshot()
                stateStore.updateTaskCache(snapshot)
                orchestrator.processTaskList(snapshot)
                onSnapshotObserved(snapshot)
            }
        }
    }

    private fun launchCacheFlusher() {
        orchestrator.launch("cache-flusher") {
            while (isActive && !isDestroyed.get()) {
                delay(config.flushCacheIntervalMs)
                orchestrator.runningTaskIds().forEach { taskId ->
                    stateStore.getCachedEntity(taskId)?.let { entity ->
                        if (entity.status == DownloadStatus.RUNNING) {
                            val ti =
                                runCatching { TorrentInfo(File(entity.torrentFilePath)) }.getOrNull()
                            ti?.let {
                                val key = it.infoHash().toString()
                                torrentManager.flushCache(key)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchMainObserver() {
        orchestrator.launch("main-observer") {
            recoverActiveStatesAfterProcessRestart()
            dao.observeAll()
                .distinctUntilChanged()
                .collectLatest { list ->
                    DownloadLog.d("Observer snapshot size=${list.size}")
                    stateStore.updateTaskCache(list)
                    orchestrator.processTaskList(list)
                    onSnapshotObserved(list)
                }
        }
    }

    private suspend fun recoverActiveStatesAfterProcessRestart() {
        val snapshot = dao.getAllSnapshot()
        if (snapshot.isEmpty()) return

        val recovered = reconcileRestartSnapshot(snapshot)
        val changed = recovered.filter { candidate ->
            snapshot.any { it.id == candidate.id && it != candidate }
        }
        if (changed.isEmpty()) return

        changed.forEach { dao.upsert(it) }
        DownloadLog.w(
            "Recovered ${changed.size} task(s) after process restart: " +
                "RUNNING->QUEUED, volatile speed reset"
        )
    }
}

internal fun reconcileRestartSnapshot(snapshot: List<DownloadEntity>): List<DownloadEntity> =
    snapshot.map { entity ->
        val recoveredStatus = if (entity.status == DownloadStatus.RUNNING) {
            DownloadStatus.QUEUED
        } else {
            entity.status
        }
        val shouldResetSpeed = recoveredStatus in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.PAUSED
        )
        val shouldClearError = recoveredStatus == DownloadStatus.QUEUED
        if (
            recoveredStatus == entity.status &&
            (!shouldResetSpeed || entity.speedBytesPerSec == 0L) &&
            (!shouldClearError || entity.errorMessage == null)
        ) {
            entity
        } else {
            entity.copy(
                status = recoveredStatus,
                speedBytesPerSec = if (shouldResetSpeed) 0L else entity.speedBytesPerSec,
                errorMessage = if (shouldClearError) null else entity.errorMessage
            )
        }
    }

private const val SCHEDULER_TICK_MS = 1_000L

internal fun reconcileOrphanRunningSnapshot(
    snapshot: List<DownloadEntity>,
    hasRuntimeRunningTasks: Boolean
): List<DownloadEntity> {
    if (snapshot.isEmpty() || hasRuntimeRunningTasks) return snapshot
    val hasDbRunning = snapshot.any { it.status == DownloadStatus.RUNNING }
    if (!hasDbRunning) return snapshot

    return snapshot.map { entity ->
        if (entity.status == DownloadStatus.RUNNING) {
            entity.copy(
                status = DownloadStatus.QUEUED,
                speedBytesPerSec = 0L,
                errorMessage = null
            )
        } else {
            entity
        }
    }
}



