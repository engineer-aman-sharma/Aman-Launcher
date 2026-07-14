package com.example.amanslauncher.ui.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.amanslauncher.downloader.ResolvedMediaKind
import com.example.amanslauncher.downloader.history.DownloadHistoryEntity
import com.example.amanslauncher.downloader.history.isMediaStoreUriAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DownloadHistorySection(
    items: List<DownloadHistoryEntity>,
    onOpenItem: (DownloadHistoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Download History",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                text = "Downloaded photos and videos will appear here",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
            return
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth >= 600.dp -> 4
                maxWidth >= 400.dp -> 3
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.chunked(columns).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEach { item ->
                            HistoryGridItem(
                                item = item,
                                onClick = { onOpenItem(item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryGridItem(
    item: DownloadHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mediaUri = remember(item.mediaStoreUri) { Uri.parse(item.mediaStoreUri) }
    var available by remember(item.id, item.mediaStoreUri) { mutableStateOf(true) }

    LaunchedEffect(item.id, item.mediaStoreUri) {
        available = withContext(Dispatchers.IO) {
            context.isMediaStoreUriAvailable(mediaUri)
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        if (available) {
            when (item.resolvedKind()) {
                ResolvedMediaKind.Photo -> {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mediaUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.caption ?: "Downloaded photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ResolvedMediaKind.Video -> {
                    VideoHistoryThumbnail(
                        mediaUri = mediaUri,
                        fallbackThumbUrl = item.thumbnailUrl,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "▶",
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Unavailable",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Removed from Gallery",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun VideoHistoryThumbnail(
    mediaUri: Uri,
    fallbackThumbUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var frame by remember(mediaUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(mediaUri) {
        frame = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, mediaUri)
                retriever.getFrameAtTime(0L)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    when {
        frame != null -> {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Downloaded video",
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }
        !fallbackThumbUrl.isNullOrBlank() -> {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(fallbackThumbUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Downloaded video",
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
        }
        else -> {
            Box(
                modifier = modifier.background(Color(0xFF1A2433)),
            )
        }
    }
}

fun openHistoryMedia(
    context: android.content.Context,
    item: DownloadHistoryEntity,
    onMissing: () -> Unit,
) {
    val uri = Uri.parse(item.mediaStoreUri)
    if (!context.isMediaStoreUriAvailable(uri)) {
        onMissing()
        Toast.makeText(
            context,
            "This media was deleted from Gallery",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }

    val mimeType = when (item.resolvedKind()) {
        ResolvedMediaKind.Photo -> "image/*"
        ResolvedMediaKind.Video -> "video/*"
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this media", Toast.LENGTH_SHORT).show()
    }
}
