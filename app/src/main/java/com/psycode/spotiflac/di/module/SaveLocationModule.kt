package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.datastore.DataStoreSaveLocationRepository
import com.psycode.spotiflac.domain.repository.SaveLocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SaveLocationModule {

    @Binds
    @Singleton
    abstract fun bindSaveLocationRepository(
        impl: DataStoreSaveLocationRepository
    ): SaveLocationRepository
}

