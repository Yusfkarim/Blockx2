package com.agon.app.applock

import android.content.Context

/**
 * In-app lock state.
 *
 * Separate from the *settings* protection (which guards system screens) — this guards the
 * BlockX LaAbrah app itself: when enabled the whole app is sealed behind a PIN / password gate
 * that must be passed before any screen, button or content is shown, and every sensitive action
 * re-challenges after a short unlocked window.
 *
 * State lives in a single process-wide object plus a tiny SharedPreferences flag so it:
 *  - survives Activity recreation, configuration changes, Recents and back navigation, and
 *  - resets to *locked* whenever the process is recreated (cold start), because the unlocked
 *    session is intentionally held only in memory and never persisted.
 *
 * The unlocked window is time-boxed ([UNLOCK_WINDOW_MS]); after it elapses the next guarded
 * surface challenges again. This is deliberately short (45s) to satisfy "grant access only for
 * 30–60 seconds, then require the code again".
 */
object AppLockManager {

    private const val PREFS = "family_shield_app_lock"
    private const val KEY_ENABLED = "app_lock_enabled"

    /** Unlocked-session window: access is granted for this long after a successful unlock. */
    const val UNLOCK_WINDOW_MS = 45_000L

    /**
     * Grace period after the app goes to the background.
     *
     * Set to 0 so the app re-locks (and re-asks for the PIN) the instant it returns to the
     * foreground, with no free window. This is intentional: opening a system screen from the
     * app (e.g. the Admin / Accessibility controls) and coming back must challenge immediately
     * rather than silently staying unlocked for a while afterwards.
     */
    const val BACKGROUND_GRACE_MS = 0L

    /**
     * Hard cap on how long a session may stay open counting from the LAST real verification
     * (PIN / biometric). The background grace must never allow the unlocked window to be
     * re-opened — and thus extended — indefinitely by repeated short hops to another app:
     * after this much time without a fresh verification, the next return from background
     * challenges again even if it happens inside the grace window.
     */
    const val MAX_UNLOCKED_SESSION_MS = 10L * 60L * 1000L

    private lateinit var prefs: android.content.SharedPreferences

    /**
     * Timestamp until which the app is considered unlocked. Held only in memory so a cold
     * start always begins locked. Volatile because it is read/written from the UI thread and
     * from lifecycle callbacks.
     */
    @Volatile
    private var unlockedUntil: Long = 0L

    /**
     * Last moment the app went to the background (via [markBackgrounded]). 0 when currently
     * in the foreground. Used together with [BACKGROUND_GRACE_MS] to decide whether a return
     * from background should challenge again.
     */
    @Volatile
    private var backgroundedAt: Long = 0L

    /**
     * Moment of the last successful verification (PIN / biometric). 0 when no verification
     * has happened in this process yet. Used together with [MAX_UNLOCKED_SESSION_MS] so the
     * unlocked session can never outlive a real authentication by an unbounded amount.
     */
    @Volatile
    private var lastVerifiedAt: Long = 0L

    @Synchronized
    fun initialize(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun ready(): Boolean = ::prefs.isInitialized

    /** True when the whole-app lock is switched on. */
    fun isEnabled(): Boolean = ready() && prefs.getBoolean(KEY_ENABLED, false)

    /** Turns the app lock on or off. Enabling immediately relocks the session. */
    fun setEnabled(enabled: Boolean) {
        if (!ready()) return
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        // Relock immediately so enabling takes effect before the next frame, and disabling does
        // not leave a stale unlocked window behind.
        unlockedUntil = 0L
        lastVerifiedAt = 0L
    }

    /** True while the current in-memory unlocked window is still valid. */
    fun isUnlocked(now: Long = System.currentTimeMillis()): Boolean = unlockedUntil > now

    /**
     * True when a gate must be shown right now: the lock is enabled and the session is not
     * within a valid unlocked window.
     *
     * There is no background grace window — returning to the app after any trip to the
     * background challenges again immediately (see [BACKGROUND_GRACE_MS] = 0).
     */
    fun mustChallenge(now: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled()) return false
        if (isUnlocked(now)) return false
        // BACKGROUND_GRACE_MS is 0, so this branch never grants a free window: returning to
        // the app from the background always challenges again immediately.
        if (backgroundedAt > 0L && (now - backgroundedAt) <= BACKGROUND_GRACE_MS) return false
        return true
    }

    /**
     * Called from [MainActivity.onStop] when the app moves to the background. The session is
     * closed immediately so the next foreground visit challenges again.
     */
    fun markBackgrounded(now: Long = System.currentTimeMillis()) {
        backgroundedAt = now
        unlockedUntil = 0L
    }

    /**
     * Called from [MainActivity.onResume] when the app comes back. Because there is no
     * background grace window ([BACKGROUND_GRACE_MS] = 0), this never re-opens the unlocked
     * session and always returns false, so the caller re-locks and asks for the PIN again.
     */
    fun markForegrounded(now: Long = System.currentTimeMillis()): Boolean {
        val wasBackgroundedAt = backgroundedAt
        backgroundedAt = 0L
        if (wasBackgroundedAt > 0L && isEnabled() && (now - wasBackgroundedAt) <= BACKGROUND_GRACE_MS &&
            lastVerifiedAt > 0L && now - lastVerifiedAt <= MAX_UNLOCKED_SESSION_MS
        ) {
            // BACKGROUND_GRACE_MS is 0, so this is never reached: there is no grace window
            // and the unlocked session is not re-opened when the app comes back.
            unlockedUntil = now + UNLOCK_WINDOW_MS
            return true
        }
        return false
    }

    /** Opens the unlocked window after a successful verification. */
    fun markUnlocked(now: Long = System.currentTimeMillis()) {
        lastVerifiedAt = now
        unlockedUntil = now + UNLOCK_WINDOW_MS
    }

    /** Immediately ends the unlocked window (called on stop / screen off). */
    fun lock() {
        unlockedUntil = 0L
        backgroundedAt = 0L
        lastVerifiedAt = 0L
    }

    /** Milliseconds remaining in the unlocked window, or 0 when locked. */
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long =
        (unlockedUntil - now).coerceAtLeast(0L)
}
