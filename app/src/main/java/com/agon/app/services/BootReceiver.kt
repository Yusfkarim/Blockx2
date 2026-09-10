package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.blocklist.domain.BuiltInAdultKeywords
import com.agon.app.data.ShieldRepository
import com.agon.app.vpn.FamilyVpnService

/**
 * Restores protection after a reboot, an app update or an OEM "quick boot".
 *
 * The supervisor is always started; the VPN is started only when the user had it enabled and
 * asked for auto-start, so we never silently turn on filtering the user had switched off.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in HANDLED_ACTIONS) return

        // Direct-boot hardening (Android 14/15): before the user unlocks, the
        // credential-protected SharedPreferences/Room used below can hard-crash the process.
        // LOCKED_BOOT maps to a warm belt only — real bring-up repeats at USER_UNLOCKED /
        // BOOT_COMPLETED right after unlock.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED && !isUserUnlocked(context)) return

        ShieldRepository.initialize(context)
        ShieldRepository.refresh()
        BuiltInAdultDomains.initialize(context)
        BuiltInAdultKeywords.initialize(context)
        com.agon.app.blocklist.data.TimedBlockingStore.onProcessStart(context)
        KeepAliveScheduler.schedule(context)

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProtectionGuardianService::class.java),
            )
        }

        val state = ShieldRepository.state.value
        if (state.autoStart && state.vpnEnabled) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FamilyVpnService::class.java),
                )
            }
        }
    }

    /** true below API 24 or once the user has unlocked the device (credential storage ready). */
    private fun isUserUnlocked(context: Context): Boolean =
        if (android.os.Build.VERSION.SDK_INT < 24) true
        else runCatching {
            val manager = context.getSystemService(android.os.UserManager::class.java)
            manager == null || manager.isUserUnlocked
        }.getOrDefault(true)

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // OEM broadcasts used by HTC / Xiaomi / Asus fast-boot paths.
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}
