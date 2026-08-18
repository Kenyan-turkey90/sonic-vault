/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.moriextractor

import java.util.concurrent.atomic.AtomicReference

interface BearerTokenRepository {
    fun getToken(): String?

    fun updateToken(token: String)

    fun clearToken()
}

class InMemoryBearerTokenRepository(
    initialToken: String? = null,
) : BearerTokenRepository {
    private val token = AtomicReference(initialToken.normalizedToken())

    override fun getToken(): String? = token.get()

    override fun updateToken(token: String) {
        val normalizedToken = requireNotNull(token.normalizedToken()) { "Bearer token must not be blank" }
        this.token.set(normalizedToken)
    }

    override fun clearToken() {
        token.set(null)
    }
}

private fun String?.normalizedToken(): String? = this?.trim()?.takeIf(String::isNotEmpty)
