package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.repository.DownloadRepositoryImpl
import com.psycode.spotiflac.data.repository.LocalFileRepositoryImpl
import com.psycode.spotiflac.domain.repository.DownloadRepository
import com.psycode.spotiflac.domain.repository.LocalFileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadBindsModule {
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindLocalFileRepository(impl: LocalFileRepositoryImpl): LocalFileRepository
}
