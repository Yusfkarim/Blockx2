package com.agon.app.settingsprotection.data

import android.content.Context
import android.content.SharedPreferences
import com.agon.app.settingsprotection.domain.AuthMode
import com.agon.app.settingsprotection.domain.ProtectionSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret protection flags kept in SharedPreferences so the accessibility hot path can read
 * them synchronously without suspending. Secrets live in [PinCredentialStore] instead.
 */
@Singleton
class SettingsProtectionPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("family_shield_settings_protection", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var pinConfigured: Boolean
        get() = prefs.getBoolean(KEY_CONFIGURED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONFIGURED, value).apply()

    var pinLength: Int
        get() = prefs.getInt(KEY_LENGTH, 0)
        set(value) = prefs.edit().putInt(KEY_LENGTH, value).apply()

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    var graceUntil: Long
        get() = prefs.getLong(KEY_GRACE, 0L)
        set(value) = prefs.edit().putLong(KEY_GRACE, value).apply()

    var lockedUntil: Long
        get() = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_LOCKED_UNTIL, value).apply()

    var credentialCorrupted: Boolean
        get() = prefs.getBoolean(KEY_CORRUPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_CORRUPTED, value).apply()

    /** Which sign-in method is active: PIN or username + password. */
    var authMode: AuthMode
        get() = runCatching { AuthMode.valueOf(prefs.getString(KEY_AUTH_MODE, AuthMode.PIN.name)!!) }
            .getOrDefault(AuthMode.PIN)
        set(value) = prefs.edit().putString(KEY_AUTH_MODE, value.name).apply()

    /** Username for the username/password method. Empty for the PIN method. */
    var username: String
        get() = prefs.getString(KEY_USERNAME, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    fun snapshot(): ProtectionSnapshot = ProtectionSnapshot(
        enabled = enabled,
        pinConfigured = pinConfigured,
        pinLength = pinLength,
        biometricEnabled = biometricEnabled,
        graceUntil = graceUntil,
        lockedUntil = lockedUntil,
        authMode = authMode,
    )

    fun clearCredentialFlags() {
        prefs.edit()
            .putBoolean(KEY_CONFIGURED, false)
            .putInt(KEY_LENGTH, 0)
            .putBoolean(KEY_BIOMETRIC, false)
            .putLong(KEY_GRACE, 0L)
            .putString(KEY_USERNAME, "")
            .putString(KEY_AUTH_MODE, AuthMode.PIN.name)
            .apply()
    }

    private companion object {
        const val KEY_ENABLED = "protection_enabled"
        const val KEY_CONFIGURED = "pin_configured"
        const val KEY_LENGTH = "pin_length"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_GRACE = "grace_until"
        const val KEY_LOCKED_UNTIL = "locked_until"
        const val KEY_CORRUPTED = "credential_corrupted"
        const val KEY_AUTH_MODE = "auth_mode"
        const val KEY_USERNAME = "auth_username"
    }
}
