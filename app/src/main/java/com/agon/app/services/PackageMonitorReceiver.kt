package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agon.app.data.ShieldRepository
import com.agon.app.quarantine.NewAppQuarantineStore

/**
 * Package monitoring layer of the uninstall protection.
 *
 * Android never delivers a broadcast for the removal of the receiving package itself, so this
 * cannot observe its own uninstall. What it does cover is the case that matters in practice:
 * after this app is *updated or reinstalled* (`PACKAGE_REPLACED`), and whenever another package
 * is removed, protection is re-armed so a tamper attempt cannot leave the guardian stopped.
 *
 * Registered in the manifest as a plain receiver, so it costs nothing while idle and is fully
 * event driven.
 */
class PackageMonitorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REPLACED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_FULLY_REMOVED
        ) {
            return
        }

        // New-app quarantine: a freshly installed app is parked until a parent approves it.
        // The guardian restart below only concerns updates/removals, so installs return early.
        if (action == Intent.ACTION_PACKAGE_ADDED) {
            val installed = intent.data?.schemeSpecificPart
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (!installed.isNullOrBlank() && installed != context.packageName && !replacing) {
                NewAppQuarantineStore.initialize(context)
                NewAppQuarantineStore.maybeQuarantine(context, installed)
            }
            return
        }

        val changed = intent.data?.schemeSpecificPart
        val self = context.packageName

        // Our own replacement (update/reinstall) must immediately restore the guardian.
        // Removal of any other package can also indicate tampering with a companion component.
        if (changed != null && changed != self && action != Intent.ACTION_PACKAGE_REPLACED) return

        ShieldRepository.initialize(context)
        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ProtectionGuardianService::class.java),
            )
        }
    }
}
