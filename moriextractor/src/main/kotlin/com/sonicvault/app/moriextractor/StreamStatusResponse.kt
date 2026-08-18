/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.moriextractor

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreamStatusResponse(
    val success: Boolean,
    val valid: Boolean,
    @SerialName("stream_id")
    val streamId: String? = null,
    @SerialName("stream_expires_at")
    val streamExpiresAt: Long? = null,
    val error: String? = null,
)
