package com.agon.app.blocklist.domain

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.abs

/**
 * Centralized timed-blocking model shared by Reels/Shorts switches, Telegram/VPN toggles and
 * every blocklist item. Screens must read remaining time and lock state from here instead of
 * keeping their own timers.
 *
 * Countdown is driven by [SystemClock.elapsedRealtime] (monotonic, immune to timezone / date /
 * manual clock edits). Wall-clock is only used as a cross-check for tamper detection. After a
 * reboot, elapsedRealtime resets, so remaining duration is preserved and is never reduced by a
 * wall-clock jump — that is the only safe behaviour without a trusted external clock.
 */
enum class BlockingType { SHORT_VIDEO, TELEGRAM_SEARCH, VPN, WEBSITE, APP, KEYWORD }

data class BlockingRule(
    val id: String,
    val type: BlockingType,
    val target: String,
    val enabled: Boolean,
    val durationMs: Long,
    val startedAt: Long,
    val remainingDurationMs: Long,
    val lockedUntilExpiry: Boolean,
    val lastWallClock: Long,
    val lastElapsedRealtime: Long,
    val lastBootCount: Long,
    val tamperDetected: Boolean = false,
) {
    val expiresAtHint: Long
        get() = if (remainingDurationMs <= 0L) 0L else System.currentTimeMillis() + remainingDurationMs
}

object TimedBlockingIds {
    fun shortVideo(platform: ShortVideoPolicy.Platform): String = "sv:${platform.name}"
    fun telegramSearch(): String = "tg:search"
    fun vpn(): String = "vpn:apps"
    fun blocklist(ruleId: Long): String = "bl:$ruleId"
}

data class TrustedClockSnapshot(
    val wallClock: Long,
    val elapsedRealtime: Long,
    val bootCount: Long,
)

object TrustedClock {
    /** Wall vs elapsed discrepancy above this is treated as TIME_TAMPER_DETECTED. */
    const val TAMPER_THRESHOLD_MS = 60_000L

    fun snapshot(context: Context): TrustedClockSnapshot = TrustedClockSnapshot(
        wallClock = System.currentTimeMillis(),
        elapsedRealtime = SystemClock.elapsedRealtime(),
        bootCount = bootCount(context),
    )

    fun bootCount(context: Context): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
            if (count > 0) return count.toLong()
        }
        return 0L
    }

    /**
     * Advances [rule] using only monotonic elapsed time on the same boot. A reboot or a
     * wall-clock jump never reduces remaining time. Sets [BlockingRule.tamperDetected] when the
     * wall clock and elapsedRealtime diverge abnormally (TIME_TAMPER_DETECTED).
     */
    fun advance(rule: BlockingRule, now: TrustedClockSnapshot): BlockingRule {
        if (!rule.enabled || !rule.lockedUntilExpiry) {
            return rule.copy(
                lastWallClock = now.wallClock,
                lastElapsedRealtime = now.elapsedRealtime,
                lastBootCount = now.bootCount,
            )
        }

        val sameBoot = isSameBoot(rule, now)
        val elapsedDelta = if (sameBoot) {
            (now.elapsedRealtime - rule.lastElapsedRealtime).coerceAtLeast(0L)
        } else {
            // Reboot / process-seen elapsed reset: do not credit wall-clock time.
            0L
        }

        val wallDelta = now.wallClock - rule.lastWallClock
        val tamper = when {
            !sameBoot -> rule.tamperDetected
            abs(wallDelta - elapsedDelta) > TAMPER_THRESHOLD_MS -> true
            wallDelta < -TAMPER_THRESHOLD_MS -> true
            else -> rule.tamperDetected
        }

        val remaining = (rule.remainingDurationMs - elapsedDelta).coerceAtLeast(0L)
        val expired = remaining <= 0L
        return rule.copy(
            remainingDurationMs = remaining,
            enabled = if (expired) false else rule.enabled,
            lockedUntilExpiry = if (expired) false else rule.lockedUntilExpiry,
            lastWallClock = now.wallClock,
            lastElapsedRealtime = now.elapsedRealtime,
            lastBootCount = now.bootCount,
            tamperDetected = tamper,
        )
    }

    fun isSameBoot(rule: BlockingRule, now: TrustedClockSnapshot): Boolean {
        if (now.elapsedRealtime < rule.lastElapsedRealtime) return false
        if (rule.lastBootCount > 0L && now.bootCount > 0L && now.bootCount != rule.lastBootCount) return false
        return true
    }
}

/** Localized "Remaining: X d  HH:MM:SS" used under locked switches and blocklist rows. */
fun formatTimedRemaining(remainingMillis: Long, language: String): String {
    val ms = remainingMillis.coerceAtLeast(0L)
    val totalSeconds = ms / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    val prefix = when (language) {
        "en" -> "Remaining:"
        "ar" -> "المتبقي:"
        else -> "ماوە:"
    }
    // Short test locks (< 1 day): show HH:MM:SS only — cleaner for the 3-minute trial.
    if (days == 0L) {
        val clock = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        return "$prefix $clock"
    }
    val dayWord = when (language) {
        "en" -> "d"
        "ar" -> "يوم"
        else -> "ڕۆژ"
    }
    val clock = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    return "$prefix $days $dayWord  $clock"
}
