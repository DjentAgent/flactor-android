package com.psycode.spotiflac.data.service.download

import com.psycode.spotiflac.data.service.download.core.DownloadException
import com.psycode.spotiflac.data.service.download.orchestration.awaitMaterializedFileForPath
import com.psycode.spotiflac.data.service.download.orchestration.buildFileNotFoundDiagnostics
import com.psycode.spotiflac.data.service.download.orchestration.deleteFileAndPruneParents
import com.psycode.spotiflac.data.service.download.orchestration.findMaterializedFileCandidate
import com.psycode.spotiflac.data.service.download.orchestration.findTorrentFileIndexByInnerPath
import com.psycode.spotiflac.data.service.download.orchestration.isRecoverableRetryError
import com.psycode.spotiflac.data.service.download.orchestration.isRecoverableSessionStall
import com.psycode.spotiflac.data.service.download.orchestration.isRecoverableStorageMaterializationTimeout
import com.psycode.spotiflac.data.service.download.orchestration.materializationTimeoutMs
import com.psycode.spotiflac.data.service.download.orchestration.noMovementTimeoutMs
import com.psycode.spotiflac.data.service.download.orchestration.zeroProgressTimeoutMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.RuntimeException
import java.io.FileNotFoundException
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlinx.coroutines.test.runTest

class DownloadTaskProcessorUtilsTest {

    @Test
    fun `findTorrentFileIndexByInnerPath matches exact normalized path`() {
        val paths = listOf(
            "Album/CD1/track01.flac",
            "Album/CD2/track01.flac"
        )
        val index = findTorrentFileIndexByInnerPath(paths, "Album\\CD2\\track01.flac")
        assertEquals(1, index)
    }

    @Test
    fun `findTorrentFileIndexByInnerPath does not match by suffix`() {
        val paths = listOf(
            "Artist A/track.flac",
            "Artist B/track.flac"
        )
        val index = findTorrentFileIndexByInnerPath(paths, "track.flac")
        assertEquals(null, index)
    }

    @Test
    fun `findTorrentFileIndexByInnerPath matches unique filename fallback`() {
        val paths = listOf(
            "Artist A/track01.flac",
            "Artist B/track02.flac"
        )
        val index = findTorrentFileIndexByInnerPath(paths, "track02.flac")
        assertEquals(1, index)
    }

    @Test
    fun `findTorrentFileIndexByInnerPath matches by relaxed path normalization`() {
        val paths = listOf(
            "1985 - Hell Awaits/01.Hell Awaits.flac",
            "1985 - Hell Awaits/02.Kill Again.flac"
        )
        val index = findTorrentFileIndexByInnerPath(
            paths,
            "1985 - Hell Awaits [restored]/01. Hell Awaits.flac"
        )
        assertEquals(0, index)
    }

    @Test
    fun `findTorrentFileIndexByInnerPath keeps relaxed fallback unambiguous`() {
        val paths = listOf(
            "Disc A/01.Hell Awaits.flac",
            "Disc B/01 Hell Awaits.flac"
        )
        val index = findTorrentFileIndexByInnerPath(
            paths,
            "01. Hell Awaits.flac"
        )
        assertEquals(null, index)
    }

    @Test
    fun `findTorrentFileIndexByInnerPath resolves duplicate file name by parent similarity`() {
        val paths = listOf(
            "TesseracT - 2018 - Sonder/04 - Juno.flac",
            "TesseracT - 2018 - Sonder (3D Binaural Mix)/04 - Juno.flac",
            "TesseracT - 2018 - Sonder (Instrumental)/04 - Juno.flac"
        )
        val index = findTorrentFileIndexByInnerPath(
            paths,
            "TesseracT - 2018 - Sonder (3D Binaural Mix)/04 - Juno.flac"
        )
        assertEquals(1, index)
    }

    @Test
    fun `deleteFileAndPruneParents deletes file and empty parents inside root`() {
        val root = createTempDirectory(prefix = "spotiflac-dl").toFile()
        val nested = File(root, "a/b/c/file.flac").apply {
            parentFile?.mkdirs()
            writeText("data")
        }

        val deleted = deleteFileAndPruneParents(root, nested)

        assertTrue(deleted)
        assertFalse(nested.exists())
        assertFalse(File(root, "a/b/c").exists())
    }

    @Test
    fun `deleteFileAndPruneParents refuses deletion outside root`() {
        val root = createTempDirectory(prefix = "spotiflac-dl").toFile()
        val outside = createTempFile(prefix = "spotiflac-outside", suffix = ".tmp").toFile()

        val deleted = deleteFileAndPruneParents(root, outside)

        assertFalse(deleted)
        assertTrue(outside.exists())
    }

    @Test
    fun `stall timeout policy is adaptive by percent`() {
        assertEquals(180_000L, zeroProgressTimeoutMs())
        assertEquals(240_000L, noMovementTimeoutMs(10))
        assertEquals(180_000L, noMovementTimeoutMs(50))
        assertEquals(240_000L, noMovementTimeoutMs(95))
    }

    @Test
    fun `recoverable session stall detection matches expected errors`() {
        val stall0 = DownloadException.SessionError(RuntimeException("No progress for too long at 0%"))
        val stallMove = DownloadException.SessionError(RuntimeException("No byte movement for too long"))
        val other = DownloadException.SessionError(RuntimeException("Handle disappeared"))

        assertTrue(isRecoverableSessionStall(stall0))
        assertTrue(isRecoverableSessionStall(stallMove))
        assertFalse(isRecoverableSessionStall(other))
    }

    @Test
    fun `recoverable storage materialization detection matches expected errors`() {
        val materializedTimeout = DownloadException.StorageError(
            FileNotFoundException("File not materialized after 30000ms: /tmp/file.flac")
        )
        val other = DownloadException.StorageError(RuntimeException("Permission denied"))

        assertTrue(isRecoverableStorageMaterializationTimeout(materializedTimeout))
        assertFalse(isRecoverableStorageMaterializationTimeout(other))
    }

    @Test
    fun `recoverable retry error covers session stalls only`() {
        val session = DownloadException.SessionError(RuntimeException("No byte movement for too long"))
        val materializedTimeout = DownloadException.StorageError(
            FileNotFoundException("File not materialized after 30000ms: /tmp/file.flac")
        )
        val other = DownloadException.ValidationError("bad input")

        assertTrue(isRecoverableRetryError(session))
        assertFalse(isRecoverableRetryError(materializedTimeout))
        assertFalse(isRecoverableRetryError(other))
    }

    @Test
    fun `materialization timeout scales with file size and keeps sane bounds`() {
        val small =
            materializationTimeoutMs(expectedBytes = 5L * 1024 * 1024, baseTimeoutMs = 30_000L)
        val medium =
            materializationTimeoutMs(expectedBytes = 64L * 1024 * 1024, baseTimeoutMs = 30_000L)
        val huge = materializationTimeoutMs(
            expectedBytes = 8L * 1024 * 1024 * 1024,
            baseTimeoutMs = 30_000L
        )

        assertEquals(180_000L, small)
        assertTrue(medium > small)
        assertEquals(600_000L, huge)
    }

    @Test
    fun `findMaterializedFileCandidate finds matching fallback file by name and size`() {
        val root = createTempDirectory(prefix = "spotiflac-materialized").toFile()
        val target = File(root, "Album/Disc 1").apply { mkdirs() }
        File(target, "01 - Intro.flac").writeBytes(ByteArray(120))
        File(target, "02 - Track.flac").writeBytes(ByteArray(240))

        val resolved = findMaterializedFileCandidate(
            saveRoot = root,
            expectedRelativePath = "Album/Disc One/02 - Track.flac",
            expectedBytes = 200
        )

        assertEquals("02 - Track.flac", resolved?.name)
    }

    @Test
    fun `findMaterializedFileCandidate avoids unrelated parent collision`() {
        val root = createTempDirectory(prefix = "spotiflac-materialized").toFile()
        val expectedParent = File(root, "Artist/Live/Disc 1").apply { mkdirs() }
        val unrelatedParent = File(root, "Compilations/Mixed").apply { mkdirs() }
        File(expectedParent, "07 - Interlude.flac").writeBytes(ByteArray(220))
        File(unrelatedParent, "07 - Interlude.flac").writeBytes(ByteArray(280))

        val resolved = findMaterializedFileCandidate(
            saveRoot = root,
            expectedRelativePath = "Artist/Live Session/Disc 01/07 - Interlude.flac",
            expectedBytes = 200
        )

        assertEquals(File(expectedParent, "07 - Interlude.flac").canonicalPath, resolved?.canonicalPath)
    }

    @Test
    fun `buildFileNotFoundDiagnostics includes counts and candidates`() {
        val paths = listOf(
            "Album A/01 - Intro.flac",
            "Album B/04 - Juno.flac",
            "Album C/04 - Juno.flac"
        )

        val msg = buildFileNotFoundDiagnostics(paths, "Album Z/04 - Juno.flac")

        assertTrue(msg.contains("File resolution failed"))
        assertTrue(msg.contains("totalFiles=3"))
        assertTrue(msg.contains("exactTailMatches=2"))
        assertTrue(msg.contains("exactCandidates=[Album B/04 - Juno.flac"))
    }

    @Test
    fun `awaitMaterializedFileForPath returns expected file when it materializes with delay`() = runTest {
        val root = createTempDirectory(prefix = "spotiflac-await").toFile()
        val expected = File(root, "Album/Disc 1/02 - Track.flac")
        expected.parentFile?.mkdirs()
        var flushCalls = 0
        var now = 0L

        val resolved = awaitMaterializedFileForPath(
            expectedFile = expected,
            expectedRelativePath = "Album/Disc 1/02 - Track.flac",
            saveRoot = root,
            expectedBytes = 100,
            timeoutMs = 8_000L,
            flushCache = {
                flushCalls++
                if (flushCalls == 3) {
                    expected.writeBytes(ByteArray(100))
                }
            },
            delayMs = { delta -> now += delta },
            nowMs = { now }
        )

        assertEquals(expected.canonicalPath, resolved.canonicalPath)
    }

    @Test
    fun `awaitMaterializedFileForPath returns fallback candidate on timeout`() = runTest {
        val root = createTempDirectory(prefix = "spotiflac-await-fallback").toFile()
        val expected = File(root, "Album/Disc 2/07 - Interlude.flac")
        expected.parentFile?.mkdirs()
        val candidate = File(root, "Album/Disc 1/07 - Interlude.flac").apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(220))
        }
        var now = 0L

        val resolved = awaitMaterializedFileForPath(
            expectedFile = expected,
            expectedRelativePath = "Album/Disc 2/07 - Interlude.flac",
            saveRoot = root,
            expectedBytes = 200,
            timeoutMs = 4_000L,
            flushCache = {},
            delayMs = { delta -> now += delta },
            nowMs = { now }
        )

        assertEquals(candidate.canonicalPath, resolved.canonicalPath)
    }

    @Test(expected = DownloadException.StorageError::class)
    fun `awaitMaterializedFileForPath throws storage error when file never materializes`() = runTest {
        val root = createTempDirectory(prefix = "spotiflac-await-miss").toFile()
        val expected = File(root, "Album/Disc 1/09 - Missing.flac")
        expected.parentFile?.mkdirs()
        var now = 0L

        awaitMaterializedFileForPath(
            expectedFile = expected,
            expectedRelativePath = "Album/Disc 1/09 - Missing.flac",
            saveRoot = root,
            expectedBytes = 150,
            timeoutMs = 3_000L,
            flushCache = {},
            delayMs = { delta -> now += delta },
            nowMs = { now }
        )
    }
}

