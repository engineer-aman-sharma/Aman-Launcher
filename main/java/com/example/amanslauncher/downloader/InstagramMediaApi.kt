package com.example.amanslauncher.downloader

import android.util.Log
import com.example.amanslauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class InstagramMediaApi(
    private val service: InstagramDownloaderService = defaultService,
) {
    suspend fun fetchMedia(userUrl: String): InstagramMediaResult = withContext(Dispatchers.IO) {
        // Pass the original URL once; Retrofit @Query("url") encodes it (do not pre-encode).
        val originalUrl = userUrl.trim()
        if (BuildConfig.DEBUG) {
            Log.d(DEBUG_TAG, "API_REQUEST url=$originalUrl")
        }
        val response = service.fetchMedia(url = originalUrl)

        val requestUrl = response.raw().request.url.toString()
        val statusCode = response.code()
        val contentType = response.headers()["Content-Type"]
            ?: response.raw().header("Content-Type")
            ?: "unknown"
        val rawBody = when {
            response.isSuccessful -> response.body()?.string().orEmpty()
            else -> response.errorBody()?.string().orEmpty()
        }

        logDebugRawResponse(
            requestUrl = requestUrl,
            statusCode = statusCode,
            contentType = contentType,
            body = rawBody,
        )

        if (!response.isSuccessful) {
            val apiError = InstagramMediaResponseParser.parseErrorMessage(rawBody)
                ?: rawBody.trim().takeIf { it.isNotEmpty() }?.take(500)
                ?: "Request failed ($statusCode)"
            throw MediaApiException("HTTP $statusCode: $apiError")
        }

        try {
            InstagramMediaResponseParser.parse(rawBody)
        } catch (error: MediaApiException) {
            throw error
        } catch (error: Exception) {
            throw MediaApiException(
                error.message?.takeIf { it.isNotBlank() } ?: "Failed to parse API response",
            )
        }
    }

    private fun logDebugRawResponse(
        requestUrl: String,
        statusCode: Int,
        contentType: String,
        body: String,
    ) {
        if (!BuildConfig.DEBUG) return
        val header =
            "Instagram API raw response BEFORE parse. " +
                "url=$requestUrl status=$statusCode contentType=$contentType bodyLength=${body.length}"
        Log.d(DEBUG_TAG, header)
        // Logcat truncates ~4KB; chunk so the complete body is inspectable.
        if (body.isEmpty()) {
            Log.d(DEBUG_TAG, "body=<empty>")
            return
        }
        var offset = 0
        var part = 0
        while (offset < body.length) {
            val end = minOf(offset + LOG_CHUNK_SIZE, body.length)
            Log.d(DEBUG_TAG, "body[$part]: ${body.substring(offset, end)}")
            offset = end
            part++
        }
    }

    companion object {
        private const val DEBUG_TAG = "InstagramMediaApi"
        private const val LOG_CHUNK_SIZE = 3500

        private const val BASE_URL =
            "https://instagram-downloader-download-instagram-videos-stories1.p.rapidapi.com/"
        private const val RAPID_API_HOST =
            "instagram-downloader-download-instagram-videos-stories1.p.rapidapi.com"
        private const val RAPID_API_KEY = "hidden"

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Content-Type", "application/json")
                    .header("x-rapidapi-host", RAPID_API_HOST)
                    .header("x-rapidapi-key", RAPID_API_KEY)
                    .build()
                chain.proceed(request)
            }
            .build()

        private val defaultService: InstagramDownloaderService =
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(defaultClient)
                .build()
                .create(InstagramDownloaderService::class.java)
    }
}
