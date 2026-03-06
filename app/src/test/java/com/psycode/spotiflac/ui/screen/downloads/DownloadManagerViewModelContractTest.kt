package com.psycode.spotiflac.ui.screen.downloads

import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadStatus
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.model.TorrentResult
import com.psycode.spotiflac.domain.repository.DownloadRepository
import com.psycode.spotiflac.domain.repository.LocalFileRepository
import com.psycode.spotiflac.domain.repository.TorrentRepository
import com.psycode.spotiflac.domain.usecase.download.CancelDownloadUseCase
import com.psycode.spotiflac.domain.usecase.download.DeleteGroupUseCase
import com.psycode.spotiflac.domain.usecase.download.DeleteLocalFileByUriUseCase
import com.psycode.spotiflac.domain.usecase.download.EnqueueDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.torrent.GetTorrentFilesUseCase
import com.psycode.spotiflac.domain.usecase.download.ObserveDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.download.PauseGroupDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.download.PauseDownloadUseCase
import com.psycode.spotiflac.domain.usecase.download.RemoveDownloadUseCase
import com.psycode.spotiflac.domain.usecase.download.ResumeGroupDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.download.ResumeDownloadUseCase
import com.psycode.spotiflac.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadManagerViewModelContractTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `pause action is deduplicated while job is active`() = runTest {
        val downloadRepo = FakeDownloadRepository()
        val pauseGate = CompletableDeferred<Unit>()
        downloadRepo.onPause = {
            pauseGate.await()
        }
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        val firstPause = async { viewModel.onAction(DownloadManagerAction.Pause("1_task")) }
        val secondPause = async { viewModel.onAction(DownloadManagerAction.Pause("1_task")) }

        advanceUntilIdle()

        assertEquals(1, downloadRepo.pauseCalls.size)

        pauseGate.complete(Unit)
        firstPause.await()
        secondPause.await()
        collector.cancel()
    }

    @Test
    fun `resume action is deduplicated while job is active`() = runTest {
        val downloadRepo = FakeDownloadRepository()
        val resumeGate = CompletableDeferred<Unit>()
        downloadRepo.onResume = { resumeGate.await() }
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        val first = async { viewModel.onAction(DownloadManagerAction.Resume("1_task")) }
        val second = async { viewModel.onAction(DownloadManagerAction.Resume("1_task")) }

        advanceUntilIdle()
        assertEquals(1, downloadRepo.resumeCalls.size)

        resumeGate.complete(Unit)
        first.await()
        second.await()
        collector.cancel()
    }

    @Test
    fun `cancel action is deduplicated while job is active`() = runTest {
        val downloadRepo = FakeDownloadRepository()
        val cancelGate = CompletableDeferred<Unit>()
        downloadRepo.onCancel = { cancelGate.await() }
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        val first = async { viewModel.onAction(DownloadManagerAction.Cancel("1_task")) }
        val second = async { viewModel.onAction(DownloadManagerAction.Cancel("1_task")) }

        advanceUntilIdle()
        assertEquals(1, downloadRepo.cancelCalls.size)

        cancelGate.complete(Unit)
        first.await()
        second.await()
        collector.cancel()
    }

    @Test
    fun `mixed queue actions keep per-action dedupe under burst`() = runTest {
        val downloadRepo = FakeDownloadRepository()
        val pauseGate = CompletableDeferred<Unit>()
        val resumeGate = CompletableDeferred<Unit>()
        val cancelGate = CompletableDeferred<Unit>()
        downloadRepo.onPause = { pauseGate.await() }
        downloadRepo.onResume = { resumeGate.await() }
        downloadRepo.onCancel = { cancelGate.await() }

        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        repeat(5) { viewModel.onAction(DownloadManagerAction.Pause("burst_task")) }
        repeat(5) { viewModel.onAction(DownloadManagerAction.Resume("burst_task")) }
        repeat(5) { viewModel.onAction(DownloadManagerAction.Cancel("burst_task")) }

        advanceUntilIdle()
        assertEquals(1, downloadRepo.pauseCalls.count { it == "burst_task" })
        assertEquals(1, downloadRepo.resumeCalls.count { it == "burst_task" })
        assertEquals(1, downloadRepo.cancelCalls.count { it == "burst_task" })

        pauseGate.complete(Unit)
        resumeGate.complete(Unit)
        cancelGate.complete(Unit)
        advanceUntilIdle()
        collector.cancel()
    }

    @Test
    fun `group pause action is deduplicated while lock is active`() = runTest {
        val downloadRepo = FakeDownloadRepository(
            initialTasks = listOf(
                task(id = "7_a", status = DownloadStatus.RUNNING),
                task(id = "7_b", status = DownloadStatus.QUEUED)
            )
        )
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.PauseGroup(7))
        viewModel.onAction(DownloadManagerAction.PauseGroup(7))
        advanceUntilIdle()

        assertEquals(listOf(7), downloadRepo.pauseGroupCalls)
        collector.cancel()
    }

    @Test
    fun `open manage files updates ui state with loaded files`() = runTest {
        val torrentRepo = FakeTorrentRepository().apply {
            filesByTopic[42] = listOf(
                TorrentFile(
                    name = "track.flac",
                    size = 100L,
                    torrentFilePath = "/tmp/file.torrent",
                    innerPath = "Album/track.flac"
                )
            )
        }
        val viewModel = createViewModel(torrentRepo = torrentRepo)
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.OpenManageFiles(topicId = 42, torrentTitle = "Album"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(42, state.currentTopicId)
        assertEquals("Album", state.currentTitle)
        val success = state.manageFilesState as ManageFilesUiState.Success
        assertEquals(1, success.files.size)
        assertEquals("track.flac", success.files.first().name)
        collector.cancel()
    }

    @Test
    fun `remove completed entry with local delete sets success file-op state`() = runTest {
        val downloadRepo = FakeDownloadRepository()
        val localRepo = FakeLocalFileRepository().apply { deleteResult = true }
        val viewModel = createViewModel(
            downloadRepo = downloadRepo,
            localRepo = localRepo
        )
        val collector = collectUiState(viewModel)

        viewModel.onAction(
            DownloadManagerAction.RemoveCompletedEntry(
                taskId = "12_task",
                alsoDeleteLocal = true,
                contentUri = "content://downloads/12"
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("content://downloads/12"), localRepo.deleteCalls)
        assertEquals(listOf("12_task"), downloadRepo.removeCalls)
        assertEquals(
            DownloadManagerViewModel.FileOpUiState.Success(R.string.file_deleted_and_delisted),
            viewModel.uiState.value.fileOp
        )
        collector.cancel()
    }

    @Test
    fun `request and confirm delete group reflects items count and clears dialog`() = runTest {
        val downloadRepo = FakeDownloadRepository(
            initialTasks = listOf(
                task(id = "7_a", status = DownloadStatus.RUNNING),
                task(id = "7_b", status = DownloadStatus.PAUSED),
                task(id = "8_c", status = DownloadStatus.QUEUED)
            )
        )
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.RequestDeleteGroup(topicId = 7, title = "Album 7"))
        val confirm = viewModel.uiState.value.deleteDialog as DeleteGroupUiState.Confirm
        assertEquals(2, confirm.itemsCount)
        assertEquals("Album 7", confirm.title)

        viewModel.onAction(DownloadManagerAction.SetDeleteGroupAlsoDeleteLocalFiles(true))
        viewModel.onAction(DownloadManagerAction.ConfirmDeleteGroup)
        advanceUntilIdle()

        assertEquals(listOf(DeleteGroupCall(topicId = 7, alsoDeleteLocalFiles = true)), downloadRepo.deleteGroupCalls)
        assertTrue(viewModel.uiState.value.deleteDialog is DeleteGroupUiState.Hidden)
        collector.cancel()
    }

    @Test
    fun `confirm delete group keeps dialog open with error when use-case fails`() = runTest {
        val downloadRepo = FakeDownloadRepository().apply {
            deleteGroupError = IllegalStateException("boom")
        }
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.RequestDeleteGroup(topicId = 9, title = "Album 9"))
        viewModel.onAction(DownloadManagerAction.ConfirmDeleteGroup)
        advanceUntilIdle()

        val state = viewModel.uiState.value.deleteDialog as DeleteGroupUiState.Confirm
        assertEquals(9, state.topicId)
        assertEquals("boom", state.error)
        assertTrue(!state.inProgress)
        collector.cancel()
    }

    @Test
    fun `add selected to queue exposes error when repository enqueue fails`() = runTest {
        val downloadRepo = FakeDownloadRepository().apply {
            enqueueError = IllegalStateException("enqueue boom")
        }
        val viewModel = createViewModel(downloadRepo = downloadRepo)
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.OpenManageFiles(topicId = 11, torrentTitle = "Album"))
        viewModel.onAction(
            DownloadManagerAction.AddSelectedToQueue(
                files = listOf(
                    TorrentFile(
                        name = "a.flac",
                        size = 1L,
                        torrentFilePath = "/tmp/a.torrent",
                        innerPath = "a.flac"
                    )
                ),
                saveOption = SaveOption.MUSIC_LIBRARY,
                folderUri = null
            )
        )
        advanceUntilIdle()

        val fileOp = viewModel.uiState.value.fileOp as DownloadManagerViewModel.FileOpUiState.Error
        assertEquals("enqueue boom", fileOp.message)
        assertEquals(R.string.could_not_start_download, fileOp.fallbackResId)
        collector.cancel()
    }

    @Test
    fun `custom folder enqueue without uri returns validation file-op error`() = runTest {
        val viewModel = createViewModel()
        val collector = collectUiState(viewModel)

        viewModel.onAction(DownloadManagerAction.OpenManageFiles(topicId = 12, torrentTitle = "Album"))
        viewModel.onAction(
            DownloadManagerAction.AddSelectedToQueue(
                files = listOf(
                    TorrentFile(
                        name = "a.flac",
                        size = 1L,
                        torrentFilePath = "/tmp/a.torrent",
                        innerPath = "a.flac"
                    )
                ),
                saveOption = SaveOption.CUSTOM_FOLDER,
                folderUri = null
            )
        )
        advanceUntilIdle()

        val fileOp = viewModel.uiState.value.fileOp as DownloadManagerViewModel.FileOpUiState.Error
        assertEquals(R.string.could_not_start_download_no_folder, fileOp.fallbackResId)
        collector.cancel()
    }

    private fun kotlinx.coroutines.test.TestScope.collectUiState(
        viewModel: DownloadManagerViewModel
    ) = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
        viewModel.uiState.collect { }
    }

    private fun createViewModel(
        downloadRepo: FakeDownloadRepository = FakeDownloadRepository(),
        torrentRepo: FakeTorrentRepository = FakeTorrentRepository(),
        localRepo: FakeLocalFileRepository = FakeLocalFileRepository()
    ): DownloadManagerViewModel {
        return DownloadManagerViewModel(
            observeDownloads = ObserveDownloadsUseCase(downloadRepo),
            pauseDownload = PauseDownloadUseCase(downloadRepo),
            resumeDownload = ResumeDownloadUseCase(downloadRepo),
            pauseGroupDownloads = PauseGroupDownloadsUseCase(downloadRepo),
            resumeGroupDownloads = ResumeGroupDownloadsUseCase(downloadRepo),
            cancelDownload = CancelDownloadUseCase(downloadRepo),
            removeDownload = RemoveDownloadUseCase(downloadRepo),
            getTorrentFiles = GetTorrentFilesUseCase(torrentRepo),
            enqueueDownloads = EnqueueDownloadsUseCase(downloadRepo),
            deleteLocalFileByUri = DeleteLocalFileByUriUseCase(localRepo),
            deleteGroupUseCase = DeleteGroupUseCase(downloadRepo)
        )
    }

    private class FakeDownloadRepository(
        initialTasks: List<DownloadTask> = emptyList()
    ) : DownloadRepository {
        val pauseCalls = mutableListOf<String>()
        val resumeCalls = mutableListOf<String>()
        val cancelCalls = mutableListOf<String>()
        val pauseGroupCalls = mutableListOf<Int>()
        val resumeGroupCalls = mutableListOf<Int>()
        val removeCalls = mutableListOf<String>()
        val deleteGroupCalls = mutableListOf<DeleteGroupCall>()
        val enqueuedCalls = mutableListOf<EnqueueCall>()
        var onPause: (suspend () -> Unit)? = null
        var onResume: (suspend () -> Unit)? = null
        var onCancel: (suspend () -> Unit)? = null
        var deleteGroupError: Throwable? = null
        var enqueueError: Throwable? = null

        private val tasksFlow = MutableStateFlow(initialTasks)

        override suspend fun enqueueDownloads(
            topicId: Int,
            files: List<TorrentFile>,
            saveOption: SaveOption,
            folderUri: String?,
            torrentTitle: String
        ) {
            enqueueError?.let { throw it }
            enqueuedCalls += EnqueueCall(topicId, files, saveOption, folderUri, torrentTitle)
        }

        override fun observeDownloads(): Flow<List<DownloadTask>> = tasksFlow

        override suspend fun pauseDownload(taskId: String) {
            pauseCalls += taskId
            onPause?.invoke()
        }

        override suspend fun resumeDownload(taskId: String) {
            resumeCalls += taskId
            onResume?.invoke()
        }

        override suspend fun cancelDownload(taskId: String) {
            cancelCalls += taskId
            onCancel?.invoke()
        }

        override suspend fun pauseGroup(topicId: Int) {
            pauseGroupCalls += topicId
        }

        override suspend fun resumeGroup(topicId: Int) {
            resumeGroupCalls += topicId
        }

        override suspend fun removeDownload(taskId: String) {
            removeCalls += taskId
        }

        override suspend fun deleteGroup(topicId: Int, alsoDeleteLocalFiles: Boolean) {
            deleteGroupCalls += DeleteGroupCall(topicId = topicId, alsoDeleteLocalFiles = alsoDeleteLocalFiles)
            deleteGroupError?.let { throw it }
        }
    }

    private data class DeleteGroupCall(
        val topicId: Int,
        val alsoDeleteLocalFiles: Boolean
    )

    private data class EnqueueCall(
        val topicId: Int,
        val files: List<TorrentFile>,
        val saveOption: SaveOption,
        val folderUri: String?,
        val torrentTitle: String
    )

    private class FakeTorrentRepository : TorrentRepository {
        val filesByTopic = mutableMapOf<Int, List<TorrentFile>>()
        var getTorrentFilesError: Throwable? = null

        override fun searchTorrents(query: String, track: String, lossless: Boolean): Flow<List<TorrentResult>> {
            return MutableStateFlow(emptyList())
        }

        override suspend fun completeCaptcha(sessionId: String, solution: String) = Unit

        override suspend fun getTorrentFiles(topicId: Int): List<TorrentFile> {
            getTorrentFilesError?.let { throw it }
            return filesByTopic[topicId].orEmpty()
        }
    }

    private class FakeLocalFileRepository : LocalFileRepository {
        val deleteCalls = mutableListOf<String>()
        var deleteResult: Boolean = true
        var deleteError: Throwable? = null

        override suspend fun deleteByUri(uri: String): Boolean {
            deleteCalls += uri
            deleteError?.let { throw it }
            return deleteResult
        }
    }

    private fun task(id: String, status: DownloadStatus): DownloadTask = DownloadTask(
        id = id,
        fileName = "$id.flac",
        size = 100,
        progress = 30,
        status = status,
        errorMessage = null,
        contentUri = null,
        torrentTitle = "Album",
        speedBytesPerSec = 0,
        createdAt = 1L
    )
}

