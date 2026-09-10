package com.agon.app.vpn

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.agon.app.security.TamperProofClock

/**
 * Independent, tamper-proof timer for **درع التصفح الآمن / Safe Browsing VPN only**.
 *
 * Completely decoupled from Standard / Strong protection commitments
 * ([com.agon.app.settingsprotection.data.ProtectionCommitmentStore]).
 */
object CommitmentTimerModule {

    private const val PREFS = "safe_browsing_commitment_v1"
    private const val KEY_REMAINING_MS = "remaining_ms"
    private const val KEY_LAST_TICK_ELAPSED = "last_tick_elapsed"
    private const val KEY_LAST_TICK_REAL = "last_tick_real"
    private const val KEY_STARTED_AT = "started_at"
    private const val KEY_OPTION_ID = "option_id"
    private const val KEY_ACTIVE = "active"

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MIN_MS = 60L * 1000L

    /** 5 minutes → 365 days (VPN-only lock options). */
    enum class DurationOption(val id: String, val millis: Long) {
        MINUTES_5("5m", 5L * MIN_MS),
        DAY_2("2d", 2L * DAY_MS),
        DAY_3("3d", 3L * DAY_MS),
        DAY_4("4d", 4L * DAY_MS),
        DAY_5("5d", 5L * DAY_MS),
        DAY_6("6d", 6L * DAY_MS),
        DAY_7("7d", 7L * DAY_MS),
        DAY_8("8d", 8L * DAY_MS),
        DAY_9("9d", 9L * DAY_MS),
        DAY_10("10d", 10L * DAY_MS),
        DAY_11("11d", 11L * DAY_MS),
        DAY_12("12d", 12L * DAY_MS),
        DAY_13("13d", 13L * DAY_MS),
        DAY_14("14d", 14L * DAY_MS),
        DAY_15("15d", 15L * DAY_MS),
        MONTH_1("30d", 30L * DAY_MS),
        DAY_60("60d", 60L * DAY_MS),
        DAY_90("90d", 90L * DAY_MS),
        DAY_120("120d", 120L * DAY_MS),
        DAY_160("160d", 160L * DAY_MS),
        DAY_200("200d", 200L * DAY_MS),
        DAY_250("250d", 250L * DAY_MS),
        DAY_300("300d", 300L * DAY_MS),
        DAY_365("365d", 365L * DAY_MS),
    }

    val ALL_OPTIONS: List<DurationOption> = DurationOption.entries

    fun label(option: DurationOption, language: String): String {
        if (option == DurationOption.MINUTES_5) {
            return when (language) {
                "en" -> "5 minutes"
                "ar" -> "5 دقائق"
                else -> "٥ خولەک"
            }
        }
        if (option == DurationOption.MONTH_1) {
            return when (language) {
                "en" -> "1 month"
                "ar" -> "شهر واحد"
                else -> "١ مانگ"
            }
        }
        val days = (option.millis / DAY_MS).toInt()
        return when (language) {
            "en" -> "$days days"
            "ar" -> "$days يوم"
            else -> "$days ڕۆژ"
        }
    }

    @Synchronized
    fun start(context: Context, option: DurationOption): Boolean {
        if (isActive(context)) return false
        val now = TamperProofClock.now(context)
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putString(KEY_OPTION_ID, option.id)
            .putLong(KEY_REMAINING_MS, option.millis)
            .putLong(KEY_STARTED_AT, now)
            .putLong(KEY_LAST_TICK_ELAPSED, SystemClock.elapsedRealtime())
            .putLong(KEY_LAST_TICK_REAL, now)
            .apply()
        return true
    }

    @Synchronized
    fun isActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        return remainingMillis(context) > 0L
    }

    @Synchronized
    fun remainingMillis(context: Context): Long {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return 0L
        val stored = p.getLong(KEY_REMAINING_MS, 0L)
        if (stored <= 0L) {
            clearExpired(p)
            return 0L
        }
        val delta = honestDeltaMillis(p, SystemClock.elapsedRealtime(), TamperProofClock.now(context))
        return (stored - delta).coerceAtLeast(0L)
    }

    @Synchronized
    fun tick(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return
        val remaining = p.getLong(KEY_REMAINING_MS, 0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        val honestNow = TamperProofClock.now(context)
        val delta = honestDeltaMillis(p, elapsedNow, honestNow)
        val next = (remaining - delta).coerceAtLeast(0L)
        val editor = p.edit()
            .putLong(KEY_REMAINING_MS, next)
            .putLong(KEY_LAST_TICK_ELAPSED, elapsedNow)
            .putLong(KEY_LAST_TICK_REAL, honestNow)
        if (next <= 0L) editor.putBoolean(KEY_ACTIVE, false)
        editor.apply()
    }

    fun formatRemaining(remainingMillis: Long, language: String): String {
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val days = totalSeconds / 86400L
        val hours = (totalSeconds % 86400L) / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when (language) {
            "en" -> if (days > 0) "$days d $hours h $minutes m"
            else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else String.format("%02d:%02d", minutes, seconds)
            "ar" -> if (days > 0) "$days يوم و $hours س و $minutes د"
            else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else String.format("%02d:%02d", minutes, seconds)
            else -> if (days > 0) "$days ڕۆژ و $hours ک و $minutes خ"
            else if (hours > 0) String.format("%02d:%02d:%02d", hours, minutes, seconds)
            else String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun clearExpired(p: SharedPreferences) {
        p.edit().putBoolean(KEY_ACTIVE, false).putLong(KEY_REMAINING_MS, 0L).apply()
    }

    private fun honestDeltaMillis(p: SharedPreferences, elapsedNow: Long, honestNow: Long): Long {
        val lastElapsed = p.getLong(KEY_LAST_TICK_ELAPSED, elapsedNow)
        val lastReal = p.getLong(KEY_LAST_TICK_REAL, honestNow)
        var delta = elapsedNow - lastElapsed
        if (delta < 0L) delta = (honestNow - lastReal).coerceAtLeast(0L)
        return delta.coerceAtLeast(0L)
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
