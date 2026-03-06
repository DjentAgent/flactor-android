package com.psycode.spotiflac.ui.component.managefiles

import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageFilesFiltersTest {

    @Test
    fun `buildManageFilesFilterStats counts categories`() {
        val prep = PreparedFilesDl(
            ordered = listOf(
                ui("a.flac", DownloadStatus.QUEUED),
                ui("b.flac", DownloadStatus.RUNNING),
                ui("c.flac", DownloadStatus.PAUSED),
                ui("d.flac", DownloadStatus.COMPLETED),
                ui("e.flac", DownloadStatus.FAILED),
                ui("f.flac", DownloadStatus.CANCELED),
                ui("g.flac", null)
            ),
            indexByPath = emptyMap(),
            selectableIndices = listOf(4, 5, 6),
            removableCompletedIndices = listOf(3),
            initialChecks = List(7) { false },
            duplicateKeys = emptySet(),
            matchedIndices = emptySet()
        )

        val stats = buildManageFilesFilterStats(prep)
        assertEquals(7, stats.all)
        assertEquals(3, stats.downloadable)
        assertEquals(3, stats.downloading)
        assertEquals(1, stats.downloaded)
        assertEquals(2, stats.failed)
    }

    @Test
    fun `manageFilesFilterMatches applies status and query rules`() {
        val completed = ui("Artist/Track.flac", DownloadStatus.COMPLETED)
        val failed = ui("Artist/Broken.flac", DownloadStatus.FAILED)
        val queued = ui("Artist/Queue.flac", DownloadStatus.QUEUED)

        assertTrue(manageFilesFilterMatches(completed, ManageFilesFilter.DOWNLOADED, ""))
        assertFalse(manageFilesFilterMatches(completed, ManageFilesFilter.DOWNLOADING, ""))

        assertTrue(manageFilesFilterMatches(failed, ManageFilesFilter.FAILED, "broken"))
        assertFalse(manageFilesFilterMatches(failed, ManageFilesFilter.FAILED, "track"))

        assertTrue(manageFilesFilterMatches(queued, ManageFilesFilter.DOWNLOADING, "queue"))
        assertFalse(manageFilesFilterMatches(queued, ManageFilesFilter.DOWNLOADABLE, ""))
    }

    @Test
    fun `recommendedManageFilesFilter prioritizes failed then downloading then downloadable`() {
        assertEquals(
            ManageFilesFilter.FAILED,
            recommendedManageFilesFilter(
                ManageFilesFilterStats(
                    all = 10,
                    downloadable = 4,
                    downloading = 2,
                    downloaded = 2,
                    failed = 1
                )
            )
        )
        assertEquals(
            ManageFilesFilter.DOWNLOADING,
            recommendedManageFilesFilter(
                ManageFilesFilterStats(
                    all = 10,
                    downloadable = 4,
                    downloading = 2,
                    downloaded = 4,
                    failed = 0
                )
            )
        )
        assertEquals(
            ManageFilesFilter.DOWNLOADABLE,
            recommendedManageFilesFilter(
                ManageFilesFilterStats(
                    all = 10,
                    downloadable = 4,
                    downloading = 0,
                    downloaded = 6,
                    failed = 0
                )
            )
        )
    }

    private fun ui(path: String, status: DownloadStatus?): UiFileDl {
        val file = TorrentFile(
            name = path.substringAfterLast('/'),
            size = 10L,
            torrentFilePath = "/tmp/a.torrent",
            innerPath = path
        )
        return UiFileDl(
            file = file,
            taskId = "id-$path",
            normalizedName = normalizeCore(fileBaseName(file.name)),
            normalizedPath = normalizeCore(path),
            dupKey = duplicateKeyOf(fileBaseName(file.name)),
            size = file.size,
            status = status,
            progress = null,
            contentUri = null,
            completedAt = null,
            disabled = false,
            matchScore = 0
        )
    }
}
