package com.agon.app.services

import android.content.Context
import com.agon.app.security.TamperProofClock

/**
 * Persistent state of the "Phone Settings Lock" option (قفلکردنی سێتینگی مۆبایل /
 * قفل إعدادات الهاتف): while armed, opening the Settings app (`com.android.settings`) is
 * intercepted at the first window frame and routed through the protection PIN.
 *
 * The store shares [NotificationShadeLock]'s tamper-proof time-keeping: deadlines live on
 * [TamperProofClock], so clock/date changes or reboots cannot break the lock. Expiry clears
 * itself lazily on first read — no worker needed.
 */
object PhoneSettingsLock {

    /** Sentinel duration meaning "forever" (boo هەتاهەتایە / إلى الأبد). */
    const val FOREVER_MILLIS = -1L

    private const val PREFS = "phone_settings_lock"
    private const val KEY_ENABLED = "enabled"

    /** Tamper-proof deadline when the lock stops, [FOREVER_MILLIS] = never. */
    private const val KEY_UNTIL = "locked_until"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Enables the lock for [durationMillis] (or forever when [FOREVER_MILLIS]). */
    fun enableFor(context: Context, durationMillis: Long) {
        val until = if (durationMillis == FOREVER_MILLIS) {
            FOREVER_MILLIS
        } else {
            TamperProofClock.now(context) + durationMillis
        }
        prefs(context).edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_UNTIL, until)
            .apply()
    }

    /** Turns the lock off immediately. */
    fun disable(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).putLong(KEY_UNTIL, 0L).apply()
    }

    /**
     * True while the lock must be enforced. An overdue deadline clears itself on first read,
     * so the toggle auto-unlocks exactly when its countdown ends.
     */
    fun isEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until == FOREVER_MILLIS) return true
        if (TamperProofClock.now(context) < until) return true
        disable(context)
        return false
    }

    /**
     * Milliseconds left on the lock for UI countdowns ([FOREVER_MILLIS] when endless).
     * Compares against [TamperProofClock], so the visible countdown cannot be cheated.
     */
    fun remainingMillis(context: Context): Long {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return 0L
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until == FOREVER_MILLIS) return FOREVER_MILLIS
        return (until - TamperProofClock.now(context)).coerceAtLeast(0L)
    }
}
