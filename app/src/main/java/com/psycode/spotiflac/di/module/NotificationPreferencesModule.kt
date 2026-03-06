package com.psycode.spotiflac.di.module

import com.psycode.spotiflac.data.preferences.SharedPrefsNotificationPreferencesRepository
import com.psycode.spotiflac.domain.repository.NotificationPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationPreferencesModule {

    @Binds
    @Singleton
    abstract fun bindNotificationPreferencesRepository(
        impl: SharedPrefsNotificationPreferencesRepository
    ): NotificationPreferencesRepository
}

