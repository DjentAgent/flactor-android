package com.psycode.spotiflac.data.service.download.orchestration

import android.net.Uri
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.notification.AppNotificationManager
import com.psycode.spotiflac.data.service.download.core.DownloadConfig
import com.psycode.spotiflac.data.service.download.core.DownloadException
import com.psycode.spotiflac.data.service.download.core.DownloadLog
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.session.SpeedCalculator
import com.psycode.spotiflac.data.service.download.session.TorrentManager
import com.psycode.spotiflac.data.service.download.storage.FileManager
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.SaveOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class DownloadTaskProcessor(
    private val config: DownloadConfig,
    private val isDestroyed: AtomicBoolean,
    private val downloadRootDir: File,
    private val torrentManager: TorrentManager,
    private val notificationManager: AppNotificationManager,
    private val fileManager: FileManager,
    private val stateStore: DownloadStateStore,
    private val speedCalculators: ConcurrentHashMap<String, SpeedCalculator>
) {
    private val stallRequeueCounts = ConcurrentHashMap<String, Int>()
    private val maxStallRequeuesPerTask = 1

    suspend fun runTaskWithRetry(
        entity: DownloadEntity,
        maxRetries: Int = 3,
        semaphore: Semaphore? = null
    ) {
        DownloadLog.d("runTaskWithRetry start task=${entity.id} retries=$maxRetries")
        repeat(maxRetries) { attempt ->
            try {
                validateEntity(entity).getOrThrow()
                if (semaphore != null) {
                    DownloadLog.d("Task ${entity.id} waiting for permit")
                    semaphore.withPermit {
                        DownloadLog.d("Task ${entity.id} acquired permit")
                        runTask(entity)
                    }
                } else {
                    runTask(entity)
                }
                DownloadLog.d("runTaskWithRetry success task=${entity.id} attempt=${attempt + 1}")
                stallRequeueCounts.remove(entity.id)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadLog.e("Task ${entity.id} attempt ${attempt + 1} failed", e)
                val latest = stateStore.getCachedEntity(entity.id)
                if (latest?.status == DownloadStatus.PAUSED) {
                    DownloadLog.t(
                        scope = "Processor",
                        message = "skipRetryAfterPause taskId=${entity.id} attempt=${attempt + 1}"
                    )
                    stallRequeueCounts.remove(entity.id)
                    return
                }
                if (latest?.status == DownloadStatus.CANCELED) {
                    DownloadLog.t(
                        scope = "Processor",
                        message = "skipRetryAfterCancel taskId=${entity.id} attempt=${attempt + 1}"
                    )
                    stallRequeueCounts.remove(entity.id)
                    return
                }
                if (isRecoverableStorageMaterializationTimeout(e)) {
                    stallRequeueCounts.remove(entity.id)
                    val base = stateStore.getCachedEntity(entity.id) ?: entity
                    val msg = "Materialization timeout: ${e.message}"
                    stateStore.stageUpdate(
                        base.copy(
                            status = DownloadStatus.FAILED,
                            speedBytesPerSec = 0,
                            errorMessage = msg
                        )
                    )
                    stateStore.flushPendingUpdates()
                    notificationManager.showTaskError(base, msg)
                    DownloadLog.w("Task ${entity.id} marked FAILED due to materialization timeout")
                    return
                }
                if (attempt == maxRetries - 1) {
                    if (shouldSoftRequeue(entity, e)) {
                        recoverHandleForTask(entity, e)
                        val requeueCount = (stallRequeueCounts[entity.id] ?: 0) + 1
                        stallRequeueCounts[entity.id] = requeueCount
                        val cached = stateStore.getCachedEntity(entity.id) ?: entity
                        stateStore.stageUpdate(
                            cached.copy(
                                status = DownloadStatus.QUEUED,
                                speedBytesPerSec = 0,
                                errorMessage = null
                            )
                        )
                        stateStore.flushPendingUpdates()
                        DownloadLog.w(
                            "Task ${entity.id} soft-requeued after session stall " +
                                "($requeueCount/$maxStallRequeuesPerTask)"
                        )
                        return
                    }
                    stallRequeueCounts.remove(entity.id)
                    val msg = "Failed after $maxRetries attempts: ${e.message}"
                    stateStore.stageUpdate(
                        entity.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = msg
                        )
                    )
                    notificationManager.showTaskError(entity, msg)
                } else {
                    delay(2000L * (attempt + 1))
                }
            }
        }
    }

    private fun validateEntity(entity: DownloadEntity): Result<Unit> = when {
        !File(entity.torrentFilePath).exists() ->
            Result.failure(DownloadException.TorrentNotFound(entity.torrentFilePath))

        entity.innerPath.isBlank() ->
            Result.failure(DownloadException.ValidationError("Inner path is blank"))

        entity.saveOption !in SaveOption.values().map { it.name } ->
            Result.failure(DownloadException.ValidationError("Invalid save option"))

        else -> Result.success(Unit)
    }

    private suspend fun runTask(initialEntity: DownloadEntity) {
        DownloadLog.d(
            "runTask start id=${initialEntity.id} innerPath=${initialEntity.innerPath} " +
                "saveOption=${initialEntity.saveOption}"
        )
        val ti = TorrentInfo(File(initialEntity.torrentFilePath))
        val isPrivateTorrent = ti.isPrivate
        val numFiles = ti.files().numFiles()
        val filePaths = (0 until numFiles).map { idx -> ti.files().filePath(idx) }
        val fileIndex = findTorrentFileIndexByInnerPath(filePaths, initialEntity.innerPath)
        if (fileIndex == null) {
            DownloadLog.w(buildFileNotFoundDiagnostics(filePaths, initialEntity.innerPath))
            throw DownloadException.FileNotFound(initialEntity.innerPath)
        }
        DownloadLog.d("Resolved torrent fileIndex=$fileIndex numFiles=$numFiles task=${initialEntity.id}")

        val (key, initialHandle) = torrentManager.getOrCreateHandle(ti)
        var handle = initialHandle
        DownloadLog.d("Handle acquired key=$key task=${initialEntity.id}")
        val speedCalc =
            speedCalculators.getOrPut(initialEntity.id) { SpeedCalculator(config.speedWindowSize) }

        try {
            val started = stateStore.updateTaskIfAndFlush(
                taskId = initialEntity.id,
                predicate = { statusEntity ->
                    statusEntity.status == DownloadStatus.RUNNING ||
                        statusEntity.status == DownloadStatus.QUEUED
                },
                transform = { statusEntity ->
                    statusEntity.copy(status = DownloadStatus.RUNNING)
                }
            )
            var current = started ?: (stateStore.getCachedEntity(initialEntity.id) ?: initialEntity)
            if (started != null) {
                torrentManager.addActiveFile(key, fileIndex)
                torrentManager.applyPriorities(key)
            }

            val totalBytes = ti.files().fileSize(fileIndex)
            var wasPaused = (current.status == DownloadStatus.PAUSED)
            var lastProgress = -1
            var completedSinceMs: Long? = null
            var zeroProgressMs = 0L
            var noMovementMs = 0L
            var lastProgressBytes = -1L
            var lastLoggedProgressBucket = -1
            var lastLoopMs = System.currentTimeMillis()
            var nextZeroWarnAtMs = ZERO_PROGRESS_FIRST_WARN_MS
            var nextNoMovementWarnAtMs = NO_MOVEMENT_FIRST_WARN_MS
            var zeroProgressStartedAtMs: Long? = null
            var noMovementStartedAtMs: Long? = null
            var zeroProgressStallActive = false
            var noMovementStallActive = false
            var zeroProgressRecoveryAttempt = 0
            var noMovementRecoveryAttempt = 0
            var exitedByPause = false
            var removedFromState = false

            while (!isDestroyed.get()) {
                val cachedCurrent = stateStore.getCachedEntity(initialEntity.id)
                if (cachedCurrent == null) {
                    removedFromState = true
                    DownloadLog.w(
                        "Task ${initialEntity.id} disappeared from state cache; " +
                            "stopping task loop without finalization"
                    )
                    break
                }
                current = cachedCurrent
                val nowLoopMs = System.currentTimeMillis()
                val elapsedMs = (nowLoopMs - lastLoopMs).coerceIn(250L, 5_000L)
                lastLoopMs = nowLoopMs

                if (!handle.isValid) {
                    val refreshed = torrentManager.getHandle(key)
                    if (refreshed != null && refreshed.isValid) {
                        handle = refreshed
                        DownloadLog.w("Task ${initialEntity.id} rebound to recovered handle key=$key")
                    } else {
                        throw DownloadException.SessionError(
                            RuntimeException("Torrent handle became invalid for task ${initialEntity.id}")
                        )
                    }
                }

                when (current.status) {
                    DownloadStatus.PAUSED -> {
                        if (!wasPaused) {
                            torrentManager.removeActiveFile(key, fileIndex)
                            torrentManager.applyPriorities(key)
                            wasPaused = true
                            speedCalc.reset()
                            val latestForPause = stateStore.getCachedEntity(initialEntity.id)
                            if (latestForPause?.status == DownloadStatus.PAUSED) {
                                stateStore.stageUpdate(
                                    latestForPause.copy(speedBytesPerSec = 0)
                                )
                            } else {
                                DownloadLog.t(
                                    scope = "Processor",
                                    message = "skipPauseOverwrite taskId=${initialEntity.id} " +
                                        "latestStatus=${latestForPause?.status}"
                                )
                            }
                            DownloadLog.d("Task ${initialEntity.id} paused")
                            DownloadLog.t(
                                scope = "Processor",
                                message = "state taskId=${initialEntity.id} transitioned=PAUSED fileIndex=$fileIndex"
                            )
                        }
                        val latestAfterPause = stateStore.getCachedEntity(initialEntity.id)
                        if (latestAfterPause != null && latestAfterPause.status != DownloadStatus.PAUSED) {
                            current = latestAfterPause
                            DownloadLog.t(
                                scope = "Processor",
                                message = "pauseRaceRecovered taskId=${initialEntity.id} " +
                                    "latestStatus=${latestAfterPause.status}"
                            )
                            delay(120)
                            continue
                        }
                        notificationManager.updateTaskProgress(current, paused = true, totalBytes)
                        // Exit paused tasks to release orchestration slot/semaphore permit.
                        // Resume will requeue and restart the task loop explicitly.
                        exitedByPause = true
                        break
                    }

                    DownloadStatus.CANCELED, DownloadStatus.FAILED, DownloadStatus.COMPLETED -> {
                        DownloadLog.d("Task ${initialEntity.id} terminal status=${current.status}")
                        break
                    }

                    else -> {
                        if (wasPaused) {
                            torrentManager.addActiveFile(key, fileIndex)
                            torrentManager.applyPriorities(key)
                            wasPaused = false
                            speedCalc.reset()
                            DownloadLog.d("Task ${initialEntity.id} resumed")
                            DownloadLog.t(
                                scope = "Processor",
                                message = "state taskId=${initialEntity.id} transitioned=RUNNING fileIndex=$fileIndex"
                            )
                        }
                    }
                }

                val progressBytes = runCatching {
                    handle.fileProgress().getOrNull(fileIndex) ?: 0L
                }.getOrElse { progressError ->
                    val refreshed = torrentManager.getHandle(key)
                    if (refreshed != null && refreshed.isValid) {
                        handle = refreshed
                        DownloadLog.w(
                            "Task ${initialEntity.id} rebound after fileProgress failure key=$key"
                        )
                        runCatching { handle.fileProgress().getOrNull(fileIndex) ?: 0L }
                            .getOrDefault(0L)
                    } else {
                        throw DownloadException.SessionError(progressError)
                    }
                }
                val percent = if (totalBytes <= 0L) 0 else {
                    ((progressBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                }
                val now = System.currentTimeMillis()
                val speed = speedCalc.addMeasurement(now, progressBytes)
                val handleRateBytesPerSec = runCatching {
                    handle.status(false).downloadRate().toLong().coerceAtLeast(0L)
                }.getOrDefault(0L)
                val progressBucket = percent / 10
                if (progressBucket > lastLoggedProgressBucket) {
                    DownloadLog.d(
                        "Task ${initialEntity.id} progress=$percent% bytes=$progressBytes/$totalBytes speed=$speed"
                    )
                    lastLoggedProgressBucket = progressBucket
                }

                val transportSeemsActive = handleRateBytesPerSec > HANDLE_RATE_ACTIVITY_THRESHOLD_BYTES
                if (progressBytes == 0L && speed <= 0L && percent == 0 && !transportSeemsActive) {
                    if (zeroProgressStartedAtMs == null) {
                        zeroProgressStartedAtMs = nowLoopMs
                    }
                    zeroProgressMs += elapsedMs
                    if (zeroProgressMs >= nextZeroWarnAtMs) {
                        zeroProgressRecoveryAttempt += 1
                        if (!zeroProgressStallActive) {
                            zeroProgressStallActive = true
                            DownloadLog.w(
                                "STALL_START task=${initialEntity.id} key=${key.take(8)} " +
                                    "type=zero_progress elapsed=${zeroProgressMs}ms percent=$percent " +
                                    "bytes=$progressBytes speed=$speed " +
                                    "${buildHandleSnapshot(handle)}"
                            )
                        }
                        nextZeroWarnAtMs += STALL_WARN_REPEAT_MS
                        torrentManager.applyPriorities(key)
                        runCatching { handle.resume() }
                        val refreshed = torrentManager.refreshPeerDiscovery(
                            key = key,
                            reason = "zero_progress task=${initialEntity.id} elapsed=${zeroProgressMs}ms",
                            allowDhtAnnounce = !isPrivateTorrent
                        )
                        var nudged = false
                        if (zeroProgressMs >= STALL_ESCALATION_THRESHOLD_MS) {
                            nudged = torrentManager.nudgeHandle(
                                key = key,
                                reason = "zero_progress_escalation task=${initialEntity.id} elapsed=${zeroProgressMs}ms",
                                minIntervalMs = STALL_ESCALATION_COOLDOWN_MS
                            )
                        }
                        DownloadLog.w(
                            "STALL_STEP task=${initialEntity.id} key=${key.take(8)} type=zero_progress " +
                                "attempt=$zeroProgressRecoveryAttempt elapsed=${zeroProgressMs}ms percent=$percent " +
                                "bytes=$progressBytes speed=$speed refreshed=$refreshed nudged=$nudged " +
                                "${buildHandleSnapshot(handle)}"
                        )
                    }
                    if (zeroProgressMs >= zeroProgressTimeoutMs()) {
                        throw DownloadException.SessionError(
                            RuntimeException("No progress for too long at 0%")
                        )
                    }
                } else {
                    maybeLogStallResolved(
                        active = zeroProgressStallActive,
                        taskId = initialEntity.id,
                        key = key,
                        type = "zero_progress",
                        startedAtMs = zeroProgressStartedAtMs,
                        nowMs = nowLoopMs,
                        percent = percent,
                        progressBytes = progressBytes,
                        speed = speed,
                        handle = handle
                    )
                    zeroProgressStartedAtMs = null
                    zeroProgressStallActive = false
                    zeroProgressRecoveryAttempt = 0
                    zeroProgressMs = 0L
                    nextZeroWarnAtMs = ZERO_PROGRESS_FIRST_WARN_MS
                }

                if (percent < 100) {
                    if (lastProgressBytes >= 0L && progressBytes <= lastProgressBytes && !transportSeemsActive) {
                        if (noMovementStartedAtMs == null) {
                            noMovementStartedAtMs = nowLoopMs
                        }
                        noMovementMs += elapsedMs
                        if (noMovementMs >= nextNoMovementWarnAtMs) {
                            noMovementRecoveryAttempt += 1
                            if (!noMovementStallActive) {
                                noMovementStallActive = true
                                DownloadLog.w(
                                    "STALL_START task=${initialEntity.id} key=${key.take(8)} " +
                                        "type=no_movement elapsed=${noMovementMs}ms percent=$percent " +
                                        "bytes=$progressBytes speed=$speed " +
                                        "${buildHandleSnapshot(handle)}"
                                )
                            }
                            nextNoMovementWarnAtMs += STALL_WARN_REPEAT_MS
                            torrentManager.applyPriorities(key)
                            runCatching { handle.resume() }
                            val refreshed = torrentManager.refreshPeerDiscovery(
                                key = key,
                                reason = "no_movement task=${initialEntity.id} elapsed=${noMovementMs}ms percent=$percent",
                                allowDhtAnnounce = !isPrivateTorrent
                            )
                            var nudged = false
                            if (noMovementMs >= STALL_ESCALATION_THRESHOLD_MS) {
                                nudged = torrentManager.nudgeHandle(
                                    key = key,
                                    reason = "no_movement_escalation task=${initialEntity.id} elapsed=${noMovementMs}ms percent=$percent",
                                    minIntervalMs = STALL_ESCALATION_COOLDOWN_MS
                                )
                            }
                            var recovered = false
                            if (noMovementMs >= HANDLE_RECOVERY_THRESHOLD_MS) {
                                recovered = torrentManager.recoverHandle(
                                    key = key,
                                    reason = "no_movement_handle_recover task=${initialEntity.id} elapsed=${noMovementMs}ms percent=$percent"
                                )
                                if (recovered) {
                                    val recoveredHandle = torrentManager.getHandle(key)
                                    if (recoveredHandle != null && recoveredHandle.isValid) {
                                        val stallElapsedMs = noMovementMs
                                        handle = recoveredHandle
                                        noMovementMs = 0L
                                        zeroProgressMs = 0L
                                        lastProgressBytes = -1L
                                        speedCalc.reset()
                                        nextNoMovementWarnAtMs = NO_MOVEMENT_FIRST_WARN_MS
                                        nextZeroWarnAtMs = ZERO_PROGRESS_FIRST_WARN_MS
                                        zeroProgressStartedAtMs = null
                                        noMovementStartedAtMs = null
                                        zeroProgressStallActive = false
                                        noMovementStallActive = false
                                        zeroProgressRecoveryAttempt = 0
                                        noMovementRecoveryAttempt = 0
                                        DownloadLog.w(
                                            "STALL_ESCALATED task=${initialEntity.id} key=${key.take(8)} " +
                                                "type=no_movement action=recover_handle success=true " +
                                                "elapsed=${stallElapsedMs}ms percent=$percent " +
                                                "${buildHandleSnapshot(handle)}"
                                        )
                                        delay(200)
                                        continue
                                    }
                                }
                            }
                            DownloadLog.w(
                                "STALL_STEP task=${initialEntity.id} key=${key.take(8)} type=no_movement " +
                                    "attempt=$noMovementRecoveryAttempt elapsed=${noMovementMs}ms percent=$percent " +
                                    "bytes=$progressBytes speed=$speed refreshed=$refreshed nudged=$nudged " +
                                    "recovered=$recovered ${buildHandleSnapshot(handle)}"
                            )
                        }
                        if (noMovementMs >= noMovementTimeoutMs(percent)) {
                            throw DownloadException.SessionError(
                                RuntimeException("No byte movement for too long")
                            )
                        }
                    } else {
                        maybeLogStallResolved(
                            active = noMovementStallActive,
                            taskId = initialEntity.id,
                            key = key,
                            type = "no_movement",
                            startedAtMs = noMovementStartedAtMs,
                            nowMs = nowLoopMs,
                            percent = percent,
                            progressBytes = progressBytes,
                            speed = speed,
                            handle = handle
                        )
                        noMovementStartedAtMs = null
                        noMovementStallActive = false
                        noMovementRecoveryAttempt = 0
                        noMovementMs = 0L
                        nextNoMovementWarnAtMs = NO_MOVEMENT_FIRST_WARN_MS
                    }
                } else {
                    maybeLogStallResolved(
                        active = noMovementStallActive,
                        taskId = initialEntity.id,
                        key = key,
                        type = "no_movement",
                        startedAtMs = noMovementStartedAtMs,
                        nowMs = nowLoopMs,
                        percent = percent,
                        progressBytes = progressBytes,
                        speed = speed,
                        handle = handle
                    )
                    noMovementStartedAtMs = null
                    noMovementStallActive = false
                    noMovementRecoveryAttempt = 0
                    noMovementMs = 0L
                    nextNoMovementWarnAtMs = NO_MOVEMENT_FIRST_WARN_MS
                }
                lastProgressBytes = progressBytes

                if (percent >= 100) {
                    if (completedSinceMs == null) {
                        completedSinceMs = nowLoopMs
                        DownloadLog.d("Task ${initialEntity.id} reached 100%; waiting for file materialization")
                    }
                    val waited = nowLoopMs - (completedSinceMs ?: nowLoopMs)
                    if (waited >= 8_000L) {
                        runCatching { torrentManager.flushCache(key) }
                        val expectedFile = getExpectedFile(handle, ti, fileIndex)
                        val status = runCatching { handle.status(false) }.getOrNull()
                        val readyForFinalization = expectedFile.exists() ||
                            status?.isFinished() == true ||
                            status?.isSeeding() == true
                        if (readyForFinalization) {
                            DownloadLog.d(
                                "Task ${initialEntity.id} reached 100% and waited ${waited}ms; " +
                                    "starting finalization path"
                            )
                            break
                        }
                        if (waited >= PRE_FINALIZATION_MAX_WAIT_MS) {
                            val rechecked = torrentManager.forceRecheckIfStaleCompletion(
                                key = key,
                                reason = "stale_completion task=${initialEntity.id} waited=${waited}ms",
                                minIntervalMs = 30_000L
                            )
                            if (rechecked) {
                                DownloadLog.w(
                                    "Task ${initialEntity.id} at 100% without materialization; forced recheck and continue waiting"
                                )
                            }
                        }
                        if (waited >= COMPLETION_STALL_TIMEOUT_MS) {
                            throw DownloadException.SessionError(
                                RuntimeException("Completed state without materialization for too long")
                            )
                        }
                    }
                } else {
                    completedSinceMs = null
                }
                lastProgress = percent

                // Re-check latest cached status before persisting RUNNING progress.
                // This avoids overwriting a just-issued PAUSE/CANCEL/FAIL command
                // that could arrive between loop-start read and this stageUpdate call.
                val latest = stateStore.getCachedEntity(initialEntity.id) ?: break
                if (latest.status !in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED)) {
                    DownloadLog.t(
                        scope = "Processor",
                        message = "skipRunningOverwrite taskId=${initialEntity.id} latestStatus=${latest.status} " +
                            "percent=$percent speed=$speed"
                    )
                    delay(120)
                    continue
                }

                val updated = latest.copy(
                    progress = percent
                        .coerceIn(0, 100)
                        .coerceAtLeast(latest.progress.coerceIn(0, 100)),
                    speedBytesPerSec = speed.coerceAtLeast(0),
                    status = DownloadStatus.RUNNING
                )
                stateStore.stageUpdate(updated)
                notificationManager.updateTaskProgress(updated, paused = false, totalBytes)

                if (percent >= 100) {
                    val file = getExpectedFile(handle, ti, fileIndex)
                    if (file.exists() && file.length() >= totalBytes * 0.95) {
                        break
                    }
                }

                delay(getUpdateInterval(speed))
            }

            if (exitedByPause) {
                DownloadLog.t(
                    scope = "Processor",
                    message = "pausedExit taskId=${initialEntity.id} releasingSlot=true"
                )
                return
            }

            if (removedFromState) {
                DownloadLog.t(
                    scope = "Processor",
                    message = "stateRemovedExit taskId=${initialEntity.id} finalize=false"
                )
                return
            }

            if (current.status == DownloadStatus.CANCELED || current.status == DownloadStatus.FAILED) {
                cleanupPartialPayload(key, ti, fileIndex)
                return
            }

            finalizeTorrentDownload(current, handle, ti, fileIndex, totalBytes, key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanupPartialPayload(key, ti, fileIndex)
            throw e

        } finally {
            torrentManager.removeActiveFile(key, fileIndex)
            torrentManager.applyPriorities(key)
            torrentManager.releaseHandle(key)
            notificationManager.cancelTaskNotification(initialEntity.id)
            DownloadLog.d("runTask cleanup completed task=${initialEntity.id}")
        }
    }

    private suspend fun finalizeTorrentDownload(
        entity: DownloadEntity,
        handle: TorrentHandle,
        ti: TorrentInfo,
        fileIndex: Int,
        totalBytes: Long,
        torrentKey: String
    ) {
        try {
            DownloadLog.d("Finalization start task=${entity.id}")
            torrentManager.flushCache(torrentKey)
            delay(1000)

            val src = awaitFileMaterialized(handle, ti, fileIndex, totalBytes)

            val outputUri: Uri = when (SaveOption.valueOf(entity.saveOption)) {
                SaveOption.MUSIC_LIBRARY -> fileManager.saveToMediaStore(src, entity.fileName)
                SaveOption.CUSTOM_FOLDER -> fileManager.saveToCustomFolder(
                    src,
                    entity.fileName,
                    entity.folderUri
                )
            }

            val cleanedImmediately = deleteSourceIfPossibleNow(src, entity.id)
            if (!cleanedImmediately) {
                queueDeferredSourceCleanup(torrentKey, src, handle, ti, fileIndex, entity.id)
            }

            val finalEntity = entity.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                contentUri = outputUri.toString(),
                speedBytesPerSec = 0,
                createdAt = System.currentTimeMillis()
            )
            stateStore.stageUpdate(finalEntity)
            stateStore.flushPendingUpdates()
            queueWholeTorrentCleanupIfNoPending(torrentKey, ti, finalEntity)

            notificationManager.showTaskCompleted(finalEntity, outputUri)
            DownloadLog.d("Finalization success task=${entity.id} uri=$outputUri")

        } catch (e: Exception) {
            DownloadLog.e("Finalization failed for ${entity.id}", e)
            throw when (e) {
                is CancellationException -> e
                is DownloadException -> e
                else -> DownloadException.StorageError(e)
            }
        }
    }

    private fun getExpectedFile(handle: TorrentHandle, ti: TorrentInfo, fileIndex: Int): File {
        val relativePath = ti.files().filePath(fileIndex).replace('\\', '/')
        val savePath = runCatching { handle.savePath() }.getOrElse { downloadRootDir.absolutePath }
        return File(savePath, relativePath)
    }

    private suspend fun cleanupPartialPayload(
        torrentKey: String,
        ti: TorrentInfo,
        fileIndex: Int
    ) {
        val relativePath = runCatching { ti.files().filePath(fileIndex) }.getOrNull()
        if (relativePath.isNullOrBlank()) {
            DownloadLog.w("cleanupPartialPayload skipped: cannot resolve path fileIndex=$fileIndex")
            return
        }
        torrentManager.deferFileCleanup(torrentKey, relativePath)
    }

    private suspend fun queueDeferredSourceCleanup(
        torrentKey: String,
        resolvedSource: File,
        handle: TorrentHandle,
        ti: TorrentInfo,
        fileIndex: Int,
        taskId: String
    ) {
        val expectedRelativePath = runCatching { ti.files().filePath(fileIndex) }
            .getOrNull()
            ?.let(::normalizeTorrentPath)
            .orEmpty()
        val resolvedRelativePath = resolveRelativePathInsideRoot(resolvedSource)

        var queuedAny = false
        if (expectedRelativePath.isNotBlank()) {
            torrentManager.deferFileCleanup(torrentKey, expectedRelativePath)
            queuedAny = true
        }

        if (!resolvedRelativePath.isNullOrBlank() && resolvedRelativePath != expectedRelativePath) {
            torrentManager.deferFileCleanup(torrentKey, resolvedRelativePath)
            queuedAny = true
        }

        if (!queuedAny) {
            val expectedSource = runCatching { getExpectedFile(handle, ti, fileIndex).canonicalPath }.getOrNull()
            val resolvedCanonical = runCatching { resolvedSource.canonicalPath }.getOrNull()
            DownloadLog.w(
                "Skip deferred source cleanup for task=$taskId: " +
                    "expected='$expectedSource' resolved='$resolvedCanonical'"
            )
        }
    }

    private fun deleteSourceIfPossibleNow(source: File, taskId: String): Boolean {
        val canonical = runCatching { source.canonicalFile }.getOrNull()
        if (canonical == null) {
            DownloadLog.w("Immediate source cleanup skipped for task=$taskId: unresolved canonical source")
            return false
        }
        val deleted = runCatching { deleteFileAndPruneParents(downloadRootDir, canonical) }.getOrDefault(false)
        if (!deleted) {
            DownloadLog.w(
                "Immediate source cleanup deferred for task=$taskId: source='${canonical.path}'"
            )
        }
        return deleted
    }

    private fun resolveRelativePathInsideRoot(source: File): String? {
        val root = runCatching { downloadRootDir.canonicalFile }.getOrNull() ?: return null
        val canonical = runCatching { source.canonicalFile }.getOrNull() ?: return null
        val rootPath = root.path
        val sourcePath = canonical.path
        if (sourcePath == rootPath || !sourcePath.startsWith(rootPath + File.separator)) return null
        return normalizeTorrentPath(sourcePath.removePrefix(rootPath + File.separator))
    }

    private suspend fun queueWholeTorrentCleanupIfNoPending(
        torrentKey: String,
        ti: TorrentInfo,
        completed: DownloadEntity
    ) {
        val activeStatuses = setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED, DownloadStatus.PAUSED)
        val hasPendingForTorrent = stateStore.snapshot().any { candidate ->
            candidate.id != completed.id &&
                candidate.torrentFilePath == completed.torrentFilePath &&
                candidate.status in activeStatuses
        }
        if (hasPendingForTorrent) return

        val files = ti.files()
        val numFiles = runCatching { files.numFiles() }.getOrDefault(0)
        for (index in 0 until numFiles) {
            val relativePath = runCatching { files.filePath(index) }.getOrNull()
            if (!relativePath.isNullOrBlank()) {
                torrentManager.deferFileCleanup(torrentKey, relativePath)
            }
        }
    }

    private suspend fun awaitFileMaterialized(
        handle: TorrentHandle,
        ti: TorrentInfo,
        fileIndex: Int,
        expectedBytes: Long
    ): File {
        val file = getExpectedFile(handle, ti, fileIndex)
        val expectedRelativePath = ti.files().filePath(fileIndex)
        val savePath = runCatching { handle.savePath() }.getOrElse { downloadRootDir.absolutePath }
        val saveRoot = File(savePath)
        val timeoutMs = materializationTimeoutMs(
            expectedBytes = expectedBytes,
            baseTimeoutMs = config.fileWaitTimeoutMs
        )
        return awaitMaterializedFileForPath(
            expectedFile = file,
            expectedRelativePath = expectedRelativePath,
            saveRoot = saveRoot,
            expectedBytes = expectedBytes,
            timeoutMs = timeoutMs,
            flushCache = { runCatching { handle.flushCache() } },
            delayMs = { ms -> delay(ms) },
            nowMs = { System.currentTimeMillis() }
        )
    }

    private fun getUpdateInterval(speed: Long): Long = when {
        speed > 10_000_000 -> 500L
        speed > 1_000_000 -> 1000L
        speed > 100_000 -> 1500L
        else -> 2000L
    }

    private fun maybeLogStallResolved(
        active: Boolean,
        taskId: String,
        key: String,
        type: String,
        startedAtMs: Long?,
        nowMs: Long,
        percent: Int,
        progressBytes: Long,
        speed: Long,
        handle: TorrentHandle
    ) {
        if (!active) return
        val elapsed = startedAtMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        DownloadLog.w(
            "STALL_RESOLVED task=$taskId key=${key.take(8)} type=$type elapsed=${elapsed}ms " +
                "percent=$percent bytes=$progressBytes speed=$speed ${buildHandleSnapshot(handle)}"
        )
    }

    private fun buildHandleSnapshot(handle: TorrentHandle): String =
        runCatching {
            val status = handle.status(false)
            "state=${status.state().name} peers=${status.numPeers()} " +
                "seeds=${status.numSeeds()} dRate=${status.downloadRate()}Bps"
        }.getOrDefault("state=err peers=err")

    private fun shouldSoftRequeue(entity: DownloadEntity, error: Exception): Boolean {
        if (!isRecoverableRetryError(error)) return false
        val cached = stateStore.getCachedEntity(entity.id) ?: return false
        if (cached.status !in setOf(DownloadStatus.RUNNING, DownloadStatus.QUEUED)) {
            return false
        }
        val alreadyRequeued = stallRequeueCounts[entity.id] ?: 0
        return alreadyRequeued < maxStallRequeuesPerTask
    }

    private suspend fun recoverHandleForTask(entity: DownloadEntity, error: Throwable) {
        val key = runCatching {
            TorrentInfo(File(entity.torrentFilePath)).infoHash().toString()
        }.getOrNull() ?: return
        torrentManager.recoverHandle(
            key = key,
            reason = "recoverable_requeue task=${entity.id} error=${error::class.simpleName}"
        )
    }
}

internal fun zeroProgressTimeoutMs(): Long = 180_000L

internal fun noMovementTimeoutMs(percent: Int): Long = when {
    percent <= 0 -> 180_000L
    percent < 20 -> 240_000L
    percent < 80 -> 180_000L
    else -> 240_000L
}

internal fun isRecoverableSessionStall(error: Throwable): Boolean {
    val sessionError = error as? DownloadException.SessionError ?: return false
    val message = sessionError.cause?.message?.lowercase().orEmpty()
    return "no progress for too long" in message ||
        "no byte movement for too long" in message ||
        "completed state without materialization for too long" in message
}

internal fun isRecoverableStorageMaterializationTimeout(error: Throwable): Boolean {
    val storageError = error as? DownloadException.StorageError ?: return false
    val message = storageError.cause?.message?.lowercase().orEmpty()
    return "file not materialized after" in message
}

internal fun isRecoverableRetryError(error: Throwable): Boolean =
    isRecoverableSessionStall(error)

internal fun materializationTimeoutMs(
    expectedBytes: Long,
    baseTimeoutMs: Long
): Long {
    val normalizedBase = baseTimeoutMs.coerceAtLeast(180_000L)
    val sizeExtra = (expectedBytes.coerceAtLeast(0L) / (16L * 1024L * 1024L)) * 5_000L
    return (normalizedBase + sizeExtra).coerceAtMost(600_000L)
}

internal fun minMaterializedLength(expectedBytes: Long): Long =
    if (expectedBytes <= 0L) 1L else (expectedBytes * 90L / 100L)

internal suspend fun awaitMaterializedFileForPath(
    expectedFile: File,
    expectedRelativePath: String,
    saveRoot: File,
    expectedBytes: Long,
    timeoutMs: Long,
    flushCache: suspend () -> Unit,
    delayMs: suspend (Long) -> Unit,
    nowMs: () -> Long
): File {
    val strictEnoughBytes = if (expectedBytes <= 0L) 1L else (expectedBytes * 95L / 100L)
    repeat(5) {
        runCatching { flushCache() }
        delayMs(1000)

        if (expectedFile.exists()) {
            val length = expectedFile.length()
            if (length >= strictEnoughBytes) {
                return expectedFile
            }
        }
    }

    val startTime = nowMs()
    while (nowMs() - startTime < timeoutMs) {
        if (expectedFile.exists()) {
            val length = expectedFile.length()
            if (length > 0) {
                delayMs(500)
                val newLength = expectedFile.length()
                if (newLength == length && length >= minMaterializedLength(expectedBytes)) {
                    return expectedFile
                }
            }
        }
        runCatching { flushCache() }
        delayMs(2000)
    }

    findMaterializedFileCandidate(
        saveRoot = saveRoot,
        expectedRelativePath = expectedRelativePath,
        expectedBytes = expectedBytes
    )?.let { candidate ->
        DownloadLog.w(
            "Materialization fallback matched file for task path '$expectedRelativePath' -> '${candidate.absolutePath}'"
        )
        return candidate
    }

    throw DownloadException.StorageError(
        FileNotFoundException(
            "File not materialized after ${timeoutMs}ms: ${expectedFile.absolutePath}"
        )
    )
}

internal fun findMaterializedFileCandidate(
    saveRoot: File,
    expectedRelativePath: String,
    expectedBytes: Long
): File? {
    if (!saveRoot.exists() || !saveRoot.isDirectory) return null
    val targetName = File(expectedRelativePath).name
    if (targetName.isBlank()) return null
    val expectedParentNorm = normalizeLooseTorrentPath(expectedRelativePath)
        .substringBeforeLast('/', missingDelimiterValue = "")

    val minAcceptableBytes = minMaterializedLength(expectedBytes)
    return runCatching {
        saveRoot.walkTopDown()
            .filter { candidate ->
                if (!candidate.isFile) return@filter false
                if (candidate.name != targetName) return@filter false
                if (candidate.length() < minAcceptableBytes) return@filter false

                // Avoid cross-file collisions in multi-disc torrents by requiring
                // some parent path similarity with expected relative path.
                val rel = candidate.toRelativeString(saveRoot).replace('\\', '/')
                val candidateParentNorm = normalizeLooseTorrentPath(rel)
                    .substringBeforeLast('/', missingDelimiterValue = "")
                expectedParentNorm.isBlank() ||
                    parentPathOverlapScore(expectedParentNorm, candidateParentNorm) >= 1
            }
            .maxWithOrNull(
                compareBy<File> {
                    val rel = it.toRelativeString(saveRoot).replace('\\', '/')
                    val candidateParentNorm = normalizeLooseTorrentPath(rel)
                        .substringBeforeLast('/', missingDelimiterValue = "")
                    parentPathOverlapScore(expectedParentNorm, candidateParentNorm)
                }.thenBy { it.length() }
            )
    }.getOrNull()
}

internal fun findTorrentFileIndexByInnerPath(
    filePaths: List<String>,
    innerPath: String
): Int? {
    val expected = normalizeTorrentPath(innerPath)
    val normalizedPaths = filePaths.map(::normalizeTorrentPath)
    val exact = normalizedPaths.indexOfFirst { it == expected }.takeIf { it >= 0 }
    if (exact != null) return exact

    val expectedTail = expected.substringAfterLast('/')
    if (expectedTail.isNotBlank()) {
        val byName = normalizedPaths.withIndex()
            .filter { (_, path) -> path.substringAfterLast('/') == expectedTail }
        if (byName.size == 1) {
            return byName.first().index
        }
        resolveBestByParentSimilarity(
            expectedPath = expected,
            candidates = byName.map { it.index to normalizedPaths[it.index] }
        )?.let { return it }
    }

    val relaxedExpected = normalizeLooseTorrentPath(expected)
    val relaxedPaths = normalizedPaths.map(::normalizeLooseTorrentPath)
    val relaxedExact = relaxedPaths.withIndex()
        .filter { (_, path) -> path == relaxedExpected }
    if (relaxedExact.size == 1) {
        return relaxedExact.first().index
    }

    val relaxedTail = relaxedExpected.substringAfterLast('/')
    if (relaxedTail.isNotBlank()) {
        val relaxedByName = relaxedPaths.withIndex()
            .filter { (_, path) -> path.substringAfterLast('/') == relaxedTail }
        if (relaxedByName.size == 1) {
            return relaxedByName.first().index
        }
        resolveBestByParentSimilarity(
            expectedPath = relaxedExpected,
            candidates = relaxedByName.map { it.index to relaxedPaths[it.index] }
        )?.let { return it }
    }
    return null
}

private fun resolveBestByParentSimilarity(
    expectedPath: String,
    candidates: List<Pair<Int, String>>
): Int? {
    if (candidates.isEmpty()) return null
    val expectedParent = expectedPath.substringBeforeLast('/', missingDelimiterValue = "")
    if (expectedParent.isBlank()) return null

    val scored = candidates.map { (index, path) ->
        val candidateParent = path.substringBeforeLast('/', missingDelimiterValue = "")
        val score = parentPathSimilarityScore(expectedParent, candidateParent)
        index to score
    }.sortedByDescending { it.second }

    if (scored.isEmpty()) return null
    val best = scored[0]
    val second = scored.getOrNull(1)
    val isUniqueBest = second == null || best.second > second.second
    val hasSignal = best.second >= 1
    return if (isUniqueBest && hasSignal) best.first else null
}

private fun parentPathSimilarityScore(expectedParent: String, candidateParent: String): Int {
    if (expectedParent == candidateParent) return 10
    val expectedSegments = expectedParent.split('/').filter { it.isNotBlank() }
    val candidateSegments = candidateParent.split('/').filter { it.isNotBlank() }
    if (expectedSegments.isEmpty() || candidateSegments.isEmpty()) return 0

    var score = 0
    val tailLen = minOf(expectedSegments.size, candidateSegments.size)
    for (i in 1..tailLen) {
        if (expectedSegments[expectedSegments.size - i] == candidateSegments[candidateSegments.size - i]) {
            score += i * 2
        } else {
            break
        }
    }
    return score
}

private fun parentPathOverlapScore(expectedParent: String, candidateParent: String): Int {
    if (expectedParent.isBlank() || candidateParent.isBlank()) return 0
    val expectedSegments = expectedParent.split('/').filter { it.isNotBlank() }
    val candidateSegments = candidateParent.split('/').filter { it.isNotBlank() }
    if (expectedSegments.isEmpty() || candidateSegments.isEmpty()) return 0

    val sharedSegments = expectedSegments.toSet().intersect(candidateSegments.toSet()).size
    val suffixScore = parentPathSimilarityScore(expectedParent, candidateParent)
    return suffixScore * 10 + sharedSegments
}

private const val PRE_FINALIZATION_MAX_WAIT_MS = 60_000L
private const val COMPLETION_STALL_TIMEOUT_MS = 180_000L
private const val ZERO_PROGRESS_FIRST_WARN_MS = 15_000L
private const val NO_MOVEMENT_FIRST_WARN_MS = 20_000L
private const val STALL_WARN_REPEAT_MS = 60_000L
// Treat low but stable payload (few KB/s) as active transport to avoid false STALL_* at startup.
private const val HANDLE_RATE_ACTIVITY_THRESHOLD_BYTES = 2L * 1024L
private const val STALL_ESCALATION_THRESHOLD_MS = 60_000L
private const val STALL_ESCALATION_COOLDOWN_MS = 60_000L
private const val HANDLE_RECOVERY_THRESHOLD_MS = 120_000L

internal fun buildFileNotFoundDiagnostics(
    filePaths: List<String>,
    innerPath: String
): String {
    val expected = normalizeTorrentPath(innerPath)
    val relaxedExpected = normalizeLooseTorrentPath(innerPath)
    val expectedTail = expected.substringAfterLast('/')
    val relaxedTail = relaxedExpected.substringAfterLast('/')
    val normalizedPaths = filePaths.map(::normalizeTorrentPath)
    val relaxedPaths = normalizedPaths.map(::normalizeLooseTorrentPath)

    val exactTailMatches = normalizedPaths.withIndex()
        .filter { (_, path) -> expectedTail.isNotBlank() && path.substringAfterLast('/') == expectedTail }
        .map { it.index }
    val relaxedTailMatches = relaxedPaths.withIndex()
        .filter { (_, path) -> relaxedTail.isNotBlank() && path.substringAfterLast('/') == relaxedTail }
        .map { it.index }

    val sample = filePaths.take(8).joinToString(separator = " | ")
    val exactCandidates = exactTailMatches.take(3).joinToString { idx -> filePaths[idx] }
    val relaxedCandidates = relaxedTailMatches.take(3).joinToString { idx -> filePaths[idx] }

    return buildString {
        append("File resolution failed. expected='")
        append(innerPath)
        append("', normalized='")
        append(expected)
        append("', relaxed='")
        append(relaxedExpected)
        append("', totalFiles=")
        append(filePaths.size)
        append(", exactTailMatches=")
        append(exactTailMatches.size)
        append(", relaxedTailMatches=")
        append(relaxedTailMatches.size)
        if (exactCandidates.isNotBlank()) {
            append(", exactCandidates=[")
            append(exactCandidates)
            append(']')
        }
        if (relaxedCandidates.isNotBlank()) {
            append(", relaxedCandidates=[")
            append(relaxedCandidates)
            append(']')
        }
        append(", sample=[")
        append(sample)
        append(']')
    }
}

internal fun normalizeTorrentPath(path: String): String = path
    .replace('\\', '/')
    .trim()
    .removePrefix("./")
    .trimStart('/')

internal fun normalizeLooseTorrentPath(path: String): String = normalizeTorrentPath(path)
    .split('/')
    .joinToString("/") { segment ->
        segment
            .replace(Regex("""\[[^]]*]"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
            .lowercase()
    }

internal fun deleteFileAndPruneParents(rootDir: File, target: File): Boolean {
    val root = rootDir.canonicalFile
    val file = target.canonicalFile
    if (file.path != root.path && !file.path.startsWith(root.path + File.separator)) return false
    if (!file.exists()) return false
    if (!file.delete()) return false

    var parent = file.parentFile
    while (parent != null && parent.path != root.path && parent.listFiles()?.isEmpty() == true) {
        val next = parent.parentFile
        parent.delete()
        parent = next
    }
    return true
}




