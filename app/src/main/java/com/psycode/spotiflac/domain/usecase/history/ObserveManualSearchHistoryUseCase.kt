package com.psycode.spotiflac.domain.usecase.history
import com.psycode.spotiflac.domain.model.ManualSearchHistoryEntry
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveManualSearchHistoryUseCase @Inject constructor(
    private val repository: ManualSearchHistoryRepository
) {
    operator fun invoke(): Flow<List<ManualSearchHistoryEntry>> = repository.observeHistory()
}


