package com.psycode.spotiflac.ui.screen.trackdetail

import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.ui.screen.downloads.ManageFilesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDetailUiMappingsTest {

    @Test
    fun `mapTorrentFilesUiStateToManageFilesUiState keeps variant semantics`() {
        assertTrue(mapTorrentFilesUiStateToManageFilesUiState(TorrentFilesUiState.Hidden) is ManageFilesUiState.Hidden)
        assertTrue(mapTorrentFilesUiStateToManageFilesUiState(TorrentFilesUiState.Loading) is ManageFilesUiState.Loading)

        val error = mapTorrentFilesUiStateToManageFilesUiState(TorrentFilesUiState.Error("err"))
        require(error is ManageFilesUiState.Error)
        assertEquals("err", error.message)

        val files = listOf(
            TorrentFile("a.flac", 1L, "/tmp/a.torrent", "a.flac")
        )
        val success = mapTorrentFilesUiStateToManageFilesUiState(TorrentFilesUiState.Success(files))
        require(success is ManageFilesUiState.Success)
        assertEquals(files, success.files)
    }

    @Test
    fun `resolveAutoMatchInputs prefers manual query when present`() {
        val sticky = track(artist = "Sticky Artist", title = "Sticky Title")
        val resolved = resolveAutoMatchInputs(
            stickyTrack = sticky,
            manualMode = false,
            manualQuery = "Manual Artist" to "Manual Title",
            manualArtistInput = "Input Artist",
            manualTitleInput = "Input Title"
        )
        assertEquals("Manual Artist" to "Manual Title", resolved)
    }

    @Test
    fun `resolveAutoMatchInputs falls back to sticky when not manual mode`() {
        val sticky = track(artist = "Sticky Artist", title = "Sticky Title")
        val resolved = resolveAutoMatchInputs(
            stickyTrack = sticky,
            manualMode = false,
            manualQuery = null,
            manualArtistInput = "Input Artist",
            manualTitleInput = "Input Title"
        )
        assertEquals("Sticky Artist" to "Sticky Title", resolved)
    }

    @Test
    fun `resolveAutoMatchInputs uses manual inputs in manual mode`() {
        val sticky = track(artist = "Sticky Artist", title = "Sticky Title")
        val resolved = resolveAutoMatchInputs(
            stickyTrack = sticky,
            manualMode = true,
            manualQuery = null,
            manualArtistInput = "Input Artist",
            manualTitleInput = "Input Title"
        )
        assertEquals("Input Artist" to "Input Title", resolved)
    }

    private fun track(artist: String, title: String): TrackDetail = TrackDetail(
        id = "1",
        title = title,
        artist = artist,
        albumName = "Album",
        albumCoverUrl = "https://example.com/a.jpg",
        durationMs = 1000,
        popularity = 1,
        previewUrl = null
    )
}
