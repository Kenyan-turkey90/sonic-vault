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
internal data class BackendExtractorResponse(
    val success: Boolean = false,
    val valid: Boolean = false,
    val cached: Boolean = false,
    @SerialName("server_version")
    val serverVersion: String? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    @SerialName("stream_url")
    val streamUrl: String? = null,
    @SerialName("stream_path")
    val streamPath: String? = null,
    @SerialName("stream_expires_at")
    val streamExpiresAt: Long? = null,
    @SerialName("format_id")
    val formatId: String? = null,
    val ext: String? = null,
    val acodec: String? = null,
    @SerialName("mime_type")
    val mimeType: String? = null,
    val error: String? = null,
)
