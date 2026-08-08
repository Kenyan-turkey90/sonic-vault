/*
 * SonicVault (2026)
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.gatekeeper

import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import javax.inject.Inject
import javax.inject.Singleton

sealed interface GatekeeperResult {
    data object Allowed : GatekeeperResult

    data class Blocked(
        val message: String,
        val retryable: Boolean,
    ) : GatekeeperResult
}

/**
 * Sonic Vault is fully self-contained — there is no external remote to verify against, so
 * network access is always allowed. The base ArchiveTune gatekeeper gated every request
 * behind the upstream author's private server plus a bearer token, which would otherwise
 * block all playback, search, and discovery on a rebranded build.
 */
@Singleton
class GatekeeperRepository
    @Inject
    constructor() {
        suspend fun checkAccess(): GatekeeperResult {
            NetworkGatekeeper.setConnectionBlocked(false)
            return GatekeeperResult.Allowed
        }
    }