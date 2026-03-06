package com.psycode.spotiflac.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.psycode.spotiflac.domain.model.DownloadStatus

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}

class Converters {
    @androidx.room.TypeConverter
    fun fromStatus(s: DownloadStatus): String = s.name
    @androidx.room.TypeConverter
    fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}
