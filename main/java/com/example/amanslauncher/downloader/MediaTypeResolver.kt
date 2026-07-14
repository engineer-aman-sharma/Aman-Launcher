package com.example.amanslauncher.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// Downloader platform by Aman Sharma
private val headClient: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(true)
    .followSslRedirects(true)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()


object MediaTypeResolver {

    suspend fun resolve(
        apiType: String?,
        mediaUrl: String,
    ): ResolvedMediaKind = withContext(Dispatchers.IO) {
        // Prefer real media signals; API type is only a fallback when unreliable.
        hintFromContentType(mediaUrl)
            ?: hintFromUrl(mediaUrl)
            ?: hintFromApiType(apiType)
            ?: ResolvedMediaKind.Photo
    }

    private fun hintFromApiType(apiType: String?): ResolvedMediaKind? {
        val type = apiType?.lowercase()?.trim().orEmpty()
        if (type.isEmpty()) return null
        return when {
            type.contains("video") ||
                type.contains("reel") ||
                type.contains("clip") ||
                type.contains("igtv") ||
                type == "mp4" -> ResolvedMediaKind.Video

            type.contains("image") ||
                type.contains("photo") ||
                type.contains("picture") ||
                type.contains("jpg") ||
                type.contains("jpeg") ||
                type.contains("png") ||
                type.contains("webp") ||
                type == "graphimage" -> ResolvedMediaKind.Photo

            else -> null
        }
    }

    private fun hintFromContentType(mediaUrl: String): ResolvedMediaKind? {
        return runCatching {
            val headRequest = Request.Builder()
                .url(mediaUrl)
                .head()
                .header("User-Agent", "Mozilla/5.0")
                .build()

            headClient.newCall(headRequest).execute().use { response ->
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()

                when {
                    contentType.startsWith("video/") -> ResolvedMediaKind.Video
                    contentType.startsWith("image/") -> ResolvedMediaKind.Photo
                    else -> {
                        // Some hosts reject HEAD; try a ranged GET for Content-Type only.
                        if (!response.isSuccessful) {
                            hintFromRangedGet(mediaUrl)
                        } else {
                            null
                        }
                    }
                }
            }
        }.getOrElse {
            hintFromRangedGet(mediaUrl)
        }
    }

    private fun hintFromRangedGet(mediaUrl: String): ResolvedMediaKind? {
        return runCatching {
            val request = Request.Builder()
                .url(mediaUrl)
                .get()
                .header("Range", "bytes=0-0")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            headClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.lowercase()
                    .orEmpty()
                when {
                    contentType.startsWith("video/") -> ResolvedMediaKind.Video
                    contentType.startsWith("image/") -> ResolvedMediaKind.Photo
                    else -> null
                }
            }
        }.getOrNull()
    }

    private fun hintFromUrl(mediaUrl: String): ResolvedMediaKind? {
        val path = mediaUrl.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".mp4") ||
                path.endsWith(".webm") ||
                path.endsWith(".mkv") ||
                path.endsWith(".mov") ||
                path.endsWith(".m4v") ||
                path.endsWith(".m3u8") -> ResolvedMediaKind.Video

            path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".png") ||
                path.endsWith(".webp") ||
                path.endsWith(".gif") ||
                path.endsWith(".heic") ||
                path.endsWith(".bmp") -> ResolvedMediaKind.Photo

            else -> null
        }
    }
}
