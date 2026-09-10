package com.agon.app.timelimit

import android.content.Context
import com.agon.app.security.TamperProofClock
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.ceil

/**
 * Per-app daily time limits with automatic midnight reset.
 *
 * Usage is persisted in milliseconds, rather than rounded minutes, so short foreground sessions
 * accumulate accurately and a limit is enforced as soon as its full duration is consumed.
 */
object AppTimeLimitStore {

    private const val PREFS = "family_shield_time_limits"
    private const val KEY_LIMITS = "limits_json"
    private const val KEY_USAGE = "usage_json"
    private const val KEY_USAGE_MILLIS = "usage_millis_json"
    private const val KEY_USAGE_DAY = "usage_day"
    private const val KEY_CREATED_AT = "limits_created_at_json"

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var appContext: Context

    @Synchronized
    fun initialize(context: Context) {
        if (::prefs.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Device-clock-independent "now": manual date changes cannot skip the limit day. */
    private fun trustedNow(): Long =
        if (::appContext.isInitialized) TamperProofClock.now(appContext) else System.currentTimeMillis()

    private fun ensure(): Boolean = ::prefs.isInitialized

    /** Includes the year so a stale ledger can never be reused on the same day next year. */
    private fun today(): Int {
        // Anchored to the tamper-proof clock: setting the device to tomorrow must not reset
        // the day's usage ledger, and moving it to yesterday must not extend it.
        val calendar = Calendar.getInstance().apply { timeInMillis = trustedNow() }
        return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
    }

    /** Configured daily budgets, keyed by package name (minutes). Empty when none set. */
    @Synchronized
    fun limits(): Map<String, Int> {
        if (!ensure()) return emptyMap()
        val raw = prefs.getString(KEY_LIMITS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.getInt(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    /** Exact milliseconds consumed today per package, auto-reset when the day changes. */
    @Synchronized
    fun usageMillisToday(): Map<String, Long> {
        if (!ensure() || prefs.getInt(KEY_USAGE_DAY, -1) != today()) return emptyMap()
        val exact = prefs.getString(KEY_USAGE_MILLIS, null)
        if (exact != null) {
            return runCatching {
                val json = JSONObject(exact)
                buildMap {
                    json.keys().forEach { key -> put(key, json.getLong(key).coerceAtLeast(0L)) }
                }
            }.getOrDefault(emptyMap())
        }

        // One-time migration from the previous whole-minute ledger.
        val legacy = prefs.getString(KEY_USAGE, null) ?: return emptyMap()
        val migrated = runCatching {
            val json = JSONObject(legacy)
            buildMap {
                json.keys().forEach { key -> put(key, json.getLong(key).coerceAtLeast(0L) * MINUTE_MS) }
            }
        }.getOrDefault(emptyMap())
        if (migrated.isNotEmpty()) writeUsage(migrated)
        return migrated
    }

    /** Whole minutes shown in the UI; any started minute is visible instead of being discarded. */
    @Synchronized
    fun usageToday(): Map<String, Int> = usageMillisToday().mapValues { (_, millis) ->
        ceil(millis / MINUTE_MS.toDouble()).toInt()
    }

    /** Wall-clock epoch millis when each limit was first created, keyed by package name. */
    @Synchronized
    fun createdAtMap(): Map<String, Long> {
        if (!ensure()) return emptyMap()
        val raw = prefs.getString(KEY_CREATED_AT, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap { json.keys().forEach { key -> put(key, json.getLong(key)) } }
        }.getOrDefault(emptyMap())
    }

    /** Creation time of one limit, or 0 when it is unknown / not set. */
    @Synchronized
    fun createdAt(packageName: String): Long = createdAtMap()[packageName] ?: 0L

    /**
     * Remaining millis of the 48h delete cool-down for [packageName], or 0 once elapsed.
     * Compared against the real clock so it is precise to the second and survives restarts.
     */
    @Synchronized
    fun deleteLockRemaining(
        packageName: String,
        nowMillis: Long = trustedNow(),
    ): Long {
        val created = createdAt(packageName)
        if (created <= 0L) return 0L
        return (created + EDIT_LOCK_MILLIS - nowMillis).coerceAtLeast(0L)
    }

    /** True while [packageName] is still inside its 48h delete cool-down. */
    @Synchronized
    fun isDeleteLocked(
        packageName: String,
        nowMillis: Long = trustedNow(),
    ): Boolean = deleteLockRemaining(packageName, nowMillis) > 0L

    /** Sets or clears (limit <= 0 removes) the daily budget for [packageName]. */
    @Synchronized
    fun setLimit(packageName: String, minutes: Int) {
        if (!ensure()) return
        val current = limits().toMutableMap()
        val created = createdAtMap().toMutableMap()
        if (minutes <= 0) {
            // Enforce the 48h cool-down at the data layer: a still-locked limit cannot be removed
            // even if a caller bypasses the UI guard.
            if (isDeleteLocked(packageName)) return
            current.remove(packageName)
            created.remove(packageName)
        } else {
            current[packageName] = minutes
            // Stamp creation only on first creation; editing the minutes keeps the original anchor.
            if (!created.containsKey(packageName)) created[packageName] = trustedNow()
        }
        prefs.edit()
            .putString(KEY_LIMITS, JSONObject(current as Map<*, *>).toString())
            .putString(KEY_CREATED_AT, JSONObject(created as Map<*, *>).toString())
            .apply()
    }

    /** Exact consumed milliseconds for one app today. */
    @Synchronized
    fun usedMillis(packageName: String): Long = usageMillisToday()[packageName] ?: 0L

    /** Remaining milliseconds; null when the app has no configured limit. */
    @Synchronized
    fun remainingMillis(packageName: String): Long? {
        val limit = limits()[packageName] ?: return null
        return (limit * MINUTE_MS - usedMillis(packageName)).coerceAtLeast(0L)
    }

    /** Remaining display minutes, rounded up so partial available minutes are not hidden. */
    @Synchronized
    fun remainingMinutes(packageName: String): Int? = remainingMillis(packageName)?.let { millis ->
        ceil(millis / MINUTE_MS.toDouble()).toInt()
    }

    /** True when the app has a limit and today's budget is fully spent. */
    @Synchronized
    fun isExhausted(packageName: String): Boolean = remainingMillis(packageName)?.let { it <= 0L } ?: false

    /** Adds exact foreground usage for today. Resets the ledger at midnight. */
    @Synchronized
    fun addUsageMillis(packageName: String, millis: Long) {
        if (!ensure() || millis <= 0L || limits()[packageName] == null) return
        val day = today()
        val usage = if (prefs.getInt(KEY_USAGE_DAY, -1) == day) {
            usageMillisToday().toMutableMap()
        } else {
            mutableMapOf()
        }
        usage[packageName] = (usage[packageName] ?: 0L) + millis
        writeUsage(usage, day)
    }

    /** Clears today's consumed time for one app after credential verification. */
    @Synchronized
    fun resetUsage(packageName: String) {
        if (!ensure()) return
        val usage = usageMillisToday().toMutableMap()
        usage.remove(packageName)
        writeUsage(usage)
    }

    @Synchronized
    private fun writeUsage(usage: Map<String, Long>, day: Int = today()) {
        prefs.edit()
            .putInt(KEY_USAGE_DAY, day)
            .putString(KEY_USAGE_MILLIS, JSONObject(usage as Map<*, *>).toString())
            .remove(KEY_USAGE)
            .apply()
    }

    const val MINUTE_MS = 60_000L

    /** Same 48-hour cool-down window used by the block schedules. */
    val EDIT_LOCK_MILLIS: Long get() = BlockScheduleStore.EDIT_LOCK_MILLIS
}
