package com.psycode.spotiflac.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.usecase.mode.ClearAppModeUseCase
import com.psycode.spotiflac.domain.usecase.mode.ObserveAppModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModeBarViewModel @Inject constructor(
    private val clearAppMode: ClearAppModeUseCase,
    observeAppMode: ObserveAppModeUseCase
) : ViewModel() {

    val mode: StateFlow<AppMode> = observeAppMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppMode.Unselected)

    val isPublicMode: StateFlow<Boolean> = observeAppMode()
        .map { mode -> mode == AppMode.SpotifyPublic }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun clearMode() {
        viewModelScope.launch {
            clearAppMode()
        }
    }
}

