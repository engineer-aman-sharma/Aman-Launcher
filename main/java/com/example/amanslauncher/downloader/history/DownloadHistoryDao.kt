package com.example.amanslauncher.downloader.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadHistoryEntity): Long

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
