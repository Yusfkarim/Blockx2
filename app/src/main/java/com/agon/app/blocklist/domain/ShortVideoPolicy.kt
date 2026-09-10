package com.agon.app.blocklist.domain

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URI
import java.util.Locale

/**
 * Detects dedicated short-video surfaces while leaving normal social feeds, profiles, messages,
 * and long-form players usable. Full and Lite variants are covered, as are web URLs and preview
 * cards that navigate from a normal home feed into a Reels/Shorts player.
 */
object ShortVideoPolicy {

    enum class Platform(val displayName: String) {
        INSTAGRAM("Instagram Reels"),
        FACEBOOK("Facebook Reels"),
        YOUTUBE("YouTube Shorts"),
        TIKTOK("TikTok short videos"),
        TWITTER("X / Twitter short videos"),
    }

    private val nativePlatforms = mapOf(
        "com.instagram.android" to Platform.INSTAGRAM,
        "com.instagram.lite" to Platform.INSTAGRAM,
        "com.facebook.katana" to Platform.FACEBOOK,
        "com.facebook.lite" to Platform.FACEBOOK,
        "com.google.android.youtube" to Platform.YOUTUBE,
        "com.google.android.youtube.lite" to Platform.YOUTUBE,
        "com.zhiliaoapp.musically" to Platform.TIKTOK,
        "com.zhiliaoapp.musically.go" to Platform.TIKTOK,
        "com.ss.android.ugc.trill" to Platform.TIKTOK,
        "com.ss.android.ugc.aweme" to Platform.TIKTOK,
        "com.ss.android.ugc.aweme.lite" to Platform.TIKTOK,
        "com.twitter.android" to Platform.TWITTER,
        "com.twitter.android.lite" to Platform.TWITTER,
    )

    private val reelLabels = setOf(
        "reel", "reels", "watch reels", "facebook reels", "instagram reels",
        "reels and short videos", "short videos", "facebook short videos",
        "ريل", "ريلز", "مشاهدة ريلز", "مقاطع ريلز", "ریلز", "ریل", "ڕیل", "ڕیلز", "ڕیلەکان",
        "فيديوهات قصيرة", "مقاطع فيديو قصيرة",
    )

    private val shortsLabels = setOf(
        "short", "shorts", "youtube shorts", "شورتس", "فيديو قصير",
        "فيديوهات قصيرة", "ویدئوی کوتاه", "ڤیدیۆی کورت", "کورتە ڤیدیۆ",
        "شۆرت", "شۆرتس",
    )

    private val tiktokLabels = setOf(
        "tiktok", "for you", "for you feed", "foryou", "following",
        "لك", "من أجلك", "أتابعه", "تتابعه", "بۆ تۆ", "بۆ ئێوە", "شوێنکەوتن",
    )

    private val twitterVideoLabels = setOf(
        "video", "play video", "video player", "immersive video", "media viewer",
        "تشغيل الفيديو", "مشغل الفيديو", "فيديو", "ڤیدیۆ", "لێدانی ڤیدیۆ",
    )

    /** IDs that appear only after the dedicated short-video viewer has opened. */
    private val commonPlayerIdMarkers = listOf(
        "reel_player_page", "reels_player_page", "reel_viewer", "reels_viewer",
        "clips_viewer", "shorts_player", "shorts_viewer", "shorts_container",
        // Newer layouts (YouTube/Facebook rename their containers over time).
        // NOTE: deliberately excludes "reel_feed" / "reel_feed_item" — those appear on
        // Instagram's HOME feed and must never count as the full-screen Reels player.
        "reel_watch", "shorts_watch", "shorts_feed",
        "reels_pager", "reel_pager", "shorts_pager", "shortsplayer", "reelsplayer",
        "clips_player", "shorts_video_container", "shorts_video_item",
        // In-player chrome that exists only while a short is actually playing:
        "reel_progress", "shorts_progress", "reel_player_underlay",
        "clips_viewer_view_pager", "reels_tray_viewer", "clips_video_container",
    )

    /** IDs frequently exposed on a Reels/Shorts preview card inside a normal feed. */
    private val platformEntryIdMarkers = mapOf(
        Platform.INSTAGRAM to listOf(
            "clips_media", "reel_video", "reels_video", "clips_item", "reel_item",
            "clips_tab",
        ),
        Platform.FACEBOOK to listOf(
            "reel_video", "reels_video", "reel_unit", "reels_unit", "reels_card",
            "reel_card", "reel_thumbnail", "reels_thumbnail",
            // Additional Facebook Lite Reels entry markers (feed cards/shelf items).
            "reel_play_button", "reels_play_button",
            "reel_card_container", "reels_card_container",
            "reel_shelf", "reels_shelf",
            "reel_section", "reels_section",
            "reels_rail", "reel_rail",
            "reels_feed_item", "reel_feed_item",
            "reels_preview", "reel_preview",
            "fbreels_card", "fbreel_card",
        ),
        Platform.YOUTUBE to listOf(
            "reel_item", "shorts_item", "shorts_thumbnail", "reel_thumbnail",
            "reel_video", "shorts_video", "shorts_lockup",
        ),
        Platform.TIKTOK to listOf("aweme_video", "video_view", "feed_video", "viewpager"),
        Platform.TWITTER to listOf("video_container", "video_player", "video_thumbnail"),
    )

    private val platformPlayerIdMarkers = mapOf(
        Platform.INSTAGRAM to listOf(
            // Immersive full-screen Reels/Clips player containers only.
            "clips_viewer_view_pager", "clips_viewer", "reels_viewer", "clips_player",
            "clips_video_container", "clips_swipe_refresh",
            "reels_viewer_view_pager", "reels_player", "reel_viewer",
            "clips_viewer_video_layout", "reels_video_player", "clips_video_player",
            "gesture_manager", // often present on immersive clips pager (paired with other checks)
            "reels_tab_root", "clips_tab_root",
        ),
        Platform.FACEBOOK to listOf(
            "reels_viewer", "reels_player", "reel_viewer", "reel_player_page",
            "reel_video_player", "reels_video_player", "fullscreen_reel", "reels_pager",
            // Facebook Lite Reels player markers (Lite uses different view IDs).
            "reel_player", "reels_container", "reel_container",
            "reels_fullscreen", "reel_fullscreen", "short_video_player",
            "fbreels_player", "fbreels_viewer", "fbreel_player",
            "reels_player_container", "reel_player_container",
            "reel_viewer_container", "reels_viewer_container",
            // Facebook Lite often uses generic IDs; these are more specific to the Reels surface.
            "reels_surface", "reel_surface", "reels_overlay",
            // Additional Facebook Lite Reels player ID markers.
            "reel_player_view", "reels_player_view", "reel_view_pager", "reels_view_pager",
            "reel_full_screen", "reels_full_screen",
            "short_video_player_view", "short_video_container",
            "reels_swipe_layout", "reel_swipe_layout",
            "reels_scroll_container", "reel_scroll_container",
            "fbreels_container", "fbreel_container",
            "reels_video_container", "reel_video_container",
            "reels_player_layout", "reel_player_layout",
            "reels_viewer_layout", "reel_viewer_layout",
            "reels_pager_layout", "reel_pager_layout",
        ),
        Platform.YOUTUBE to listOf(
            "reel_player_page", "reel_watch_player", "reel_recycler",
            "shorts_player", "shorts_container", "reel_player_overlay",
            // Additional YouTube Shorts player containers (covers home-feed entry as well).
            "shorts_videos", "shorts_recycler", "shorts_item_detail", "shorts_detail",
            "shorts_feed", "shorts_watch", "shortsplayer", "reel_watch_container",
            // Like/comment/share rail inside the Shorts player of recent builds.
            "reel_dyn",
            // Additional markers found in recent YouTube builds.
            "reel_video_player", "shorts_video_player", "shorts_player_container",
            "reel_player_v2", "shorts_player_v2",
        ),
        Platform.TIKTOK to listOf(
            "viewpager", "view_pager", "aweme_video", "video_view", "video_player",
            "feed_video", "for_you", "foryou", "recommend_feed", "recommend_viewpager",
        ),
        Platform.TWITTER to listOf(
            "immersive_video", "fullscreen_video", "media_viewer", "video_viewer",
            "video_detail", "video_pager", "immersive_media",
        ),
    )

    private val platformClassMarkers = mapOf(
        Platform.INSTAGRAM to listOf(
            // Full-screen Reels / Clips viewers only — never FeedFragment / MainFeed.
            "reelactivity", "reelsactivity",
            "reelsviewerfragment", "reelsviewer", "reelviewerfragment", "reelviewer",
            "clipsviewerfragment", "clipsviewer", "clipsviewersfragment",
            "clipsfragment", "reelfragment", "reelsfragment",
            "igdsreels", "reelsplayback", "clipspage", "reelspage",
            "sundial", // IG internal name used by some clips surfaces
        ),
        Platform.FACEBOOK to listOf(
            "reelactivity", "reelsactivity", "reelfragment", "reelsfragment", "reelsviewer",
            // Facebook Lite uses different class names for the Reels player.
            "fbreelsfragment", "fbreelsactivity", "fbreelfragment",
            "shortvideoactivity", "shortvideofragment", "reelsplayeractivity",
            "reelsfullscreen", "reelfullscreen",
            // Additional Facebook Lite Reels class markers (Lite uses distinct class names
            // that differ from the main Facebook app).
            "litereelsactivity", "litereelfragment", "litereelsfragment",
            "fbreelsviewer", "fbreelsplayer", "fbreelviewer", "fbreelplayer",
            "reelsvieweractivity", "reelsplayerfragment", "reelviewerfragment",
            "reelsfullscreenactivity", "reelfullscreenactivity",
            "shortvideoviewer", "shortvideoplayer", "shortvideopager",
            "reelspageractivity", "reelpageractivity",
        ),
        Platform.YOUTUBE to listOf(
            "shortsactivity", "shortsfragment", "reelwatch", "reelplayer",
            "reelwatchfragmentactivity", "shortsplayeractivity", "shortspivot", "shortscontent",
            "reelplayerfragment", "youtubeinternalshortsactivity",
        ),
        Platform.TIKTOK to listOf(
            "detailactivity", "awemedetail", "feedactivity", "recommendactivity",
        ),
        Platform.TWITTER to listOf(
            "videoplayeractivity", "mediavieweractivity", "immersivemedia", "fullscreenvideo",
        ),
    )

    fun nativePlatform(packageName: String): Platform? {
        nativePlatforms[packageName]?.let { return it }
        val normalized = packageName.lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("com.zhiliaoapp.musically") ||
                normalized.startsWith("com.ss.android.ugc.") -> Platform.TIKTOK
            normalized.startsWith("com.twitter.android") -> Platform.TWITTER
            // Modded/alternative YouTube clients expose the very same Shorts UI and are a
            // well-known route around blockers that only recognise the official package.
            normalized.startsWith("app.revanced.android.youtube") ||
                normalized.startsWith("app.rvx.android.youtube") ||
                normalized.startsWith("com.vanced.android.youtube") ||
                // ReVanced Extended (anddea) repackages YouTube under its own id but ships the
                // identical Shorts UI, so it must be recognised as YouTube as well.
                normalized.startsWith("anddea.youtube") ||
                normalized == "com.google.android.apps.youtube.mango" -> Platform.YOUTUBE
            else -> null
        }
    }

    /** TikTok variants are dedicated short-video applications. */
    fun blocksEntireNativeApp(platform: Platform): Boolean = platform == Platform.TIKTOK

    /**
     * Recognises a tap on the bottom destination and taps on a Reels/Shorts preview card. Lite
     * apps frequently put the useful label or resource ID on a parent/sibling rather than on the
     * clicked icon, so a bounded family subtree around each ancestor is inspected.
     */
    fun isEntryClick(platform: Platform, event: AccessibilityEvent): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) return false

        val values = mutableListOf<String>()
        event.text?.forEach { value -> value?.toString()?.let(values::add) }
        event.contentDescription?.toString()?.let(values::add)

        // Facebook and YouTube keep a persistent bottom navigation bar whose Reels/Shorts tab
        // sits right next to the other tabs (Friends/Subscriptions/Library …). Walking up to the
        // shared bar container and scanning its family would see the neighbouring "Reels"/"Shorts"
        // label and eject the user when they merely tapped a different tab. So for these two apps
        // we inspect ONLY the tapped node's own text/ids and never the sibling family: the real
        // Reels/Shorts tab still carries its own label (so it is still blocked), while the feed
        // Shorts/Reels shelf is caught separately by isShelfEntryClick/isPortraitRailCardClick.
        // Instagram: same sibling-tab pitfall as Facebook/YouTube (Home next to Reels).
        // Only inspect the tapped node chain; shelf/rail openers are handled separately and the
        // full-screen viewer is caught by isNativeShortVideoScreen after navigation.
        val scanFamily = platform != Platform.FACEBOOK &&
            platform != Platform.YOUTUBE &&
            platform != Platform.INSTAGRAM

        var node = event.source
        repeat(MAX_CLICK_ANCESTORS) { ancestorIndex ->
            val current = node ?: return@repeat
            collectNodeValues(current, values)
            if (scanFamily && ancestorIndex <= MAX_FAMILY_SCAN_ANCESTOR) {
                collectFamilyValues(current, depth = 0, counter = Counter(), values = values)
            }
            val parent = current.parent
            current.recycleCompat()
            node = parent
        }
        node?.recycleCompat()

        return values.any { value ->
            containsSectionLabel(platform, value) || isPlayerId(platform, value) ||
                isEntryId(platform, value) ||
                // Home-feed shelves put the section word inside the tapped card description.
                (value.contains('/') || value.contains(':') || value.contains(' ')) &&
                looseSectionMatch(platform, value)
        }
    }

    /**
     * Catches taps on a Reels/Shorts SHELF card inside a home/feed page (the reported bypass):
     * unlike the bottom Shorts tab, shelf cards carry no "Shorts" label on themselves, so the
     * tap is attributed by locating the shelf *header* (an exact "Shorts"/"شورتس" label node)
     * inside one of the tapped card's ancestor containers.
     *
     * False-positive guards: the header must live in the upper 82% of the window (bottom-nav
     * pivots are excluded) and matching is exact/section-style, never a substring of a title.
     */
    fun isShelfEntryClick(
        platform: Platform,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) return false
        val needles = shelfHeaderNeedles[platform].orEmpty()
        if (needles.isEmpty()) return false

        val screenBounds = Rect()
        root?.getBoundsInScreen(screenBounds)
        val maxHeaderCenterY = screenBounds.height() * 82 / 100

        var matched = false
        var hops = 0
        var current: AccessibilityNodeInfo? = event.source
        var parent: AccessibilityNodeInfo?
        while (current != null && hops < 6 && !matched) {
            parent = current.parent
            if (parent != null) {
                matched = subtreeHasSectionHeader(parent, needles, maxHeaderCenterY, Counter(), 0)
            }
            current.recycleCompat()
            current = parent
            hops++
        }
        current?.recycleCompat()
        return matched
    }

    /** Labels that title a whole Reels/Shorts shelf/section. Exact (or space-suffixed) match. */
    private val shelfHeaderNeedles = mapOf(
        Platform.YOUTUBE to setOf("shorts", "shorts.", "شورتس", "فيديوهات قصيرة"),
        Platform.INSTAGRAM to setOf("reels", "ريلز", "ریلز"),
        Platform.FACEBOOK to setOf("reels", "ريلز", "ریلز", "reels and short videos"),
        Platform.TIKTOK to emptySet(),
        Platform.TWITTER to emptySet(),
    )

    private fun subtreeHasSectionHeader(
        node: AccessibilityNodeInfo?,
        needles: Set<String>,
        maxHeaderCenterY: Int,
        counter: Counter,
        depth: Int,
    ): Boolean {
        if (node == null || depth > 3 || counter.visited > 100) return false
        counter.visited++

        val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in values) {
            if (raw.isBlank() || raw.length > 40) continue
            val label = raw.trim().trim('.', ':').lowercase(Locale.ROOT)
            val headerHit = needles.any { label == it || label.endsWith(" $it") }
            if (headerHit) {
                val bounds = Rect().also(node::getBoundsInScreen)
                // Ignore bottom-navigation labels: only a mid-screen section header counts.
                if (maxHeaderCenterY <= 0 || bounds.centerY() <= maxHeaderCenterY) return true
            }
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = subtreeHasSectionHeader(child, needles, maxHeaderCenterY, counter, depth + 1)
            child.recycleCompat()
            if (found) return true
        }
        return false
    }

    /**
     * Geometry signature of a tapped Reels/Shorts rail card — works even when the feed exposes
     * no accessibility text at all (the case on recent YouTube builds). A rail tile is a clearly
     * portrait rectangle that is narrower than two thirds of the window and has at least one
     * neighbouring portrait tile next to it. Long-form thumbnails are wide (16:9) and regular
     * feed photos are square, so feeds are never misidentified.
     */
    fun isPortraitRailCardClick(
        platform: Platform,
        event: AccessibilityEvent,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (platform == Platform.TIKTOK) return false
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED
        ) return false
        val source = event.source ?: return false
        val clicked = Rect().also(source::getBoundsInScreen)
        source.recycleCompat()

        val screen = Rect()
        root?.getBoundsInScreen(screen)
        val screenWidth = screen.width().coerceAtLeast(1)
        if (clicked.width() <= 0 || clicked.height() <= 0) return false

        // Clearly portrait and clearly not a full-width long-form card.
        if (clicked.height() * 100 < clicked.width() * 122) return false
        if (clicked.width() * 3 > screenWidth * 2) return false

        // At least one other portrait tile aligned in the same horizontal band confirms a rail.
        val siblings = countNeighbourRailTiles(root, clicked, Counter(), 0)
        if (siblings < 1) return false

        // Facebook Friends cards and Instagram home photo grids can look like a portrait rail.
        // Geometry alone is not enough — require an explicit Reels/Clips marker on the tap path.
        if ((platform == Platform.FACEBOOK || platform == Platform.INSTAGRAM) &&
            !clickHasReelMarker(platform, event)
        ) {
            return false
        }

        return true
    }

    /** True when the tapped node or one of its ancestors carries a Reels/short-video marker. */
    private fun clickHasReelMarker(platform: Platform, event: AccessibilityEvent): Boolean {
        val values = mutableListOf<String>()
        event.text?.forEach { value -> value?.toString()?.let(values::add) }
        event.contentDescription?.toString()?.let(values::add)
        var node: AccessibilityNodeInfo? = event.source
        var hops = 0
        while (node != null && hops < MAX_CLICK_ANCESTORS) {
            collectNodeValues(node, values)
            val parent = node.parent
            node.recycleCompat()
            node = parent
            hops++
        }
        node?.recycleCompat()
        return values.any { value ->
            containsSectionLabel(platform, value) ||
                isPlayerId(platform, value) ||
                isEntryId(platform, value) ||
                looseSectionMatch(platform, value)
        }
    }

    private fun countNeighbourRailTiles(
        node: AccessibilityNodeInfo?,
        clicked: Rect,
        counter: Counter,
        depth: Int,
    ): Int {
        if (node == null || depth > MAX_DEPTH || counter.visited > 600) return 0
        counter.visited++
        var count = 0
        val bounds = Rect().also(node::getBoundsInScreen)
        if (bounds != clicked && bounds.width() > 0 && bounds.height() > 0 &&
            // Same tall-portrait proportion …
            bounds.height() * 100 >= bounds.width() * 122 &&
            // … roughly the same size …
            bounds.height() * 100 >= clicked.height() * 75 &&
            bounds.height() * 100 <= clicked.height() * 135 &&
            // … and in the same band vertically, but not overlapping horizontally.
            kotlin.math.abs(bounds.top - clicked.top) <= clicked.height() * 3 / 10 &&
            (bounds.left >= clicked.right - bounds.width() / 10 ||
                bounds.right <= clicked.left + bounds.width() / 10)
        ) {
            count = 1
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            count += countNeighbourRailTiles(child, clicked, counter, depth + 1)
            child.recycleCompat()
            if (count >= 1 || counter.visited > 600) return count
        }
        return count
    }

    /** Inspects the destination reached after a preview or tab click. */
    fun isNativeShortVideoScreen(
        platform: Platform,
        className: String?,
        root: AccessibilityNodeInfo?,
        packageName: String? = null,
    ): Boolean {
        if (blocksEntireNativeApp(platform)) return true
        val normalizedClass = className.orEmpty().lowercase(Locale.ROOT)

        // Early-exit: if the class name clearly indicates a channel/browse/playlists page
        // on YouTube, this is NOT the Shorts player regardless of what the tree scan finds.
        // Channel pages carry a "Shorts" tab in their tab bar plus video thumbnails, which
        // previously caused false-positive blocks ejecting the user from channel video lists.
        if (platform == Platform.YOUTUBE && isYouTubeChannelOrBrowsePage(className)) return false

        if (platformClassMarkers[platform].orEmpty().any(normalizedClass::contains)) return true
        if (root == null) return false

        val rootBounds = Rect().also(root::getBoundsInScreen)
        val scan = ScanResult()
        scanNode(platform, root, rootBounds, depth = 0, counter = Counter(), result = scan)

        // Facebook-specific gate (does NOT affect YouTube/Instagram/TikTok/Twitter): a normal
        // Facebook video post in the FEED has a large VideoView plus a HORIZONTAL row of
        // like/comment/share actions, and the app keeps a persistent "Reels" bottom-nav tab and
        // home-feed shelf header on screen. To avoid ejecting the user from ordinary feed/video
        // browsing we require strong, Reels-player-specific evidence:
        //   1) a real Reels player container/class, or
        //   2) the Reels tab being the CURRENTLY SELECTED destination with a full-screen player,
        //      or
        //   3) the immersive Reels player's VERTICAL right-rail (like/comment/share/save stacked
        //      on the right edge) together with a full-screen video. The feed shows these actions
        //      in a horizontal row, so the right-rail signature only appears inside the player.
        if (platform == Platform.FACEBOOK) {
            val isLite = packageName == "com.facebook.lite"
            // A visited profile or Page also owns a "Reels" tab, and profile/Page videos are
            // large surfaces, so "selected Reels label + big video" matched there and ejected the
            // user. The immersive Reels player is always identified either by its own player
            // container/class (playerStructure) or by the vertically-stacked like/comment/share
            // rail that only the full-screen viewer shows — neither of which appears on a profile,
            // a Page or the feed. Relying solely on those two signals keeps Reels itself blocked
            // while leaving profiles, Pages and normal video posts alone.
            //
            // Facebook Lite (com.facebook.lite) uses different class names, view IDs and layout
            // structures from the main Facebook app. The right-rail pattern may not appear, and
            // player container IDs often differ. For Lite we add two supplementary conditions:
            //   1) The Reels tab is the CURRENTLY SELECTED destination alongside a large video
            //      (55 %+ of screen). A selected tab means the user navigated into Reels, not
            //      just that the tab icon exists on the bottom bar.
            //   2) A "Reels" section label is present together with a large video AND at least
            //      two short-video action signals (Like/Comment/Share/Follow). This combination
            //      only occurs inside the immersive Reels player; a normal feed video post shows
            //      actions in a horizontal row (not counted by actionSignals which requires the
            //      right-rail position) and never stacks a "Reels" header above a nearly-full
            //      video.
            // Both supplementary conditions use largeVideoSurface (55 %) rather than
            // fullScreenVideoSurface (82 %) because Facebook Lite renders more chrome around the
            // player, so the video does not fill as much of the screen as in the main app.
            if (isLite) {
                return scan.playerStructure ||
                    (rightRailIsStackedRail(scan) && scan.fullScreenVideoSurface) ||
                    (scan.selectedSection && scan.largeVideoSurface) ||
                    (scan.sectionLabels > 0 && scan.largeVideoSurface && scan.actionSignals >= 2)
            }
            return scan.playerStructure ||
                (rightRailIsStackedRail(scan) && scan.fullScreenVideoSurface)
        }

        // YouTube's "You" tab (profile) and its Library/History screens embed a "Shorts" shelf and
        // the user's own videos, so a bare "selected Shorts label" or "Shorts label + a video"
        // matched there and ejected the user from those ordinary screens. The full-screen Shorts
        // player is always identified by its own player container/class (playerStructure), by the
        // immersive vertical like/comment/share rail, or by the Shorts tab being selected WHILE a
        // full-screen video fills the page. Requiring the large video alongside the selected label
        // keeps Shorts itself blocked while leaving the You/Library/Subscriptions screens alone.
        //
        // CRITICAL FIX: A YouTube CHANNEL page (e.g. Channel -> Videos tab) also has a "Shorts"
        // tab in its tab bar and shows large video thumbnails, which previously triggered the
        // (selectedSection + largeVideoSurface) condition and ejected the user from the channel.
        // The channel page is NOT the Shorts player — it's a browseable list of videos. We now
        // require fullScreenVideoSurface (82%+ of screen) instead of largeVideoSurface (55%+)
        // because channel video thumbnails never fill 82% of the screen, but the immersive Shorts
        // player always does. Additionally, channel-specific class names are excluded.
        if (platform == Platform.YOUTUBE) {
            // If the current class name looks like a channel/browse page, never block.
            // Channel pages show a "Shorts" tab + video list but are NOT the Shorts player.
            if (isYouTubeChannelOrBrowsePage(className)) return false
            return scan.playerStructure ||
                (scan.selectedSection && scan.fullScreenVideoSurface) ||
                scan.rightRailActions >= 2
        }

        // Instagram-specific gate: the HOME feed autoplays large videos and often shows a
        // "Reels" shelf header + like/comment row while the user merely scrolls. Blocking on
        // those signals ejected users from ordinary feed browsing. Full-screen Reels is only
        // confirmed by:
        //   1) dedicated Reels/Clips viewer structure (class / player view-id), or
        //   2) the Reels bottom-tab being SELECTED together with a near-full-screen video, or
        //   3) the immersive vertical right-rail (like/comment/share/save) + full-screen video
        //      — feed posts use a horizontal action row, so the stacked rail is viewer-only.
        // A bare "Reels" label, a large in-feed video, or feed action buttons alone never block.
        if (platform == Platform.INSTAGRAM) {
            if (scan.looksLikeHomeFeed && !scan.playerStructure) {
                // Explicit home-feed chrome without a real viewer container → never block.
                return false
            }
            return scan.playerStructure ||
                (scan.selectedSection && scan.fullScreenVideoSurface) ||
                (rightRailIsStackedRail(scan) && scan.fullScreenVideoSurface)
        }

        return scan.playerStructure || scan.selectedSection ||
            (scan.sectionLabels > 0 && (scan.largeVideoSurface || scan.actionSignals >= 2)) ||
            scan.rightRailActions >= 2
    }

    fun webHostPlatform(rawUrl: String): Platform? {
        val uri = parseUri(rawUrl) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        return when {
            // Suffix matching covers every mobile/regional subdomain (m., touch., mbasic.,
            // web., vm., vt., music-less youtube-nocookie embeds, …) instead of a fixed list.
            hostMatches(host, "youtube.com") || hostMatches(host, "youtube-nocookie.com") ||
                // youtu.be is YouTube's short link host; the /shorts/ path check still applies.
                hostMatches(host, "youtu.be") -> Platform.YOUTUBE
            hostMatches(host, "instagram.com") -> Platform.INSTAGRAM
            hostMatches(host, "facebook.com") || hostMatches(host, "fb.watch") -> Platform.FACEBOOK
            hostMatches(host, "tiktok.com") -> Platform.TIKTOK
            hostMatches(host, "x.com") || hostMatches(host, "twitter.com") -> Platform.TWITTER
            else -> null
        }
    }

    private fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    /** Returns a platform only for a URL that is already on a short-video path. */
    fun blockedWebPlatform(rawUrl: String): Platform? {
        val uri = parseUri(rawUrl) ?: return null
        val platform = webHostPlatform(rawUrl) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val segments = uri.rawPath.orEmpty().lowercase(Locale.ROOT)
            .split('/').filter { it.isNotBlank() }

        return when (platform) {
            Platform.YOUTUBE -> platform.takeIf { segments.firstOrNull() == "shorts" }
            Platform.INSTAGRAM -> platform.takeIf { segments.firstOrNull() in setOf("reel", "reels") }
            Platform.FACEBOOK -> platform.takeIf {
                segments.firstOrNull() in setOf("reel", "reels") ||
                    (segments.size > 1 && segments[0] == "watch" && segments[1] == "reels") ||
                    // fb.watch/r/… is the share-link form Facebook generates for a reel.
                    (hostMatches(host, "fb.watch") && segments.firstOrNull() == "r")
            }
            Platform.TIKTOK -> platform
            Platform.TWITTER -> platform.takeIf {
                segments.firstOrNull() in setOf("video", "videos") ||
                    segments.contains("video") || segments.contains("videos") ||
                    segments.contains("immersive_timeline")
            }
        }
    }

    /** Uses the current website host plus clicked-node semantics to block web preview cards. */
    fun isWebEntryClick(currentUrl: String, event: AccessibilityEvent): Platform? {
        val platform = webHostPlatform(currentUrl) ?: return null
        return platform.takeIf { isEntryClick(platform, event) }
    }

    private fun parseUri(rawUrl: String): URI? {
        val candidate = rawUrl.trim()
        if (candidate.isEmpty()) return null
        return runCatching {
            URI(if (candidate.contains("://")) candidate else "https://$candidate")
        }.getOrNull()
    }

    private fun scanNode(
        platform: Platform,
        node: AccessibilityNodeInfo?,
        rootBounds: Rect,
        depth: Int,
        counter: Counter,
        result: ScanResult,
    ) {
        if (node == null || depth > MAX_DEPTH || counter.visited >= MAX_NODES || result.playerStructure) return
        counter.visited++

        val rawId = node.viewIdResourceName.orEmpty()
        val normalizedId = normalizeId(rawId)
        if (platform == Platform.INSTAGRAM && isInstagramHomeFeedId(normalizedId)) {
            result.looksLikeHomeFeed = true
        }
        if (isPlayerId(platform, rawId)) {
            // Instagram home still exposes some "clips_*" tray ids; only treat player ids as
            // structure when they are true viewer containers (not feed tray / feed item).
            if (platform != Platform.INSTAGRAM || isInstagramViewerPlayerId(normalizedId)) {
                result.playerStructure = true
                return
            }
        }

        val className = node.className?.toString().orEmpty().lowercase(Locale.ROOT)
        if (platform == Platform.INSTAGRAM && isInstagramHomeFeedClass(className)) {
            result.looksLikeHomeFeed = true
        }
        // Class-level viewer markers inside the tree (fragment hosts sometimes leak here).
        if (platform == Platform.INSTAGRAM &&
            platformClassMarkers[platform].orEmpty().any(className::contains)
        ) {
            result.playerStructure = true
            return
        }
        if (className.contains("videoview") || className.contains("surfaceview") ||
            className.contains("textureview")
        ) {
            val bounds = Rect().also(node::getBoundsInScreen)
            val rootHeight = rootBounds.height().coerceAtLeast(1)
            val rootWidth = rootBounds.width().coerceAtLeast(1)
            if (bounds.height() >= rootHeight * LARGE_VIDEO_HEIGHT_PERCENT / 100 &&
                bounds.width() >= rootWidth * LARGE_VIDEO_WIDTH_PERCENT / 100
            ) {
                result.largeVideoSurface = true
            }
            // A genuine Reels player fills almost the whole screen. A profile/feed video post is
            // only a portion of the page even while scrolling, so this stricter test tells the
            // immersive viewer apart from an ordinary large video.
            if (bounds.height() >= rootHeight * FULLSCREEN_VIDEO_HEIGHT_PERCENT / 100 &&
                bounds.width() >= rootWidth * FULLSCREEN_VIDEO_WIDTH_PERCENT / 100
            ) {
                result.fullScreenVideoSurface = true
            }
        }

        val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        values.forEach { value ->
            if (containsSectionLabel(platform, value)) {
                result.sectionLabels++
                if (node.isSelected || node.isChecked || hasSelectedSemantics(value)) {
                    result.selectedSection = true
                }
            }
            if (isShortVideoAction(value)) {
                result.actionSignals++
                val bounds = Rect().also(node::getBoundsInScreen)
                val rightRailStart = rootBounds.left +
                    rootBounds.width() * RIGHT_RAIL_START_PERCENT / 100
                if (bounds.centerX() >= rightRailStart && bounds.height() > 0) {
                    val rootHeight = rootBounds.height().coerceAtLeast(1)
                    val verticalSlot = bounds.centerY() * RIGHT_RAIL_SLOT_COUNT / rootHeight
                    result.rightRailSlots.add(verticalSlot)
                    result.rightRailActions = result.rightRailSlots.size
                }
            }
        }

        // Some tab implementations mark the parent selected while the child owns the label.
        if (node.isSelected || node.isChecked) {
            val selectedValues = mutableListOf<String>()
            collectFamilyValues(node, depth = 0, counter = Counter(), values = selectedValues)
            if (selectedValues.any { containsSectionLabel(platform, it) }) {
                result.selectedSection = true
            }
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            scanNode(platform, child, rootBounds, depth + 1, counter, result)
            child.recycleCompat()
            if (result.playerStructure || counter.visited >= MAX_NODES) return
        }
    }

    private fun collectNodeValues(node: AccessibilityNodeInfo, values: MutableList<String>) {
        node.text?.toString()?.let(values::add)
        node.contentDescription?.toString()?.let(values::add)
        node.viewIdResourceName?.let(values::add)
    }

    private fun collectFamilyValues(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
        values: MutableList<String>,
    ) {
        if (node == null || depth > MAX_FAMILY_DEPTH || counter.visited >= MAX_FAMILY_NODES) return
        counter.visited++
        collectNodeValues(node, values)
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectFamilyValues(child, depth + 1, counter, values)
            child.recycleCompat()
            if (counter.visited >= MAX_FAMILY_NODES) return
        }
    }

    private fun rightRailIsStackedRail(scan: ScanResult): Boolean {
        if (scan.rightRailActions < 2) return false
        val slots = scan.rightRailSlots
        if (slots.isEmpty()) return false
        val span = slots.max() - slots.min()
        return span <= RIGHT_RAIL_MAX_CLUSTER_SPAN
    }

    private fun isPlayerId(platform: Platform, rawId: String): Boolean {
        val id = normalizeId(rawId)
        return commonPlayerIdMarkers.any(id::contains) ||
            platformPlayerIdMarkers[platform].orEmpty().any(id::contains)
    }

    /**
     * Returns true if the class name indicates a YouTube channel page, browse page,
     * playlist page, or search results page — surfaces that are NOT the Shorts player
     * even though they contain a "Shorts" tab and video thumbnails.
     *
     * This is the core fix for the "YouTube Shorts vs Channel Video List" bug: channel
     * pages show a tab bar with Shorts/Videos/Playlists tabs, and the Videos tab displays
     * large video thumbnails. The previous logic saw "Shorts tab selected + large video"
     * and incorrectly pressed BACK, ejecting the user from the channel.
     *
     * These class names are stable across YouTube versions because they are top-level
     * Activity/Fragment names defined in the app manifest and navigation graph.
     */
    private fun isYouTubeChannelOrBrowsePage(className: String?): Boolean {
        val cls = className.orEmpty().lowercase(Locale.ROOT)
        if (cls.isBlank()) return false
        val channelMarkers = listOf(
            // Channel activity/fragment — the main channel page with Videos/Shorts/Playlists tabs.
            "channelactivity", "channelfragment", "channelbrowsingactivity",
            "channelpage", "channelview", "channelcontent",
            // Browse activity — used for browsing categories, channels, and playlists.
            "browseactivity", "browsefragment", "browsepage",
            // Playlist/library pages — show video lists that are not Shorts.
            "playlistactivity", "playlistfragment", "playlistpage",
            "libraryactivity", "libraryfragment", "librarypage",
            // Search results — contains video cards but is not Shorts.
            "searchactivity", "searchfragment", "searchresults",
            // Video detail/player for LONG-FORM videos (not Shorts).
            // Shorts uses ShortsActivity/ReelWatchFragmentActivity instead.
            "watchactivity", "watchfragment", "watchlateractivity",
            // History and subscriptions pages.
            "historyactivity", "historyfragment", "subscriptionactivity",
        )
        return channelMarkers.any(cls::contains)
    }

    private fun isEntryId(platform: Platform, rawId: String): Boolean {
        val id = normalizeId(rawId)
        return platformEntryIdMarkers[platform].orEmpty().any(id::contains)
    }

    private fun normalizeId(rawId: String): String = rawId.substringAfterLast('/').lowercase(Locale.ROOT)

    private fun containsSectionLabel(platform: Platform, value: String): Boolean {
        val label = normalizeLabel(value)
        return labelsFor(platform).any { marker ->
            label == marker || label.startsWith("$marker ") || label.endsWith(" $marker") ||
                label.contains(" $marker ")
        }
    }

    private fun labelsFor(platform: Platform): Set<String> = when (platform) {
        Platform.YOUTUBE -> shortsLabels
        Platform.INSTAGRAM, Platform.FACEBOOK -> reelLabels
        Platform.TIKTOK -> tiktokLabels
        Platform.TWITTER -> twitterVideoLabels
    }

    private fun normalizeLabel(value: String): String = value
        .substringAfterLast('/')
        .replace('_', ' ')
        .trim()
        .lowercase(Locale.ROOT)
        .trim('.', ':', '-', '–', '—', '،')

    private fun hasSelectedSemantics(value: String): Boolean {
        val label = normalizeLabel(value)
        return selectedMarkers.any(label::contains)
    }

    private fun isShortVideoAction(value: String): Boolean {
        val label = normalizeLabel(value)
        return shortVideoActionMarkers.any { marker ->
            label == marker || label.startsWith("$marker ") || label.endsWith(" $marker") ||
                label.contains(" $marker ")
        }
    }

    private val shortVideoActionMarkers = listOf(
        "like", "likes", "dislike", "comment", "comments", "share", "shares",
        "remix", "use audio", "save", "follow",
        "إعجاب", "أعجبني", "عدم الإعجاب", "تعليق", "تعليقات", "التعليقات",
        "مشاركة", "حفظ", "متابعة", "ريمكس", "استخدام الصوت",
        "پسندیدن", "نظر", "اشتراک گذاری", "ذخیره", "دنبال کردن",
        "لایک", "بەدڵبوون", "کۆمێنت", "لێدوان", "هاوبەشکردن", "شەیر",
        "پاشەکەوت", "شوێنکەوتن",
    )

    private val selectedMarkers = listOf(
        "selected", "current tab", "active tab", "محدد", "مختار", "علامة التبويب الحالية",
        "هەڵبژێردراو", "تابی ئێستا",
    )

    private class Counter(var visited: Int = 0)

    private class ScanResult(
        var playerStructure: Boolean = false,
        var selectedSection: Boolean = false,
        var sectionLabels: Int = 0,
        var largeVideoSurface: Boolean = false,
        var fullScreenVideoSurface: Boolean = false,
        var actionSignals: Int = 0,
        var rightRailActions: Int = 0,
        val rightRailSlots: MutableSet<Int> = hashSetOf(),
        /** Instagram MainFeed / home chrome detected — blocks false positives while scrolling. */
        var looksLikeHomeFeed: Boolean = false,
    )

    /**
     * Loose per-tap fallback: home-feed Reels/Shorts shelf items often carry the section word
     * deep in their accessibility description (e.g. "… — Shorts"). Only evaluated on a tap, so
     * it can never fire while scrolling a long-form feed without interaction.
     */
    private val looseSectionNeedles = mapOf(
        Platform.INSTAGRAM to listOf("reels", "ريلز", "ریلز"),
        Platform.FACEBOOK to listOf("reels", "ريلز", "ریلز"),
        Platform.YOUTUBE to listOf("shorts", "شورتس", "فيديوهات قصيرة"),
        Platform.TIKTOK to listOf("tiktok", "for you"),
        Platform.TWITTER to emptyList(),
    )

    private fun looseSectionMatch(platform: Platform, value: String): Boolean {
        val label = value.lowercase(Locale.ROOT)
        return looseSectionNeedles[platform].orEmpty().any(label::contains)
    }

    /**
     * Instagram view-ids that belong to the main home feed / main activity chrome.
     * Presence of these without a dedicated viewer container means "do not block".
     */
    private fun isInstagramHomeFeedId(id: String): Boolean {
        if (id.isEmpty()) return false
        val feedMarkers = listOf(
            "feed_tab", "main_feed", "feed_root", "feed_list", "feed_recycler",
            "sticky_header_list", "action_bar_root_view", "tab_bar", "bottom_navigation",
            "feed_composer", "row_feed", "media_group", "feed_preview",
            "carousel_media", "media_set", "feed_post", "legacy_feed",
            // Home "Reels tray" strip at the top of the feed is NOT the full-screen viewer.
            "reels_tray", "reel_ring", "tray_item", "stories_tray",
        )
        return feedMarkers.any(id::contains)
    }

    private fun isInstagramHomeFeedClass(className: String): Boolean {
        if (className.isEmpty()) return false
        val markers = listOf(
            "feedfragment", "mainfeedfragment", "mainfeed", "newsfeedfragment",
            "instagrammainactivity", "mainactivity", "feedactivity",
        )
        // Never treat known viewer classes as home.
        if (platformClassMarkers[Platform.INSTAGRAM].orEmpty().any(className::contains)) return false
        return markers.any(className::contains)
    }

    /**
     * Instagram player ids that truly mean the immersive viewer is open.
     * Excludes home-feed tray / item ids that contain "clips" or "reel" substrings.
     */
    private fun isInstagramViewerPlayerId(id: String): Boolean {
        if (id.isEmpty()) return false
        // Explicit non-viewer ids that must never open a block by themselves.
        val nonViewer = listOf(
            "reel_feed", "reel_feed_item", "reels_tray", "reel_ring", "tray",
            "clips_tab", "clips_item", "clips_media", // entry/tray — click path handles those
            "row_feed", "feed_", "story_", "stories_",
        )
        if (nonViewer.any(id::contains) &&
            !id.contains("viewer") && !id.contains("player") && !id.contains("pager")
        ) {
            return false
        }
        val viewer = listOf(
            "clips_viewer", "reels_viewer", "reel_viewer", "clips_player", "reels_player",
            "clips_viewer_view_pager", "reels_viewer_view_pager", "clips_video_container",
            "clips_swipe_refresh", "reel_player", "reels_video_player", "clips_video_player",
            "reel_player_page", "reels_player_page", "reel_watch", "reels_pager", "reel_pager",
            "clips_viewer_video", "gesture_manager",
        )
        return viewer.any(id::contains) ||
            platformPlayerIdMarkers[Platform.INSTAGRAM].orEmpty().any(id::contains)
    }

    // Recent Instagram/YouTube builds nest the player 16-20 levels deep; the previous
    // depth of 14 stopped short of the marker nodes and let the viewer slip through.
    private const val MAX_DEPTH = 22
    private const val MAX_NODES = 1200
    private const val MAX_CLICK_ANCESTORS = 7
    // The clicked node and its immediate parent are enough to capture an icon + label card.
    // Scanning higher ancestors would include sibling navigation tabs and cause false positives.
    private const val MAX_FAMILY_SCAN_ANCESTOR = 1
    private const val MAX_FAMILY_DEPTH = 4
    private const val MAX_FAMILY_NODES = 120
    private const val LARGE_VIDEO_HEIGHT_PERCENT = 55
    private const val LARGE_VIDEO_WIDTH_PERCENT = 55
    // A real immersive Reels player fills almost the entire screen; a profile/feed video post
    // never does, even mid-scroll. Used only by the Facebook gate to avoid false blocks there.
    private const val FULLSCREEN_VIDEO_HEIGHT_PERCENT = 82
    private const val FULLSCREEN_VIDEO_WIDTH_PERCENT = 88
    private const val RIGHT_RAIL_START_PERCENT = 72
    // A real immersive Reels right-rail (like/comment/share/save) is stacked within roughly the
    // lower half of the screen. Feed action buttons from separate posts are spread across the
    // full height, so their slot span is much larger; anything wider than this is treated as a
    // feed, not a Reels rail. Out of RIGHT_RAIL_SLOT_COUNT (32) slots, 16 ≈ half the screen.
    private const val RIGHT_RAIL_MAX_CLUSTER_SPAN = 16
    private const val RIGHT_RAIL_SLOT_COUNT = 32
}

@Suppress("DEPRECATION")
private fun AccessibilityNodeInfo.recycleCompat() {
    if (android.os.Build.VERSION.SDK_INT < 33) runCatching { recycle() }
}
