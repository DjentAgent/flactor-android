package com.psycode.spotiflac.ui.screen.tracklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.model.Track
import com.psycode.spotiflac.domain.usecase.mode.ClearAppModeUseCase
import com.psycode.spotiflac.domain.usecase.track.GetSavedTracksUseCase
import com.psycode.spotiflac.domain.usecase.mode.ObserveAppModeUseCase
import com.psycode.spotiflac.domain.usecase.track.SearchTracksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackListUiState(
    val isPublicMode: Boolean = false,
    val query: String = ""
)

sealed interface TrackListAction {
    data class QueryChanged(val query: String) : TrackListAction
    data object ResetModeClicked : TrackListAction
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrackListViewModel @Inject constructor(
    private val getSavedTracks: GetSavedTracksUseCase,
    private val searchTracks: SearchTracksUseCase,
    observeAppMode: ObserveAppModeUseCase,
    private val clearAppMode: ClearAppModeUseCase
) : ViewModel() {

    private val isPublicModeFlow: StateFlow<Boolean> =
        observeAppMode()
            .map { mode -> mode == AppMode.SpotifyPublic }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<TrackListUiState> =
        combine(isPublicModeFlow, queryFlow) { isPublicMode, query ->
            TrackListUiState(isPublicMode = isPublicMode, query = query)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TrackListUiState()
        )

    fun onAction(action: TrackListAction) {
        when (action) {
            is TrackListAction.QueryChanged -> queryFlow.value = action.query
            TrackListAction.ResetModeClicked -> viewModelScope.launch { clearAppMode() }
        }
    }

    val pagingData: StateFlow<PagingData<Track>> =
        combine(
            queryFlow
                .map { it.trim() }
                .debounce(450)
                .distinctUntilChanged(),
            isPublicModeFlow
        ) { query, isPublicMode -> query to isPublicMode }
            .flatMapLatest { (query, isPublicMode) ->
                when {
                    !isPublicMode -> flowOf(PagingData.empty())
                    query.isBlank() -> getSavedTracks().cachedIn(viewModelScope)
                    query.length < 2 -> flowOf(PagingData.empty())
                    else -> searchTracks(query).cachedIn(viewModelScope)
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = PagingData.empty()
            )
}

