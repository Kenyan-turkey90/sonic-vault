/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.sonicvault.app.constants.MaxSongCacheSizeKey
import com.sonicvault.app.constants.SmartOfflineKey
import com.sonicvault.app.constants.SmartOfflineTargetSongsKey
import com.sonicvault.app.db.MusicDatabase
import com.sonicvault.app.utils.NetworkConnectivityObserver
import com.sonicvault.app.utils.dataStore
import com.sonicvault.app.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Smart Offline: automatically caches your most-listened (and liked) songs
 * ahead of time, within a storage budget, when on an unmetered network.
 *
 * Mirrors what the big streaming apps call "smart downloads" — the app keeps a
 * rolling set of your frequent songs available offline.
 */
@Singleton
class SmartOfflineScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
    ) {
        @Volatile
        private var isRunning = false

        /**
         * Queues downloads for top smart-offline candidates that aren't already
         * downloaded or queued. No-op unless the feature is enabled, the network
         * is unmetered, and no download is already in flight.
         */
        suspend fun maybeRun(connectivityObserver: NetworkConnectivityObserver) {
            if (isRunning) return
            val enabled = context.dataStore.get(SmartOfflineKey, false)
            if (!enabled) return
            if (connectivityObserver.isMetered()) return
            if (connectivityObserver.isCurrentlyConnected().not()) return

            val activeDownloads =
                downloadUtil.downloads.value.values.any {
                    it.state == Download.STATE_QUEUED ||
                        it.state == Download.STATE_DOWNLOADING ||
                        it.state == Download.STATE_STOPPED
                }
            if (activeDownloads) return

            isRunning = true
            try {
                withContext(Dispatchers.IO) {
                    runCatching { runDownloadSelection() }
                        .onFailure { Timber.w(it, "Smart offline selection failed") }
                }
            } finally {
                isRunning = false
            }
        }

        private suspend fun runDownloadSelection() {
            val targetSongs = context.dataStore.get(SmartOfflineTargetSongsKey, 20)
            val maxCacheMb = context.dataStore.get(MaxSongCacheSizeKey, 1024)
            val now = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()
            val monthAgo = now - 30L * 24L * 60L * 60L * 1000L

            val candidates =
                database
                    .smartOfflineCandidates(fromTimeStamp = monthAgo, limit = targetSongs.coerceIn(5, 100))
                    .first()

            if (candidates.isEmpty()) return

            val downloadedIds =
                downloadUtil.downloads.value.values
                    .filter { it.state == Download.STATE_COMPLETED }
                    .map { it.request.id }
                    .toSet()
            val queuedIds =
                downloadUtil.downloads.value.values
                    .filter {
                        it.state == Download.STATE_QUEUED ||
                            it.state == Download.STATE_DOWNLOADING ||
                            it.state == Download.STATE_STOPPED
                    }.map { it.request.id }
                    .toSet()

            // Budget-aware: only queue while we have room under the song-cache cap.
            // -1 means unlimited in the settings UI.
            val hasUnlimitedCache = maxCacheMb <= 0 || maxCacheMb == -1
            var cacheSpaceBytes =
                runCatching { downloadUtil.downloadCache.cacheSpace }.getOrDefault(0L)

            var queued = 0
            for (candidate in candidates) {
                if (candidate.song.isLocal) continue
                if (candidate.id in downloadedIds || candidate.id in queuedIds) continue
                if (queued >= targetSongs) break
                if (!hasUnlimitedCache && cacheSpaceBytes >= maxCacheMb.toLong() * 1024L * 1024L) {
                    Timber.tag("SmartOffline").i(
                        "Smart offline: storage budget reached (%.0f MB), stopping at %d songs",
                        cacheSpaceBytes / 1024.0 / 1024.0,
                        queued,
                    )
                    break
                }

                val request =
                    DownloadRequest
                        .Builder(candidate.id, candidate.id.toUri())
                        .setCustomCacheKey(candidate.id)
                        .setData(candidate.title.toByteArray())
                        .build()
                runCatching {
                    DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        request,
                        false,
                    )
                }.onFailure {
                    Timber.w(it, "Smart offline: failed to queue %s", candidate.id)
                }
                queued += 1
                cacheSpaceBytes += candidate.song.duration.coerceAtLeast(0).toLong() * 128L * 1024L / 1000L
            }

            if (queued > 0) {
                Timber.tag("SmartOffline").i("Smart offline: queued %d song(s) for download", queued)
            }
        }
    }
