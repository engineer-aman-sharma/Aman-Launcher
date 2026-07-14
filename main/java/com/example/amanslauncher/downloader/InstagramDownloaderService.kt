package com.example.amanslauncher.downloader

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * RapidAPI operation name is "get-info"; the HTTP route is `/get-info-rapidapi`.
 * Pass the original Instagram post/Reel/photo URL as [url]; Retrofit encodes it once.
 */
interface InstagramDownloaderService {
    @GET("get-info-rapidapi")
    suspend fun fetchMedia(
        @Query("url") url: String,
    ): Response<ResponseBody>
}
