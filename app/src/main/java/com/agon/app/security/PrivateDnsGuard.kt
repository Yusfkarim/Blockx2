package com.agon.app.security

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects Android system-level Private DNS (DNS-over-TLS / DoH-style hostnames).
 *
 * When Private DNS is configured, device DNS leaves the local VPN tunnel and bypasses
 * [com.agon.app.vpn.FamilyVpnService] filtering. This guard exposes that state for UI alerts
 * and accessibility ejection of the Private DNS settings surfaces.
 */
object PrivateDnsGuard {

    /** True when Private DNS would bypass the local DNS tunnel filter. */
    fun isBypassActive(context: Context): Boolean {
        val mode = runCatching {
            Settings.Global.getString(context.contentResolver, PRIVATE_DNS_MODE)
        }.getOrNull().orEmpty()
        val specifier = configuredHostname(context)
        // Hostname mode is the hard bypass. Opportunistic + non-empty specifier is also treated
        // as active on many OEM skins that still encrypt DNS outside the VPN tunnel.
        return mode.equals(MODE_HOSTNAME, ignoreCase = true) ||
            mode.equals(MODE_OPPORTUNISTIC, ignoreCase = true) ||
            specifier.isNotBlank()
    }

    /** Configured Private DNS hostname, or empty when none. */
    fun configuredHostname(context: Context): String = runCatching {
        Settings.Global.getString(context.contentResolver, PRIVATE_DNS_SPECIFIER).orEmpty()
    }.getOrDefault("")

    /** True when the visible settings window is the Private DNS configuration surface. */
    fun isPrivateDnsSettingsSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val pkg = packageName.lowercase()
        if (!pkg.contains("settings") && pkg != "com.android.settings") return false
        val klass = activityClass.lowercase()
        if (klass.isNotEmpty() && PRIVATE_DNS_CLASS_MARKERS.any { klass.contains(it) }) return true
        return rootHasPrivateDnsEvidence(root)
    }

    private fun rootHasPrivateDnsEvidence(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return walkPrivateDnsEvidence(root, 0, 0)
    }

    private fun walkPrivateDnsEvidence(node: AccessibilityNodeInfo?, depth: Int, visited: Int): Boolean {
        if (node == null || depth > 10 || visited > 260) return false
        var count = visited + 1
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (id.isNotEmpty() && PRIVATE_DNS_VIEW_ID_TOKENS.any { id.contains(it) }) return true
        val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in values) {
            val lowered = raw.trim().lowercase()
            if (lowered.isEmpty() || lowered.length > 48) continue
            if (PRIVATE_DNS_TEXT_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (walkPrivateDnsEvidence(child, depth + 1, count)) return true
            count++
        }
        return false
    }

    private const val PRIVATE_DNS_MODE = "private_dns_mode"
    private const val PRIVATE_DNS_SPECIFIER = "private_dns_specifier"
    private const val MODE_HOSTNAME = "hostname"
    private const val MODE_OPPORTUNISTIC = "opportunistic"

    private val PRIVATE_DNS_CLASS_MARKERS = listOf(
        "privatedns",
        "private_dns",
        "privatednsmode",
        "privatednsconfiguration",
        "privatednssettings",
        "networkprivatedns",
    )

    private val PRIVATE_DNS_VIEW_ID_TOKENS = listOf(
        "private_dns",
        "privatedns",
        "dns_mode",
        "dns_specifier",
    )

    private val PRIVATE_DNS_TEXT_WORDS = listOf(
        "private dns",
        "dns خاص",
        "dns الخاص",
        "دی ان ئی ئێس تایبەت",
        "dns تایبەت",
        "dns خاص",
        "private dns provider hostname",
        "hostname of private dns",
        "dns-over-tls",
        "dns over tls",
        "dns.google",
        "one.one.one.one",
        "cloudflare-dns",
        "adguard-dns",
    )
}
