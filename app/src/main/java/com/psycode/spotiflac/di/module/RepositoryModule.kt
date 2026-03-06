package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.repository.SpotifyTrackRepository
import com.psycode.spotiflac.data.repository.TorrentRepositoryImpl
import com.psycode.spotiflac.domain.repository.TorrentRepository
import com.psycode.spotiflac.domain.repository.TrackRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        impl: SpotifyTrackRepository
    ): TrackRepository

    @Binds
    @Singleton
    abstract fun bindTorrentRepository(
        impl: TorrentRepositoryImpl
    ): TorrentRepository
}
