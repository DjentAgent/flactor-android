package com.psycode.spotiflac.domain.usecase.torrent
import com.psycode.spotiflac.domain.model.TorrentResult
import com.psycode.spotiflac.domain.repository.TorrentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTorrentsForTrackUseCase @Inject constructor(
    private val repo: TorrentRepository
) {
    operator fun invoke(q: String, track: String, lossless: Boolean = true): Flow<List<TorrentResult>> =
        repo.searchTorrents(q, track, lossless)
}



