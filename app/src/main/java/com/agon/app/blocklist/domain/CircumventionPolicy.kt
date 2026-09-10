package com.agon.app.blocklist.domain

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects tools and web services whose primary purpose is bypassing the active family filter.
 * This policy is independent of the user-managed content blocklist, so a whitelist rule cannot
 * accidentally permit a VPN, proxy, Tor client, or tunnelling service.
 */
object CircumventionPolicy {

    private val appDecisionCache = ConcurrentHashMap<String, Boolean>()

    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    // Loose IPv6 character check; combined with a colon test it is enough to flag a bare IPv6 host.
    private val IPV6_CHARS = Regex("""^[0-9a-f:]+$""")

    private val exactPackages = setOf(
        "org.torproject.android",
        "org.torproject.torbrowser",
        "com.cloudflare.onedotonedotonedotone",
        "com.wireguard.android",
        "com.psiphon3.subscription",
        "com.psiphon3",
        "org.outline.android.client",
        "com.github.shadowsocks",
        "com.github.kr328.clash",
        "com.github.kr328.clash.foss",
        "com.v2ray.ang",
        "io.nekohasekai.sagernet",
        "io.nekohasekai.sfa",
    )

    private val packageMarkers = listOf(
        ".vpn", "vpn.", ".proxy", "proxy.", "orbot", "torproject", "wireguard",
        "openvpn", "shadowsocks", "psiphon", "ultrasurf", "lantern", "outline",
        "v2ray", "xray", "singbox", "nekobox", "sagernet", "clash", "tunnelbear",
        "hotspotshield", "betternet", "supervpn", "securevpn", "thundervpn", "hola",
        "windscribe", "protonvpn", "nordvpn", "expressvpn", "surfshark",
    )

    private val labelMarkers = listOf(
        "vpn", "proxy", "tor browser", "orbot", "wireguard", "openvpn", "shadowsocks",
        "psiphon", "ultrasurf", "lantern", "outline", "v2ray", "xray", "sing-box",
        "nekobox", "sagernet", "clash", "tunnelbear", "hotspot shield", "anonymizer",
    )

    private val blockedDomains = setOf(
        "proxysite.com", "croxyproxy.com", "croxyproxy.rocks", "proxyium.com",
        "kproxy.com", "4everproxy.com", "hidester.com", "hide.me", "hidemyass.com",
        "blockaway.net", "genmirror.com", "filterbypass.me", "webproxy.to",
        "proxybrowser.xyz", "unblockit.foo", "unblockit.boo", "unblockit.vegas",
        "psiphon.ca", "ultrasurf.us", "torproject.org", "protonvpn.com",
        "nordvpn.com", "expressvpn.com", "surfshark.com", "windscribe.com",
        "tunnelbear.com", "hotspotshield.com", "betternet.co", "hola.org",
        "openvpn.net", "wireguard.com", "hideip.me", "whoer.net",
        // Encrypted resolver endpoints can bypass the protected system resolver.
        "dns.google", "cloudflare-dns.com", "chrome.cloudflare-dns.com", "one.one.one.one",
        "mozilla.cloudflare-dns.com", "doh.opendns.com", "dns.quad9.net", "dns11.quad9.net",
        "dns.nextdns.io", "doh.cleanbrowsing.org", "doh.dns.sb", "dns.adguard-dns.com",
        "dns-family.adguard.com", "doh.mullvad.net", "dns.mullvad.net", "freedns.controld.com",
    )

    private val domainMarkers = listOf(
        "webproxy", "croxyproxy", "proxysite", "proxyium", "kproxy", "4everproxy",
        "filterbypass", "unblock", "hideip", "hidemyass", "vpnproxy", "freeproxy",
        "psiphon", "ultrasurf", "shadowsocks", "wireguard", "openvpn",
    )

    private val textMarkers = listOf(
        "free web proxy", "online web proxy", "web proxy", "proxy server", "vpn proxy",
        "free vpn", "connect vpn", "unblock websites", "unblock sites", "bypass blocked",
        "bypass filter", "hide your ip", "hide my ip", "browse anonymously",
        "anonymous browsing", "tor browser", "download vpn", "proxy browser",
    )

    fun isCircumventionApp(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == context.packageName) return false
        return appDecisionCache.getOrPut(packageName) { inspectPackage(context, packageName) }
    }

    /** Any device-level HTTP proxy would move resolution outside the protected connection. */
    fun isSystemProxyActive(context: Context): Boolean = runCatching {
        val proxy = context.getSystemService(ConnectivityManager::class.java)?.defaultProxy
        proxy != null && !proxy.host.isNullOrBlank() && proxy.port > 0
    }.getOrDefault(false)

    /**
     * Clean True Positive Filtering Only (false-blocking fix): [domainMarkers] are matched
     * against each DNS LABEL exactly, never as a blanket substring across the whole host. A
     * blanket `normalized.contains(marker)` check previously flagged any domain that merely
     * CONTAINED a marker anywhere — e.g. "unblock" inside an unrelated host like
     * "sunblockstore.example.com" — as a circumvention service even though it was never added
     * to the user's own blocklist. Matching per-label instead means a domain is only ever
     * caught here when one of its labels IS the marker verbatim (e.g. "webproxy.example.com"),
     * which is exactly how the curated [blockedDomains] set above already behaves.
     */
    fun domainBlockReason(domain: String): String? {
        val normalized = TextNormalizer.normalizeDomain(domain)
        if (normalized.isBlank()) return null
        if (blockedDomains.any { normalized == it || normalized.endsWith(".$it") }) {
            return "Circumvention service blocked"
        }
        if (normalized.split('.').any { label -> label in domainMarkers }) {
            return "Circumvention service blocked"
        }
        return null
    }

    fun inputBlockReason(value: String): String? {
        if (value.isBlank()) return null
        if (isRawPublicIpHost(value)) return "Direct IP access blocked"
        domainBlockReason(value)?.let { return it }
        val normalized = TextNormalizer.normalize(TextNormalizer.decodeUrl(value))
        if (textMarkers.any { normalized.contains(it) }) return "Circumvention service blocked"
        // Single-token detection is only appropriate for a short address/search input. It must
        // never classify a full article body that merely mentions one of these technologies.
        if (normalized.length <= MAX_INPUT_LENGTH) {
            val tokens = TextNormalizer.tokenize(normalized)
            if (tokens.any { it in setOf("vpn", "proxy", "psiphon", "ultrasurf", "orbot", "tor") }) {
                return "Circumvention service blocked"
            }
        }
        return null
    }

    /**
     * True when [value] is a browser address whose host is a bare PUBLIC IP address (v4 or v6).
     * Visiting a site by raw public IP performs no DNS lookup, so it would bypass DNS-based
     * filtering entirely. Private/loopback/link-local ranges (routers, local devices, localhost)
     * are intentionally allowed so ordinary local access keeps working.
     */
    private fun isRawPublicIpHost(value: String): Boolean {
        var host = value.trim().lowercase(Locale.ROOT)
            .removePrefix("https://").removePrefix("http://")
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        // Strip an IPv6 bracket form [::1]:443 and any :port suffix (but keep bare IPv6 colons).
        if (host.startsWith("[")) {
            host = host.substringAfter('[').substringBefore(']')
        } else if (host.count { it == ':' } == 1) {
            host = host.substringBefore(':')
        }
        if (host.isEmpty()) return false
        return when {
            IPV4_REGEX.matches(host) -> !isPrivateIpv4(host)
            host.contains(':') && IPV6_CHARS.matches(host) -> !isLocalIpv6(host)
            else -> false
        }
    }

    private fun isPrivateIpv4(ip: String): Boolean {
        val p = ip.split('.').mapNotNull { it.toIntOrNull() }
        if (p.size != 4 || p.any { it !in 0..255 }) return true // malformed -> don't block
        return when {
            p[0] == 10 -> true
            p[0] == 127 -> true                       // loopback
            p[0] == 192 && p[1] == 168 -> true
            p[0] == 172 && p[1] in 16..31 -> true
            p[0] == 169 && p[1] == 254 -> true        // link-local
            p[0] == 0 -> true
            else -> false
        }
    }

    private fun isLocalIpv6(ip: String): Boolean {
        return ip == "::1" || ip.startsWith("fe80") || ip.startsWith("fc") || ip.startsWith("fd") || ip == "::"
    }

    private fun inspectPackage(context: Context, packageName: String): Boolean {
        val normalizedPackage = packageName.lowercase(Locale.ROOT)
        if (normalizedPackage in exactPackages || packageMarkers.any(normalizedPackage::contains)) {
            return true
        }

        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SERVICES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SERVICES)
            }
        }.getOrNull()

        if (packageInfo?.services.orEmpty().any { service ->
                service.permission == "android.permission.BIND_VPN_SERVICE"
            }
        ) {
            return true
        }

        val label = runCatching {
            val applicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0)
            }
            context.packageManager.getApplicationLabel(applicationInfo).toString()
                .lowercase(Locale.ROOT)
        }.getOrDefault("")
        return labelMarkers.any(label::contains)
    }

    private const val MAX_INPUT_LENGTH = 200
}
