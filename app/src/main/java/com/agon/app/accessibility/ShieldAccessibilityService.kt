package com.agon.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.FrameLayout
import com.agon.app.BlockedAppActivity
import com.agon.app.BlockedWebsiteActivity
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.data.ShortVideoPreferences
import com.agon.app.blocklist.domain.AiContentClassifier
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.blocklist.domain.ContentCategory
import com.agon.app.blocklist.domain.CircumventionPolicy
import com.agon.app.blocklist.domain.MonitoredApps
import com.agon.app.blocklist.domain.PhoneCallPolicy
import com.agon.app.blocklist.domain.RuleType
import com.agon.app.blocklist.domain.SearchPolicy
import com.agon.app.blocklist.domain.ShortVideoPolicy
import com.agon.app.blocklist.domain.TelegramSearchPolicy
import com.agon.app.blocklist.domain.TextNormalizer
import com.agon.app.data.ShieldRepository
import com.agon.app.localization.tr
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.ProtectedScreenType
import com.agon.app.settingsprotection.domain.ProtectionTiming
import com.agon.app.settingsprotection.domain.SettingsProtectionGuard
import com.agon.app.quarantine.NewAppQuarantineStore
import com.agon.app.timelimit.AppTimeLimitStore
import com.agon.app.timelimit.AppTimeTracker
import com.agon.app.timelimit.BlockScheduleStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single accessibility service for app blocking, browser protection and global keyword
 * protection.
 *
 * Performance model: the service never polls the screen. Work is triggered only by
 * `TYPE_WINDOW_STATE_CHANGED` (navigation), `TYPE_VIEW_TEXT_CHANGED` / `TYPE_VIEW_TEXT_SELECTION_CHANGED`
 * (focused editable text) and `TYPE_VIEW_CLICKED` (submission). Browser page inspection is
 * additionally debounced and skipped entirely when no rules are configured.
 */
@AndroidEntryPoint
class ShieldAccessibilityService : AccessibilityService() {

    @Inject lateinit var blocklistRepository: BlocklistRepository

    @Inject lateinit var shortVideoPreferences: ShortVideoPreferences
    @Inject lateinit var telegramSearchPreferences: com.agon.app.blocklist.data.TelegramSearchPreferences
    @Inject lateinit var vpnProtectionPreferences: com.agon.app.blocklist.data.VpnProtectionPreferences

    @Inject lateinit var settingsProtectionGuard: SettingsProtectionGuard

    @Inject lateinit var settingsProtectionRepository: SettingsProtectionRepository

    /** Structural screen detector reused directly for the System VPN Settings interception —
     * independent of [settingsProtectionGuard]'s PIN/grace state machine, which governs the
     * separate "Phone Settings Lock" feature. */
    @Inject lateinit var settingsScreenDetector: com.agon.app.settingsprotection.domain.SettingsScreenDetector

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    // ------------------------------------------------ shade-lock hard interception layer

    /**
     * The ONLY thing the per-event path reads: a volatile flag refreshed by the 1-second
     * checkpoint (never reads the store per event — that was an ANR amplifier). Checked once
     * per event on the callback thread at zero cost.
     */
    @Volatile
    private var shadeLockArmed = false

    /** Single-flight guard for geometry sweeps — a scroll burst can never stack worker jobs. */
    private val shadeSweepInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Updates the cached arming flag and flips the overlay only on transitions. Called by the
     * 1s sustain loop (and once at connect). Prefs + TamperProofClock stay QUARTER-second data
     * strobes like everything else in this service — never per-event.
     */
    private fun refreshShadeLockState() {
        // Licence gate: locked features are inert without a valid activation (UI alone is not a
        // boundary — enforcement itself stays off until a code is verified).
        val armed = com.agon.app.services.NotificationShadeLock.isEnabled(this) &&
            !ShieldRepository.isProtectionPaused()
        // Dynamic-activation fix (banking overlay warnings): the pull-strip overlay exists ONLY
        // while a shade-leak surface (Settings hosts, launcher, SystemUI) actually faces the
        // user. Everywhere else — banking apps, NFC payment flows, games — the window is
        // detached, so nothing flags a screen-overlay while protection still fully works there.
        val foreground = ShieldRepository.foregroundPackage
        val needed = armed && foreground != null && isShadeSensitiveHost(foreground)
        shadeLockArmed = armed
        updateShadeLockOverlay(needed)
    }

    /** Surfaces where a status-bar pull could actually leak into the protected settings flow. */
    private fun isShadeSensitiveHost(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.settings" || pkg.endsWith(".settings") ||
            pkg == "com.android.systemui" || pkg.endsWith(".systemui") ||
            pkg.contains("launcher") ||
            pkg.contains("securitycenter") || pkg.contains("systemmanager") ||
            pkg.contains("safecenter") || pkg.contains("packageinstaller")
    }

    /**
     * Belt-1 of the notification-shade hard lock: an invisible accessibility overlay window
     * (TYPE_ACCESSIBILITY_OVERLAY — the permission-free overlay channel available to an enabled
     * accessibility service) stretched over the status-bar pull zone. Its view CONSUMES every
     * touch landing there, so the pull-down gesture a user starts can never reach the system
     * and the panel never begins to drop. Works identically on One UI, HyperOS/MIUI, MagicOS,
     * EMUI, ColorOS, FuntouchOS/OriginOS and AOSP because it never touches the panel itself.
     */
    @Volatile
    private var shadeOverlayAttached = false
    private var shadeOverlayView: FrameLayout? = null

    private fun updateShadeLockOverlay(lockedNow: Boolean) {
        if (lockedNow == shadeOverlayAttached) return
        if (lockedNow) attachShadeOverlay() else detachShadeOverlay()
    }

    private fun attachShadeOverlay() {
        if (shadeOverlayAttached) return
        shadeOverlayAttached = runCatching {
            val overlay = FrameLayout(this).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                // Consume every gesture in the pull zone: without a delivered gesture the system
                // never starts the drag, so no visible panel, no restore race, nothing to close.
                setOnTouchListener { _, _ -> true }
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                shadeStripHeightPx(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Consume touches ONLY inside our strip; everything below/around falls through.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            getSystemService(WindowManager::class.java).addView(overlay, params)
            shadeOverlayView = overlay
            true
        }.getOrDefault(false)
        if (!shadeOverlayAttached) shadeOverlayView = null
    }

    private fun detachShadeOverlay() {
        val view = shadeOverlayView
        shadeOverlayView = null
        shadeOverlayAttached = false
        if (view != null) {
            runCatching { getSystemService(WindowManager::class.java).removeViewImmediate(view) }
        }
    }

    /** The pull strip: the real status-bar height plus a small gesture buffer, in pixels. */
    private fun shadeStripHeightPx(): Int {
        val metrics = resources.displayMetrics
        val statusBarRes = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarRes > 0) resources.getDimensionPixelSize(statusBarRes) else 0
        val fallback = (metrics.density * 48f).toInt()
        return maxOf(statusBar, fallback) + (metrics.density * 16f).toInt()
    }

    // --------------------------------------------------- belt-2 collapse re-check scheduler

    /** Extra bounded rechecks after a collapse, so a panel animating open between events still
     * loses every race while the lock is armed. */
    private var shadeRecheckPassesLeft = 0

    private val shadeRecheck = object : Runnable {
        override fun run() {
            shadeRecheckPassesLeft--
            if (!com.agon.app.services.NotificationShadeLock.isEnabled(this@ShieldAccessibilityService)) return
            if (ShieldRepository.isProtectionPaused()) return
            if (isShadeExpandedNow()) collapseNotificationShade()
            if (shadeRecheckPassesLeft > 0) mainHandler.postDelayed(this, SHADE_RECHECK_INTERVAL_MS)
        }
    }

    private fun scheduleShadeRecheck() {
        if (shadeRecheckPassesLeft >= SHADE_RECHECK_PASSES) return
        mainHandler.removeCallbacks(shadeRecheck)
        shadeRecheckPassesLeft = SHADE_RECHECK_PASSES
        mainHandler.postDelayed(shadeRecheck, SHADE_RECHECK_INTERVAL_MS)
    }

    /** Converts foreground-app transitions into exact consumed time for daily limits. */
    private val timeTracker = AppTimeTracker()

    /**
     * Self-healing attachment: the 1s checkpoint loop re-asserts the touch overlay while armed,
     * so a killed window manager state never leaves the pull zone unguarded for more than a
     * second. Reads only the SharedPreferences memory map — free per call.
     */
    private fun sustainShadeLockOverlay() = refreshShadeLockState()

    /**
     * IO-side sweep — called from a service coroutine, NEVER on the callback thread. Confirms a
     * pulled panel by window-bounds geometry, then evicts it with the triple forced action and
     * arms the bounded recheck ampoule. performGlobalAction is binder-backed, so it is perfectly
     * safe off the main thread.
     */
    private fun sweepShadeLock() {
        if (!shadeLockArmed) return
        if (!com.agon.app.services.NotificationShadeLock.isEnabled(this)) return
        if (isShadeExpandedNow()) {
            collapseNotificationShade()
            scheduleShadeRecheck()
        }
    }

    /**
     * Checkpoints the currently visible limited app once per second. Accessibility window events
     * are transition-based, so without this lightweight callback a long uninterrupted session
     * would not be persisted or blocked until the user left the app.
     */
    private val timeLimitCheckpoint = object : Runnable {
        override fun run() {
            // Advance the protection-commitment ledger by real elapsed time while the
            // accessibility service is alive (mirrors the guardian loop tick).
            runCatching { com.agon.app.settingsprotection.data.ProtectionCommitmentStore.tick(this@ShieldAccessibilityService) }
            // Keep the touch-interception overlay alive: a transient window-manager refresh can
            // silently drop it, and every self-healing beat repairs it long before the next pull.
            sustainShadeLockOverlay()
            val limits = AppTimeLimitStore.limits()
            // Reconcile against the actual active accessibility window every second. Some
            // OEM calculators and utility apps do not emit a reliable WINDOW_STATE_CHANGED
            // event, so transition-only tracking can miss their entire foreground session.
            val foregroundPackage = resolveForegroundPackage()
            if (!foregroundPackage.isNullOrBlank()) {
                // Scheduled quiet times (bedtime, study, prayer) block every non-excluded
                // app for the duration of the window, independent of per-app daily limits.
                // The schedule check is independent of the per-app limit list so a user who
                // never set a daily limit still benefits from the schedule.
                val activeSchedule = BlockScheduleStore.activeScheduleNow()
                if (activeSchedule != null &&
                    foregroundPackage != applicationContext.packageName &&
                    !MonitoredApps.isExcluded(foregroundPackage)
                ) {
                    showScheduleBlock(foregroundPackage, activeSchedule.name)
                    timeTracker.checkpoint()
                } else if (limits.isEmpty()) {
                    timeTracker.flush()
                } else {
                    val exhausted = timeTracker.onForegroundApp(foregroundPackage)
                    if (!MonitoredApps.isExcluded(foregroundPackage) && exhausted) {
                        showTimeLimitReached(foregroundPackage)
                    }
                }
            } else {
                timeTracker.checkpoint()
            }
            mainHandler.postDelayed(this, TIME_LIMIT_CHECKPOINT_MS)
        }
    }

    /** Browsers discovered from the package manager, merged with the static list. */
    private val detectedBrowsers = HashSet<String>(24)

    private var pendingShortVideoPackage: String? = null
    private var pendingShortVideoPlatform: ShortVideoPolicy.Platform? = null
    private var lastTelegramContentCheckAt = 0L
    private var shortVideoRecheckAttempts = 0
    private var shortVideoRecheckScheduled = false
    private val shortVideoRecheck = object : Runnable {
        override fun run() {
            shortVideoRecheckScheduled = false
            val packageName = pendingShortVideoPackage ?: return
            val platform = pendingShortVideoPlatform ?: return
            if (!shortVideoPreferences.isEnabled(platform)) {
                cancelShortVideoRecheck()
                return
            }
            val root = rootInActiveWindow
            val activePackage = root?.packageName?.toString()
            val blocked = activePackage == packageName &&
                ShortVideoPolicy.isNativeShortVideoScreen(platform, root?.className?.toString(), root, packageName)
            root?.recycleCompat()
            if (blocked) {
                cancelShortVideoRecheck()
                blockShortVideo(platform, packageName)
                return
            }
            shortVideoRecheckAttempts++
            val isFacebook = platform == ShortVideoPolicy.Platform.FACEBOOK
            val limit = if (isFacebook) FACEBOOK_RECHECK_LIMIT else SHORT_VIDEO_RECHECK_LIMIT
            val interval = if (isFacebook) FACEBOOK_RECHECK_INTERVAL_MS else SHORT_VIDEO_RECHECK_INTERVAL_MS
            if (shortVideoRecheckAttempts < limit) {
                shortVideoRecheckScheduled = true
                mainHandler.postDelayed(this, interval)
            } else {
                cancelShortVideoRecheck()
            }
        }
    }

    private var lastBlockedValue = ""
    private var lastBlockAt = 0L

    /** Early-exit cache for the browser inspector (see inspectBrowser): identical fingerprint
     * inside this nano-time window ⇒ the heavy tree walk is skipped entirely. */
    private var lastBrowserScanKey = ""
    private var lastBrowserScanAtNs = 0L
    // Facebook's video feed fires content-changed/scroll events extremely fast; scanning its
    // huge node tree on every one of them froze the main thread (ANR). This throttles the
    // Facebook window scan only — every other platform is untouched.
    private var lastFacebookScanAt = 0L
    // Last time a non-Facebook Reels/Shorts window scan ran (throttles content-change churn).
    private var lastShortVideoScanAt = 0L
    // Last time the settings-protection guard ran a full-tree scan (throttles content-change churn).
    private var lastSettingsGuardAt = 0L
    // Throttle for the Telegram search BACK action: one press is enough to close search;
    // this stops a burst of content-changed events from firing repeated BACKs (which would
    // otherwise walk the user all the way out of Telegram).
    private var lastTelegramBackAt = 0L

    /** Last top-level Telegram activity seen — drives the chat-vs-home(global) search split. */
    private var lastTelegramActivity: String? = null

    /** Toasts for the Telegram search gate are throttled so back-to-back attempts stay calm. */
    private var lastTelegramToastAt = 0L

    /** Toasts for the system VPN settings gate are throttled so repeated retries stay calm. */
    private var lastSystemVpnSettingsToastAt = 0L

    /** Toasts for the Full Phone Settings Lock gate are throttled so retries stay calm. */
    private var lastFullSettingsLockToastAt = 0L
    // Throttle for the Telegram Web tree scan: web pages fire content-changed events rapidly,
    // so the two bounded scans (URL + search) are debounced to keep the browser responsive.
    private var lastTelegramWebScanAt = 0L

    /**
     * Deduplicates only *identical consecutive* text callbacks, which Android delivers two or
     * three times for a single keystroke. It is deliberately NOT a "handled" cache: it is
     * cleared by [resetDetectionState] after every block so that retyping or re-searching the
     * same keyword is always analysed again.
     */
    private var lastInspectedText = ""
    private var lastInspectedAt = 0L

    override fun onServiceConnected() {
        activeService = this
        ShieldRepository.initialize(this)
        ShieldRepository.refresh()
        AppTimeLimitStore.initialize(this)
        // Re-assert the shade-lock touch overlay as soon as the service is (re)connected — it
        // never expires with the process and must not depend on a later event to come back.
        refreshShadeLockState()
        BlockScheduleStore.initialize(this)
        NewAppQuarantineStore.initialize(this)
        settingsProtectionGuard.reset()
        settingsProtectionRepository.revokeTemporaryAccess()
        resetDetectionState()
        lastBlockedValue = ""
        lastBlockAt = 0L
        // Overlay permission is a soft preflight (the blocking UI is activity-based today);
        // ask exactly once per install, at the user-intent moment of service connection.
        runCatching { com.agon.app.security.OverlayAccessGate.maybePromptOnce(this) }
        // Protection must never depend on an Activity being open: make sure the always-on
        // supervisor is running as soon as the accessibility service connects, and arm the
        // keep-alive schedule so OEM killers revive us after hours of idle.
        com.agon.app.services.KeepAliveScheduler.schedule(this)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, com.agon.app.services.ProtectionGuardianService::class.java),
            )
        }
        // Re-load bundled + user keywords immediately so matching works after process death.
        serviceScope.launch {
            runCatching {
                com.agon.app.blocklist.domain.BuiltInAdultDomains.initialize(this@ShieldAccessibilityService)
                com.agon.app.blocklist.domain.BuiltInAdultKeywords.initialize(this@ShieldAccessibilityService)
                blocklistRepository.refreshEngine()
            }
        }
        serviceInfo = serviceInfo.apply {
            // Keep the runtime mask aligned with accessibility_service_config.xml:
            // only navigation + content + click. Typing / selection / scroll storms were the
            // primary source of binder-thread lag on mid-range devices.
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                // Long-press still needed for Safe Mode / factory-reset action interception.
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED or
                AccessibilityEvent.TYPE_VIEW_SELECTED or
                // WINDOWS_CHANGED covers multi-window / shade transitions without full typeAllMask.
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // 50ms debounce coalesces binder floods while still catching navigation promptly.
            notificationTimeout = 50
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
        discoverBrowsers()
        mainHandler.removeCallbacks(timeLimitCheckpoint)
        mainHandler.postDelayed(timeLimitCheckpoint, TIME_LIMIT_CHECKPOINT_MS)
        serviceScope.launch {
            // Single low-frequency refresh loop. Rules are pushed by the repository on change;
            // this only repairs state after process death. Browser discovery is repeated here
            // as well so a browser installed AFTER the service connected is still recognised
            // without waiting for a reboot or service reconnect.
            while (isActive) {
                blocklistRepository.refreshEngine()
                discoverBrowsers()
                delay(RULE_REFRESH_INTERVAL_MS)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return
        val type = event.eventType

        // Instant typing short-circuit: never walk the tree or run detectors while the user is
        // editing text. Prevents keyboard lag from accessibility binder work.
        if (type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY
        ) {
            return
        }
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val focusedEditable = runCatching {
                val src = event.source
                if (src != null) {
                    val editable = src.isEditable || src.isPassword
                    runCatching { src.recycle() }
                    editable
                } else {
                    false
                }
            }.getOrDefault(false)
            if (focusedEditable) return
        }

        // Track the currently visible app so overlay decisions elsewhere (e.g. the VPN block
        // screen) can tell an actively browsed page from a background SDK request.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            ShieldRepository.foregroundPackage = packageName
            // Re-evaluate the dynamic pull-strip overlay whenever the foreground host changes —
            // it only lives while the foreground app is a shade-leak surface (settings/launcher).
            refreshShadeLockState()
        }
        if (packageName == applicationContext.packageName) {
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                timeTracker.onForegroundApp(packageName)
            }
            return
        }

        // Notification-shade lock ("قفلکردنی دابەزاندنی نۆتیفەیشن") — HYBRID, NON-BLOCKING
        // enforcement: the callback thread reads the volatile flag and dispatches the geometry
        // sweep to the IO worker, then the event CONTINUES down the pipeline — every other
        // feature (Telegram search gate, keywords, websites, apps, settings guard… ) still sees
        // it. A pull never settles because each new frame schedules a sweep; single-flight keeps
        // a scroll burst from stacking hundreds of worker launches.
        if (shadeLockArmed &&
            (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_ANNOUNCEMENT)
        ) {
            if (shadeSweepInFlight.compareAndSet(false, true)) {
                serviceScope.launch {
                    try {
                        sweepShadeLock()
                    } finally {
                        shadeSweepInFlight.set(false)
                    }
                }
            }
            // Deliberately NO early return here: the event now flows on to the rest of the
            // pipeline (previously this `return` starved the Telegram / keyword / website
            // features whenever the shade lock was armed — see the work order).
        }

        // Quick-Settings bypass countermeasure (cross-vendor hardening): while protection is
        // armed and a settings host is in front, a pulled shade would let the diagonal swipe
        // reach its toggles — the panel is collapsed in-frame. Idle users are unaffected: this
        // fires only on confirmed-expanded geometry, only on settings-family foreground, and
        // only while protection is armed and not paused (identical to the floating rule).
        val settingsProtectionArmed = settingsProtectionRepository.currentSnapshot().active ||
            com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(this)
        if (settingsProtectionArmed &&
            !ShieldRepository.isProtectionPaused() &&
            ShieldRepository.foregroundPackage?.contains("settings") == true &&
            (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_SCROLLED) &&
            isShadeExpandedNow()
        ) {
            collapseNotificationShade()
            return
        }

        // Full Phone Settings Lock ("قفلکردنی تەواوی سێتینگی مۆبایل / قفل إعدادات الهاتف
        // بالكامل"): critical execution-failure fix. The PREVIOUS implementation routed this
        // through the protection PIN and only unlocked when `!snapshot.pinConfigured` was
        // false — i.e. it required the user to have ALSO configured the completely unrelated
        // "Settings Protection" PIN feature. Any user who armed this toggle WITHOUT ever
        // setting that separate PIN saw `unlocked = true` unconditionally, so the block never
        // fired at all — exactly the reported "the switch does nothing" failure. This lock is
        // now fully independent and unconditional (mirrors [NotificationShadeLock]'s own
        // contract exactly): the ONLY escape is the global credential-authorized pause, which
        // already suspends every protection surface in the app identically. 0ms ejection via
        // BACK then HOME the instant the Settings app opens on ANY OEM skin — no PIN, no
        // exception — reading [PhoneSettingsLock.isEnabled] live on every qualifying event so
        // a mid-session toggle takes effect on the very next window.
        if (com.agon.app.services.PhoneSettingsLock.isEnabled(this) &&
            isFullSettingsLockTarget(packageName) &&
            !ShieldRepository.isProtectionPaused() &&
            (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        ) {
            ejectFullSettingsLock(packageName)
            return
        }

        // Settings-protection must run even for packages excluded from keyword scanning (the
        // system Settings app is excluded from keyword blocking but is exactly where the
        // protection service would be switched off), so the guard is evaluated BEFORE the
        // keyword-exclusion early-return below.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
            // Some OEM skins surface the accessibility service detail screen (with its on/off
            // toggle) through a content change rather than a fresh window state, so the guard
            // must also see those events. The guard is throttled and de-duplicated internally,
            // so this adds no meaningful cost. Also covers power-menu Safe mode rows that only
            // populate their labels after the first content change.
            type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            // A real navigation / click is always evaluated at once. The high-frequency
            // CONTENT_CHANGED stream (a Settings list scrolling, a busy screen re-rendering) is
            // throttled so the guard's full-tree scan cannot pile up on the main thread and trip
            // an ANR on slower OEM devices.
            val guardNow = System.currentTimeMillis()
            val isContentChange = type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            // Zero-latency exception: frames of a Settings SEARCH host re-render results while the
            // user types; throttling them is exactly what let "Accessibility/Autostart" appear
            // behind "Admin". Search surfaces are light bounded lists, so every frame is safe.
            val className = event.className?.toString()?.lowercase().orEmpty()
            val isSearchHostFrame = className.contains("search")
            if (!isContentChange ||
                guardNow - lastSettingsGuardAt >= SETTINGS_GUARD_MIN_INTERVAL_MS ||
                isSearchHostFrame
            ) {
                lastSettingsGuardAt = guardNow
                guardAppSettings(packageName, event.className?.toString(), event)
            }
        }

        // Reels/Shorts protection is evaluated before pause and blocklist early returns. It is
        // permanently active in the background and cannot be disabled by the temporary pause.
        // navigation protection, not content classification, and therefore remains active even
        // when the user-managed keyword list is empty.
        val shortVideoPlatform = ShortVideoPolicy.nativePlatform(packageName)
        if (shortVideoPlatform != null && shortVideoPreferences.isEnabled(shortVideoPlatform)) {
            val isClickOrSelect = type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_SELECTED
            val wantsWindowScan = shouldInspectShortVideoWindow(type, shortVideoPlatform)

            // Facebook-only throttle for the expensive full-tree window scan. A real navigation
            // (window state/added) always scans immediately so entering Reels is caught at once;
            // the fast feed content-change/scroll churn is debounced so it can no longer flood
            // the main thread. Every other platform keeps its exact per-event behaviour.
            val now = System.currentTimeMillis()
            // A genuine navigation (new window) is always scanned immediately so entering
            // Reels/Shorts is caught at once. The churny CONTENT_CHANGED / SCROLLED stream is
            // throttled for EVERY platform (Facebook keeps its own tighter cadence) so the
            // expensive full-tree scan can never flood the main thread and cause an ANR.
            // Instagram: NEVER full-tree scan on scroll/content-change — home feed autoplay would
            // otherwise look like Reels. Only real navigation / selection / clicks open a scan.
            val isRealNavigation = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            val scanAllowed = when (shortVideoPlatform) {
                ShortVideoPolicy.Platform.FACEBOOK ->
                    isRealNavigation || now - lastFacebookScanAt >= FACEBOOK_SCAN_MIN_INTERVAL_MS
                ShortVideoPolicy.Platform.INSTAGRAM ->
                    isRealNavigation ||
                        type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                        type == AccessibilityEvent.TYPE_VIEW_CLICKED
                else ->
                    isRealNavigation || now - lastShortVideoScanAt >= SHORT_VIDEO_SCAN_MIN_INTERVAL_MS
            }
            val willScanWindow = wantsWindowScan && scanAllowed
            if (willScanWindow && !isRealNavigation) lastShortVideoScanAt = now

            // Fetch the active-window root at most ONCE per event and reuse it everywhere below.
            // Fetching it separately for each detector was the main source of the Facebook ANR.
            val root: AccessibilityNodeInfo? =
                if (isClickOrSelect || willScanWindow) rootInActiveWindow else null

            // The entry-click detectors only ever match on a click/select event (they return
            // false immediately otherwise), so gating them here is behaviour-preserving for
            // every platform while avoiding wasted tree walks on scroll/content-change events.
            val entryClicked = isClickOrSelect && (
                ShortVideoPolicy.isEntryClick(shortVideoPlatform, event) ||
                    ShortVideoPolicy.isShelfEntryClick(shortVideoPlatform, event, root) ||
                    ShortVideoPolicy.isPortraitRailCardClick(shortVideoPlatform, event, root)
                )

            if (shortVideoPlatform == ShortVideoPolicy.Platform.FACEBOOK && wantsWindowScan) {
                lastFacebookScanAt = now
            }

            val shortVideoWindow = willScanWindow &&
                ShortVideoPolicy.isNativeShortVideoScreen(
                    platform = shortVideoPlatform,
                    className = event.className?.toString(),
                    root = root,
                    packageName = packageName,
                )
            if (entryClicked || shortVideoWindow) {
                cancelShortVideoRecheck()
                blockShortVideo(shortVideoPlatform, packageName)
                return
            }
            // Recheck only after a real navigation/click — never on scroll/content churn.
            // Instagram home feed fires continuous CONTENT_CHANGED while scrolling; probing
            // that stream was a major source of false Reels blocks on ordinary videos.
            val shouldRecheck = type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            if (shouldRecheck) {
                scheduleShortVideoRecheck(shortVideoPlatform, packageName)
            }
        }

        // The Telegram-only link keyword is checked before ordinary keyword filtering. It is not
        // part of the global keyword list, so the same word remains usable everywhere else.
        if (TelegramSearchPolicy.isTelegram(packageName) &&
            (type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_SELECTED) &&
            blockTelegramVibecastLink(packageName, event)
        ) {
            return
        }

        // Telegram search protection (interaction-only, product contract):
        //  - Targets ONLY the "global search" on the chat-list surface (users / channels /
        //    public groups). In-chat search (inside an open conversation) is untouched.
        //  - Blocks ONLY on direct interaction: tapping the search entry, focusing it, or
        //    typing the first character. Showing the home screen never ejects anybody.
        //  - On a hit: instant BACK, then one verify-window presses BACK again if the search
        //    surface is still up, and a short localized toast explains the block.
        if (TelegramSearchPolicy.isTelegram(packageName) && telegramSearchPreferences.isEnabled()) {
            // Track the current top-level Telegram activity for the chat-vs-home split. Window
            // transitions carry the activity class; content churn never mutates it.
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
            ) {
                val candidate = event.className?.toString()
                if (!candidate.isNullOrBlank()) lastTelegramActivity = candidate
            }

            // In-chat search is a legitimate feature (in-chat search of messages): free entirely.
            if (!TelegramSearchPolicy.isChatContext(lastTelegramActivity)) {
                val isEntryInteraction = type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                    type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                    type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                    type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                    type == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                val isTypingInteraction = type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                    type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED ||
                    type == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY

                val interactionHit = when {
                    isEntryInteraction -> TelegramSearchPolicy.isSearchEntry(event)
                    isTypingInteraction -> TelegramSearchPolicy.isGlobalSearchTyping(event)
                    else -> false
                }
                if (interactionHit) {
                    // The media picker still wins absolutely: sending a photo must never be
                    // blocked and the keyboard must never be dismissed there.
                    val interactionRoot = rootInActiveWindow
                    val verdict = TelegramSearchPolicy.evaluate(interactionRoot)
                    interactionRoot?.recycleCompat()
                    if (verdict != TelegramSearchPolicy.Verdict.MEDIA_PICKER) {
                        blockTelegramSearch(packageName)
                        return
                    }
                }

                // OnePlus/OxygenOS (Android 10) field fix: taps on the top-bar magnifier there
                // can arrive with NO search marker anywhere in the payload or ancestor chain,
                // so click/typing-based interception alone misses the entry. A throttled,
                // window-content fallback(SEARCH verdict from the same bounded scan the rest of
                // the gate uses) ejects the surface the moment it is visible, before any text
                // is typed. Media picker still wins globally via evaluate()'s own ordering.
                if (!interactionHit && type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                    val now = System.currentTimeMillis()
                    if (now - lastTelegramContentCheckAt >= TELEGRAM_CONTENT_CHECK_MIN_MS) {
                        lastTelegramContentCheckAt = now
                        val root = rootInActiveWindow
                        val verdict = TelegramSearchPolicy.evaluate(root)
                        root?.recycleCompat()
                        if (verdict == TelegramSearchPolicy.Verdict.SEARCH) {
                            blockTelegramSearch(packageName)
                            return
                        }
                    }
                }
            }
        }

        // Telegram Web (web.telegram.org inside any browser) exposes the same search screen as the
        // native app. When enabled, detect it and press BACK to close the search overlay, leaving
        // the rest of Telegram Web usable. Only runs for browsers and only when the current page
        // is Telegram Web, so ordinary browsing is untouched.
        if (telegramSearchPreferences.isEnabled() && isBrowser(packageName)) {
            val inspectTgWeb = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
            val nowTg = System.currentTimeMillis()
            val tgNavigation = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                type == AccessibilityEvent.TYPE_VIEW_SELECTED
            val tgScanAllowed = tgNavigation ||
                nowTg - lastTelegramWebScanAt >= TELEGRAM_WEB_SCAN_MIN_INTERVAL_MS
            if (inspectTgWeb && tgScanAllowed) {
                lastTelegramWebScanAt = nowTg
                val root = rootInActiveWindow
                // One bounded scan decides both "is this Telegram Web" and "is search open",
                // so it works even when the browser hides its address bar behind the keyboard.
                val webVerdict = TelegramSearchPolicy.evaluateWeb(root)
                root?.recycleCompat()
                if (webVerdict == TelegramSearchPolicy.Verdict.SEARCH) {
                    blockTelegramSearch(packageName)
                    return
                }
            }
        }

        // Web preview cards can navigate to a short-video viewer before the browser exposes the
        // new URL. Inspect the click itself, then the existing browser debounce verifies the URL.
        if (isBrowser(packageName) &&
            (type == AccessibilityEvent.TYPE_VIEW_CLICKED || type == AccessibilityEvent.TYPE_VIEW_SELECTED)
        ) {
            val webRoot = rootInActiveWindow
            val currentUrl = extractUrlBar(webRoot)
            webRoot?.recycleCompat()
            ShortVideoPolicy.isWebEntryClick(currentUrl, event)
                ?.takeIf(shortVideoPreferences::isEnabled)
                ?.let { platform ->
                    blockShortVideo(platform, packageName)
                    return
                }
            // Block the destination before the browser navigates: domain/URL + title/snippet
            // of the tapped result are evaluated synchronously on the click event.
            if (inspectClickTarget(packageName, event)) return
        }

        // A verified temporary pause suspends ordinary app/content filtering, but the permanent
        // Reels/Shorts protection above remains active.
        if (ShieldRepository.isProtectionPaused()) return

        // Track every foreground transition before excluded packages return. Launchers, system
        // UI and BlockX LaAbrah itself must settle the app being left, otherwise its timer would
        // appear frozen while the user is outside it.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            val exhausted = timeTracker.onForegroundApp(packageName)
            if (!MonitoredApps.isExcluded(packageName) && exhausted) {
                showTimeLimitReached(packageName)
                return
            }
        }

        // Keyword / content scanning is skipped for excluded packages (the Settings app, the
        // launcher, this app …); time tracking above has already settled the previous app.
        if (MonitoredApps.isExcluded(packageName)) return

        // New-app quarantine runs before the blocklist engine so a just-installed app is
        // blocked even while rules are still loading. Approval is PIN-gated inside the app.
        if (isAppBlockingEvent(type) &&
            NewAppQuarantineStore.isInitialized() &&
            NewAppQuarantineStore.shouldBlock(packageName) &&
            isForegroundApp(packageName)
        ) {
            showQuarantineBlock(packageName)
            return
        }

        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            // VPN / circumvention-app blocking is opt-in and controlled by the user through the
            // VPN toggle (the Reels section). When off, dedicated VPN / proxy / Tor apps and
            // browsers tunnelling through a system proxy are NOT blocked, so the user is never
            // locked out by it. Other protections (websites, DNS, keywords) stay untouched.
            if (vpnProtectionPreferences.isEnabled()) {
                // Browsers such as Brave and Opera may bundle an optional tunnel service. They must
                // remain usable while our shield is active; only dedicated circumvention apps are
                // blocked here. Proxy use inside a browser is handled separately below.
                val circumventionApp = !isBrowser(packageName) &&
                    CircumventionPolicy.isCircumventionApp(this, packageName)
                val browserThroughProxy = isBrowser(packageName) &&
                    CircumventionPolicy.isSystemProxyActive(this)
                if (circumventionApp || browserThroughProxy) {
                    // Absolute exclusion: this very package is never a circumvention surface —
                    // the "block VPN apps" toggle must never collide with our own protection
                    // tunnel (reported conflict in the field).
                    if (packageName == applicationContext.packageName) return
                    showBlockedApp(packageName)
                    return
                }
            }
        }

        if (!BlockEngine.isInitialized()) {
            serviceScope.launch {
                blocklistRepository.refreshEngine()
                mainHandler.post {
                    // Same rule as the initialized path: only a real app entry may block, and
                    // only while that app still owns the active window once rules are loaded.
                    if (isAppBlockingEvent(type) &&
                        BlockEngine.isAppBlocked(packageName) &&
                        isForegroundApp(packageName)
                    ) {
                        showBlockedApp(packageName)
                    }
                }
            }
            return
        }

        if (isAppBlockingEvent(type)) {
            // Scheduled quiet-time block (bedtime, study, prayer …). Checked BEFORE the
            // blocklist engine so an active schedule wins even if no per-app rule is set.
            // Skips launcher, system UI and our own UI; everything else is blocked for the
            // duration of the window. Persistence and reboots are handled by the store.
            val activeSchedule = BlockScheduleStore.activeScheduleNow()
            if (activeSchedule != null &&
                packageName != applicationContext.packageName &&
                !MonitoredApps.isExcluded(packageName) &&
                isForegroundApp(packageName)
            ) {
                showScheduleBlock(packageName, activeSchedule.name)
                return
            }
        }

        if (isAppBlockingEvent(type) &&
            BlockEngine.isAppBlocked(packageName) &&
            isForegroundApp(packageName)
        ) {
            showBlockedApp(packageName)
            return
        }

        // Auto-detect unknown browsers: any foreground app showing a URL/address bar is treated as
        // a browser from now on, so keyword/URL protection is no longer limited to a fixed list or
        // to whatever package-manager discovery returned.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName !in detectedBrowsers && !MonitoredApps.isBrowser(packageName) &&
            !MonitoredApps.isExcluded(packageName) && hasBrowserUrlBar()
        ) {
            detectedBrowsers.add(packageName)
        }

        val inspectUserKeywords = BlockEngine.shouldInspectText()
        val inspectCircumvention = isBrowser(packageName) || MonitoredApps.isTextSurface(packageName)
        if (!inspectUserKeywords && !inspectCircumvention) return

        when (type) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            -> {
                inspectEditableText(packageName, event)
                // The first event of a burst can carry partial text (IME composing region,
                // paste still settling). One short verification of the focused field closes
                // that gap without any polling.
                scheduleFocusedFieldVerification(packageName)
            }

            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Focus moved: drop stale state and pending checks, then evaluate the new field.
                cancelFocusedFieldVerification()
                resetDetectionState()
                inspectEditableText(packageName, event)
                scheduleFocusedFieldVerification(packageName)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Only text-bearing subtree changes are relevant; everything else is ignored so
                // this high-frequency event type costs nothing.
                val changeTypes = event.contentChangeTypes
                val textChanged = changeTypes == 0 ||
                    (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT) != 0 ||
                    (changeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
                if (textChanged) inspectEditableText(packageName, event)
                if (isBrowser(packageName)) scheduleBrowserInspection()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (isBrowser(packageName)) scheduleBrowserInspection()
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                // Submission: re-check the focused field, then the page after it settles.
                inspectEditableText(packageName, event)
                inspectFocusedField(packageName)
                if (isBrowser(packageName)) scheduleBrowserInspection()
            }

            // Enter key, IME "Search" action and the on-screen search button all surface here.
            // Re-reading the field at this instant catches voice input and autocomplete, whose
            // text can appear without a preceding TYPE_VIEW_TEXT_CHANGED event.
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY,
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            -> inspectFocusedField(packageName)

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> {
                // A new window, a refresh or a repeated page load must always be re-evaluated,
                // so the per-field dedupe never carries over between screens.
                resetDetectionState()
                if (isBrowser(packageName)) scheduleBrowserInspection()
            }
        }
    }

    private fun isBrowser(packageName: String): Boolean =
        MonitoredApps.isBrowser(packageName) || packageName in detectedBrowsers

    /**
     * True for the actual system Settings app package on any OEM skin — the exact surface
     * Full Phone Settings Lock governs. Deliberately narrower than [looksLikeSettingsPackage]
     * (which also matches security-center / app-manager / app-store / SystemUI hosts): this
     * lock must only ever evict the literal Settings app, never the notification shade, a
     * security-center dashboard or an app store. Every OEM skin reviewed (AOSP/Pixel, Samsung
     * One UI, Xiaomi MIUI/HyperOS, Honor MagicOS, Huawei EMUI, Oppo ColorOS, Vivo Funtouch/
     * OriginOS, Realme UI) keeps its Settings app under a package literally ending in
     * ".settings", so a single suffix check covers every manufacturer, present and future.
     */
    private fun isFullSettingsLockTarget(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.android.settings" || lower.endsWith(".settings")
    }

    /**
     * Collapses an expanded notification shade / quick panel at once. The dedicated global
     * action exists on API 31+; older builds fall back to BACK, which every ROM treats as
     * "close the panel". No HOME, no PIN: this is a plain UI eviction, not a challenge.
     *
     * Samsung One UI safeguard (SDK 33/34): GLOBAL_ACTION_HOME is never triggered when the
     * foreground activity is a SubSettings or base SecSettings class on Samsung devices, because
     * pressing HOME from those containers causes the ejection loop reported on Galaxy M32 5G
     * (Android 13). BACK alone is sufficient to collapse the shade on Samsung; HOME is only
     * used on other OEMs where BACK may not reach the panel.
     */
    private fun collapseNotificationShade() {
        // TRIPLE forced eviction in the same frame, per the work order: the official dismiss
        // (API 31+), then BACK, then HOME — a panel that shrugs off one of them is pulled up by
        // the others, and every further window/scroll event races it again while held open.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            runCatching {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        }
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        // Samsung One UI safeguard: skip HOME on SubSettings/SecSettings classes (SDK 33/34)
        // to prevent the settings ejection loop (Galaxy M32 5G field report).
        if (!isSamsungOneUiSettingsContainer()) {
            runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
    }

    /**
     * Returns true when the current foreground is a Samsung One UI settings container class
     * (SubSettings, SecSettings2Activity, SecMainSettingsActivity) on SDK 33/34. Used to
     * suppress GLOBAL_ACTION_HOME which causes the ejection loop on these devices.
     */
    private fun isSamsungOneUiSettingsContainer(): Boolean {
        if (android.os.Build.VERSION.SDK_INT != 33 && android.os.Build.VERSION.SDK_INT != 34) {
            return false
        }
        val fg = ShieldRepository.foregroundPackage?.lowercase() ?: return false
        if (fg != "com.android.settings" && fg != "com.samsung.android.settings") return false
        val root = rootInActiveWindow ?: return false
        val cls = root.className?.toString()?.lowercase().orEmpty()
        root.recycleCompat()
        val isContainer = cls.contains("subsettings") ||
            cls.contains("secsettings2activity") ||
            cls.contains("secmainsettingsactivity") ||
            cls.contains("settingshomepageactivity") ||
            cls.contains("settingshomepage") ||
            cls.contains("settingclasses") ||
            cls.contains("samsungsettings")
        if (isContainer) {
            Log.d("ShieldAccessibility", "Samsung One UI bypass: suppressing GLOBAL_ACTION_HOME on $cls (SDK ${android.os.Build.VERSION.SDK_INT})")
        }
        return isContainer
    }

    /**
     * True while a pulled-down shade / quick-settings / control-centre panel covers the screen.
     * Geometry-driven, identical on every OEM and locale: walks the interactive windows, finds
     * an active/focused SystemUI window and accepts it as a panel only when it is nearly
     * full-width AND taller than a mere status strip. The always-on status bar stays under the
     * height threshold, so nothing fires during normal usage.
     */
    private fun isShadeExpandedNow(): Boolean {
        val metrics = resources.displayMetrics
        val minWidth = (metrics.widthPixels * SHADE_MIN_WIDTH_FRACTION).toInt()
        val minHeight = (metrics.heightPixels * SHADE_MIN_HEIGHT_FRACTION).toInt()
        val currentWindows = runCatching { windows }.getOrNull() ?: return false
        val rect = android.graphics.Rect()
        for (window in currentWindows) {
            // No TYPE_APPLICATION filter any more: several ROMs (One UI, HyperOS panels) report
            // the expanded shade as a SYSTEM-class accessibility window, so the type gate was
            // silently excluding exactly the window we hunt. Geometry + owner are the truth.
            val root = window.root
            val windowPackage = root?.packageName?.toString().orEmpty()
            root?.recycleCompat()
            // ONLY SystemUI-family packages can own a pull-down panel. The previous revision
            // also matched "controlcenter"/"securitycenter" hosts — on HyperOS the App-Info page
            // itself is hosted by com.miui.securitycenter with full-screen bounds, which made it
            // look exactly like an expanded panel and ejected the user from App Info. Strictly
            // systemui now: every OEM SystemUI fork (AOSP, OneUI, HyperOS/MIUI, MagicOS, EMUI,
            // ColorOS, OriginOS, Pixel) ships its shade under a *systemui* package.
            if (!windowPackage.contains("systemui")) continue
            window.getBoundsInScreen(rect)
            if (rect.width() >= minWidth && rect.height() > minHeight) return true
        }
        return false
    }

    /** True when the active window exposes a browser-style URL/address bar. */
    private fun hasBrowserUrlBar(): Boolean {
        val root = rootInActiveWindow ?: return false
        val found = extractUrlBar(root).isNotBlank()
        root.recycleCompat()
        return found
    }

    private fun shouldInspectShortVideoWindow(
        eventType: Int,
        platform: ShortVideoPolicy.Platform,
    ): Boolean {
        // Instagram home feed scrolls continuously; never treat scroll/content-change as a
        // Reels window probe. Viewer entry is always a click/tab/window transition.
        if (platform == ShortVideoPolicy.Platform.INSTAGRAM) {
            return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
                eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        }
        return eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SELECTED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
    }

    /**
     * App blocking is driven by real navigation into an app only. `TYPE_WINDOW_CONTENT_CHANGED`
     * (and `TYPE_WINDOWS_CHANGED`) are also emitted while the launcher home screen, the app
     * drawer or the recents overview merely *draws* an icon or a task card of a blocked app —
     * on several launchers those events even carry the blocked app's own package name. Reacting
     * to them made the block screen appear over the home screen and made the icon impossible to
     * remove. Only `TYPE_WINDOW_STATE_CHANGED` means the app itself was actually opened.
     */
    private fun isAppBlockingEvent(eventType: Int): Boolean =
        eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

    /**
     * Second guard for app blocking: the package must really own the active window. Launcher and
     * recents transitions can still emit a window-state event tagged with the blocked app's
     * package while the launcher stays in front; comparing against the active window's root
     * package filters those out. When the active window cannot be resolved at all (some OEM apps
     * expose no root) the block is still enforced, so protection is never silently lost.
     */
    private fun isForegroundApp(packageName: String): Boolean {
        val foreground = resolveForegroundPackage()
        return foreground.isNullOrBlank() || foreground == packageName
    }

    private fun scheduleShortVideoRecheck(
        platform: ShortVideoPolicy.Platform,
        packageName: String,
    ) {
        if (pendingShortVideoPackage != packageName || pendingShortVideoPlatform != platform) {
            cancelShortVideoRecheck()
            pendingShortVideoPackage = packageName
            pendingShortVideoPlatform = platform
            shortVideoRecheckAttempts = 0
        }
        if (shortVideoRecheckScheduled) return
        shortVideoRecheckScheduled = true
        mainHandler.postDelayed(shortVideoRecheck, SHORT_VIDEO_RECHECK_INITIAL_DELAY_MS)
    }

    private fun cancelShortVideoRecheck() {
        mainHandler.removeCallbacks(shortVideoRecheck)
        shortVideoRecheckScheduled = false
        pendingShortVideoPackage = null
        pendingShortVideoPlatform = null
        shortVideoRecheckAttempts = 0
    }

    /** Returns to the previous screen immediately without closing the host social application. */
    private fun blockShortVideo(platform: ShortVideoPolicy.Platform, packageName: String) {
        cancelShortVideoRecheck()
        val blockKey = "SHORT_VIDEO:${platform.name}:$packageName"
        // Enforcement must NEVER be skipped: the dedupe window only guards the stats/log entry,
        // otherwise a Shorts opened from the home feed right after a blocked attempt (or a
        // quick back-and-tap sequence) would stay completely unblocked.
        val shouldLog = canBlock(blockKey)
        if (shouldLog) {
            lastBlockedValue = blockKey
            lastBlockAt = System.currentTimeMillis()
            ShieldRepository.recordBlocked(platform.displayName, "Short-video protection")
        }
        resetDetectionState()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Sends the user back out of the Telegram search entry point. Uses BACK (not HOME) so the
     * user simply returns to the chat list they came from, keeping the rest of Telegram usable.
     */
    private fun blockTelegramSearch(packageName: String) {
        // One BACK closes the search; ignore further triggers for a short window so the rapid
        // content-changed burst during the close transition cannot push the user out of the app.
        val now = System.currentTimeMillis()
        if (now - lastTelegramBackAt < TELEGRAM_BACK_THROTTLE_MS) return
        lastTelegramBackAt = now

        val blockKey = "TELEGRAM_SEARCH:$packageName"
        if (canBlock(blockKey)) {
            lastBlockedValue = blockKey
            lastBlockAt = now
            ShieldRepository.recordBlocked("Telegram", "Telegram search protection")
        }
        resetDetectionState()
        // BACK once, instantly. A single verification pass after the close animation then
        // presses BACK again only if the search surface is still alive, and the user hears why
        // via one short, throttled toast.
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        mainHandler.postDelayed({
            runCatching {
                val root = rootInActiveWindow
                val stillOpen = TelegramSearchPolicy.evaluate(root) == TelegramSearchPolicy.Verdict.SEARCH
                root?.recycleCompat()
                if (stillOpen) performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }, TELEGRAM_SEARCH_VERIFY_MS)
        showTelegramSearchToast()
    }

    /** One calm toast per blocked attempt (throttled so rapid retries stay quiet). */
    private fun showTelegramSearchToast() {
        val now = System.currentTimeMillis()
        if (now - lastTelegramToastAt < TELEGRAM_TOAST_THROTTLE_MS) return
        lastTelegramToastAt = now
        val language = ShieldRepository.state.value.language
        runCatching {
            android.widget.Toast.makeText(
                this,
                tr("telegram_search_blocked_toast", language),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Blocks a clicked Telegram link when its URL contains the Telegram-only "vibecast" keyword.
     * The check is intentionally scoped to Telegram packages; vibecast is not in the global
     * keyword/domain lists and therefore remains allowed in every other app.
     */
    private fun blockTelegramVibecastLink(
        packageName: String,
        event: AccessibilityEvent,
    ): Boolean {
        val source = event.source
        return try {
            val targetUrl = extractLinkFromNode(source).ifBlank {
                buildList {
                    event.text?.forEach { value -> value?.toString()?.let(::add) }
                    event.contentDescription?.toString()?.let(::add)
                }.asSequence()
                    .map(::extractUrlCandidate)
                    .firstOrNull(String::isNotBlank)
                    .orEmpty()
            }
            if (targetUrl.isBlank() || !TelegramSearchPolicy.containsTelegramOnlyBlockedKeyword(targetUrl)) {
                false
            } else {
                // Keep duplicate accessibility click events from stacking block screens, while
                // still consuming the event so Telegram cannot proceed with the same navigation.
                val blockKey = "TELEGRAM_VIBECAST:$targetUrl"
                if (!canBlock(blockKey)) {
                    true
                } else {
                    val now = System.currentTimeMillis()
                    lastBlockedValue = blockKey
                    lastBlockAt = now
                    val reason = "Blocked keyword: vibecast"
                    blocklistRepository.recordBlock("vibecast", RuleType.KEYWORD, reason, packageName)
                    ShieldRepository.recordBlocked(targetUrl, "Telegram link protection")
                    resetDetectionState()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    showBlockedWebsite(targetUrl, reason, "Blocked keyword", 100)
                    true
                }
            }
        } finally {
            source?.recycleCompat()
        }
    }

    // ------------------------------------------------- global keyword protection

    /**
     * Inspects the editable field that produced [event]. Covers typing, pasting and submission
     * across browsers, search, social, messaging, mail and notes apps — every surface that
     * exposes its text through accessibility, which is the limit of what Android permits.
     */
    private fun inspectEditableText(packageName: String, event: AccessibilityEvent) {
        val builder = StringBuilder(96)
        event.text?.forEach { value -> if (value != null) builder.append(value).append(' ') }
        event.contentDescription?.let { builder.append(it) }

        val source = event.source
        var editable = MonitoredApps.isTextSurface(packageName) || isBrowser(packageName)
        var explicitInput = event.className?.toString()?.contains("EditText", ignoreCase = true) == true
        if (source != null) {
            if (source.isEditable) {
                editable = true
                explicitInput = true
                // Prefer the node's own text: it always holds the complete field value, while
                // the event payload can be a partial IME composition.
                source.text?.let { full ->
                    if (full.isNotEmpty()) {
                        builder.setLength(0)
                        builder.append(full)
                    }
                }
            } else if (builder.isBlank()) {
                source.text?.let { builder.append(it) }
            }
        }
        source?.recycleCompat()

        val raw = builder.toString().trim()
        if (raw.isEmpty() || raw.length > MAX_FIELD_LENGTH) return
        // User-managed keywords are enforced in EVERY app: many browsers and WebView/Compose
        // based apps never mark their input node editable (or deliver a null source), which
        // previously limited detection to Chrome-like browsers and the fixed text-surface
        // list. Text is still deduplicated and length-capped, and circumvention enforcement
        // stays restricted to explicit input fields to avoid false positives on plain UI text.
        analyzeText(raw, packageName, enforceCircumvention = explicitInput && editable)
    }

    /**
     * Shared analysis path for every source of text. Deduplicates only identical consecutive
     * values so repeated typing of the same blocked keyword is always caught again after a
     * block (the state is reset by [resetDetectionState]).
     */
    private fun analyzeText(
        raw: String,
        packageName: String,
        enforceCircumvention: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (raw == lastInspectedText && now - lastInspectedAt < TEXT_DEDUPE_MS) return
        lastInspectedText = raw
        lastInspectedAt = now

        // Circumvention keyword enforcement ("vpn", "proxy", …) only runs while the user-
        // enabled VPN/circumvention blocking toggle is ON (the Reels section control). When it
        // is off, ordinary input containing those words must pass through untouched.
        if (enforceCircumvention && vpnProtectionPreferences.isEnabled()) {
            CircumventionPolicy.inputBlockReason(raw)?.let { reason ->
                blockCircumventionInput(raw, reason, packageName)
                return
            }
        }

        // Normalize once, then run every user-managed matcher against the canonical form.
        if (!BlockEngine.shouldInspectText()) return
        val normalized = TextNormalizer.normalize(TextNormalizer.decodeUrl(raw))
        val keyword = BlockEngine.matchingBlockedKeywordNormalized(normalized)
            ?: BlockEngine.matchingUrlKeyword(raw)
        if (keyword != null) {
            blockKeywordInput(keyword, packageName)
            return
        }


        // A typed or pasted URL is checked against the domain rules as well, which stops a
        // blocked host before the request is ever issued.
        val candidate = TextNormalizer.normalizeDomain(raw)
        if (candidate.contains('.') && BlockEngine.isWebsiteBlocked(candidate)) {
            blockBrowser(candidate, RuleType.WEBSITE, packageName)
        }
    }

    /**
     * Reacts to a blocked keyword in an input field.
     *
     * Ordering is critical and deliberate: the field is emptied **synchronously, first**, before
     * any navigation or logging. Detection runs on text change, i.e. while the user is still
     * typing, so by the time Enter / the search button / the IME search action is delivered the
     * query string is already gone and no request is ever issued. Android does not expose a way
     * to veto a keystroke, so emptying the source of the request is the strongest supported
     * equivalent of cancelling it.
     */
    private fun blockCircumventionInput(value: String, reason: String, packageName: String) {
        val displayValue = value.trim().take(160).ifBlank { "Circumvention service" }
        if (!canBlock("BYPASS:$displayValue")) return
        lastBlockedValue = "BYPASS:$displayValue"
        lastBlockAt = System.currentTimeMillis()
        clearFocusedField()
        performGlobalAction(GLOBAL_ACTION_BACK)
        ShieldRepository.recordBlocked(displayValue, "Circumvention protection")
        showBlockedWebsite(displayValue, reason, "Circumvention blocked", 100)
        resetDetectionState()
    }

    private fun blockKeywordInput(keyword: String, packageName: String) {
        if (!canBlock("KW:$keyword")) return
        lastBlockedValue = "KW:$keyword"
        lastBlockAt = System.currentTimeMillis()

        // 1. Destroy the query before it can be submitted.
        clearFocusedField()
        // 2. Dismiss the IME and any autocomplete dropdown that could still submit it.
        if (SearchPolicy.isSearchSurface(packageName)) performGlobalAction(GLOBAL_ACTION_BACK)

        val reason = "Blocked keyword: $keyword"
        blocklistRepository.recordBlock(keyword, RuleType.KEYWORD, reason, packageName)
        ShieldRepository.recordBlocked(keyword, "Keyword protection")

        performGlobalAction(GLOBAL_ACTION_BACK)
        showBlockedWebsite(keyword, reason, "Blocked keyword", 100)

        // Detection must fire every single time. Clearing the transient state here guarantees
        // the next attempt at the same keyword is evaluated from scratch.
        resetDetectionState()
    }

    /**
     * Drops every transient detection state so no event can ever be treated as "already
     * handled". Called after each block and whenever the window or focus changes.
     */
    private fun resetDetectionState() {
        lastInspectedText = ""
        lastInspectedAt = 0L
        pendingVerificationPackage = null
        mainHandler.removeCallbacks(focusedFieldVerification)
    }

    /** Package awaiting the single delayed verification pass. */
    @Volatile
    private var pendingVerificationPackage: String? = null

    private val focusedFieldVerification = Runnable {
        val target = pendingVerificationPackage ?: return@Runnable
        pendingVerificationPackage = null
        inspectFocusedField(target)
    }

    /**
     * Schedules exactly one lightweight re-read of the focused editable field. This covers the
     * case where the triggering event carried incomplete text; it never repeats and is replaced
     * (not stacked) if another event arrives first.
     */
    private fun scheduleFocusedFieldVerification(packageName: String) {
        pendingVerificationPackage = packageName
        mainHandler.removeCallbacks(focusedFieldVerification)
        mainHandler.postDelayed(focusedFieldVerification, FIELD_VERIFY_DELAY_MS)
    }

    private fun cancelFocusedFieldVerification() {
        pendingVerificationPackage = null
        mainHandler.removeCallbacks(focusedFieldVerification)
    }

    /**
     * Reads the currently focused editable node directly. Required for Compose and WebView
     * surfaces whose content-changed events carry no text payload at all.
     */
    private fun inspectFocusedField(packageName: String) {
        val focused = runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull() ?: return
        val text = focused.text?.toString().orEmpty()
        val editable = focused.isEditable
        focused.recycleCompat()
        if (!editable || text.isBlank() || text.length > MAX_FIELD_LENGTH) return
        analyzeText(text.trim(), packageName, enforceCircumvention = true)
    }

    /** Best-effort clearing of the focused editable node using official node actions. */
    private fun clearFocusedField() {
        // Try the input-focused node first, then fall back to searching the tree: some browsers
        // report focus on a container rather than on the omnibox itself.
        val focused = runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (focused != null) {
            val cleared = clearNode(focused)
            focused.recycleCompat()
            if (cleared) return
        }
        val root = runCatching { rootInActiveWindow }.getOrNull() ?: return
        findEditableNode(root, 0)?.let { node ->
            clearNode(node)
            node.recycleCompat()
        }
    }

    /** Empties an editable node using the official SET_TEXT action. */
    private fun clearNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isEditable) return false
        val current = node.text
        if (current.isNullOrEmpty()) return true
        return runCatching {
            val arguments = android.os.Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
    }

    /** Depth-limited search for the first non-empty editable node in the window. */
    private fun findEditableNode(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_TREE_DEPTH) return null
        if (node.isEditable && !node.text.isNullOrEmpty()) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = findEditableNode(child, depth + 1)
            if (found != null) {
                if (found !== child) child.recycleCompat()
                return found
            }
            child.recycleCompat()
        }
        return null
    }

    // ------------------------------------------------------- browser inspection

    private val delayedInspection = Runnable { inspectCurrentWindow() }
    private val settledInspection = Runnable { inspectCurrentWindow() }

    private fun scheduleBrowserInspection() {
        mainHandler.removeCallbacks(delayedInspection)
        mainHandler.postDelayed(delayedInspection, BROWSER_DEBOUNCE_MS)
        // Second pass after the page settles: progressive rendering and pull-to-refresh can
        // populate the tree AFTER the first debounced pass ran, and no further event is
        // guaranteed. Re-running the same idempotent inspection closes that gap.
        mainHandler.removeCallbacks(settledInspection)
        mainHandler.postDelayed(settledInspection, BROWSER_RESCAN_MS)
    }

    private fun inspectCurrentWindow() {
        var root = rootInActiveWindow
        var packageName = root?.packageName?.toString().orEmpty()

        // Multi-window fallback (tablets / split-screen / DeX): when the active window is not a
        // browser (it is often the other pane), find the interactive browser window from the
        // window list, so a tab switch inside the browser pane is never missed.
        if (root == null || !isBrowser(packageName)) {
            val browserRoot = findForegroundBrowserWindowRoot()
            if (browserRoot != null) {
                root?.recycleCompat()
                root = browserRoot
                packageName = root.packageName?.toString().orEmpty()
            }
        }
        if (root == null || packageName.isBlank()) {
            root?.recycleCompat()
            return
        }
        try {
            if (packageName == applicationContext.packageName) return
            guardAppSettings(packageName, root.className?.toString())
            if (BlockEngine.isAppBlocked(packageName)) {
                showBlockedApp(packageName)
                return
            }
            if (isBrowser(packageName)) inspectBrowser(packageName, null, root)
        } finally {
            root.recycleCompat()
        }
    }

    /**
     * Finds the root of the currently interactive BROWSER window in the live window list. Used
     * by the multi-window fallback path (a split-screen tablet may have the browser visible but
     * not as the active window) — the budget is the window list, always a handful.
     */
    private fun findForegroundBrowserWindowRoot(): AccessibilityNodeInfo? = runCatching {
        windows
            .asSequence()
            .filter { it.isActive || it.isFocused }
            .mapNotNull { window ->
                val windowRoot = window.root ?: return@mapNotNull null
                val pkg = windowRoot.packageName?.toString()
                if (pkg != null && isBrowser(pkg)) {
                    windowRoot
                } else {
                    windowRoot.recycleCompat()
                    null
                }
            }
            .firstOrNull()
    }.getOrNull()

    /**
     * Universal cross-device filtering fix (tablet / split-screen / DeX): [root] is now taken
     * as a PARAMETER — the SAME window root [inspectCurrentWindow] already resolved through its
     * multi-window fallback ([findForegroundBrowserWindowRoot]) — instead of re-fetching
     * `rootInActiveWindow` here. On a tablet in split-screen/multi-window (or any layout where
     * the browser pane is visible but not the OS-reported "active" window, which is far more
     * common on large-screen devices than on phones), the previous re-fetch silently returned a
     * DIFFERENT window's root (or none at all): the URL bar, page title and visible-text scans
     * below then read the wrong tree, so [BlockEngine.evaluateNavigationSurface] never saw the
     * real page and blocked sites never triggered a filter hit. Ownership of [root]'s lifecycle
     * (including recycling) stays with the caller — this function only reads it.
     */
    private fun inspectBrowser(packageName: String, event: AccessibilityEvent?, root: AccessibilityNodeInfo?) {
        val urlBar = extractUrlBar(root)

        // Early-exit cache: the delayed+settled double-pass (and tablet event churn) reproduce
        // the same scan twice per navigation. An identical (package, urlBar, root description)
        // fingerprint ⇒ the exact same result as the previous pass; skip it and save the whole
        // collectText walk. Tablet editors no longer pay for continuous re-renders at all.
        val nowScanNs = System.nanoTime()
        val scanKey = packageName + '|' + urlBar + '|' +
            (root?.contentDescription?.toString().orEmpty().hashCode())
        if (scanKey == lastBrowserScanKey && nowScanNs - lastBrowserScanAtNs < BROWSER_SCAN_DEDUPE_NS) {
            return
        }
        lastBrowserScanKey = scanKey
        lastBrowserScanAtNs = nowScanNs

        if (urlBar.isNotEmpty()) {
            ShortVideoPolicy.blockedWebPlatform(urlBar)
                ?.takeIf(shortVideoPreferences::isEnabled)
                ?.let { platform ->
                    blockShortVideo(platform, packageName)
                    return
                }
            if (vpnProtectionPreferences.isEnabled()) {
                CircumventionPolicy.inputBlockReason(urlBar)?.let { reason ->
                    blockCircumventionInput(urlBar, reason, packageName)
                    return
                }
            }
        }

        // Collect the title + ONLY the text actually visible on screen, then evaluate
        // domain/URL/keywords in a single real-time pass before the page is allowed to stay open.
        // The AccessibilityEvent's own text/contentDescription is deliberately NOT mixed in here:
        // on a browser a WINDOW_CONTENT_CHANGED event can carry stale, offscreen or previously
        // rendered text (a prior page, a hidden suggestion, a recycled row), which produced blocks
        // for words that were not on the current screen. collectText() walks the live tree and
        // keeps only nodes reported visible to the user, so the analysed snippet always matches
        // exactly what the user is actually seeing.
        val pageTitle = extractPageTitle(root, event)
        val snippet = buildString {
            collectText(root, this, 0)
        }
        BlockEngine.evaluateNavigationSurface(
            url = urlBar,
            title = pageTitle,
            snippet = snippet,
        )?.let { (value, type) ->
            blockBrowser(value, type, packageName)
            return
        }

        if (snippet.isBlank() && urlBar.isEmpty()) return

        // AI content classification is DISABLED by product decision: blocking now comes only
        // from the fixed lists (websites / keywords / apps + bundled domain index). Any future
        // re-enable would reintroduce an AI call here — every other path is list-based.
    }

    /**
     * Inspects the tapped node (search result / link) before navigation. Returns true when a
     * block was applied so the caller can stop further work for this event.
     */
    private fun inspectClickTarget(packageName: String, event: AccessibilityEvent): Boolean {
        val source = event.source
        try {
            val targetUrl = extractLinkFromNode(source).ifBlank {
                // Only fetch the window root when the click node itself had no link, and recycle
                // it immediately so this fallback never leaks a node tree.
                val clickRoot = rootInActiveWindow
                val fromBar = extractUrlBar(clickRoot)
                clickRoot?.recycleCompat()
                fromBar
            }
            val title = buildString {
                source?.text?.let { append(it) }
                source?.contentDescription?.let {
                    if (isNotEmpty()) append(' ')
                    append(it)
                }
                event.text?.forEach {
                    if (isNotEmpty()) append(' ')
                    append(it)
                }
                event.contentDescription?.let {
                    if (isNotEmpty()) append(' ')
                    append(it)
                }
            }
            val snippet = if (source != null) {
                buildString { collectText(source, this, 0) }
            } else {
                title
            }
            if (targetUrl.isEmpty() && title.isBlank() && snippet.isBlank()) return false

            if (targetUrl.isNotEmpty()) {
                ShortVideoPolicy.blockedWebPlatform(targetUrl)
                    ?.takeIf(shortVideoPreferences::isEnabled)
                    ?.let { platform ->
                        blockShortVideo(platform, packageName)
                        return true
                    }
                if (vpnProtectionPreferences.isEnabled()) {
                    CircumventionPolicy.inputBlockReason(targetUrl)?.let { reason ->
                        blockCircumventionInput(targetUrl, reason, packageName)
                        return true
                    }
                }
            }

            BlockEngine.evaluateNavigationSurface(
                url = targetUrl,
                title = title,
                snippet = snippet,
            )?.let { (value, type) ->
                // blockBrowser performs BACK + the blocked screen so the destination never loads.
                blockBrowser(value, type, packageName)
                return true
            }
            return false
        } finally {
            source?.recycleCompat()
        }
    }

    /** Best-effort page / result title from the active window or the driving event. */
    private fun extractPageTitle(root: AccessibilityNodeInfo?, event: AccessibilityEvent?): String {
        // The page title is read only from the live, on-screen node tree — never from the
        // AccessibilityEvent's own text/contentDescription, which can be stale or offscreen on a
        // browser and would otherwise let a keyword from non-visible content trigger a block.
        if (root == null) return ""
        val id = root.viewIdResourceName
        if (root.isVisibleToUser && id != null && (id.contains("title", true) || id.contains("heading", true))) {
            root.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            root.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        // Prefer a short heading-like node near the top of the tree.
        val heading = findHeadingText(root, 0)
        if (heading.isNotBlank()) return heading
        return ""
    }

    private fun findHeadingText(node: AccessibilityNodeInfo?, depth: Int): String {
        if (node == null || depth > 6) return ""
        val id = node.viewIdResourceName.orEmpty()
        val heading = id.contains("title", true) ||
            id.contains("heading", true) ||
            (android.os.Build.VERSION.SDK_INT >= 28 && node.isHeading)
        // Only accept a heading the user can actually see, so an offscreen/hidden title node can
        // never supply text that is treated as the current page.
        if (heading && node.isVisibleToUser) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        val childCount = node.childCount
        for (index in 0 until childCount) {
            val child = node.getChild(index) ?: continue
            val found = findHeadingText(child, depth + 1)
            child.recycleCompat()
            if (found.isNotBlank()) return found
        }
        return ""
    }

    /**
     * Pulls a URL/domain from a clicked accessibility node (link, result row, anchor).
     * Checks text, contentDescription, view id and a shallow child walk.
     */
    private fun extractLinkFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        fun candidateOf(info: AccessibilityNodeInfo): String {
            val values = listOfNotNull(
                info.text?.toString(),
                info.contentDescription?.toString(),
                info.viewIdResourceName,
            )
            for (raw in values) {
                val url = extractUrlCandidate(raw)
                if (url.isNotEmpty()) return url
            }
            return ""
        }
        candidateOf(node).takeIf { it.isNotEmpty() }?.let { return it }
        val childCount = minOf(node.childCount, 12)
        for (index in 0 until childCount) {
            val child = node.getChild(index) ?: continue
            val found = candidateOf(child).ifEmpty { extractLinkFromNode(child) }
            child.recycleCompat()
            if (found.isNotEmpty()) return found
        }
        // Parent often holds the href-like description on result rows.
        val parent = node.parent
        try {
            if (parent != null) {
                candidateOf(parent).takeIf { it.isNotEmpty() }?.let { return it }
            }
        } finally {
            parent?.recycleCompat()
        }
        return ""
    }

    private fun extractUrlCandidate(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
            return trimmed
        }
        // Bare domain inside a longer description: "example.com - Site title"
        val match = URL_IN_TEXT.find(trimmed) ?: return ""
        return match.value
    }

    // ------------------------------------------------------------- block actions

    /**
     * Immediate (0ms) eviction for the Full Phone Settings Lock — unconditional, no PIN
     * challenge involved (see the call site for why the previous PIN-routed implementation
     * silently never fired). BACK first unwinds Settings off its own back-stack (so a later
     * Recents-card tap cannot resume it either), then HOME brings the launcher to front. A
     * short, throttled toast explains the block; no dedicated block screen is shown, since
     * Settings itself must never render as a per-app "blocked app" surface.
     */
    private fun ejectFullSettingsLock(packageName: String) {
        if (!canBlock("FULL_SETTINGS_LOCK")) return
        lastBlockedValue = "FULL_SETTINGS_LOCK"
        lastBlockAt = System.currentTimeMillis()
        ShieldRepository.recordBlocked(packageName, "Full phone settings lock")
        resetDetectionState()
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        // Samsung One UI safeguard: skip HOME on SubSettings/SecSettings (SDK 33/34)
        // to prevent the settings ejection loop (Galaxy M32 5G field report).
        // BACK alone is sufficient to leave the settings surface on Samsung.
        mainHandler.postDelayed({
            if (!isSamsungOneUiSettingsContainer()) {
                runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            }
        }, 120)
        showFullSettingsLockToast()
    }

    /** One calm toast per blocked attempt (throttled so rapid retries stay quiet). */
    private fun showFullSettingsLockToast() {
        val now = System.currentTimeMillis()
        if (now - lastFullSettingsLockToastAt < FULL_SETTINGS_LOCK_TOAST_THROTTLE_MS) return
        lastFullSettingsLockToastAt = now
        val language = ShieldRepository.state.value.language
        runCatching {
            android.widget.Toast.makeText(
                this,
                tr("full_settings_lock_blocked_toast", language),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /**
     * Immediate (0ms) eviction of the system VPN settings page. Deliberately does NOT reuse
     * [showBlockedApp] / [BlockedAppActivity]: that screen resolves and displays the app label
     * for [packageName], which here would misleadingly read "Settings is blocked" and directly
     * contradict Selective Settings Blocking (Settings itself must never appear wholesale
     * blocked). BACK first unwinds the VPN page off the Settings task's own back-stack (so a
     * later Recents-card tap resumes Settings' previous screen rather than the VPN page again),
     * then HOME brings the launcher to front — identical order to every other sensitive-task
     * evacuation in this service. A short, throttled toast explains the block without opening
     * any screen.
     */
    private fun ejectSystemVpnSettings(packageName: String) {
        if (mustSuppressBlocking(packageName)) return
        if (!canBlock("VPN_SETTINGS")) return
        lastBlockedValue = "VPN_SETTINGS"
        lastBlockAt = System.currentTimeMillis()
        blocklistRepository.recordBlock("system_vpn_settings", RuleType.APP)
        ShieldRepository.recordBlocked("VPN settings", "System VPN settings blocked")
        resetDetectionState()
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        // Samsung One UI safeguard: skip HOME on SubSettings/SecSettings (SDK 33/34)
        mainHandler.postDelayed({
            if (!isSamsungOneUiSettingsContainer()) {
                runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
            }
        }, 120)
        showSystemVpnSettingsToast()
    }

    /**
     * Ejects the user from Private DNS settings so DoT/DoH cannot bypass the local DNS tunnel.
     * Always ends on HOME so the Settings Private DNS page cannot remain interactive.
     */
    private fun ejectPrivateDnsSettings() {
        if (!canBlock("PRIVATE_DNS")) return
        lastBlockedValue = "PRIVATE_DNS"
        lastBlockAt = System.currentTimeMillis()
        ShieldRepository.recordBlocked("Private DNS", "Private DNS settings blocked")
        resetDetectionState()
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        mainHandler.postDelayed({
            runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        }, 80)
        showPrivateDnsBlockedToast()
    }

    @Volatile
    private var lastPrivateDnsToastAt = 0L

    private fun showPrivateDnsBlockedToast() {
        val now = System.currentTimeMillis()
        if (now - lastPrivateDnsToastAt < 2_500L) return
        lastPrivateDnsToastAt = now
        val language = ShieldRepository.state.value.language
        val message = when (language) {
            "en" -> "Private DNS is blocked — it bypasses protection"
            "ar" -> "تم حظر DNS الخاص — يتجاوز الحماية"
            else -> "DNS تایبەت بلۆک کرا — پاراستن تێدەپەڕێنێت"
        }
        runCatching {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /** One calm toast per blocked attempt (throttled so rapid retries stay quiet). */
    private fun showSystemVpnSettingsToast() {
        val now = System.currentTimeMillis()
        if (now - lastSystemVpnSettingsToastAt < SYSTEM_VPN_SETTINGS_TOAST_THROTTLE_MS) return
        lastSystemVpnSettingsToastAt = now
        val language = ShieldRepository.state.value.language
        runCatching {
            android.widget.Toast.makeText(
                this,
                tr("system_vpn_settings_blocked_toast", language),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun showBlockedApp(packageName: String) {
        if (mustSuppressBlocking(packageName)) return
        if (!canBlock(packageName)) return
        lastBlockedValue = packageName
        lastBlockAt = System.currentTimeMillis()
        blocklistRepository.recordBlock(packageName, RuleType.APP)
        ShieldRepository.recordBlocked(packageName, "Blocked app")
        killBlockedProcess(packageName)
        resetDetectionState()

        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, BlockedAppActivity::class.java)
                        .putExtra(BlockedAppActivity.EXTRA_PACKAGE, packageName)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }, 120)
    }

    /**
     * Sends the user home and shows the block screen when an app's daily time budget is spent.
     * Reuses [BlockedAppActivity] with a time-limit flag so the message explains the limit
     * rather than a permanent block.
     */
    /** Scheduled quiet-time block (bedtime / study / prayer windows). */
    private fun showScheduleBlock(packageName: String, scheduleName: String) {
        if (mustSuppressBlocking(packageName)) return
        if (!canBlock("SCHED:$packageName")) return
        lastBlockedValue = "SCHED:$packageName"
        lastBlockAt = System.currentTimeMillis()
        ShieldRepository.recordBlocked(packageName, "Scheduled block: $scheduleName")
        killBlockedProcess(packageName)
        resetDetectionState()

        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, BlockedAppActivity::class.java)
                        .putExtra(BlockedAppActivity.EXTRA_PACKAGE, packageName)
                        .putExtra(BlockedAppActivity.EXTRA_SCHEDULE_NAME, scheduleName)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }, 120)
    }

    /** Quarantine block for a freshly installed app that awaits parent approval. */
    private fun showQuarantineBlock(packageName: String) {
        if (mustSuppressBlocking(packageName)) return
        if (!canBlock("QUAR:$packageName")) return
        lastBlockedValue = "QUAR:$packageName"
        lastBlockAt = System.currentTimeMillis()
        ShieldRepository.recordBlocked(packageName, "New app quarantine")
        killBlockedProcess(packageName)
        resetDetectionState()

        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, BlockedAppActivity::class.java)
                        .putExtra(BlockedAppActivity.EXTRA_PACKAGE, packageName)
                        .putExtra(BlockedAppActivity.EXTRA_QUARANTINE, true)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }, 120)
    }

    private fun showTimeLimitReached(packageName: String) {
        if (mustSuppressBlocking(packageName)) return
        if (!canBlock("TIME:$packageName")) return
        timeTracker.flush()
        lastBlockedValue = "TIME:$packageName"
        lastBlockAt = System.currentTimeMillis()
        ShieldRepository.recordBlocked(packageName, "Daily time limit reached")
        killBlockedProcess(packageName)
        resetDetectionState()

        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, BlockedAppActivity::class.java)
                        .putExtra(BlockedAppActivity.EXTRA_PACKAGE, packageName)
                        .putExtra(BlockedAppActivity.EXTRA_TIME_LIMIT, true)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }, 120)
    }

    private fun blockAiContent(
        signals: AiContentClassifier.Signals,
        result: AiContentClassifier.Result,
        browserPackage: String,
    ) {
        val value = signals.domain.ifBlank { signals.searchQuery.take(160) }
            .ifBlank { result.category.displayName }
        if (!canBlock("AI:$value")) return
        lastBlockedValue = "AI:$value"
        lastBlockAt = System.currentTimeMillis()
        blocklistRepository.recordAiBlock(signals, result, browserPackage)
        ShieldRepository.recordBlocked(value, "${result.category.displayName} (${result.confidence}%)")
        performGlobalAction(GLOBAL_ACTION_BACK)
        showBlockedWebsite(value, result.reason, result.category.displayName, result.confidence)
        resetDetectionState()
    }

    private fun blockBrowser(value: String, type: RuleType, browserPackage: String) {
        if (!canBlock(value)) return
        lastBlockedValue = value
        lastBlockAt = System.currentTimeMillis()
        val reason = if (type == RuleType.KEYWORD) {
            "Blocked keyword: $value"
        } else {
            BlockEngine.websiteBlockReason(value) ?: "Blocked website rule"
        }
        blocklistRepository.recordBlock(value, type, reason, browserPackage)
        ShieldRepository.recordBlocked(value, "Browser protection")
        performGlobalAction(GLOBAL_ACTION_BACK)
        showBlockedWebsite(
            value,
            reason,
            if (type == RuleType.KEYWORD) "Blocked keyword" else "Website rule",
            100,
        )
        resetDetectionState()
    }

    private fun showBlockedWebsite(value: String, reason: String, category: String, confidence: Int) {
        mainHandler.postDelayed({
            runCatching {
                startActivity(
                    Intent(this, BlockedWebsiteActivity::class.java)
                        .putExtra(BlockedWebsiteActivity.EXTRA_DOMAIN, value)
                        .putExtra(BlockedWebsiteActivity.EXTRA_REASON, reason)
                        .putExtra(BlockedWebsiteActivity.EXTRA_CATEGORY, category)
                        .putExtra(BlockedWebsiteActivity.EXTRA_CONFIDENCE, confidence)
                        .putExtra(BlockedWebsiteActivity.EXTRA_TIME, System.currentTimeMillis())
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION,
                        ),
                )
            }
        }, 100)
    }

    private fun canBlock(value: String): Boolean =
        value != lastBlockedValue || System.currentTimeMillis() - lastBlockAt > BLOCK_DEDUPE_MS

    /**
     * Emergency-calling safety valve (urge/panic mode included): the dialer and in-call UI are
     * never blocked, and while any call rings or runs the overlay is suspended globally. All
     * other blocking behaviour is completely untouched — the exemption is exclusive to calls.
     */
    private fun mustSuppressBlocking(packageName: String): Boolean =
        PhoneCallPolicy.mustSuppressBlocking(this, packageName)

    /** The blocked app must not keep running in the background after the overlay eject. */
    private fun killBlockedProcess(packageName: String) {
        runCatching {
            (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)
                ?.killBackgroundProcesses(packageName)
        }
    }

    // ------------------------------------------------------ settings protection

    /**
     * Intercepts the system "App info" family of screens for this package. Runs before any
     * blocklist work so protection wins even when the blocklist engine is still loading.
     *
     * Lifecycle behaviour: every WINDOW_STATE / WINDOWS / CONTENT change re-evaluates. When the
     * tree is not yet populated (common on resume before the first draw), a short burst of
     * rechecks runs so the PIN appears without the user having to scroll or tap.
     */
    private fun guardAppSettings(
        packageName: String,
        className: String?,
        event: AccessibilityEvent? = null,
    ) {
        // System VPN Settings interception (extends "Block VPN & bypass apps"): while VPN
        // blocking is armed, opening Settings → Connections → VPN — or any OEM equivalent
        // (Samsung One UI, Xiaomi MIUI/HyperOS, Honor MagicOS, Huawei EMUI, Oppo ColorOS, Vivo
        // Funtouch/OriginOS, Realme UI) — must be ejected exactly like opening a dedicated VPN
        // app; otherwise a manually created/edited VPN profile from system Settings bypasses
        // the whole feature. Independent of Settings-protection PIN state (a separate toggle,
        // "Phone Settings Lock") and evaluated FIRST, before the floating-window pre-check
        // below, so a freeform/split VPN settings panel is caught identically to a full-screen
        // one — 0ms ejection via BACK/HOME, no PIN involved (same contract as the dedicated
        // VPN-app block this feature extends).
        if (vpnProtectionPreferences.isEnabled() && looksLikeSettingsPackage(packageName)) {
            val vpnRoot = eventWindowRoot(packageName)
            val isVpnSettingsPage = try {
                settingsScreenDetector.isSystemVpnSettingsSurface(packageName, className.orEmpty(), vpnRoot)
            } catch (_: Exception) {
                false
            } finally {
                runCatching { vpnRoot?.recycleCompat() }
            }
            if (isVpnSettingsPage) {
                ejectSystemVpnSettings(packageName)
                return
            }
        }

        // Private DNS (DoT/DoH hostname) bypass prevention: opening or editing Private DNS in
        // system Settings ejects the user to Home so encrypted DNS cannot leave the VPN tunnel.
        if (looksLikeSettingsPackage(packageName) && !ShieldRepository.isProtectionPaused()) {
            val dnsRoot = eventWindowRoot(packageName)
            val isPrivateDnsPage = try {
                settingsScreenDetector.isPrivateDnsSettingsSurface(
                    packageName,
                    className.orEmpty(),
                    dnsRoot,
                )
            } catch (_: Exception) {
                false
            } finally {
                runCatching { dnsRoot?.recycleCompat() }
            }
            if (isPrivateDnsPage) {
                ejectPrivateDnsSettings()
                return
            }
        }

        // Floating-settings check: a freeform / pop-up window owned by the Settings app while
        // protection is armed is a POTENTIAL bypass attempt (bounds-narrower-than-screen =
        // floating). Only Settings is targeted — every other app's floating windows stay
        // untouched per contract. The pause window also frees floating Settings windows: while
        // the user holds a valid 3-minute pause, no settings surface (floating or not) is
        // challenged at all.
        //
        // Samsung One UI critical targeted fix: window GEOMETRY alone ("is this Settings window
        // narrower than the full screen?") is true for EVERY dialog Settings ever shows — Wi-Fi
        // password prompts, "Forget network?", a plain confirmation — regardless of device or
        // OEM. Geometry can therefore never be a safe trigger by itself. The contract is now
        // STRICT ALLOWLIST, not blanket block: a small window is challenged ONLY when
        // [SettingsProtectionGuard.isConfirmedProtectedSurface] — the SAME structural detector
        // that governs every other surface in this app, narrowed to Selective Settings Blocking
        // (Accessibility / Downloaded apps / Device Admin) while Phone Settings Lock is OFF —
        // positively identifies it as a protected screen. Any other small Settings window falls
        // through untouched to the ordinary evaluate() pipeline below instead of evacuating the
        // whole Settings app.
        val protectionArmed = (settingsProtectionRepository.currentSnapshot().active ||
            com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(this)) &&
            !ShieldRepository.isProtectionPaused()
        if (protectionArmed &&
            (packageName == "com.android.settings" || packageName == "com.samsung.android.settings") &&
            isSettingsFloatingWindow()
        ) {
            val floatingRoot = eventWindowRoot(packageName)
            val confirmedProtected = try {
                settingsProtectionGuard.isConfirmedProtectedSurface(packageName, className, floatingRoot)
            } catch (_: Exception) {
                false
            } finally {
                runCatching { floatingRoot?.recycleCompat() }
            }
            if (confirmedProtected) {
                raiseSettingsPinChallenge()
                return
            }
            // Not confirmed as one of the explicitly protected (and currently allowed)
            // categories: deliberately no early return here — falls through to the normal
            // evaluate() pipeline below, which still catches a genuinely protected screen
            // reached via a floating window through its own class/text detection, just never
            // on size alone.
        }

        val isClick = event != null && (
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED
            )
        // A real navigation (new window / screen). Used by the guard to end the strictly
        // surface-bound factory-reset unlock the moment the user leaves that screen.
        val isWindowTransition = event != null &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        val clickSource = if (isClick) event?.source else null
        val clickTexts: List<CharSequence> = if (isClick && event != null) {
            buildList {
                event.text?.let { addAll(it) }
                event.contentDescription?.let { add(it) }
            }
        } else {
            emptyList()
        }
        val decision = try {
            settingsProtectionGuard.evaluate(
                packageName = packageName,
                className = className,
                root = eventWindowRoot(packageName),
                clickSource = clickSource,
                clickEventTexts = clickTexts,
                windowTransition = isWindowTransition,
            )
        } catch (_: Exception) {
            SettingsProtectionGuard.Decision.IGNORE
        } finally {
            clickSource?.recycleCompat()
        }
        when (decision) {
            SettingsProtectionGuard.Decision.CHALLENGE -> {
                cancelSettingsProtectionRecheck()
                // Speed-trick defense: flip our freshly-disabled autostart switch back ON
                // synchronously, in the SAME frame and BEFORE HOME is pressed, so a sub-second
                // tap can never actually leave the protection disabled. No-op for other types.
                settingsProtectionGuard.revertDetectedProtectionSwitch(eventWindowRoot(packageName))
                raiseSettingsPinChallenge()
            }
            SettingsProtectionGuard.Decision.IGNORE -> {
                // After a Settings click, Samsung may open MasterClear a moment later — recheck.
                if (isClick && looksLikeSettingsPackage(packageName)) {
                    scheduleSettingsProtectionRecheck(packageName, className)
                } else if (!isClick && looksLikeSettingsPackage(packageName)) {
                    scheduleSettingsProtectionRecheck(packageName, className)
                } else if (!looksLikeSettingsPackage(packageName)) {
                    cancelSettingsProtectionRecheck()
                }
            }
            SettingsProtectionGuard.Decision.ALLOW -> {
                cancelSettingsProtectionRecheck()
            }
        }
    }

    /**
     * Raises the full-screen PIN challenge.
     *
     * Some ROMs (Samsung One UI and similar strict background-launch enforcement) silently
     * drop a background Activity launch from a process without a visible window — the launch
     * neither throws nor shows anything. A single launch is therefore never trusted:
     * [PinLockActivity] reports when it is actually visible (guard.onChallengeActivityShown)
     * and, until that happens, the launch is retried with backoff. Meanwhile the guard never
     * allows the protected surface through, so a dropped launch can never open Admin /
     * protected settings without the PIN on any device.
     */
    private fun raiseSettingsPinChallenge() {
        // Device-admin deactivation hardening (all OEMs): when the PIN is raised because the
        // operator touched a DEVICE_ADMIN surface, lock the screen first — a deactivating guard
        // cannot quietly be uninstalled mid-tap. lockNow() is a no-op (caught) unless THIS app
        // holds active admin rights.
        if (settingsProtectionGuard.detection?.type ==
            com.agon.app.settingsprotection.domain.ProtectedScreenType.DEVICE_ADMIN
        ) {
            runCatching {
                val devicePolicy = getSystemService(DevicePolicyManager::class.java)
                val component = android.content.ComponentName(
                    this,
                    com.agon.app.admin.ShieldDeviceAdminReceiver::class.java,
                )
                if (devicePolicy.isAdminActive(component)) devicePolicy.lockNow()
            }
        }
        // Throttle the whole dispatch (HOME + launch): while a challenge is unconfirmed the
        // guard re-challenges on every event, but the actual dispatch must not run on each of
        // them. A skipped dispatch is safe — the previous dispatch's retry chain is still
        // active and keeps trying to bring the PIN on screen.
        val now = SystemClock.elapsedRealtime()
        if (now - lastChallengeDispatchAt < CHALLENGE_DISPATCH_MIN_INTERVAL_MS) {
            return
        }
        lastChallengeDispatchAt = now
        // Sensitive-task evacuation: BACK first (it unwinds the protected activity off the
        // settings task's stack, so a later Recents-card tap resumes the settings HOME rather
        // than the forbidden page), then HOME pulls the user out to the launcher, then PIN.
        // The triple BACK gets REPLACED by this because Home-after-Back is the only order that
        // actually clears the saved task state.
        evacuateSensitiveTaskThenHome()
        challengeRetryIndex = 0
        attemptChallengeLaunch()
    }

    /**
     * Evacuate a protected surface: (1) BACK at 0ms pops the forbidden activity off the
     * settings task's own back-stack — the cleanest, OEM-agnostic way to make a later
     * Recents-card tap resume the settings HOME instead of the tamper surface; (2) HOME at
     * +120ms then brings the launcher in front. The order matters: after HOME the settings
     * task is backgrounded and no longer accepts navigation, so BACK must come first.
     * The SystemUI Recents screen itself is never touched — it is not a tamper surface and
     * no rule ever gates it.
     */
    /**
     * True when com.android.settings currently renders inside a free-form / pop-up window
     * (MIUI floating window, Samsung pop-up view, OEM small-screen panel …). Detection is
     * structural: the active application window for Settings is narrower/shorter than the real
     * display — full-screen Settings never trips it, so regular users are unaffected.
     */
    private fun isSettingsFloatingWindow(): Boolean = runCatching {
        val dm = resources.displayMetrics
        windows.firstOrNull { window ->
            if (!window.isActive || window.type != AccessibilityWindowInfo.TYPE_APPLICATION) {
                false
            } else {
                val root = window.root ?: return@firstOrNull false
                val pkg = root.packageName?.toString()
                root.recycleCompat()
                pkg == "com.android.settings" || pkg == "com.samsung.android.settings"
            }
        }?.let { window ->
            val rect = android.graphics.Rect()
            window.getBoundsInScreen(rect)
            rect.width() < (dm.widthPixels * 0.88f) || rect.height() < (dm.heightPixels * 0.92f)
        } == true
    }.getOrDefault(false)

    private fun evacuateSensitiveTaskThenHome() {
        runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }
        mainHandler.postDelayed({
            runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
        }, 120)
    }

    private var lastChallengeDispatchAt = 0L
    private var lastChallengeLaunchAt = 0L
    private var challengeRetryIndex = 0
    private var challengeRetryScheduled = false

    private val challengeRetry = object : Runnable {
        override fun run() {
            challengeRetryScheduled = false
            val guard = settingsProtectionGuard
            // Stop once the challenge is resolved (granted / denied / cancelled) or the PIN
            // lock is actually on screen.
            if (!guard.challengeDispatched || guard.challengeVisible) return
            // If the user has already moved on to a completely different app, do not chase
            // them: their next visit to a protected surface challenges fresh.
            val foreground = resolveForegroundPackage()
            if (foreground != null && !looksLikeSettingsPackage(foreground) &&
                !isHomeOrSystemUi(foreground)
            ) {
                return
            }
            attemptChallengeLaunch()
        }
    }

    private fun attemptChallengeLaunch() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastChallengeLaunchAt < CHALLENGE_LAUNCH_MIN_INTERVAL_MS) {
            scheduleChallengeRetry()
            return
        }
        lastChallengeLaunchAt = now
        // Zero-delay launch: the PIN overlay must intercept the very first frame of the
        // protected surface so a fast double-tap cannot slip a toggle through before the lock
        // is physically on screen. The retry chain below covers ROMs that drop the first
        // background launch attempt.
        mainHandler.postDelayed({
            val guard = settingsProtectionGuard
            if (guard.challengeDispatched && !guard.challengeVisible) {
                try {
                    startActivity(guard.challengeIntent())
                } catch (_: Exception) {
                    // A dropped launch is not a dead end: the retry loop re-attempts.
                }
            }
        }, 0)
        scheduleChallengeRetry()
    }

    private fun scheduleChallengeRetry() {
        if (challengeRetryScheduled) return
        val delay = CHALLENGE_RETRY_DELAYS_MS.getOrNull(challengeRetryIndex) ?: return
        challengeRetryIndex++
        challengeRetryScheduled = true
        mainHandler.postDelayed(challengeRetry, delay)
    }

    private var cachedHomePackage: String? = null

    /**
     * True for the default launcher (Home) and SystemUI (recents / lock screen) — the
     * surfaces the user is on right after the challenge dispatch presses HOME. Retries must
     * continue there; only a genuinely different third-party app stops them.
     */
    private fun isHomeOrSystemUi(packageName: String): Boolean {
        if (packageName.contains("systemui")) return true
        val home = cachedHomePackage ?: runCatching {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
        }.getOrNull().orEmpty()
        if (home.isNotEmpty()) cachedHomePackage = home
        // Unknown launcher (resolution failed) → keep retrying: the cost of the PIN briefly
        // appearing over the launcher is small, missing the retry would leave the protected
        // surface unchallenged.
        return home.isEmpty() || packageName == home
    }

    private var settingsRecheckPackage: String? = null
    private var settingsRecheckClass: String? = null
    private var settingsRecheckAttempts = 0
    private var settingsRecheckScheduled = false

    private val settingsProtectionRecheck = object : Runnable {
        override fun run() {
            settingsRecheckScheduled = false
            val packageName = settingsRecheckPackage ?: return
            val className = settingsRecheckClass
            // Prefer the live foreground package so a stale recheck cannot fire after the user
            // already left settings.
            val foreground = resolveForegroundPackage() ?: packageName
            if (foreground != packageName && !looksLikeSettingsPackage(foreground)) {
                cancelSettingsProtectionRecheck()
                return
            }
            val targetPackage = if (looksLikeSettingsPackage(foreground)) foreground else packageName
            val decision = try {
                settingsProtectionGuard.evaluate(
                    targetPackage,
                    className,
                    eventWindowRoot(targetPackage),
                    clickSource = null,
                    clickEventTexts = emptyList(),
                )
            } catch (_: Exception) {
                SettingsProtectionGuard.Decision.IGNORE
            }
            when (decision) {
                SettingsProtectionGuard.Decision.CHALLENGE -> {
                    cancelSettingsProtectionRecheck()
                    raiseSettingsPinChallenge()
                }
                SettingsProtectionGuard.Decision.ALLOW -> cancelSettingsProtectionRecheck()
                SettingsProtectionGuard.Decision.IGNORE -> {
                    settingsRecheckAttempts += 1
                    if (settingsRecheckAttempts < SETTINGS_PROTECTION_RECHECK_LIMIT) {
                        settingsRecheckScheduled = true
                        mainHandler.postDelayed(this, SETTINGS_PROTECTION_RECHECK_INTERVAL_MS)
                    } else {
                        cancelSettingsProtectionRecheck()
                    }
                }
            }
        }
    }

    private fun scheduleSettingsProtectionRecheck(packageName: String, className: String?) {
        settingsRecheckPackage = packageName
        settingsRecheckClass = className
        if (settingsRecheckScheduled) return
        settingsRecheckAttempts = 0
        settingsRecheckScheduled = true
        mainHandler.postDelayed(settingsProtectionRecheck, SETTINGS_PROTECTION_RECHECK_INITIAL_MS)
    }

    private fun cancelSettingsProtectionRecheck() {
        if (settingsRecheckScheduled) {
            mainHandler.removeCallbacks(settingsProtectionRecheck)
            settingsRecheckScheduled = false
        }
        settingsRecheckPackage = null
        settingsRecheckClass = null
        settingsRecheckAttempts = 0
    }

    private fun looksLikeSettingsPackage(packageName: String): Boolean {
        if (packageName.isBlank() || packageName == applicationContext.packageName) return false
        val lower = packageName.lowercase()
        return lower.contains("settings") ||
            lower.contains("packageinstaller") ||
            lower.contains("permissioncontroller") ||
            // OEM permission/app-manager hosts that keep protected surfaces (App Info, admin,
            // accessibility) on their own packages: HyperOS permcenter, EMUI/MagicOS
            // systemmanager/privacycenter, ColorOS securitypermission, Vivo appdetail.
            lower.contains("permissionmanager") ||
            lower.contains("securitypermission") ||
            lower.contains("permcenter") ||
            lower.contains("privacycenter") ||
            lower.contains("systemmanager") ||
            lower.contains("appmanager") ||
            lower.contains("appdetail") ||
            lower == "com.android.systemui" ||
            lower.contains("systemui") ||
            lower.contains("securitycenter") ||
            lower.contains("securitycore") ||
            lower.contains("safecenter") ||
            lower.contains("globalactions") ||
            lower.contains("documentsui") ||
            lower.contains("documents") ||
            // App stores with direct uninstall affordances (Play / Galaxy Store / AppGallery /
            // GetApps / HeyTap / Vivo) — their protected pages must keep challenge retries alive.
            lower.contains("vending") ||
            lower.contains("appmarket") ||
            lower.contains("appstore") ||
            lower.contains("mipicks") ||
            lower.contains("heytap")
    }

    // --------------------------------------------------------------- traversal

    private fun extractUrlBar(node: AccessibilityNodeInfo?): String =
        extractUrlBar(node, 0, IntArray(1))

    /**
     * Finds the browser URL/address bar text. A URL bar always lives near the top of the tree, so
     * a shallow depth cap plus a hard node budget keep this off the main thread's critical path:
     * without them a huge page (a long chat thread) forced a full-tree walk here — and it was
     * called twice per pass — which was a primary ANR cause on slower devices.
     */
    /**
     * Tablet/DeX-grade URL-bar resolver. Deep-but-bounded BREADTH-FIRST traversal with an idle
     * budget identical to the previous implementation (the ANR protection contract is
     * preserved): every child of every container is walked regardless of nesting depth — this
     * is exactly what tablet browsers need (their omnibox hides under AddressBar/UrlBar/
     * Omnibox/SearchBox wrappers several levels deep). Priorities: a node whose view id carries
     * a URL-bar token wins instantly; otherwise the first EDITABLE node holding a real
     * URL/domain is remembered as the fallback (covers Brave's "url_bar" edit view and the
     * simple WebView-based browsers that expose no ids at all).
     */
    private fun extractUrlBar(node: AccessibilityNodeInfo?, depth: Int, visited: IntArray): String {
        if (node == null) return ""
        if (visited[0] >= MAX_URL_BAR_NODES) return ""
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        visited[0]++
        queue.add(node)
        var editableUrlCandidate = ""
        try {
            while (queue.isNotEmpty() && visited[0] < MAX_URL_BAR_NODES) {
                val current = queue.removeFirst()
                visited[0]++
                val id = current.viewIdResourceName.orEmpty().lowercase()
                val idHit = URL_BAR_ID_TOKENS.any { id.contains(it) }
                if (idHit) {
                    // Known omnibox id: return its content the moment we see it — and prefer
                    // editable content (it reflects the live field) over static labels.
                    val text = current.text?.toString().orEmpty().trim()
                    if (text.isNotEmpty()) {
                        // …still peek ONE level down for the real EditText some skins wrap.
                        val inner = firstSelectableChildText(current)
                        current.recycleCompat()
                        return inner.ifBlank { text }
                    }
                }
                if (editableUrlCandidate.isEmpty() && current.isEditable) {
                    val text = current.text?.toString().orEmpty().trim()
                    val looksLikeUrl = text.length in 4..MAX_FIELD_LENGTH &&
                        (text.contains("://") || text.contains("www.") || URL_IN_TEXT.find(text) != null)
                    if (looksLikeUrl) editableUrlCandidate = text
                }
                val count = minOf(current.childCount, MAX_URL_BAR_CHILDREN)
                for (index in 0 until count) {
                    if (visited[0] >= MAX_URL_BAR_NODES) break
                    val child = current.getChild(index) ?: continue
                    queue.add(child)
                }
            }
        } finally {
            while (queue.isNotEmpty()) queue.removeFirst().recycleCompat()
        }
        return editableUrlCandidate
    }

    /** One shallow level: the first child carrying non-blank text (id-bearing wrappers). */
    private fun firstSelectableChildText(node: AccessibilityNodeInfo): String {
        val count = minOf(node.childCount, 6)
        for (index in 0 until count) {
            val child = node.getChild(index) ?: continue
            val text = child.text?.toString().orEmpty().trim()
            child.recycleCompat()
            if (text.isNotEmpty()) return text
        }
        return ""
    }

    private fun collectText(node: AccessibilityNodeInfo?, result: StringBuilder, depth: Int) {
        collectText(node, result, depth, IntArray(1))
    }

    /**
     * Walks the accessibility tree collecting visible text. A hard visit budget ([nodeBudget])
     * caps how many nodes are ever touched: a very large browser DOM (a long chat thread, an
     * endless feed) could otherwise take hundreds of milliseconds on the main thread and trip the
     * system's "isn't responding" (ANR) dialog. The cap keeps every scan bounded and smooth while
     * still gathering more than enough text for the keyword/AI engines.
     */
    private fun collectText(node: AccessibilityNodeInfo?, result: StringBuilder, depth: Int, visited: IntArray) {
        if (node == null || depth > MAX_WEB_TREE_DEPTH || result.length > MAX_TEXT_LENGTH) return
        if (visited[0] >= MAX_TEXT_NODES) return
        visited[0]++
        // Only harvest text the user can actually SEE. Reading offscreen / hidden nodes (other
        // tabs, collapsed menus, recycled list rows kept in the tree) caused false blocks where a
        // keyword appeared in content that was never on screen. A node's own text is collected
        // only when it is visible to the user; children are still traversed because a container
        // may report itself as not visible while its on-screen children are.
        if (node.isVisibleToUser) {
            node.text?.let { result.append(' ').append(it) }
            node.contentDescription?.let { result.append(' ').append(it) }
        }
        val childCount = node.childCount
        for (index in 0 until childCount) {
            if (result.length > MAX_TEXT_LENGTH || visited[0] >= MAX_TEXT_NODES) return
            val child = node.getChild(index) ?: continue
            collectText(child, result, depth + 1, visited)
            child.recycleCompat()
        }
    }

    /**
     * Root node of the window that actually belongs to [packageName].
     *
     * In multi-window / split-screen / pop-up-view the window that fired an accessibility event
     * is not necessarily the ACTIVE window: `rootInActiveWindow` then returns a DIFFERENT app's
     * tree, so scanning it could not see a floating Settings App-info / Accessibility page while
     * another app held focus — a real bypass on freeform-capable skins. Prefer the window whose
     * root package matches the event package; fall back to the active root when unresolvable
     * (some hosts expose no root at all — behaviour identical to before in that case).
     */
    private fun eventWindowRoot(packageName: String): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active?.packageName?.toString() == packageName) return active
        runCatching { active?.recycleCompat() }
        return runCatching {
            windows
                .asSequence()
                .mapNotNull { window -> window.root }
                .firstOrNull { root -> root.packageName?.toString() == packageName }
        }.getOrNull()
    }

    /** Resolves the active package even on OEM apps that expose no active root directly. */
    private fun resolveForegroundPackage(): String? {
        val direct = runCatching {
            val root = rootInActiveWindow
            val packageName = root?.packageName?.toString()
            root?.recycleCompat()
            packageName
        }.getOrNull()
        if (!direct.isNullOrBlank()) return direct

        return runCatching {
            windows
                .asSequence()
                .filter { window -> window.isActive || window.isFocused }
                .mapNotNull { window ->
                    val root = window.root ?: return@mapNotNull null
                    val packageName = root.packageName?.toString()
                    root.recycleCompat()
                    packageName
                }
                .firstOrNull { packageName -> packageName.isNotBlank() }
        }.getOrNull()
    }

    private fun discoverBrowsers() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
            // MATCH_ALL is required: without it Android 6+ can return only the user's default
            // browser for a web intent, leaving every other installed browser undetected — the
            // reason keyword/URL blocking appeared to work only in Chrome and Brave.
            packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_ALL)
                .mapTo(detectedBrowsers) { it.activityInfo.packageName }
            packageManager.queryIntentActivities(intent, 0)
                .mapTo(detectedBrowsers) { it.activityInfo.packageName }
            // Some lesser-known browsers only advertise a BROWSABLE web capability rather than a
            // plain VIEW handler, so query that explicitly too.
            val browsable = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            packageManager.queryIntentActivities(browsable, android.content.pm.PackageManager.MATCH_ALL)
                .mapTo(detectedBrowsers) { it.activityInfo.packageName }
        }
    }

    /**
     * The system interrupts feedback (for example during a phone call). Any half-finished
     * detection state is dropped so the next event is evaluated cleanly.
     */
    override fun onInterrupt() {
        mainHandler.removeCallbacks(delayedInspection)
        mainHandler.removeCallbacks(settledInspection)
        cancelSettingsProtectionRecheck()
        resetDetectionState()
    }

    /**
     * Called when the service is unbound, which is what happens if the user switches
     * accessibility off. Clearing state guarantees a clean start on reconnect, and the guardian
     * raises the user-facing warning.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        if (activeService === this) activeService = null
        shadeLockArmed = false
        updateShadeLockOverlay(false)
        cancelShortVideoRecheck()
        cancelSettingsProtectionRecheck()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { timeTracker.flush() }
        resetDetectionState()
        runCatching { settingsProtectionGuard.reset() }
        lastBlockedValue = ""
        lastBlockAt = 0L
        com.agon.app.services.KeepAliveScheduler.schedule(this)
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                this,
                Intent(this, com.agon.app.services.ProtectionGuardianService::class.java),
            )
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        runCatching { timeTracker.flush() }
        cancelShortVideoRecheck()
        cancelSettingsProtectionRecheck()
        mainHandler.removeCallbacksAndMessages(null)
        shadeLockArmed = false
        updateShadeLockOverlay(false)
        if (activeService === this) activeService = null
        com.agon.app.services.KeepAliveScheduler.schedule(applicationContext)
        com.agon.app.services.KeepAliveScheduler.ensureProtectionRunning(applicationContext)
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Restores the exact block screen that [com.agon.app.services.CallOverlayGate] suppressed for
     * a phone call. Called at IDLE (0ms path from the manifest receiver). Deterministic guard:
     * the restore fires only when the blocked subject is still in the foreground — for app blocks
     * that is the blocked package itself, for website blocks any active browser. Otherwise the
     * snapshot is dropped (the user legitimately navigated away during the call).
     */
    internal fun restoreSuppressedOverlay(suppressed: com.agon.app.services.CallOverlayGate.SuppressedOverlay) {
        val foreground = resolveForegroundPackage()
        val subjectVisible = if (suppressed.kindApp) {
            foreground == suppressed.packageName
        } else {
            foreground != null && isBrowser(foreground)
        }
        if (!subjectVisible) return
        // Bypass the block dedupe window — this is a restoration of the SAME, still-valid block,
        // not a fresh detection, so neither the log nor the stats are touched again.
        runCatching {
            val intent = if (suppressed.kindApp) {
                Intent(this, BlockedAppActivity::class.java)
                    .putExtra(BlockedAppActivity.EXTRA_PACKAGE, suppressed.packageName)
                    .putExtra(BlockedAppActivity.EXTRA_TIME_LIMIT, suppressed.timeLimit)
                    .putExtra(BlockedAppActivity.EXTRA_QUARANTINE, suppressed.quarantine)
                    .apply { suppressed.scheduleName?.let { putExtra(BlockedAppActivity.EXTRA_SCHEDULE_NAME, it) } }
            } else {
                Intent(this, BlockedWebsiteActivity::class.java)
                    .putExtra(BlockedWebsiteActivity.EXTRA_DOMAIN, suppressed.domain)
                    .putExtra(BlockedWebsiteActivity.EXTRA_REASON, suppressed.reason)
                    .putExtra(BlockedWebsiteActivity.EXTRA_CATEGORY, suppressed.category)
                    .putExtra(BlockedWebsiteActivity.EXTRA_CONFIDENCE, suppressed.confidence)
                    .putExtra(BlockedWebsiteActivity.EXTRA_TIME, suppressed.time)
            }
            startActivity(
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                ),
            )
        }
    }

    companion object {
        /**
         * The live service instance (null while unbound). Used by [com.agon.app.services.CallOverlayGate]
         * to restore a call-suspended block screen at the exact moment the phone returns to IDLE.
         */
        @Volatile
        var activeService: ShieldAccessibilityService? = null

        // Shade-lock geometry calibration (see isShadeExpandedNow): the panel is treated as
        // open only when a SystemUI window is almost full-width and clearly taller than the
        // always-visible status strip (≈3–5% of the display).
        private const val SHADE_MIN_WIDTH_FRACTION = 0.92f
        private const val SHADE_MIN_HEIGHT_FRACTION = 0.30f

        /** After a collapse, re-verify the panel twice within ~280ms so animated openers lose. */
        private const val SHADE_RECHECK_PASSES = 2
        private const val SHADE_RECHECK_INTERVAL_MS = 140L

        /** One delayed verification re-press after the search-close BACK (close animation tail). */
        private const val TELEGRAM_SEARCH_VERIFY_MS = 250L

        /** Minimum gap between Telegram-block toasts (rapid attempts stay calm). */
        private const val TELEGRAM_TOAST_THROTTLE_MS = 5_000L
        private const val SYSTEM_VPN_SETTINGS_TOAST_THROTTLE_MS = 5_000L
        private const val FULL_SETTINGS_LOCK_TOAST_THROTTLE_MS = 5_000L

        const val RULE_REFRESH_INTERVAL_MS = 60_000L
        // Keep browser re-check near-instant so title/URL/snippet blocks feel real-time.
        const val BROWSER_DEBOUNCE_MS = 40L
        const val TEXT_DEDUPE_MS = 180L
        const val FIELD_VERIFY_DELAY_MS = 40L
        const val TIME_LIMIT_CHECKPOINT_MS = 1_000L
        const val SHORT_VIDEO_RECHECK_INITIAL_DELAY_MS = 90L
        // Minimum gap between two Facebook feed-churn window scans (ms). Real navigations
        // (window state/added) bypass this, so entering Reels is still caught instantly.
        const val FACEBOOK_SCAN_MIN_INTERVAL_MS = 450L
        // Minimum gap between two non-Facebook short-video window scans on the content-change /
        // scroll stream. Real navigations bypass this, so entering Reels/Shorts is still instant.
        const val SHORT_VIDEO_SCAN_MIN_INTERVAL_MS = 300L
        // Minimum gap between two settings-guard full-tree scans on the content-change stream.
        const val SETTINGS_GUARD_MIN_INTERVAL_MS = 300L
        // Minimum gap between two Telegram search BACK presses.
        const val TELEGRAM_BACK_THROTTLE_MS = 150L
        // Follow-up probes after a search entry tap / navigation so a slow-mount search UI is
        // still closed (~2s total).
        // Minimum gap between two Telegram Web tree scans on content-change bursts.
        const val TELEGRAM_WEB_SCAN_MIN_INTERVAL_MS = 350L
        const val SHORT_VIDEO_RECHECK_INTERVAL_MS = 200L
        // Home-entry builds the Shorts player slower than the tab button (the tree needs up to
        // a few seconds on budget devices and slow networks), so verification now covers
        // roughly eight seconds after the last click/window transition.
        const val SHORT_VIDEO_RECHECK_LIMIT = 40
        // Facebook's node tree is huge, so its recheck scan is heavier. Use a slower cadence
        // and fewer attempts (about 2.5s total) to keep the main thread responsive; the
        // on-navigation scan already catches most Reels entries immediately.
        const val FACEBOOK_RECHECK_INTERVAL_MS = 500L
        const val FACEBOOK_RECHECK_LIMIT = 5
        // Resume of App Info / Accessibility can land before the view tree is ready; re-probe
        // for ~1s so PIN still appears without any user scroll/tap.
        const val SETTINGS_PROTECTION_RECHECK_INITIAL_MS = 60L
        const val SETTINGS_PROTECTION_RECHECK_INTERVAL_MS = 120L
        const val SETTINGS_PROTECTION_RECHECK_LIMIT = 10
        // Backoff between re-attempts of a dropped PIN challenge launch. ROMs that block the
        // first background activity launch (Samsung One UI and similar strict BAL
        // enforcement) usually accept a later one once the process is warm.
        val CHALLENGE_RETRY_DELAYS_MS = longArrayOf(1_200L, 2_600L, 4_500L)
        // Minimum gap between two full challenge dispatches (HOME + launch). The guard
        // re-challenges on every event while the challenge is unconfirmed, so the dispatch
        // itself is throttled to one per unconfirmed window.
        val CHALLENGE_DISPATCH_MIN_INTERVAL_MS = ProtectionTiming.CHALLENGE_UNCONFIRMED_WINDOW_MS
        // Minimum gap between two actual challenge launches (duplicate CHALLENGE decisions
        // from the high-frequency event stream must not spam startActivity).
        const val CHALLENGE_LAUNCH_MIN_INTERVAL_MS = 1_000L
        const val BLOCK_DEDUPE_MS = 900L

        /** Minimum gap between window-content verdict scans on Telegram (CPU guard). */
        const val TELEGRAM_CONTENT_CHECK_MIN_MS = 350L
        const val MAX_FIELD_LENGTH = 4_000
        const val MAX_TEXT_LENGTH = 12_000
        const val MAX_TREE_DEPTH = 14

        /** Browser/WebView accessibility trees nest much deeper than native app screens. */
        const val MAX_WEB_TREE_DEPTH = 28

        /** Hard cap on nodes visited per text scan; bounds main-thread work to avoid ANRs. */
        const val MAX_TEXT_NODES = 2_000

        /** URL bars may now nest deeply (tablet omnibox in AddressBar/UrlBar/Omnibox wrappers);
         * the new resolver is breadth-first but still visit-budgeted so a huge DOM never stalls. */
        const val MAX_URL_BAR_NODES = 900

        /** Breadth cap per visited node while walking browser chrome (each container fans out). */
        const val MAX_URL_BAR_CHILDREN = 24

        /** View-id tokens used by browser omnibox/bar containers incl. tablet/DeX hierarchies. */
        private val URL_BAR_ID_TOKENS = listOf(
            "url", "address", "search_box", "search_bar", "location_bar", "omnibox",
            "omnibox_text_field", "url_field", "url_bar", "urlbar", "address_bar",
            "browser_toolbar", "toolbar_edit", "toolbar_url", "top_toolbar", "search_plate",
            "search_url", "search_edit_frame", "omnibar", "url_input",
        )

        /** Same scan key within this window ⇒ the second scheduled pass is skipped (early-exit). */
        private const val BROWSER_SCAN_DEDUPE_NS = 1_500L * 1_000_000L

        /** Follow-up inspection delay letting a progressively rendered page settle. */
        const val BROWSER_RESCAN_MS = 650L
        private val URL_IN_TEXT = Regex(
            """(?i)\b((?:https?://)?(?:www\.)?[a-z0-9][a-z0-9\-.]{1,251}\.[a-z]{2,24}(?:/[^\s]*)?)""",
        )
    }
}

/** Recycling is a no-op from API 33 onward; kept for older devices to avoid node leaks. */
private fun AccessibilityNodeInfo.recycleCompat() {
    if (android.os.Build.VERSION.SDK_INT < 33) {
        @Suppress("DEPRECATION")
        runCatching { recycle() }
    }
}
