package com.psycode.spotiflac.domain.usecase.history
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import javax.inject.Inject

class ClearManualSearchHistoryUseCase @Inject constructor(
    private val repository: ManualSearchHistoryRepository
) {
    suspend operator fun invoke() = repository.clear()
}


