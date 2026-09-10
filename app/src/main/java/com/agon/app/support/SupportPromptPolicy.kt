package com.agon.app.support

import android.content.Context

/**
 * Decides when the support screen should appear, based on local storage only.
 *
 * The logic is intentionally isolated here so the cadence can be tuned later without touching any
 * UI code. It is only ever consulted once the whole app setup and permission flow is complete, so
 * the prompt never interrupts onboarding.
 *
 * Current policy:
 *  - The support screen is shown on EVERY launch that reaches the fully-configured home screen,
 *    before the home screen is used. The user can dismiss it each time.
 *
 * To change the behaviour later (e.g. show at most once per day, or a limited number of times),
 * flip [SHOW_EVERY_LAUNCH] to false and tune [MAX_SHOWS] / [REAPPEAR_INTERVAL_MS]; the UI never
 * needs to change.
 */
object SupportPromptPolicy {

    private const val PREFS = "support_prompt"
    private const val KEY_SHOWN_COUNT = "shown_count"
    private const val KEY_LAST_SHOWN_AT = "last_shown_at"

    /** When true, the support screen appears on every launch (current requirement). */
    const val SHOW_EVERY_LAUNCH = true

    /** Used only when [SHOW_EVERY_LAUNCH] is false: max automatic appearances in total. */
    const val MAX_SHOWS = 3

    /** Used only when [SHOW_EVERY_LAUNCH] is false: minimum gap between appearances. */
    const val REAPPEAR_INTERVAL_MS = 3L * 24L * 60L * 60L * 1000L

    /** True when the support screen may be shown right now. */
    fun shouldShow(context: Context): Boolean {
        if (SHOW_EVERY_LAUNCH) return true
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_SHOWN_COUNT, 0)
        if (count >= MAX_SHOWS) return false
        val last = prefs.getLong(KEY_LAST_SHOWN_AT, 0L)
        if (last == 0L) return true
        return System.currentTimeMillis() - last >= REAPPEAR_INTERVAL_MS
    }

    /** Records that the support screen was shown now. */
    fun markShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_SHOWN_COUNT, 0)
        prefs.edit()
            .putInt(KEY_SHOWN_COUNT, count + 1)
            .putLong(KEY_LAST_SHOWN_AT, System.currentTimeMillis())
            .apply()
    }
}
