package com.psycode.spotiflac.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BackendSpotifyApi {
    @GET("api/v1/spotify/search")
    suspend fun searchPublicTracks(
        @Query("q") q: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): BackendSearchTracksResponse

    @GET("api/v1/spotify/search")
    suspend fun searchPublicTracksLegacy(
        @Query("q") q: String,
    ): BackendSearchTracksResponse

    @GET("api/v1/spotify/tracks/{id}")
    suspend fun getTrackDetailPublic(
        @Path("id") id: String,
    ): BackendTrackDetailDto
}

data class BackendSearchTracksResponse(
    @SerializedName("tracksPage") val tracksPage: BackendSearchTracksPageDto,
)

data class BackendSearchTracksPageDto(
    @SerializedName("href") val href: String,
    @SerializedName("items") val items: List<BackendTrackDto>,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("total") val total: Int,
)

data class BackendTrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artists") val artists: List<BackendArtistDto>,
    @SerializedName("album") val album: BackendAlbumDto,
)

data class BackendArtistDto(
    @SerializedName("name") val name: String,
)

data class BackendAlbumDto(
    @SerializedName("images") val images: List<BackendImageDto>,
)

data class BackendImageDto(
    @SerializedName("url") val url: String,
)

data class BackendTrackDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artists") val artists: List<BackendArtistDto>,
    @SerializedName("album") val album: BackendAlbumDetailDto,
    @SerializedName("duration_ms") val durationMs: Int,
    @SerializedName("popularity") val popularity: Int,
    @SerializedName("preview_url") val previewUrl: String?,
)

data class BackendAlbumDetailDto(
    @SerializedName("name") val name: String,
    @SerializedName("images") val images: List<BackendImageDto>,
)

