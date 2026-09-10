package com.agon.app.blocklist.domain

/** Exact application packages that are always blocked in the background. */
object BuiltInBlockedApps {
    private val packages = hashSetOf(
        // Authoritative list from the shared link (yousf3.lovable.app) — applied verbatim.
        "mega.privacy.android.app", // MEGA
        "com.linkbox.plus.android", // LinkBox
        "hub.browser.video.downloader.saver", // Hub Video Downloader
        "iplayer.and.new.com", // iPlayer
        "com.lite.tera.iplayerbox", // iPlayer Box Lite
        "com.onyx.iplayer", // Onyx iPlayer
        "proxy.browser.unblock.sites.proxybrowser.unblocksites", // Unblock Proxy Browser
        "org.superfast.xbrowser", // SuperFast X Browser
        "com.browser.dhromebrowser", // DHrome Browser
        "com.x_browser", // X Browser
        "com.uc_browser_download", // UC Browser Download
        "com.browser.browserx", // BrowserX
        "com.browser.video.xdownloader", // BrowserX Video Downloader
        "com.smarttools.xbrowser.videodownloader", // XBrowser Video Downloader
        "com.xbrowser.video.browserdownloader.downloader", // XBrowser Browser Downloader
        "com.xsafebrowser.web", // XSafe Browser
        "com.xv.downloader.hd.player", // XV Downloader HD Player
        "com.screxo.tech", // Screxo
        "com.videlyy.top", // Videlyy
    )

    fun isBlocked(packageName: String): Boolean = packageName in packages
}
