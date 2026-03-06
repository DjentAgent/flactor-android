package com.psycode.spotiflac.data.network

import com.psycode.spotiflac.data.model.CaptchaCompleteRequestDto
import com.psycode.spotiflac.data.model.TorrentResultDto
import com.psycode.spotiflac.data.model.GenericStatusResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming





interface TorrentApi {

    
    @GET("api/v1/torrents/search")
    suspend fun searchTorrents(
        @Query("q") query: String,
        @Query("lossless") lossless: Boolean? = null,
        @Query("track") track: String? = null
    ): List<TorrentResultDto>

    
    @POST("api/v1/torrents/login/complete")
    suspend fun loginComplete(
        @Body body: CaptchaCompleteRequestDto
    ): GenericStatusResponseDto

    
    @GET("api/v1/torrents/download/{topic_id}")
    @Streaming
    suspend fun downloadTorrent(
        @Path("topic_id") topicId: Int
    ): Response<ResponseBody>
}

