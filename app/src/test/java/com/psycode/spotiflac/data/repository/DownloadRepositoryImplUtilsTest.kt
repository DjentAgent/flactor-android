package com.psycode.spotiflac.data.repository

import com.psycode.spotiflac.data.local.DownloadEntity
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.buildDownloadTaskId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class DownloadRepositoryImplUtilsTest {

    @Test
    fun `buildDownloadTaskId is deterministic and keeps topic prefix`() {
        val id = buildDownloadTaskId(topicId = 42, innerPath = "Album/Track.flac")
        assertTrue(id.startsWith("42_"))
        assertEquals(id, buildDownloadTaskId(topicId = 42, innerPath = "Album/Track.flac"))
    }

    @Test
    fun `buildDownloadTaskId avoids old hashCode collision case`() {
        
        val first = buildDownloadTaskId(topicId = 1, innerPath = "FB")
        val second = buildDownloadTaskId(topicId = 1, innerPath = "Ea")
        assertNotEquals(first, second)
    }

    @Test
    fun `resolveFileInsideRoot blocks path traversal`() {
        val root = createTempDirectory(prefix = "spotiflac-root").toFile()
        val resolved = resolveFileInsideRoot(root, "../outside.txt")
        assertNull(resolved)
    }

    @Test
    fun `resolveFileInsideRoot resolves normalized separators`() {
        val root = createTempDirectory(prefix = "spotiflac-root").toFile()
        val resolved = resolveFileInsideRoot(root, "Album\\Track.flac")
        val expected = File(root, "Album/Track.flac").canonicalFile
        assertEquals(expected, resolved)
    }

    @Test
    fun `resolveEnqueueEntity keeps active existing task state`() {
        val existing = existingEntity(status = DownloadStatus.RUNNING, progress = 42, speed = 12_000L)
        val result = resolveEnqueueEntity(
            id = existing.id,
            file = torrentFile(),
            existing = existing,
            existingMediaStoreUri = null,
            torrentTitle = "Title",
            saveOption = SaveOption.MUSIC_LIBRARY,
            folderUri = null,
            now = 999L
        )
        assertEquals(DownloadStatus.RUNNING, result.entity.status)
        assertEquals(42, result.entity.progress)
        assertEquals(12_000L, result.entity.speedBytesPerSec)
        assertEquals(existing.createdAt, result.entity.createdAt)
        assertTrue(result.shouldQueue)
    }

    @Test
    fun `resolveEnqueueEntity marks completed when media file exists`() {
        val result = resolveEnqueueEntity(
            id = "1_test",
            file = torrentFile(),
            existing = null,
            existingMediaStoreUri = "content://media/audio/1",
            torrentTitle = "Title",
            saveOption = SaveOption.MUSIC_LIBRARY,
            folderUri = null,
            now = 999L
        )
        assertEquals(DownloadStatus.COMPLETED, result.entity.status)
        assertEquals(100, result.entity.progress)
        assertEquals("content://media/audio/1", result.entity.contentUri)
        assertEquals(false, result.shouldQueue)
    }

    @Test
    fun `resolveEnqueueEntity requeues failed existing task`() {
        val existing = existingEntity(status = DownloadStatus.FAILED, progress = 17, speed = 500L)
        val result = resolveEnqueueEntity(
            id = existing.id,
            file = torrentFile(),
            existing = existing,
            existingMediaStoreUri = null,
            torrentTitle = "Title",
            saveOption = SaveOption.MUSIC_LIBRARY,
            folderUri = null,
            now = 999L
        )
        assertEquals(DownloadStatus.QUEUED, result.entity.status)
        assertEquals(0, result.entity.progress)
        assertEquals(null, result.entity.errorMessage)
        assertTrue(result.shouldQueue)
    }

    @Test
    fun `buildGroupAnchorEntity uses synthetic id and does not keep file binding`() {
        val source = existingEntity(status = DownloadStatus.COMPLETED, progress = 100, speed = 0L)

        val anchor = buildGroupAnchorEntity(
            source = source,
            topicId = 77,
            now = 555L
        )

        assertEquals("77_group_anchor", anchor.id)
        assertEquals("__group_anchor__", anchor.fileName)
        assertEquals("__group_anchor__", anchor.innerPath)
        assertEquals(0L, anchor.size)
        assertEquals(0, anchor.progress)
        assertEquals(DownloadStatus.CANCELED, anchor.status)
        assertEquals(null, anchor.contentUri)
    }

    private fun torrentFile() = TorrentFile(
        name = "Track.flac",
        size = 123L,
        torrentFilePath = "/tmp/a.torrent",
        innerPath = "Album/Track.flac"
    )

    private fun existingEntity(
        status: DownloadStatus,
        progress: Int,
        speed: Long
    ) = DownloadEntity(
        id = "1_existing",
        fileName = "Track.flac",
        size = 123L,
        progress = progress,
        status = status,
        errorMessage = "err",
        contentUri = null,
        torrentTitle = "Old",
        torrentFilePath = "/tmp/a.torrent",
        innerPath = "Album/Track.flac",
        saveOption = SaveOption.MUSIC_LIBRARY.name,
        folderUri = null,
        speedBytesPerSec = speed,
        createdAt = 100L
    )
}

