package com.psycode.spotiflac.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")

    object TrackList : Screen("track_list")

    object TrackDetail : Screen("track_detail/{trackId}") {
        fun createRoute(trackId: String) = "track_detail/$trackId"
    }

    object TrackDetailManual : Screen("track_detail_manual")

    object Downloads : Screen("downloads")

    object Settings : Screen("settings")
}
