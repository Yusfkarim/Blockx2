package com.agon.app.health

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agon.app.admin.ShieldDeviceAdminReceiver
import com.agon.app.admin.deviceAdminSettingsIntent
import com.agon.app.battery.BatteryOptimizationManager
import com.agon.app.battery.DeviceVendor
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.blocklist.domain.BuiltInAdultKeywords
import com.agon.app.data.ShieldRepository
import com.agon.app.services.ProtectionGuardianService
import com.agon.app.vpn.FamilyVpnService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HealthCheckId {
    ACCESSIBILITY,
    BATTERY,
    AUTOSTART,
    NOTIFICATIONS,
    EXACT_ALARM,
    DEVICE_ADMIN,
    GUARDIAN_SERVICE,
    VPN_OPTIONAL,
    KEYWORD_ENGINE,
}

data class HealthCheckItem(
    val id: HealthCheckId,
    val ok: Boolean,
    val required: Boolean,
    val titleKey: String,
    val descKey: String,
    val fixIntent: Intent? = null,
)

data class ProtectionHealthSnapshot(
    val vendor: DeviceVendor,
    val vendorLabel: String,
    val score: Int,
    val items: List<HealthCheckItem>,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val allRequiredOk: Boolean get() = items.filter { it.required }.all { it.ok }
    val okCount: Int get() = items.count { it.ok }
}

object ProtectionHealth {

    fun evaluate(
        context: Context,
        batteryManager: BatteryOptimizationManager,
        autostartManager: AutostartManager = AutostartManager(context),
    ): ProtectionHealthSnapshot {
        val app = context.applicationContext
        ShieldRepository.refresh()
        val state = ShieldRepository.state.value

        BuiltInAdultKeywords.initialize(app)

        val accessibilityOk = isAccessibilityEnabled(app) || state.accessibilityEnabled
        val batteryOk = runCatching { batteryManager.readState().isUnrestricted || batteryManager.isExempt() }
            .getOrDefault(false)
        val autostartOk = autostartManager.isSatisfied()
        val notificationsOk = NotificationHealth.areNotificationsHealthy(app)
        val exactAlarmOk = ExactAlarmHelper.isAllowed(app)
        val adminOk = isDeviceAdminActive(app)
        val guardianOk = isServiceRunning(app, ProtectionGuardianService::class.java.name) || accessibilityOk
        val vpnOk = !state.vpnEnabled || state.vpnRunning
        val keywordOk = BlockEngine.isInitialized() && BlockEngine.shouldInspectText()

        val items = listOf(
            HealthCheckItem(
                id = HealthCheckId.ACCESSIBILITY,
                ok = accessibilityOk,
                required = true,
                titleKey = "check_accessibility",
                descKey = "check_accessibility_desc",
                fixIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            ),
            HealthCheckItem(
                id = HealthCheckId.DEVICE_ADMIN,
                ok = adminOk,
                required = true,
                titleKey = "check_admin",
                descKey = "check_admin_desc",
                fixIntent = deviceAdminSettingsIntent(app),
            ),
            HealthCheckItem(
                id = HealthCheckId.BATTERY,
                ok = batteryOk,
                required = true,
                titleKey = "check_battery",
                descKey = "check_battery_desc",
                fixIntent = batteryManager.resolveSettingsIntent(),
            ),
            HealthCheckItem(
                id = HealthCheckId.AUTOSTART,
                ok = autostartOk,
                // Always required: stock devices auto-pass via AutostartManager.isSatisfied().
                required = true,
                titleKey = "check_autostart",
                descKey = "check_autostart_desc",
                fixIntent = autostartManager.resolveSettingsIntent(),
            ),
            HealthCheckItem(
                id = HealthCheckId.NOTIFICATIONS,
                ok = notificationsOk,
                required = true,
                titleKey = "check_notifications",
                descKey = "check_notifications_desc",
                fixIntent = NotificationHealth.settingsIntent(app),
            ),
            HealthCheckItem(
                id = HealthCheckId.EXACT_ALARM,
                ok = exactAlarmOk || Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
                // Optional: keep-alive falls back to WorkManager + inexact alarms without it.
                required = false,
                titleKey = "check_exact_alarm",
                descKey = "check_exact_alarm_desc",
                fixIntent = if (ExactAlarmHelper.requiresUserAction(app)) {
                    ExactAlarmHelper.settingsIntent(app)
                } else {
                    null
                },
            ),
            HealthCheckItem(
                id = HealthCheckId.GUARDIAN_SERVICE,
                ok = guardianOk,
                required = true,
                titleKey = "check_guardian",
                descKey = "check_guardian_desc",
                fixIntent = Intent(app, ProtectionGuardianService::class.java),
            ),
            HealthCheckItem(
                id = HealthCheckId.KEYWORD_ENGINE,
                ok = keywordOk,
                required = true,
                titleKey = "check_keyword_engine",
                descKey = "check_keyword_engine_desc",
                fixIntent = null,
            ),
            HealthCheckItem(
                id = HealthCheckId.VPN_OPTIONAL,
                ok = vpnOk,
                // Required exactly when the user asked for web protection: an enabled-but-down
                // VPN must lower the score instead of showing a red card next to a 100% header.
                required = state.vpnEnabled,
                titleKey = "check_vpn",
                descKey = "check_vpn_desc",
                // The Fix button has a dedicated handler in HealthCheckScreen: it runs
                // VpnService.prepare() and either opens the system VPN consent dialog or
                // starts the tunnel right away — it must never merely navigate home.
                fixIntent = null,
            ),
        )

        val required = items.filter { it.required }
        val score = if (required.isEmpty()) 100 else (required.count { it.ok } * 100) / required.size

        return ProtectionHealthSnapshot(
            vendor = batteryManager.vendor,
            vendorLabel = batteryManager.deviceLabel,
            score = score.coerceIn(0, 100),
            items = items,
        )
    }

    /**
     * Live test (four independent layers, in order):
     *  1. the keyword engine must match a known adult sample,
     *  2. the domain layer must still recognise the bundled probe host
     *     (guards against a silently empty block index after upgrades),
     *  3. the VPN tunnel must be up (direct service flag + descriptor liveness — our own app
     *     can never see TRANSPORT_VPN on purpose, it is excluded from its own tunnel),
     *  4. the accessibility service must be connected (app-level blocking).
     * Only when every layer is alive do we claim full protection.
     */
    fun runProtectionTest(context: Context): ProtectionTestResult {
        val app = context.applicationContext
        BuiltInAdultKeywords.initialize(app)
        val sample = "xxx porn sex test"
        val match = BlockEngine.matchingBlockedKeyword(sample)
            ?: BuiltInAdultKeywords.firstMatch(
                com.agon.app.blocklist.domain.TextNormalizer.normalize(sample),
            )

        BuiltInAdultDomains.initialize(app)
        val domainLayerOk = domainLayerBlocksProbe(app)

        val state = runCatching { com.agon.app.data.ShieldRepository.state.value }.getOrNull()
        // tunnel_down false-negative root cause (field fix): our own app is deliberately excluded
        // from its own tunnel (addDisallowedApplication), so ConnectivityManager never reports a
        // TRANSPORT_VPN network to us — the authoritative signal is the service flag written by
        // the service itself after Builder.establish() succeeded and the packet loop is live.
        val tunnelUp = state != null && state.vpnEnabled && state.vpnRunning &&
            com.agon.app.vpn.FamilyVpnService.isTunnelInterfaceUp

        val accessibility = isAccessibilityEnabled(app)
        return when {
            match != null && domainLayerOk && tunnelUp && accessibility ->
                ProtectionTestResult(true, "ok", match)
            match == null || !domainLayerOk ->
                ProtectionTestResult(false, "engine_fail", match)
            !tunnelUp ->
                ProtectionTestResult(false, "tunnel_down", match)
            else ->
                ProtectionTestResult(false, "engine_ok_accessibility_off", match)
        }
    }

    /**
     * The bundled probe domain ("enjoyvideo.top", in the shipped extra blocklist) keeps an
     * end-to-end trip through the domain layer honest: first the curated in-memory lists, then
     * the disk index — an all-green tunnel with an empty rule-set is a false active.
     */
    private fun domainLayerBlocksProbe(context: Context): Boolean {
        BuiltInAdultDomains.initialize(context)
        return BuiltInAdultDomains.matches("enjoyvideo.top") ||
            runCatching { BlockEngine.isWebsiteBlocked("enjoyvideo.top") }.getOrDefault(false)
    }

    fun isAccessibilityEnabled(context: Context): Boolean = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty().contains(
            "${context.packageName}/com.agon.app.accessibility.ShieldAccessibilityService",
            ignoreCase = true,
        )
    }.getOrDefault(false)

    fun isDeviceAdminActive(context: Context): Boolean = runCatching {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val component = ComponentName(context, ShieldDeviceAdminReceiver::class.java)
        dpm?.isAdminActive(component) == true
    }.getOrDefault(false)

    fun isServiceRunning(context: Context, className: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        @Suppress("DEPRECATION")
        return runCatching {
            am.getRunningServices(50).any { it.service.className == className }
        }.getOrDefault(false)
    }

    fun restartGuardian(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProtectionGuardianService::class.java),
            )
        }
        val state = ShieldRepository.state.value
        if (state.vpnEnabled && !ShieldRepository.isProtectionPaused()) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FamilyVpnService::class.java),
                )
            }
        }
    }
}

data class ProtectionTestResult(
    val success: Boolean,
    val code: String,
    val matchedKeyword: String?,
)
