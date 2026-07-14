package com.example.amanslauncher.downloader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ensures at most one media API call per unique URL.
 * Debounces typed input, cancels in-flight work when the URL changes, and caches successes.
 * Pass [debounceMs] = 0 for immediate Paste-triggered fetches.
 */
class MediaApiRequestGate(
    private val api: InstagramMediaApi = InstagramMediaApi(),
) {
    private val mutex = Mutex()
    private var fetchJob: Job? = null

    @Volatile
    private var isRequestInProgress: Boolean = false

    @Volatile
    private var inFlightUrl: String? = null

    @Volatile
    private var lastSuccessfulUrl: String? = null

    @Volatile
    private var lastSuccessfulState: MediaFetchState.Success? = null

    fun cachedSuccessFor(url: String): MediaFetchState.Success? {
        val normalized = url.trim()
        return if (normalized == lastSuccessfulUrl) lastSuccessfulState else null
    }

    fun isInProgress(): Boolean = isRequestInProgress

    /**
     * Schedules a fetch for [url]. No-ops for in-flight duplicates.
     * When [ignoreCache] is false, cached successes are returned without a new network call.
     */
    fun requestFetch(
        scope: CoroutineScope,
        url: String,
        debounceMs: Long = DEFAULT_DEBOUNCE_MS,
        ignoreCache: Boolean = false,
        onState: (MediaFetchState) -> Unit,
    ) {
        val candidate = url.trim()
        if (candidate.isEmpty()) {
            onState(MediaFetchState.Idle)
            return
        }

        if (!ignoreCache) {
            cachedSuccessFor(candidate)?.let { cached ->
                onState(cached)
                return
            }
        }

        // Same URL already fetching — keep the existing single in-flight call.
        if (isRequestInProgress && inFlightUrl == candidate) {
            return
        }

        fetchJob?.cancel()
        fetchJob = scope.launch {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }

            if (!ignoreCache) {
                cachedSuccessFor(candidate)?.let { cached ->
                    onState(cached)
                    return@launch
                }
            }

            val shouldFetch = mutex.withLock {
                if (isRequestInProgress && inFlightUrl == candidate) {
                    false
                } else {
                    isRequestInProgress = true
                    inFlightUrl = candidate
                    true
                }
            }
            if (!shouldFetch) return@launch

            onState(MediaFetchState.Loading)
            try {
                val result = api.fetchMedia(candidate)
                val kind = MediaTypeResolver.resolve(result.type, result.downloadUrl)
                val success = MediaFetchState.Success(result = result, kind = kind)
                lastSuccessfulUrl = candidate
                lastSuccessfulState = success
                onState(success)
            } catch (error: Exception) {
                onState(
                    MediaFetchState.Error(
                        message = when (error) {
                            is MediaApiException -> error.message ?: "Failed to fetch media"
                            else -> error.message?.takeIf { it.isNotBlank() }
                                ?: "Failed to fetch media"
                        },
                    ),
                )
            } finally {
                mutex.withLock {
                    if (inFlightUrl == candidate) {
                        isRequestInProgress = false
                        inFlightUrl = null
                    }
                }
            }
        }
    }

    fun cancel() {
        fetchJob?.cancel()
        fetchJob = null
        isRequestInProgress = false
        inFlightUrl = null
    }

    /** Cancels in-flight work and clears cached success so the Downloader session is fresh. */
    fun resetSession() {
        cancel()
        lastSuccessfulUrl = null
        lastSuccessfulState = null
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MS = 650L
    }
}
