package com.agon.app.settingsprotection.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.agon.app.admin.ShieldDeviceAdminReceiver
import com.agon.app.data.ShieldRepository
import com.agon.app.settingsprotection.domain.AuthMethod
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.FailedAttempt
import com.agon.app.settingsprotection.domain.PinOperationResult
import com.agon.app.settingsprotection.domain.PinPolicy
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection
import com.agon.app.settingsprotection.domain.ProtectionSnapshot
import com.agon.app.settingsprotection.domain.ProtectionTiming
import com.agon.app.settingsprotection.domain.UsernamePolicy
import com.agon.app.security.TamperProofClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for App Settings Protection.
 *
 * The repository is intentionally split into a synchronous surface (used by the accessibility
 * service, which must decide within milliseconds) and a suspending surface (used by the UI).
 */
@Singleton
class SettingsProtectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SettingsProtectionPreferences,
    private val credentials: PinCredentialStore,
    private val notifications: ProtectionNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(preferences.snapshot())
    val snapshot: StateFlow<ProtectionSnapshot> = _snapshot.asStateFlow()

    val failedAttempts: Flow<List<FailedAttempt>> = credentials.attempts

    private val _corrupted = MutableStateFlow(preferences.credentialCorrupted)
    val corrupted: StateFlow<Boolean> = _corrupted.asStateFlow()

    init {
        preferences.enabled = true
        preferences.lockedUntil = Long.MAX_VALUE
        publish()
        scope.launch { reconcileCredentialState() }
    }

    /** Cheap synchronous read for the accessibility hot path. */
    fun currentSnapshot(): ProtectionSnapshot = _snapshot.value

    /** Current sign-in method. */
    fun currentAuthMode(): AuthMode = preferences.authMode

    /** Configured username for the username/password method, empty otherwise. */
    fun currentUsername(): String = preferences.username

    /** True when a detection should trigger the PIN screen right now. */
    fun shouldChallenge(now: Long = TamperProofClock.now(context)): Boolean {
        val current = _snapshot.value
        if (ProtectionCommitmentStore.isStrongActive(context)) return true
        // When the commitment has expired or been stopped, no challenge is needed —
        // settings are accessible directly until the user manually renews protection.
        if (ProtectionCommitmentStore.isExpired(context) || ProtectionCommitmentStore.isStopped(context)) return false
        return current.active && !current.hasTemporaryAccess(now)
    }

    /** Creates or replaces the PIN. [currentPin] is required once a PIN already exists. */
    suspend fun setPin(newPin: String, confirmPin: String, currentPin: String?): PinOperationResult {
        if (ProtectionCommitmentStore.isStrongActive(context)) return PinOperationResult.Locked
        if (!PinPolicy.isValid(newPin)) return PinOperationResult.InvalidLength
        if (newPin != confirmPin) return PinOperationResult.Mismatch
        val verified = verifyCurrentForChange(currentPin) ?: return PinOperationResult.WrongPin
        if (verified == VerifyForChange.CORRUPTED) return PinOperationResult.StorageFailure
        if (credentials.isConfigured() &&
            preferences.authMode == AuthMode.PIN &&
            currentPin.orEmpty() == newPin
        ) {
            return PinOperationResult.SameAsCurrent
        }
        if (!credentials.store(newPin)) {
            markCorrupted()
            return PinOperationResult.StorageFailure
        }
        preferences.authMode = AuthMode.PIN
        preferences.username = ""
        preferences.pinConfigured = true
        preferences.pinLength = newPin.length
        preferences.credentialCorrupted = false
        _corrupted.value = false
        publish()
        return PinOperationResult.Success
    }

    /**
     * Creates or replaces the username + password credential. Mutually exclusive with the PIN
     * method: setting it switches [AuthMode] to [AuthMode.USERNAME_PASSWORD] and clears any PIN.
     * [current] is required once a credential already exists (a PIN or a password).
     */
    suspend fun setUsernamePassword(
        username: String,
        newPassword: String,
        confirmPassword: String,
        current: String?,
    ): PinOperationResult {
        if (ProtectionCommitmentStore.isStrongActive(context)) return PinOperationResult.Locked
        if (!UsernamePolicy.isValid(username)) return PinOperationResult.InvalidLength
        if (!PinPolicy.isValid(newPassword)) return PinOperationResult.InvalidLength
        if (newPassword != confirmPassword) return PinOperationResult.Mismatch
        val verified = verifyCurrentForChange(current) ?: return PinOperationResult.WrongPin
        if (verified == VerifyForChange.CORRUPTED) return PinOperationResult.StorageFailure
        if (!credentials.store(newPassword)) {
            markCorrupted()
            return PinOperationResult.StorageFailure
        }
        preferences.authMode = AuthMode.USERNAME_PASSWORD
        preferences.username = username
        preferences.pinConfigured = true
        preferences.pinLength = newPassword.length
        preferences.credentialCorrupted = false
        _corrupted.value = false
        publish()
        return PinOperationResult.Success
    }

    private enum class VerifyForChange { OK, CORRUPTED }

    /**
     * Verifies the current secret before a credential change. Returns null when it is wrong, a
     * non-null outcome otherwise. When nothing is configured yet, any input is accepted.
     */
    private suspend fun verifyCurrentForChange(current: String?): VerifyForChange? {
        if (!credentials.isConfigured()) return VerifyForChange.OK
        return when (credentials.matches(current.orEmpty())) {
            PinCredentialStore.VerificationOutcome.MATCH,
            PinCredentialStore.VerificationOutcome.NOT_CONFIGURED,
            -> VerifyForChange.OK
            PinCredentialStore.VerificationOutcome.CORRUPTED -> {
                markCorrupted()
                VerifyForChange.CORRUPTED
            }
            PinCredentialStore.VerificationOutcome.MISMATCH -> null
        }
    }

    /** Removes protection after verifying [currentPin]. Refused while a lock period is running. */
    suspend fun removePin(currentPin: String): PinOperationResult {
        if (ProtectionCommitmentStore.isStrongActive(context) || _snapshot.value.isLocked(TamperProofClock.now(context))) {
            return PinOperationResult.Locked
        }
        return when (credentials.matches(currentPin)) {
            PinCredentialStore.VerificationOutcome.MATCH,
            PinCredentialStore.VerificationOutcome.NOT_CONFIGURED,
            -> {
                credentials.clear()
                preferences.clearCredentialFlags()
                publish()
                PinOperationResult.Success
            }
            PinCredentialStore.VerificationOutcome.MISMATCH -> PinOperationResult.WrongPin
            PinCredentialStore.VerificationOutcome.CORRUPTED -> {
                markCorrupted()
                PinOperationResult.StorageFailure
            }
        }
    }

    /** Recovers from an invalidated keystore key by wiping the unusable credential. */
    suspend fun resetCorruptedCredential() {
        credentials.clear()
        preferences.clearCredentialFlags()
        preferences.credentialCorrupted = false
        _corrupted.value = false
        publish()
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferences.biometricEnabled = enabled
        publish()
    }

    /** Verifies a PIN entered on the lock screen. */
    suspend fun verifyPin(pin: String): Boolean = when (credentials.matches(pin)) {
        PinCredentialStore.VerificationOutcome.MATCH -> true
        PinCredentialStore.VerificationOutcome.CORRUPTED -> {
            markCorrupted()
            false
        }
        else -> false
    }

    /**
     * Verifies a username + password entered on the lock screen. The username is compared
     * case-insensitively; the password is verified against the stored digest.
     */
    suspend fun verifyUsernamePassword(username: String, password: String): Boolean {
        val expected = preferences.username
        if (expected.isBlank()) return false
        if (!expected.equals(username.trim(), ignoreCase = true)) return false
        return verifyPin(password)
    }

    /** Opens the 60 second access window after a successful unlock. */
    fun grantTemporaryAccess(now: Long = TamperProofClock.now(context)) {
        if (ProtectionCommitmentStore.isStrongActive(context)) return
        preferences.graceUntil = now + ProtectionTiming.GRACE_MILLIS
        publish()
    }

    /** Closes the access window so the next visit challenges again. */
    fun revokeTemporaryAccess() {
        preferences.graceUntil = 0L
        publish()
    }

    /**
     * Records a failed authentication attempt and applies an exponential lockout penalty.
     *
     * Lockout schedule (consecutive failures within the last 10 minutes):
     *   3 → 30 seconds
     *   6 → 5 minutes
     *   9 → 30 minutes
     *  12+ → 1 hour
     *
     * The lockout is cumulative and resets only after a successful unlock or a 10-minute gap.
     */
    fun recordFailure(detection: ProtectedScreenDetection?, method: AuthMethod) {
        // Tamper-proof: a manual clock change must not reset lockout windows or jump a
        // lockout to its end — timestamps/lock durations anchor to the honest clock.
        val now = TamperProofClock.now(context)
        val attempt = FailedAttempt(
            timestamp = now,
            screenType = detection?.type?.name ?: "APP_INFO",
            settingsPackage = detection?.settingsPackage.orEmpty(),
            method = method.name,
        )
        scope.launch {
            credentials.appendAttempt(attempt)
            ShieldRepository.recordBlocked(
                domain = attempt.screenType.lowercase().replace('_', ' '),
                source = "Settings protection",
            )
            applyLockoutPenalty(now)
        }
        notifications.notifyBlockedAttempt(attempt)
    }

    /** Computes consecutive failures in the last 10 minutes and applies an exponential lockout. */
    private suspend fun applyLockoutPenalty(now: Long) {
        val attempts = credentials.attempts.first()
        val windowStart = now - LOCKOUT_WINDOW_MILLIS
        val consecutive = attempts.count { it.timestamp >= windowStart }
        val lockDuration = when {
            consecutive >= 12 -> 60L * 60L * 1000L // 1 hour
            consecutive >= 9  -> 30L * 60L * 1000L // 30 minutes
            consecutive >= 6  -> 5L * 60L * 1000L  // 5 minutes
            consecutive >= 3  -> 30L * 1000L       // 30 seconds
            else -> 0L
        }
        if (lockDuration > 0L) {
            preferences.lockedUntil = now + lockDuration
            publish()
        }
    }

    private companion object {
        const val LOCKOUT_WINDOW_MILLIS = 10L * 60L * 1000L // 10 minutes
    }

    fun clearHistory() {
        scope.launch { credentials.clearAttempts() }
    }

    fun isDeviceAdminActive(): Boolean = runCatching {
        context.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(ComponentName(context, ShieldDeviceAdminReceiver::class.java)) == true
    }.getOrDefault(false)

    fun isAccessibilityConnected(): Boolean = runCatching {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        enabled.contains(
            "${context.packageName}/com.agon.app.accessibility.ShieldAccessibilityService",
            ignoreCase = true,
        )
    }.getOrDefault(false)

    /** Aligns cached flags with the real credential state after process restarts. */
    private suspend fun reconcileCredentialState() {
        val configured = credentials.isConfigured()
        if (preferences.pinConfigured != configured) {
            preferences.pinConfigured = configured
            if (!configured) preferences.pinLength = 0
        }
        val now = TamperProofClock.now(context)
        if (preferences.graceUntil <= now) preferences.graceUntil = 0L
        if (preferences.lockedUntil <= now) preferences.lockedUntil = 0L
        _corrupted.value = preferences.credentialCorrupted
        publish()
    }

    private fun markCorrupted() {
        preferences.credentialCorrupted = true
        _corrupted.value = true
    }

    private fun publish() {
        _snapshot.value = preferences.snapshot()
    }
}
