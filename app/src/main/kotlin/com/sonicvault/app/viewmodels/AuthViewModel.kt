/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.sonicvault.app.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object SignedIn : AuthUiState
    data object CheckingSession : AuthUiState
    data class Error(val message: String) : AuthUiState
    data class SignedUp(val email: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    private fun checkSession() {
        viewModelScope.launch {
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session != null) {
                    _uiState.value = AuthUiState.SignedIn
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check session")
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                _uiState.value = AuthUiState.SignedIn
            } catch (e: Exception) {
                Timber.e(e, "Sign in failed")
                _uiState.value = AuthUiState.Error(
                    e.message ?: "Sign in failed. Check your credentials."
                )
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = AuthUiState.Error("Email and password are required")
            return
        }

        if (password.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                _uiState.value = AuthUiState.SignedUp(email)
            } catch (e: Exception) {
                Timber.e(e, "Sign up failed")
                _uiState.value = AuthUiState.Error(
                    e.message ?: "Sign up failed. Please try again."
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                _uiState.value = AuthUiState.Idle
            } catch (e: Exception) {
                Timber.e(e, "Sign out failed")
            }
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
