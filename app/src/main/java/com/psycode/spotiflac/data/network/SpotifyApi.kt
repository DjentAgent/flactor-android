package com.psycode.spotiflac.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SpotifyApi {
    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SavedTracksResponse

    @GET("v1/me/tracks/{id}")
    suspend fun getTrackById(
        @Path("id") id: String,
        @Header("Authorization") bearer: String,
    ): SavedTrackItem

    @GET("v1/search")
    suspend fun searchTracks(
        @Header("Authorization") bearer: String,
        @Query("q") query: String,
        @Query("type") type: String = "track",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): SearchTracksResponse

    @GET("v1/tracks/{id}")
    suspend fun getTrackDetail(
        @Path("id") id: String,
        @Header("Authorization") bearer: String,
    ): TrackDetailDto
}

data class TrackDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val title: String,
    @SerializedName("artists") val artists: List<ArtistDto>,
    @SerializedName("album") val album: AlbumDetailDto,
    @SerializedName("duration_ms") val durationMs: Int,
    @SerializedName("popularity") val popularity: Int,
    @SerializedName("preview_url") val previewUrl: String?,
)

data class AlbumDetailDto(
    @SerializedName("name") val name: String,
    @SerializedName("images") val images: List<ImageDto>,
)

data class SearchTracksResponse(
    @SerializedName("tracks") val tracksPage: SearchTracksPageDto,
)

data class SearchTracksPageDto(
    @SerializedName("href") val href: String,
    @SerializedName("items") val items: List<TrackDto>,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("total") val total: Int,
)

data class SavedTracksResponse(
    @SerializedName("href") val href: String,
    @SerializedName("items") val items: List<SavedTrackItem>,
    @SerializedName("limit") val limit: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("offset") val offset: Int,
    @SerializedName("total") val total: Int,
)

data class SavedTrackItem(
    @SerializedName("track") val track: TrackDto,
)

data class TrackDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val title: String,
    @SerializedName("artists") val artists: List<ArtistDto>,
    @SerializedName("album") val album: AlbumDto,
)

data class ArtistDto(
    @SerializedName("name") val name: String,
)

data class AlbumDto(
    @SerializedName("images") val images: List<ImageDto>,
)

data class ImageDto(
    @SerializedName("url") val url: String,
)

