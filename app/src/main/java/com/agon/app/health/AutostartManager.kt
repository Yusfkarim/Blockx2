package com.agon.app.health

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.agon.app.battery.DeviceVendor

/**
 * OEM autostart / protected-apps surfaces. Most vendors do not expose a query API, so satisfaction
 * is: stock Android auto-passes; OEM families require opening the vendor screen and an explicit
 * user confirmation stored in prefs (re-checked on every setup resume).
 */
class AutostartManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun vendor(): DeviceVendor = detectVendor()

    fun requiresUserAction(): Boolean = when (vendor()) {
        DeviceVendor.PIXEL, DeviceVendor.GENERIC, DeviceVendor.MOTOROLA -> false
        else -> true
    }

    fun isSatisfied(): Boolean {
        if (!requiresUserAction()) return true
        return prefs.getBoolean(KEY_CONFIRMED, false)
    }

    fun markConfigured() {
        prefs.edit().putBoolean(KEY_CONFIRMED, true).apply()
    }

    fun clearConfigured() {
        prefs.edit().putBoolean(KEY_CONFIRMED, false).apply()
    }

    fun resolveSettingsIntent(): Intent? {
        for (intent in vendorIntents(vendor())) {
            if (canResolve(intent)) {
                return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun guideSteps(language: String): List<String> {
        val v = vendor()
        return OemKnowledgeBase.autostartSteps(v, language)
    }

    fun guideTitle(language: String): String = OemKnowledgeBase.autostartTitle(vendor(), language)

    private fun vendorIntents(vendor: DeviceVendor): List<Intent> = when (vendor) {
        DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", context.packageName),
        )
        DeviceVendor.SAMSUNG -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component("com.samsung.android.sm", "com.samsung.android.sm.app.dashboard.SmartManagerDashBoardActivity"),
        )
        DeviceVendor.OPPO, DeviceVendor.REALME -> listOf(
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"),
            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
        )
        DeviceVendor.VIVO -> listOf(
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
        )
        DeviceVendor.HUAWEI -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        DeviceVendor.HONOR -> listOf(
            component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )
        DeviceVendor.ONEPLUS -> listOf(
            component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
            component("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
        )
        DeviceVendor.ASUS -> listOf(
            component("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
            component("com.asus.mobilemanager", "com.asus.mobilemanager.entry.FunctionActivity"),
        )
        DeviceVendor.NOTHING -> listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        DeviceVendor.MOTOROLA, DeviceVendor.PIXEL, DeviceVendor.GENERIC -> emptyList()
    }

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))

    private fun canResolve(intent: Intent): Boolean = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ) != null
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }.getOrDefault(false)

    private fun detectVendor(): DeviceVendor {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val isMiui = runCatching { Class.forName("miui.os.Build") }.isSuccess
        return when {
            brand.contains("redmi") -> DeviceVendor.REDMI
            brand.contains("poco") -> DeviceVendor.POCO
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || isMiui -> DeviceVendor.XIAOMI
            manufacturer.contains("samsung") -> DeviceVendor.SAMSUNG
            brand.contains("realme") || manufacturer.contains("realme") -> DeviceVendor.REALME
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceVendor.ONEPLUS
            manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceVendor.OPPO
            manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo") -> DeviceVendor.VIVO
            manufacturer.contains("honor") || brand.contains("honor") -> DeviceVendor.HONOR
            manufacturer.contains("huawei") || brand.contains("huawei") -> DeviceVendor.HUAWEI
            manufacturer.contains("asus") || brand.contains("asus") -> DeviceVendor.ASUS
            manufacturer.contains("nothing") || brand.contains("nothing") -> DeviceVendor.NOTHING
            manufacturer.contains("motorola") || brand.contains("moto") -> DeviceVendor.MOTOROLA
            manufacturer.contains("google") || brand.contains("pixel") -> DeviceVendor.PIXEL
            else -> DeviceVendor.GENERIC
        }
    }

    private companion object {
        const val PREFS = "protection_autostart"
        const val KEY_CONFIRMED = "autostart_confirmed"
    }
}
