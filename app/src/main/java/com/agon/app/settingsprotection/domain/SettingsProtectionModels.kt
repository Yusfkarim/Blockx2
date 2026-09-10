package com.agon.app.settingsprotection.domain

import kotlinx.serialization.Serializable

/** Categories of system screens that expose destructive control over BlockX LaAbrah. */
enum class ProtectedScreenType {
    APP_INFO,
    APP_DETAILS_SETTINGS,
    INSTALLED_APP_DETAILS,
    UNINSTALL,
    FORCE_STOP,
    CLEAR_DATA,
    PERMISSIONS,

    /** Device administrator list or the deactivate-admin confirmation screen. */
    DEVICE_ADMIN,

    /** Security settings, the entry point to the device admin list on most skins. */
    SECURITY_SETTINGS,

    /** Manage/installed applications list, the usual route to the uninstall button. */
    MANAGE_APPS,

    /**
     * Accessibility settings / this app's accessibility-service detail screen, i.e. the place a
     * user would go to turn the protection service off. Guarded so protection cannot be disabled
     * without the PIN.
     */
    ACCESSIBILITY_SETTINGS,

    /**
     * System power menu "Safe mode" entry (and OEM equivalents). Entering Safe Mode disables
     * third-party protection, so the option is gated behind the protection PIN while the device
     * is running. Hardware boot-key Safe Mode cannot be blocked by a normal app.
     */
    SAFE_MODE,

    /**
     * System power menu Power off / Restart (and OEM equivalents). Gated so a child cannot
     * shut down or reboot the device without the protection PIN. Holding hardware keys while
     * the device is already off cannot be blocked by a normal app.
     */
    POWER_MENU,

    /**
     * Factory reset / erase-all-data / reset-options surfaces in system Settings. Gated so a
     * child cannot wipe the device without the protection PIN. Recovery-mode factory reset
     * (hardware keys while powered off) cannot be blocked by a normal app.
     */
    FACTORY_RESET,

    /**
     * "Delete system update" confirmation (often hosted by DocumentsUI / Files when an OTA
     * package is selected). Deleting a staged update can be used to weaken device integrity
     * controls, so it requires the protection PIN.
     */
    SYSTEM_UPDATE_DELETE,

    /**
     * Developer options screen and the sensitive toggles inside it (OEM unlocking, USB / wireless
     * debugging). These are the usual routes a technical user takes to sideload tools or pull the
     * app off the device, so the screen is gated behind the protection PIN.
     */
    DEVELOPER_OPTIONS,

    /**
     * THIS app's own battery / background-restriction settings page (never the general system
     * battery screens, never another app's page). Dynamic two-phase rule: the page stays
     * PIN-free while background work is not yet enabled so the parent can pick "No
     * restrictions"; the moment background work is enabled (system whitelist or the checked
     * unrestricted option on the page) the surface is instantly locked, so the child cannot
     * re-optimise / restrict / kill the app's background operation.
     */
    BATTERY_RESTRICTION,

    /**
     * A tap on this app's own autostart toggle inside the general autostart list
     * (MIUI/HyperOS Security Center etc.). Click-path only: unlike ordinary surfaces this one
     * NEVER rides the temporary-access grace — every ON→OFF attempt on a protection switch is
     * challenged again, on any device and in any device language.
     */
    AUTOSTART_TOGGLE,

    /**
     * The "Background autostart" entry card just became VISIBLE on a security-center dashboard
     * (Xiaomi Manage apps / Security Center home and OEM equivalents). Guarded by presence only
     * (no click required): the service backs the user out of the screen and raises the PIN.
     * A correct PIN drops the adult straight into the real autostart page; a cancel keeps the
     * device out of it entirely.
     */
    AUTOSTART_ENTRY,
}

/** How the user proved (or failed to prove) their identity. */
enum class AuthMethod { PIN, BIOMETRIC, BACK_PRESS, TIMEOUT }

/**
 * The single sign-in method the user has chosen. Exactly one can be active at a time: enabling
 * one automatically replaces the other, so PIN and username/password are mutually exclusive.
 */
enum class AuthMode {
    /** A single passcode field (letters, digits and symbols allowed). */
    PIN,

    /** Two separate fields: a username and a password. */
    USERNAME_PASSWORD,
}

/** Result of a single detection pass over the currently visible system window. */
data class ProtectedScreenDetection(
    val type: ProtectedScreenType,
    val settingsPackage: String,
    val screenClass: String,
    val confidence: Int,
)

/** Persisted record of a blocked access attempt. */
@Serializable
data class FailedAttempt(
    val timestamp: Long,
    val screenType: String,
    val settingsPackage: String,
    val method: String,
)

/** Synchronous view of protection flags, safe to read from the accessibility hot path. */
data class ProtectionSnapshot(
    val enabled: Boolean,
    val pinConfigured: Boolean,
    val pinLength: Int,
    val biometricEnabled: Boolean,
    val graceUntil: Long,
    val lockedUntil: Long = 0L,
    val authMode: AuthMode = AuthMode.PIN,
) {
    val active: Boolean get() = enabled && pinConfigured
    fun hasTemporaryAccess(now: Long): Boolean = graceUntil > now
    fun remainingAccessSeconds(now: Long): Int =
        if (graceUntil > now) (((graceUntil - now) + 999L) / 1000L).toInt() else 0

    /** True while an enforced protection duration is still running; disabling is refused. */
    fun isLocked(now: Long): Boolean = lockedUntil > now
    fun remainingLockedMillis(now: Long): Long = if (lockedUntil > now) lockedUntil - now else 0L
}

/** Preset durations the user can commit protection to run for. */
enum class ProtectionDuration(val millis: Long) {
    /** No enforced period; protection can be turned off at any time. */
    CONTINUOUS(0L),
    ONE_DAY(24L * 60L * 60L * 1000L),
    THREE_DAYS(3L * 24L * 60L * 60L * 1000L),
    ONE_WEEK(7L * 24L * 60L * 60L * 1000L),
    TWO_WEEKS(14L * 24L * 60L * 60L * 1000L),
    ONE_MONTH(30L * 24L * 60L * 60L * 1000L),
}

/** Observable state consumed by the settings UI. */
data class SettingsProtectionState(
    val enabled: Boolean = false,
    val pinConfigured: Boolean = false,
    val pinLength: Int = 0,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val temporaryAccessSeconds: Int = 0,
    val failedAttempts: List<FailedAttempt> = emptyList(),
    val credentialCorrupted: Boolean = false,
    val locked: Boolean = false,
    val remainingLockedMillis: Long = 0L,
    val authMode: AuthMode = AuthMode.PIN,
    val username: String = "",
) {
    val protecting: Boolean get() = enabled && pinConfigured
}

/** Outcome of a PIN mutation requested from the UI. */
sealed interface PinOperationResult {
    data object Success : PinOperationResult
    data object WrongPin : PinOperationResult
    data object InvalidLength : PinOperationResult
    data object Mismatch : PinOperationResult
    data object SameAsCurrent : PinOperationResult
    data object StorageFailure : PinOperationResult

    /** Rejected because an enforced protection period is still running. */
    data object Locked : PinOperationResult
}

object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    /**
     * A PIN is valid when it is 4-8 digits long. Only digits are allowed, so the passcode is a
     * pure numeric PIN entered with a number-only keyboard.
     */
    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() } && pin.isNotBlank()

    fun canAppend(current: String): Boolean = current.length < MAX_LENGTH

    /** Keeps only digits and caps the length at [MAX_LENGTH]; used to sanitise text-field input. */
    fun sanitize(value: String): String = value.filter { it.isDigit() }.take(MAX_LENGTH)
}

/** Rules for the username field of the username/password sign-in method. */
object UsernamePolicy {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /** A username is 3-20 non-whitespace characters. */
    fun isValid(username: String): Boolean =
        username.length in MIN_LENGTH..MAX_LENGTH && username.none { it.isWhitespace() } && username.isNotBlank()
}

object ProtectionTiming {
    /** Temporary access window granted after a successful unlock. */
    const val GRACE_MILLIS = 60_000L

    /**
     * How long an in-flight PIN challenge may suppress a second launch while the lock UI opens.
     * Not a substitute for grace — only avoids stacking duplicate PIN activities.
     */
    const val PROMPT_IN_FLIGHT_MILLIS = 2_500L

    /**
     * How long a dispatched challenge may stay UNCONFIRMED (the lock Activity never reported
     * itself visible) before the guard re-dispatches it. Shorter than [PROMPT_IN_FLIGHT_MILLIS]
     * because the launch happens from the already-live service process: if the PIN is not on
     * screen this fast, the ROM most likely dropped the launch and the protected surface must
     * be re-challenged without waiting out the full in-flight window.
     */
    const val CHALLENGE_UNCONFIRMED_WINDOW_MS = 1_200L

    /**
     * After a successful unlock the task may briefly show Home before Settings returns.
     * Do not clear grace during this settle window.
     */
    const val POST_GRANT_SETTLE_MILLIS = 4_000L

    /** @deprecated Kept for source compatibility. */
    const val PROMPT_THROTTLE_MILLIS = 1_200L
}
