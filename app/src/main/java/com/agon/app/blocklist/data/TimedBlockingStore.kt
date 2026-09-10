package com.agon.app.blocklist.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.agon.app.blocklist.domain.BlockingRule
import com.agon.app.blocklist.domain.BlockingType
import com.agon.app.blocklist.domain.TrustedClock
import com.agon.app.blocklist.domain.TrustedClockSnapshot
import com.agon.app.services.KeepAliveReceiver
import org.json.JSONObject
import kotlin.math.abs

/**
 * Process-wide source of truth for every timed blocking rule. Persistence is a private
 * SharedPreferences file so state survives app restart, force-stop and reboot. All screens and
 * engines must go through this store for remaining time, lock and expiry.
 */
object TimedBlockingStore {

    const val TIME_TAMPER_DETECTED = "TIME_TAMPER_DETECTED"

    private const val PREFS = "timed_blocking_v1"
    private const val KEY_IDS = "rule_ids"
    private const val KEY_DONE = "completed_ids"
    private const val KEY_GLOBAL_WALL = "global_last_wall"
    private const val KEY_GLOBAL_ELAPSED = "global_last_elapsed"
    private const val KEY_GLOBAL_BOOT = "global_last_boot"
    private const val KEY_GLOBAL_TAMPER = "global_tamper"
    private const val DAY_MS = 86_400_000L
    private const val MAX_DURATION_MS = 400L * DAY_MS
    private const val RULE_PREFIX = "rule:"

    @Volatile private var cachedContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        cachedContext = context.applicationContext
        onProcessStart(context.applicationContext)
    }

    @Synchronized
    fun onProcessStart(context: Context) {
        val app = context.applicationContext
        cachedContext = app
        val now = TrustedClock.snapshot(app)
        detectGlobalTamper(app, now)
        // Advance every rule (elapsed only) and persist reboot / expiry / tamper transitions.
        ids(app).forEach { resolveLocked(app, it, now, persistAlways = false) }
        persistGlobal(app, now)
        TimedBlockingAlarms.reschedule(app)
    }

    /**
     * Called on ACTION_TIME_CHANGED / ACTION_DATE_CHANGED / ACTION_TIMEZONE_CHANGED.
     * Timezone changes do not affect epoch millis and are not tamper. A large wall/elapsed
     * split is TIME_TAMPER_DETECTED — blocking stays ON and countdown ignores the new wall clock.
     */
    @Synchronized
    fun onSystemClockSignal(context: Context, action: String?) {
        val app = context.applicationContext
        val now = TrustedClock.snapshot(app)
        val timezoneOnly = action == android.content.Intent.ACTION_TIMEZONE_CHANGED
        if (!timezoneOnly) detectGlobalTamper(app, now)
        ids(app).forEach { resolveLocked(app, it, now, persistAlways = false) }
        persistGlobal(app, now)
        TimedBlockingAlarms.reschedule(app)
    }

    @Synchronized
    fun start(
        context: Context,
        id: String,
        type: BlockingType,
        target: String,
        durationDays: Int,
    ): BlockingRule = startMs(
        context = context,
        id = id,
        type = type,
        target = target,
        durationMs = durationDays.coerceAtLeast(1).toLong() * DAY_MS,
    )

    /**
     * Starts a timed lock for an arbitrary positive duration in milliseconds (used by the
     * short "test" option as well as multi-day blocks).
     */
    @Synchronized
    fun startMs(
        context: Context,
        id: String,
        type: BlockingType,
        target: String,
        durationMs: Long,
    ): BlockingRule {
        val app = context.applicationContext
        val now = TrustedClock.snapshot(app)
        val ms = durationMs.coerceIn(1L, MAX_DURATION_MS)
        val rule = BlockingRule(
            id = id,
            type = type,
            target = target,
            enabled = true,
            durationMs = ms,
            startedAt = now.wallClock,
            remainingDurationMs = ms,
            lockedUntilExpiry = true,
            lastWallClock = now.wallClock,
            lastElapsedRealtime = now.elapsedRealtime,
            lastBootCount = now.bootCount,
            tamperDetected = isGlobalTamper(app),
        )
        save(app, rule)
        unmarkCompleted(app, id)
        persistGlobal(app, now)
        TimedBlockingAlarms.reschedule(app)
        return rule
    }

    /**
     * One-time adoption of a legacy wall-clock [blockUntil] so existing rules keep working after
     * the upgrade without using wall-clock for later enforcement.
     */
    @Synchronized
    fun adoptLegacy(
        context: Context,
        id: String,
        type: BlockingType,
        target: String,
        blockUntil: Long,
    ): BlockingRule? {
        val app = context.applicationContext
        if (isCompleted(app, id)) return null
        load(app, id)?.let { return resolveLocked(app, id, TrustedClock.snapshot(app), persistAlways = false) }
        if (blockUntil <= 0L) return null
        val now = TrustedClock.snapshot(app)
        val remaining = (blockUntil - now.wallClock).coerceIn(0L, MAX_DURATION_MS)
        if (remaining <= 0L) return null
        val rule = BlockingRule(
            id = id,
            type = type,
            target = target,
            enabled = true,
            durationMs = remaining,
            startedAt = now.wallClock,
            remainingDurationMs = remaining,
            lockedUntilExpiry = true,
            lastWallClock = now.wallClock,
            lastElapsedRealtime = now.elapsedRealtime,
            lastBootCount = now.bootCount,
            tamperDetected = isGlobalTamper(app),
        )
        save(app, rule)
        return rule
    }

    @Synchronized
    fun remainingMillis(context: Context, id: String): Long {
        val rule = resolve(context, id) ?: return 0L
        return if (rule.enabled && rule.lockedUntilExpiry) rule.remainingDurationMs else 0L
    }

    @Synchronized
    fun isLocked(context: Context, id: String): Boolean {
        val rule = resolve(context, id) ?: return false
        if (!rule.enabled || !rule.lockedUntilExpiry) return false
        // Tamper never unlocks a still-active rule. Expiry is only via remainingDuration <= 0
        // computed from elapsedRealtime.
        return rule.remainingDurationMs > 0L
    }

    @Synchronized
    fun isActive(context: Context, id: String): Boolean {
        val rule = resolve(context, id) ?: return false
        return rule.enabled && (rule.remainingDurationMs > 0L || !rule.lockedUntilExpiry)
    }

    @Synchronized
    fun has(context: Context, id: String): Boolean = load(context.applicationContext, id) != null

    @Synchronized
    fun resolve(context: Context, id: String): BlockingRule? {
        val app = context.applicationContext
        return resolveLocked(app, id, TrustedClock.snapshot(app), persistAlways = false)
    }

    /**
     * Refuses to turn a locked rule off. Returns false when the caller must keep the switch ON.
     */
    @Synchronized
    fun tryDisable(context: Context, id: String): Boolean {
        val app = context.applicationContext
        val rule = resolveLocked(app, id, TrustedClock.snapshot(app), persistAlways = false) ?: return true
        if (rule.enabled && rule.lockedUntilExpiry && rule.remainingDurationMs > 0L) return false
        save(app, rule.copy(enabled = false, lockedUntilExpiry = false, remainingDurationMs = 0L))
        TimedBlockingAlarms.reschedule(app)
        return true
    }

    @Synchronized
    fun clear(context: Context, id: String) {
        val app = context.applicationContext
        val rule = load(app, id) ?: return
        if (rule.enabled && rule.lockedUntilExpiry && rule.remainingDurationMs > 0L) return
        val prefs = prefs(app)
        val remaining = ids(app).toMutableSet().apply { remove(id) }
        val done = completedIds(app).toMutableSet().apply { add(id) }
        prefs.edit()
            .remove(RULE_PREFIX + id)
            .putStringSet(KEY_IDS, remaining)
            .putStringSet(KEY_DONE, done)
            .apply()
        TimedBlockingAlarms.reschedule(app)
    }

    /** Rules that just auto-expired this pass (remainingDuration <= 0). */
    @Synchronized
    fun consumeExpired(context: Context): List<BlockingRule> {
        val app = context.applicationContext
        val now = TrustedClock.snapshot(app)
        val expired = mutableListOf<BlockingRule>()
        ids(app).forEach { id ->
            val before = load(app, id) ?: return@forEach
            val after = resolveLocked(app, id, now, persistAlways = false) ?: return@forEach
            if (before.enabled && (!after.enabled || after.remainingDurationMs <= 0L)) {
                val done = after.copy(enabled = false, lockedUntilExpiry = false, remainingDurationMs = 0L)
                save(app, done)
                markCompleted(app, id)
                expired += done
            }
        }
        if (expired.isNotEmpty()) TimedBlockingAlarms.reschedule(app)
        return expired
    }

    @Synchronized
    fun checkpointAll(context: Context) {
        val app = context.applicationContext
        val now = TrustedClock.snapshot(app)
        detectGlobalTamper(app, now)
        ids(app).forEach { resolveLocked(app, it, now, persistAlways = true) }
        persistGlobal(app, now)
        TimedBlockingAlarms.reschedule(app)
    }

    fun isTamperDetected(context: Context): Boolean = isGlobalTamper(context.applicationContext)

    fun minRemainingMillis(context: Context): Long {
        val app = context.applicationContext
        var min = Long.MAX_VALUE
        ids(app).forEach { id ->
            val rem = remainingMillis(app, id)
            if (rem in 1 until min) min = rem
        }
        return if (min == Long.MAX_VALUE) 0L else min
    }

    // ------------------------------------------------------------------ internals

    private fun resolveLocked(
        app: Context,
        id: String,
        now: TrustedClockSnapshot,
        persistAlways: Boolean,
    ): BlockingRule? {
        val loaded = load(app, id) ?: return null
        val advanced = TrustedClock.advance(loaded, now)
        val bootChanged = !TrustedClock.isSameBoot(loaded, now)
        val expired = loaded.enabled && (!advanced.enabled || advanced.remainingDurationMs <= 0L)
        val tamperChanged = advanced.tamperDetected != loaded.tamperDetected
        if (persistAlways || bootChanged || expired || tamperChanged ||
            advanced.enabled != loaded.enabled
        ) {
            save(app, advanced)
        } else {
            // Keep lastElapsed on disk so a force-stop still subtracts the full elapsed gap.
            // In-memory callers get [advanced]; next process start uses disk lastElapsed.
        }
        return advanced
    }

    private fun load(app: Context, id: String): BlockingRule? {
        val raw = prefs(app).getString(RULE_PREFIX + id, null) ?: return null
        return decode(raw)
    }

    private fun save(app: Context, rule: BlockingRule) {
        val nextIds = ids(app).toMutableSet().apply { add(rule.id) }
        prefs(app).edit()
            .putString(RULE_PREFIX + rule.id, encode(rule))
            .putStringSet(KEY_IDS, nextIds)
            .apply()
    }

    private fun ids(app: Context): Set<String> =
        prefs(app).getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()

    private fun completedIds(app: Context): Set<String> =
        prefs(app).getStringSet(KEY_DONE, emptySet())?.toSet().orEmpty()

    private fun isCompleted(app: Context, id: String): Boolean = completedIds(app).contains(id)

    private fun markCompleted(app: Context, id: String) {
        val done = completedIds(app).toMutableSet().apply { add(id) }
        prefs(app).edit().putStringSet(KEY_DONE, done).apply()
    }

    private fun unmarkCompleted(app: Context, id: String) {
        val done = completedIds(app).toMutableSet().apply { remove(id) }
        prefs(app).edit().putStringSet(KEY_DONE, done).apply()
    }

    private fun prefs(app: Context) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun isGlobalTamper(app: Context) = prefs(app).getBoolean(KEY_GLOBAL_TAMPER, false)

    private fun detectGlobalTamper(app: Context, now: TrustedClockSnapshot) {
        val p = prefs(app)
        val lastWall = p.getLong(KEY_GLOBAL_WALL, 0L)
        val lastElapsed = p.getLong(KEY_GLOBAL_ELAPSED, 0L)
        val lastBoot = p.getLong(KEY_GLOBAL_BOOT, 0L)
        if (lastWall <= 0L || lastElapsed <= 0L) return
        val sameBoot = now.elapsedRealtime >= lastElapsed &&
            (lastBoot == 0L || now.bootCount == 0L || now.bootCount == lastBoot)
        if (!sameBoot) return
        val elapsedDelta = now.elapsedRealtime - lastElapsed
        val wallDelta = now.wallClock - lastWall
        if (abs(wallDelta - elapsedDelta) > TrustedClock.TAMPER_THRESHOLD_MS ||
            wallDelta < -TrustedClock.TAMPER_THRESHOLD_MS
        ) {
            p.edit().putBoolean(KEY_GLOBAL_TAMPER, true).apply()
        }
    }

    private fun persistGlobal(app: Context, now: TrustedClockSnapshot) {
        prefs(app).edit()
            .putLong(KEY_GLOBAL_WALL, now.wallClock)
            .putLong(KEY_GLOBAL_ELAPSED, now.elapsedRealtime)
            .putLong(KEY_GLOBAL_BOOT, now.bootCount)
            .apply()
    }

    private fun encode(rule: BlockingRule): String = JSONObject().apply {
        put("id", rule.id)
        put("type", rule.type.name)
        put("target", rule.target)
        put("enabled", rule.enabled)
        put("durationMs", rule.durationMs)
        put("startedAt", rule.startedAt)
        put("remainingDurationMs", rule.remainingDurationMs)
        put("lockedUntilExpiry", rule.lockedUntilExpiry)
        put("lastWallClock", rule.lastWallClock)
        put("lastElapsedRealtime", rule.lastElapsedRealtime)
        put("lastBootCount", rule.lastBootCount)
        put("tamperDetected", rule.tamperDetected)
    }.toString()

    private fun decode(raw: String): BlockingRule? = runCatching {
        val o = JSONObject(raw)
        BlockingRule(
            id = o.getString("id"),
            type = runCatching { BlockingType.valueOf(o.getString("type")) }.getOrDefault(BlockingType.KEYWORD),
            target = o.optString("target"),
            enabled = o.optBoolean("enabled", true),
            durationMs = o.optLong("durationMs"),
            startedAt = o.optLong("startedAt"),
            remainingDurationMs = o.optLong("remainingDurationMs"),
            lockedUntilExpiry = o.optBoolean("lockedUntilExpiry", true),
            lastWallClock = o.optLong("lastWallClock"),
            lastElapsedRealtime = o.optLong("lastElapsedRealtime"),
            lastBootCount = o.optLong("lastBootCount"),
            tamperDetected = o.optBoolean("tamperDetected", false),
        )
    }.getOrNull()
}

/**
 * Schedules an elapsedRealtime wake-up for the soonest expiry so a rule can auto-expire even
 * if the process was killed. Uses inexact / windowed alarms (no USE_EXACT_ALARM).
 */
object TimedBlockingAlarms {
    private const val REQUEST_CODE = 7204
    const val ACTION_EXPIRE = "com.agon.app.action.TIMED_BLOCK_EXPIRE"

    fun reschedule(context: Context) {
        val app = context.applicationContext
        val remaining = TimedBlockingStore.minRemainingMillis(app)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(app)
        if (remaining <= 0L) {
            runCatching { alarmManager.cancel(pending) }
            return
        }
        val triggerAt = SystemClock.elapsedRealtime() + remaining
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setWindow(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    60_000L,
                    pending,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_EXPIRE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
