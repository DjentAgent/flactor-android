package com.psycode.spotiflac.data.paging

import android.net.Uri
import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.psycode.spotiflac.data.network.BackendSpotifyApi
import com.psycode.spotiflac.domain.model.Track
import retrofit2.HttpException

class PublicSearchPagingSource(
    private val backendApi: BackendSpotifyApi,
    private val query: String,
    private val pageSize: Int = 20
) : PagingSource<Int, Track>() {
    private companion object {
        const val TAG = "SpotiFlacSearch"
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Track> {
        val offset = params.key ?: 0
        return try {
            val resp = fetchPage(offset)

            val tracks = resp.tracksPage.items.map { dto ->
                Track(
                    id            = dto.id,
                    title         = dto.title,                                   
                    artist        = dto.artists.firstOrNull()?.name.orEmpty(),
                    albumCoverUrl = dto.album.images.firstOrNull()?.url.orEmpty()
                )
            }

            val nextKey = resp.tracksPage.next
                ?.let { Uri.parse(it).getQueryParameter("offset")?.toIntOrNull() }

            LoadResult.Page(
                data    = tracks,
                prevKey = if (offset == 0) null else (offset - pageSize).coerceAtLeast(0),
                nextKey = nextKey
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Public search load failed query=$query offset=$offset error=${e::class.java.simpleName}: ${e.message}"
            )
            LoadResult.Error(e)
        }
    }

    private suspend fun fetchPage(offset: Int) =
        try {
            backendApi.searchPublicTracks(q = query, limit = pageSize, offset = offset)
        } catch (e: HttpException) {
            
            if (offset == 0 && e.code() in 500..504) {
                Log.w(TAG, "Public search fallback to legacy params, code=${e.code()}, query=$query")
                try {
                    backendApi.searchPublicTracksLegacy(q = query)
                } catch (legacyError: Exception) {
                    Log.e(
                        TAG,
                        "Public search legacy call failed query=$query error=${legacyError::class.java.simpleName}: ${legacyError.message}"
                    )
                    throw legacyError
                }
            } else {
                throw e
            }
        }

    override fun getRefreshKey(state: PagingState<Int, Track>): Int? {
        return state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.prevKey?.plus(pageSize)
                ?: state.closestPageToPosition(pos)?.nextKey?.minus(pageSize)
        }
    }

    override fun toString(): String {
        return "PublicSearchPagingSource(query=$query,pageSize=$pageSize)"
    }
}

