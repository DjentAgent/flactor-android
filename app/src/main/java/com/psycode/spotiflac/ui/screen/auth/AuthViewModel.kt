package com.psycode.spotiflac.ui.screen.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.domain.mode.AppMode
import com.psycode.spotiflac.domain.usecase.mode.SetAppModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val setAppModeUseCase: SetAppModeUseCase
) : ViewModel() {
    fun setMode(mode: AppMode, onDone: () -> Unit) {
        viewModelScope.launch {
            setAppModeUseCase(mode)
            onDone()
        }
    }
}

