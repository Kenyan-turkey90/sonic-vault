/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.utils

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Records playback failures, recovery attempts and resolutions so users can
 * diagnose why a track failed to play (and what the app tried to do about it).
 *
 * Kept entirely in-memory per process with a bounded ring buffer — no disk I/O
 * on the playback path.
 */
object PlaybackDiagnosticsRecorder {

    enum class Kind { ERROR, RETRY, RECOVERED, SKIPPED, STOPPED }

    @Immutable
    data class Entry(
        val id: Long,
        val kind: Kind,
        val mediaId: String?,
        val title: String?,
        val timestampUptimeMs: Long,
        val elapsedRealtimeMs: Long,
        val errorCode: Int?,
        val errorReason: String?,
        val detail: String?,
        val networkMetered: Boolean,
        val wasCached: Boolean,
        val retryCount: Int,
    ) {
        val elapsedFormatted: String
            get() {
                val totalSeconds = elapsedRealtimeMs / 1000L
                val minutes = totalSeconds / 60L
                val seconds = totalSeconds % 60L
                return "%02d:%02d".format(minutes, seconds)
            }
    }

    private const val MAX_ENTRIES = 200

    private val entriesDeque = ConcurrentLinkedDeque<Entry>()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _totalErrors = MutableStateFlow(0L)
    val totalErrors: StateFlow<Long> = _totalErrors.asStateFlow()

    private val _totalRetries = MutableStateFlow(0L)
    val totalRetries: StateFlow<Long> = _totalRetries.asStateFlow()

    private val _totalRecoveries = MutableStateFlow(0L)
    val totalRecoveries: StateFlow<Long> = _totalRecoveries.asStateFlow()

    private val retryCounts = HashMap<String, Int>()
    private val idSequence = java.util.concurrent.atomic.AtomicLong(0L)

    @Synchronized
    private fun add(entry: Entry) {
        entriesDeque.addFirst(entry)
        while (entriesDeque.size > MAX_ENTRIES) {
            entriesDeque.pollLast()
        }
        _entries.value = entriesDeque.toList()
    }

    @Synchronized
    fun recordError(
        mediaId: String?,
        title: String?,
        errorCode: Int?,
        errorReason: String?,
        detail: String?,
        networkMetered: Boolean,
        wasCached: Boolean,
    ) {
        val count = retryCounts[mediaId] ?: 0
        add(
            Entry(
                id = idSequence.incrementAndGet(),
                kind = Kind.ERROR,
                mediaId = mediaId,
                title = title,
                timestampUptimeMs = SystemClock.uptimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                errorCode = errorCode,
                errorReason = errorReason,
                detail = detail,
                networkMetered = networkMetered,
                wasCached = wasCached,
                retryCount = count,
            ),
        )
        _totalErrors.value += 1L
    }

    @Synchronized
    fun recordRetry(
        mediaId: String?,
        title: String?,
        detail: String?,
        networkMetered: Boolean,
        wasCached: Boolean,
    ) {
        val count = (mediaId?.let { retryCounts[it] } ?: 0) + 1
        if (mediaId != null) retryCounts[mediaId] = count
        add(
            Entry(
                id = idSequence.incrementAndGet(),
                kind = Kind.RETRY,
                mediaId = mediaId,
                title = title,
                timestampUptimeMs = SystemClock.uptimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                errorCode = null,
                errorReason = null,
                detail = detail,
                networkMetered = networkMetered,
                wasCached = wasCached,
                retryCount = count,
            ),
        )
        _totalRetries.value += 1L
    }

    @Synchronized
    fun recordRecovered(
        mediaId: String?,
        title: String?,
        detail: String?,
    ) {
        retryCounts.remove(mediaId)
        add(
            Entry(
                id = idSequence.incrementAndGet(),
                kind = Kind.RECOVERED,
                mediaId = mediaId,
                title = title,
                timestampUptimeMs = SystemClock.uptimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                errorCode = null,
                errorReason = null,
                detail = detail,
                networkMetered = false,
                wasCached = false,
                retryCount = 0,
            ),
        )
        _totalRecoveries.value += 1L
    }

    @Synchronized
    fun recordSkippedOrStopped(
        mediaId: String?,
        title: String?,
        skipped: Boolean,
        detail: String?,
    ) {
        retryCounts.remove(mediaId)
        add(
            Entry(
                id = idSequence.incrementAndGet(),
                kind = if (skipped) Kind.SKIPPED else Kind.STOPPED,
                mediaId = mediaId,
                title = title,
                timestampUptimeMs = SystemClock.uptimeMillis(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                errorCode = null,
                errorReason = null,
                detail = detail,
                networkMetered = false,
                wasCached = false,
                retryCount = 0,
            ),
        )
    }

    @Synchronized
    fun clear() {
        entriesDeque.clear()
        retryCounts.clear()
        _entries.value = emptyList()
        _totalErrors.value = 0L
        _totalRetries.value = 0L
        _totalRecoveries.value = 0L
    }
}
