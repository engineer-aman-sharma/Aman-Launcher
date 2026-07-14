package com.example.amanslauncher.downloader.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.amanslauncher.downloader.ResolvedMediaKind

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalUrl: String,
    val mediaStoreUri: String,
    val mediaType: String,
    val shortcode: String?,
    val caption: String?,
    val thumbnailUrl: String?,
    val downloadedAt: Long,
) {
    fun resolvedKind(): ResolvedMediaKind =
        if (mediaType.equals(ResolvedMediaKind.Video.name, ignoreCase = true)) {
            ResolvedMediaKind.Video
        } else {
            ResolvedMediaKind.Photo
        }
}
