package com.agon.app.ui.routes

import android.content.Context
import com.agon.app.settingsprotection.data.ProtectionCommitmentStore

/**
 * Protection-layer helpers (Standard / Strong) — **never** starts or stops [com.agon.app.vpn.FamilyVpnService].
 *
 * Safe Browsing VPN is owned exclusively by [com.agon.app.vpn.CommitmentTimerModule] + the
 * independent درع التصفح controls.
 */
object ProtectionSettingsRoute {

    /**
     * Arms Standard or Strong protection commitment only.
     * Does not touch VPN desired state or FamilyVpnService.
     */
    fun applyProtectionCommitment(context: Context, daysOption: Int, isStrong: Boolean) {
        when {
            isStrong -> ProtectionCommitmentStore.setStrongDays(context, daysOption)
            daysOption == ProtectionCommitmentStore.FOREVER.toInt() ->
                ProtectionCommitmentStore.setForever(context)
            else -> ProtectionCommitmentStore.setNormalDays(context, daysOption)
        }
        ProtectionCommitmentStore.setStopped(context, false)
    }

    /**
     * Stops Standard protection commitment only (blocked while Strong is active).
     * Does not disconnect VPN / Safe Browsing.
     */
    fun stopStandardProtectionOnly(context: Context): Boolean {
        if (ProtectionCommitmentStore.isStrongActive(context)) return false
        ProtectionCommitmentStore.setStopped(context, true)
        ProtectionCommitmentStore.clearCommitment(context)
        return true
    }
}
