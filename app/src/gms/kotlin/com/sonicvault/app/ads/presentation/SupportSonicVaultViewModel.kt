/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.ads.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.sonicvault.app.ads.domain.ObserveSupportAdAvailabilityUseCase
import com.sonicvault.app.ads.domain.ObserveSupportAdEventsUseCase
import com.sonicvault.app.ads.domain.SetPersonalizedAdsConsentUseCase
import com.sonicvault.app.ads.domain.ShowSupportAdUseCase
import com.sonicvault.app.ads.domain.SupportAdAvailability
import com.sonicvault.app.ads.domain.SupportAdEvent
import com.sonicvault.app.ads.domain.SupportAdRequestResult
import javax.inject.Inject

internal sealed interface SupportSonicVaultScreenState {
    val model: SupportSonicVaultUiModel

    @Immutable
    data class Loading(
        override val model: SupportSonicVaultUiModel,
    ) : SupportSonicVaultScreenState

    @Immutable
    data class Success(
        override val model: SupportSonicVaultUiModel,
    ) : SupportSonicVaultScreenState

    @Immutable
    data class Empty(
        override val model: SupportSonicVaultUiModel,
    ) : SupportSonicVaultScreenState

    @Immutable
    data class Error(
        override val model: SupportSonicVaultUiModel,
    ) : SupportSonicVaultScreenState
}

@Immutable
internal data class SupportSonicVaultUiModel(
    val privacyOptionsRequired: Boolean,
    val consentDialogPurpose: ConsentDialogPurpose?,
)

internal enum class ConsentDialogPurpose {
    SupportAd,
    PrivacyOptions,
}

internal enum class SupportSonicVaultUiEvent {
    RewardEarned,
    AdFailed,
    ActivityUnavailable,
    PrivacyOptionsUpdated,
}

@HiltViewModel
internal class SupportSonicVaultViewModel
    @Inject
    constructor(
        observeAvailability: ObserveSupportAdAvailabilityUseCase,
        observeEvents: ObserveSupportAdEventsUseCase,
        private val showSupportAd: ShowSupportAdUseCase,
        private val setPersonalizedAdsConsent: SetPersonalizedAdsConsentUseCase,
    ) : ViewModel() {
        private val consentDialogPurpose = MutableStateFlow<ConsentDialogPurpose?>(null)

        val screenState: StateFlow<SupportSonicVaultScreenState> =
            combine(observeAvailability(), consentDialogPurpose) { availability, dialogPurpose ->
                val model =
                    SupportSonicVaultUiModel(
                        privacyOptionsRequired = true,
                        consentDialogPurpose = dialogPurpose,
                    )
                when (availability) {
                    SupportAdAvailability.Preparing -> SupportSonicVaultScreenState.Loading(model)

                    SupportAdAvailability.Ready,
                    SupportAdAvailability.ConsentRequired,
                    -> SupportSonicVaultScreenState.Success(model)

                    SupportAdAvailability.Unavailable -> SupportSonicVaultScreenState.Empty(model)

                    SupportAdAvailability.Failed -> SupportSonicVaultScreenState.Error(model)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    SupportSonicVaultScreenState.Loading(
                        SupportSonicVaultUiModel(
                            privacyOptionsRequired = true,
                            consentDialogPurpose = null,
                        ),
                    ),
            )

        private val eventChannel = Channel<SupportSonicVaultUiEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                observeEvents().collect { event ->
                    eventChannel.send(event.toUiEvent())
                }
            }
        }

        fun onSupportSonicVaultClick() {
            handleRequestResult(showSupportAd())
        }

        fun onPrivacyOptionsClick() {
            consentDialogPurpose.value = ConsentDialogPurpose.PrivacyOptions
        }

        fun onConsentSelected(personalized: Boolean) {
            val purpose = consentDialogPurpose.value ?: return
            consentDialogPurpose.value = null
            setPersonalizedAdsConsent(personalized)
            if (purpose == ConsentDialogPurpose.SupportAd) {
                handleRequestResult(showSupportAd())
            } else {
                eventChannel.trySend(SupportSonicVaultUiEvent.PrivacyOptionsUpdated)
            }
        }

        fun onConsentDialogDismissed() {
            consentDialogPurpose.value = null
        }

        private fun handleRequestResult(result: SupportAdRequestResult) {
            when (result) {
                SupportAdRequestResult.ConsentRequired -> {
                    consentDialogPurpose.value = ConsentDialogPurpose.SupportAd
                }

                SupportAdRequestResult.ActivityUnavailable -> {
                    eventChannel.trySend(SupportSonicVaultUiEvent.ActivityUnavailable)
                }

                SupportAdRequestResult.ConfigurationMissing -> {
                    eventChannel.trySend(SupportSonicVaultUiEvent.AdFailed)
                }

                SupportAdRequestResult.Accepted,
                SupportAdRequestResult.AlreadyPending,
                -> {
                    Unit
                }
            }
        }

        private fun SupportAdEvent.toUiEvent(): SupportSonicVaultUiEvent =
            when (this) {
                SupportAdEvent.RewardEarned -> SupportSonicVaultUiEvent.RewardEarned
                SupportAdEvent.AdFailed -> SupportSonicVaultUiEvent.AdFailed
                SupportAdEvent.ActivityUnavailable -> SupportSonicVaultUiEvent.ActivityUnavailable
            }
    }
