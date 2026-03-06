package com.psycode.spotiflac.domain.usecase.mode
import com.psycode.spotiflac.domain.repository.AppModeRepository
import javax.inject.Inject

class ClearAppModeUseCase @Inject constructor(
    private val appModeRepository: AppModeRepository
) {
    suspend operator fun invoke() {
        appModeRepository.clearMode()
    }
}


