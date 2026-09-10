package com.agon.app.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agon.app.services.ProtectionGuardianService
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.ProtectedScreenType
import com.agon.app.settingsprotection.ui.PinLockActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Existing device admin receiver, extended with uninstall protection.
 *
 * [onDisableRequested] is the one official callback Android gives an app before its admin
 * rights are revoked. It cannot veto the action, but it can return a warning and raise the PIN
 * screen, which is the closest supported behaviour to blocking the removal outright.
 */
class ShieldDeviceAdminReceiver : DeviceAdminReceiver() {

    /** Hilt cannot inject a manifest-declared receiver, so dependencies are pulled on demand. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AdminEntryPoint {
        fun uninstallProtection(): UninstallProtectionManager
        fun settingsProtection(): SettingsProtectionRepository
    }

    private fun entryPoint(context: Context): AdminEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, AdminEntryPoint::class.java)

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        ContextCompat.startForegroundService(
            context,
            Intent(context, ProtectionGuardianService::class.java),
        )
        // Enabling never needs a credential; once active, immediately re-arm protection so
        // returning here to disable the admin requires verification.
        runCatching { entryPoint(context).settingsProtection().revokeTemporaryAccess() }
        // Escalate to device-owner locks when the device was provisioned for it.
        runCatching { entryPoint(context).uninstallProtection().applyStrongestProtection() }
    }

    /**
     * Fired when the user opens the "Deactivate" confirmation for this admin. Returning a
     * non-null message shows it on that screen; raising the PIN activity at the same moment
     * puts verification in front of the action with no visible delay.
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val protection = runCatching { entryPoint(context).settingsProtection() }.getOrNull()
        val snapshot = protection?.currentSnapshot()
        if (snapshot != null && snapshot.active && !snapshot.hasTemporaryAccess(System.currentTimeMillis())) {
            runCatching {
                context.startActivity(
                    Intent(context, PinLockActivity::class.java)
                        .putExtra(PinLockActivity.EXTRA_SCREEN_TYPE, ProtectedScreenType.DEVICE_ADMIN.name)
                        .putExtra(PinLockActivity.EXTRA_SETTINGS_PACKAGE, "android.app.action.DEVICE_ADMIN")
                        .putExtra(PinLockActivity.EXTRA_SCREEN_CLASS, "DeviceAdminDisableRequested")
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }
        return WARNING
    }

    /** Admin rights were revoked: keep the remaining protection layers running. */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        runCatching { entryPoint(context).settingsProtection().revokeTemporaryAccess() }
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProtectionGuardianService::class.java),
            )
        }
    }

    private companion object {
        const val WARNING =
            "BlockX LaAbrah protection will be turned off and this app can then be uninstalled. " +
                "Protection credential verification is required."
    }
}
