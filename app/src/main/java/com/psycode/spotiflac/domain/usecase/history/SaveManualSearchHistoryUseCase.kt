package com.psycode.spotiflac.domain.usecase.history
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import javax.inject.Inject

class SaveManualSearchHistoryUseCase @Inject constructor(
    private val repository: ManualSearchHistoryRepository
) {
    suspend operator fun invoke(entry: ManualSearchHistoryEntry) = repository.save(entry)
}


