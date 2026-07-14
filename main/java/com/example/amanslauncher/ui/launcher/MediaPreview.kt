package com.example.amanslauncher.ui.launcher

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.amanslauncher.downloader.ResolvedMediaKind

@Composable
fun MediaPreview(
    mediaUrl: String,
    kind: ResolvedMediaKind,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        when (kind) {
            ResolvedMediaKind.Photo -> PhotoPreview(mediaUrl = mediaUrl)
            ResolvedMediaKind.Video -> VideoPreview(mediaUrl = mediaUrl)
        }
    }
}

@Composable
private fun PhotoPreview(
    mediaUrl: String,
    modifier: Modifier = Modifier,
) {
    val context: Context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(mediaUrl)
            .crossfade(true)
            .build(),
        contentDescription = "Media preview",
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .heightIn(max = 520.dp),
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPreview(
    mediaUrl: String,
    modifier: Modifier = Modifier,
) {
    val context: Context = LocalContext.current
    var aspectRatio: Float by remember(mediaUrl) { mutableFloatStateOf(16f / 9f) }

    val exoPlayer: ExoPlayer = remember(mediaUrl) {
        val player: ExoPlayer = ExoPlayer.Builder(context).build()
        val mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(mediaUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = false
        player
    }

    DisposableEffect(exoPlayer) {
        val listener: Player.Listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    aspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx: Context ->
            val playerView = PlayerView(ctx)
            playerView.player = exoPlayer
            playerView.useController = true
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            playerView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            playerView
        },
        update = { playerView: PlayerView ->
            playerView.player = exoPlayer
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .aspectRatio(aspectRatio),
    )
}
