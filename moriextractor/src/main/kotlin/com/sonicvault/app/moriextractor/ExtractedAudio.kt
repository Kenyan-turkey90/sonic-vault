/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.moriextractor

data class ExtractedAudio(
    val success: Boolean,
    val valid: Boolean,
    val cached: Boolean,
    val serverVersion: String,
    val title: String?,
    val thumbnail: String?,
    val streamUrl: String,
    val streamPath: String,
    val streamExpiresAt: Long,
    val formatId: String?,
    val ext: String?,
    val acodec: String?,
    val mimeType: String?,
    val error: String?,
)
