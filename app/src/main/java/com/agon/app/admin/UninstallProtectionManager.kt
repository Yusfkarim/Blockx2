package com.agon.app.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Opens Android's Device Admin list when available, with Security settings as an OEM-safe fallback. */
fun deviceAdminSettingsIntent(context: Context): Intent {
    val directDestinations = listOf(
        ComponentName("com.android.settings", "com.android.settings.Settings\$DeviceAdminSettingsActivity"),
        ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings"),
    )
    return directDestinations
        .asSequence()
        .map { component -> Intent().setComponent(component) }
        .firstOrNull { intent -> intent.resolveActivity(context.packageManager) != null }
        ?: Intent(Settings.ACTION_SECURITY_SETTINGS)
}

/**
 * Applies the strongest uninstall protection the platform grants this app.
 *
 * Android deliberately offers no way for a normal application to veto its own removal, so the
 * protection is layered and each layer degrades gracefully:
 *
 *  1. **Device Owner** (provisioned device): `setUninstallBlocked` plus the
 *     `DISALLOW_UNINSTALL_APPS` restriction make the app genuinely unremovable through the UI.
 *  2. **Device Admin** (normal case): removal first requires deactivating the admin, which is
 *     intercepted by the accessibility guard and gated behind the PIN.
 *  3. **Accessibility detection**: navigation to App info, uninstall, Manage apps, Device admin
 *     or Security screens raises the PIN screen and sends the user home on failure.
 *
 * Reuses the existing [ShieldDeviceAdminReceiver]; it never registers a second admin component.
 */
@Singleton
class UninstallProtectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Strength of the protection currently in force. */
    enum class Level {
        /** No admin rights: only accessibility interception is active. */
        ACCESSIBILITY_ONLY,

        /** Device admin active: deactivation is PIN gated. */
        DEVICE_ADMIN,

        /** Device owner: uninstall is blocked by the platform itself. */
        DEVICE_OWNER,
    }

    val adminComponent: ComponentName
        get() = ComponentName(context, ShieldDeviceAdminReceiver::class.java)

    private val policyManager: DevicePolicyManager?
        get() = runCatching { context.getSystemService(DevicePolicyManager::class.java) }.getOrNull()

    fun isAdminActive(): Boolean = runCatching {
        policyManager?.isAdminActive(adminComponent) == true
    }.getOrDefault(false)

    fun isDeviceOwner(): Boolean = runCatching {
        policyManager?.isDeviceOwnerApp(context.packageName) == true
    }.getOrDefault(false)

    fun currentLevel(): Level = when {
        isDeviceOwner() -> Level.DEVICE_OWNER
        isAdminActive() -> Level.DEVICE_ADMIN
        else -> Level.ACCESSIBILITY_ONLY
    }

    /**
     * Enables every protection the current privilege level allows. Safe to call repeatedly and
     * from any state; unsupported operations are ignored rather than throwing.
     *
     * @return the level that ended up active.
     */
    fun applyStrongestProtection(): Level {
        val manager = policyManager ?: return Level.ACCESSIBILITY_ONLY
        if (!isDeviceOwner()) return currentLevel()

        runCatching { manager.setUninstallBlocked(adminComponent, context.packageName, true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { manager.addUserRestriction(adminComponent, UserManager.DISALLOW_UNINSTALL_APPS) }
        }
        return Level.DEVICE_OWNER
    }


    /** True when the platform is currently blocking uninstall for this package. */
    fun isUninstallBlocked(): Boolean = runCatching {
        val manager = policyManager ?: return false
        isDeviceOwner() && manager.isUninstallBlocked(adminComponent, context.packageName)
    }.getOrDefault(false)

}
