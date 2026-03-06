package com.psycode.spotiflac.domain.usecase.download
import com.psycode.spotiflac.domain.repository.LocalFileRepository
import javax.inject.Inject

class DeleteLocalFileByUriUseCase @Inject constructor(
    private val repository: LocalFileRepository
) {
    suspend operator fun invoke(uri: String): Boolean = repository.deleteByUri(uri)
}


