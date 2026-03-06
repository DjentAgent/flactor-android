package com.psycode.spotiflac.domain.mode

object AppModeCodec {
    private const val MODE_SPOTIFY_PUBLIC = "mode:spotify_public"
    private const val MODE_MANUAL_TORRENT = "mode:manual_torrent"

    fun encode(mode: AppMode): String = when (mode) {
        AppMode.Unselected -> ""
        AppMode.SpotifyPublic -> MODE_SPOTIFY_PUBLIC
        AppMode.ManualTorrent -> MODE_MANUAL_TORRENT
    }

    fun decode(raw: String?): AppMode {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return AppMode.Unselected

        return when (value) {
            MODE_SPOTIFY_PUBLIC -> AppMode.SpotifyPublic
            MODE_MANUAL_TORRENT -> AppMode.ManualTorrent
            else -> AppMode.Unselected
        }
    }
}

