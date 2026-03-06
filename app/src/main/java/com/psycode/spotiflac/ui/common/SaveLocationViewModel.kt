package com.psycode.spotiflac.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psycode.spotiflac.domain.model.DefaultSaveLocation
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.usecase.savelocation.ObserveDefaultSaveLocationUseCase
import com.psycode.spotiflac.domain.usecase.savelocation.SetDefaultSaveLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaveLocationViewModel @Inject constructor(
    observeDefaultSaveLocation: ObserveDefaultSaveLocationUseCase,
    private val setDefaultSaveLocation: SetDefaultSaveLocationUseCase
) : ViewModel() {

    val defaultSaveLocation: StateFlow<DefaultSaveLocation> =
        observeDefaultSaveLocation().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DefaultSaveLocation()
        )

    fun setDefaultMediaLibrary() {
        viewModelScope.launch {
            setDefaultSaveLocation(SaveOption.MUSIC_LIBRARY, customFolderUri = null)
        }
    }

    fun setDefaultCustomFolder(folderUri: String) {
        viewModelScope.launch {
            setDefaultSaveLocation(SaveOption.CUSTOM_FOLDER, customFolderUri = folderUri)
        }
    }

    fun persistDefault(saveOption: SaveOption, customFolderUri: String?) {
        viewModelScope.launch {
            setDefaultSaveLocation(saveOption, customFolderUri)
        }
    }
}


