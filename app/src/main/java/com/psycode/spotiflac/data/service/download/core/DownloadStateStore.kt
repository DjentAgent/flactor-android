package com.psycode.spotiflac.data.service.download.core

import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadEntity
import dagger.hilt.android.scopes.ServiceScoped
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@ServiceScoped
class DownloadStateStore @Inject constructor(
    private val dao: DownloadDao
) {
    private val taskCache = ConcurrentHashMap<String, DownloadEntity>()
    private val pendingUpdates = ConcurrentHashMap<String, DownloadEntity>()
    private val updateMutex = Mutex()

    fun updateTaskCache(entities: List<DownloadEntity>) {
        val incomingById = entities.associateBy { it.id }
        val currentIds = incomingById.keys

        taskCache.keys.removeAll { it !in currentIds }
        pendingUpdates.keys.removeAll { it !in currentIds }

        incomingById.forEach { (taskId, incoming) ->
            val pending = pendingUpdates[taskId]
            if (pending != null) {
                taskCache[taskId] = pending
                if (pending.status != incoming.status || pending.progress != incoming.progress) {
                    DownloadLog.t(
                        scope = "StateStore",
                        message = "cacheMerge keepPending taskId=$taskId pending=${pending.status}/${pending.progress} " +
                            "incoming=${incoming.status}/${incoming.progress}"
                    )
                }
            } else {
                taskCache[taskId] = incoming
            }
        }
    }

    fun getCachedEntity(id: String): DownloadEntity? = taskCache[id]

    fun stageUpdate(entity: DownloadEntity) {
        if (!taskCache.containsKey(entity.id)) {
            DownloadLog.d("Ignored stageUpdate for missing taskId=${entity.id}")
            return
        }
        taskCache[entity.id] = entity
        pendingUpdates[entity.id] = entity
    }

    suspend fun updateTaskIfAndFlush(
        taskId: String,
        predicate: (DownloadEntity) -> Boolean,
        transform: (DownloadEntity) -> DownloadEntity
    ): DownloadEntity? = updateMutex.withLock {
        val cached = taskCache[taskId] ?: dao.getById(taskId)?.also { fromDb ->
            taskCache[taskId] = fromDb
            DownloadLog.t(
                scope = "StateStore",
                message = "cacheMissRecovered taskId=$taskId status=${fromDb.status}"
            )
        } ?: run {
            DownloadLog.t(
                scope = "StateStore",
                message = "cacheMissNotFound taskId=$taskId"
            )
            return@withLock null
        }

        val baseEntity = if (predicate(cached)) {
            cached
        } else {
            val fromDb = dao.getById(taskId)
            if (fromDb != null && predicate(fromDb)) {
                taskCache[taskId] = fromDb
                DownloadLog.t(
                    scope = "StateStore",
                    message = "predicateRecoveredFromDb taskId=$taskId cacheStatus=${cached.status} dbStatus=${fromDb.status}"
                )
                fromDb
            } else {
                DownloadLog.t(
                    scope = "StateStore",
                    message = "predicateRejected taskId=$taskId cacheStatus=${cached.status} dbStatus=${fromDb?.status}"
                )
                return@withLock null
            }
        }

        val updated = transform(baseEntity)
        taskCache[taskId] = updated
        pendingUpdates[taskId] = updated
        flushPendingUpdatesLocked()
        updated
    }

    suspend fun flushPendingUpdates() {
        if (pendingUpdates.isEmpty()) return
        updateMutex.withLock { flushPendingUpdatesLocked() }
    }

    private suspend fun flushPendingUpdatesLocked() {
        val updates = pendingUpdates.values.toList()
        pendingUpdates.clear()

        try {
            updates.forEach { dao.upsert(it) }
        } catch (e: Exception) {
            DownloadLog.e("Failed to flush updates", e)
            updates.forEach { pendingUpdates[it.id] = it }
        }
    }

    fun snapshot(): List<DownloadEntity> = taskCache.values.toList()

    fun clear() {
        taskCache.clear()
        pendingUpdates.clear()
    }
}




