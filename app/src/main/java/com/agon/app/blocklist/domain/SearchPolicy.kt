package com.agon.app.blocklist.domain

/**
 * Search engine policy shared by the VPN and the accessibility layer.
 *
 * Two independent defences are driven from here:
 *
 *  1. **DNS SafeSearch enforcement** — the search host is resolved to its provider-operated
 *     SafeSearch address instead of the normal one. This is the only officially supported way
 *     to constrain results for a request whose query string is inside TLS, and it survives
 *     refreshes, incognito, browser restarts and reboots because it happens at resolution time.
 *
 *  2. **Pre-submit keyword interception** — the accessibility layer recognises a search surface
 *     and blocks the term before the request is ever issued.
 */
object SearchPolicy {

    /**
     * Maps a search host onto the hostname that serves enforced-safe results.
     * Resolving the mapped name yields the provider's SafeSearch VIP, so no IP is hardcoded.
     */
    private val safeSearchHosts: Map<String, String> = buildMap {
        // Google — forcesafesearch covers every regional TLD.
        put("google.com", "forcesafesearch.google.com")
        put("www.google.com", "forcesafesearch.google.com")
        // YouTube — strictest restricted mode.
        put("youtube.com", "restrict.youtube.com")
        put("www.youtube.com", "restrict.youtube.com")
        put("m.youtube.com", "restrict.youtube.com")
        put("youtubei.googleapis.com", "restrict.youtube.com")
        put("youtube.googleapis.com", "restrict.youtube.com")
        // Bing
        put("bing.com", "strict.bing.com")
        put("www.bing.com", "strict.bing.com")
        // DuckDuckGo
        put("duckduckgo.com", "safe.duckduckgo.com")
        put("www.duckduckgo.com", "safe.duckduckgo.com")
        // Pixabay / Yandex family expose the same mechanism.
        put("yandex.com", "familysearch.yandex.ru")
        put("www.yandex.com", "familysearch.yandex.ru")
        put("yandex.ru", "familysearch.yandex.ru")
    }

    /** Regional Google domains (google.co.uk, google.de, ...) all map to forcesafesearch. */
    private const val GOOGLE_SAFE = "forcesafesearch.google.com"

    /**
     * Hostnames of engines that do not publish a SafeSearch VIP. They are recognised so the
     * accessibility layer still applies keyword filtering to their result pages.
     */
    private val plainSearchHosts: Set<String> = setOf(
        "search.yahoo.com", "yahoo.com", "www.yahoo.com",
        "search.brave.com", "brave.com",
        "ecosia.org", "www.ecosia.org",
        "startpage.com", "www.startpage.com",
        "qwant.com", "www.qwant.com",
        "baidu.com", "www.baidu.com",
        "ask.com", "www.ask.com",
        "aol.com", "search.aol.com",
        "searx.be", "mojeek.com", "lite.duckduckgo.com", "html.duckduckgo.com",
    )

    /**
     * Returns the hostname to resolve in place of [host] to enforce SafeSearch, or null when
     * the host is not a search engine with a SafeSearch endpoint.
     */
    fun safeSearchTarget(host: String): String? {
        if (host.isEmpty()) return null
        val normalized = TextNormalizer.normalizeDomain(host)
        if (normalized.isEmpty()) return null
        safeSearchHosts[normalized]?.let { return it }

        // Regional Google properties: google.<tld> and www.google.<tld>.
        val withoutWww = normalized.removePrefix("www.")
        if (withoutWww.startsWith("google.")) {
            val suffix = withoutWww.substringAfter("google.")
            // Guard against unrelated hosts such as google.com.evil.example.
            if (suffix.isNotEmpty() && suffix.count { it == '.' } <= 1) return GOOGLE_SAFE
        }
        return null
    }

    /** True when [host] is any recognised search engine surface. */
    fun isSearchHost(host: String): Boolean {
        if (host.isEmpty()) return false
        val normalized = TextNormalizer.normalizeDomain(host)
        if (normalized.isEmpty()) return false
        if (safeSearchHosts.containsKey(normalized) || normalized in plainSearchHosts) return true
        val withoutWww = normalized.removePrefix("www.")
        if (withoutWww.startsWith("google.")) return true
        return plainSearchHosts.any { normalized == it || normalized.endsWith(".$it") }
    }

    /**
     * True when the package is a dedicated search or browser surface whose editable field is a
     * search box, so a blocked keyword must be intercepted before submission.
     */
    fun isSearchSurface(packageName: String): Boolean =
        MonitoredApps.isBrowser(packageName) || packageName in SEARCH_APPS

    private val SEARCH_APPS: Set<String> = setOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.apps.searchlite",
        "com.microsoft.bing",
        "com.bing.search",
        "com.yahoo.mobile.client.android.search",
        "com.duckduckgo.mobile.android",
        "com.ecosia.android",
        "com.qwant.liberty",
        "com.yandex.searchapp",
        "com.brave.browser",
    )
}
