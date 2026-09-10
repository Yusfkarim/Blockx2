package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes the process on a schedule (and on user-present / screen-on) so keyword protection and
 * the guardian service are restored after the system or an OEM killer stopped them.
 */
class KeepAliveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED) return
        when (action) {
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                com.agon.app.blocklist.data.TimedBlockingStore.onSystemClockSignal(context, action)
            }
            com.agon.app.blocklist.data.TimedBlockingAlarms.ACTION_EXPIRE -> {
                com.agon.app.blocklist.data.TimedBlockingStore.consumeExpired(context)
                com.agon.app.blocklist.data.TimedBlockingAlarms.reschedule(context)
            }
            else -> com.agon.app.blocklist.data.TimedBlockingStore.checkpointAll(context)
        }
        KeepAliveScheduler.ensureProtectionRunning(context)
    }

    private companion object {
        val HANDLED = setOf(
            KeepAliveScheduler.ACTION_KEEP_ALIVE,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            com.agon.app.blocklist.data.TimedBlockingAlarms.ACTION_EXPIRE,
        )
    }
}
