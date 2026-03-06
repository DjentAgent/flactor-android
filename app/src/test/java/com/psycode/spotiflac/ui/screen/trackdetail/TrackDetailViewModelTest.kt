package com.psycode.spotiflac.ui.screen.trackdetail

import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TorrentResult
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.domain.repository.DownloadRepository
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import com.psycode.spotiflac.domain.repository.TorrentRepository
import com.psycode.spotiflac.domain.repository.TrackRepository
import com.psycode.spotiflac.domain.usecase.history.ClearManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.torrent.CompleteCaptchaUseCase
import com.psycode.spotiflac.domain.usecase.download.EnqueueDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.torrent.GetTorrentFilesUseCase
import com.psycode.spotiflac.domain.usecase.torrent.GetTorrentsForTrackUseCase
import com.psycode.spotiflac.domain.usecase.track.GetTrackDetailUseCase
import com.psycode.spotiflac.domain.usecase.history.ObserveManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.history.RemoveManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.history.SaveManualSearchHistoryUseCase
import com.psycode.spotiflac.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrackDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `latest manual search result wins over stale in-flight result`() = runTest {
        val trackRepo = FakeTrackRepository()
        val torrentRepo = FakeTorrentRepository(
            byQuery = mapOf(
                "first|" to QueryResponse(delayMs = 120, results = listOf(result("first"))),
                "second|" to QueryResponse(delayMs = 10, results = listOf(result("second")))
            )
        )
        val vm = createViewModel(trackRepo, torrentRepo)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }

        vm.onAction(TrackDetailAction.SearchTorrentsManual(artist = "first", title = ""))
        vm.onAction(TrackDetailAction.SearchTorrentsManual(artist = "second", title = ""))
        advanceUntilIdle()

        val state = vm.uiState.value.torrentState as TorrentUiState.Success
        assertEquals(1, state.results.size)
        assertEquals("second", state.results.first().title)
        collector.cancel()
    }

    @Test
    fun `manual search emits loading then success`() = runTest {
        val trackRepo = FakeTrackRepository()
        val torrentRepo = FakeTorrentRepository(
            byQuery = mapOf(
                "artist|track" to QueryResponse(delayMs = 20, results = listOf(result("ok")))
            )
        )
        val vm = createViewModel(trackRepo, torrentRepo)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }

        vm.onAction(TrackDetailAction.SearchTorrentsManual("artist", "track"))
        assertTrue(vm.uiState.value.torrentState is TorrentUiState.Loading)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.torrentState is TorrentUiState.Success)
        collector.cancel()
    }

    private fun createViewModel(
        trackRepo: FakeTrackRepository,
        torrentRepo: FakeTorrentRepository
    ): TrackDetailViewModel {
        val historyRepo = FakeManualSearchHistoryRepository()
        return TrackDetailViewModel(
            getTrackDetail = GetTrackDetailUseCase(trackRepo),
            getTorrents = GetTorrentsForTrackUseCase(torrentRepo),
            getTorrentFiles = GetTorrentFilesUseCase(torrentRepo),
            enqueueDownloadsUseCase = EnqueueDownloadsUseCase(FakeDownloadRepository()),
            completeCaptcha = CompleteCaptchaUseCase(torrentRepo),
            observeManualSearchHistory = ObserveManualSearchHistoryUseCase(historyRepo),
            saveManualSearchHistory = SaveManualSearchHistoryUseCase(historyRepo),
            removeManualSearchHistory = RemoveManualSearchHistoryUseCase(historyRepo),
            clearManualSearchHistory = ClearManualSearchHistoryUseCase(historyRepo)
        )
    }

    private data class QueryResponse(
        val delayMs: Long,
        val results: List<TorrentResult>
    )

    private class FakeTorrentRepository(
        private val byQuery: Map<String, QueryResponse>
    ) : TorrentRepository {
        override fun searchTorrents(query: String, track: String, lossless: Boolean): Flow<List<TorrentResult>> = flow {
            val key = "$query|$track"
            val response = byQuery[key] ?: QueryResponse(delayMs = 0, results = emptyList())
            delay(response.delayMs)
            emit(response.results)
        }

        override suspend fun completeCaptcha(sessionId: String, solution: String) = Unit

        override suspend fun getTorrentFiles(topicId: Int): List<TorrentFile> = emptyList()
    }

    private class FakeTrackRepository : TrackRepository {
        override fun getSavedTracks() = flowOf(androidx.paging.PagingData.empty<Track>())

        override fun searchTracks(query: String) = flowOf(androidx.paging.PagingData.empty<Track>())

        override fun getTrackById(trackId: String): Flow<TrackDetail> = flowOf(
            TrackDetail(
                id = trackId,
                title = "t",
                artist = "a",
                albumName = "al",
                albumCoverUrl = "",
                durationMs = 1,
                popularity = 1,
                previewUrl = null
            )
        )
    }

    private class FakeDownloadRepository : DownloadRepository {
        override suspend fun enqueueDownloads(
            topicId: Int,
            files: List<TorrentFile>,
            saveOption: SaveOption,
            folderUri: String?,
            torrentTitle: String
        ) = Unit

        override fun observeDownloads(): Flow<List<DownloadTask>> = MutableStateFlow(emptyList())

        override suspend fun pauseDownload(taskId: String) = Unit

        override suspend fun resumeDownload(taskId: String) = Unit

        override suspend fun pauseGroup(topicId: Int) = Unit

        override suspend fun resumeGroup(topicId: Int) = Unit

        override suspend fun cancelDownload(taskId: String) = Unit

        override suspend fun removeDownload(taskId: String) = Unit

        override suspend fun deleteGroup(topicId: Int, alsoDeleteLocalFiles: Boolean) = Unit
    }

    private class FakeManualSearchHistoryRepository : ManualSearchHistoryRepository {
        private val state = MutableStateFlow<List<ManualSearchHistoryEntry>>(emptyList())

        override fun observeHistory(): Flow<List<ManualSearchHistoryEntry>> = state

        override suspend fun save(entry: ManualSearchHistoryEntry) {
            val deduped = state.value.filterNot { it == entry }
            state.value = (listOf(entry) + deduped).take(8)
        }

        override suspend fun remove(entry: ManualSearchHistoryEntry) {
            state.value = state.value.filterNot { it == entry }
        }

        override suspend fun clear() {
            state.value = emptyList()
        }
    }

    private fun result(title: String) = TorrentResult(
        title = title,
        url = "u",
        size = "1 GB",
        seeders = 1,
        leechers = 0,
        topicId = 1
    )
}

