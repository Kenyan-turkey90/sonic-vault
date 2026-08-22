/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.di

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    private const val SUPABASE_URL = "https://hnnmgofzuyjkiyvaosfj.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhubm1nb2Z6dXlqa2l5dmFvc2ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODczODEzNDMsImV4cCI6MjEwMjk1NzM0M30.nAgC-YSKV5dIPosy6Tn0Pb78bYL55LYrKPajZFoKZiI"

    @Singleton
    @Provides
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY,
    ) {
        install(Auth)
    }
}
