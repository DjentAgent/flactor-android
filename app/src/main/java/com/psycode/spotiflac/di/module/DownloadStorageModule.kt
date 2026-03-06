package com.psycode.spotiflac.di.module

import android.content.Context
import androidx.room.Room
import com.psycode.spotiflac.data.local.DownloadDao
import com.psycode.spotiflac.data.local.DownloadDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadStorageModule {

    @Provides @Singleton
    fun provideDb(@ApplicationContext context: Context): DownloadDatabase =
        Room.databaseBuilder(context, DownloadDatabase::class.java, "downloads.db").build()

    @Provides
    fun provideDao(db: DownloadDatabase): DownloadDao = db.downloadDao()
}
