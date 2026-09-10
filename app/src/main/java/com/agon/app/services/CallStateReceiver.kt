package com.agon.app.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import android.telephony.TelephonyManager
import com.agon.app.accessibility.ShieldAccessibilityService

/**
 * Instant phone-call awareness for the blocking overlays.
 *
 * Contract (In-Call Overlay Policy):
 *  1. A block screen (app / schedule / urge / quarantine / website) that is VISIBLE when a call
 *     starts (RINGING or OFFHOOK) gets out of the way of the call UI immediately, so the user
 *     can always see and answer the phone. Nothing else about the block is relaxed — the
 *     engines keep enforcing rules in the background the whole call.
 *  2. At IDLE (call ended) the exact same screen is restored at 0ms — but only while the
 *     blocked subject is still in the foreground (otherwise the user legitimately moved on
 *     during the call and the pending restore is dropped).
 *  3. The mechanism needs no runtime permission: the system `PHONE_STATE` broadcast carries the
 *     state (no number) to manifest receivers on every Android version and every OEM skin.
 */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING,
            TelephonyManager.EXTRA_STATE_OFFHOOK,
            -> CallOverlayGate.onCallActive(context)
            TelephonyManager.EXTRA_STATE_IDLE -> CallOverlayGate.onCallIdle(context)
        }
    }
}

/**
 * Shared, process-local call-overlay state. Both block screens register their "please finish"
 * listener through [attach]; the manifest receiver flips the call state; the accessibility
 * service performs the 0ms restore on [ShieldAccessibilityService.restoreSuppressedOverlay].
 */
object CallOverlayGate {

    /** App-private broadcast both block screens listen to while they are visible. */
    const val ACTION_SUPPRESS_OVERLAYS = "com.agon.app.action.CALL_OVERLAY_SUPPRESS"

    /** Everything needed to rebuild the hidden overlay byte-for-byte after the call. */
    data class SuppressedOverlay(
        val kindApp: Boolean,
        val packageName: String,
        val timeLimit: Boolean = false,
        val scheduleName: String? = null,
        val quarantine: Boolean = false,
        val domain: String = "",
        val reason: String = "",
        val category: String = "",
        val confidence: Int = 100,
        val time: Long = 0L,
    )

    /** The overlay suspended by the ongoing call (null when none). */
    @Volatile
    private var pending: SuppressedOverlay? = null

    /** True while a call is ringing or ongoing. */
    @Volatile
    var callActive: Boolean = false
        private set

    fun onCallActive(context: Context) {
        if (callActive) return
        callActive = true
        // Ask every visible block screen to finish now so the call UI owns the display.
        runCatching {
            context.sendBroadcast(Intent(ACTION_SUPPRESS_OVERLAYS).setPackage(context.packageName))
        }
    }

    fun onCallIdle(context: Context) {
        if (!callActive) return
        callActive = false
        val snapshot = pending ?: return
        // 0ms restore via the live accessibility service (privileged foreground start). Without
        // a live service the pending restore is dropped: the protection engine blocks again on
        // the very next interaction with the subject anyway.
        val service = ShieldAccessibilityService.activeService
        if (service == null) {
            pending = null
            return
        }
        pending = null
        service.restoreSuppressedOverlay(snapshot)
    }

    fun pendingOverlay(): SuppressedOverlay? = pending

    fun clearPendingOverlay() {
        pending = null
    }

    /**
     * Subscribes [activity] to call-suppression. On a call start the overlay records its own
     * restoration descriptor and finishes itself, so the phone UI is never covered.
     *
     * @return the registered receiver — pass it back to [detach] from `onDestroy`.
     */
    fun attach(activity: ComponentActivity, descriptor: () -> SuppressedOverlay): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_SUPPRESS_OVERLAYS) return
                pending = descriptor()
                activity.runOnUiThread { activity.finish() }
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_SUPPRESS_OVERLAYS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    fun detach(activity: ComponentActivity, receiver: BroadcastReceiver?) {
        receiver ?: return
        runCatching { activity.unregisterReceiver(receiver) }
    }
}
