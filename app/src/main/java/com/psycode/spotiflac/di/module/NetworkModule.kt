package com.psycode.spotiflac.di.module

import android.util.Log
import com.google.gson.Gson
import com.psycode.spotiflac.BuildConfig
import com.psycode.spotiflac.data.network.BackendSpotifyApi
import com.psycode.spotiflac.data.network.NetworkConfig
import com.psycode.spotiflac.data.network.SpotifyApi
import com.psycode.spotiflac.data.network.TorrentApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val TAG = "SpotiFlacNetwork"

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig {
        val config = NetworkConfig(
            spotifyApiBaseUrl = BuildConfig.SPOTIFY_API_BASE_URL.ensureTrailingSlash(),
            backendBaseUrl = BuildConfig.BACKEND_BASE_URL.ensureTrailingSlash()
        )
        Log.d(
            TAG,
            "NetworkConfig spotifyBase=${config.spotifyApiBaseUrl} backendBase=${config.backendBaseUrl}"
        )
        return config
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @Named("spotifyRetrofit")
    fun provideSpotifyRetrofit(
        client: OkHttpClient,
        gson: Gson,
        config: NetworkConfig
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.spotifyApiBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("torrentRetrofit")
    fun provideTorrentRetrofit(
        client: OkHttpClient,
        gson: Gson,
        config: NetworkConfig
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.backendBaseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideSpotifyApi(
        @Named("spotifyRetrofit") retrofit: Retrofit
    ): SpotifyApi = retrofit.create(SpotifyApi::class.java)

    @Provides
    @Singleton
    fun provideTorrentApi(
        @Named("torrentRetrofit") retrofit: Retrofit
    ): TorrentApi = retrofit.create(TorrentApi::class.java)

    @Provides
    @Singleton
    fun provideBackendSpotifyApi(
        @Named("torrentRetrofit") retrofit: Retrofit
    ): BackendSpotifyApi = retrofit.create(BackendSpotifyApi::class.java)
}

private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"
