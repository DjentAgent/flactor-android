package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.datastore.DataStoreManualSearchHistoryRepository
import com.psycode.spotiflac.domain.repository.ManualSearchHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ManualSearchHistoryModule {

    @Binds
    @Singleton
    abstract fun bindManualSearchHistoryRepository(
        impl: DataStoreManualSearchHistoryRepository
    ): ManualSearchHistoryRepository
}
