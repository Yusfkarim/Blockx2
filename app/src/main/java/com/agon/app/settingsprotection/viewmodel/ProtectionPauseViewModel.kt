package com.agon.app.settingsprotection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.AuthMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Verifies the configured credential before protection is temporarily paused.
 *
 * Unlike [AdminDisableViewModel], this never releases the uninstall/device-owner locks: pausing
 * is a reversible, time-boxed action and must not weaken the app's removal protection.
 */
@HiltViewModel
class ProtectionPauseViewModel @Inject constructor(
    private val repository: SettingsProtectionRepository,
) : ViewModel() {

    val pinConfigured: StateFlow<Boolean> = repository.snapshot
        .map { it.active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.currentSnapshot().active)

    val authMode: StateFlow<AuthMode> = repository.snapshot
        .map { it.authMode }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            repository.currentSnapshot().authMode,
        )

    /** Returns true when the configured credential is correct. Performs no other side effect. */
    suspend fun verify(username: String, secret: String): Boolean = when (authMode.value) {
        AuthMode.PIN -> repository.verifyPin(secret)
        AuthMode.USERNAME_PASSWORD -> repository.verifyUsernamePassword(username, secret)
    }
}
