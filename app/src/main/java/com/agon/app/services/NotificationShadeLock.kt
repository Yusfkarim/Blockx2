package com.agon.app.services

import android.content.Context
import com.agon.app.security.TamperProofClock

/**
 * Persistent state of the "Block notification pull-down" option (قفلکردنی دابەزاندنی
 * نۆتیفەیشن / حظر سحب شريط الإشعارات).
 *
 * Time-keeping is tamper-proof by construction: deadlines are computed and compared with
 * [TamperProofClock] (the project's monotonic-anchored clock), never the raw device wall
 * clock — so changing the date/time can neither shorten nor stretch the lock, and reboot
 * downtime is still charged from the same ledger. Reading an expired flag lazily disables it,
 * so a finished lock silently ends without any scheduler.
 */
object NotificationShadeLock {

    /** Sentinel duration meaning "forever" (boo هەتاهەتایە / إلى الأبد). */
    const val FOREVER_MILLIS = -1L

    private const val PREFS = "notification_shade_lock"
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
     * so clock drift or an app kill across midnight can never leave the shade jammed.
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
     * Compares against [TamperProofClock], so the visible countdown is manipulation-proof.
     */
    fun remainingMillis(context: Context): Long {
        val prefs = prefs(context)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return 0L
        val until = prefs.getLong(KEY_UNTIL, 0L)
        if (until == FOREVER_MILLIS) return FOREVER_MILLIS
        return (until - TamperProofClock.now(context)).coerceAtLeast(0L)
    }
}
