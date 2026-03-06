package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.data.service.download.orchestration.applyCancelTransition
import com.psycode.spotiflac.data.service.download.orchestration.applyPauseTransition
import com.psycode.spotiflac.data.service.download.orchestration.applyResumeTransition
import com.psycode.spotiflac.data.service.download.orchestration.canPause
import com.psycode.spotiflac.data.service.download.orchestration.canResume
import com.psycode.spotiflac.data.service.download.orchestration.selectTasksToSchedule
import com.psycode.spotiflac.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DownloadStateMachineFuzzTest {

    @Test
    fun `fuzz transitions keep peer isolation and scheduler invariants`() = runTest {
        val dao = FuzzDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val ids = (1..12).map { idx -> "topic_99_task_$idx" }
        val initial = ids.mapIndexed { idx, id ->
            entity(
                id = id,
                status = when (idx % 4) {
                    0 -> DownloadStatus.RUNNING
                    1 -> DownloadStatus.QUEUED
                    2 -> DownloadStatus.PAUSED
                    else -> DownloadStatus.FAILED
                },
                createdAt = idx.toLong()
            )
        }
        initial.forEach { dao.upsert(it) }
        stateStore.updateTaskCache(dao.getAllSnapshot())

        val random = Random(42)
        repeat(300) { step ->
            val targetId = ids[random.nextInt(ids.size)]
            val action = random.nextInt(3)

            // Occasionally inject cache drift to emulate observer lag/race.
            if (step % 11 == 0) {
                val drifted = dao.getAllSnapshot().map { entity ->
                    if (entity.id == targetId && entity.status == DownloadStatus.PAUSED) {
                        entity.copy(status = DownloadStatus.RUNNING)
                    } else {
                        entity
                    }
                }
                stateStore.updateTaskCache(drifted)
            } else {
                stateStore.updateTaskCache(dao.getAllSnapshot())
            }

            val before = dao.getAllSnapshot().associateBy { it.id }
            when (action) {
                0 -> {
                    stateStore.updateTaskIfAndFlush(
                        taskId = targetId,
                        predicate = { canPause(it.status) },
                        transform = { applyPauseTransition(it) ?: it }
                    )
                }
                1 -> {
                    stateStore.updateTaskIfAndFlush(
                        taskId = targetId,
                        predicate = { canResume(it.status) },
                        transform = { applyResumeTransition(it, nowMs = 10_000L + step) ?: it }
                    )
                }
                else -> {
                    stateStore.updateTaskIfAndFlush(
                        taskId = targetId,
                        predicate = { true },
                        transform = ::applyCancelTransition
                    )
                }
            }

            val after = dao.getAllSnapshot().associateBy { it.id }

            // Operation must not mutate non-target tasks.
            ids.filter { it != targetId }.forEach { id ->
                assertEquals(before.getValue(id), after.getValue(id))
            }

            // Target must stay within valid status enum and have non-negative speed.
            val target = after.getValue(targetId)
            assertTrue(target.status in DownloadStatus.values().toSet())
            assertTrue(target.speedBytesPerSec >= 0L)

            // Scheduler invariant: only RUNNING/QUEUED unscheduled tasks can be selected.
            val scheduled = ids.shuffled(random).take(4).toSet()
            val selected = selectTasksToSchedule(
                snapshot = after.values.toList(),
                scheduledTaskIds = scheduled,
                maxParallelDownloads = 5,
                reservedPausedTaskIds = emptySet()
            )
            assertTrue(selected.all { it.id !in scheduled })
            assertTrue(selected.all { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.QUEUED })
        }
    }

    private fun entity(
        id: String,
        status: DownloadStatus,
        createdAt: Long
    ): DownloadEntity = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 0,
        status = status,
        errorMessage = if (status == DownloadStatus.FAILED) "err" else null,
        contentUri = null,
        torrentTitle = "torrent",
        torrentFilePath = "/tmp/$id.torrent",
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = 10L,
        createdAt = createdAt
    )
}

private class FuzzDownloadDao : DownloadDao {
    private val items = linkedMapOf<String, DownloadEntity>()
    private val flow = MutableStateFlow<List<DownloadEntity>>(emptyList())

    override fun observeAll(): Flow<List<DownloadEntity>> = flow

    override suspend fun getAllSnapshot(): List<DownloadEntity> = items.values.toList()

    override suspend fun upsert(entity: DownloadEntity) {
        items[entity.id] = entity
        emit()
    }

    override suspend fun update(entity: DownloadEntity) = upsert(entity)

    override suspend fun delete(entity: DownloadEntity) {
        items.remove(entity.id)
        emit()
    }

    override suspend fun getById(id: String): DownloadEntity? = items[id]

    override suspend fun deleteById(id: String) {
        items.remove(id)
        emit()
    }

    override suspend fun clearAll() {
        items.clear()
        emit()
    }

    override suspend fun getByTopicPrefix(prefix: String): List<DownloadEntity> =
        items.values.filter { it.id.startsWith(prefix) }

    override suspend fun deleteByTopicPrefix(prefix: String): Int {
        val ids = items.keys.filter { it.startsWith(prefix) }
        ids.forEach { items.remove(it) }
        emit()
        return ids.size
    }

    private fun emit() {
        flow.value = items.values.toList()
    }
}

