package com.agon.app.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.agon.app.data.ShieldRepository
import com.agon.app.health.ExactAlarmHelper
import com.agon.app.vpn.FamilyVpnService

/**
 * Schedules recurring wake-ups so protection is revived after OEM battery killers, process
 * death, or long idle periods.
 *
 * Exact alarms are used only when [ExactAlarmHelper.isAllowed] is true (SCHEDULE_EXACT_ALARM
 * granted on API 31+). Otherwise falls back to inexact AlarmManager APIs + WorkManager.
 * Never depends on USE_EXACT_ALARM (Play-restricted).
 */
object KeepAliveScheduler {

    const val ACTION_KEEP_ALIVE = "com.agon.app.action.KEEP_ALIVE"
    private const val REQUEST_CODE = 7101

    /** Interval between keep-alive pings. Short enough to recover within minutes. */
    const val INTERVAL_MS = 5 * 60_000L

    /** First fire after process start / schedule call. */
    private const val INITIAL_DELAY_MS = 60_000L

    fun schedule(context: Context) {
        val app = context.applicationContext
        scheduleAlarm(app, INITIAL_DELAY_MS)
        // WorkManager belt — primary survival path when exact alarms are unavailable.
        runCatching { KeepAliveWorker.enqueue(app) }
    }

    /** Reschedule the next one-shot idle-capable alarm after a keep-alive tick. */
    fun scheduleNext(context: Context) {
        scheduleAlarm(context.applicationContext, INTERVAL_MS)
        runCatching { KeepAliveWorker.enqueue(context.applicationContext) }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { alarmManager.cancel(pendingIntent(app)) }
    }

    /** Start guardian (+ VPN if configured) immediately. Safe to call from any process. */
    fun ensureProtectionRunning(context: Context) {
        val app = context.applicationContext
        ShieldRepository.initialize(app)
        ShieldRepository.refresh()
        runCatching {
            ContextCompat.startForegroundService(
                app,
                Intent(app, ProtectionGuardianService::class.java),
            )
        }
        val state = ShieldRepository.state.value
        if (state.vpnEnabled && !ShieldRepository.isProtectionPaused()) {
            runCatching {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, FamilyVpnService::class.java),
                )
            }
        }
        scheduleNext(app)
    }

    private fun scheduleAlarm(app: Context, delayMs: Long) {
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntent(app)
        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        // Runtime-exactness check per Play policy: on Android 12+ (API 31) exact alarms need
        // the SCHEDULE_EXACT_ALARM runtime grant — never call the exact API without it, and a
        // SecurityException must fall through to the inexact path instead of crashing.
        val allowExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            ExactAlarmHelper.isAllowed(app)
        }

        try {
            if (allowExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // setAndAllowWhileIdle is the least-privileged exact-while-idle API; requires
                // SCHEDULE_EXACT_ALARM on API 31+ when the app is not exempt.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pending,
                )
            } else {
                scheduleInexact(alarmManager, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            // Revoked between the check and the call (or an OEM regression) — fall through to
            // the inexact belt so the keep-alive chain survives instead of crashing the app.
            scheduleInexact(alarmManager, triggerAt, pending)
        }

        // Repeating inexact belt (may batch under Doze; still recovers on idle exit).
        runCatching {
            @Suppress("DEPRECATION")
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt + INTERVAL_MS,
                INTERVAL_MS,
                pending,
            )
        }
    }

    private fun scheduleInexact(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pending: PendingIntent,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Windowed wake-up does not require USE_EXACT_ALARM / canScheduleExactAlarms.
                alarmManager.setWindow(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    INTERVAL_MS / 2,
                    pending,
                )
            } else {
                @Suppress("DEPRECATION")
                alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pending)
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, KeepAliveReceiver::class.java).setAction(ACTION_KEEP_ALIVE)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
