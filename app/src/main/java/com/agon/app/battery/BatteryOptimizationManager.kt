package com.agon.app.battery

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Effective background-execution status for this app, as reported by the platform.
 *
 * These map onto what Android surfaces in App info -> Battery:
 *  - [UNRESTRICTED] "Unrestricted" / "No restrictions" - background work is allowed,
 *  - [OPTIMIZED]    "Optimized" - the default, Android may defer background work,
 *  - [RESTRICTED]   "Restricted" - background work is actively blocked, the worst case.
 */
enum class BatteryStatus { UNRESTRICTED, OPTIMIZED, RESTRICTED }

/**
 * Full snapshot of every power signal the platform exposes for this package.
 *
 * @param status           the effective state shown to the user
 * @param onDozeAllowlist  result of `PowerManager.isIgnoringBatteryOptimizations()`
 * @param backgroundRestricted result of `ActivityManager.isBackgroundRestricted()` (API 28+)
 * @param standbyBucket    `UsageStatsManager` bucket for this app, -1 when unavailable
 * @param powerSaveMode    whether system Battery Saver is currently on
 */
data class BatteryState(
    val status: BatteryStatus,
    val onDozeAllowlist: Boolean,
    val backgroundRestricted: Boolean,
    val standbyBucket: Int,
    val powerSaveMode: Boolean,
) {
    /** True when Android will let protection keep running in the background. */
    val isUnrestricted: Boolean get() = status == BatteryStatus.UNRESTRICTED

    /** True when the user should be warned and offered a settings destination. */
    val needsAction: Boolean get() = status != BatteryStatus.UNRESTRICTED
}

/** Device families that need their own wording and their own settings destination. */
enum class DeviceVendor {
    SAMSUNG,
    XIAOMI,
    REDMI,
    POCO,
    OPPO,
    REALME,
    VIVO,
    ONEPLUS,
    HUAWEI,
    HONOR,
    MOTOROLA,
    PIXEL,
    ASUS,
    NOTHING,
    GENERIC,
}

/**
 * Resolves battery-optimization state and the best available settings destination for the
 * current device.
 *
 * Detection is intentionally conservative: the exemption state is read through the official
 * [PowerManager] API, and every OEM deep link is validated with the package manager before it is
 * offered, so an unresolvable component can never crash the app or dead-end the user.
 */
@Singleton
class BatteryOptimizationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** The detected vendor, evaluated once. */
    val vendor: DeviceVendor by lazy(LazyThreadSafetyMode.PUBLICATION) { detectVendor() }

    /** Human readable device name used in the guide header. */
    val deviceLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val brand = Build.BRAND?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL?.takeIf { it.isNotBlank() }
        listOfNotNull(brand, model).joinToString(" ").ifBlank { "This device" }
    }

    /**
     * Reads every power signal the platform exposes and resolves the effective status.
     *
     * The previous implementation trusted `isIgnoringBatteryOptimizations()` alone. That call
     * only reports membership of the Doze allowlist, and several OEM skins (One UI, MIUI /
     * HyperOS, ColorOS, Funtouch) move an app to "Unrestricted" without adding it to that
     * allowlist. The result was a permanent false warning on devices that were already
     * configured correctly.
     *
     * Resolution order, strongest evidence first:
     *  1. `isBackgroundRestricted()` true  -> RESTRICTED, the user explicitly restricted us,
     *  2. Doze allowlist true              -> UNRESTRICTED, the definitive positive signal,
     *  3. standby bucket EXEMPTED          -> UNRESTRICTED, how OEM "Unrestricted" surfaces,
     *  4. otherwise                        -> OPTIMIZED.
     */
    fun readState(): BatteryState {
        // Before Android M there is no Doze and no standby bucket at all.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return BatteryState(
                status = BatteryStatus.UNRESTRICTED,
                onDozeAllowlist = true,
                backgroundRestricted = false,
                standbyBucket = STANDBY_BUCKET_UNKNOWN,
                powerSaveMode = false,
            )
        }

        val power = context.getSystemService(PowerManager::class.java)
        val onAllowlist = runCatching {
            power?.isIgnoringBatteryOptimizations(context.packageName) == true
        }.getOrDefault(false)

        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
            }.getOrDefault(false)
        } else {
            false
        }

        val bucket = readStandbyBucket()
        val saver = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false)

        val status = when {
            restricted -> BatteryStatus.RESTRICTED
            onAllowlist -> BatteryStatus.UNRESTRICTED
            // OEM "Unrestricted" / "No restrictions" commonly appears as the EXEMPTED bucket
            // rather than as Doze allowlist membership.
            bucket != STANDBY_BUCKET_UNKNOWN && bucket <= STANDBY_BUCKET_EXEMPTED ->
                BatteryStatus.UNRESTRICTED
            else -> BatteryStatus.OPTIMIZED
        }

        return BatteryState(
            status = status,
            onDozeAllowlist = onAllowlist,
            backgroundRestricted = restricted,
            standbyBucket = bucket,
            powerSaveMode = saver,
        )
    }

    /**
     * True when Android will no longer put this app to sleep in the background.
     *
     * Kept as the stable entry point used across the app; now backed by [readState] so every
     * caller benefits from the corrected multi-signal detection.
     */
    fun isExempt(): Boolean = readState().isUnrestricted

    /**
     * App standby bucket, or [STANDBY_BUCKET_UNKNOWN] when the platform does not expose it.
     *
     * `getAppStandbyBucket()` reports the *calling* app's own bucket and needs no permission,
     * which is what makes this a reliable cross-vendor signal.
     */
    private fun readStandbyBucket(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return STANDBY_BUCKET_UNKNOWN
        return runCatching {
            context.getSystemService(UsageStatsManager::class.java)?.appStandbyBucket
                ?: STANDBY_BUCKET_UNKNOWN
        }.getOrDefault(STANDBY_BUCKET_UNKNOWN)
    }

    /**
     * The official per-app exemption dialog, or null when the platform does not expose it.
     *
     * This is always preferred: it grants the exemption in a single tap and reports the result
     * back, whereas OEM screens only navigate the user to a list.
     */
    fun resolveOfficialRequestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.fromParts("package", context.packageName, null),
        )
        return intent.takeIf { canResolve(it) }
    }

    /**
     * Closest supported battery-management destination for skins that block or ignore the
     * official request dialog (MIUI/HyperOS, ColorOS, Funtouch, EMUI, MagicOS).
     */
    fun resolveFallbackIntent(): Intent? {
        for (intent in fallbackIntents()) {
            if (canResolve(intent)) return intent
        }
        return null
    }

    /**
     * Candidate destinations in priority order: the official exemption dialog first, then the
     * vendor's own per-app battery screen, then the generic lists.
     */
    fun resolveSettingsIntent(): Intent? =
        resolveOfficialRequestIntent() ?: resolveFallbackIntent()

    /** True when a vendor specific screen exists, used to tailor the on-screen instructions. */
    fun hasVendorScreen(): Boolean = vendorIntents().any { canResolve(it) }

    private fun fallbackIntents(): List<Intent> = buildList {
        addAll(vendorIntents())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            add(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        // Universal fallback: this app's own details page, which always exposes battery usage.
        add(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    /** Known per-app power management screens, by vendor. */
    private fun vendorIntents(): List<Intent> = when (vendor) {
        DeviceVendor.XIAOMI, DeviceVendor.REDMI, DeviceVendor.POCO -> listOf(
            component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                .putExtra("package_name", context.packageName)
                .putExtra("package_label", appLabel()),
            component("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
            component(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        )

        DeviceVendor.SAMSUNG -> listOf(
            component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            component("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )

        DeviceVendor.HUAWEI -> listOf(
            component("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
            component(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )

        DeviceVendor.HONOR -> listOf(
            component("com.hihonor.systemmanager", "com.hihonor.systemmanager.power.ui.HwPowerManagerActivity"),
            component(
                "com.hihonor.systemmanager",
                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            ),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        )

        DeviceVendor.OPPO, DeviceVendor.REALME -> listOf(
            component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
        )

        DeviceVendor.VIVO -> listOf(
            component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
            component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
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

    private fun appLabel(): String = runCatching {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    }.getOrDefault("BlockX LaAbrah")

    /** Validates that an intent can actually be started before it is offered to the user. */
    private fun canResolve(intent: Intent): Boolean = runCatching {
        val manager = context.packageManager
        if (Build.VERSION.SDK_INT >= 33) {
            manager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            ) != null
        } else {
            @Suppress("DEPRECATION")
            manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }.getOrDefault(false)

    private companion object {
        /** Returned when the platform cannot report a standby bucket. */
        const val STANDBY_BUCKET_UNKNOWN = -1

        /**
         * `UsageStatsManager.STANDBY_BUCKET_EXEMPTED` (5). Referenced by value because the
         * constant is hidden on some API levels; anything at or below it means the app is
         * exempt from standby throttling.
         */
        const val STANDBY_BUCKET_EXEMPTED = 5
    }

    private fun detectVendor(): DeviceVendor {
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()
        val isMiui = runCatching { Class.forName("miui.os.Build") }.isSuccess

        return when {
            brand.contains("redmi") -> DeviceVendor.REDMI
            brand.contains("poco") -> DeviceVendor.POCO
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || isMiui -> DeviceVendor.XIAOMI
            manufacturer.contains("samsung") || brand.contains("samsung") -> DeviceVendor.SAMSUNG
            brand.contains("realme") || manufacturer.contains("realme") -> DeviceVendor.REALME
            manufacturer.contains("oneplus") || brand.contains("oneplus") -> DeviceVendor.ONEPLUS
            manufacturer.contains("oppo") || brand.contains("oppo") -> DeviceVendor.OPPO
            manufacturer.contains("vivo") || brand.contains("vivo") || brand.contains("iqoo") -> DeviceVendor.VIVO
            manufacturer.contains("honor") || brand.contains("honor") -> DeviceVendor.HONOR
            manufacturer.contains("huawei") || brand.contains("huawei") -> DeviceVendor.HUAWEI
            manufacturer.contains("asus") || brand.contains("asus") -> DeviceVendor.ASUS
            manufacturer.contains("nothing") || brand.contains("nothing") -> DeviceVendor.NOTHING
            manufacturer.contains("motorola") || brand.contains("moto") || brand.contains("lenovo") ->
                DeviceVendor.MOTOROLA
            manufacturer.contains("google") || brand.contains("google") || brand.contains("pixel") ->
                DeviceVendor.PIXEL
            else -> DeviceVendor.GENERIC
        }
    }
}
