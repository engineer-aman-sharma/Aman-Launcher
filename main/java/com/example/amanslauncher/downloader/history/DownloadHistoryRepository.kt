package com.example.amanslauncher.downloader.history

import android.content.Context
import android.net.Uri
import com.example.amanslauncher.downloader.InstagramMediaResult
import com.example.amanslauncher.downloader.ResolvedMediaKind
import kotlinx.coroutines.flow.Flow

class DownloadHistoryRepository(
    private val dao: DownloadHistoryDao,
) {
    fun observeHistory(): Flow<List<DownloadHistoryEntity>> = dao.observeAll()

    suspend fun addSuccessfulDownload(
        originalUrl: String,
        mediaStoreUri: Uri,
        kind: ResolvedMediaKind,
        result: InstagramMediaResult,
    ) {
        dao.insert(
            DownloadHistoryEntity(
                originalUrl = originalUrl,
                mediaStoreUri = mediaStoreUri.toString(),
                mediaType = kind.name,
                shortcode = result.shortcode,
                caption = result.caption,
                thumbnailUrl = result.thumb,
                downloadedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun remove(id: Long) {
        dao.deleteById(id)
    }

    companion object {
        @Volatile
        private var instance: DownloadHistoryRepository? = null

        fun getInstance(context: Context): DownloadHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: DownloadHistoryRepository(
                    DownloadHistoryDatabase.getInstance(context).downloadHistoryDao(),
                ).also { instance = it }
            }
        }
    }
}

fun Context.isMediaStoreUriAvailable(uri: Uri): Boolean {
    return runCatching {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}
