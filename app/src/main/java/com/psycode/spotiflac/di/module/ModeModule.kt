package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.datastore.DataStoreAppModeRepository
import com.psycode.spotiflac.domain.repository.AppModeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ModeModule {

    @Binds
    @Singleton
    abstract fun bindAppModeRepository(
        impl: DataStoreAppModeRepository
    ): AppModeRepository
}
