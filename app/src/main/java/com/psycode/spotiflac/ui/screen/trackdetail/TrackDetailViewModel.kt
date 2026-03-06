package com.psycode.spotiflac.ui.screen.trackdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.domain.model.CaptchaRequiredException
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.model.TorrentFile
import com.psycode.spotiflac.domain.usecase.history.ClearManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.torrent.CompleteCaptchaUseCase
import com.psycode.spotiflac.domain.usecase.download.EnqueueDownloadsUseCase
import com.psycode.spotiflac.domain.usecase.torrent.GetTorrentsForTrackUseCase
import com.psycode.spotiflac.domain.usecase.torrent.GetTorrentFilesUseCase
import com.psycode.spotiflac.domain.usecase.track.GetTrackDetailUseCase
import com.psycode.spotiflac.domain.usecase.history.ObserveManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.history.RemoveManualSearchHistoryUseCase
import com.psycode.spotiflac.domain.usecase.history.SaveManualSearchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

private const val MIN_TORRENT_LOADING_MS = 450L
private val TORRENT_DOWNLOAD_HTTP_CODE_REGEX = Regex("\\.torrent:\\s*(\\d{3})")

enum class TorrentSearchErrorReason {
    SERVER_UNREACHABLE,
    TIMEOUT,
    UNKNOWN
}

data class TrackDetailScreenUiState(
    val trackState: TrackDetailUiState = TrackDetailUiState.Loading,
    val torrentState: TorrentUiState = TorrentUiState.Loading,
    val captchaState: CaptchaState = CaptchaState.Idle,
    val losslessSelected: Boolean = true,
    val isCaptchaSubmitting: Boolean = false,
    val torrentFilesState: TorrentFilesUiState = TorrentFilesUiState.Hidden,
    val manualQuery: Pair<String, String>? = null,
    val manualSearchHistory: List<ManualSearchHistoryEntry> = emptyList()
)

sealed interface TrackDetailAction {
    data class LoadTrack(val trackId: String) : TrackDetailAction
    data class SetLosslessFilter(val enabled: Boolean) : TrackDetailAction
    data class SubmitCaptcha(val solution: String) : TrackDetailAction
    data class SearchTorrentsManual(val artist: String, val title: String) : TrackDetailAction
    data class SaveManualSearchHistory(val entry: ManualSearchHistoryEntry) : TrackDetailAction
    data class RemoveManualSearchHistory(val entry: ManualSearchHistoryEntry) : TrackDetailAction
    data object ClearManualSearchHistory : TrackDetailAction
    data object ClearManualOverrides : TrackDetailAction
    data class LoadTorrentFiles(val topicId: Int) : TrackDetailAction
    data object RetryLoadTorrentFiles : TrackDetailAction
    data object HideTorrentFiles : TrackDetailAction
    data class StartDownloads(
        val topicId: Int,
        val files: List<TorrentFile>,
        val saveOption: SaveOption,
        val folderUri: String?,
        val torrentTitle: String
    ) : TrackDetailAction
}

@HiltViewModel
class TrackDetailViewModel @Inject constructor(
    private val getTrackDetail: GetTrackDetailUseCase,
    private val getTorrents: GetTorrentsForTrackUseCase,
    private val getTorrentFiles: GetTorrentFilesUseCase,
    private val enqueueDownloadsUseCase: EnqueueDownloadsUseCase,
    private val completeCaptcha: CompleteCaptchaUseCase,
    observeManualSearchHistory: ObserveManualSearchHistoryUseCase,
    private val saveManualSearchHistory: SaveManualSearchHistoryUseCase,
    private val removeManualSearchHistory: RemoveManualSearchHistoryUseCase,
    private val clearManualSearchHistory: ClearManualSearchHistoryUseCase
) : ViewModel() {

    private val trackState = MutableStateFlow<TrackDetailUiState>(TrackDetailUiState.Loading)
    private val torrentState = MutableStateFlow<TorrentUiState>(TorrentUiState.Loading)
    private val captchaState = MutableStateFlow<CaptchaState>(CaptchaState.Idle)
    private val losslessSelected = MutableStateFlow(true)
    private val isCaptchaSubmitting = MutableStateFlow(false)
    private val torrentFilesState = MutableStateFlow<TorrentFilesUiState>(TorrentFilesUiState.Hidden)
    private val manualQuery = MutableStateFlow<Pair<String, String>?>(null)
    private val manualSearchHistory = observeManualSearchHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private data class CoreUiState(
        val trackState: TrackDetailUiState,
        val torrentState: TorrentUiState,
        val captchaState: CaptchaState,
        val losslessSelected: Boolean,
        val isCaptchaSubmitting: Boolean
    )

    val uiState: StateFlow<TrackDetailScreenUiState> =
        combine(
            combine(
                trackState,
                torrentState,
                captchaState,
                losslessSelected,
                isCaptchaSubmitting
            ) { track, torrents, captcha, lossless, submitting ->
                CoreUiState(
                    trackState = track,
                    torrentState = torrents,
                    captchaState = captcha,
                    losslessSelected = lossless,
                    isCaptchaSubmitting = submitting
                )
            },
            torrentFilesState,
            manualQuery,
            manualSearchHistory
        ) { core, files, manual, history ->
            TrackDetailScreenUiState(
                trackState = core.trackState,
                torrentState = core.torrentState,
                captchaState = core.captchaState,
                losslessSelected = core.losslessSelected,
                isCaptchaSubmitting = core.isCaptchaSubmitting,
                torrentFilesState = files,
                manualQuery = manual,
                manualSearchHistory = history
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackDetailScreenUiState()
        )

    private var lastArtist: String = ""
    private var lastTitle: String = ""
    private var manualArtistOverride: String? = null
    private var manualTitleOverride: String? = null
    private var lastFilesTopicId: Int? = null
    private var loadTrackJob: Job? = null
    private var refreshTorrentsJob: Job? = null
    private var refreshGeneration: Long = 0L

    fun onAction(action: TrackDetailAction) {
        when (action) {
            is TrackDetailAction.LoadTrack -> loadTrack(action.trackId)
            is TrackDetailAction.SetLosslessFilter -> setLosslessFilter(action.enabled)
            is TrackDetailAction.SubmitCaptcha -> submitCaptchaSolution(action.solution)
            is TrackDetailAction.SearchTorrentsManual -> searchTorrentsManual(action.artist, action.title)
            is TrackDetailAction.SaveManualSearchHistory -> persistHistoryEntry(action.entry)
            is TrackDetailAction.RemoveManualSearchHistory -> removeHistoryEntry(action.entry)
            TrackDetailAction.ClearManualSearchHistory -> clearHistory()
            TrackDetailAction.ClearManualOverrides -> clearManualOverrides()
            is TrackDetailAction.LoadTorrentFiles -> loadTorrentFiles(action.topicId)
            TrackDetailAction.RetryLoadTorrentFiles -> retryLoadTorrentFiles()
            TrackDetailAction.HideTorrentFiles -> hideTorrentFiles()
            is TrackDetailAction.StartDownloads -> startDownloads(
                topicId = action.topicId,
                files = action.files,
                saveOption = action.saveOption,
                folderUri = action.folderUri,
                torrentTitle = action.torrentTitle
            )
        }
    }

    private fun currentArtist(): String = manualArtistOverride ?: lastArtist
    private fun currentTitle(): String = manualTitleOverride ?: lastTitle

    private fun loadTrack(id: String) {
        loadTrackJob?.cancel()
        loadTrackJob = viewModelScope.launch {
            getTrackDetail(id)
                .onStart {
                    trackState.value = TrackDetailUiState.Loading
                    manualArtistOverride = null
                    manualTitleOverride = null
                    manualQuery.value = null
                }
                .catch { e -> trackState.value = TrackDetailUiState.Error(e.message) }
                .collect { detail ->
                    trackState.value = TrackDetailUiState.Success(detail)
                    lastArtist = detail.artist
                    lastTitle = detail.title
                    refreshTorrents()
                }
        }
    }

    private fun setLosslessFilter(enabled: Boolean) {
        if (losslessSelected.value != enabled) {
            losslessSelected.value = enabled
            refreshTorrents()
        }
    }

    private fun submitCaptchaSolution(solution: String) {
        val sessionId = (captchaState.value as? CaptchaState.Required)?.sessionId ?: return
        viewModelScope.launch {
            isCaptchaSubmitting.value = true
            try {
                completeCaptcha(sessionId, solution)
                captchaState.value = CaptchaState.Idle
                refreshTorrents()
            } catch (_: Exception) {
                captchaState.value = CaptchaState.Error("Invalid captcha solution")
            } finally {
                isCaptchaSubmitting.value = false
            }
        }
    }

    private fun refreshTorrents() {
        val artist = currentArtist().trim()
        val title = currentTitle().trim()
        if (artist.isBlank()) {
            captchaState.value = CaptchaState.Idle
            torrentState.value = TorrentUiState.Success(emptyList())
            return
        }

        val generation = ++refreshGeneration
        refreshTorrentsJob?.cancel()
        refreshTorrentsJob = viewModelScope.launch {
            var loadingStartedAt = 0L
            getTorrents(
                q = artist,
                track = title,
                lossless = losslessSelected.value
            )
                .onStart {
                    if (generation == refreshGeneration) {
                        captchaState.value = CaptchaState.Idle
                        torrentState.value = TorrentUiState.Loading
                        loadingStartedAt = System.currentTimeMillis()
                    }
                }
                .catch { e ->
                    if (generation != refreshGeneration) return@catch
                    ensureMinimumTorrentLoading(loadingStartedAt)
                    if (e is CaptchaRequiredException) {
                        captchaState.value = CaptchaState.Required(
                            sessionId = e.sessionId,
                            captchaImageUrl = e.captchaImageUrl
                        )
                    } else {
                        torrentState.value = TorrentUiState.Error(resolveTorrentSearchErrorReason(e))
                    }
                }
                .collect { list ->
                    if (generation == refreshGeneration) {
                        ensureMinimumTorrentLoading(loadingStartedAt)
                        torrentState.value = TorrentUiState.Success(list)
                    }
                }
        }
    }

    private fun searchTorrentsManual(artist: String, title: String) {
        val a = artist.trim()
        val t = title.trim()

        if (a.isBlank() && t.isBlank()) {
            clearManualOverrides()
            return
        }

        manualArtistOverride = a.ifBlank { null }
        manualTitleOverride = t
        manualQuery.value = a to t

        refreshTorrents()
    }

    private fun clearManualOverrides() {
        manualArtistOverride = null
        manualTitleOverride = null
        manualQuery.value = null
        refreshTorrents()
    }

    private fun resolveTorrentSearchErrorReason(error: Throwable): TorrentSearchErrorReason {
        return when (error) {
            is SocketTimeoutException -> TorrentSearchErrorReason.TIMEOUT
            is UnknownHostException,
            is ConnectException -> TorrentSearchErrorReason.SERVER_UNREACHABLE
            is HttpException -> {
                when (error.code()) {
                    408, 504 -> TorrentSearchErrorReason.TIMEOUT
                    502, 503, 521, 522, 523, 524 -> TorrentSearchErrorReason.SERVER_UNREACHABLE
                    else -> TorrentSearchErrorReason.UNKNOWN
                }
            }

            is IOException -> TorrentSearchErrorReason.SERVER_UNREACHABLE
            else -> TorrentSearchErrorReason.UNKNOWN
        }
    }

    private fun loadTorrentFiles(topicId: Int) {
        lastFilesTopicId = topicId
        viewModelScope.launch {
            torrentFilesState.value = TorrentFilesUiState.Loading
            val timeoutMs = 30_000L
            val stepMs = 1500L
            val started = System.currentTimeMillis()

            while (true) {
                try {
                    val files = withContext(Dispatchers.IO) { getTorrentFiles(topicId) }
                    torrentFilesState.value = TorrentFilesUiState.Success(files)
                    return@launch
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    val timedOut = System.currentTimeMillis() - started > timeoutMs
                    if (!isRetryableTorrentFilesError(e) || timedOut) {
                        torrentFilesState.value = TorrentFilesUiState.Error(
                            resolveTorrentFilesLoadErrorMessage(e, timedOut)
                        )
                        return@launch
                    }
                    delay(stepMs)
                }
            }
        }
    }

    private fun isRetryableTorrentFilesError(error: Throwable): Boolean {
        val code = extractTorrentDownloadHttpCode(error)
        if (code != null) {
            return code == 408 || code == 429 || code >= 500
        }
        return when (error) {
            is SocketTimeoutException,
            is UnknownHostException,
            is ConnectException,
            is IOException -> true
            else -> false
        }
    }

    private fun extractTorrentDownloadHttpCode(error: Throwable): Int? {
        if (error is HttpException) return error.code()
        val message = error.message.orEmpty()
        val match = TORRENT_DOWNLOAD_HTTP_CODE_REGEX.find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }
    private fun resolveTorrentFilesLoadErrorMessage(error: Throwable, timedOut: Boolean): String {
        val code = extractTorrentDownloadHttpCode(error)
        return when {
            code == 404 -> "Torrent is unavailable or was removed (404)."
            code != null && code in 400..499 && code != 408 && code != 429 ->
                "Server rejected .torrent request (HTTP $code)."
            timedOut -> "Couldn't get .torrent within 30 seconds. Check connection and retry."
            !error.message.isNullOrBlank() -> error.message.orEmpty()
            else -> "Couldn't load torrent file list."
        }
    }

    private fun retryLoadTorrentFiles() {
        lastFilesTopicId?.let { loadTorrentFiles(it) }
    }

    private fun hideTorrentFiles() {
        torrentFilesState.value = TorrentFilesUiState.Hidden
    }

    private fun startDownloads(
        topicId: Int,
        files: List<TorrentFile>,
        saveOption: SaveOption,
        folderUri: String?,
        torrentTitle: String
    ) {
        viewModelScope.launch {
            enqueueDownloadsUseCase(topicId, files, saveOption, folderUri, torrentTitle)
        }
    }

    private fun persistHistoryEntry(entry: ManualSearchHistoryEntry) {
        viewModelScope.launch { saveManualSearchHistory(entry) }
    }

    private fun removeHistoryEntry(entry: ManualSearchHistoryEntry) {
        viewModelScope.launch { removeManualSearchHistory(entry) }
    }

    private fun clearHistory() {
        viewModelScope.launch { clearManualSearchHistory() }
    }

    private suspend fun ensureMinimumTorrentLoading(loadingStartedAt: Long) {
        if (loadingStartedAt <= 0L) return
        val elapsed = System.currentTimeMillis() - loadingStartedAt
        val remaining = MIN_TORRENT_LOADING_MS - elapsed
        if (remaining > 0) delay(remaining)
    }

    override fun onCleared() {
        super.onCleared()
        loadTrackJob?.cancel()
        refreshTorrentsJob?.cancel()
    }
}

sealed interface TrackDetailUiState {
    data object Loading : TrackDetailUiState
    data class Success(val track: com.psycode.spotiflac.domain.model.TrackDetail) : TrackDetailUiState
    data class Error(val message: String?) : TrackDetailUiState
}

sealed interface TorrentUiState {
    data object Loading : TorrentUiState
    data class Success(val results: List<com.psycode.spotiflac.domain.model.TorrentResult>) : TorrentUiState
    data class Error(val reason: TorrentSearchErrorReason) : TorrentUiState
}

sealed interface CaptchaState {
    data object Idle : CaptchaState
    data class Required(val sessionId: String, val captchaImageUrl: String) : CaptchaState
    data class Error(val message: String) : CaptchaState
}

sealed interface TorrentFilesUiState {
    data object Hidden : TorrentFilesUiState
    data object Loading : TorrentFilesUiState
    data class Success(val files: List<TorrentFile>) : TorrentFilesUiState
    data class Error(val message: String) : TorrentFilesUiState
}

