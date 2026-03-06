package com.psycode.spotiflac.ui.screen.downloads

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.R
import com.psycode.spotiflac.domain.model.DownloadTask
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DownloadManagerAction {
    data class Pause(val taskId: String) : DownloadManagerAction
    data class Resume(val taskId: String) : DownloadManagerAction
    data class PauseGroup(val topicId: Int) : DownloadManagerAction
    data class ResumeGroup(val topicId: Int) : DownloadManagerAction
    data class Cancel(val taskId: String) : DownloadManagerAction
    data class OpenManageFiles(val topicId: Int, val torrentTitle: String) : DownloadManagerAction
    data object HideManageFiles : DownloadManagerAction
    data class AddSelectedToQueue(
        val files: List<TorrentFile>,
        val saveOption: SaveOption,
        val folderUri: String?
    ) : DownloadManagerAction
    data class RemoveCompletedEntry(
        val taskId: String,
        val alsoDeleteLocal: Boolean,
        val contentUri: String?
    ) : DownloadManagerAction
    data object ResetFileOpState : DownloadManagerAction
    data class RequestDeleteGroup(val topicId: Int, val title: String) : DownloadManagerAction
    data object CancelDeleteGroup : DownloadManagerAction
    data class SetDeleteGroupAlsoDeleteLocalFiles(val value: Boolean) : DownloadManagerAction
    data object ConfirmDeleteGroup : DownloadManagerAction
}

data class DownloadManagerUiState(
    val tasks: List<DownloadTask> = emptyList(),
    val manageFilesState: ManageFilesUiState = ManageFilesUiState.Hidden,
    val currentTopicId: Int? = null,
    val currentTitle: String = "",
    val fileOp: DownloadManagerViewModel.FileOpUiState = DownloadManagerViewModel.FileOpUiState.Idle,
    val deleteDialog: DeleteGroupUiState = DeleteGroupUiState.Hidden,
    val groupControls: Map<Int, GroupControlUiState> = emptyMap()
)

enum class GroupControlAction {
    PAUSE,
    RESUME
}

data class GroupControlUiState(
    val action: GroupControlAction?,
    val startedAtMs: Long,
    val lockedUntilMs: Long,
    val targetTaskIds: Set<String> = emptySet()
)

@HiltViewModel
class DownloadManagerViewModel @Inject constructor(
    observeDownloads: ObserveDownloadsUseCase,
    private val pauseDownload: PauseDownloadUseCase,
    private val resumeDownload: ResumeDownloadUseCase,
    private val pauseGroupDownloads: PauseGroupDownloadsUseCase,
    private val resumeGroupDownloads: ResumeGroupDownloadsUseCase,
    private val cancelDownload: CancelDownloadUseCase,
    private val removeDownload: RemoveDownloadUseCase,
    private val getTorrentFiles: GetTorrentFilesUseCase,
    private val enqueueDownloads: EnqueueDownloadsUseCase,
    private val deleteLocalFileByUri: DeleteLocalFileByUriUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase
) : ViewModel() {

    private fun trace(message: String) {
        runCatching { Log.d("SpotiFlacDMTrace", "DL_TRACE/VM $message") }
            .onFailure { println("DL_TRACE/VM $message") }
    }

    fun onAction(action: DownloadManagerAction) {
        when (action) {
            is DownloadManagerAction.Pause -> trace("onAction Pause taskId=${action.taskId}")
            is DownloadManagerAction.Resume -> trace("onAction Resume taskId=${action.taskId}")
            is DownloadManagerAction.PauseGroup -> trace("onAction PauseGroup topicId=${action.topicId}")
            is DownloadManagerAction.ResumeGroup -> trace("onAction ResumeGroup topicId=${action.topicId}")
            is DownloadManagerAction.Cancel -> trace("onAction Cancel taskId=${action.taskId}")
            else -> Unit
        }
        when (action) {
            is DownloadManagerAction.Pause -> pause(action.taskId)
            is DownloadManagerAction.Resume -> resume(action.taskId)
            is DownloadManagerAction.PauseGroup -> pauseGroup(action.topicId)
            is DownloadManagerAction.ResumeGroup -> resumeGroup(action.topicId)
            is DownloadManagerAction.Cancel -> cancel(action.taskId)
            is DownloadManagerAction.OpenManageFiles -> openManageFiles(action.topicId, action.torrentTitle)
            DownloadManagerAction.HideManageFiles -> hideManageFiles()
            is DownloadManagerAction.AddSelectedToQueue -> addSelectedToQueue(
                files = action.files,
                saveOption = action.saveOption,
                folderUri = action.folderUri
            )
            is DownloadManagerAction.RemoveCompletedEntry -> removeCompletedEntry(
                taskId = action.taskId,
                alsoDeleteLocal = action.alsoDeleteLocal,
                contentUri = action.contentUri
            )
            DownloadManagerAction.ResetFileOpState -> resetFileOpState()
            is DownloadManagerAction.RequestDeleteGroup -> requestDeleteGroup(action.topicId, action.title)
            DownloadManagerAction.CancelDeleteGroup -> cancelDeleteGroup()
            is DownloadManagerAction.SetDeleteGroupAlsoDeleteLocalFiles ->
                setDeleteGroupAlsoDeleteLocalFiles(action.value)
            DownloadManagerAction.ConfirmDeleteGroup -> confirmDeleteGroup()
        }
    }

    private val tasksFlow: StateFlow<List<DownloadTask>> =
        observeDownloads()
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = emptyList()
            )

    private val pauseJobs = mutableMapOf<String, Job>()
    private val resumeJobs = mutableMapOf<String, Job>()
    private val cancelJobs = mutableMapOf<String, Job>()
    private val groupJobs = mutableMapOf<Int, Job>()
    private val _groupControls = MutableStateFlow<Map<Int, GroupControlUiState>>(emptyMap())

    private fun pause(taskId: String) {
        if (pauseJobs.containsKey(taskId)) {
            trace("pause dedup ignored taskId=$taskId")
            return
        }
        pauseJobs[taskId] = viewModelScope.launch {
            try {
                pauseDownload(taskId)
            } finally {
                pauseJobs.remove(taskId)
            }
        }
    }

    private fun resume(taskId: String) {
        if (resumeJobs.containsKey(taskId)) {
            trace("resume dedup ignored taskId=$taskId")
            return
        }
        resumeJobs[taskId] = viewModelScope.launch {
            try {
                resumeDownload(taskId)
            } finally {
                resumeJobs.remove(taskId)
            }
        }
    }

    private fun cancel(taskId: String) {
        if (cancelJobs.containsKey(taskId)) {
            trace("cancel dedup ignored taskId=$taskId")
            return
        }
        cancelJobs[taskId] = viewModelScope.launch {
            try {
                cancelDownload(taskId)
            } finally {
                cancelJobs.remove(taskId)
            }
        }
    }

    private fun pauseGroup(topicId: Int) {
        applyGroupAction(topicId, GroupControlAction.PAUSE) {
            pauseGroupDownloads(topicId)
        }
    }

    private fun resumeGroup(topicId: Int) {
        applyGroupAction(topicId, GroupControlAction.RESUME) {
            resumeGroupDownloads(topicId)
        }
    }

    private fun applyGroupAction(
        topicId: Int,
        action: GroupControlAction,
        block: suspend () -> Unit
    ) {
        val now = System.currentTimeMillis()
        val current = _groupControls.value[topicId]
        if (current != null && current.lockedUntilMs > now) {
            trace(
                "groupAction blocked topicId=$topicId action=$action lockLeftMs=${current.lockedUntilMs - now}"
            )
            return
        }
        if (groupJobs[topicId]?.isActive == true) {
            trace("groupAction blocked topicId=$topicId action=$action reason=job_active")
            return
        }
        val targetTaskIds = resolveGroupTargetTaskIds(topicId, action)
        if (targetTaskIds.isEmpty()) {
            trace("groupAction skipped topicId=$topicId action=$action reason=no_targets")
            return
        }

        val state = GroupControlUiState(
            action = action,
            startedAtMs = now,
            lockedUntilMs = now + GROUP_ACTION_LOCK_TTL_MS,
            targetTaskIds = targetTaskIds
        )
        trace(
            "groupAction start topicId=$topicId action=$action lockMs=$GROUP_ACTION_LOCK_TTL_MS " +
                "targets=${targetTaskIds.size}"
        )
        _groupControls.value = _groupControls.value + (topicId to state)

        groupJobs[topicId] = viewModelScope.launch {
            try {
                block()
                trace("groupAction dispatched topicId=$topicId action=$action")
            } finally {
                delay(GROUP_ACTION_STATUS_TTL_MS)
                clearGroupActionIfSame(topicId, state.startedAtMs)
                delay((GROUP_ACTION_LOCK_TTL_MS - GROUP_ACTION_STATUS_TTL_MS).coerceAtLeast(0L))
                removeGroupControlIfStale(topicId)
                trace("groupAction finished topicId=$topicId action=$action")
                groupJobs.remove(topicId)
            }
        }
    }

    private fun resolveGroupTargetTaskIds(
        topicId: Int,
        action: GroupControlAction
    ): Set<String> = tasksFlow.value
        .asSequence()
        .filter { parseTopicIdFromTaskId(it.id) == topicId }
        .filter { task ->
            when (action) {
                GroupControlAction.PAUSE ->
                    task.status == com.psycode.spotiflac.domain.model.DownloadStatus.RUNNING ||
                        task.status == com.psycode.spotiflac.domain.model.DownloadStatus.QUEUED
                GroupControlAction.RESUME ->
                    task.status == com.psycode.spotiflac.domain.model.DownloadStatus.PAUSED ||
                        task.status == com.psycode.spotiflac.domain.model.DownloadStatus.FAILED
            }
        }
        .map { it.id }
        .toSet()

    private fun clearGroupActionIfSame(topicId: Int, startedAtMs: Long) {
        val current = _groupControls.value[topicId] ?: return
        if (current.startedAtMs != startedAtMs) return
        _groupControls.value = _groupControls.value + (topicId to current.copy(action = null))
        trace("groupAction clearStatus topicId=$topicId")
    }

    private fun removeGroupControlIfStale(topicId: Int) {
        val current = _groupControls.value[topicId] ?: return
        if (current.lockedUntilMs > System.currentTimeMillis()) return
        _groupControls.value = _groupControls.value - topicId
        trace("groupAction removeControl topicId=$topicId")
    }

    private val _manageFilesState = MutableStateFlow<ManageFilesUiState>(ManageFilesUiState.Hidden)

    private val _currentTopicId = MutableStateFlow<Int?>(null)

    private val _currentTitle = MutableStateFlow("")

    private var loadFilesJob: Job? = null
    private var lastLoadedTopicId: Int? = null

    private fun openManageFiles(topicId: Int, torrentTitle: String) {
        if (loadFilesJob?.isActive == true && lastLoadedTopicId == topicId) return

        _currentTopicId.value = topicId
        _currentTitle.value = torrentTitle

        loadFilesJob?.cancel()
        loadFilesJob = viewModelScope.launch {
            _manageFilesState.value = ManageFilesUiState.Loading
            lastLoadedTopicId = topicId
            try {
                val files = getTorrentFiles(topicId)
                if (_currentTopicId.value == topicId) {
                    _manageFilesState.value = ManageFilesUiState.Success(files)
                }
            } catch (e: Exception) {
                if (_currentTopicId.value == topicId) {
                    _manageFilesState.value = ManageFilesUiState.Error(
                        resolveManageFilesLoadErrorMessage(e)
                    )
                }
            }
        }
    }

    private fun hideManageFiles() {
        loadFilesJob?.cancel()
        loadFilesJob = null
        lastLoadedTopicId = null
        _manageFilesState.value = ManageFilesUiState.Hidden
        _currentTopicId.value = null
        _currentTitle.value = ""
    }

    private fun addSelectedToQueue(
        files: List<TorrentFile>,
        saveOption: SaveOption,
        folderUri: String?
    ) {
        val topicId = _currentTopicId.value ?: return
        val title = _currentTitle.value
        viewModelScope.launch {
            try {
                if (saveOption == SaveOption.CUSTOM_FOLDER && folderUri.isNullOrBlank()) {
                    _fileOpState.value = FileOpUiState.Error(
                        message = null,
                        fallbackResId = R.string.could_not_start_download_no_folder
                    )
                    return@launch
                }
                enqueueDownloads(topicId, files, saveOption, folderUri, title)
            } catch (e: Exception) {
                _fileOpState.value = FileOpUiState.Error(
                    message = e.message,
                    fallbackResId = R.string.could_not_start_download
                )
            }
        }
    }

    sealed interface FileOpUiState {
        data object Idle : FileOpUiState
        data object Running : FileOpUiState
        data class Success(val messageResId: Int) : FileOpUiState
        data class Error(val message: String?, val fallbackResId: Int) : FileOpUiState
    }

    private val _fileOpState = MutableStateFlow<FileOpUiState>(FileOpUiState.Idle)

    private fun resetFileOpState() {
        _fileOpState.value = FileOpUiState.Idle
    }

    private fun removeCompletedEntry(
        taskId: String,
        alsoDeleteLocal: Boolean,
        contentUri: String?
    ) {
        viewModelScope.launch {
            _fileOpState.value = FileOpUiState.Running
            var fileDeleted: Boolean? = null
            try {
                if (alsoDeleteLocal && !contentUri.isNullOrBlank()) {
                    fileDeleted = deleteLocalFileByUri(contentUri)
                }

                removeDownload(taskId)

                val messageResId = resolveRemoveEntrySuccessMessageRes(
                    alsoDeleteLocal = alsoDeleteLocal,
                    fileDeleted = fileDeleted
                )
                _fileOpState.value = FileOpUiState.Success(messageResId)
            } catch (e: Exception) {
                val fallbackResId = resolveRemoveEntryErrorFallbackRes(
                    alsoDeleteLocal = alsoDeleteLocal,
                    fileDeleted = fileDeleted
                )
                _fileOpState.value = FileOpUiState.Error(
                    message = e.message,
                    fallbackResId = fallbackResId
                )
            }
        }
    }

    private val _deleteDialog = MutableStateFlow<DeleteGroupUiState>(DeleteGroupUiState.Hidden)

    val uiState: StateFlow<DownloadManagerUiState> =
        combine(
            combine(
                tasksFlow,
                _manageFilesState.asStateFlow(),
                _currentTopicId.asStateFlow(),
                _currentTitle.asStateFlow(),
                _fileOpState.asStateFlow()
            ) { tasks, manageFilesState, currentTopicId, currentTitle, fileOp ->
                DownloadManagerUiState(
                    tasks = tasks,
                    manageFilesState = manageFilesState,
                    currentTopicId = currentTopicId,
                    currentTitle = currentTitle,
                    fileOp = fileOp
                )
            },
            _groupControls.asStateFlow(),
            _deleteDialog.asStateFlow()
        ) { core, groupControls, deleteDialog ->
            DownloadManagerUiState(
                tasks = core.tasks,
                manageFilesState = core.manageFilesState,
                currentTopicId = core.currentTopicId,
                currentTitle = core.currentTitle,
                fileOp = core.fileOp,
                deleteDialog = deleteDialog,
                groupControls = groupControls
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadManagerUiState()
        )

    private var deleteJob: Job? = null

    private fun requestDeleteGroup(topicId: Int, title: String) {
        if (_deleteDialog.value !is DeleteGroupUiState.Hidden) return
        val count = tasksFlow.value.count { task ->
            parseTopicIdFromTaskId(task.id) == topicId
        }
        _deleteDialog.value = DeleteGroupUiState.Confirm(
            topicId = topicId,
            title = title,
            itemsCount = count,
            alsoDeleteLocalFiles = false,
            inProgress = false,
            error = null
        )
    }

    private fun cancelDeleteGroup() {
        deleteJob?.cancel()
        deleteJob = null
        _deleteDialog.value = DeleteGroupUiState.Hidden
    }

    private fun setDeleteGroupAlsoDeleteLocalFiles(value: Boolean) {
        val state = _deleteDialog.value as? DeleteGroupUiState.Confirm ?: return
        if (state.inProgress) return
        _deleteDialog.value = state.copy(alsoDeleteLocalFiles = value)
    }

    private fun confirmDeleteGroup() {
        val state = _deleteDialog.value as? DeleteGroupUiState.Confirm ?: return
        if (deleteJob?.isActive == true) return
        _deleteDialog.value = state.copy(inProgress = true, error = null)

        deleteJob = viewModelScope.launch {
            try {
                if (_currentTopicId.value == state.topicId) hideManageFiles()
                deleteGroupUseCase(
                    topicId = state.topicId,
                    alsoDeleteLocalFiles = state.alsoDeleteLocalFiles
                )
                _deleteDialog.value = DeleteGroupUiState.Hidden
            } catch (e: Exception) {
                _deleteDialog.value = state.copy(
                    inProgress = false,
                    error = e.message
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadFilesJob?.cancel()
        deleteJob?.cancel()
        pauseJobs.values.forEach { it.cancel() }
        resumeJobs.values.forEach { it.cancel() }
        cancelJobs.values.forEach { it.cancel() }
        groupJobs.values.forEach { it.cancel() }
    }
}

private const val GROUP_ACTION_STATUS_TTL_MS = 900L
private const val GROUP_ACTION_LOCK_TTL_MS = 1_600L

sealed interface ManageFilesUiState {
    data object Hidden : ManageFilesUiState
    data object Loading : ManageFilesUiState
    data class Success(val files: List<TorrentFile>) : ManageFilesUiState
    data class Error(val message: String) : ManageFilesUiState
}

sealed interface DeleteGroupUiState {
    data object Hidden : DeleteGroupUiState
    data class Confirm(
        val topicId: Int,
        val title: String,
        val itemsCount: Int,
        val alsoDeleteLocalFiles: Boolean,
        val inProgress: Boolean,
        val error: String?
    ) : DeleteGroupUiState
}

