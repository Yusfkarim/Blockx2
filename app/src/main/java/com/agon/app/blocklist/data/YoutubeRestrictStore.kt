package com.agon.app.blocklist.data

import android.content.Context

/**
 * Persisted switch for the optional "YouTube Restricted Mode" DNS rewrite.
 *
 * The flag is read on the VPN packet hot path, so it is cached in a volatile field —
 * DataStore would be too slow there and the SharedPreferences read is in-memory after load.
 */
object YoutubeRestrictStore {

    private const val PREFS = "youtube_restrict"
    private const val KEY_ENABLED = "yt_restrict_enabled"

    @Volatile
    private var enabled = false

    @Volatile
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        enabled = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        initialized = true
    }

    fun isEnabled(): Boolean = initialized && enabled

    fun setEnabled(context: Context, value: Boolean) {
        initialize(context)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
        enabled = value
    }

    /** The official restricted-mode CNAME that YouTube publishes for restricted clients. */
    const val RESTRICT_TARGET = "restrict.youtube.com"

    /**
     * Official anycast IPs of YouTube's STRICT restricted-mode frontend (Google-operated, stable
     * for years — the same records that school routers answer with). Returned directly in the
     * DNS A answer so client-side DNS caches, direct-IP code paths and QUIC sessions all land on
     * the restricted endpoint without any extra upstream hop.
     */
    val RESTRICT_IPV4 = byteArrayOf(216.toByte(), 239.toByte(), 38.toByte(), 120.toByte())

    /** AAAA record of restrict.youtube.com (2001:4860:4802:32::78). */
    val RESTRICT_IPV6 = byteArrayOf(
        0x20, 0x01, 0x48, 0x60, 0x48, 0x02, 0x00, 0x32,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x78,
    )

    /**
     * Every YouTube surface covered by the rewrite: core site, mobile web, the app API hosts
     * (official app + ReVanced / NewPipe / Pure Tuber all resolve these exact hosts), music,
     * short links and the nocookie embeds. Third-party frontends cannot bypass the mode because
     * the rewrite happens at the DNS layer for these shared hostnames.
     */
    private val YOUTUBE_SUFFIXES = listOf(
        "youtube.com",
        "youtube.googleapis.com",
        "youtubei.googleapis.com",
        "ytimg.com",
        "youtu.be",
        "youtube-nocookie.com",
    )

    /** True when [domain] belongs to YouTube (exact or any sub-domain of the covered hosts). */
    fun isYoutubeHost(domain: String): Boolean {
        val normalized = domain.trim().lowercase().trimEnd('.')
        if (normalized.isEmpty()) return false
        return YOUTUBE_SUFFIXES.any { suffix ->
            normalized == suffix || normalized.endsWith(".$suffix")
        }
    }
}
