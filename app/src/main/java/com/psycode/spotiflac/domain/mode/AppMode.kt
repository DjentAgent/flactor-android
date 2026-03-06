package com.psycode.spotiflac.domain.mode

sealed interface AppMode {
    data object Unselected : AppMode
    data object SpotifyPublic : AppMode
    data object ManualTorrent : AppMode
}
