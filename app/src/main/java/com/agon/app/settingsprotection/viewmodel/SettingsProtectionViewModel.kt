package com.agon.app.settingsprotection.viewmodel

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.PinOperationResult
import com.agon.app.settingsprotection.domain.SettingsProtectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "App Settings Protection" section inside the app's own Settings tab. */
@HiltViewModel
class SettingsProtectionViewModel @Inject constructor(
    private val repository: SettingsProtectionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val ticker = MutableStateFlow(System.currentTimeMillis())
    private val environment = MutableStateFlow(readEnvironment())

    private val _results = MutableSharedFlow<PinOperationResult>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val results: Flow<PinOperationResult> = _results.asSharedFlow()

    val state: StateFlow<SettingsProtectionState> = combine(
        repository.snapshot,
        repository.failedAttempts,
        repository.corrupted,
        ticker,
        environment,
    ) { snapshot, attempts, corrupted, now, env ->
        SettingsProtectionState(
            enabled = snapshot.enabled,
            pinConfigured = snapshot.pinConfigured,
            pinLength = snapshot.pinLength,
            biometricEnabled = snapshot.biometricEnabled,
            biometricAvailable = env.biometricAvailable,
            deviceAdminActive = env.deviceAdminActive,
            accessibilityConnected = env.accessibilityConnected,
            temporaryAccessSeconds = snapshot.remainingAccessSeconds(now),
            failedAttempts = attempts,
            credentialCorrupted = corrupted,
            locked = snapshot.isLocked(now),
            remainingLockedMillis = snapshot.remainingLockedMillis(now),
            authMode = snapshot.authMode,
            username = repository.currentUsername(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsProtectionState())

    init {
        // Keeps the temporary-access countdown and permission chips accurate while visible.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                ticker.value = System.currentTimeMillis()
            }
        }
    }

    fun refreshEnvironment() {
        environment.value = readEnvironment()
    }

    /** Allows opening a currently disabled system service screen without challenging activation. */
    fun allowPermissionEnable() = repository.grantTemporaryAccess()

    fun setBiometricEnabled(enabled: Boolean) = repository.setBiometricEnabled(enabled)

    fun savePin(newPin: String, confirmPin: String, currentPin: String) {
        viewModelScope.launch {
            _results.tryEmit(repository.setPin(newPin, confirmPin, currentPin.ifBlank { null }))
        }
    }

    fun saveUsernamePassword(
        username: String,
        newPassword: String,
        confirmPassword: String,
        currentSecret: String,
    ) {
        viewModelScope.launch {
            _results.tryEmit(
                repository.setUsernamePassword(
                    username,
                    newPassword,
                    confirmPassword,
                    currentSecret.ifBlank { null },
                ),
            )
        }
    }

    fun removePin(currentPin: String) {
        viewModelScope.launch { _results.tryEmit(repository.removePin(currentPin)) }
    }

    fun resetCorruptedCredential() {
        viewModelScope.launch {
            repository.resetCorruptedCredential()
            _results.tryEmit(PinOperationResult.Success)
        }
    }

    fun endTemporaryAccess() = repository.revokeTemporaryAccess()

    fun clearHistory() = repository.clearHistory()

    private fun readEnvironment(): Environment = Environment(
        biometricAvailable = runCatching {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false),
        deviceAdminActive = repository.isDeviceAdminActive(),
        accessibilityConnected = repository.isAccessibilityConnected(),
    )

    private data class Environment(
        val biometricAvailable: Boolean,
        val deviceAdminActive: Boolean,
        val accessibilityConnected: Boolean,
    )
}
