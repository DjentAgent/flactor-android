package com.psycode.spotiflac.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    

    @Query("SELECT * FROM download_tasks ORDER BY rowid DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download_tasks ORDER BY rowid DESC")
    suspend fun getAllSnapshot(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Delete
    suspend fun delete(entity: DownloadEntity)

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_tasks")
    suspend fun clearAll()


    
    

    @Query("SELECT * FROM download_tasks WHERE id LIKE :prefix || '%' ORDER BY rowid DESC")
    suspend fun getByTopicPrefix(prefix: String): List<DownloadEntity>

    @Transaction
    suspend fun getByTopicId(topicId: Int): List<DownloadEntity> =
        getByTopicPrefix("${topicId}_")

    @Query("DELETE FROM download_tasks WHERE id LIKE :prefix || '%'")
    suspend fun deleteByTopicPrefix(prefix: String): Int

    @Transaction
    suspend fun deleteByTopicId(topicId: Int): Int =
        deleteByTopicPrefix("${topicId}_")
}

