package com.example.amanslauncher.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

sealed interface MediaDownloadState {
    data object Idle : MediaDownloadState
    data class InProgress(val percent: Int?) : MediaDownloadState
    data class Success(val savedUri: Uri) : MediaDownloadState
    data class Error(val message: String) : MediaDownloadState
}

data class ResolvedMediaFileInfo(
    val mimeType: String,
    val extension: String,
)

class MediaStoreDownloader(
    private val client: OkHttpClient = defaultClient,
) {
    suspend fun download(
        context: Context,
        downloadUrl: String,
        kind: ResolvedMediaKind,
        albumName: String,
        onProgress: (Int?) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(downloadUrl)
            .get()
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw MediaDownloadException("Download failed (${response.code})")
            }
            val body = response.body ?: throw MediaDownloadException("Empty download body")
            val fileInfo = resolveFileInfo(
                kind = kind,
                mediaUrl = downloadUrl,
                contentType = response.header("Content-Type"),
            )
            val displayName = buildFileName(kind, fileInfo.extension)
            val totalBytes = body.contentLength().takeIf { it > 0L }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(
                    context = context,
                    kind = kind,
                    albumName = albumName,
                    displayName = displayName,
                    mimeType = fileInfo.mimeType,
                    totalBytes = totalBytes,
                    body = body,
                    onProgress = onProgress,
                )
            } else {
                writeViaLegacyPublicDir(
                    context = context,
                    kind = kind,
                    albumName = albumName,
                    displayName = displayName,
                    mimeType = fileInfo.mimeType,
                    totalBytes = totalBytes,
                    body = body,
                    onProgress = onProgress,
                )
            }
        }
    }

    private suspend fun writeViaMediaStore(
        context: Context,
        kind: ResolvedMediaKind,
        albumName: String,
        displayName: String,
        mimeType: String,
        totalBytes: Long?,
        body: ResponseBody,
        onProgress: (Int?) -> Unit,
    ): Uri {
        val collection = when (kind) {
            ResolvedMediaKind.Photo ->
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            ResolvedMediaKind.Video ->
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = when (kind) {
            ResolvedMediaKind.Photo -> "${Environment.DIRECTORY_PICTURES}/$albumName"
            ResolvedMediaKind.Video -> "${Environment.DIRECTORY_MOVIES}/$albumName"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collection, values)
            ?: throw MediaDownloadException("Unable to create MediaStore entry")

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                copyWithProgress(
                    input = body.byteStream(),
                    output = output,
                    totalBytes = totalBytes,
                    onProgress = onProgress,
                )
            } ?: throw MediaDownloadException("Unable to open output stream")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            return itemUri
        } catch (error: Exception) {
            resolver.delete(itemUri, null, null)
            throw error
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun writeViaLegacyPublicDir(
        context: Context,
        kind: ResolvedMediaKind,
        albumName: String,
        displayName: String,
        mimeType: String,
        totalBytes: Long?,
        body: ResponseBody,
        onProgress: (Int?) -> Unit,
    ): Uri {
        val baseDir = when (kind) {
            ResolvedMediaKind.Photo ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            ResolvedMediaKind.Video ->
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        }
        val albumDir = File(baseDir, albumName).apply {
            if (!exists() && !mkdirs()) {
                throw MediaDownloadException("Unable to create album folder")
            }
        }
        val targetFile = File(albumDir, displayName)
        FileOutputStream(targetFile).use { output ->
            copyWithProgress(
                input = body.byteStream(),
                output = output,
                totalBytes = totalBytes,
                onProgress = onProgress,
            )
        }

        val collection = when (kind) {
            ResolvedMediaKind.Photo -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            ResolvedMediaKind.Video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
            put(MediaStore.MediaColumns.SIZE, targetFile.length())
            when (kind) {
                ResolvedMediaKind.Photo ->
                    put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                ResolvedMediaKind.Video ->
                    put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            }
        }
        return context.contentResolver.insert(collection, values)
            ?: Uri.fromFile(targetFile)
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        totalBytes: Long?,
        onProgress: (Int?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var downloaded = 0L
        var lastEmitted = -1
        onProgress(if (totalBytes != null) 0 else null)

        while (true) {
            coroutineContext.ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            if (totalBytes != null && totalBytes > 0L) {
                val percent = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                if (percent != lastEmitted) {
                    lastEmitted = percent
                    onProgress(percent)
                }
            } else {
                onProgress(null)
            }
        }
        output.flush()
        if (totalBytes != null) onProgress(100)
    }

    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

fun resolveFileInfo(
    kind: ResolvedMediaKind,
    mediaUrl: String,
    contentType: String?,
): ResolvedMediaFileInfo {
    val normalizedType = contentType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.isNotBlank() && it != "application/octet-stream" }

    val urlExtension = mediaUrl
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }

    val mimeFromExtension = urlExtension
        ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)?.lowercase() }

    val mimeType = when {
        normalizedType != null && (
            normalizedType.startsWith("image/") || normalizedType.startsWith("video/")
            ) -> normalizedType
        mimeFromExtension != null -> mimeFromExtension
        kind == ResolvedMediaKind.Video -> "video/mp4"
        else -> "image/jpeg"
    }

    val extension = when {
        urlExtension != null && mimeMatchesKind(mimeFromExtension, kind) -> urlExtension
        else -> MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.lowercase()
            ?: defaultExtension(kind)
    }

    return ResolvedMediaFileInfo(mimeType = mimeType, extension = extension)
}

private fun mimeMatchesKind(mime: String?, kind: ResolvedMediaKind): Boolean {
    if (mime == null) return false
    return when (kind) {
        ResolvedMediaKind.Photo -> mime.startsWith("image/")
        ResolvedMediaKind.Video -> mime.startsWith("video/")
    }
}

private fun defaultExtension(kind: ResolvedMediaKind): String = when (kind) {
    ResolvedMediaKind.Photo -> "jpg"
    ResolvedMediaKind.Video -> "mp4"
}

private fun buildFileName(kind: ResolvedMediaKind, extension: String): String {
    val prefix = when (kind) {
        ResolvedMediaKind.Photo -> "IMG"
        ResolvedMediaKind.Video -> "VID"
    }
    return "${prefix}_${System.currentTimeMillis()}.$extension"
}

class MediaDownloadException(message: String) : Exception(message)

fun Context.mediaAlbumFolderName(): String {
    val label = runCatching {
        packageManager.getApplicationLabel(applicationInfo).toString()
    }.getOrDefault("AmansLauncher")
    return label.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "AmansLauncher" }
}
