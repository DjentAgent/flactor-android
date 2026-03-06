package com.psycode.spotiflac.ui.screen.trackdetail

import com.psycode.spotiflac.domain.model.TrackDetail
import com.psycode.spotiflac.ui.screen.downloads.ManageFilesUiState

internal fun mapTorrentFilesUiStateToManageFilesUiState(
    filesState: TorrentFilesUiState
): ManageFilesUiState = when (filesState) {
    is TorrentFilesUiState.Loading -> ManageFilesUiState.Loading
    is TorrentFilesUiState.Error -> ManageFilesUiState.Error(filesState.message)
    is TorrentFilesUiState.Success -> ManageFilesUiState.Success(filesState.files)
    is TorrentFilesUiState.Hidden -> ManageFilesUiState.Hidden
}

internal fun resolveAutoMatchInputs(
    stickyTrack: TrackDetail?,
    manualMode: Boolean,
    manualQuery: Pair<String, String>?,
    manualArtistInput: String,
    manualTitleInput: String
): Pair<String?, String?> {
    val artist = manualQuery?.first ?: if (!manualMode) stickyTrack?.artist else manualArtistInput
    val title = manualQuery?.second ?: if (!manualMode) stickyTrack?.title else manualTitleInput
    return artist to title
}
