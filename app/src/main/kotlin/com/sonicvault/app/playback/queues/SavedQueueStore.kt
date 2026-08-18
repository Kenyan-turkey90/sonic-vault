/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.playback.queues

import android.content.Context
import com.sonicvault.app.models.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Persists named snapshots of the current playback queue as JSON files in internal storage.
 * Only the [MediaMetadata] of each item is stored (the same object that rides along in each
 * MediaItem's tag), so a snapshot can be restored into a fresh [ListQueue] at any time —
 * even across app restarts.
 */
object SavedQueueStore {
    private const val DIR_NAME = "saved_queues"
    private const val MAX_NAME_LENGTH = 60

    data class SavedQueueSummary(
        val name: String,
        val title: String,
        val itemCount: Int,
        val savedAtMs: Long,
    )

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun safeFileName(name: String): String {
        val sanitized =
            name.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9._-]"), "_")
                .take(MAX_NAME_LENGTH)
        return if (sanitized.isBlank()) "queue" else sanitized
    }

    fun saveQueue(
        context: Context,
        name: String,
        items: List<MediaMetadata>,
        queueTitle: String?,
    ): Boolean {
        if (items.isEmpty()) return false
        val fileName = safeFileName(name)
        val payload =
            JSONObject().apply {
                put("name", name)
                put("title", queueTitle ?: "")
                put("savedAtMs", System.currentTimeMillis())
                put(
                    "items",
                    JSONArray().apply {
                        items.forEach { item -> put(item.toJson()) }
                    },
                )
            }
        return runCatching {
            File(dir(context), "$fileName.json").writeText(payload.toString())
        }.isSuccess
    }

    fun listQueues(context: Context): List<SavedQueueSummary> =
        runCatching {
            dir(context).listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { file ->
                    runCatching {
                        val json = JSONObject(file.readText())
                        SavedQueueSummary(
                            name = json.optString("name", file.nameWithoutExtension),
                            title = json.optString("title", ""),
                            itemCount = json.optJSONArray("items")?.length() ?: 0,
                            savedAtMs = json.optLong("savedAtMs", file.lastModified()),
                        )
                    }.getOrNull()
                }
                .sortedByDescending { it.savedAtMs }
        }.getOrDefault(emptyList())

    fun loadQueue(
        context: Context,
        name: String,
    ): List<MediaMetadata> =
        runCatching {
            val file = File(dir(context), "${safeFileName(name)}.json")
            if (!file.exists()) return@runCatching emptyList()
            val json = JSONObject(file.readText())
            val items = json.optJSONArray("items") ?: return@runCatching emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    items.optJSONObject(i)?.toMediaMetadata()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())

    fun deleteQueue(
        context: Context,
        name: String,
    ) {
        runCatching {
            File(dir(context), "${safeFileName(name)}.json").delete()
        }
    }

    private fun MediaMetadata.toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("duration", duration)
            put("thumbnailUrl", thumbnailUrl ?: JSONObject.NULL)
            put("explicit", explicit)
            put("isMusicVideo", isMusicVideo)
            put(
                "artists",
                JSONArray().apply {
                    artists.forEach { artist ->
                        put(
                            JSONObject().apply {
                                artist.id?.let { put("id", it) }
                                put("name", artist.name)
                                artist.thumbnailUrl?.let { put("thumbnailUrl", it) }
                            },
                        )
                    }
                },
            )
            album?.let {
                put(
                    "album",
                    JSONObject().apply {
                        put("id", it.id)
                        put("title", it.title)
                    },
                )
            }
        }

    private fun JSONObject.toMediaMetadata(): MediaMetadata? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val artistsJson = optJSONArray("artists")
        val artists =
            buildList {
                artistsJson?.let { array ->
                    for (i in 0 until array.length()) {
                        val artist = array.optJSONObject(i) ?: continue
                        val name = artist.optString("name").takeIf { it.isNotBlank() } ?: continue
                        add(
                            MediaMetadata.Artist(
                                id = artist.optString("id").takeIf { it.isNotBlank() },
                                name = name,
                                thumbnailUrl = artist.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
                if (isEmpty()) {
                    add(MediaMetadata.Artist(id = null, name = "Unknown Artist"))
                }
            }
        val albumJson = optJSONObject("album")
        return MediaMetadata(
            id = id,
            title = title,
            artists = artists,
            duration = optInt("duration", 0),
            thumbnailUrl = optString("thumbnailUrl").takeIf { it.isNotBlank() },
            album =
                albumJson?.let {
                    MediaMetadata.Album(
                        id = it.optString("id", id),
                        title = it.optString("title", ""),
                    )
                },
            explicit = optBoolean("explicit", false),
            isMusicVideo = optBoolean("isMusicVideo", false),
        )
    }
}
