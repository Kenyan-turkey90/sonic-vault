/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.ads

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.sonicvault.app.ads.data.StartIoSupportAdRepository

object SupportAdsInitializer {
    fun initialize(application: Application) {
        EntryPointAccessors
            .fromApplication(application, SupportAdsEntryPoint::class.java)
            .supportAdRepository()
            .initialize(application)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SupportAdsEntryPoint {
    fun supportAdRepository(): StartIoSupportAdRepository
}
