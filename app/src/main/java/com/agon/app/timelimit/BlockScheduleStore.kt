package com.agon.app.timelimit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * One recurring blocking window (e.g. bedtime 22:00–07:00).
 *
 * Times are stored as minutes since midnight. [days] holds [Calendar.DAY_OF_WEEK]
 * values (Sunday = 1 … Saturday = 7). Overnight windows (start > end) are supported: the
 * evening half belongs to the start day, the morning half to the day after.
 */
data class BlockSchedule(
    val id: Long,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int>,
    val enabled: Boolean = true,
    /**
     * Wall-clock epoch millis when this schedule was first created. Used to enforce the
     * 48-hour cool-down during which the schedule cannot be edited or deleted. Persisted so
     * the rule survives app restarts and reboots, and compared against the real current time
     * (never UI state), so it is precise to the second.
     */
    val createdAtMillis: Long = 0L,
)

/**
 * Persistent store for the user's blocking schedules.
 *
 * Follows the same lightweight SharedPreferences + JSON pattern as [AppTimeLimitStore]:
 * the accessibility service reads [activeScheduleNow] on its existing 1-second checkpoint,
 * so enforcement needs no alarms, no wake-locks and no extra battery cost.
 */
object BlockScheduleStore {

    private var appContext: Context? = null

    /** Device-clock-independent "now" for durations/cool-downs (scheduler windows stay local-clock). */
    private fun trustedNow(): Long =
        appContext?.let { com.agon.app.security.TamperProofClock.now(it) } ?: System.currentTimeMillis()

    private const val PREFS = "family_shield_block_schedules"
    private const val KEY_SCHEDULES = "schedules_json_v1"

    private const val FIELD_ID = "id"
    private const val FIELD_NAME = "name"
    private const val FIELD_START = "start"
    private const val FIELD_END = "end"
    private const val FIELD_DAYS = "days"
    private const val FIELD_ENABLED = "enabled"
    private const val FIELD_CREATED_AT = "createdAt"

    /** Hard cap on how long a single blocking window may last (10 hours). */
    const val MAX_DURATION_MINUTES = 10 * 60

    /**
     * Cool-down after a schedule is created during which it cannot be edited or deleted,
     * so a moment of weakness cannot dismantle a freshly-set protection. Exactly 48 hours,
     * measured to the millisecond against the real clock.
     */
    const val EDIT_LOCK_MILLIS = 48L * 60L * 60L * 1000L

    // ---- one-shot "urgent craving" block --------------------------------------------------
    /** Synthetic id used by the one-shot urge block; never collides with real schedules. */
    const val URGE_SCHEDULE_ID = -1L
    const val URGE_MIN_MINUTES = 3
    const val URGE_MAX_MINUTES = 30
    const val URGE_DEFAULT_MINUTES = 30
    private const val KEY_URGE_UNTIL = "urge_block_until_v1"
    private const val KEY_URGE_NAME = "urge_block_name_v1"

    /** Window length in minutes, correctly handling overnight windows (start > end). */
    fun durationMinutes(startMinutes: Int, endMinutes: Int): Int =
        (endMinutes - startMinutes + 1440) % 1440

    /**
     * Epoch millis at which [schedule] becomes editable/deletable: creation time + 48h.
     * A value of 0 for [BlockSchedule.createdAtMillis] (should not happen for real rows) is
     * treated as "created now" so the lock still applies rather than being bypassed.
     */
    fun editUnlockAt(schedule: BlockSchedule): Long {
        val created = if (schedule.createdAtMillis > 0L) schedule.createdAtMillis else schedule.id
        return created + EDIT_LOCK_MILLIS
    }

    /** Remaining millis of the 48h cool-down, or 0 once it has fully elapsed. */
    fun editLockRemaining(schedule: BlockSchedule, nowMillis: Long = trustedNow()): Long =
        (editUnlockAt(schedule) - nowMillis).coerceAtLeast(0L)

    /**
     * True while the schedule may NOT be edited or deleted: either it is still inside its 48h
     * creation cool-down, or it is actively blocking at this very moment (so an edit cannot cut
     * a running window short and bypass protection).
     */
    fun isEditLocked(schedule: BlockSchedule, nowMillis: Long = trustedNow()): Boolean =
        editLockRemaining(schedule, nowMillis) > 0L ||
            (schedule.enabled && isActiveAt(schedule, nowMillis))

    /** Shortens any window longer than [MAX_DURATION_MINUTES] so the cap can never be bypassed. */
    private fun clampToMaxDuration(schedule: BlockSchedule): BlockSchedule =
        if (durationMinutes(schedule.startMinutes, schedule.endMinutes) > MAX_DURATION_MINUTES) {
            schedule.copy(endMinutes = (schedule.startMinutes + MAX_DURATION_MINUTES) % 1440)
        } else {
            schedule
        }

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isInitialized(): Boolean = prefs != null

    @Synchronized
    fun schedules(): List<BlockSchedule> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_SCHEDULES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val daysJson = item.optJSONArray(FIELD_DAYS)
                    val days = buildSet {
                        if (daysJson != null) {
                            for (dayIndex in 0 until daysJson.length()) add(daysJson.getInt(dayIndex))
                        }
                    }
                    add(
                        clampToMaxDuration(
                            BlockSchedule(
                                id = item.getLong(FIELD_ID),
                                name = item.optString(FIELD_NAME),
                                startMinutes = item.getInt(FIELD_START).coerceIn(0, 1439),
                                endMinutes = item.getInt(FIELD_END).coerceIn(0, 1439),
                                days = days.ifEmpty { (1..7).toSet() },
                                enabled = item.optBoolean(FIELD_ENABLED, true),
                                createdAtMillis = item.optLong(
                                    FIELD_CREATED_AT,
                                    // Legacy rows have no stored timestamp: fall back to the id,
                                    // which is the creation-time millis for schedules made by upsert().
                                    item.getLong(FIELD_ID),
                                ),
                            ),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    /** Creates (id == 0L) or updates a schedule. */
    @Synchronized
    fun upsert(schedule: BlockSchedule) {
        val current = schedules()
        // Editing an existing schedule is refused while it is still inside its 48h
        // cool-down, mirroring the UI guard so protection cannot be weakened early.
        if (schedule.id != 0L) {
            val existing = current.firstOrNull { it.id == schedule.id }
            if (existing != null && isEditLocked(existing)) return
        }
        val withId = clampToMaxDuration(
            if (schedule.id == 0L) {
                val now = trustedNow()
                schedule.copy(id = now, createdAtMillis = now)
            } else {
                // Preserve the original creation time on edits so the 48h window is anchored
                // to first creation, never reset by a later save.
                val original = current.firstOrNull { it.id == schedule.id }
                schedule.copy(
                    createdAtMillis = when {
                        schedule.createdAtMillis > 0L -> schedule.createdAtMillis
                        original != null -> original.createdAtMillis
                        else -> schedule.id
                    },
                )
            },
        )
        val next = current.filterNot { it.id == withId.id } + withId
        persist(next)
    }

    @Synchronized
    fun remove(id: Long) {
        val current = schedules()
        val target = current.firstOrNull { it.id == id }
        // Enforce the 48h cool-down at the data layer too, so the rule cannot be bypassed
        // even if a caller skips the UI guard.
        if (target != null && isEditLocked(target)) return
        persist(current.filterNot { it.id == id })
    }

    @Synchronized
    fun setEnabled(id: Long, enabled: Boolean) {
        persist(schedules().map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    /**
     * Starts the one-shot "urgent craving" block for [durationMinutes] (clamped to 3..30).
     * Enforcement rides the existing 1-second schedule checkpoint: no alarms needed, and the
     * block ends automatically the moment the deadline passes.
     */
    @Synchronized
    fun startUrgeBlock(durationMinutes: Int, displayName: String) {
        val minutes = durationMinutes.coerceIn(URGE_MIN_MINUTES, URGE_MAX_MINUTES)
        prefs?.edit()
            ?.putLong(KEY_URGE_UNTIL, trustedNow() + minutes * 60_000L)
            ?.putString(KEY_URGE_NAME, displayName)
            ?.apply()
    }

    /** Epoch millis when the urge block ends, or 0 when none is active. */
    fun urgeBlockUntil(): Long {
        val until = prefs?.getLong(KEY_URGE_UNTIL, 0L) ?: 0L
        return if (until > trustedNow()) until else 0L
    }

    private fun urgeScheduleOrNull(nowMillis: Long): BlockSchedule? {
        val store = prefs ?: return null
        val until = store.getLong(KEY_URGE_UNTIL, 0L)
        if (until <= nowMillis) return null
        val name = store.getString(KEY_URGE_NAME, null).orEmpty()
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val startOfDay = calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return BlockSchedule(
            id = URGE_SCHEDULE_ID,
            name = name,
            startMinutes = (((nowMillis - startOfDay) / 60_000L).toInt()).coerceIn(0, 1439),
            endMinutes = (((until - startOfDay) / 60_000L).toInt() % 1440).coerceIn(0, 1439),
            days = (1..7).toSet(),
        )
    }

    /** True when [schedule] covers the given moment. */
    fun isActiveAt(schedule: BlockSchedule, nowMillis: Long): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return covers(schedule, dayOfWeek, minuteOfDay)
    }

    /**
     * Epoch millis of the next time [schedule] starts, strictly after [nowMillis].
     * Scans at most 8 days ahead, which always contains the next weekly occurrence.
     */
    fun nextStartMillis(schedule: BlockSchedule, nowMillis: Long): Long {
        val base = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        for (offset in 0..7) {
            val day = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (day.get(Calendar.DAY_OF_WEEK) !in schedule.days) continue
            val start = day.timeInMillis + schedule.startMinutes * 60_000L
            if (start > nowMillis) return start
        }
        return 0L
    }

    /**
     * Epoch millis when the currently running window of [schedule] ends. Only meaningful when
     * [isActiveAt] is true; handles overnight windows across midnight.
     */
    fun activeEndMillis(schedule: BlockSchedule, nowMillis: Long): Long {
        if (schedule.id == URGE_SCHEDULE_ID) {
            val until = prefs?.getLong(KEY_URGE_UNTIL, 0L) ?: 0L
            if (until > nowMillis) return until
        }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val minuteOfDay = ((nowMillis - calendar.timeInMillis) / 60_000L).toInt()
        val overnightEveningHalf = schedule.startMinutes > schedule.endMinutes &&
            minuteOfDay >= schedule.startMinutes
        if (overnightEveningHalf) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis + schedule.endMinutes * 60_000L
    }

    /**
     * The schedule covering [now] (first match wins), or null when no enabled schedule covers
     * the current minute. The one-shot urge block is evaluated first, then the weekly windows.
     * Overnight windows are evaluated correctly across midnight.
     */
    fun activeScheduleNow(now: Calendar = Calendar.getInstance()): BlockSchedule? {
        urgeScheduleOrNull(now.timeInMillis)?.let { return it }
        val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
        val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return schedules().firstOrNull { schedule ->
            schedule.enabled && covers(schedule, dayOfWeek, minuteOfDay)
        }
    }

    private fun covers(schedule: BlockSchedule, dayOfWeek: Int, minuteOfDay: Int): Boolean {
        val inToday = schedule.days.contains(dayOfWeek)
        val previousDay = if (dayOfWeek == Calendar.SUNDAY) Calendar.SATURDAY else dayOfWeek - 1
        val inPreviousDay = schedule.days.contains(previousDay)
        return if (schedule.startMinutes <= schedule.endMinutes) {
            inToday && minuteOfDay >= schedule.startMinutes && minuteOfDay < schedule.endMinutes
        } else {
            // Overnight window: evening half belongs to the configured day,
            // morning half continues into the next day.
            (inToday && minuteOfDay >= schedule.startMinutes) ||
                (inPreviousDay && minuteOfDay < schedule.endMinutes)
        }
    }

    private fun persist(list: List<BlockSchedule>) {
        val array = JSONArray()
        list.forEach { schedule ->
            val item = JSONObject()
                .put(FIELD_ID, schedule.id)
                .put(FIELD_NAME, schedule.name)
                .put(FIELD_START, schedule.startMinutes)
                .put(FIELD_END, schedule.endMinutes)
                .put(FIELD_ENABLED, schedule.enabled)
                .put(FIELD_CREATED_AT, schedule.createdAtMillis)
            val days = JSONArray()
            schedule.days.sorted().forEach { days.put(it) }
            item.put(FIELD_DAYS, days)
            array.put(item)
        }
        prefs?.edit()?.putString(KEY_SCHEDULES, array.toString())?.apply()
    }
}
