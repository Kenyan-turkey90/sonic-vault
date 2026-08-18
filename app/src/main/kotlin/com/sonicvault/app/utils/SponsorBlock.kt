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
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * Fetches skip segments from the SponsorBlock API so sponsored/intro/outro/off-topic parts of
 * a music video can be auto-skipped during playback. Music videos typically only report the
 * "music_offtopic" category (talking segments between songs), which is what we request.
 */
object SponsorBlock {
    private const val API_ENDPOINT = "https://sponsor.ajay.app/api/skipSegments"

    /** The categories Sonic Vault is interested in, ordered by preference. */
    val requestedCategories: List<String> = listOf("music_offtopic", "intro", "outro", "selfpromo")

    data class Segment(
        val startMs: Long,
        val endMs: Long,
        val category: String,
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun fetchSegments(videoId: String): Result<List<Segment>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val categoriesParam =
                    JSONArray(requestedCategories).toString()
                        .replace(",", "%2C")
                        .replace("\"", "%22")
                val url = "$API_ENDPOINT?videoID=$videoId&categories=$categoriesParam"
                val request =
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", "SonicVault/1.0")
                        .build()
                val body =
                    client.newCall(request).execute().use { response ->
                        if (response.code == 404) return@use null // no segments for this video
                        if (!response.isSuccessful) return@use null
                        response.body?.string()
                    } ?: return@runCatching emptyList()

                val array = JSONArray(body)
                buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.optJSONObject(i) ?: continue
                        val segment = obj.optJSONArray("segment") ?: continue
                        if (segment.length() < 2) continue
                        add(
                            Segment(
                                startMs = (segment.optDouble(0) * 1000).toLong(),
                                endMs = (segment.optDouble(1) * 1000).toLong(),
                                category = obj.optString("category", ""),
                            ),
                        )
                    }
                }.sortedBy { it.startMs }
            }
        }
}
