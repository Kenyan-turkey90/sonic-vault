/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamRecoveryTrackerTest {
    @Test
    fun firstRetryForMediaIdIsAccepted() {
        val tracker = PlaybackStreamRecoveryTracker()

        assertTrue(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun duplicateRetryForSameMediaIdIsRejected() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        assertFalse(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun retryForDifferentMediaIdIsAccepted() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        assertTrue(tracker.registerRetryAttempt("video-2"))
    }

    @Test
    fun recoveryClearsTrackingForSameMediaId() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        tracker.onPlaybackRecovered("video-1")

        assertTrue(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun recoveryForOtherMediaIdDoesNotClearTracking() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        tracker.onPlaybackRecovered("video-2")

        assertFalse(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun nullRecoveryDoesNotClearTracking() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        tracker.onPlaybackRecovered(null)

        assertFalse(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun mediaItemChangeToSameIdKeepsTracking() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        tracker.onMediaItemChanged("video-1")

        assertFalse(tracker.registerRetryAttempt("video-1"))
    }

    @Test
    fun mediaItemChangeToDifferentIdClearsTracking() {
        val tracker = PlaybackStreamRecoveryTracker()
        tracker.registerRetryAttempt("video-1")

        tracker.onMediaItemChanged("video-2")

        assertTrue(tracker.registerRetryAttempt("video-2"))
        assertTrue(tracker.registerRetryAttempt("video-1"))
    }
}
