package com.agon.app.blocklist.domain

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Detects the Telegram in-app search (search icon tap or the search input screen) so the
 * accessibility layer can send the user back before search results are shown. It is deliberately
 * narrow: it only reacts to the search entry point, never to ordinary chat browsing, so Telegram
 * stays fully usable outside of search.
 */
object TelegramSearchPolicy {

    private val telegramPackages = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "org.telegram.graph",
        "nekox.messenger",
        "org.telegram.messenger.huawei",
        "com.radolyn.ayugram",
        "uz.unnarsx.cherrygram",
        "com.exteragram.messenger",
        "org.aka.messenger",
        "com.iMe.android",
        "org.telegram.BifToGram",
    )

    private val telegramPackagePrefixes = listOf(
        "org.telegram.",
        "org.thunderdog.challegram",
        "nekox.messenger",
        "com.radolyn.ayugram",
        "uz.unnarsx.cherrygram",
        "com.exteragram.messenger",
    )

    fun isTelegram(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName in telegramPackages) return true
        val lower = packageName.lowercase(Locale.ROOT)
        return telegramPackagePrefixes.any { lower.startsWith(it) || lower == it }
    }

    /**
     * True when [className] identifies a CHAT / channel / conversation host activity of
     * Telegram or its forks (ChatActivity and OEM-family variants). The global-search gate
     * uses this to free in-chat search interactions completely: only the main-screen
     * (chat-list) surface is ever challenged, everything inside an open conversation passes.
     */
    fun isChatContext(className: String?): Boolean {
        val value = className?.lowercase(Locale.ROOT).orEmpty()
        if (value.isEmpty()) return false
        return value.contains("chatactivity") ||
            value.contains("chat_activity") ||
            value.contains("chatactivitychannel") ||
            value.contains("conversationactivity")
    }

    /**
     * Typing inside the GLOBAL search field (its first character). Gates the interaction-only
     * blocking rule: it looks for an EDITABLE node whose own view-id, hint or class carries a
     * search marker — the in-chat message composer (also editable) never matches, by design.
     */
    fun isGlobalSearchTyping(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
        ) {
            return false
        }
        val node = event.source ?: return false
        return try {
            // Note: the endpoint node itself may be non-editable on some forks (wrapper views);
            // the marker-driven walk below handles both shapes identically.
            var current: AccessibilityNodeInfo? = node
            var hops = 0
            var hit = false
            while (current != null && hops < 6 && !hit) {
                val cls = current.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
                if (cls.contains("search") || cls.contains("searc")) {
                    hit = true
                    break
                }
                current.viewIdResourceName?.let { raw ->
                    val id = raw.substringAfterLast('/').lowercase(Locale.ROOT)
                    if (searchIdMarkers.any { id.contains(it) }) hit = true
                }
                if (!hit) {
                    current.hintTextCompat()?.let { hint ->
                        if (matchesSearch(hint)) hit = true
                    }
                }
                if (!hit) {
                    current.text?.toString()?.let { text ->
                        if (matchesSearch(text)) hit = true
                    }
                }
                if (!hit) {
                    current.contentDescription?.toString()?.let { contentDescription ->
                        if (matchesSearch(contentDescription)) hit = true
                    }
                }
                val parent = current.parent
                current.recycleCompat()
                current = parent
                hops++
            }
            hit
        } finally {
            node.recycleCompat()
        }
    }

    /** True when a URL contains the Telegram-only keyword that must be blocked on link open. */
    fun containsTelegramOnlyBlockedKeyword(url: String): Boolean =
        TextNormalizer.decodeUrl(url).contains(TELEGRAM_ONLY_BLOCKED_KEYWORD, ignoreCase = true)

    /**
     * True when [url] points at Telegram Web (web.telegram.org — the /k, /a and /z clients — or
     * the older webk/weba hosts). Used so the search protection also covers Telegram opened
     * inside a browser, not only the native app.
     */
    fun isTelegramWebUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val value = url.trim().lowercase(Locale.ROOT)
        if (value.contains("web.telegram.org") || value.contains("webk.telegram.org") ||
            value.contains("weba.telegram.org")
        ) {
            return true
        }
        val host = value.removePrefix("https://").removePrefix("http://")
            .substringBefore('/').substringBefore('?').trim()
        return host == "web.telegram.org" || host.endsWith(".web.telegram.org") ||
            host == "webk.telegram.org" || host == "weba.telegram.org"
    }

    /**
     * Search markers across the languages Telegram commonly localises its UI into. Telegram sets
     * its own strings (independent of the system language), so a multilingual set is required.
     * Matching is exact / word-boundary to avoid catching unrelated labels.
     */
    private val searchMarkers = setOf(
        "search", "search messages", "search for messages or users", "search chats",
        "search people", "search stickers", "global search",
        "بحث", "البحث", "بحث في الرسائل", "ابحث", "بحث عن", "ابحث عن رسائل",
        "بحث عن رسائل أو مستخدمين", "بحث في الدردشات",
        "گەڕان", "گەران", "گەڕان لە نامەکان",
        "جستجو", "جست‌وجو", "جست و جو",
        "ara", "arama", "sohbet ara",
        "поиск", "искать", "поиск сообщений",
        "buscar", "recherche", "rechercher", "suche", "suchen",
        "cerca", "ricerca", "pesquisar", "pesquisa",
    )

    /** viewId fragments that Telegram / forks expose on the search entry or field. */
    private val searchIdMarkers = listOf(
        "search",
        "menu_search",
        "search_field",
        "searchedittext",
        "search_src_text",
        "action_search",
        "open_search",
        "search_button",
        "search_item",
        "search_container",
        "search_wrap",
        "search_box",
        "searchbox",
        "search_input",
        "header_search",
        "top_search",
        "global_search",
        "search_bar",
        "searchbar",
        "menu_item_search",
        "ic_ab_search",
        "action_mode_search",
    )

    /**
     * The search screen shows a category tab row (Chats / Channels / Apps / Posts / Media …).
     * Seeing two or more of these together is a strong, focus-independent signal that the search
     * screen is open, so detection fires instantly instead of waiting for a focus event.
     */
    private val searchTabMarkers = setOf(
        // Unique-to-search tabs (avoid home "chats" and profile media tabs).
        "channels", "apps", "posts", "public posts", "downloads",
        "القنوات", "التطبيقات", "المنشورات", "منشورات",
        "کەناڵەکان", "ئەپەکان", "پۆستەکان",
        "کانال‌ها", "برنامه‌ها", "پست‌ها",
        "kanallar", "uygulamalar", "gönderiler",
        "каналы", "приложения", "посты",
    )

    /**
     * Markers that only appear on the media / attachment picker (gallery), never on Telegram's
     * chat-search screen. When any of these is present the picker's own focused search box must
     * NOT be treated as Telegram search, so sending a photo is never interrupted.
     */
    private val mediaPickerMarkers = setOf(
        "gallery", "camera", "allow access to camera", "media selected", "discard selection",
        "recents", "no access to camera", "open settings", "send photo", "send video",
        "send as file", "send without compression", "add caption", "caption", "photos",
        "search images", "search gifs", "search stickers", "gif", "gifs",
        "معرض", "المعرض", "الكاميرا", "كاميرا", "السماح بالوصول", "الوصول إلى الكاميرا",
        "الوسائط", "تجاهل", "الصور الحديثة", "المعرض الأخير", "وسائط", "إرسال الصورة",
        "أضف تعليق", "أضف تعليقًا", "تعليق", "الصور", "بحث عن صور",
        "گەلەری", "کامێرا", "کامیرا", "وێنەکان", "ناردنی وێنە",
        "گالری", "دوربین", "ارسال عکس",
        "галерея", "камера", "отправить фото",
        "galeri", "kamera",
    )

    private val mediaPickerIdMarkers = listOf(
        "photo_picker", "photopicker", "media_picker", "mediapicker", "attach", "gallery",
        "camera", "picker_grid", "photos_layout", "photo_viewer", "photoviewer",
        "caption", "editor", "media_grid", "attach_photo", "shareactivity", "photoattach",
    )

    /**
     * Fast search-entry detector driven by the event's own source node. Fires on click, select
     * OR focus, so touching the search icon or the search field is caught immediately — before
     * Telegram finishes drawing the search screen. It never reacts to a message input field
     * (only search-specific ids/labels match), so normal chatting is untouched.
     */
    fun isSearchEntry(event: AccessibilityEvent): Boolean {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> Unit
            else -> return false
        }

        // Class-name hint on the event itself (some skins put the widget class here).
        val eventClass = event.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (eventClass.contains("search")) return true

        val values = mutableListOf<String>()
        event.text?.forEach { value -> value?.toString()?.let(values::add) }
        event.contentDescription?.toString()?.let(values::add)

        var node = event.source
        var hops = 0
        var unlabeledTopBarIcon = false
        while (node != null && hops < 6) {
            node.viewIdResourceName?.let(values::add)
            node.text?.toString()?.let(values::add)
            node.contentDescription?.toString()?.let(values::add)
            node.hintTextCompat()?.let(values::add)
            val cls = node.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (cls.contains("search")) {
                node.recycleCompat()
                return true
            }
            // Track unlabeled top-bar icons; only used together with a search-ish parent id below.
            if (hops == 0 && looksLikeTopBarSearchControl(node)) {
                unlabeledTopBarIcon = true
            }
            val parent = node.parent
            node.recycleCompat()
            node = parent
            hops++
        }
        node?.recycleCompat()

        if (values.any { raw ->
                val id = raw.substringAfterLast('/').lowercase(Locale.ROOT)
                searchIdMarkers.any(id::contains) || matchesSearch(raw)
            }
        ) {
            return true
        }
        // Unlabeled magnifying-glass: only when a parent/self id still carries a search marker
        // fragment that is not a full label (already checked) — values may include raw ids.
        if (unlabeledTopBarIcon &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) &&
            values.any { raw ->
                val id = raw.substringAfterLast('/').lowercase(Locale.ROOT)
                id.contains("action") || id.contains("menu") || id.contains("header") ||
                    id.contains("toolbar") || id.contains("actionbar")
            }
        ) {
            return true
        }
        return false
    }

    /**
     * True when [event] is a tap/selection on the Telegram search icon (or its labelled parent).
     * Only click/select events are considered, so scrolling a chat list never matches.
     */
    fun isSearchClick(event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) {
            return false
        }
        return isSearchEntry(event)
    }

    /**
     * True only when the Telegram search field is actually OPEN (the user has entered search).
     * The home chat list shows a passive, unfocused search bar which must NOT match alone,
     * otherwise the user would be ejected from all of Telegram rather than only from search.
     */
    fun isSearchScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val result = ScanResult()
        scan(root, depth = 0, counter = Counter(), result = result)
        if (result.mediaPicker) return false
        return isSearchVerdict(result)
    }

    /**
     * True when a media / attachment picker (gallery, camera, photo editor, caption screen, GIF
     * or sticker search) is anywhere on screen. Used to completely suspend Telegram search
     * enforcement so choosing, captioning or searching media to send is never interrupted and
     * the keyboard is never dismissed.
     */
    fun isMediaPickerOnScreen(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val result = ScanResult()
        scan(root, depth = 0, counter = Counter(), result = result)
        return result.mediaPicker
    }

    /** Combined verdict from a single tree scan (cheaper than calling the two checks separately). */
    enum class Verdict { NONE, SEARCH, MEDIA_PICKER }

    fun evaluate(root: AccessibilityNodeInfo?): Verdict {
        if (root == null) return Verdict.NONE
        val result = ScanResult()
        scan(root, depth = 0, counter = Counter(), result = result)
        // A media picker anywhere on screen always wins, so sending photos is never interrupted.
        if (result.mediaPicker) return Verdict.MEDIA_PICKER
        return if (isSearchVerdict(result)) Verdict.SEARCH else Verdict.NONE
    }

    /**
     * True when the hosting activity/fragment class name itself is a search surface
     * (e.g. ActionBarSearch, DialogsSearchAdapter host). Used as an immediate signal on
     * WINDOW_STATE_CHANGED before the tree is fully populated.
     */
    fun isSearchClassName(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        val c = className.lowercase(Locale.ROOT)
        if (!c.contains("search")) return false
        // Avoid settings / privacy search screens inside Telegram.
        if (c.contains("settings") || c.contains("privacy") || c.contains("proxy")) return false
        return true
    }

    /**
     * Verdict for a browser tab. Returns SEARCH only when the current page is Telegram Web AND
     * the search screen is open (search-tab row Chats/Channels/Apps/Posts or a focused search
     * field). A single bounded scan does everything, so ordinary browsing is never affected.
     */
    fun evaluateWeb(root: AccessibilityNodeInfo?): Verdict {
        if (root == null) return Verdict.NONE
        val result = ScanResult()
        scan(root, depth = 0, counter = Counter(), result = result)
        if (!result.telegramWeb) return Verdict.NONE
        return if (isSearchVerdict(result)) Verdict.SEARCH else Verdict.NONE
    }

    /**
     * A page counts as "search" only when really open in front of the user: either the field is
     * focused, or the page shows the category tab row unique to global search (Chats / Channels /
     * Apps / Posts…). The bare "chrome + field" heuristic is retired: on Telegram's home screen
     * it matched a collapsed hidden container and caused the forced-exit bug.
     */
    private fun isSearchVerdict(result: ScanResult): Boolean {
        if (result.focusedSearchField) return true
        // Category tab row unique to global search (Channels/Apps/Posts…).
        if (result.searchTabs.size >= 2) return true
        // One unique search tab + a visible search field (focused or not) is enough — covers
        // skins that only show a subset of tabs while the search UI is open.
        if (result.searchTabs.isNotEmpty() && result.visibleSearchField) return true
        return false
    }

    private fun scan(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
        result: ScanResult,
    ) {
        if (node == null || depth > MAX_DEPTH || counter.visited >= MAX_NODES) return
        counter.visited++

        val id = node.viewIdResourceName.orEmpty()
        val normalizedId = id.substringAfterLast('/').lowercase(Locale.ROOT)
        if (mediaPickerIdMarkers.any(normalizedId::contains)) result.mediaPicker = true
        if (searchIdMarkers.any(normalizedId::contains)) result.searchChrome = true

        val hint = node.hintTextCompat()
        val values = listOfNotNull(node.text?.toString(), hint, node.contentDescription?.toString())
        if (values.any(::isMediaPickerLabel)) result.mediaPicker = true

        // Telegram Web marker: the address bar (or a WebView node) exposes web.telegram.org.
        if (id.lowercase(Locale.ROOT).let { it.contains("url") || it.contains("location") } &&
            values.any(::isTelegramWebUrl)
        ) {
            result.telegramWeb = true
        }
        if (values.any(::isTelegramWebUrl)) result.telegramWeb = true

        // Search-screen tab row (Channels / Apps / Posts …).
        for (raw in values) {
            val label = raw.trim().lowercase(Locale.ROOT).trim('.', ':', '،')
            if (label.length in 1..18 && searchTabMarkers.contains(label)) result.searchTabs.add(label)
        }

        val className = node.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
        if (className.contains("search") && !className.contains("settings")) {
            result.searchChrome = true
        }
        val isEditable = node.isEditable || className.contains("edittext")
        val looksSearchField = isEditable && (
            searchIdMarkers.any(normalizedId::contains) ||
                values.any(::matchesSearch) ||
                (hint != null && matchesSearch(hint))
            )
        if (looksSearchField) {
            result.visibleSearchField = true
            if (node.isFocused) result.focusedSearchField = true
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            scan(child, depth + 1, counter, result)
            child.recycleCompat()
            if (counter.visited >= MAX_NODES) return
        }
    }

    /**
     * Heuristic for the unlabeled magnifying-glass control in Telegram's top action bar.
     * Must be clickable (or a button/image), small, and located in the top strip of the screen.
     */
    private fun looksLikeTopBarSearchControl(node: AccessibilityNodeInfo): Boolean {
        if (!node.isClickable && !node.isLongClickable) {
            val cls = node.className?.toString()?.lowercase(Locale.ROOT).orEmpty()
            if (!cls.contains("image") && !cls.contains("button") && !cls.contains("actionmenu")) {
                return false
            }
        }
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        if (bounds.isEmpty) return false
        // Small control in the top action-bar band.
        if (bounds.height() > 220 || bounds.width() > 280) return false
        if (bounds.top > 280) return false
        // Prefer right-side action icons (search is usually top-right in LTR).
        // Still accept top-left for RTL layouts.
        return true
    }

    private fun isMediaPickerLabel(rawValue: String): Boolean {
        val label = rawValue.trim().lowercase(Locale.ROOT)
        if (label.isEmpty() || label.length > 40) return false
        return mediaPickerMarkers.any { marker ->
            label == marker || label.contains(marker)
        }
    }

    private class ScanResult(
        var focusedSearchField: Boolean = false,
        var visibleSearchField: Boolean = false,
        var searchChrome: Boolean = false,
        var mediaPicker: Boolean = false,
        var telegramWeb: Boolean = false,
        val searchTabs: MutableSet<String> = hashSetOf(),
    )

    private fun matchesSearch(rawValue: String): Boolean {
        val id = rawValue.substringAfterLast('/').lowercase(Locale.ROOT)
        if (searchIdMarkers.any(id::contains)) return true
        val label = rawValue.trim().lowercase(Locale.ROOT).trim('.', ':', '-', '،')
        if (label.isEmpty() || label.length > 48) return false
        return searchMarkers.any { marker ->
            label == marker || label.startsWith("$marker ") || label.endsWith(" $marker") ||
                label.contains(" $marker ") || label.startsWith("$marker…") ||
                label.startsWith("$marker...")
        }
    }

    private class Counter(var visited: Int = 0)

    private const val TELEGRAM_ONLY_BLOCKED_KEYWORD = "vibecast"
    private const val MAX_DEPTH = 40
    private const val MAX_NODES = 2500
}

private fun AccessibilityNodeInfo.hintTextCompat(): String? =
    if (android.os.Build.VERSION.SDK_INT >= 26) hintText?.toString() else null

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleCompat() {
    if (android.os.Build.VERSION.SDK_INT < 33) runCatching { recycle() }
}
