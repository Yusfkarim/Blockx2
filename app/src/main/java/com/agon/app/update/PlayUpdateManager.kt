package com.agon.app.update

import android.app.Activity
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Thin wrapper over Google Play's In-App Update API. When the app was installed from Google Play
 * and an update is available, it launches Play's IMMEDIATE (full-screen, blocking) update flow so
 * the user must update in place. Silently no-ops for side-loaded installs (Play returns no update),
 * where the remote-manifest gate handles the prompt instead.
 */
object PlayUpdateManager {

    const val REQUEST_CODE = 7391

    /**
     * Asks Play for an update and, if one is available and allowed, starts the IMMEDIATE flow.
     * Returns true when a flow was started (so the caller can wait), false otherwise. Never throws.
     */
    fun startImmediateIfAvailable(activity: Activity, onNoUpdate: () -> Unit = {}) {
        runCatching {
            val manager = AppUpdateManagerFactory.create(activity)
            manager.appUpdateInfo
                .addOnSuccessListener { info ->
                    val canImmediate =
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    val resuming =
                        info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                    if (canImmediate || resuming) {
                        runCatching {
                            manager.startUpdateFlowForResult(
                                info,
                                AppUpdateType.IMMEDIATE,
                                activity,
                                REQUEST_CODE,
                            )
                        }.onFailure { onNoUpdate() }
                    } else {
                        onNoUpdate()
                    }
                }
                .addOnFailureListener { onNoUpdate() }
        }.onFailure { onNoUpdate() }
    }
}
