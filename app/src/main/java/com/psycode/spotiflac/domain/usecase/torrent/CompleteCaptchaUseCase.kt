package com.psycode.spotiflac.domain.usecase.torrent
import com.psycode.spotiflac.domain.repository.TorrentRepository
import javax.inject.Inject

class CompleteCaptchaUseCase @Inject constructor(
    private val repository: TorrentRepository
) {
    suspend operator fun invoke(sessionId: String, solution: String) {
        repository.completeCaptcha(sessionId, solution)
    }
}



