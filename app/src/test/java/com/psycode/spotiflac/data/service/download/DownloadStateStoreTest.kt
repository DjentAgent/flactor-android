package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.data.service.download.core.DownloadStateStore
import com.psycode.spotiflac.domain.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadStateStoreTest {

    @Test
    fun `updateTaskIfAndFlush recovers from db when cache predicate fails`() = runTest {
        val dao = FakeDownloadDao()
        val initial = entity(id = "task-1", status = DownloadStatus.PAUSED)
        dao.upsert(initial)

        val stateStore = DownloadStateStore(dao)
        stateStore.updateTaskCache(
            listOf(
                initial.copy(status = DownloadStatus.RUNNING)
            )
        )

        val updated = stateStore.updateTaskIfAndFlush(
            taskId = initial.id,
            predicate = { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED },
            transform = { it.copy(status = DownloadStatus.QUEUED, speedBytesPerSec = 0L) }
        )

        assertNotNull(updated)
        assertEquals(DownloadStatus.QUEUED, updated?.status)
        assertEquals(DownloadStatus.QUEUED, dao.getById(initial.id)?.status)
        assertEquals(DownloadStatus.QUEUED, stateStore.getCachedEntity(initial.id)?.status)
    }

    @Test
    fun `updateTaskIfAndFlush returns null when predicate fails in cache and db`() = runTest {
        val dao = FakeDownloadDao()
        val initial = entity(id = "task-2", status = DownloadStatus.RUNNING)
        dao.upsert(initial)

        val stateStore = DownloadStateStore(dao)
        stateStore.updateTaskCache(listOf(initial))

        val updated = stateStore.updateTaskIfAndFlush(
            taskId = initial.id,
            predicate = { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED },
            transform = { it.copy(status = DownloadStatus.QUEUED) }
        )

        assertNull(updated)
        assertEquals(DownloadStatus.RUNNING, dao.getById(initial.id)?.status)
        assertEquals(DownloadStatus.RUNNING, stateStore.getCachedEntity(initial.id)?.status)
    }

    private fun entity(id: String, status: DownloadStatus): DownloadEntity = DownloadEntity(
        id = id,
        fileName = "$id.flac",
        size = 100L,
        progress = 0,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "Torrent",
        torrentFilePath = "/tmp/$id.torrent",
        innerPath = "$id.flac",
        saveOption = "MUSIC_LIBRARY",
        folderUri = null,
        speedBytesPerSec = 1L,
        createdAt = 1L
    )
}

private class FakeDownloadDao : DownloadDao {
    private val items = linkedMapOf<String, DownloadEntity>()
    private val flow = MutableStateFlow<List<DownloadEntity>>(emptyList())

    override fun observeAll(): Flow<List<DownloadEntity>> = flow

    override suspend fun getAllSnapshot(): List<DownloadEntity> = items.values.toList()

    override suspend fun upsert(entity: DownloadEntity) {
        items[entity.id] = entity
        emit()
    }

    override suspend fun update(entity: DownloadEntity) {
        upsert(entity)
    }

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
        val toDelete = items.keys.filter { it.startsWith(prefix) }
        toDelete.forEach { items.remove(it) }
        emit()
        return toDelete.size
    }

    private fun emit() {
        flow.value = items.values.toList()
    }
}
