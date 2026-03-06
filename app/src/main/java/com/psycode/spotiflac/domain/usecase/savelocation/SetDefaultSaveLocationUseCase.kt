package com.psycode.spotiflac.domain.usecase.savelocation
import com.psycode.spotiflac.domain.model.SaveOption
import com.psycode.spotiflac.domain.repository.SaveLocationRepository
import javax.inject.Inject

class SetDefaultSaveLocationUseCase @Inject constructor(
    private val repository: SaveLocationRepository
) {
    suspend operator fun invoke(saveOption: SaveOption, customFolderUri: String?) {
        repository.setDefaultSaveLocation(saveOption, customFolderUri)
    }
}



