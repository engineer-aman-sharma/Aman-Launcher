package com.example.amanslauncher.downloader

data class InstagramMediaResult(
    val downloadUrl: String,
    val thumb: String?,
    val type: String?,
    val caption: String?,
    val shortcode: String?,
    val hosting: String?,
    val audio: String?,
)

sealed interface MediaFetchState {
    data object Idle : MediaFetchState
    data object Loading : MediaFetchState
    data class Success(
        val result: InstagramMediaResult,
        val kind: ResolvedMediaKind,
    ) : MediaFetchState

    data class Error(val message: String) : MediaFetchState
}

enum class ResolvedMediaKind {
    Photo,
    Video,
}
