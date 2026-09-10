package com.agon.app.security

import android.content.Context
import android.os.SystemClock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Tamper-proof wall clock shared by every protection timer (commitment countdown, PIN lockout,
 * pause windows, limit cool-downs, urge blocks …).
 *
 * Model: a persisted pair of anchors — last known honest wall time and the monotonic
 * [SystemClock.elapsedRealtime] captured at the same instant. Every call recomputes the honest
 * "now" as `anchorReal + (elapsedNow - anchorElapsed)` and compares it with the device clock:
 *
 *  - **Consistent** (device clock inside ±[TOLERANCE_MILLIS] of elapsed progression) → the
 *    device reading is returned and the anchors tighten, so NTP jitter never accumulates.
 *  - **Tampered** (user moved the clock forwards or backwards beyond tolerance, including
 *    "set tomorrow" / "set yesterday") → the device reading is ignored and the monotonic
 *    honest estimate is returned. Protection timers keep ticking at the real rate and can
 *    never be ended early — the countdown freezes into truth until the clock is restored.
 *
 * Reboot resets elapsedRealtime only: anchors are re-locked without trusting a forged boot clock
 * (wall anchor only ever moves forward). Optional network sync ([maybeSyncWithNetwork]) re-binds
 * the anchor to real internet time whenever connectivity is available.
 *
 * Storage is SharedPreferences (same mechanism as every existing store in this project); the
 * data is integrity anchors, not secrets.
 */
object TamperProofClock {

    private const val PREFS = "tamper_proof_clock"
    private const val KEY_REAL_ANCHOR = "real_anchor"
    private const val KEY_ELAPSED_ANCHOR = "elapsed_anchor"
    private const val KEY_TAMPER_SUSPECTED = "tamper_suspected"

    /**
     * Max clock/device drift tolerated before a jump is treated as manipulation. Epoch time is
     * UTC — timezone changes and DST do NOT move it — so anything beyond ±90 seconds versus
     * elapsed-time progression can only come from a manual date/time change.
     */
    private const val TOLERANCE_MILLIS = 90_000L

    /** Network time is consulted at most this often (never on the accessibility hot path). */
    private const val NETWORK_SYNC_INTERVAL_MS = 10L * 60L * 1000L

    @Volatile
    private var lastNetworkSyncAtElapsed = 0L

    /**
     * Authoritative current epoch millis, immune to manual device-clock changes.
     * Cheap: in-memory preference reads after first load; safe on the main thread.
     */
    @Synchronized
    fun now(context: Context): Long {
        val prefs = prefs(context)
        val deviceNow = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()

        var anchorReal = prefs.getLong(KEY_REAL_ANCHOR, 0L)
        var anchorElapsed = prefs.getLong(KEY_ELAPSED_ANCHOR, 0L)

        if (anchorReal <= 0L || anchorElapsed <= 0L) {
            // First run on this device: the device clock is the initial truth.
            prefs.edit()
                .putLong(KEY_REAL_ANCHOR, deviceNow)
                .putLong(KEY_ELAPSED_ANCHOR, elapsedNow)
                .apply()
            return deviceNow
        }

        // Reboot resets elapsedRealtime; re-anchor without ever moving the honest clock back.
        if (elapsedNow < anchorElapsed) {
            anchorElapsed = elapsedNow
            anchorReal = maxOf(deviceNow, anchorReal)
            prefs.edit()
                .putLong(KEY_REAL_ANCHOR, anchorReal)
                .putLong(KEY_ELAPSED_ANCHOR, anchorElapsed)
                .apply()
        }

        val honestNow = anchorReal + (elapsedNow - anchorElapsed)
        val tampered = deviceNow - honestNow > TOLERANCE_MILLIS || honestNow - deviceNow > TOLERANCE_MILLIS

        if (tampered) {
            if (!prefs.getBoolean(KEY_TAMPER_SUSPECTED, false)) {
                prefs.edit().putBoolean(KEY_TAMPER_SUSPECTED, true).apply()
            }
            // Clock manipulation detected: NEVER end or shorten protection because of it.
            // Timers advance strictly at the real elapsed rate from the last honest moment.
            return honestNow
        }

        // Consistent reading: tighten the anchors so elapsed-time noise never accumulates.
        prefs.edit()
            .putLong(KEY_REAL_ANCHOR, deviceNow)
            .putLong(KEY_ELAPSED_ANCHOR, elapsedNow)
            .putBoolean(KEY_TAMPER_SUSPECTED, false)
            .apply()
        return deviceNow
    }

    /** True when a manual clock manipulation was detected and not yet resolved. */
    fun isClockTamperSuspected(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TAMPER_SUSPECTED, false)

    /**
     * Opportunity-only SNTP sync (called from the background guardian loop, already on an IO
     * dispatcher; throttled internally and skipped entirely while offline). A successful answer
     * re-anchors the honest clock to real internet time.
     */
    fun maybeSyncWithNetwork(context: Context) {
        val elapsedNow = SystemClock.elapsedRealtime()
        if (elapsedNow - lastNetworkSyncAtElapsed < NETWORK_SYNC_INTERVAL_MS) return
        lastNetworkSyncAtElapsed = elapsedNow
        val networkNow = fetchNetworkTime() ?: return
        prefs(context).edit()
            // Internet UTC beats both the device clock and local estimates.
            .putLong(KEY_REAL_ANCHOR, networkNow)
            .putLong(KEY_ELAPSED_ANCHOR, SystemClock.elapsedRealtime())
            .apply()
    }

    private fun fetchNetworkTime(): Long? {
        val hosts = arrayOf("time.cloudflare.com", "pool.ntp.org", "time.google.com")
        for (host in hosts) {
            val value = runCatching { sntpRequest(host) }.getOrNull()
            if (value != null && value > 0L) return value
        }
        return null
    }

    /** Minimal SNTP (RFC 4330) client: one 48-byte UDP packet, no dependencies. */
    private fun sntpRequest(host: String): Long {
        val address = InetAddress.getByName(host)
        val buffer = ByteArray(48)
        buffer[0] = 0x1B.toByte() // LI=0, VN=3, Mode=3 (client)
        val socket = DatagramSocket()
        try {
            socket.soTimeout = 2_500
            socket.send(DatagramPacket(buffer, buffer.size, address, 123))
            val response = ByteArray(48)
            socket.receive(DatagramPacket(response, response.size))
            var seconds = 0L
            for (index in 40..43) seconds = seconds shl 8 or (response[index].toLong() and 0xFF)
            var fraction = 0L
            for (index in 44..47) fraction = fraction shl 8 or (response[index].toLong() and 0xFF)
            val millis = fraction * 1_000L / 4_294_967_296L
            // NTP epoch (1900) → Unix epoch (1970): 2,208,988,800 seconds.
            return (seconds - 2_208_988_800L) * 1_000L + millis
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
