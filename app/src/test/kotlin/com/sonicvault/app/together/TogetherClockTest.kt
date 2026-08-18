/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.together

import org.junit.Assert.assertEquals
import org.junit.Test

class TogetherClockTest {
    @Test
    fun firstPongSeedsOffsetAndRtt() {
        val clock = TogetherClock()
        // sent at t=1000, received at t=1200 (rtt=200), server thinks it is 1100
        val snapshot = clock.onPong(sentAtElapsedMs = 1_000L, receivedAtElapsedMs = 1_200L, serverElapsedMs = 1_100L)

        assertEquals(200L, snapshot.estimatedRttMs)
        // mid = 1000 + 200/2 = 1100; offset = 1100 - 1100 = 0
        assertEquals(0L, snapshot.estimatedOffsetMs)
    }

    @Test
    fun negativeRttIsClampedToZero() {
        val clock = TogetherClock()
        // received before sent -> clamped rtt = 0, mid = sent; offset stays as measured
        val snapshot = clock.onPong(sentAtElapsedMs = 1_200L, receivedAtElapsedMs = 1_000L, serverElapsedMs = 1_000L)

        assertEquals(0L, snapshot.estimatedRttMs)
        // mid = 1200, server = 1000 -> offset = -200 (negative offsets are not clamped)
        assertEquals(-200L, snapshot.estimatedOffsetMs)
    }

    @Test
    fun smallOffsetsUseSlowConvergence() {
        val clock = TogetherClock()
        // seed offset 1000ms (first measurement is taken directly)
        clock.onPong(sentAtElapsedMs = 1_000L, receivedAtElapsedMs = 1_000L, serverElapsedMs = 2_000L)
        // second pong: server ahead by another 100ms -> newOffset 1100
        val snapshot = clock.onPong(sentAtElapsedMs = 2_000L, receivedAtElapsedMs = 2_000L, serverElapsedMs = 3_100L)

        // alpha = 0.2: offset = 1000 + (1100 - 1000) * 0.2 = 1020ms
        assertEquals(1_020L, snapshot.estimatedOffsetMs)
    }

    @Test
    fun largeOffsetsConvergeFaster() {
        val clock = TogetherClock()
        // seed offset 11_000ms
        clock.onPong(sentAtElapsedMs = 1_000L, receivedAtElapsedMs = 1_000L, serverElapsedMs = 12_000L)
        // second pong: newOffset 21_000ms (>1500 delta) -> alpha 0.6
        val snapshot = clock.onPong(sentAtElapsedMs = 2_000L, receivedAtElapsedMs = 2_000L, serverElapsedMs = 23_000L)

        // offset = 11000 + (21000 - 11000) * 0.6 = 17_000ms
        assertEquals(17_000L, snapshot.estimatedOffsetMs)
    }

    @Test
    fun rttSmoothingBlendsMeasurements() {
        val clock = TogetherClock()
        clock.onPong(sentAtElapsedMs = 1_000L, receivedAtElapsedMs = 1_200L, serverElapsedMs = 1_100L) // rtt 200
        // second pong rtt = 1000 -> smoothed = 200 + (1000 - 200) * 0.15 = 320
        val snapshot = clock.onPong(sentAtElapsedMs = 2_000L, receivedAtElapsedMs = 3_000L, serverElapsedMs = 2_500L)

        assertEquals(320L, snapshot.estimatedRttMs)
    }

    @Test
    fun snapshotDefaultsToZero() {
        assertEquals(TogetherClockSnapshot(0L, 0L), TogetherClock().snapshot())
    }
}
