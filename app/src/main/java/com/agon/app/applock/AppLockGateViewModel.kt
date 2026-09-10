package com.agon.app.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.UsernamePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Verifies the passcode / username+password for the in-app lock gate, reusing the exact same
 * hardened credential store as the settings protection (PBKDF2 + Android Keystore). It never
 * stores or logs the raw secret, and it opens the short unlocked window in [AppLockManager] on
 * success.
 */
@HiltViewModel
class AppLockGateViewModel @Inject constructor(
    private val repository: SettingsProtectionRepository,
) : ViewModel() {

    data class UiState(
        val authMode: AuthMode = AuthMode.PIN,
        val pin: String = "",
        val username: String = "",
        val verifying: Boolean = false,
        val error: Boolean = false,
        val unlocked: Boolean = false,
    ) {
        val canSubmit: Boolean
            get() = !verifying && PinPolicy.isValid(pin) &&
                (authMode == AuthMode.PIN || UsernamePolicy.isValid(username))
    }

    private val _state = MutableStateFlow(
        UiState(authMode = repository.currentAuthMode(), username = ""),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setPin(value: String) {
        if (_state.value.verifying) return
        // PIN mode keeps digits only (capped at 8); the username/password method still accepts any
        // non-whitespace password characters.
        val sanitized = if (_state.value.authMode == AuthMode.PIN) {
            PinPolicy.sanitize(value)
        } else {
            value.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH)
        }
        _state.value = _state.value.copy(pin = sanitized, error = false)
    }

    fun setUsername(value: String) {
        if (_state.value.verifying) return
        _state.value = _state.value.copy(
            username = value.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH),
            error = false,
        )
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.value = current.copy(verifying = true)
        viewModelScope.launch {
            val granted = if (current.authMode == AuthMode.USERNAME_PASSWORD) {
                repository.verifyUsernamePassword(current.username, current.pin)
            } else {
                repository.verifyPin(current.pin)
            }
            if (granted) {
                AppLockManager.markUnlocked()
                _state.value = _state.value.copy(verifying = false, pin = "", error = false, unlocked = true)
            } else {
                _state.value = _state.value.copy(verifying = false, pin = "", error = true)
            }
        }
    }

    /** Marks the biometric unlock as successful (verification handled by the OS prompt). */
    fun onBiometricSuccess() {
        AppLockManager.markUnlocked()
        _state.value = _state.value.copy(unlocked = true, error = false)
    }

    fun onBiometricRejected() {
        _state.value = _state.value.copy(error = true, pin = "")
    }

    /** Re-arms a reused gate and re-reads the configured authentication method. */
    fun refreshAuthMode() {
        _state.value = UiState(authMode = repository.currentAuthMode())
    }

    fun biometricEnabled(): Boolean = repository.currentSnapshot().biometricEnabled
}
