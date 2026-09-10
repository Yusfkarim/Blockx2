package com.agon.app.timelimit

import android.os.SystemClock

/**
 * Converts accessibility events into exact foreground-app usage for [AppTimeLimitStore].
 *
 * Time is checkpointed while the limited app remains in the foreground, not only when the user
 * switches apps. This keeps both persistence and enforcement accurate during long uninterrupted
 * sessions and survives service restarts without losing rounded partial minutes.
 */
class AppTimeTracker {

    private var currentPackage: String? = null
    private var checkpointAtMs: Long = 0L

    /**
     * Records that [packageName] is currently in the foreground and checkpoints elapsed usage.
     * Returns true as soon as the app has exhausted its daily budget.
     */
    fun onForegroundApp(packageName: String, nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        if (packageName == currentPackage) {
            checkpoint(nowMs)
            return AppTimeLimitStore.isExhausted(packageName)
        }

        settleCurrent(nowMs)
        currentPackage = packageName.takeIf { AppTimeLimitStore.limits().containsKey(it) }
        checkpointAtMs = nowMs
        return AppTimeLimitStore.isExhausted(packageName)
    }

    /** Persists the active app's elapsed time without changing foreground ownership. */
    fun checkpoint(nowMs: Long = SystemClock.elapsedRealtime()): Boolean {
        val packageName = currentPackage ?: return false
        val span = (nowMs - checkpointAtMs).coerceAtLeast(0L).coerceAtMost(MAX_REASONABLE_SPAN_MS)
        if (span > 0L) AppTimeLimitStore.addUsageMillis(packageName, span)
        checkpointAtMs = nowMs
        return AppTimeLimitStore.isExhausted(packageName)
    }

    /** Flushes the currently tracked app's elapsed time into the store. */
    fun flush(nowMs: Long = SystemClock.elapsedRealtime()) {
        settleCurrent(nowMs)
        currentPackage = null
        checkpointAtMs = nowMs
    }

    fun currentLimitedPackage(): String? = currentPackage

    private fun settleCurrent(nowMs: Long) {
        val packageName = currentPackage ?: return
        val span = (nowMs - checkpointAtMs).coerceAtLeast(0L).coerceAtMost(MAX_REASONABLE_SPAN_MS)
        if (span > 0L) AppTimeLimitStore.addUsageMillis(packageName, span)
        checkpointAtMs = nowMs
    }

    private companion object {
        /** A very long callback gap is treated as sleep/process suspension and safely capped. */
        const val MAX_REASONABLE_SPAN_MS = 15L * 60L * 1000L
    }
}
