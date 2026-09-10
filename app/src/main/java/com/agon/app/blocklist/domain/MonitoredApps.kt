package com.agon.app.blocklist.domain

/**
 * Package sets used by the accessibility layer to decide how a window should be inspected.
 *
 * Centralised here so the service keeps a single immutable source of truth instead of rebuilding
 * collections on every event.
 */
object MonitoredApps {

    /** Browsers whose URL bar and page content are inspected. */
    val BROWSERS: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.chromium.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.brave.browser",
        "com.brave.browser_nightly",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.UCMobile.intl",
        "com.yandex.browser",
        "mark.via.gp",
        "acr.browser.lightning",
        "org.torproject.torbrowser",
        // Additional widely-installed browsers so keyword/URL protection is not limited to
        // Chrome-family browsers even before runtime discovery completes.
        "org.mozilla.fenix",
        "org.mozilla.firefox_nightly",
        "com.opera.browser.beta",
        "com.opera.mini.native.beta",
        "com.android.browser",
        "com.mi.globalbrowser",
        "com.miui.browser",
        "com.huawei.browser",
        "com.heytap.browser",
        "com.coloros.browser",
        "com.vivo.browser",
        "com.oneplus.browser",
        "com.transsion.phoenix",
        "com.ecosia.android",
        "com.aloha.browser",
        "alook.browser",
        "alook.browser.google",
        "com.mycompany.app.soulbrowser",
        "com.naver.whale",
        "mobi.mgeek.TunnyBrowser",
        "com.dolphin.browser.express.web",
        "com.cloudmosa.puffinFree",
        "com.uc.browser.en",
        "com.ucmobile.lab",
        "org.cromite.cromite",
        "org.bromite.bromite",
        "us.spotco.fennec_dos",
        "io.github.forkmaintainers.iceraven",
        "org.adblockplus.browser",
        "com.qwant.liberty",
        "com.startpage.app",
        "com.sec.android.app.sbrowser.lite",
        "net.fast.web.browser",
        "com.apusapps.browser",
        "com.hsv.freeadblockerbrowser",
    )

    /** Search, social, messaging, mail and notes apps that expose editable text. */
    val TEXT_SURFACES: Set<String> = setOf(
        // AI assistants: their prompt boxes are ordinary editable nodes, so the same keyword
        // filter applies to anything typed into them.
        "com.openai.chatgpt",
        "com.google.android.apps.bard",
        "com.microsoft.copilot",
        "com.microsoft.bing.copilot",
        "ai.perplexity.app.android",
        "com.anthropic.claude",
        "com.deepseek.chat",
        "xyz.grok.android",
        // Search
        "com.google.android.googlequicksearchbox",
        "com.microsoft.bing",
        "com.bing.search",
        "com.yahoo.mobile.client.android.search",
        "com.yahoo.mobile.client.android.mail",
        // Social
        "com.instagram.android",
        "com.instagram.lite",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go",
        "com.ss.android.ugc.trill",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.snapchat.android",
        "com.twitter.android",
        "com.twitter.android.lite",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.linkedin.android",
        "com.google.android.youtube",
        "anddea.youtube",
        "com.tumblr",
        // Messaging
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.thunderdog.challegram",
        "com.viber.voip",
        "com.facebook.orca",
        "com.discord",
        "com.google.android.apps.messaging",
        "org.thoughtcrime.securesms",
        "com.imo.android.imoim",
        "jp.naver.line.android",
        // Mail
        "com.google.android.gm",
        "com.microsoft.office.outlook",
        "com.samsung.android.email.provider",
        "ru.yandex.mail",
        "com.fsck.k9",
        // Notes
        "com.google.android.keep",
        "com.samsung.android.app.notes",
        "com.microsoft.office.onenote",
        "com.evernote",
        "com.simplenote.android",
    )

    /** System surfaces that must never be treated as user content. */
    val EXCLUDED: Set<String> = setOf(
        "com.android.systemui",
        "android",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.touchtype.swiftkey",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
    )

    fun isBrowser(packageName: String): Boolean = packageName in BROWSERS

    fun isTextSurface(packageName: String): Boolean = packageName in TEXT_SURFACES

    fun isExcluded(packageName: String): Boolean = packageName in EXCLUDED
}
