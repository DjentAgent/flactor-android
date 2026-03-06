package com.psycode.spotiflac.ui.screen.tracklist

internal enum class TrackListBackAction {
    CLOSE_SEARCH,
    CLEAR_QUERY,
    NAVIGATE_BACK
}

internal fun resolveTrackListBackAction(
    searchActive: Boolean,
    query: String
): TrackListBackAction = when {
    searchActive -> TrackListBackAction.CLOSE_SEARCH
    query.isNotBlank() -> TrackListBackAction.CLEAR_QUERY
    else -> TrackListBackAction.NAVIGATE_BACK
}
