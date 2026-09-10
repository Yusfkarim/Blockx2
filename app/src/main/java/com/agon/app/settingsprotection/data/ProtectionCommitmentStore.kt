package com.agon.app.settingsprotection.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.agon.app.security.TamperProofClock

/**
 * Stores the user's chosen "protection commitment" — the period the user pledges to keep
 * protection running. It is picked during initial setup (and when changing the credential), and
 * shown as a live countdown on the home screen.
 *
 * Supports Standard Protection and Strong Protection (الحماية القوية 🔒).
 * During Strong Protection, protection cannot be disabled, duration cannot be reduced or cancelled,
 * PIN changes are blocked, and protected settings / uninstall gates show remaining time without PIN bypass.
 *
 * ## Clock-tamper-proof model (accumulative ledger)
 *
 * Expiry is NEVER decided by comparing the device wall clock against an end timestamp
 * (`System.currentTimeMillis() >= until`). Instead the store keeps a persisted ledger of
 * *remaining* milliseconds that is decremented only by genuinely elapsed time:
 *
 *  - While the app (guardian service / accessibility service) is alive, [tick] subtracts the
 *    real monotonic delta (`elapsedRealtime`) from the stored remainder.
 *  - [remainingMillis] projects the honest advance between ticks so displays stay exact.
 *  - Across a reboot (elapsed clock resets), downtime is charged via [TamperProofClock], whose
 *    estimate a manual date change cannot move — so "set the date forward" can never drain the
 *    ledger, and "set the date back" can never refund it.
 *  - [KEY_UNTIL] is still mirrored purely for legacy display code and plays no role in any
 *    active/expired decision.
 */
object ProtectionCommitmentStore {

    private const val PREFS = "protection_commitment"
    private const val KEY_START = "commitment_start"
    private const val KEY_UNTIL = "commitment_until"
    private const val KEY_STOPPED = "protection_stopped"
    private const val KEY_STRONG_ACTIVE = "strong_protection_active"

    /** Accumulative ledger: remaining millis, decremented by real elapsed time only. */
    private const val KEY_REMAINING_MS = "commitment_remaining_ms"

    /** Monotonic stamp of the last ledger tick (this boot). */
    private const val KEY_LAST_TICK_ELAPSED = "commitment_last_tick_elapsed"

    /** Honest wall-clock stamp of the last ledger tick (covers reboot downtime). */
    private const val KEY_LAST_TICK_REAL = "commitment_last_tick_real"

    /** Sentinel representing a 5-minute test option. */
    const val TEST_5_MINUTES = -5

    /** Sentinel meaning the commitment never ends. */
    const val FOREVER = -1L

    /** No commitment configured. */
    const val NONE = 0L

    /** 5 minutes in milliseconds. */
    const val FIVE_MINUTES_MILLIS = 5L * 60L * 1000L

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Standard list of options: starts with the 5 minutes test option, then the short
     * commitments (1, 3, 7 and 14 days) added before the 30-day step, followed by the
     * longer day increments (30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 360).
     */
    val NORMAL_DAY_OPTIONS: List<Int> = listOf(
        TEST_5_MINUTES,
        1, 3, 7, 14,
        30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 360
    )

    /**
     * Strong Protection options: same sequence and order as Normal Protection,
     * starting with 5 minutes test, then 1, 3, 7 and 14 days, but strictly WITHOUT Forever.
     */
    val STRONG_DAY_OPTIONS: List<Int> = listOf(
        TEST_5_MINUTES,
        1, 3, 7, 14,
        30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330, 360
    )

    /** Backward-compatible alias for day options. */
    val DAY_OPTIONS: List<Int> = NORMAL_DAY_OPTIONS

    /** Calculates duration in milliseconds for an option ([TEST_5_MINUTES] or positive number of days). */
    fun durationMillisForOption(option: Int): Long = when (option) {
        TEST_5_MINUTES -> FIVE_MINUTES_MILLIS
        else -> option.toLong().coerceAtLeast(0L) * DAY_MILLIS
    }

    /** Persists a Strong Protection commitment of [option] (either [TEST_5_MINUTES] or number of days). */
    fun setStrongDays(context: Context, option: Int) {
        val now = TamperProofClock.now(context)
        val duration = durationMillisForOption(option)
        prefs(context).edit()
            .putLong(KEY_START, now)
            .putLong(KEY_UNTIL, now + duration)
            .putLong(KEY_REMAINING_MS, duration)
            .putLong(KEY_LAST_TICK_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_TICK_REAL, now)
            .putBoolean(KEY_STRONG_ACTIVE, true)
            .putBoolean(KEY_STOPPED, false)
            .apply()
    }

    /** Persists a Normal Protection commitment of [option] (either [TEST_5_MINUTES] or number of days). */
    fun setNormalDays(context: Context, option: Int) {
        val now = TamperProofClock.now(context)
        val duration = durationMillisForOption(option)
        prefs(context).edit()
            .putLong(KEY_START, now)
            .putLong(KEY_UNTIL, now + duration)
            .putLong(KEY_REMAINING_MS, duration)
            .putLong(KEY_LAST_TICK_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_TICK_REAL, now)
            .putBoolean(KEY_STRONG_ACTIVE, false)
            .putBoolean(KEY_STOPPED, false)
            .apply()
    }

    /** Persists a permanent normal commitment. */
    fun setNormalForever(context: Context) {
        val now = TamperProofClock.now(context)
        prefs(context).edit()
            .putLong(KEY_START, now)
            .putLong(KEY_UNTIL, FOREVER)
            .remove(KEY_REMAINING_MS)
            .remove(KEY_LAST_TICK_ELAPSED)
            .remove(KEY_LAST_TICK_REAL)
            .putBoolean(KEY_STRONG_ACTIVE, false)
            .putBoolean(KEY_STOPPED, false)
            .apply()
    }

    /** Persists a commitment of [days] days from now and clears any "stopped" state. */
    fun setDays(context: Context, days: Int) {
        setNormalDays(context, days)
    }

    /** Persists a permanent commitment and clears any "stopped" state. */
    fun setForever(context: Context) {
        setNormalForever(context)
    }

    /** The commitment end timestamp, [FOREVER] for permanent, or [NONE] when none was set. */
    fun commitmentUntil(context: Context): Long =
        prefs(context).getLong(KEY_UNTIL, NONE)

    /** The commitment start timestamp, or [NONE] when none was set. */
    fun commitmentStart(context: Context): Long =
        prefs(context).getLong(KEY_START, NONE)

    /** True when a permanent commitment is configured. */
    fun isForever(context: Context): Boolean = commitmentUntil(context) == FOREVER

    /** True when any commitment (timed or permanent) has been configured. */
    fun hasCommitment(context: Context): Boolean = commitmentUntil(context) != NONE

    /** True while a commitment is still active (permanent, or its ledger still holds time). */
    fun isActive(context: Context): Boolean {
        if (isForever(context)) return true
        if (!hasCommitment(context)) return false
        return remainingMillis(context) > 0L
    }

    /** True when Strong Protection is armed and its ledger still holds time. */
    fun isStrongActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_STRONG_ACTIVE, false)) return false
        val until = p.getLong(KEY_UNTIL, NONE)
        if (until <= NONE || until == FOREVER) return false
        return remainingMillis(context) > 0L
    }

    /**
     * Milliseconds left in a timed commitment — projected exactly between service ticks from the
     * honest elapsed delta. 0 when expired/none, [Long.MAX_VALUE] for forever.
     */
    @Synchronized
    fun remainingMillis(context: Context): Long {
        val p = prefs(context)
        val until = p.getLong(KEY_UNTIL, NONE)
        if (until == FOREVER) return Long.MAX_VALUE
        if (until <= NONE) return 0L
        ensureLedger(p, context, until)
        val stored = p.getLong(KEY_REMAINING_MS, 0L)
        if (stored <= 0L) return 0L
        val delta = honestDeltaMillis(p, SystemClock.elapsedRealtime(), TamperProofClock.now(context))
        return (stored - delta).coerceAtLeast(0L)
    }

    /** True when a TIMED commitment's ledger has genuinely run dry (needs the continue/stop ask). */
    fun isExpired(context: Context): Boolean {
        val p = prefs(context)
        val until = p.getLong(KEY_UNTIL, NONE)
        if (until <= NONE || until == FOREVER) return false
        return remainingMillis(context) <= 0L
    }

    /** Clears the commitment entirely (no countdown shown). */
    fun clearCommitment(context: Context) {
        prefs(context).edit()
            .putLong(KEY_UNTIL, NONE)
            .putLong(KEY_START, NONE)
            .remove(KEY_REMAINING_MS)
            .remove(KEY_LAST_TICK_ELAPSED)
            .remove(KEY_LAST_TICK_REAL)
            .putBoolean(KEY_STRONG_ACTIVE, false)
            .apply()
    }

    // -------------------------------------------------------------------------------------------
    // Accumulative tick: called by the guardian service loop (every 8s) and the accessibility
    // 1-second checkpoint. The ledger only ever shrinks by honestly elapsed time.
    // -------------------------------------------------------------------------------------------

    @Synchronized
    fun tick(context: Context) {
        val p = prefs(context)
        val until = p.getLong(KEY_UNTIL, NONE)
        if (until <= NONE || until == FOREVER) return
        ensureLedger(p, context, until)
        val remaining = p.getLong(KEY_REMAINING_MS, 0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        val honestNow = TamperProofClock.now(context)
        val delta = honestDeltaMillis(p, elapsedNow, honestNow)
        val editor = p.edit()
        if (delta > 0L) {
            editor.putLong(KEY_REMAINING_MS, (remaining - delta).coerceAtLeast(0L))
        }
        editor
            .putLong(KEY_LAST_TICK_ELAPSED, elapsedNow)
            .putLong(KEY_LAST_TICK_REAL, honestNow)
            .apply()
    }

    /**
     * One-time migration from the legacy wall-clock end timestamp ([KEY_UNTIL]) to the
     * remaining-seconds ledger, computed against the tamper-proof clock so an already-moved
     * device date cannot extend or end the commitment at upgrade time.
     */
    private fun ensureLedger(p: SharedPreferences, context: Context, until: Long) {
        if (p.contains(KEY_REMAINING_MS)) return
        val now = TamperProofClock.now(context)
        val remaining = (until - now).coerceAtLeast(0L)
        p.edit()
            .putLong(KEY_REMAINING_MS, remaining)
            .putLong(KEY_LAST_TICK_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_TICK_REAL, now)
            .apply()
    }

    /**
     * Honestly elapsed milliseconds since the last stored tick. Same boot → the pure monotonic
     * delta; after a reboot ([SystemClock.elapsedRealtime] reset) downtime is charged from the
     * tamper-proof wall clock, which a manual date change cannot move either direction.
     */
    private fun honestDeltaMillis(p: SharedPreferences, elapsedNow: Long, honestNow: Long): Long {
        val lastElapsed = p.getLong(KEY_LAST_TICK_ELAPSED, elapsedNow)
        val lastReal = p.getLong(KEY_LAST_TICK_REAL, honestNow)
        var delta = elapsedNow - lastElapsed
        if (delta < 0L) {
            delta = (honestNow - lastReal).coerceAtLeast(0L)
        }
        return delta.coerceAtLeast(0L)
    }

    // ---- Stopped state (user chose to stop protection after the commitment ended) ------------

    fun isStopped(context: Context): Boolean = prefs(context).getBoolean(KEY_STOPPED, false)

    fun setStopped(context: Context, stopped: Boolean) {
        prefs(context).edit().putBoolean(KEY_STOPPED, stopped).apply()
    }

    /** Formats remaining milliseconds for Strong Protection into a readable localized string. */
    fun formatStrongRemaining(remainingMillis: Long, language: String): String {
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L

        return when (language) {
            "en" -> {
                if (days > 0) "$days d $hours h $minutes m"
                else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
                else String.format("%02d:%02d", minutes, seconds)
            }
            "ar" -> {
                if (days > 0) "$days يوم و $hours ساعة و $minutes دقيقة"
                else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
                else String.format("%02d:%02d", minutes, seconds)
            }
            else -> {
                if (days > 0) "$days ڕۆژ و $hours کاتژمێر و $minutes خولەک"
                else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
                else String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    /** Formats option into readable localized chip label. */
    fun formatOptionLabel(option: Int, language: String): String {
        return if (option == TEST_5_MINUTES) {
            when (language) {
                "en" -> "5 mins (Test)"
                "ar" -> "5 دقائق (تجربة)"
                else -> "٥ خولەک (تێست)"
            }
        } else {
            when (language) {
                "en" -> "$option days"
                "ar" -> "$option يوم"
                else -> "$option ڕۆژ"
            }
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
