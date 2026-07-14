package com.example.amanslauncher.ui.launcher

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.amanslauncher.downloader.InstagramMediaResult
import com.example.amanslauncher.downloader.MediaApiRequestGate
import com.example.amanslauncher.downloader.MediaDownloadException
import com.example.amanslauncher.downloader.MediaDownloadState
import com.example.amanslauncher.downloader.MediaFetchState
import com.example.amanslauncher.downloader.MediaStoreDownloader
import com.example.amanslauncher.downloader.ResolvedMediaKind
import com.example.amanslauncher.downloader.history.DownloadHistoryRepository
import com.example.amanslauncher.downloader.mediaAlbumFolderName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DownloadBlue = Color(0xFF1E88E5)

/** Survives Downloader page recomposition / pager revisits within the process. */
private val sharedMediaFetchGate = MediaApiRequestGate()

/** Last clipboard URL auto-applied; survives leaving/reopening Downloader. Manual Paste is unaffected. */
private var lastAutoProcessedClipboardUrl: String? = null

private data class PendingDownload(
    val sourceUrl: String,
    val result: InstagramMediaResult,
    val kind: ResolvedMediaKind,
)

/**
 * Minimal full-screen Downloader: URL field, API preview, download, and Room history.
 */
@Composable
fun DownloaderPage(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val fetchGate = remember { sharedMediaFetchGate }
    val downloader = remember { MediaStoreDownloader() }
    val historyRepository = remember { DownloadHistoryRepository.getInstance(context) }
    val historyItems by historyRepository.observeHistory().collectAsState(initial = emptyList())

    var url by remember { mutableStateOf("") }
    var fetchState by remember { mutableStateOf<MediaFetchState>(MediaFetchState.Idle) }
    var downloadState by remember { mutableStateOf<MediaDownloadState>(MediaDownloadState.Idle) }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var lastSuccessfulSourceUrl by remember { mutableStateOf("") }
    /** When true, the next [url] LaunchedEffect must not start a fetch (Paste already did). */
    var suppressUrlDrivenFetch by remember { mutableStateOf(false) }

    fun resetDownloaderUiState() {
        fetchGate.resetSession()
        url = ""
        fetchState = MediaFetchState.Idle
        downloadState = MediaDownloadState.Idle
        pendingDownload = null
        lastSuccessfulSourceUrl = ""
        suppressUrlDrivenFetch = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun fetchPastedUrl(pasted: String) {
        val candidate = pasted.trim()
        // Paste owns the API call — do not let TextField/url LaunchedEffect fire another one.
        suppressUrlDrivenFetch = true
        url = candidate
        lastAutoProcessedClipboardUrl = candidate
        if (!candidate.isValidHttpUrl()) {
            fetchState = MediaFetchState.Idle
            return
        }
        downloadState = MediaDownloadState.Idle
        fetchGate.requestFetch(
            scope = scope,
            url = candidate,
            debounceMs = 0L,
            ignoreCache = true,
            onState = { state -> fetchState = state },
        )
    }

    fun startDownload(pending: PendingDownload) {
        if (downloadState is MediaDownloadState.InProgress) return
        scope.launch {
            downloadState = MediaDownloadState.InProgress(percent = 0)
            downloadState = runCatching {
                val savedUri = withContext(Dispatchers.IO) {
                    downloader.download(
                        context = context,
                        downloadUrl = pending.result.downloadUrl,
                        kind = pending.kind,
                        albumName = context.mediaAlbumFolderName(),
                        onProgress = { percent ->
                            scope.launch(Dispatchers.Main.immediate) {
                                if (downloadState is MediaDownloadState.InProgress) {
                                    downloadState = MediaDownloadState.InProgress(percent)
                                }
                            }
                        },
                    )
                }
                historyRepository.addSuccessfulDownload(
                    originalUrl = pending.sourceUrl,
                    mediaStoreUri = savedUri,
                    kind = pending.kind,
                    result = pending.result,
                )
                MediaDownloadState.Success(savedUri)
            }.getOrElse { error ->
                MediaDownloadState.Error(
                    message = when (error) {
                        is MediaDownloadException -> error.message ?: "Download failed"
                        else -> error.message?.takeIf { it.isNotBlank() } ?: "Download failed"
                    },
                )
            }
        }
    }

    fun requestDownload(pending: PendingDownload) {
        if (downloadState is MediaDownloadState.InProgress) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startDownload(pending)
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startDownload(pending)
        } else {
            pendingDownload = pending
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingDownload
        pendingDownload = null
        if (granted && pending != null) {
            startDownload(pending)
        } else if (!granted) {
            downloadState = MediaDownloadState.Error(
                message = "Storage permission is required to save media on this Android version",
            )
        }
    }

    LaunchedEffect(pendingDownload) {
        val pending = pendingDownload ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            pendingDownload = null
            startDownload(pending)
        }
    }

    // Leave Downloader (swipe/back): full UI reset; history is Room-backed and untouched.
    // Become active: clipboard auto-fill at most once per distinct URL.
    LaunchedEffect(isActive) {
        if (!isActive) {
            resetDownloaderUiState()
            return@LaunchedEffect
        }
        val clipboardUrl = readClipboardUrl(context) ?: return@LaunchedEffect
        if (clipboardUrl == lastAutoProcessedClipboardUrl) return@LaunchedEffect
        lastAutoProcessedClipboardUrl = clipboardUrl
        if (clipboardUrl == url.trim()) return@LaunchedEffect
        url = clipboardUrl
    }

    // Typed / auto-filled URL fetch pipeline (not used for Paste — Paste calls the gate directly).
    LaunchedEffect(url) {
        if (suppressUrlDrivenFetch) {
            suppressUrlDrivenFetch = false
            return@LaunchedEffect
        }
        val candidate = url.trim()
        if (!candidate.isValidHttpUrl()) {
            // Keep Success preview after clearing the field post-fetch.
            if (candidate.isEmpty() && fetchState !is MediaFetchState.Success) {
                fetchState = MediaFetchState.Idle
                downloadState = MediaDownloadState.Idle
            }
            return@LaunchedEffect
        }

        fetchGate.cachedSuccessFor(candidate)?.let { cached ->
            fetchState = cached
            return@LaunchedEffect
        }

        downloadState = MediaDownloadState.Idle
        fetchGate.requestFetch(
            scope = this,
            url = candidate,
            onState = { state ->
                fetchState = state
            },
        )
    }

    // After a successful fetch only: clear the URL field and dismiss the keyboard.
    LaunchedEffect(fetchState) {
        if (fetchState !is MediaFetchState.Success) return@LaunchedEffect
        val source = url.trim()
        if (source.isNotEmpty()) {
            lastSuccessfulSourceUrl = source
            url = ""
        }
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E14),
                        Color(0xFF121820),
                        Color(0xFF0A0E14),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text(
                text = "Download Here",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(36.dp))
            UrlInputRow(
                url = url,
                onUrlChange = { url = it },
                onPaste = {
                    val pasted = readClipboardText(context)?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: return@UrlInputRow
                    fetchPastedUrl(pasted)
                },
            )
            Spacer(modifier = Modifier.height(24.dp))
            when (val state = fetchState) {
                MediaFetchState.Idle -> Unit
                MediaFetchState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Fetching media…",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
                is MediaFetchState.Error -> {
                    Text(
                        text = state.message,
                        color = Color(0xFFFF8A80),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is MediaFetchState.Success -> {
                    MediaPreview(
                        mediaUrl = state.result.downloadUrl,
                        kind = state.kind,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DownloadSection(
                        downloadState = downloadState,
                        onDownloadClick = {
                            requestDownload(
                                PendingDownload(
                                    sourceUrl = lastSuccessfulSourceUrl.ifBlank { url.trim() },
                                    result = state.result,
                                    kind = state.kind,
                                ),
                            )
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            DownloadHistorySection(
                items = historyItems,
                onOpenItem = { item ->
                    openHistoryMedia(
                        context = context,
                        item = item,
                        onMissing = {
                            scope.launch {
                                historyRepository.remove(item.id)
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DownloadSection(
    downloadState: MediaDownloadState,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloading = downloadState is MediaDownloadState.InProgress

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onDownloadClick,
            enabled = !downloading,
            colors = ButtonDefaults.buttonColors(
                containerColor = DownloadBlue,
                contentColor = Color.White,
                disabledContainerColor = DownloadBlue.copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.8f),
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (downloading) "Downloading…" else "Download",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        when (val state = downloadState) {
            MediaDownloadState.Idle -> Unit
            is MediaDownloadState.InProgress -> {
                Spacer(modifier = Modifier.height(12.dp))
                if (state.percent != null) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = DownloadBlue,
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.percent}%",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = DownloadBlue,
                        trackColor = Color.White.copy(alpha = 0.15f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Downloading…",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                    )
                }
            }
            is MediaDownloadState.Success -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Saved to Gallery",
                    color = Color(0xFF81C784),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is MediaDownloadState.Error -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.message,
                    color = Color(0xFFFF8A80),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UrlInputRow(
    url: String,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Paste or enter a URL",
                    color = Color.White.copy(alpha = 0.4f),
                )
            },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.45f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            ),
        )
        TextButton(onClick = onPaste) {
            Text(
                text = "Paste",
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun readClipboardUrl(context: Context): String? {
    val text = readClipboardText(context)?.trim().orEmpty()
    if (text.isEmpty()) return null
    return text.takeIf { it.isValidHttpUrl() }
}

private fun readClipboardText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    if (!clipboard.hasPrimaryClip()) return null
    return clipboard.primaryClip
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}

private fun String.isValidHttpUrl(): Boolean {
    val candidate = trim()
    if (candidate.isEmpty()) return false
    if (!Patterns.WEB_URL.matcher(candidate).matches()) return false
    val uri = Uri.parse(candidate)
    val scheme = uri.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
}
