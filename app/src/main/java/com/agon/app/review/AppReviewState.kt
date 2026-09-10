package com.agon.app.review

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the In-App Review state.
 *
 * Logic:
 * - If the user has never completed the review flow, the rating button is always shown.
 * - After the review flow completes, we save the timestamp. The button stays visible for
 *   24 hours (in case the user wants to modify their review), then is hidden permanently.
 * - After 24 hours, the button is hidden and won't reappear.
 */
object AppReviewState {

    private const val PREFS = "app_review_state"
    private const val KEY_RATED_TIMESTAMP = "rated_timestamp"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * True when the review button should be HIDDEN.
     *
     * The button is hidden only after the user completedB has completed the review flow
     * AND 24 hours have passed since then.
     */
    fun shouldHideReviewButton(context: Context): Boolean {
        val timestamp = prefs(context).getLong(KEY_RATED_TIMESTAMP, 0L)
        if (timestamp == 0L) return false // Never rated → always show
        val elapsed = System.currentTimeMillis() - timestamp
        return elapsed >= TWENTY_FOUR_HOURS_MILLIS
    }

    /** True if the user has completed the review flow at least once. */
    fun hasRatedApp(context: Context): Boolean =
        prefs(context).getLong(KEY_RATED_TIMESTAMP, 0L) > 0L

    /** Returns millis since the review flow was completed, or null if never. */
    fun timeSinceReview(context: Context): Long? {
        val ts = prefs(context).getLong(KEY_RATED_TIMESTAMP, 0L)
        return if (ts == 0L) null else System.currentTimeMillis() - ts
    }

    /** Marks the review flow as completed with the current timestamp. */
    fun markRated(context: Context) {
        prefs(context).edit().putLong(KEY_RATED_TIMESTAMP, System.currentTimeMillis()).apply()
    }

    private const val TWENTY_FOUR_HOURS_MILLIS = 24L * 60L * 60L * 1000L
}
