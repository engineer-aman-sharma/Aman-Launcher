package com.example.amanslauncher.downloader.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DownloadHistoryDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile
        private var instance: DownloadHistoryDatabase? = null

        fun getInstance(context: Context): DownloadHistoryDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadHistoryDatabase::class.java,
                    "download_history.db",
                ).build().also { instance = it }
            }
        }
    }
}
