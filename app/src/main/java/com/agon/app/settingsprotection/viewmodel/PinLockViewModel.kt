package com.agon.app.settingsprotection.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.AuthMethod
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection
import com.agon.app.settingsprotection.domain.ProtectedScreenType
import com.agon.app.settingsprotection.domain.UsernamePolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Drives the full screen PIN challenge shown above the protected settings window. */
@HiltViewModel
class PinLockViewModel @Inject constructor(
    private val repository: SettingsProtectionRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val pin: String = "",
        val username: String = "",
        val authMode: AuthMode = AuthMode.PIN,
        val expectedLength: Int = PinPolicy.MIN_LENGTH,
        val verifying: Boolean = false,
        val error: Boolean = false,
        val biometricEnabled: Boolean = false,
        val screenType: ProtectedScreenType = ProtectedScreenType.APP_INFO,
        val isStrongProtectionActive: Boolean = false,
        val remainingStrongMillis: Long = 0L,
    ) {
        val canSubmit: Boolean
            get() = !isStrongProtectionActive && !verifying && PinPolicy.isValid(pin) &&
                (authMode == AuthMode.PIN || UsernamePolicy.isValid(username))
    }

    /** One-shot signals the activity reacts to. */
    sealed interface Event {
        data object Granted : Event
        data object Denied : Event
        data object WrongPinFeedback : Event
        data object LaunchBiometric : Event
    }

    private val _state = MutableStateFlow(
        UiState(
            authMode = repository.currentAuthMode(),
            expectedLength = repository.currentSnapshot().pinLength.coerceAtLeast(PinPolicy.MIN_LENGTH),
            biometricEnabled = repository.currentSnapshot().biometricEnabled && !ProtectionCommitmentStore.isStrongActive(context),
            isStrongProtectionActive = ProtectionCommitmentStore.isStrongActive(context),
            remainingStrongMillis = ProtectionCommitmentStore.remainingMillis(context),
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<Event> = _events.asSharedFlow()

    private var detection: ProtectedScreenDetection? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                val strong = ProtectionCommitmentStore.isStrongActive(context)
                val remaining = ProtectionCommitmentStore.remainingMillis(context)
                _state.value = _state.value.copy(
                    isStrongProtectionActive = strong,
                    remainingStrongMillis = remaining,
                    biometricEnabled = if (strong) false else repository.currentSnapshot().biometricEnabled,
                )
                // If the commitment expired or was stopped while the lock screen is visible,
                // auto-dismiss: the user should have direct access without entering PIN.
                if (!strong && (ProtectionCommitmentStore.isExpired(context) || ProtectionCommitmentStore.isStopped(context))) {
                    repository.grantTemporaryAccess()
                    _events.tryEmit(Event.Granted)
                }
                delay(1_000L)
            }
        }
    }

    fun bind(detection: ProtectedScreenDetection) {
        this.detection = detection
        _state.value = _state.value.copy(screenType = detection.type)
    }

    fun append(digit: Char) {
        val current = _state.value
        if (current.isStrongProtectionActive || current.verifying || !PinPolicy.canAppend(current.pin)) return
        val updated = current.pin + digit
        _state.value = current.copy(pin = updated, error = false)
        // Auto-submit only applies to the single-field PIN method.
        if (current.authMode == AuthMode.PIN &&
            updated.length >= current.expectedLength && PinPolicy.isValid(updated)
        ) {
            submit()
        }
    }

    /**
     * Sets the passcode text typed into the field. In PIN mode only digits are kept and the length
     * is capped at [PinPolicy.MAX_LENGTH] (8); the username/password method still accepts any
     * non-whitespace password characters.
     */
    fun setPin(value: String) {
        val current = _state.value
        if (current.isStrongProtectionActive || current.verifying) return
        val sanitized = if (current.authMode == AuthMode.PIN) {
            PinPolicy.sanitize(value)
        } else {
            value.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH)
        }
        _state.value = current.copy(pin = sanitized, error = false)
    }

    /** Sets the username typed into the username field (username/password method). */
    fun setUsername(value: String) {
        val current = _state.value
        if (current.isStrongProtectionActive || current.verifying) return
        val sanitized = value.filter { !it.isWhitespace() }.take(UsernamePolicy.MAX_LENGTH)
        _state.value = current.copy(username = sanitized, error = false)
    }

    fun backspace() {
        val current = _state.value
        if (current.isStrongProtectionActive || current.verifying || current.pin.isEmpty()) return
        _state.value = current.copy(pin = current.pin.dropLast(1), error = false)
    }

    fun clear() {
        val current = _state.value
        if (current.isStrongProtectionActive || current.verifying) return
        _state.value = current.copy(pin = "", error = false)
    }

    fun submit() {
        val current = _state.value
        if (current.isStrongProtectionActive) {
            _events.tryEmit(Event.Denied)
            return
        }
        if (!current.canSubmit) return
        _state.value = current.copy(verifying = true)
        viewModelScope.launch {
            val granted = if (current.authMode == AuthMode.USERNAME_PASSWORD) {
                repository.verifyUsernamePassword(current.username, current.pin)
            } else {
                repository.verifyPin(current.pin)
            }
            if (granted) {
                repository.grantTemporaryAccess()
                _state.value = _state.value.copy(verifying = false, pin = "", error = false)
                _events.tryEmit(Event.Granted)
            } else {
                _state.value = _state.value.copy(verifying = false, pin = "", error = true)
                _events.tryEmit(Event.WrongPinFeedback)
                repository.recordFailure(detection, AuthMethod.PIN)
                _events.tryEmit(Event.Denied)
            }
        }
    }

    fun requestBiometric() {
        if (!_state.value.isStrongProtectionActive && _state.value.biometricEnabled) {
            _events.tryEmit(Event.LaunchBiometric)
        }
    }

    fun onBiometricSuccess() {
        if (ProtectionCommitmentStore.isStrongActive(context)) {
            _events.tryEmit(Event.Denied)
            return
        }
        repository.grantTemporaryAccess()
        _events.tryEmit(Event.Granted)
    }

    /** Biometric errors fall back to the PIN keypad instead of denying access outright. */
    fun onBiometricUnavailable() {
        _state.value = _state.value.copy(verifying = false)
    }

    fun onBiometricRejected() {
        _state.value = _state.value.copy(error = true, pin = "")
        _events.tryEmit(Event.WrongPinFeedback)
    }

    /** Back press or cancellation is treated as a failed attempt. */
    fun onCancelled() {
        repository.recordFailure(detection, AuthMethod.BACK_PRESS)
        _events.tryEmit(Event.Denied)
    }

    fun onTimeout() {
        repository.recordFailure(detection, AuthMethod.TIMEOUT)
        _events.tryEmit(Event.Denied)
    }
}
