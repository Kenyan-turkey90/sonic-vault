/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.home

import android.content.Context
import com.sonicvault.app.ai.AiServiceConfig
import com.sonicvault.app.ai.AiTextService
import com.sonicvault.app.constants.AiApiKeyKey
import com.sonicvault.app.constants.AiCustomEndpointKey
import com.sonicvault.app.constants.AiCustomModelKey
import com.sonicvault.app.constants.AiProvider
import com.sonicvault.app.constants.AiProviderKey
import com.sonicvault.app.constants.AiSelectedModelKey
import com.sonicvault.app.db.MusicDatabase
import com.sonicvault.app.db.entities.Song
import com.sonicvault.app.extensions.toEnum
import com.sonicvault.app.utils.dataStore
import com.sonicvault.app.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartMixUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) {
        data class SmartMix(
            val title: String,
            val description: String,
            val songs: List<Song>,
        )

        /**
         * Builds a Smart Mix entirely from on-device listening history. When the
         * user has configured an AI provider, the mix gets a short DJ-style title
         * and description; otherwise a local default is used.
         */
        suspend operator fun invoke(
            limit: Int = 24,
            forceAiNarration: Boolean = false,
        ): SmartMix = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val songs =
                database
                    .smartMixSongs(now = now, limit = limit)
                    .first()
                    .distinctBy { it.song.id }

            if (songs.isEmpty()) {
                return@withContext SmartMix("Smart Mix", "", emptyList())
            }

            var title = "Your Smart Mix"
            var description = "Built from your listening history — stays on your device."

            if (forceAiNarration) {
                val prefs = context.dataStore.data.first()
                val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
                val config =
                    AiServiceConfig(
                        provider = provider,
                        apiKey = prefs[AiApiKeyKey].orEmpty(),
                        customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
                        model =
                            if (provider == AiProvider.CUSTOM) {
                                prefs[AiCustomModelKey].orEmpty()
                            } else {
                                prefs[AiSelectedModelKey].orEmpty()
                            },
                    )
                if (config.canCallApi) {
                    runCatching {
                        val (aiTitle, aiDescription) =
                            AiTextService.narrateSmartMix(
                                config = config,
                                songTitles = songs.map { it.song.title },
                            )
                        title = aiTitle
                        if (aiDescription.isNotBlank()) description = aiDescription
                    }.onFailure {
                        // Non-fatal: keep local defaults when AI narration fails.
                    }
                }
            }

            SmartMix(title, description, songs)
        }
    }
