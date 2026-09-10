package com.agon.app.motivation

import android.content.Context
import java.util.Calendar

/** Immutable snapshot of the user's protection streak. */
data class StreakInfo(
    val currentDays: Int,
    val bestDays: Int,
    val startMillis: Long,
)

/**
 * Persistent "protection streak" counter — the number of consecutive days since the user
 * started (or last restarted) their clean streak. Mirrors the lightweight SharedPreferences
 * pattern used by [com.agon.app.timelimit.BlockScheduleStore]; it holds no alarms, no
 * wake-locks and adds no battery cost. This is a motivational counter only and never touches
 * the blocking engine, PIN, VPN or any protection setting.
 */
object StreakStore {

    private const val PREFS = "family_shield_streak"
    private const val KEY_START_DAY = "streak_start_epoch_day_v1"
    private const val KEY_BEST_DAYS = "streak_best_days_v1"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        // First ever launch: the streak begins today.
        if (!store.contains(KEY_START_DAY)) {
            store.edit().putLong(KEY_START_DAY, todayEpochDay()).apply()
        }
    }

    /** Local-time day number (days since epoch); used only for day-to-day differences. */
    private fun todayEpochDay(): Long {
        val calendar = Calendar.getInstance()
        val offset = calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return (calendar.timeInMillis + offset) / 86_400_000L
    }

    /** Current snapshot, keeping the stored best streak in sync as the current one grows. */
    @Synchronized
    fun info(): StreakInfo {
        val store = prefs ?: return StreakInfo(0, 0, System.currentTimeMillis())
        val startDay = store.getLong(KEY_START_DAY, todayEpochDay())
        val current = (todayEpochDay() - startDay).toInt().coerceAtLeast(0)
        var best = store.getInt(KEY_BEST_DAYS, 0)
        if (current > best) {
            best = current
            store.edit().putInt(KEY_BEST_DAYS, best).apply()
        }
        return StreakInfo(current, best, startDay * 86_400_000L)
    }

    /** Restarts the streak today, preserving the best streak ever achieved. */
    @Synchronized
    fun restart() {
        val store = prefs ?: return
        val current = (todayEpochDay() - store.getLong(KEY_START_DAY, todayEpochDay())).toInt().coerceAtLeast(0)
        val best = maxOf(store.getInt(KEY_BEST_DAYS, 0), current)
        store.edit()
            .putLong(KEY_START_DAY, todayEpochDay())
            .putInt(KEY_BEST_DAYS, best)
            .apply()
    }

    /** Milestone ladder (in days) used to unlock achievements on the motivation card. */
    val MILESTONES: List<Int> = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365)
}
