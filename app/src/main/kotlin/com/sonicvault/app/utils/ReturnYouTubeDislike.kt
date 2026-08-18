/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Restores public like/dislike counts for YouTube videos via the Return YouTube Dislike API
 * (YouTube removed the public dislike count in 2021; RYD aggregates community vote data).
 */
object ReturnYouTubeDislike {
    private const val API_ENDPOINT = "https://returnyoutubedislike.com/votes?videoId="

    data class Votes(
        val likes: Int,
        val dislikes: Int,
        val rating: Double,
        val viewCount: Int,
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun fetchVotes(videoId: String): Result<Votes> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(API_ENDPOINT + videoId)
                        .header("User-Agent", "SonicVault/1.0")
                        .build()
                val body =
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        response.body?.string()
                    } ?: error("RYD request failed")
                val json = JSONObject(body)
                Votes(
                    likes = json.optInt("likes", 0),
                    dislikes = json.optInt("dislikes", 0),
                    rating = json.optDouble("rating", 0.0),
                    viewCount = json.optInt("viewCount", 0),
                )
            }
        }
}
