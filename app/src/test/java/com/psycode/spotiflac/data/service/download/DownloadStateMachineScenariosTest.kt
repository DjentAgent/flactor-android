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

class DownloadStateMachineScenariosTest {

    @Test
    fun `pause and resume one running task does not mutate peer tasks and scheduler prioritizes resumed`() = runTest {
        val dao = ScenarioDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val runningA = entity("topic_1_a", DownloadStatus.RUNNING, createdAt = 100L)
        val runningB = entity("topic_1_b", DownloadStatus.RUNNING, createdAt = 90L)
        val queuedC = entity("topic_1_c", DownloadStatus.QUEUED, createdAt = 80L)
        listOf(runningA, runningB, queuedC).forEach { dao.upsert(it) }
        stateStore.updateTaskCache(dao.getAllSnapshot())

        val paused = stateStore.updateTaskIfAndFlush(
            taskId = runningA.id,
            predicate = { canPause(it.status) },
            transform = { applyPauseTransition(it) ?: it }
        )
        assertEquals(DownloadStatus.PAUSED, paused?.status)
        assertEquals(DownloadStatus.RUNNING, dao.getById(runningB.id)?.status)
        assertEquals(DownloadStatus.QUEUED, dao.getById(queuedC.id)?.status)

        val resumed = stateStore.updateTaskIfAndFlush(
            taskId = runningA.id,
            predicate = { canResume(it.status) },
            transform = { applyResumeTransition(it, nowMs = 1_000L) ?: it }
        )
        assertEquals(DownloadStatus.QUEUED, resumed?.status)
        assertEquals(1_000L, resumed?.createdAt)
        assertEquals(DownloadStatus.RUNNING, dao.getById(runningB.id)?.status)

        val selected = selectTasksToSchedule(
            snapshot = dao.getAllSnapshot(),
            scheduledTaskIds = setOf(runningB.id),
            maxParallelDownloads = 2,
            reservedPausedTaskIds = emptySet()
        )
        assertEquals(listOf(runningA.id), selected.map { it.id })
    }

    @Test
    fun `failed to queued retry does not displace already scheduled running tasks`() = runTest {
        val dao = ScenarioDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val run1 = entity("topic_2_run1", DownloadStatus.RUNNING, createdAt = 100L)
        val run2 = entity("topic_2_run2", DownloadStatus.RUNNING, createdAt = 90L)
        val failed = entity("topic_2_failed", DownloadStatus.FAILED, createdAt = 10L, error = "timeout")
        listOf(run1, run2, failed).forEach { dao.upsert(it) }
        stateStore.updateTaskCache(dao.getAllSnapshot())

        val retried = stateStore.updateTaskIfAndFlush(
            taskId = failed.id,
            predicate = { canResume(it.status) },
            transform = { applyResumeTransition(it, nowMs = 5_000L) ?: it }
        )
        assertEquals(DownloadStatus.QUEUED, retried?.status)
        assertEquals(null, retried?.errorMessage)

        val selectedNoSlot = selectTasksToSchedule(
            snapshot = dao.getAllSnapshot(),
            scheduledTaskIds = setOf(run1.id, run2.id),
            maxParallelDownloads = 2,
            reservedPausedTaskIds = emptySet()
        )
        assertTrue(selectedNoSlot.isEmpty())

        val selectedWithSlot = selectTasksToSchedule(
            snapshot = dao.getAllSnapshot(),
            scheduledTaskIds = setOf(run1.id, run2.id),
            maxParallelDownloads = 3,
            reservedPausedTaskIds = emptySet()
        )
        assertEquals(listOf(failed.id), selectedWithSlot.map { it.id })
    }

    @Test
    fun `cancel one queued task keeps other queued task schedulable`() = runTest {
        val dao = ScenarioDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val queued1 = entity("topic_3_q1", DownloadStatus.QUEUED, createdAt = 10L)
        val queued2 = entity("topic_3_q2", DownloadStatus.QUEUED, createdAt = 20L)
        listOf(queued1, queued2).forEach { dao.upsert(it) }
        stateStore.updateTaskCache(dao.getAllSnapshot())

        stateStore.updateTaskIfAndFlush(
            taskId = queued2.id,
            predicate = { true },
            transform = ::applyCancelTransition
        )

        val selected = selectTasksToSchedule(
            snapshot = dao.getAllSnapshot(),
            scheduledTaskIds = emptySet(),
            maxParallelDownloads = 1,
            reservedPausedTaskIds = emptySet()
        )
        assertEquals(listOf(queued1.id), selected.map { it.id })
    }

    @Test
    fun `pause resume pause loop is deterministic`() = runTest {
        val dao = ScenarioDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val target = entity("topic_4_target", DownloadStatus.RUNNING, createdAt = 1L)
        dao.upsert(target)
        stateStore.updateTaskCache(dao.getAllSnapshot())

        stateStore.updateTaskIfAndFlush(
            taskId = target.id,
            predicate = { canPause(it.status) },
            transform = { applyPauseTransition(it) ?: it }
        )
        stateStore.updateTaskIfAndFlush(
            taskId = target.id,
            predicate = { canResume(it.status) },
            transform = { applyResumeTransition(it, nowMs = 2L) ?: it }
        )
        val final = stateStore.updateTaskIfAndFlush(
            taskId = target.id,
            predicate = { canPause(it.status) },
            transform = { applyPauseTransition(it) ?: it }
        )

        assertEquals(DownloadStatus.PAUSED, final?.status)
    }

    @Test
    fun `pause resume one task in active trio keeps other active tasks runnable`() = runTest {
        val dao = ScenarioDownloadDao()
        val stateStore = DownloadStateStore(dao)
        val runA = entity("topic_5_a", DownloadStatus.RUNNING, createdAt = 100L)
        val runB = entity("topic_5_b", DownloadStatus.RUNNING, createdAt = 90L)
        val runC = entity("topic_5_c", DownloadStatus.RUNNING, createdAt = 80L)
        listOf(runA, runB, runC).forEach { dao.upsert(it) }
        stateStore.updateTaskCache(dao.getAllSnapshot())

        stateStore.updateTaskIfAndFlush(
            taskId = runA.id,
            predicate = { canPause(it.status) },
            transform = { applyPauseTransition(it) ?: it }
        )
        stateStore.updateTaskIfAndFlush(
            taskId = runA.id,
            predicate = { canResume(it.status) },
            transform = { applyResumeTransition(it, nowMs = 10_000L) ?: it }
        )

        val snapshot = dao.getAllSnapshot()
        val selected = selectTasksToSchedule(
            snapshot = snapshot,
            scheduledTaskIds = setOf(runB.id),
            maxParallelDownloads = 2,
            reservedPausedTaskIds = emptySet()
        )

        val selectedIds = selected.map { it.id }.toSet()
        assertTrue(runA.id in selectedIds || runC.id in selectedIds)
        assertTrue(runB.id !in selectedIds)
        assertEquals(DownloadStatus.RUNNING, dao.getById(runC.id)?.status)
    }

    private fun entity(
        id: String,
        status: DownloadStatus,
        createdAt: Long,
        error: String? = null
    ): DownloadEntity = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 0,
        status = status,
        errorMessage = error,
        contentUri = null,
        torrentTitle = "torrent",
        torrentFilePath = "/tmp/$id.torrent",
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = 111L,
        createdAt = createdAt
    )
}

private class ScenarioDownloadDao : DownloadDao {
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
