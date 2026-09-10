package com.agon.app.settingsprotection.domain

import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.security.PrivateDnsGuard
import com.agon.app.settingsprotection.domain.oem.OemSettingsRouter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recognises the system "App info" family of screens across OEM skins.
 *
 * Detection is deliberately layered so that a single OEM specific string is never a hard
 * requirement:
 *  1. the event must originate from a settings / package-installer / permission-manager package,
 *  2. a *structural* signal must be present (activity class or resource id that only exists on an
 *     application-detail surface),
 *  3. the window must be showing *this* application (label or package id),
 *  4. localized action words add confidence for skins with fully custom view ids.
 */
@Singleton
class SettingsScreenDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val oemRouter: OemSettingsRouter,
) {

    private val ownPackage: String = context.packageName

    private val ownLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        runCatching {
            val info = context.packageManager.getApplicationInfo(ownPackage, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault("BlockX LaAbrah").trim()
    }

    /**
     * This app's accessibility-service description as declared in resources. Every OEM skin
     * renders it verbatim inside the service-detail page, in every device language — a stable,
     * translation-independent identity signal for the accessibility fail-closed path.
     */
    private val ownServiceDescriptionNormalized: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val raw = runCatching {
            context.getString(com.agon.app.R.string.accessibility_description)
        }.getOrDefault("")
        normalizeForIdentity(raw.lowercase())
    }

    private fun isWirelessPairingSurface(packageName: String, activityClass: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg == "com.android.bluetooth" || pkg.endsWith(".bluetooth") ||
            pkg == "com.android.nfc" || pkg.endsWith(".nfc") ||
            pkg.contains("hce") || pkg.contains("beaming")
        ) return true
        val klass = activityClass.lowercase()
        return WIRELESS_CLASS_MARKERS.any { klass.contains(it) }
    }

    /** Samsung One UI base navigation container shells (never tamper targets by themselves). */
    private fun isOneUiBaseNavContainer(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return ONE_UI_BASE_CONTAINERS.any { lowered.contains(it) }
    }

    /**
     * Samsung One UI whitelist-first gate (Galaxy M32 5G / Android 13 / SDK 33 field report).
     *
     * On Samsung One UI, `com.android.settings` uses fragmented view roots, sub-activity
     * proxies (`SecSettings2Activity`, `SecMainSettingsActivity`) and embedded fragments inside
     * `SubSettings`. The detection logic must NOT aggressively intercept generic settings
     * hierarchy events — only surfaces that explicitly match sensitive app-management /
     * uninstallation / accessibility / device-admin classes may be blocked. Every general
     * settings category (WiFi, Sound, Display, Lock Screen, Accounts, Device Care, etc.)
     * passes through immediately.
     *
     * Evaluation order:
     *  1. Package must be `com.android.settings` or Samsung settings variant.
     *  2. Activity class must NOT match any high-risk target (app uninstall/info,
     *     accessibility management, device admin, force stop). If it does, return false
     *     so the surface continues into the normal detection pipeline.
     *  3. Activity class must match a known benign One UI container or category marker.
     *  4. If neither container nor category matches, return false (fall through to normal flow).
     */
    private fun isSamsungOneUiBenignSettingsSurface(
        packageName: String,
        activityClass: String,
    ): Boolean {
        val pkg = packageName.lowercase()
        if (pkg != "com.android.settings" && pkg != "com.samsung.android.settings") return false
        if (activityClass.isBlank()) return false

        val lowered = activityClass.lowercase()

        // High-risk targets that must NEVER be whitelisted — these still need protection:
        // 1. Uninstallation / Application Info surfaces
        if (SAMSUNG_HIGH_RISK_CLASSES.any { lowered.contains(it) }) return false
        // 2. Device Admin surfaces
        if (DEVICE_ADMIN_CLASSES.any { lowered.contains(it) }) return false
        // 3. Accessibility management surfaces
        if (ACCESSIBILITY_CLASSES.any { lowered.contains(it) }) return false
        // 4. Force stop / uninstall confirmation dialogs
        if (lowered.contains("forcestop") || lowered.contains("force_stop") ||
            lowered.contains("uninstall")
        ) return false
        // 5. Manage-apps / downloaded-apps list surfaces (route to uninstall/force-stop)
        if (MANAGE_APPS_CLASSES.any { lowered.contains(it) }) return false
        // 6. Battery / autostart / background-restriction surfaces for our app
        if (BATTERY_PER_APP_SURFACE_CLASSES.any { lowered.contains(it) }) return false
        if (BATTERY_SYSTEM_SURFACE_CLASSES.any { lowered.contains(it) }) return false
        if (AUTOSTART_TOGGLE_SURFACE_CLASSES.any { lowered.contains(it) }) return false

        // If the class is a known Samsung One UI container (SecSettings2, SubSettings, etc.)
        // that didn't match any high-risk target above, it is benign.
        if (ONE_UI_BASE_CONTAINERS.any { lowered.contains(it) }) {
            Log.d("SettingsScreenDetector", "Samsung One UI whitelist: bypassing detection for container class=$lowered pkg=$packageName")
            return true
        }

        // Samsung One UI category pages hosted in generic containers — always whitelisted.
        if (SAMSUNG_BENIGN_CATEGORY_MARKERS.any { lowered.contains(it) }) {
            Log.d("SettingsScreenDetector", "Samsung One UI whitelist: bypassing detection for category class=$lowered pkg=$packageName")
            return true
        }

        // Not a Samsung One UI surface we can classify as benign — fall through to normal flow.
        return false
    }

    /** Launchers/home screens are never a tamper surface for the settings guard. */
    private fun isLauncherPackage(packageName: String): Boolean {
        val lowered = packageName.lowercase()
        return lowered in LAUNCHER_PACKAGES ||
            lowered.contains("launcher") ||
            lowered.endsWith(".home")
    }

    /** Returns a detection when the visible window is a protected system surface for this app. */
    fun detect(packageName: String, className: String?, root: AccessibilityNodeInfo?): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (isLauncherPackage(packageName)) return null

        // The system VPN-consent dialog (com.android.vpndialogs.ConfirmDialog) must ALWAYS be
        // free: gating it would let the shield fail to arm its own tunnel (reported conflict).
        if (packageName == "com.android.vpndialogs") return null

        val activityClass = className?.trim().orEmpty()

        // Barebone wireless hardware workflows (Bluetooth pairing + NFC services) are plain
        // system UI, never a tamper surface — intercepting them broke real-world pairing on
        // multiple OEM stacks (field report covered by this audit).
        if (isWirelessPairingSurface(packageName, activityClass)) return null

        // OEM strategy layer (Samsung One UI whitelist, Xiaomi security-center early gates, …).
        // Keeps vendor quirks out of the core pipeline while preserving identical behaviour.
        if (oemRouter.isBenignSettingsSurface(packageName, activityClass)) return null
        oemRouter.detectEarly(packageName, activityClass, root)?.let { return it }

        // Samsung One UI whitelist-first gate retained as a secondary path for parity with
        // legacy constants still defined in this file (router already covers the common case).
        if (isSamsungOneUiBenignSettingsSurface(packageName, activityClass)) return null

        // Instant render block: the Ultra / Super / Extreme power-saving tile label must not
        // even be DISPLAYED on a guarded surface while protection is armed — wording presence
        // alone completes the challenge at 0ms, before any finger can land on the tile. This
        // covers the main notification-shade panel, the tile-edit customizer and any other
        // host that renders the toggle, on every OEM and in every language. Runs before the
        // isSystemSurface early-return below so SystemUI panel windows are included. The
        // vocabulary carries an ultra/super/extreme/max qualifier on every entry, so ordinary
        // "Battery Saver" wording can never trip this gate. The notification shade / quick panel
        // (SystemUI) is deliberately excluded from all Ultra-tile enforcement: the tile there is
        // free by product decision, and protection stays at full strength inside Settings hosts.
        if (isSystemSurface(packageName)) {
            if (pageRendersUltraPowerSaving(root)) {
                return ProtectedScreenDetection(
                    type = ProtectedScreenType.BATTERY_RESTRICTION,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 97,
                )
            }
        }

        // Safe Mode in the power menu is hosted by System UI (not Settings) and must be gated
        // before the generic settings-surface filter, which would otherwise ignore it.
        if (isSafeModeSurface(packageName, activityClass, root)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.SAFE_MODE,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 95,
            )
        }

        // NOTE: Ordinary Power off / Restart is intentionally NOT gated. Only Safe Mode is
        // protected (above), because Safe Mode is the route that disables third-party protection.
        // A normal shutdown/restart is harmless and must not require the PIN.

        // "Delete system update" is often shown by DocumentsUI / Files, not Settings.
        if (isSystemUpdateDeleteSurface(packageName, activityClass, root)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.SYSTEM_UPDATE_DELETE,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 95,
            )
        }

        // Developer options & USB debugging protection is DISABLED by product decision: users
        // may now open Developer Options and enable USB debugging freely. (The detector helpers
        // stay in place in case the policy is re-enabled later; nothing else is touched.)

        val isStoreSurface = !isSystemSurface(packageName) && packageName in APP_STORE_PACKAGES
        if (!isSystemSurface(packageName) && !isStoreSurface) return null

        val scan = scan(root, activityClass)

        // App stores whose flows can remove an app WITHOUT going through the guarded system
        // uninstall confirmation (Galaxy Store / AppGallery / GetApps / HeyTap / Vivo uninstall
        // silently; Play Store only when the device-admin lock happens to be off). Only THIS
        // app's own store page with a visible Uninstall action is gated — browsing the store
        // or other apps' pages is never disturbed, so the forced in-app update flow (Play)
        // stays intact as well.
        if (isStoreSurface) {
            return if (scan.matchesOwnApp && scan.uninstallSignal) {
                ProtectedScreenDetection(
                    type = ProtectedScreenType.UNINSTALL,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 94,
                )
            } else {
                null
            }
        }

        // THIS app's own battery / background-restriction page. Evaluated BEFORE the generic
        // battery class/text exclusions: those keep system battery screens and other apps'
        // battery pages PIN-free, while this single surface follows its own dynamic two-phase
        // rule — freely reachable until background work ("No restrictions") is enabled, then
        // instantly PIN-gated so the setting cannot be rolled back.
        if (isOwnAppBatterySurface(activityClass, scan)) {
            return if (isOwnAppBatteryUnrestricted(scan)) {
                ProtectedScreenDetection(
                    type = ProtectedScreenType.BATTERY_RESTRICTION,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 95,
                )
            } else {
                null
            }
        }

        // Security-center / app-manager surfaces (Xiaomi Security Center, Honor/Huawei
        // SystemManager, ColorOS Safecenter, Vivo iQOO Secure): the ROOT dashboard stays open —
        // harmless tools (cleaner, security scan, data usage …) never raise a PIN. Every
        // sensitive sub-section inside these hosts (manage-apps list, autostart page,
        // permissions) is gated on render, driven purely by activity/fragment class names so it
        // is identical in every device language. App-details surfaces inside the hosts are
        // protected ONLY for THIS app (identity) — third-party App-info always passes free.
        // The generic Apps list inside com.android.settings is untouched by this gate.
        if (isSecurityHostSensitiveSurface(packageName, activityClass, scan)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.AUTOSTART_ENTRY,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 93,
            )
        }

        // Speed-trick close-out (all OEMs): ANY surface that renders OUR own background /
        // autostart / auto-launch / power-exemption toggle is gated AT window render — the PIN
        // activity lands at 0ms and covers every pixel before a touch can reach the switch.
        // Settings → Apps lists and dashboards stay 100% free (verified: no class token here
        // matches "manageapplications/…list" pages, and the structural signal requires OUR
        // identity + a live switch).
        if (isSystemSurface(packageName) && isAutostartToggleSurface(activityClass, scan)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.AUTOSTART_ENTRY,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 94,
            )
        }

        // Zero-click autostart rule (work-order): ANY system surface already RENDERING the
        // autostart section's wording or view-ids — search results, settings pages, security
        // hosts, every OEM, every language — is ejected with the PIN overlay at 0ms. Fires only
        // while protection is armed (the guard early-returns otherwise), so bootstrapping stays
        // free, and the section's own row-direction logic still governs after a valid unlock.
        if (isSystemSurface(packageName) && scan.autostartEntrySignal) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.AUTOSTART_ENTRY,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 94,
            )
        }

        // Battery surfaces — split deliberately:
        //  • System-wide battery pages (saver, device care, optimization list, …) are always
        //    lock-points once protection is armed: a toggle there quietly throttles the
        //    watchers and the VPN.
        //  • PER-APP battery detail pages stay free for every third-party app; only THIS app's
        //    page is protected (identity), and even ours keeps its Phase-1 corridor until
        //    "No restrictions" is set — so first-time setup can never soft-lock.
        // Structural class matching only: the battery quick-tile on security-center dashboards
        // (e.g. a "Battery saver" card on Xiaomi's main page) must never trip the gate.
        if (isPerAppBatterySurface(activityClass)) {
            if (scan.matchesOwnApp && !ownBatteryPhaseOneFree(activityClass, scan)) {
                return ProtectedScreenDetection(
                    type = ProtectedScreenType.BATTERY_RESTRICTION,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 90,
                )
            }
        } else if (isSystemBatterySurface(activityClass)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.BATTERY_RESTRICTION,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 90,
            )
        }

        // Settings search pages across EVERY OEM (AOSP, MIUI/HyperOS, EMUI/MagicOS, One UI,
        // ColorOS, OriginOS): a NON-EMPTY user query whose results surface a sensitive
        // destination (autostart / accessibility / downloaded apps) is ejected on sight — the
        // service then presses BACK to leave the page and overlays the PIN, 0ms. Confirmation
        // is structural (editable search box in the tree OR a search-host window class), so it
        // is identical in every device language. An empty query is never gated.
        val onSearchSurface = run {
            val cls = activityClass.lowercase()
            scan.settingsSearchSignal || SEARCH_HOST_CLASSES.any { cls.contains(it) }
        }
        if (onSearchSurface && !scan.searchQueryText.isNullOrBlank()) {
            if (scan.autostartEntrySignal) {
                return ProtectedScreenDetection(
                    type = ProtectedScreenType.AUTOSTART_ENTRY,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 93,
                )
            }
            if (scan.searchSensitiveHit) {
                return ProtectedScreenDetection(
                    type = ProtectedScreenType.ACCESSIBILITY_SETTINGS,
                    settingsPackage = packageName,
                    screenClass = activityClass.ifBlank { packageName },
                    confidence = 90,
                )
            }
        }

        // App Info policy (targeted protection only) — evaluated BEFORE the apps-list gates
        // below. A third-party App Info / app-details page is ALWAYS free: no PIN and no
        // ejection, on every OEM and in every language. This early exit guarantees that a
        // shared list view-id (app_manager / apps_list …) rendered inside another app's details
        // page can never drag that page into the apps-list challenge. This app's own details
        // page is NOT released here (identity matches), so it continues downstream and is
        // PIN-gated with the 0ms challenge by the structural gate.
        if (isAppDetailSurface(activityClass, scan) && !scan.matchesOwnApp) {
            return null
        }

        if (isExcludedClass(activityClass)) return null
        // "All apps" / "Downloaded apps" section headings and the generic app-list container /
        // search-bar view ids used to veto the WHOLE installed-apps screen, which silently
        // left "Force stop" / "Uninstall" reachable without a PIN on the skins that show them.
        // The veto now yields when the screen:
        //   - actually shows destructive actions for this app (see [ScanResult.destructiveOwnApp]),
        //   - or is genuinely the installed-apps / downloaded-apps list ([installedAppsListSignal]),
        //     which is the route to this app's uninstall / force-stop controls. That list surface
        //     must stay guarded on every skin instead of being excluded.
        if (scan.listSurfaceExcluded && !scan.destructiveOwnApp && !scan.installedAppsListSignal) return null

        // Product change: the general apps list (Settings → Apps / Manage apps / Application
        // manager) IS a protected surface once protection is armed — class tokens plus the
        // structural list signals keep it identical across every OEM and every language.
        if (isGenericAppsListSurface(activityClass, scan) ||
            isDownloadedAppsListSurface(activityClass, scan)
        ) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.MANAGE_APPS,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 92,
            )
        }

        // MIUI / HyperOS / Funtouch / some OnePlus skins host the dedicated factory-reset screen
        // (and its erase-all-data confirmation) inside a generic container class (SubSettings,
        // Settings with a fragment id …) which carries no master-clear keyword. The screen is
        // Factory-reset protection is DISABLED by product decision (kept behind this comment):
        // the dedicated MasterClear/Erase-all-data surfaces and the wipe-row clicks are no
        // longer gated, in any language or on any OEM. Everything else is untouched.

        // Uninstall-related surfaces that never mention this app by name (the device admin
        // list, security settings, the manage-apps list) are matched on their activity class
        // alone, because there is no identity signal to require.
        // Permission-flow bypass (product decision): the "Draw over other apps" grant screen is
        // never PIN-gated — not even for THIS app. The protection stack does not rely on that
        // permission (block screens are activities), so granting or denying it cannot weaken
        // anything; kicking the user to the PIN during onboarding would just block set-up.
        // Structural class match only — identical on every OEM and in every device language.
        if (isOverlayGrantSurface(activityClass)) return null

        adminSurfaceType(packageName, activityClass, scan)?.let { type ->
            return ProtectedScreenDetection(
                type = type,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 90,
            )
        }

        // Accessibility settings: this is where the protection service would be switched off.
        // The generic accessibility list mentions every app, so we require this app's identity
        // (its label or package id must be visible) to avoid intercepting unrelated toggles.
        // Because the whole guard only runs once a PIN is configured, enabling the service for
        // the first time (no PIN yet) is never blocked.
        //
        // Many OEM skins (MIUI / HyperOS, ColorOS …) host the service-detail toggle inside a
        // generic container activity (e.g. com.android.settings.SubSettings), so the activity
        // class alone carries no "accessibility" marker. In that case we fall back to a content
        // signal (an accessibility view-id or localized wording) combined with this app's
        // identity, so the turn-off screen is still guarded on those devices.
        // OEM fallback: on Samsung SubSettings / MIUI containers the disable screen carries no
        // "accessibility" class or view-id marker at all, but it always renders a Switch next
        // to this app's identity. That structural combination is language-independent and
        // closes the turn-off route on those devices. Any other system screen showing a switch
        // next to this app (Usage access, Notification access …) is a tamper surface anyway.
        // Recents / task-switcher resume hole closed: some OEM builds deliver the content-root
        // view class (instead of the activity class) on WINDOW_STATE_CHANGED, so class matching
        // alone misses the resume. The immutable service description is the definitive identity
        // on every resumed state, every OEM, every language.
        val accessibilityStructuralSignal = scan.accessibilitySignal ||
            (scan.switchFound && scan.matchesOwnApp) ||
            scan.ownServiceDescriptionVisible
        if ((accessibilitySurface(activityClass) || accessibilityStructuralSignal) && scan.matchesOwnApp) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.ACCESSIBILITY_SETTINGS,
                settingsPackage = packageName,
                screenClass = activityClass.ifBlank { packageName },
                confidence = 90,
            )
        }

        // App Info / application-details for ANY app other than ours must never challenge.
        // PIN is reserved exclusively for THIS app's App Info (uninstall / force-stop / clear-data).
        // Placed after factory-reset / device-admin / accessibility so those surfaces stay intact.
        if (isAppDetailSurface(activityClass, scan) && !scan.matchesOwnApp) {
            return null
        }

        // Samsung One UI (Android 13/SDK 33, M32 5G field report): the base navigation hosts
        // plain system pages (Wi-Fi, Display, Sound...) inside SecSettings2Activity /
        // SecMainSettingsActivity. They are containers, not surfaces — never a tamper target.
        // Protection stays inviolate: anything with our identity or a destructive signal keeps
        // its PIN below; Device Admin / Accessibility / VPN pages hit their own gates earlier.
        if (isOneUiBaseNavContainer(activityClass) && !scan.destructiveOwnApp && !scan.matchesOwnApp) {
            return null
        }

        // App Info / destructive controls: PIN only when THIS application is the subject.
        // Other apps' App Info pages are rejected above via [isAppDetailSurface].
        val structural = classSignal(activityClass) || scan.structuralViewId || scan.destructiveOwnApp
        val identity = scan.matchesOwnApp
        if (!structural || !identity) return null

        var confidence = 55
        if (classSignal(activityClass)) confidence += 15
        if (scan.structuralViewId) confidence += 10
        if (scan.packageIdVisible) confidence += 15
        if (scan.destructiveOwnApp) confidence += 10
        confidence += (scan.actionWords * 5).coerceAtMost(10)

        return ProtectedScreenDetection(
            type = resolveType(activityClass, scan),
            settingsPackage = packageName,
            screenClass = activityClass.ifBlank { packageName },
            confidence = confidence.coerceAtMost(100),
        )
    }

    /** A per-app battery usage/detail page (App battery usage …) — never class content. */
    private fun isPerAppBatterySurface(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return BATTERY_PER_APP_SURFACE_CLASSES.any { lowered.contains(it) }
    }

    /** A system-wide battery-management surface (by class) — gated once protection is armed. */
    private fun isSystemBatterySurface(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return BATTERY_SYSTEM_SURFACE_CLASSES.any { lowered.contains(it) }
    }

    /** The own-battery Phase-1 corridor: free until background work is enabled. */
    private fun ownBatteryPhaseOneFree(activityClass: String, scan: ScanResult): Boolean =
        isOwnAppBatterySurface(activityClass, scan) && !isOwnAppBatteryUnrestricted(scan)

    /**
     * True when the visible screen is the system's "Display over other apps" grant page
     * (AOSP `Settings$AppDrawOverlaySettingsActivity` and every OEM mirroring class — Honor/
     * MagicOS, EMUI, MIUI/HyperOS, ColorOS/RealmeUI/OxygenOS, Funtouch/OriginOS, Pixel).
     * Class-token based, so it is identical in every device language and locale.
     */
    private fun isOverlayGrantSurface(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return OVERLAY_PERMISSION_CLASSES.any { lowered.contains(it) }
    }

    /**
     * True when the current window IS a background-execution / autostart control surface on
     * which our app's toggle exists — covers Xiaomi/HyperOS `AutoStartManagementActivity`,
     * Honor/Huawei "App launch" managers, Oppo/ColorOS auto-launch pages, Vivo background-power
     * managers, Samsung sleeping apps, Pixel/AOSP per-app background pages, in every language.
     * Two independent carriers: activity-class tokens, or tree semantics (autostart markers +
     * a switch + our identity). Everything else stays untouched.
     */
    private fun isAutostartToggleSurface(activityClass: String, scan: ScanResult): Boolean {
        if (activityClass.isNotBlank()) {
            val lowered = activityClass.lowercase()
            if (AUTOSTART_TOGGLE_SURFACE_CLASSES.any { lowered.contains(it) }) return true
        }
        return scan.autostartListSignal && scan.switchFound && scan.matchesOwnApp
    }

    /**
     * True when the visible window is THIS app's own battery / background-restriction detail
     * page. Structural signals (activity class, page view ids) are primary; localized titles only
     * add coverage. This app's identity must be visible, so the general system battery screens
     * and other applications' battery pages are never matched.
     */
    private fun isOwnAppBatterySurface(activityClass: String, scan: ScanResult): Boolean {
        if (!scan.matchesOwnApp) return false
        if (activityClass.isNotBlank()) {
            val lowered = activityClass.lowercase()
            if (OWN_BATTERY_CLASSES.any { lowered.contains(it) }) return true
        }
        return scan.batteryPageSignal
    }

    /**
     * True once background work is enabled for this app. Primary source: the system state
     * [PowerManager.isIgnoringBatteryOptimizations] — identical in every language and every
     * OEM skin. Secondary: the page itself shows the unrestricted option in a checked/selected
     * state, covering skins that mirror the choice without updating the system whitelist.
     */
    private fun isOwnAppBatteryUnrestricted(scan: ScanResult): Boolean {
        val systemSignal = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(ownPackage) == true
        }.getOrDefault(false)
        return systemSignal || scan.unrestrictedChecked
    }

    /**
     * Normalized (bidi-stripped, lowercased) application label, cached once for the row
     * identity checks inside the autostart list.
     */
    private val normalizedOwnLabel: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        normalizeForIdentity(ownLabel.lowercase())
    }

    /**
     * Last observed ON/OFF state of every switch on the autostart surface, keyed by its row
     * label (see [rowKeyForSwitch]). Refreshed only by surface scans, never at click time, so
     * skins that toggle the widget before delivering the click event cannot invert the
     * direction of the tap.
     */
    @Volatile
    private var autostartSwitchStates: Map<String, Boolean> = emptyMap()

    /**
     * General multi-app "Background autostart / Startup manager / App launch" list (MIUI /
     * HyperOS permcenter, Huawei-Honor systemmanager, ColorOS …). The list itself is NEVER
     * gated, and only a click on the toggle of THIS app while it is ON — i.e. turning
     * background autostart OFF — raises the protection challenge. Turning it ON and every
     * other app's toggle pass through untouched.
     *
     * The tap direction is decided from the switch state REMEMBERED from the last completed
     * window scan, never from the live node at click time — otherwise the already-flipped
     * state would be read and an OFF→ON tap would be mistaken for a disable attempt.
     */
    fun detectAutostartDisableClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null
        // Ignore the synthetic click emitted by our own programmatic revert (state enforcement).
        if (System.currentTimeMillis() - lastAutostartRevertAt < AUTOSTART_REVERT_WINDOW_MS) return null
        val scan = scan(root, className.orEmpty(), refreshAutostart = false)
        if (!isAutostartListSurface(className.orEmpty(), scan)) return null

        // Container gate: the SMALLEST ancestor of the click that carries this app's identity
        // AND a switch (our row on the list / our option dialog on Honor). Bigger containers —
        // up to the whole-list container, which also shows this app's label — are rejected by
        // the size bound, so another app's row never matches.
        var row: AccessibilityNodeInfo? = null
        var current = source
        var depth = 0
        while (current != null && depth < 5 && row == null) {
            if (current.childCount in 1..10 &&
                subtreeHasOwnIdentity(current) &&
                findSwitchNode(current) != null
            ) {
                row = current
            }
            current = current.parent
            depth++
        }
        if (row == null) return null

        // The switch that was actually tapped: the source itself when it is switch-like,
        // otherwise the switch nearest to the tap inside its own row.
        val switchNode = if (isSwitchLike(source)) source else findNearestSwitch(source)
        if (switchNode == null) return null

        // Language-independent direction detection. The remembered state (snapshot taken from
        // the last full surface scan, never at click time) is authoritative because some skins
        // toggle the widget BEFORE the accessibility click event is delivered.
        val remembered = autostartSwitchStates[rowKeyForSwitch(switchNode)]
        when (remembered) {
            false -> return null // Definitely OFF → this tap turns it ON: always free.
            true -> Unit // Definitely ON → tap turns it OFF: challenged below.
            else -> {
                // Missing history (e.g. first observation): fail CLOSED — an ON-looking toggle
                // being tapped is a disable attempt, never assume the user means "turn on".
                if (!switchNode.isChecked) return null
            }
        }
        return ProtectedScreenDetection(
            type = ProtectedScreenType.AUTOSTART_TOGGLE,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 97,
        )
    }

    /**
     * Fail-closed protection of THIS app's own ON/OFF switch (accessibility service detail,
     * usage-access, draw-over-apps …). A tap on the toggle is challenged the instant it lands —
     * before a confirm dialog can even display — using only structural signals (widget class +
     * immutable identity), so it closes the reported Honor/MagicOS bypass on which the page
     * came back without any language markers at all. Permission-toggle pages for other apps
     * never carry this app's identity, so they are untouched.
     */
    fun detectOwnServiceToggleClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null
        // Ignore the synthetic click emitted by our own programmatic revert (state enforcement).
        if (System.currentTimeMillis() - lastToggleRevertAt < TOGGLE_REVERT_WINDOW_MS) return null
        if (className.orEmpty().lowercase().let { it.contains("search") }) return null
        val scan = scan(root, className.orEmpty(), refreshAutostart = false)
        if (!scan.matchesOwnApp || !scan.switchFound) return null

        // Resolve the tapped switch (widget itself, or the one inside the tapped row).
        val switchNode = if (isSwitchLike(source)) source else findNearestSwitch(source)
        if (switchNode == null) return null

        // Fail closed: a checked toggle means THIS protection service is ON, so the tap tries
        // to turn it OFF. The confirm step never runs before the challenge is raised.
        if (!switchNode.isChecked) return null
        return ProtectedScreenDetection(
            type = ProtectedScreenType.ACCESSIBILITY_SETTINGS,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 99,
        )
    }

    /**
     * Short window after a programmatic revert of our autostart switch. The revert itself emits
     * a click event on the switch; without this guard the same synthetic click would look like
     * a fresh user disable attempt and the protection would loop on itself.
     */
    @Volatile
    private var lastAutostartRevertAt = 0L

    /** Same self-click suppression window for the generic own-toggle revert. */
    @Volatile
    private var lastToggleRevertAt = 0L

    /**
     * State enforcement for the speed trick. When a sub-second tap flips this app's autostart
     * switch to OFF before the challenge overlay is fully in front, the service calls this to
     * flip it BACK in the same frame (programmatic ACTION_CLICK on the live Switch node,
     * language-independent by construction) before HOME is pressed and the PIN appears.
     */
    fun revertOwnAutostartSwitch(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val identityNode = findOwnIdentityNode(root, 0, Counter()) ?: return false

        // Climb from our identity label to the smallest row container holding a switch.
        var container: AccessibilityNodeInfo? = identityNode
        var depth = 0
        var target: AccessibilityNodeInfo? = null
        while (container != null && depth < 6 && target == null) {
            if (container.childCount in 1..10) {
                target = findSwitchNode(container)
            }
            container = container.parent
            depth++
        }
        val switchNode = target ?: return false

        if (switchNode.isChecked) {
            // Row snapshots are refreshed: remember the ON state so the next tap is challenged.
            autostartSwitchStates = autostartSwitchStates + (rowKeyForSwitch(switchNode) to true)
            return true
        }
        val toggled = runCatching {
            switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
        if (toggled) {
            autostartSwitchStates = autostartSwitchStates + (rowKeyForSwitch(switchNode) to true)
            lastAutostartRevertAt = System.currentTimeMillis()
        }
        return toggled
    }

    /**
     * Generic same-frame auto re-enable for THIS app's own toggle on any surface (accessibility
     * service detail, usage access, draw-over-apps…). The row is found purely structurally
     * (smallest container holding our identity + a switch), so it works on Honor/MagicOS,
     * Xiaomi/HyperOS, Samsung One UI and ColorOS in every device language.
     */
    fun revertOwnToggleIfOff(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val identityNode = findOwnIdentityNode(root, 0, Counter()) ?: return false
        var container: AccessibilityNodeInfo? = identityNode
        var depth = 0
        var target: AccessibilityNodeInfo? = null
        while (container != null && depth < 6 && target == null) {
            if (container.childCount in 1..10) {
                target = findSwitchNode(container)
            }
            container = container.parent
            depth++
        }
        val switchNode = target ?: return false
        if (switchNode.isChecked) return true
        val toggled = runCatching {
            switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
        if (toggled) lastToggleRevertAt = System.currentTimeMillis()
        return toggled
    }

    /**
     * Presses the affirmative button on OUR own device-admin ADD/activation page
     * (DeviceAdminAdd and OEM variants) the same frame a challenge fires. Deactivate/remove
     * confirmations are deliberately NEVER clicked: the action is restricted to ADD-admin
     * surfaces and only when this app's identity is present, so no other flows are touched.
     */
    fun confirmAdminActivation(root: AccessibilityNodeInfo?, screenClass: String?): Boolean {
        if (root == null) return false
        val cls = screenClass.orEmpty().lowercase()
        if (!ADMIN_ADD_CLASSES.any { cls.contains(it) }) return false
        if (findOwnIdentityNode(root, 0, Counter()) == null) return false

        val buttons = mutableListOf<Pair<Int, AccessibilityNodeInfo>>()
        collectButtons(root, 0, Counter(), buttons)
        if (buttons.isEmpty()) return false
        // The affirmative action (Activate / Turn on) is the bottom-most button on these pages
        // on every supported OEM skin; structural placement — no localized label needed.
        val affirmative = buttons.maxByOrNull { it.first }?.second ?: return false
        return runCatching {
            affirmative.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }.getOrDefault(false)
    }

    /** Collects clickable buttons (bottom coordinate, node) inside a bounded walk. */
    private fun collectButtons(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
        out: MutableList<Pair<Int, AccessibilityNodeInfo>>,
    ) {
        if (node == null || depth > 12 || counter.visited > 200) return
        counter.visited++
        val cls = node.className?.toString()?.lowercase().orEmpty()
        if (cls.contains("button") && node.isClickable && node.isEnabled) {
            val rect = android.graphics.Rect()
            runCatching { node.getBoundsInScreen(rect) }
            out.add(rect.bottom to node)
        }
        val count = minOf(node.childCount, 10)
        for (index in 0 until count) {
            collectButtons(node.getChild(index), depth + 1, counter, out)
        }
    }

    /** First text/host node rendering this app's label or package id inside [node]'s tree. */
    private fun findOwnIdentityNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
    ): AccessibilityNodeInfo? {
        if (node == null || depth > 16 || counter.visited > 350) return null
        counter.visited++
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.isBlank() || raw.length > MAX_TEXT_LENGTH) continue
            val normalized = normalizeForIdentity(raw.lowercase())
            if (normalized.contains(ownPackage) ||
                (normalizedOwnLabel.length >= 4 && normalized.contains(normalizedOwnLabel))
            ) {
                return node
            }
        }
        val count = minOf(node.childCount, 16)
        for (index in 0 until count) {
            val child = node.getChild(index) ?: continue
            val found = findOwnIdentityNode(child, depth + 1, counter)
            if (found != null) return found
        }
        return null
    }

    /**
     * Zero-touch capture of the "App Manager / Manage apps" ENTRY on security-center dashboards
     * (Xiaomi/MIUI-HyperOS SecurityCenter, Honor/Huawei SystemManager, ColorOS/Oppo/OnePlus
     * Safecenter, Vivo iQOO Secure …). The challenge is raised on the tap itself (0ms), before
     * the App Manager section can open — from there our app's own page would be one tap away.
     *
     * Matching is scoped to the tapped row / click payload (label, contentDescription, event
     * text and a small ancestor chain), never page-wide, so every other dashboard tile
     * (cleaner, antivirus, battery, data usage …) and the host's home screen stay 100% free.
     * Fires only inside the security hosts. Language coverage: EN/AR/KU/FA/TR/ZH/JA/KO via
     * [SECURITY_APP_MANAGER_ENTRY_WORDS].
     */
    fun detectSecurityAppManagerEntryClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isAutostartEntryHost(packageName)) return null

        // Already inside the App Manager section: the window-level gate owns it from here.
        val loweredClass = className.orEmpty().lowercase()
        if (SECURITY_HOST_APP_MANAGER_CLASSES.any { loweredClass.contains(it) }) return null

        var node = source
        var depth = 0
        var textHit = false
        while (node != null && depth < 6) {
            val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            if (blobs.any { value ->
                    val lowered = value.lowercase().trim()
                    SECURITY_APP_MANAGER_ENTRY_WORDS.any { lowered == it || lowered.contains(it) }
                }
            ) {
                textHit = true
                break
            }
            node = node.parent
            depth++
        }
        val eventHit = eventTexts.any { raw ->
            val lowered = raw.toString().lowercase().trim()
            SECURITY_APP_MANAGER_ENTRY_WORDS.any { lowered == it || lowered.contains(it) }
        }
        if (!textHit && !eventHit) return null

        return ProtectedScreenDetection(
            type = ProtectedScreenType.MANAGE_APPS,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 92,
        )
    }

    /**
     * Pre-click interception of the "Background autostart" entry (quick-card / row) on Security
     * Center dashboards (MIUI / HyperOS SecurityCenter, Huawei/Honor SystemManager, ColorOS
     * Safecenter, Vivo iQOO Secure …). Opening that section is gated BEFORE the target activity
     * launches: the overlay is raised on the tap itself (0ms launch), so no locale-dependent
     * wording is ever in the critical path — entry detection needs only the hosting package,
     * structural view-ids or the immutable autostart tokens carried by the event payload.
     *
     * It never fires while the user is ALREADY inside the autostart list surface (that page is
     * governed by the toggle-direction detector so enabling stays free), and never outside the
     * security hosts.
     */
    fun detectAutostartEntryClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isAutostartEntryHost(packageName)) return null
        val scan = scan(root, className.orEmpty(), refreshAutostart = false)
        if (looksInsideAutostartList(className.orEmpty(), scan)) return null

        var node = source
        var depth = 0
        var idHit = false
        var textHit = false
        while (node != null && depth < 6) {
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (id.isNotEmpty() && AUTOSTART_ENTRY_VIEW_IDS.any { id.contains(it) }) {
                idHit = true
                break
            }
            val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            if (blobs.any { value ->
                    val lowered = value.lowercase().trim()
                    AUTOSTART_LIST_TITLE_WORDS.any { lowered == it || lowered.contains(it) }
                }
            ) {
                textHit = true
                break
            }
            node = node.parent
            depth++
        }
        val eventHit = eventTexts.any { raw ->
            val lowered = raw.toString().lowercase().trim()
            AUTOSTART_LIST_TITLE_WORDS.any { lowered == it || lowered.contains(it) }
        }
        if (!idHit && !textHit && !eventHit) return null

        return ProtectedScreenDetection(
            type = ProtectedScreenType.AUTOSTART_ENTRY,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 92,
        )
    }

    /**
     * First-click protection for the "Accessibility" entry everywhere it appears (settings home,
     * search results, shortcuts): the tap itself is challenged with the PIN overlay (0ms) before
     * the target page opens. Tooling words/ids are the language carriers; structure is the host
     * gate, so Persian/Kurdish/Turkish/Chinese devices behave identically to Arabic/English.
     */
    fun detectAccessibilityEntryClick(
        packageName: String,
        className: String?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null

        val payload = buildList {
            eventTexts.forEach { add(it.toString()) }
            var current = source
            var depth = 0
            while (current != null && depth < 6) {
                current.text?.toString()?.let(::add)
                current.contentDescription?.toString()?.let(::add)
                current.viewIdResourceName?.let(::add)
                current = current.parent
                depth++
            }
        }
        val hit = payload.any { raw ->
            val lowered = raw.lowercase().trim()
            if (lowered.isEmpty()) {
                false
            } else {
                val idPart = lowered.substringAfterLast('/')
                idPart.contains("accessibility") ||
                    ACCESSIBILITY_WORDS.any { lowered == it || lowered.contains(it) }
            }
        }
        if (!hit) return null
        return ProtectedScreenDetection(
            type = ProtectedScreenType.ACCESSIBILITY_SETTINGS,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 94,
        )
    }

    /**
     * First-click protection for THIS app's own row inside any system/host list — the
     * downloaded-apps list, installed-apps list, app manager rows… The general list stays free
     * forever; only the row rendering OUR identity (label / package / component) is guarded,
     * so a tap on it raises the PIN overlay before App-info opens. Language-independent by
     * construction, and the autostart list keeps its own direction-aware rules.
     */
    fun detectOwnAppRowClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null
        val scan = scan(root, className.orEmpty(), refreshAutostart = false)
        // Autostart rows keep the ON→OFF direction-aware contract (enabling must stay free).
        if (looksInsideAutostartList(className.orEmpty(), scan)) return null

        var current = source
        var depth = 0
        while (current != null && depth < 6) {
            if (subtreeHasOwnIdentity(current)) {
                return ProtectedScreenDetection(
                    type = ProtectedScreenType.APP_INFO,
                    settingsPackage = packageName,
                    screenClass = className.orEmpty().ifBlank { packageName },
                    confidence = 95,
                )
            }
            current = current.parent
            depth++
        }
        return null
    }

    /** True when [packageName] is a security-center host that carries the autostart entry. */
    private fun isAutostartEntryHost(packageName: String): Boolean =
        packageName.lowercase() in AUTOSTART_ENTRY_HOST_PACKAGES

    /**
     * True only when the window is a SENSITIVE sub-section inside one of the security-center
     * hosts (class-driven, language-independent). Root dashboards and the harmless tools pages
     * (cleaner, scan, data usage…) are explicitly exempt — those are class-exempt first so a
     * broad token can never sweep them in.
     */
    private fun isSecurityHostSensitiveSurface(
        packageName: String,
        activityClass: String,
        scan: ScanResult,
    ): Boolean {
        if (packageName.lowercase() !in AUTOSTART_ENTRY_HOST_PACKAGES) return false
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        // Order matters here: an app-DETAILS class wins over the App-Manager scope below,
        // because some ROMs nest detail activities under a "…appmanager…" package (MIUI/HyperOS
        // AppDetailsActivity). Details are identity-gated: third-party App-Info is always free,
        // only THIS app's own details page is a lock-point. The manager LIST itself stays gated
        // regardless of identity, per the previous policy update.
        if (SECURITY_HOST_DETAIL_CLASSES.any { lowered.contains(it) }) {
            return scan.matchesOwnApp
        }
        // Zero-click autostart + App-Manager rule inside the hosts: the SECTION is ejected the
        // instant it renders — class tokens, or merely the wording/ids of the autostart section
        // appearing on screen (scan.autostartEntrySignal covers every device language). The
        // host's dashboard and harmless tools stay 100% free by contrast.
        if (SECURITY_HOST_APP_MANAGER_CLASSES.any { lowered.contains(it) } ||
            SECURITY_HOST_AUTOSTART_CLASSES.any { lowered.contains(it) } ||
            scan.autostartEntrySignal
        ) {
            return true
        }
        return false
    }

    /**
     * Settings-search interception: a tap on a search result that routes to the autostart
     * section (".ّtheSystem Settings search shows the destination title as the row label).
     *
     * The destination Intent of a search result is never exposed to accessibility — that is a
     * platform fact — so detection rests on what IS verifiable: the search surface itself
     * (SearchView/EditText with a search id in the tree OR a search-host window class) plus the
     * clicked row's label / view-ids, which mirror the destination's wording in every locale.
     * Combined with the structural rules below this stays language-agnostic across OEM skins.
     */
    fun detectAutostartSearchResultClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null
        val scan = scan(root, className.orEmpty(), refreshAutostart = false)
        // Inside the real list the toggle rules govern; never here.
        if (looksInsideAutostartList(className.orEmpty(), scan)) return null

        val onSearchSurface = run {
            val cls = className.orEmpty().lowercase()
            scan.settingsSearchSignal || SEARCH_HOST_CLASSES.any { cls.contains(it) }
        }
        if (!onSearchSurface) return null

        val payload = buildList {
            eventTexts.forEach { add(it.toString()) }
            var current = source
            var depth = 0
            while (current != null && depth < 6) {
                current.text?.toString()?.let(::add)
                current.contentDescription?.toString()?.let(::add)
                current.viewIdResourceName?.let(::add)
                current = current.parent
                depth++
            }
        }
        val hit = payload.any { raw ->
            val lowered = raw.lowercase().trim()
            if (lowered.isEmpty()) {
                false
            } else {
                AUTOSTART_LIST_TITLE_WORDS.any { lowered == it || lowered.contains(it) } ||
                    AUTOSTART_ENTRY_VIEW_IDS.any {
                        lowered.substringAfterLast('/').lowercase().contains(it)
                    }
            }
        }
        if (!hit) return null

        return ProtectedScreenDetection(
            type = ProtectedScreenType.AUTOSTART_ENTRY,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 93,
        )
    }


    /**
     * True only while the window is REALLY the autostart/app-launch list. Structural, never
     * textual: the list renders ≥3 switch rows (one toggle per app), while security-center
     * dashboards / manage-apps pages that merely show the entry card do not.
     */
    private fun looksInsideAutostartList(activityClass: String, scan: ScanResult): Boolean {
        if (scan.switchCount >= 3) return true
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return AUTOSTART_LIST_CLASSES.any { lowered.contains(it) }
    }

    /** True when the surface is the general autostart / app-launch management list. */
    private fun isAutostartListSurface(activityClass: String, scan: ScanResult): Boolean {
        if (activityClass.isNotBlank()) {
            val lowered = activityClass.lowercase()
            if (AUTOSTART_LIST_CLASSES.any { lowered.contains(it) }) return true
        }
        return scan.autostartListSignal
    }

    /** True when [node] (or a bounded descendant) renders this app's label or package id. */
    private fun subtreeHasOwnIdentity(node: AccessibilityNodeInfo?, depth: Int = 0): Boolean {
        if (node == null || depth > 4) return false
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.isBlank() || raw.length > MAX_TEXT_LENGTH) continue
            val normalized = normalizeForIdentity(raw.lowercase())
            if (normalized.contains(ownPackage)) return true
            if (normalizedOwnLabel.length >= 4 && normalized.contains(normalizedOwnLabel)) return true
        }
        val count = minOf(node.childCount, 12)
        for (index in 0 until count) {
            val child = node.getChild(index) ?: continue
            if (subtreeHasOwnIdentity(child, depth + 1)) return true
        }
        return false
    }

    /** Switch-like widget classes across AOSP and OEM skins (MIUI SlidingButton included). */
    private fun isSwitchLike(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val nodeClass = node.className?.toString()?.lowercase().orEmpty()
        return nodeClass.contains("switch") ||
            nodeClass.contains("togglebutton") ||
            nodeClass.contains("slidingbutton")
    }

    /** First switch-like node inside [node]'s bounded subtree, or null. */
    private fun findSwitchNode(node: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (node == null || depth > 5) return null
        if (isSwitchLike(node)) return node
        val count = minOf(node.childCount, 12)
        for (index in 0 until count) {
            val child = node.getChild(index) ?: continue
            val found = findSwitchNode(child, depth + 1)
            if (found != null) return found
        }
        return null
    }

    /** The switch nearest to the tapped node: searched upwards through its own row first. */
    private fun findNearestSwitch(source: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = source
        var depth = 0
        while (current != null && depth < 4) {
            val found = findSwitchNode(current)
            if (found != null) return found
            current = current.parent
            depth++
        }
        return null
    }

    /**
     * Stable, language-agnostic key for a switch: the nearest visible label of its row,
     * normalised and lowercased. Needs zero localization knowledge — the same rendered text is
     * the key both when the surface is scanned and when the tap is handled.
     */
    private fun rowKeyForSwitch(switch: AccessibilityNodeInfo): String {
        var current: AccessibilityNodeInfo? = switch
        var depth = 0
        while (current != null && depth < 4) {
            current.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                return normalizeForIdentity(it.lowercase().trim().take(80))
            }
            val count = minOf(current.childCount, 10)
            for (index in 0 until count) {
                val child = current.getChild(index) ?: continue
                val childText = child.text?.toString()
                if (!childText.isNullOrBlank()) {
                    return normalizeForIdentity(childText.lowercase().trim().take(80))
                }
            }
            current = current.parent
            depth++
        }
        return "__row__"
    }

    /** Snapshots every switch state on the autostart surface, keyed by its row label. */
    private fun refreshAutostartSwitchStates(root: AccessibilityNodeInfo) {
        val states = HashMap<String, Boolean>(16)
        collectAutostartSwitchStates(root, 0, Counter(), states)
        autostartSwitchStates = states
    }

    private fun collectAutostartSwitchStates(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
        out: MutableMap<String, Boolean>,
    ) {
        if (node == null || depth > 14 || counter.visited > 350) return
        counter.visited++
        if (isSwitchLike(node)) {
            out[rowKeyForSwitch(node)] = node.isChecked
        }
        val count = minOf(node.childCount, 16)
        for (index in 0 until count) {
            collectAutostartSwitchStates(node.getChild(index), depth + 1, counter, out)
        }
    }

    /**
     * Click-time interception for the "Restrict background / Battery saver / Optimized" style
     * options on THIS app's battery page while background work is not yet enabled. The tap is
     * challenged with the PIN instead of silently degrading the app's background operation.
     * Never fires on any other battery surface: page identity has to match first, and a payload
     * that carries the UNRESTRICTED wording ("No restrictions" contains "restrict"…) is skipped
     * explicitly so enabling background work is never challenged.
     */
    fun detectBatteryRestrictClick(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence>,
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName)) return null
        val scan = scan(root)
        if (!isOwnAppBatterySurface(className.orEmpty(), scan)) return null
        // Once unrestricted is on, the whole page is gated by detect() instead — click math not needed.
        if (isOwnAppBatteryUnrestricted(scan)) return null

        val payload = buildList {
            eventTexts.forEach { add(it.toString()) }
            var current = source
            var depth = 0
            while (current != null && depth < 4) {
                current.text?.toString()?.let(::add)
                current.contentDescription?.toString()?.let(::add)
                current = current.parent
                depth++
            }
        }
        val hit = payload.any { raw ->
            val lowered = raw.lowercase().trim()
            if (lowered.length > MAX_TEXT_LENGTH) {
                false
            } else {
                if (BATTERY_UNRESTRICTED_WORDS.any { lowered.contains(it) }) {
                    false
                } else {
                    BATTERY_RESTRICT_WORDS.any { phrase ->
                        if (phrase.contains(' ')) {
                            lowered == phrase || lowered.contains(phrase)
                        } else {
                            lowered == phrase
                        }
                    }
                }
            }
        }
        if (!hit) return null
        return ProtectedScreenDetection(
            type = ProtectedScreenType.BATTERY_RESTRICTION,
            settingsPackage = packageName,
            screenClass = className.orEmpty().ifBlank { packageName },
            confidence = 96,
        )
    }

    private fun resolveType(activityClass: String, scan: ScanResult): ProtectedScreenType {
        val lowered = activityClass.lowercase()
        return when {
            UNINSTALL_CLASSES.any { lowered.contains(it) } || scan.uninstallSignal -> ProtectedScreenType.UNINSTALL
            PERMISSION_CLASSES.any { lowered.contains(it) } || scan.permissionSignal -> ProtectedScreenType.PERMISSIONS
            scan.forceStopSignal -> ProtectedScreenType.FORCE_STOP
            scan.clearDataSignal -> ProtectedScreenType.CLEAR_DATA
            INSTALLED_DETAIL_CLASSES.any { lowered.contains(it) } -> ProtectedScreenType.INSTALLED_APP_DETAILS
            lowered.contains("applicationdetails") || lowered.contains("appinfo") -> ProtectedScreenType.APP_DETAILS_SETTINGS
            else -> ProtectedScreenType.APP_INFO
        }
    }

    private fun isSystemSurface(packageName: String): Boolean {
        if (packageName in SYSTEM_PACKAGES) return true
        val lowered = packageName.lowercase()
        return SYSTEM_PACKAGE_MARKERS.any { lowered.contains(it) }
    }

    /**
     * Detects the power-menu Safe Mode option / confirmation across AOSP and common OEM skins.
     * Matches on package + dialog class and/or localized "Safe mode" wording in the tree.
     */
    private fun isSafeModeSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (!isPowerMenuHost(packageName)) return false
        return rootHasSafeModeText(root)
    }

    /**
     * Detects the Developer options screen (and the sensitive OEM-unlock / USB-debugging toggles
     * inside it). Requires a Settings host plus a developer-options activity class OR the screen
     * title wording, so unrelated Settings pages are never matched.
     */
    private fun isDeveloperOptionsSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (!isSystemSurface(packageName)) return false
        val loweredClass = activityClass.lowercase()
        if (DEVELOPER_OPTIONS_CLASSES.any { loweredClass.contains(it) }) return true
        return rootHasDeveloperOptionsText(root)
    }

    private fun rootHasDeveloperOptionsText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return walkDeveloperOptionsText(root, 0, Counter())
    }

    private fun walkDeveloperOptionsText(node: AccessibilityNodeInfo?, depth: Int, counter: Counter): Boolean {
        if (node == null || depth > 8 || counter.visited > 260) return false
        counter.visited++
        val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in values) {
            val lowered = raw.trim().lowercase()
            if (lowered.isEmpty() || lowered.length > 42) continue
            if (DEVELOPER_OPTIONS_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (walkDeveloperOptionsText(child, depth + 1, counter)) return true
        }
        return false
    }

    /**
     * True when the visible window is the system "Network & internet → VPN" settings page (or
     * an OEM equivalent) — the manual route to add/edit a VPN profile that would otherwise let
     * "Block VPN & bypass apps" be sidestepped entirely from inside Settings.
     *
     * Covers, across AOSP/Pixel, Samsung One UI, Xiaomi MIUI/HyperOS, Honor MagicOS, Huawei
     * EMUI, Oppo ColorOS, Vivo Funtouch/OriginOS and Realme UI:
     *  1. Dedicated activity/fragment classes (`vpn2.VpnSettings`, `VpnSettingsActivity`, any
     *     OEM class ending in `VpnSettings`/`VpnPreference` …) — language-independent, matched
     *     first so a genuine VPN screen is recognised at 0ms even before its content renders.
     *  2. A generic container class (`SubSettings`, a bare fragment host, …) IS still matched,
     *     but only together with structural content evidence — a VPN-specific view-id
     *     (`vpn_layout`, `vpn_list`, `add_vpn` …) or a short, VPN-specific heading/label
     *     ("VPN", "VPN settings", "Add VPN profile" and their Arabic/Kurdish equivalents). The
     *     length cap on scanned text keeps this from ever matching a random paragraph that
     *     merely mentions VPN in passing.
     */
    fun isSystemVpnSettingsSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (!isSystemSurface(packageName)) return false
        val loweredClass = activityClass.lowercase()
        if (loweredClass.isNotEmpty() && SYSTEM_VPN_SETTINGS_CLASSES.any { loweredClass.contains(it) }) {
            return true
        }
        return rootHasSystemVpnSettingsEvidence(root)
    }

    /**
     * True when the visible Settings window is the Private DNS (DoT/DoH hostname) surface.
     * Used by the accessibility service to eject users who would otherwise bypass the local
     * DNS VPN filter.
     */
    fun isPrivateDnsSettingsSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean = PrivateDnsGuard.isPrivateDnsSettingsSurface(packageName, activityClass, root)

    private fun rootHasSystemVpnSettingsEvidence(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return walkSystemVpnSettingsEvidence(root, 0, Counter())
    }

    private fun walkSystemVpnSettingsEvidence(node: AccessibilityNodeInfo?, depth: Int, counter: Counter): Boolean {
        if (node == null || depth > 10 || counter.visited > 280) return false
        counter.visited++
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (id.isNotEmpty() && SYSTEM_VPN_VIEW_ID_TOKENS.any { id.contains(it) }) return true
        val values = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in values) {
            val lowered = raw.trim().lowercase()
            if (lowered.isEmpty() || lowered.length > 40) continue
            if (SYSTEM_VPN_TEXT_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (walkSystemVpnSettingsEvidence(child, depth + 1, counter)) return true
        }
        return false
    }

    /**
     * Power off / Restart power menu (GlobalActions). Requires SystemUI (or OEM power host)
     * plus clear power-off/restart wording so unrelated SystemUI panels are not intercepted.
     */
    private fun isPowerMenuSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        if (!isPowerMenuHost(packageName)) return false
        val loweredClass = activityClass.lowercase()
        val classHint = loweredClass.isNotEmpty() && POWER_MENU_CLASSES.any { loweredClass.contains(it) }
        val textHint = rootHasPowerMenuText(root)
        // Prefer wording; class+wording is strongest. Class alone is not enough (volume UI etc.).
        if (textHint) return true
        return classHint && textHint
    }

    private fun isPowerMenuHost(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return pkg == "com.android.systemui" ||
            pkg.endsWith(".systemui") ||
            pkg.contains("systemui") ||
            pkg in SAFE_MODE_HOST_PACKAGES ||
            SAFE_MODE_HOST_MARKERS.any { pkg.contains(it) } ||
            // Some OEMs host reboot confirm inside Settings.
            pkg.contains("settings")
    }

    private fun rootHasPowerMenuText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        // Notification shade / quick-settings must never be matched as a power menu, even when
        // its tiles contain identical wording ("restart", state words …). Both the root itself
        // AND its ancestors carry the same view-id (`notification_panel`, `qs_panel` …) on
        // most OEM skins, so a single self-check is not enough.
        if (isInsideQuickSettings(root)) return false
        val counter = Counter()
        return walkPowerMenuText(root, 0, counter)
    }

    private fun walkPowerMenuText(node: AccessibilityNodeInfo?, depth: Int, counter: Counter): Boolean {
        if (node == null || depth > 10 || counter.visited > 140) return false
        counter.visited++
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        // Notification shade / quick-settings subtrees are never a power menu: their tiles and
        // notification actions carry labels ("restart", state words …) that would otherwise
        // mis-flag the shade and raise the PIN on a simple flashlight toggle.
        if (QUICK_SETTINGS_VIEW_IDS.any { id.contains(it) }) return false
        if (POWER_MENU_VIEW_IDS.any { id.contains(it) }) return true
        if (depth <= 2 && isInsideQuickSettings(node)) return false
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.length > MAX_TEXT_LENGTH) continue
            val lowered = raw.lowercase().trim()
            if (matchesPowerMenuWording(lowered) && !looksLikeForceStopLabel(lowered)) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (walkPowerMenuText(child, depth + 1, counter)) return true
        }
        return false
    }

    /**
     * Power-menu wording matched strictly: multi-word phrases may be a substring match, while a
     * bare single word (e.g. "إيقاف") only counts when the node's own label is exactly that word.
     * App-info pages of OTHER apps contain buttons like "إيقاف قسري" (Force stop) whose text
     * would otherwise swallow the single-word needle and mis-flag every App-info surface.
     */
    private fun matchesPowerMenuWording(lowered: String): Boolean = POWER_MENU_WORDS.any { phrase ->
        if (phrase.contains(' ') || phrase.contains('-')) {
            lowered == phrase || lowered.contains(phrase)
        } else {
            lowered == phrase
        }
    }

    /**
     * The "Force stop" button exists on *every* app's App-info page ("إيقاف قسري", "Force stop"…).
     * Such a label is never a power-menu entry, so it must not count as power-menu wording.
     */
    private fun looksLikeForceStopLabel(lowered: String): Boolean =
        FORCE_STOP_WORDS.any { lowered.contains(it) }

    private fun rootHasSafeModeText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        if (isInsideQuickSettings(root)) return false
        val counter = Counter()
        return walkSafeModeText(root, 0, counter)
    }

    private fun walkSafeModeText(node: AccessibilityNodeInfo?, depth: Int, counter: Counter): Boolean {
        if (node == null || depth > 16 || counter.visited > 300) return false
        counter.visited++
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (id.contains("safe_mode") || id.contains("safemode") || id.contains("safe_boot")) return true
        if (QUICK_SETTINGS_VIEW_IDS.any { id.contains(it) }) return false
        if (depth <= 2 && isInsideQuickSettings(node)) return false
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.length > MAX_TEXT_LENGTH) continue
            val lowered = raw.lowercase().trim()
            if (SAFE_MODE_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val found = walkSafeModeText(child, depth + 1, counter)
            if (found) return true
        }
        return false
    }

    /**
     * Walks the ancestry chain (and shallow siblings) of [node] looking for any view-id from
     * the notification shade / quick-settings panel. A positive result means the tree is
     * physically mounted inside the system UI shade, so power-menu / safe-mode wording that
     * lives there must be ignored.
     */
    private fun isInsideQuickSettings(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 8) {
            val id = current.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            if (id.isNotEmpty() && QUICK_SETTINGS_VIEW_IDS.any { id.contains(it) }) return true
            current = current.parent
            depth++
        }
        return false
    }

    /**
     * Generic Settings → Apps / All apps / Manage apps list (NOT "Downloaded apps").
     * These surfaces must stay PIN-free so the user can browse installed applications.
     */
    private fun isGenericAppsListSurface(activityClass: String, scan: ScanResult): Boolean {
        // Downloaded-apps specific wording wins over the generic list.
        if (scan.downloadedAppsListSignal) return false
        if (activityClass.isNotBlank()) {
            val lowered = activityClass.lowercase()
            if (MANAGE_APPS_CLASSES.any { lowered.contains(it) }) return true
        }
        return scan.genericAppsListSignal
    }

    /**
     * "Downloaded apps" / sideloaded-apps list — remains PIN-gated as a sensitive management path.
     */
    private fun isDownloadedAppsListSurface(activityClass: String, scan: ScanResult): Boolean {
        if (scan.downloadedAppsListSignal) return true
        // Some OEMs host Downloaded apps inside ManageApplications without a distinct class;
        // only the content signal is reliable there (handled above).
        return false
    }

    /**
     * True when the window looks like an application-details / App Info surface (any app).
     * Used to hard-exclude other apps' App Info from the PIN challenge.
     * Intentionally does NOT treat bare SubSettings / generic containers as App Info — those
     * host factory-reset, accessibility, etc. and are handled by their own detectors.
     */
    private fun isAppDetailSurface(activityClass: String, scan: ScanResult): Boolean {
        if (activityClass.isNotBlank()) {
            val lowered = activityClass.lowercase()
            // Dedicated App Info / uninstall classes only (not the broad DETAIL_CLASSES list
            // that also contains "subsettings").
            if (INSTALLED_DETAIL_CLASSES.any { lowered.contains(it) }) return true
            if (UNINSTALL_CLASSES.any { lowered.contains(it) }) return true
            if (APP_INFO_ONLY_CLASSES.any { lowered.contains(it) }) return true
        }
        // Content: classic App Info chrome (header / uninstall / force-stop).
        if (scan.structuralViewId) return true
        if (scan.uninstallSignal || scan.forceStopSignal || scan.clearDataSignal) return true
        return false
    }

    /** @deprecated Kept for call-site clarity in comments; prefer the specific helpers above. */
    private fun isInstalledAppsListSurface(activityClass: String, scan: ScanResult): Boolean {
        return isGenericAppsListSurface(activityClass, scan) || isDownloadedAppsListSurface(activityClass, scan)
    }

    /**
     * Recognises the settings destinations that lead to uninstalling or to disabling device
     * administration. Matched on the activity/fragment class so the check stays O(1) and does
     * not depend on any localized label.
     */
    private fun adminSurfaceType(packageName: String, activityClass: String, scan: ScanResult): ProtectedScreenType? {
        if (activityClass.isBlank()) return null
        val lowered = activityClass.lowercase()
        if (DEVICE_ADMIN_CLASSES.any { lowered.contains(it) }) return ProtectedScreenType.DEVICE_ADMIN
        // MANAGE_APPS (Settings → Apps list) is no longer PIN-gated — do not match it here.
        //
        // Security-center hosts (Xiaomi Security, Honor/Huawei System Manager, ColorOS
        // Safecenter, iQOO Secure …) embed a "securitycenter"-like token in EVERY activity
        // class they own, which would otherwise gate their entire home tools (cleaner,
        // antivirus, battery, data usage …) behind the PIN. By product rule their home and
        // harmless tools stay 100% free; their sensitive sub-sections are enforced by the
        // dedicated host detectors (window + click gates), identically in every language.
        if (packageName.lowercase() !in AUTOSTART_ENTRY_HOST_PACKAGES &&
            SECURITY_CLASSES.any { lowered.contains(it) }
        ) {
            return ProtectedScreenType.SECURITY_SETTINGS
        }
        // Some OEM skins reuse a generic container class for the admin list; fall back to the
        // visible label only when an explicit device-admin wording is present.
        if (scan.deviceAdminSignal) return ProtectedScreenType.DEVICE_ADMIN
        // Content fallback for THIS app's detail screens: a system screen that shows this app
        // together with a destructive action (Uninstall / Force stop / Clear data) must stay
        // guarded. The generic Apps list itself is not challenged.
        if (scan.destructiveOwnApp) {
            return when {
                scan.uninstallSignal -> ProtectedScreenType.UNINSTALL
                scan.forceStopSignal -> ProtectedScreenType.FORCE_STOP
                scan.clearDataSignal -> ProtectedScreenType.CLEAR_DATA
                else -> ProtectedScreenType.APP_INFO
            }
        }
        return null
    }

    /**
     * Dedicated factory-reset flow screens (after choosing Erase all data), not the parent
     * "Reset" menu that also lists network/Bluetooth/accessibility resets (Samsung screenshot).
     */
    private fun isFactoryResetEntryOrConfirmActivity(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        // Exclude the list/dashboard of mixed reset tools.
        if (FACTORY_RESET_LIST_CLASSES.any { lowered.contains(it) }) return false
        if (FACTORY_RESET_CONFIRM_CLASSES.any { lowered.contains(it) }) return true
        return FACTORY_RESET_ENTRY_CLASSES.any { lowered.contains(it) }
    }

    /**
     * PIN when the user taps "Erase all data (factory reset)" (or OEM equivalent).
     * [eventTexts] covers cases where [source] is an icon/chevron without label (common on Samsung).
     */
    /**
     * Catches a click / long-press on a "Power off" or "Reboot to safe mode" control inside the
     * power menu, so protection can challenge one step before the safe-mode confirmation dialog is
     * fully drawn. Only fires on a power-menu host, and only when the clicked node (or the event
     * text) carries a safe-mode trigger word — so an ordinary power-off tap on a device that shows
     * no safe-mode wording is never gated.
     */
    /**
     * Zero-touch interception of the ULTRA / EXTREME / SUPER / MAX power-saving toggle — the one
     * switch able to strip the whole protection stack (VPN killed, watchdogs frozen, some ROMs
     * even detach the accessibility service). The PIN challenge is raised on the tap itself,
     * before the mode can flip — covering both entry points named in the requirement:
     *   1. the Quick Settings / control-centre tile (SystemUI processes of every OEM), and
     *   2. the in-Settings switch (battery / power pages of Settings and the security hosts).
     * The ordinary Battery Saver toggle is deliberately NEVER matched: every vocabulary entry
     * carries an ultra/super/extreme/max qualifier, so normal power saving stays untouchable.
     * Matching targets the clicked tile/row, its small ancestor chain and the event payload —
     * never page-wide text — in English, Arabic, Kurdish, Persian, Turkish and the main OEM
     * locales (ZH / JA / KO / RU / DE / ES / FR).
     */
    fun detectUltraPowerSavingClick(
        packageName: String,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence> = emptyList(),
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        // Settings / security hosts only — the quick-settings panel and notification shade are
        // deliberately out of scope for this detector (product decision: the tile is free there).
        if (!isSystemSurface(packageName)) return null

        // Dual-surface block (product decision): the Ultra Battery Saver tile is intercepted
        // EVERYWHERE it can appear — in the main quick-settings panel and inside the tile-edit
        // customizer alike. Any tap or drag of the tile is challenged at 0ms in every language.
        var textHit = false
        var node = source
        var depth = 0
        while (node != null && depth < 6 && !textHit) {
            if (matchesUltraPowerSavingNode(node)) {
                textHit = true
                break
            }
            node = node.parent
            depth++
        }
        val eventHit = eventTexts.any { raw ->
            val lowered = raw.toString().lowercase().trim()
            ULTRA_PS_ENTRY_WORDS.any { lowered == it || lowered.contains(it) }
        }
        if (!textHit && !eventHit) return null

        return ProtectedScreenDetection(
            type = ProtectedScreenType.BATTERY_RESTRICTION,
            settingsPackage = packageName,
            screenClass = packageName,
            confidence = 96,
        )
    }

    /**
     * True when [node] — or a bounded shallow child (one label level, as quick-settings tiles
     * put their caption in a sibling/child TextView of the tapped icon) — carries either the
     * Ultra power-saving view-id or any localized toggle wording. Capability is capped so the
     * check costs nothing on unrelated taps.
     */
    private fun matchesUltraPowerSavingNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        fun matchesText(raw: CharSequence?): Boolean {
            if (raw.isNullOrBlank()) return false
            val lowered = raw.toString().lowercase().trim()
            return ULTRA_PS_ENTRY_WORDS.any { lowered == it || lowered.contains(it) }
        }
        fun matchesId(nodeId: String?): Boolean {
            val id = nodeId?.substringAfterLast('/')?.lowercase().orEmpty()
            return id.isNotEmpty() && ULTRA_PS_TILE_VIEW_IDS.any { id.contains(it) }
        }
        if (matchesId(node.viewIdResourceName) ||
            matchesText(node.text) ||
            matchesText(node.contentDescription)
        ) {
            return true
        }
        // Shallow child pass: covers tile layouts whose label sits below the tapped icon.
        val childCount = minOf(node.childCount, 12)
        for (index in 0 until childCount) {
            val child = node.getChild(index) ?: continue
            val hit = matchesId(child.viewIdResourceName) ||
                matchesText(child.text) ||
                matchesText(child.contentDescription) || run {
                val grandCount = minOf(child.childCount, 6)
                var grandHit = false
                for (grandIndex in 0 until grandCount) {
                    val grandChild = child.getChild(grandIndex) ?: continue
                    if (matchesId(grandChild.viewIdResourceName) ||
                        matchesText(grandChild.text) ||
                        matchesText(grandChild.contentDescription)
                    ) {
                        grandHit = true
                    }
                }
                grandHit
            }
            if (hit) return true
        }
        return false
    }

    /**
     * True when ANY rendered node in the window carries the Ultra / Super / Extreme power-saving
     * label or tile id. Runs the instant the surface draws (window-state / content-change
     * events), so the tile is blocked at text-appearance time — 0ms, before a tap can ever
     * land. The walk is strictly bounded (depth ≤ 7, ≤ 180 nodes) and exits on first hit, so a
     * busy SystemUI panel without the tile costs a fast no-hit scan only.
     */
    private fun pageRendersUltraPowerSaving(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        return runCatching { pageRendersUltraPowerSavingNode(root, 0, Counter()) }.getOrDefault(false)
    }

    private fun pageRendersUltraPowerSavingNode(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
    ): Boolean {
        if (node == null || depth > 7 || counter.visited > 180) return false
        counter.visited++

        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (id.isNotEmpty() && ULTRA_PS_TILE_VIEW_IDS.any { id.contains(it) }) return true
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (blob in blobs) {
            if (blob.isBlank()) continue
            val lowered = blob.lowercase().trim()
            if (ULTRA_PS_ENTRY_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }

        val count = minOf(node.childCount, 16)
        for (index in 0 until count) {
            if (pageRendersUltraPowerSavingNode(node.getChild(index), depth + 1, counter)) return true
        }
        return false
    }

    fun detectSafeModeActionClick(
        packageName: String,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence> = emptyList(),
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isPowerMenuHost(packageName)) return null

        // A direct safe-mode word anywhere in the click payload is an immediate match.
        val eventHasSafeMode = eventTexts.any { raw ->
            val l = raw.toString().lowercase().trim()
            SAFE_MODE_WORDS.any { l == it || l.contains(it) }
        }
        if (eventHasSafeMode) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.SAFE_MODE,
                settingsPackage = packageName,
                screenClass = source?.className?.toString().orEmpty().ifBlank { packageName },
                confidence = 97,
            )
        }
        // Otherwise, a click on the power-off/reboot control counts only when a safe-mode word is
        // actually present on the current screen (the long-press that reveals the safe-mode dialog).
        val triggerClicked = eventTexts.any { raw ->
            val l = raw.toString().lowercase().trim()
            SAFE_MODE_TRIGGER_WORDS.any { l == it || l.contains(it) }
        } || (source != null && nodeHasSafeModeTrigger(source))
        if (triggerClicked && rootHasSafeModeText(rootOf(source))) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.SAFE_MODE,
                settingsPackage = packageName,
                screenClass = source?.className?.toString().orEmpty().ifBlank { packageName },
                confidence = 96,
            )
        }
        return null
    }

    private fun nodeHasSafeModeTrigger(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 5) {
            val blobs = listOfNotNull(current.text?.toString(), current.contentDescription?.toString())
            for (raw in blobs) {
                val l = raw.lowercase().trim()
                if (SAFE_MODE_WORDS.any { l == it || l.contains(it) }) return true
                if (SAFE_MODE_TRIGGER_WORDS.any { l == it || l.contains(it) }) return true
            }
            current = current.parent
            depth++
        }
        return false
    }

    /** Walks up to the root of the tree that [node] belongs to (bounded). */
    private fun rootOf(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (depth < 40) {
            val parent = current?.parent ?: break
            current = parent
            depth++
        }
        return current
    }

    fun detectFactoryResetActionClick(
        packageName: String,
        source: AccessibilityNodeInfo?,
        eventTexts: List<CharSequence> = emptyList(),
    ): ProtectedScreenDetection? {
        if (packageName.isBlank() || packageName == ownPackage) return null
        if (!isSystemSurface(packageName) && !packageName.lowercase().contains("settings")) {
            return null
        }
        // Per-app storage dialogs reuse generic "erase/delete all data" wording (e.g. MIUI's
        // "محو جميع البيانات" in App info → Storage of ANY app). Only treat such wording as a
        // phone wipe when a factory/reset marker is present in the click payload or the clicked
        // row's ancestry; label[s] that already contain a reset marker qualify on their own.
        val resetContextNearby =
            eventTexts.any { containsFactoryResetMarker(it.toString()) } ||
                sourceHasFactoryResetContext(source)
        // AccessibilityEvent text is often the preference title even when source has no text.
        if (eventTexts.any { textLooksLikeFactoryResetAction(it.toString(), resetContextNearby) }) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.FACTORY_RESET,
                settingsPackage = packageName,
                screenClass = source?.className?.toString().orEmpty().ifBlank { packageName },
                confidence = 99,
            )
        }
        if (source != null && isFactoryResetActionNode(source, resetContextNearby)) {
            return ProtectedScreenDetection(
                type = ProtectedScreenType.FACTORY_RESET,
                settingsPackage = packageName,
                screenClass = source.className?.toString().orEmpty().ifBlank { packageName },
                confidence = 98,
            )
        }
        return null
    }

    private fun isFactoryResetActionNode(
        node: AccessibilityNodeInfo,
        resetContextNearby: Boolean,
    ): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (nodeLooksLikeFactoryResetAction(current, resetContextNearby)) return true
            current = current.parent
            depth++
        }
        return childrenLookLikeFactoryResetAction(node, 0, resetContextNearby)
    }

    private fun childrenLookLikeFactoryResetAction(
        node: AccessibilityNodeInfo,
        depth: Int,
        resetContextNearby: Boolean,
    ): Boolean {
        if (depth > 4) return false
        if (nodeLooksLikeFactoryResetAction(node, resetContextNearby)) return true
        val count = minOf(node.childCount, 14)
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            if (childrenLookLikeFactoryResetAction(child, depth + 1, resetContextNearby)) return true
        }
        return false
    }

    private fun nodeLooksLikeFactoryResetAction(
        node: AccessibilityNodeInfo,
        resetContextNearby: Boolean,
    ): Boolean {
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (FACTORY_RESET_ACTION_VIEW_IDS.any { id.contains(it) }) return true
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        return blobs.any { textLooksLikeFactoryResetAction(it, resetContextNearby) }
    }

    /**
     * Walks the clicked node's own labels and its parents (the row/card container) looking for
     * wipe-flow wording such as "factory" / "reset" / "إعادة ضبط المصنع".
     */
    private fun sourceHasFactoryResetContext(source: AccessibilityNodeInfo?): Boolean {
        var current = source
        var depth = 0
        while (current != null && depth < 6) {
            if (containsFactoryResetMarker(current.text?.toString())) return true
            if (containsFactoryResetMarker(current.contentDescription?.toString())) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun containsFactoryResetMarker(raw: String?): Boolean {
        if (raw.isNullOrBlank() || raw.length > MAX_TEXT_LENGTH) return false
        val lowered = raw.lowercase()
        return FACTORY_RESET_CONTEXT_WORDS.any { lowered.contains(it) }
    }

    /**
     * @param resetContextNearby true when factory/reset wording was found in the click event
     * payload or the clicked row's ancestry, i.e. the click really happens inside a phone-wipe
     * flow and not inside a per-app (other app) storage dialog.
     */
    private fun textLooksLikeFactoryResetAction(raw: String, resetContextNearby: Boolean): Boolean {
        if (raw.isBlank() || raw.length > MAX_TEXT_LENGTH) return false
        val lowered = raw.lowercase().trim()

        // Labels that unambiguously describe wiping the phone itself (factory/reset marker inside).
        if (FACTORY_RESET_STRONG_WORDS.any { phrase ->
                lowered == phrase || lowered.startsWith(phrase) || lowered.contains(phrase)
            }
        ) {
            return true
        }

        // Generic per-app storage wording ("Clear all data", "محو جميع البيانات"…) only counts
        // when a factory/reset marker accompanies the click; otherwise it is just Clear-data of
        // another application's App-info page and must never raise this app's PIN.
        val hasResetMarker = resetContextNearby || containsFactoryResetMarker(lowered)
        if (!hasResetMarker) return false

        // Ignore soft resets on the same Samsung "Reset" list.
        if (FACTORY_RESET_SOFT_RESET_WORDS.any { lowered.contains(it) }) return false
        return FACTORY_RESET_STORAGE_WORDS.any { phrase ->
            lowered == phrase || lowered.startsWith(phrase) || lowered.contains(phrase)
        }
    }

    /**
     * DocumentsUI / Settings dialog: "Delete system update?" with Cancel / Delete.
     * Package is often com.google.android.documentsui or com.android.documentsui.
     */
    private fun isSystemUpdateDeleteSurface(
        packageName: String,
        activityClass: String,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val pkg = packageName.lowercase()
        val hostOk = pkg.contains("documentsui") ||
            pkg.contains("documents") ||
            pkg in SYSTEM_UPDATE_HOST_PACKAGES ||
            isSystemSurface(packageName) ||
            pkg.contains("settings") ||
            pkg.contains("gms") ||
            pkg.contains("packageinstaller")
        if (!hostOk) return false

        val loweredClass = activityClass.lowercase()
        if (loweredClass.isNotEmpty() && SYSTEM_UPDATE_CLASSES.any { loweredClass.contains(it) }) {
            // Class alone is weak; still require wording when possible.
            if (rootHasSystemUpdateDeleteText(root)) return true
        }
        return rootHasSystemUpdateDeleteText(root)
    }

    private fun rootHasSystemUpdateDeleteText(root: AccessibilityNodeInfo?): Boolean {
        if (root == null) return false
        val counter = Counter()
        return walkSystemUpdateDeleteText(root, 0, counter)
    }

    private fun walkSystemUpdateDeleteText(
        node: AccessibilityNodeInfo?,
        depth: Int,
        counter: Counter,
    ): Boolean {
        if (node == null || depth > 12 || counter.visited > 200) return false
        counter.visited++
        val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
        if (SYSTEM_UPDATE_VIEW_IDS.any { id.contains(it) }) return true
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.length > MAX_TEXT_LENGTH) continue
            val lowered = raw.lowercase().trim()
            if (SYSTEM_UPDATE_DELETE_WORDS.any { lowered == it || lowered.contains(it) }) return true
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            if (walkSystemUpdateDeleteText(child, depth + 1, counter)) return true
        }
        return false
    }

    private fun classSignal(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return DETAIL_CLASSES.any { lowered.contains(it) }
    }

    /** True when the activity class is an accessibility settings surface. */
    private fun accessibilitySurface(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return ACCESSIBILITY_CLASSES.any { lowered.contains(it) }
    }

    private fun isExcludedClass(activityClass: String): Boolean {
        if (activityClass.isBlank()) return false
        val lowered = activityClass.lowercase()
        return EXCLUDED_CLASSES.any { lowered.contains(it) }
    }

    private fun scan(
        root: AccessibilityNodeInfo?,
        activityClass: String = "",
        refreshAutostart: Boolean = true,
    ): ScanResult {
        val result = ScanResult()
        if (root == null) return result
        val label = ownLabel.lowercase()
        walk(root, 0, Counter(), result, label)
        if (refreshAutostart && isAutostartListSurface(activityClass, result)) {
            // Snapshot the switch states on every NON-click inspection so the click handler can
            // know whether THIS app's toggle was ON or OFF before the tap. Several OEM skins
            // (MIUI SlidingButton in particular) toggle the switch BEFORE delivering the
            // accessibility click event, so a live read at click time would see the already
            // flipped state and invert ON/OFF handling.
            refreshAutostartSwitchStates(root)
        }
        return result
    }

    /**
     * Detects factory-reset screens on OEM skins (MIUI / HyperOS / Funtouch / OnePlus …) where
     * the dedicated MasterClear activity is wrapped in a generic container class. Requires TWO
     * strong signals within the same tree: a title/literal referencing "Factory reset" (or its
     * Koch-/Kurdish translated form) AND a destructive confirmation action ("Erase all data",
     * "factory data reset", "master clear"). Both must be present so that a general "Reset"
     * list — which exposes only the title — never qualifies.
     */
    private fun isFactoryResetScreenByText(root: AccessibilityNodeInfo?, scan: ScanResult): Boolean {
        if (root == null) return false
        val evidence = FactoryResetTextEvidence()
        collectFactoryResetTextEvidence(root, 0, evidence)
        return scan.factoryResetTitleSeen && evidence.erasurePerformed
    }

    private fun collectFactoryResetTextEvidence(
        node: AccessibilityNodeInfo?,
        depth: Int,
        evidence: FactoryResetTextEvidence,
    ) {
        if (node == null || depth > MAX_DEPTH || evidence.visited > MAX_NODES) return
        evidence.visited++
        val blobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
        for (raw in blobs) {
            if (raw.isBlank() || raw.length > MAX_TEXT_LENGTH) continue
            val lowered = raw.lowercase().trim()
            if (FACTORY_RESET_SCREEN_ERASURE_WORDS.any { phrase ->
                    lowered == phrase || lowered.contains(phrase)
                }
            ) {
                evidence.erasurePerformed = true
            }
        }
        for (index in 0 until node.childCount) {
            collectFactoryResetTextEvidence(node.getChild(index), depth + 1, evidence)
        }
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, counter: Counter, result: ScanResult, label: String) {
        if (node == null || depth > MAX_DEPTH || counter.visited > MAX_NODES) return
        counter.visited++

        node.viewIdResourceName?.let { rawId ->
            val id = rawId.substringAfterLast('/').lowercase()
            if (EXCLUDED_VIEW_IDS.any { id.contains(it) }) result.listSurfaceExcluded = true
            if (STRUCTURAL_VIEW_IDS.any { id.contains(it) }) result.structuralViewId = true
            if (id.contains("uninstall")) result.uninstallSignal = true
            if (id.contains("force_stop") || id.contains("forcestop")) result.forceStopSignal = true
            if (id.contains("clear_data") || id.contains("cleardata") || id.contains("clear_cache")) result.clearDataSignal = true
            if (id.contains("permission")) result.permissionSignal = true
            if (id.contains("accessibility")) result.accessibilitySignal = true
            if (ACCESSIBILITY_TOGGLE_VIEW_IDS.any { id.contains(it) }) result.accessibilitySignal = true
            if (BATTERY_PAGE_VIEW_IDS.any { id.contains(it) }) result.batteryPageSignal = true
            if (AUTOSTART_LIST_VIEW_IDS.any { id.contains(it) }) result.autostartListSignal = true
            if (AUTOSTART_ENTRY_VIEW_IDS.any { id.contains(it) }) result.autostartEntrySignal = true
            if (INSTALLED_APPS_LIST_VIEW_IDS.any { id.contains(it) }) {
                result.installedAppsListSignal = true
                // View-ids alone cannot distinguish Downloaded vs All apps; treat as generic
                // unless text scan later sets downloadedAppsListSignal.
                result.genericAppsListSignal = true
            }
            // Do not mark whole Settings pages from wipe wording — click path only.
        }

        // A switch widget next to this app's identity on a system screen is the structural,
        // language-independent signature of the accessibility/permission toggle surfaces on
        // OEM skins whose container classes (SubSettings …) carry no marker at all.
        val nodeClass = node.className?.toString()
        if (nodeClass != null && (nodeClass.lowercase().contains("switch") ||
                nodeClass.lowercase().contains("togglebutton") ||
                nodeClass.lowercase().contains("slidingbutton"))
        ) {
            result.switchFound = true
            result.switchCount++
        }

        // Settings-search surface marker: an editable field carrying a "search" id — the
        // structural signature present on every OEM Settings search page (language-independent).
        if (!result.settingsSearchSignal && nodeClass != null) {
            val loweredClass = nodeClass.lowercase()
            val idLower = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            val editable = node.isEditable || loweredClass.contains("edittext") ||
                loweredClass.contains("searchview") || loweredClass.contains("autocompletetextview")
            if (editable && (idLower.contains("search") || loweredClass.contains("searchview"))) {
                result.settingsSearchSignal = true
            }
        }
        // Capture the live query text once (the search box is the only editable search node).
        if (result.searchQueryText == null && node.isEditable) {
            val idLower = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            val cl = nodeClass?.lowercase().orEmpty()
            if (idLower.contains("search") || cl.contains("searchview")) {
                result.searchQueryText = node.text?.toString()
            } else if (node.isFocused) {
                // Fallback for exotic skins whose query field carries a generic id: while the
                // settings-search surface hosts exactly one editable field, whatever is focused
                // under the IME right now IS the query box, regardless of its resource name.
                // The gate itself additionally requires sensitive-result wording, so password /
                // free-text fields on ordinary settings pages can never trigger it.
                result.searchQueryText = node.text?.toString()
            }
        }

        // A checked/selected control describing the unrestricted state ("No restrictions",
        // "Allow background activity"…) is the on-page mirror of the system battery whitelist.
        // Used only together with this app's identity on its own battery page.
        if (!result.unrestrictedChecked && (node.isChecked || node.isSelected)) {
            val checkedBlobs = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            for (raw in checkedBlobs) {
                if (raw.length > MAX_TEXT_LENGTH) continue
                val lowered = raw.lowercase()
                if (BATTERY_UNRESTRICTED_WORDS.any { lowered.contains(it) }) {
                    result.unrestrictedChecked = true
                    break
                }
            }
        }

        val text = node.text?.toString()
        val description = node.contentDescription?.toString()
        inspectText(text, result, label)
        inspectText(description, result, label)

        for (index in 0 until node.childCount) {
            walk(node.getChild(index), depth + 1, counter, result, label)
            if (counter.visited > MAX_NODES) return
        }
    }

    private fun inspectText(value: String?, result: ScanResult, label: String) {
        if (value.isNullOrBlank() || value.length > MAX_TEXT_LENGTH) return
        val lowered = value.lowercase()
        // Identity is matched on the normalised form: invisible bidi/format characters are
        // stripped first so App Info / Admin / Accessibility captions stay recognisable when
        // the device language is Arabic, Kurdish, Persian, Turkish, French, or any RTL build.
        val normalized = normalizeForIdentity(lowered)
        val normalizedLabel = normalizeForIdentity(label)
        if (normalized.contains(ownPackage) || lowered.contains(ownPackage)) {
            result.matchesOwnApp = true
            result.packageIdVisible = true
        }
        // Technical component identifiers of this app (accessibility service / device-admin
        // receiver) rendered by some skins as flattened components — identical in every
        // language, so the surface is recognised regardless of localised wording.
        if (!result.matchesOwnApp && OWN_COMPONENT_TOKENS.any { normalized.contains(it) }) {
            result.matchesOwnApp = true
            result.packageIdVisible = true
        }
        // Our declared accessibility-service description, rendered verbatim by every OEM skin —
        // catches Honor/MagicOS generic containers whose labels avoid icon/name widgets.
        if (ownServiceDescriptionNormalized.length >= 8 &&
            normalized.contains(ownServiceDescriptionNormalized)
        ) {
            // This verbatim description is the ONLY content signature that identifies "a page
            // listing/holding THIS app's accessibility service" in every language, at every
            // resume path (Recents, task switcher, notifications), even when the hosting
            // activity class name carries no marker (generic containers, SubSettings…).
            result.matchesOwnApp = true
            result.ownServiceDescriptionVisible = true
        }
        // Label match: require the full application label (not a short fragment) so other apps'
        // App Info pages never inherit identity from a coincidental substring.
        if (normalizedLabel.isNotEmpty() && normalizedLabel.length >= 4 &&
            (normalized.contains(normalizedLabel) || lowered.contains(normalizedLabel))
        ) {
            result.matchesOwnApp = true
        }
        if (EXCLUDED_TEXTS.any { lowered.contains(it) }) result.listSurfaceExcluded = true
        if (DOWNLOADED_APPS_LIST_WORDS.any { lowered.contains(it) }) {
            result.downloadedAppsListSignal = true
            result.installedAppsListSignal = true
        } else if (GENERIC_APPS_LIST_WORDS.any { lowered.contains(it) }) {
            result.genericAppsListSignal = true
            result.installedAppsListSignal = true
        }
        if (UNINSTALL_WORDS.any { lowered.contains(it) }) {
            result.uninstallSignal = true
            result.actionWords++
        }
        if (FORCE_STOP_WORDS.any { lowered.contains(it) }) {
            result.forceStopSignal = true
            result.actionWords++
        }
        if (CLEAR_DATA_WORDS.any { lowered.contains(it) }) {
            result.clearDataSignal = true
            result.actionWords++
        }
        if (PERMISSION_WORDS.any { lowered.contains(it) }) {
            result.permissionSignal = true
            result.actionWords++
        }
        if (DEVICE_ADMIN_WORDS.any { lowered.contains(it) }) result.deviceAdminSignal = true
        if (ACCESSIBILITY_WORDS.any { lowered.contains(it) }) result.accessibilitySignal = true
        if (BATTERY_PAGE_TITLE_WORDS.any { lowered.contains(it) }) result.batteryPageSignal = true
        if (AUTOSTART_LIST_TITLE_WORDS.any { lowered.contains(it) }) {
            result.autostartListSignal = true
            result.autostartEntrySignal = true
        }
        if (SENSITIVE_SEARCH_RESULT_WORDS.any { lowered.contains(it) }) {
            result.searchSensitiveHit = true
        }
        if (APP_INFO_WORDS.any { lowered.contains(it) }) result.actionWords++
        // Title-only signal for the factory-reset screen. The destructive evidence is gathered
        // in a separate, deeper walk by [isFactoryResetScreenByText]; both must agree.
        if (FACTORY_RESET_TITLE_WORDS.any { phrase ->
                lowered == phrase || lowered.contains(phrase)
            }
        ) {
            result.factoryResetTitleSeen = true
        }
    }

    /**
     * Strips invisible bidi / zero-width / format control characters from [raw] (already
     * lowercased by the caller) so identity checks against the package id, the component
     * tokens and the app label stay language- and text-direction-independent.
     */
    private fun normalizeForIdentity(raw: String): String {
        if (raw.isEmpty()) return raw
        val out = StringBuilder(raw.length)
        for (ch in raw) {
            if (ch in IDENTITY_STRIP_CHARS) continue
            out.append(ch)
        }
        return out.toString()
    }

    private class Counter(var visited: Int = 0)

    private class ScanResult(
        var matchesOwnApp: Boolean = false,
        var packageIdVisible: Boolean = false,
        var structuralViewId: Boolean = false,
        var uninstallSignal: Boolean = false,
        var forceStopSignal: Boolean = false,
        var clearDataSignal: Boolean = false,
        var permissionSignal: Boolean = false,
        var deviceAdminSignal: Boolean = false,
        var accessibilitySignal: Boolean = false,
        /** A Switch widget is somewhere in the tree (the accessibility/service toggle). */
        var switchFound: Boolean = false,
        /** This window carries the structural signals of a per-app battery / background page. */
        var batteryPageSignal: Boolean = false,
        /** The unrestricted background option is currently checked/selected on the page. */
        var unrestrictedChecked: Boolean = false,
        /** The window is the general multi-app autostart / app-launch management list. */
        var autostartListSignal: Boolean = false,
        /** The "Background autostart" entry card is visible in the window (id or title). */
        var autostartEntrySignal: Boolean = false,
        /** Switch-like widgets present on screen. The real autostart list shows ≥3 of them. */
        var switchCount: Int = 0,
        /** The current window is a settings SEARCH surface (editable search box in tree). */
        var settingsSearchSignal: Boolean = false,
        /** The live text of the search box (empty while nothing is typed). */
        var searchQueryText: String? = null,
        /** A sensitive non-autostart target is visible among the results (accessibility, downloaded apps). */
        var searchSensitiveHit: Boolean = false,
        /** This app's service description text is visible verbatim in the window tree. */
        var ownServiceDescriptionVisible: Boolean = false,
        var factoryResetSignal: Boolean = false,
        var factoryResetTitleSeen: Boolean = false,
        var actionWords: Int = 0,
        var listSurfaceExcluded: Boolean = false,
        var installedAppsListSignal: Boolean = false,
        /** Generic Apps / All apps / Manage apps heading (PIN-free). */
        var genericAppsListSignal: Boolean = false,
        /** Downloaded apps / sideloaded apps heading (PIN-gated). */
        var downloadedAppsListSignal: Boolean = false,
    ) {
        /**
         * True when the screen shows THIS app together with a destructive action (Uninstall /
         * Force stop / Clear data). That combination is unambiguous: it is an app-management
         * surface that can act on this app, and it must stay guarded on any device, in any
         * covered language, regardless of the hosting class name or view ids.
         */
        val destructiveOwnApp: Boolean
            get() = matchesOwnApp && (uninstallSignal || forceStopSignal || clearDataSignal)
    }

    private class FactoryResetTextEvidence(
        var visited: Int = 0,
        var erasurePerformed: Boolean = false,
    )

    private companion object {
        const val MAX_DEPTH = 16
        const val MAX_NODES = 900
        const val MAX_TEXT_LENGTH = 220

        /** Settings, installer and security-manager packages across supported vendors. */
        /** Pairing + NFC class markers (device handshakes, never a protection tamper). */
        val WIRELESS_CLASS_MARKERS = listOf(
            "bluetoothpairing",
            "bluetoothpairingrequest",
            "bluetoothpairingdialog",
            "bluetoothdevicepicker",
            "bluetoothdialogactivity",
            "bluetoothoberver",
            "bluetoothopp",
            "oppnotification",
            "nfc",
        )

        /** Samsung One UI base navigation containers — shells, not protected surfaces. */
        val ONE_UI_BASE_CONTAINERS = listOf(
            "secsettings2activity",
            "secmainsettingsactivity",
            "subsettings",
            "settingshomepageactivity",
            "settingshomepage",
            "settingclasses",
            "samsungsettings",
        )

        /**
         * Samsung One UI high-risk activity class markers — these are NEVER whitelisted by
         * [isSamsungOneUiBenignSettingsSurface]. They represent surfaces where the user can
         * uninstall, force-stop, or disable the protection app.
         */
        val SAMSUNG_HIGH_RISK_CLASSES = listOf(
            "installedappdetails",
            "appinfodashboard",
            "applicationsdetails",
            "applicationdetails",
            "appinfoactivity",
            "appinfosettings",
            "appdetailactivity",
            "appdetailsactivity",
            "appmanagerdetail",
            "appinfo",
            "appcontrolactivity",
            "appcontrol",
            "appheader",
            "applicationsstate",
            "applicationsettingsactivity",
            "installedappdetailsactivity",
            "appinfofragment",
            "applicationdetailactivity",
        )

        /**
         * Samsung One UI benign settings category markers — always whitelisted by
         * [isSamsungOneUiBenignSettingsSurface]. These are general settings pages that
         * pose no tamper risk to the protection app (WiFi, Sound, Display, etc.).
         */
        val SAMSUNG_BENIGN_CATEGORY_MARKERS = listOf(
            // Connectivity
            "wifi", "wifisettings", "wifip2psettings",
            "bluetoothsettings", "nfcsettings", "vpnsettings",
            "connectionsettings", "connections",
            "airplanemode", "mobiledata", "simsettings",
            // Display & Sound
            "displaysettings", "display", "soundsettings", "sound",
            "volumesettings", "notificationsettings", "notifications",
            "ringtones", "wallpaper", "themesettings",
            // Lock screen & Security category page (specific Device Admin sub-pages are caught by DEVICE_ADMIN_CLASSES)
            "lockscreen", "lockscreenandsecurity", "lockscreenpreferences",
            "screenlock", "biometrics", "biometricsettings",
            "fingerprint", "face", "iris", "smartlock",
            "privacysettings", "privacy",
            // Accounts & Users
            "accounts", "accountsettings", "accountsyncsettings",
            "userandaccounts", "usersettings",
            // Device Care / Maintenance category page (specific battery sub-pages caught by BATTERY classes)
            "devicecare", "devicecaremain", "maintenance",
            "batterysettings", "storagesettings", "storage",
            "datamanagement", "datasync",
            // System
            "aboutphone", "aboutdevice", "softwareupdate",
            "systemsettings", "generalsettings",
            "datetime", "dateandtime", "languageandinput",
            "localepicker", "locationsettings", "location",
            "inputcontrol", "customization", "personalization",
            // Samsung-specific
            "homescreen", "homesettings",
            "mode", "easysettings", "kidssettings",
            "drivingmode", "powersaving", "batterysaver",
            "advancedfeatures", "motionsettings",
            "pen", "spen", "samsungkeyboard",
            "defaultapps", "defaultapplications", "specialaccess",
            "assistandvoiceinput", "searchsettings",
            // AOSP generic settings containers
            "settingsactivity", "settingsfragment",
        )

        val SYSTEM_PACKAGES = setOf(
            // AOSP / Pixel / Motorola / Nokia / Samsung / Xiaomi base
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            // Samsung
            "com.samsung.android.settings",
            "com.samsung.android.packageinstaller",
            "com.samsung.android.permissioncontroller",
            "com.samsung.android.lool",
            // Samsung One UI — LovelyNote / welcome assistant packages seen on Galaxy builds
            // (accessibility detail screens can be hosted there on some ship states).
            "com.samsung.android.lovelynote",
            // ColorOS keeps a separate settings host on several ROM builds.
            "com.coloros.settings",
            // Huawei/Honor per-app optimisation sub-package under System Manager.
            "com.huawei.systemmanager.optimize",
            // Xiaomi / Redmi / POCO (MIUI + HyperOS)
            "com.miui.securitycenter",
            "com.miui.securitycore",
            "com.miui.packageinstaller",
            "com.miui.appmanager",
            "com.lbe.security.miui",
            // OnePlus / Oppo / Realme (OxygenOS + ColorOS)
            "com.oplus.settings",
            "com.oplus.packageinstaller",
            "com.oplus.securitypermission",
            "com.oplus.appdetail",
            "com.coloros.securitypermission",
            "com.coloros.safecenter",
            "com.oppo.safe",
            "com.oneplus.security",
            // Vivo / iQOO
            "com.vivo.settings",
            "com.vivo.packageinstaller",
            "com.vivo.permissionmanager",
            "com.vivo.appstore",
            "com.iqoo.secure",
            // Huawei / Honor
            "com.huawei.systemmanager",
            "com.huawei.packageinstaller",
            "com.hihonor.systemmanager",
            "com.hihonor.packageinstaller",
            // Xiaomi (MIUI / HyperOS) permission & app-manager host — hosts App Info and the
            // runtime permission screens on many HyperOS builds.
            "com.miui.permcenter",
            // Huawei / Honor privacy-permission manager host (EMUI 11+ / MagicOS).
            "com.huawei.privacycenter",
            // Per-app battery / background-management hosts on the major OEM skins. They only
            // ever enter the gate together with this app's identity, so the general battery
            // pages stay untouched.
            "com.miui.powerkeeper",
            "com.huawei.powergenie",
            "com.hihonor.powergenie",
            "com.coloros.powermanager",
            // Honor MagicOS deep settings / internal app hosts (reported bypass surface).
            "com.hihonor.settings",
            "com.hihonor.android.internal.app",
            // Samsung Smart Manager family (battery / auto-run manage flows).
            "com.samsung.android.sm",
            "com.samsung.android.sm.policy",
            // Xiaomi cleaner / security extensions.
            "com.miui.cleanmaster",
            "com.miui.securityadd",
            // ColorOS / RealmeUI / OxygenOS remaining protection hosts.
            "com.oplus.safecenter",
            "com.oplus.battery",
            "com.coloros.oppoguardelf",
            // Vivo security engine.
            "com.vivo.abe",
            // HyperOS 2 split Settings host (seen on some regions/channels) — covered so the
            // settings-search gate fires on its surfaces exactly like the AOSP one.
            "com.miui.settings",
            // NOTE: launchers / home screens are deliberately NOT system surfaces here (see
            // LAUNCHER_PACKAGES below) — they always render this app's label under its icon,
            // so any vague structural signal there used to fire the PIN in an endless loop.
        )

        /**
         * Launcher / home-screen packages. These surfaces are categorically exempt from
         * settings-protection detection: they permanently display this app's label (icon
         * captions, drawer rows), so "identity" means nothing there and any structural hit is a
         * false positive. The real uninstall flow is always confirmed by the system package
         * installer (UninstallerActivity), which remains fully guarded, so dismissing the
         * launcher layer loses nothing.
         */
        val LAUNCHER_PACKAGES = setOf(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.huawei.android.launcher",
            "com.hihonor.android.launcher",
            "com.miui.home",
            "com.coloros.launcher",
            "com.oppo.launcher",
            "com.oneplus.launcher",
            "com.vivo.launcher",
            "com.bbk.launcher2",
            "com.teslacoilsw.launcher",
            "com.microsoft.launcher",
            "com.transsion.launcher",
        )

        /**
         * App stores whose detail pages expose a direct Uninstall affordance for installed
         * apps. Play is covered system-wide while the device admin is active; the OEM stores
         * can remove apps silently, so this app's own page there is PIN-gated by identity +
         * the visible Uninstall action. All other pages of these stores stay untouched.
         */
        val APP_STORE_PACKAGES = setOf(
            "com.android.vending", // Google Play
            "com.sec.android.app.samsungapps", // Galaxy Store
            "com.huawei.appmarket", // Huawei AppGallery
            "com.hihonor.appmarket", // Honor Store
            "com.xiaomi.mipicks", // GetApps (Xiaomi global)
            "com.xiaomi.market", // Xiaomi Market (CN)
            "com.oppo.market", // Oppo App Market
            "com.heytap.market", // HeyTap store (OnePlus / Realme / Oppo)
            "com.bbk.appstore", // Vivo App Store
        )

        /** Fallback markers so unlisted skins are still recognised. */
        val SYSTEM_PACKAGE_MARKERS = listOf(
            ".settings",
            "packageinstaller",
            "permissioncontroller",
            "permissionmanager",
            "securitycenter",
            "securitypermission",
            "securitycore",
            "systemmanager",
            "appmanager",
            "appdetail",
            "safecenter",
            "permcenter",
            "privacycenter",
        )

        /** Activity / fragment names that only exist on an application-detail surface. */
        val DETAIL_CLASSES = listOf(
            "installedappdetails",
            "appinfodashboard",
            "applicationsdetails",
            "applicationdetails",
            "appinfoactivity",
            "appinfosettings",
            "appdetailactivity",
            "appdetailsactivity",
            "appmanagerdetail",
            "appinfo",
            "uninstalleractivity",
            "uninstallappactivity",
            // The uninstall confirmation may render as an alert/verify activity whose class
            // carries no identity text need — class-only marker keeps it language-independent.
            "uninstallalert",
            "uninstallconfirm",
            "apppermission",
            "managepermissions",
            "permissionapps",
            // "subsettings" intentionally removed: OneUI hosts nearly every minor fragment
            // (Wi-Fi, display, sound, …) inside SubSettings, so it misclassified ordinary
            // pages as sensitive app-detail surfaces (field reports of random PIN kicks).
            "appdetail",
        )

        /**
         * Strict App Info activity/fragment names used to reject OTHER apps' App Info pages.
         * Excludes generic containers like SubSettings.
         */
        val APP_INFO_ONLY_CLASSES = listOf(
            "installedappdetails",
            "appinfodashboard",
            "applicationsdetails",
            "applicationdetails",
            "appinfoactivity",
            "appinfosettings",
            "appdetailactivity",
            "appdetailsactivity",
            "appmanagerdetail",
            "appinfo",
            "appdetail",
            "applicationsettingsactivity",
            "installedappdetailsactivity",
            "appinfofragment",
            "applicationdetailactivity",
        )

        /**
         * Device administrator surfaces. Covers AOSP/Pixel, Samsung, Xiaomi (MIUI/HyperOS),
         * Oppo/Realme/OnePlus (ColorOS/OxygenOS) and Vivo (Funtouch/OriginOS).
         */
        val DEVICE_ADMIN_CLASSES = listOf(
            "deviceadminsettings",
            "deviceadminadd",
            "deviceadminlist",
            "deviceadminreceiver",
            "deviceadministrator",
            "devicepolicy",
            "managedeviceadmin",
            "adminsettings",
            "securityadmin",
            "specialaccess",
            // Activity / fragment variants used by AOSP 14+, Samsung One UI and OEM skins.
            "deviceadminaddactivity",
            "device_admin_add",
            "adddeviceadmin",
            "deviceadmininfo",
            // MIUI / HyperOS device-admin manager activity (com.miui.securitycenter hosts).
            "deviceadminmanager",
            "deviceadminmanageractivity",
        )

        /** Application list screens that expose the uninstall action. */
        val MANAGE_APPS_CLASSES = listOf(
            "manageapplicationsactivity",
            "installedappslist",
            "appmanagementactivity",
            "applicationsettings",
            "appmanageractivity",
            "installedapplist",
            "uninstallapps",
            "appslist",
        )

        /**
         * Container view-ids unique to an installed-apps / downloaded-apps list (the screen a
         * user browses to reach this app's App-info → Uninstall / Force-stop). AOSP exposes
         * these; OEM skins reuse similar ids inside a generic container class. Combined with the
         * localized headings it makes the list surface unambiguous across skins.
         */
        val INSTALLED_APPS_LIST_VIEW_IDS = listOf(
            "app_list",
            "apps_list",
            "applist",
            "appslist",
            "installed_apps",
            "installedapps",
            "downloaded",
            "manage_applications",
            "manageapplications",
            "app_manager",
            "appmanager",
            "application_list",
            "applications_list",
            // AOSP / OEM "Downloaded apps" & "Downloaded services" list containers —
            // language-independent resource ids for the sideloaded-management surfaces.
            "downloaded_apps",
            "downloadedapps",
            "downloaded_services",
            "downloadedservices",
            "filtered_apps",
            "apps_filter",
            "service_list",
            "services_list",
        )

        /**
         * Generic Apps list headings — PIN-FREE. Opening Settings → Apps / All apps / Manage apps
         * must not challenge. Distinct from [DOWNLOADED_APPS_LIST_WORDS].
         */
        val GENERIC_APPS_LIST_WORDS = listOf(
            // English
            "all apps", "manage apps", "application manager", "app manager",
            "apps & notifications", "apps and notifications",
            "see all apps", "app list",
            // Arabic — general apps section (NOT downloaded)
            "التطبيقات", "كل التطبيقات", "إدارة التطبيقات", "تطبيقاتي", "البرامج المثبتة",
            "التطبيقات المثبتة", "التطبيقات المثبّتة",
            "التطبيقات والإشعارات", "التطبيقات والاشعارات",
            // Kurdish (Sorani)
            "بەڕێوەبردنی ئەپەکان", "هەموو ئەپەکان", "ئەپەکان",
            // Turkish / common OEM
            "tüm uygulamalar", "uygulamalar", "uygulama yöneticisi",
        )

        /**
         * "Downloaded apps" / sideloaded list — remains PIN-gated (sensitive management path).
         */
        val DOWNLOADED_APPS_LIST_WORDS = listOf(
            // English
            "downloaded apps", "downloaded services", "downloaded applications",
            "installed services",
            // Arabic
            "التطبيقات التي تم تنزيلها", "الخدمات التي تم تنزيلها",
            "التطبيقات المحملة", "التطبيقات المُنزَّلة", "التطبيقات المنزلة",
            // Kurdish (Sorani)
            "ئەپە دابەزێنراوەکان", "ئەپەکانی دابەزراو",
            // Turkish
            "indirilen uygulamalar", "yüklenen uygulamalar",
        )

        /** Union kept for any legacy reference / diagnostics. */
        val INSTALLED_APPS_LIST_WORDS = GENERIC_APPS_LIST_WORDS + DOWNLOADED_APPS_LIST_WORDS

        /** Security settings, the usual parent of the device admin list. */
        val SECURITY_CLASSES = listOf(
            "securitysettings",
            "securitydashboard",
            "securitysettingsactivity",
            "privacysettings",
            "securitycenter",
        )

        /** Localized device-admin wording used as a fallback on skins with generic classes. */
        val DEVICE_ADMIN_WORDS = listOf(
            "device admin", "device administrator", "device administrators",
            "\u0645\u0633\u0624\u0648\u0644 \u0627\u0644\u062c\u0647\u0627\u0632", "\u0645\u0633\u0624\u0648\u0644\u0648 \u0627\u0644\u062c\u0647\u0627\u0632", "\u0645\u062f\u064a\u0631 \u0627\u0644\u062c\u0647\u0627\u0632",
            "\u0628\u06d5\u0695\u06ce\u0648\u06d5\u0628\u06d5\u0631\u06cc \u0626\u0627\u0645\u06ce\u0631", "administrador del dispositivo", "administrateur de l'appareil",
            "ger\u00e4teadministrator", "cihaz y\u00f6neticisi", "\u0430\u0434\u043c\u0438\u043d\u0438\u0441\u0442\u0440\u0430\u0442\u043e\u0440\u044b \u0443\u0441\u0442\u0440\u043e\u0439\u0441\u0442\u0432\u0430",
            "\u8bbe\u5907\u7ba1\u7406\u5668", "\u88dd\u7f6e\u7ba1\u7406\u5668", "\u7aef\u672b\u7ba1\u7406\u30a2\u30d7\u30ea", "\uae30\uae30 \uad00\ub9ac\uc790", "admin perangkat",
        )

        /**
         * Localized wording that identifies an accessibility surface. Used as a content-based
         * fallback on skins (MIUI/HyperOS, ColorOS …) that host the service-detail toggle inside a
         * generic container activity whose class name carries no "accessibility" marker.
         */
        val ACCESSIBILITY_WORDS = listOf(
            "accessibility", "use family shield", "\u0625\u0645\u0643\u0627\u0646\u064a\u0629 \u0627\u0644\u0648\u0635\u0648\u0644", "\u0633\u0647\u0648\u0644\u0629 \u0627\u0644\u0627\u0633\u062a\u062e\u062f\u0627\u0645",
            "\u062a\u0648\u06d5\u0631\u06cc \u062f\u06d5\u0633\u062a\u06ce\u0648\u06d5\u0631\u06cc", "\u062f\u06d5\u0633\u062a\u06ce\u0648\u06d5\u0631\u06cc", "accesibilidad", "accessibilit\u00e9",
            "bedienungshilfen", "eri\u015filebilirlik", "\u0441\u043f\u0435\u0446\u0432\u043e\u0437\u043c\u043e\u0436\u043d\u043e\u0441\u0442\u0438", "\u65e0\u969c\u788d", "\u7121\u969c\u7919",
            "\u30e6\u30fc\u30b6\u30fc\u88dc\u52a9", "\uc811\uadfc\uc131", "aksesibilitas", "\u062f\u0633\u062a\u0631\u0633\u06cc\u200c\u067e\u0630\u06cc\u0631\u06cc",
        )

        val INSTALLED_DETAIL_CLASSES = listOf(
            "installedappdetails", "applicationsdetails", "appmanagerdetail",
            // Samsung One UI hosts the app's controls under AppControlActivity (Galaxy series).
            "appcontrolactivity", "appcontrol",
        )
        val UNINSTALL_CLASSES = listOf(
            "uninstalleractivity",
            "uninstallappactivity",
            "uninstallerfragment",
            "uninstallalert",
            "uninstallconfirm",
        )
        val PERMISSION_CLASSES = listOf("apppermission", "managepermissions", "permissionapps", "permissiondetail")

        /**
         * Surfaces that must never be intercepted: the app list, the runtime permission dialog
         * (this app requests notification access itself) and the accessibility pages that are
         * required to bootstrap protection.
         */
        val EXCLUDED_CLASSES = listOf(
            // "manageapplications" is intentionally NOT excluded: the app list is the usual
            // route to the uninstall button and is now matched by MANAGE_APPS_CLASSES.
            // "accessibility" is intentionally NOT excluded any more: the accessibility settings
            // screen is where the protection service would be turned off, so it is now guarded by
            // ACCESSIBILITY_CLASSES below. It only triggers once a PIN is configured, so the very
            // first time the user enables the service (no PIN yet) it is never intercepted.
            "runningservices",
            "grantpermissionsactivity",
            "grantpermissionsfragment",
            "searchresult",
            "settingssearch",
            "launcher",
            "recents",
            // NOTE (v2.4.141): the battery optimization / saver / background-restriction
            // family is NO LONGER excluded — those pages are a protected lock-point while
            // protection is armed (a child could otherwise silently revoke background work
            // and the watchers die). Only this app's OWN battery page keeps a free Phase-1
            // corridor until "No restrictions" is set, so first-time setup stays completable.
        )

        /**
         * Accessibility settings surfaces: the accessibility list and this app's accessibility
         * service detail/confirmation screen, across AOSP and OEM skins. Guarded so the user
         * cannot disable the protection service without the PIN.
         */
        val ACCESSIBILITY_CLASSES = listOf(
            "accessibilitysettings",
            "accessibilitydetailssettings",
            "accessibilityshortcut",
            "toggleaccessibilityservice",
            "accessibilityserviceconfirmation",
            "accessibilitymenu",
            "installedaccessibility",
            // Android 13+ restricted-accessibility warning activity (enable/service detail)
            // and OEM service-detail hosts. Class-name based — independent of the UI language.
            "accessibilityservicewarning",
            "servicewarning",
            "accessibilityservice",
            "accessibility",
        )

        /**
         * View ids unique to an accessibility service toggle/detail surface. Language-
         * independent: resource ids do not change with the device language, and they only ever
         * gate the surface together with this app's identity (see the call site).
         */
        val ACCESSIBILITY_TOGGLE_VIEW_IDS = listOf(
            "accessibility_service",
            "service_switch",
            "accessibility_toggle",
            "toggle_service",
            "service_toggle",
        )

        /**
         * Unchangeable technical identifiers of THIS app rendered by system security screens —
         * the declaration of the accessibility service and the device-admin receiver is shown
         * by several skins as a flattened component ("pkg/.X" or the simple class name) and is
         * identical in every language, so identity never depends on localized label wording.
         */
        val OWN_COMPONENT_TOKENS = listOf(
            "shieldaccessibilityservice",
            ".accessibility.shieldaccessibilityservice",
            "shielddeviceadminreceiver",
            ".admin.shielddeviceadminreceiver",
        )

        /**
         * Activity / fragment class fragments unique to a PER-APP battery detail or
         * background-restriction screen (AOSP AppBatteryUsage, Samsung app battery page,
         * Huawei/Honor App-launch manager, MIUI power manager …). They gate only together with
         * this app's identity, so system battery screens and other apps' pages never match.
         */
        val OWN_BATTERY_CLASSES = listOf(
            "appbatteryusage",
            "appbattery",
            "batteryusagedetail",
            "apppowerusage",
            "powerusagedetail",
            "appbatterydetail",
            "batterydetailsettings",
            "appbatterysettings",
            "backgroundpower",
            // NOTE: the general multi-app "Background autostart / App launch" LIST classes
            // (startupappcontrol, appstartmgr, autostartmanagement …) are deliberately NOT
            // matched here. That system list must stay open so the user can switch THIS app ON;
            // only a tap that turns this app's own toggle OFF is intercepted (see
            // detectAutostartDisableClick).
        )

        /** View ids seen on per-app battery / background-restriction detail surfaces. */
        val BATTERY_PAGE_VIEW_IDS = listOf(
            "app_battery",
            "appbattery",
            "battery_usage_detail",
            "restrict_background",
            "background_restrict",
            "allow_background",
            "allow_background_activity",
            "battery_saver_choice",
            "bg_power_settings",
        )

        /**
         * Localized titles of the per-app battery page (secondary signal only — the page also
         * carries structural class / view-id markers). Kept short and distinctive so general
         * battery screens alone cannot rely on these.
         */
        val BATTERY_PAGE_TITLE_WORDS = listOf(
            // English
            "app battery usage", "battery usage", "background settings", "background activity",
            "allow background activity", "battery saver",
            // Arabic
            "استخدام البطارية", "استخدام البطارية للتطبيق", "النشاط في الخلفية",
            "إعدادات الخلفية", "موفر البطارية",
            // Kurdish (Sorani)
            "بەکارهێنانی باتری", "کاردانەوەی پاشبنەما", "ڕێکخستنی پاشبنەما",
            // Turkish
            "pil kullanımı", "arka plan etkinliği", "pil tasarrufu",
        )

        /**
         * Wording of the UNRESTRICTED background option. Matched on a checked/selected control
         * to mirror the system battery whitelist on OEM skins, and used as a negative mask in
         * the restrict-click detector ("No restrictions" must never read as a restrict tap).
         */
        val BATTERY_UNRESTRICTED_WORDS = listOf(
            "no restrictions", "unrestricted", "don't optimize", "do not optimize",
            "allow background activity", "allow background", "unrestricted data",
            "السماح بالعمل في الخلفية", "بدون قيود", "بلا قيود", "دون قيود", "لا قيود",
            "السماح بنشاط الخلفية", "غير مقيد", "غیر مقید",
            "بێ سنووردارکردن", "بێ بەرتەسککردن", "بێ سنوور", "ڕێگەپێدان بە پاشبنەما",
            "kısıtlanmamış", "kisitlanmamis", "kısıtlama yok", "kisitlama yok",
            "arka plana izin ver",
        )

        /**
         * Wording of the RESTRICTING options on the own-app battery page. Single words match
         * only as the full label of the clicked control; multi-word phrases may be substrings.
         */
        val BATTERY_RESTRICT_WORDS = listOf(
            "restrict background activity", "restrict background", "restricted mode",
            "battery saver", "optimized", "optimize battery usage",
            "تقييد نشاط الخلفية", "تقييد نشاط التطبيق في الخلفية", "تقييد", "مقيد",
            "موفر البطارية", "موفّر البطارية", "محسّن", "تحسين استخدام البطارية",
            "بەرتەسککردنی پاشبنەما", "پاراستنی باتری", "باشترکراو",
            "arka plan etkinliğini kısıtla", "arka planda kisitla", "pil tasarrufu",
        )

        /**
         * Class fragments of the general multi-app "Background autostart / App launch /
         * Startup manager" lists (MIUI HyperOS permcenter, Huawei / Honor systemmanager,
         * ColorOS, Vivo …). Presence only narrows WHERE autostart click-interception may run;
         * the list surfaces are never gated on open.
         */
        const val AUTOSTART_REVERT_WINDOW_MS = 800L
        const val TOGGLE_REVERT_WINDOW_MS = 800L

        /** Device-admin ADD/activation surfaces (never the deactivate confirmation). */
        val ADMIN_ADD_CLASSES = listOf(
            "deviceadminadd",
            "deviceadminaddactivity",
            "adddeviceadmin",
            "add_device_admin",
            "deviceadminaddconfirm",
        )

        /** Security-center packages that host the "Background autostart" entry card. */
        val AUTOSTART_ENTRY_HOST_PACKAGES = setOf(
            "com.miui.securitycenter",
            "com.miui.appmanager",
            "com.miui.permcenter",
            "com.huawei.systemmanager",
            "com.hihonor.systemmanager",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.iqoo.secure",
        )

        /**
         * Sensitive sub-section activity/fragment names inside those hosts (manage-apps list,
         * autostart page, permission manager, per-app details, uninstall flows). Class-only —
         * the language of the device can never soften them. The list surface keeps its own
         * rules for the autostart toggles (see the click detectors) because they are evaluated
         * in parallel with this gate.
         */
        val SENSITIVE_HOST_SURFACE_CLASSES = listOf(
            "manageapps", "manageapplications", "managemyapps", "appsmanager",
            "appmanager", "appmanagement", "appmanage",
            "autostart", "auto_start", "appstart", "startupmanager", "startmanage",
            "launchmanager", "applaunchmgr",
            // Honor / Huawei "App launch" and startup management hosts (MagicOS & EMUI).
            "startupnormalapp", "startupappcontrol", "startupmgr", "startupapp",
            // Vivo / iQOO autostart + power-exemption detail activities.
            "powerexemption", "ixcstartupware",
            "applicationsdetails", "appdetails", "appinfodashboard", "appdetail",
            "apppermissions", "apppermission", "permissionmanager", "permsummary",
            "appops", "appopsummary", "appopssummaryactivity",
        )

        /**
         * Class carriers of the per-app "background autostart / auto-launch" surfaces across
         * every OEM (Xiaomi/HyperOS, Honor/Huawei, ColorOS/RealmeUI/OxygenOS, Vivo, Samsung,
         * Stock Android). Matched on activity names only — identical in every language.
         */
        val AUTOSTART_TOGGLE_SURFACE_CLASSES = listOf(
            "autostartmanagementactivity", "autostartactivity", "autostartmanager",
            "autostartsettings", "autostartlist", "autostart", "startmanage", "startupmanager",
            "startupapp", "startupnormalapp", "startupappcontrol", "bgstartupmanager",
            "backgroundapprefresh", "backgroundexecution", "applaunch", "backgroundlaunch",
            "applaunchactivity", "applaunchmgr", "powerexemption", "ixcstartupware",
            "sleepingappsactivity", "appinactiveactivity", "apppowerrestriction",
            "restrictbackgroundactivity",
        )

        /** Root dashboards / harmless tools pages that must never raise a PIN. */
        val SECURITY_HOST_FREE_CLASSES = listOf(
            "mainactivity", "maindrawer", "homeactivity", "dashboard", "shomeactivity",
            "mainpageactivity", "mainpage", "securitymain", "cleaneractivity",
            "scanactivity", "datausageactivity", "antivirusactivity",
        )

        /**
         * Per-app detail classes inside the security hosts. Those are gated ONLY for this app
         * (identity scan decides) — every other app's details stay free by contract.
         */
        val SECURITY_HOST_DETAIL_CLASSES = listOf(
            "applicationsdetails", "appdetails", "appinfodashboard", "appdetail",
            "installedappdetails",
        )

        /**
         * Activity/fragment classes that ARE the App Manager section inside the security hosts
         * (Xiaomi/MIUI-HyperOS AppManagerMainActivity, Honor/Huawei systemmanager app-manager,
         * ColorOS/Oppo/OnePlus safecenter app management, Vivo/iQOO secure app lists). Gated at
         * render time (0ms) in every language — class names cannot be localized.
         */
        val SECURITY_HOST_APP_MANAGER_CLASSES = listOf(
            "appmanager", "appmgr", "appmanagement", "manageapps", "manageapplications",
            "appsmanager", "manageappactivity", "installedappslist", "managemyapps",
        )

        /**
         * Activity/fragment classes that ARE the Autostart / auto-launch section inside the
         * security hosts (MIUI/HyperOS StartupManager, EMUI/MagicOS AppLaunchMgr, ColorOS
         * startup pages, Vivo background-start managers). Gated at render time (0ms) —
         * class names are identical in every device language.
         */
        val SECURITY_HOST_AUTOSTART_CLASSES = listOf(
            "autostart", "auto_start", "appstart", "startupmanager", "startmanage",
            "launchmanager", "applaunchmgr", "startupnormalapp", "startupappcontrol",
            "startupmgr", "appstartup", "autoboot",
        )

        /**
         * Activity/fragment classes of the system "Display over other apps" grant screen across
         * AOSP and every OEM skin (the overlay manager never renames these on anyone's build).
         * Used ONLY to exempt that surface from the PIN — see isOverlayGrantSurface.
         */
        val OVERLAY_PERMISSION_CLASSES = listOf(
            "appdrawoverlay", "appdrawoverlaysettings", "drawoverlay", "drawoverotherapps",
            "manageoverlay", "manageoverlayapps", "overlaydetai", "overlaydetail",
            "overlaypermission", "alertwindow", "alertwindows", "alertwindowcall",
            "systemalertwindow",
        )

        /**
         * Multilingual labels of the "App Manager" entry tile inside security-center
         * dashboards (Xiaomi/HyperOS, Honor, Huawei, Oppo/OnePlus/Realme, Vivo/iQOO, Samsung).
         * Matched ONLY against the tapped row / click payload — never page-wide — so cleaner,
         * antivirus and battery tiles on the same dashboard can never trip it.
         */
        val SECURITY_APP_MANAGER_ENTRY_WORDS = listOf(
            // English
            "app manager", "manage apps", "manage applications", "application manager",
            "app management", "apps manager",
            // Arabic
            "إدارة التطبيقات", "ادارة التطبيقات", "إدارة التطبيق", "اداره التطبيقات",
            "مدير التطبيقات",
            // Kurdish (Sorani)
            "بەڕێوەبردنی ئەپەکان", "بەڕێوەبردنی ئەپ",
            "ئەپەکان",
            // Persian
            "مدیریت برنامه", "مدیریت برنامه ها", "مدیریت برنامه‌ها",
            // Turkish
            "uygulama yöneticisi", "uygulamaları yönet", "uygulama yönetimi",
            // Chinese / Japanese / Korean OEM builds
            "应用管理", "應用管理", "アプリ管理", "앱 관리",
        )

        /**
         * View-id tokens of the Ultra/Super/Extreme power-saving quick-settings tile and switch,
         * across AOSP SystemUI and the OEM forks (MIUI/HyperOS, One UI, EMUI/MagicOS, ColorOS,
         * OriginOS). Structural — identical in every language.
         */
        val ULTRA_PS_TILE_VIEW_IDS = listOf(
            "ultra_power", "ultra_saving", "ultra_powersave",
            "super_power_save", "superpowersave", "super_power_saving", "supersaving",
            "extreme_power_saving", "extreme_battery_saver",
            "max_power_saving", "maximum_power_saving",
        )



        /**
         * Localized labels of the ULTRA / EXTREME / SUPER / MAX power-saving toggle. Every entry
         * carries its qualifier, so the ordinary "Battery Saver / توفير الطاقة" toggle can never
         * match. Matched against the tapped tile/row and click payload only.
         */
        val ULTRA_PS_ENTRY_WORDS = listOf(
            // English
            "ultra power saving", "ultra power save", "ultra battery saver",
            "extreme battery saver", "max power saving", "maximum power saving",
            "super power saving", "super power save", "super power saver", "super battery saver",
            "extreme power saving", "maximum battery saving",
            // Arabic (both Alif-Hamza shapes)
            "توفير الطاقة الفائق", "موفر الطاقة الفائق", "التوفير الفائق للطاقة",
            "التوفير الفائق", "التوفير الأقصى", "التوفير الاقصى",
            "موفر الطاقة الأقصى", "موفر الطاقة الاقصى", "توفير الطاقة الأقصى",
            "الوضع الفائق لتوفير الطاقة",
            // Kurdish (Sorani) — user-supplied wording plus common renderings
            "توفیری تاقەی فایق", "موفر الطاقة الفائق",
            "دۆخی ڕزگارکردنی باتری", "توفیری باتری فائق", "پاراستنی باتریی فائق",
            // Persian (both ZWNJ and plain-space shapes)
            "حالت فوق صرفه جویی", "صرفه جویی فوق العاده", "ذخیره باتری فوق العاده",
            "حالت صرفه‌جویی فوق‌العاده", "صرفه جويي فوق‌العاده",
            // Turkish
            "ultra güç tasarrufu", "aşırı pil tasarrufu", "aşırı pıl tasarrufu", "süper güç tasarrufu",
            "maksimum güç tasarrufu",
            // Chinese (simplified + traditional)
            "超级省电", "超级节电", "超級省電", "超省電", "极限省电", "極限省電",
            // Japanese / Korean
            "スーパー節電", "ウルトラ節電", "超省電力モード", "초절전", "초절전 모드", "최대 절전",
            // Russian
            "режим максимального энергосбережения", "максимального энергосбережения",
            "режим ультраэнергосбережения", "суперэкономия",
            // German
            "ultimativer energiesparmodus", "extremer energiesparmodus",
            // Spanish
            "ahorro de energía ultra", "ahorro de batería extremo", "máximo ahorro de energía",
            // French
            "économie d'énergie ultra", "économie d'énergie extrême", "économie d'énergie maximale",
        )

        /**
         * Search-host activity classes across OEM Settings builds (AOSP, MIUI/HyperOS, EMUI /
         * MagicOS, One UI, ColorOS, OriginOS). Class-name based, so it is identical in every
         * device language and never depends on a localized label.
         */
        val SEARCH_HOST_CLASSES = listOf(
            "searchresult",
            "settingssearch",
            "searchactivity",
            "settingssearchactivity",
            "searchappactivity",
            "searchhome",
            "searchcenter",
            "searchsettings",
            "search_page",
        )

        /** View-id tokens naming the autostart entry / target inside those dashboards. */
        val AUTOSTART_ENTRY_VIEW_IDS = listOf(
            "autostart",
            "auto_start",
            "app_start",
            "appstart",
            "startmanage",
            "start_manage",
            "launch_manager",
            "app_launch",
            // Icon / card variants some skins give the search-result or dashboard tile.
            "ic_autostart",
            "autostart_entry",
            "entry_autostart",
            "item_autostart",
            "card_autostart",
            "auto_start_entry",
            "power_start",
            "quick_autostart",
        )

        val AUTOSTART_LIST_CLASSES = listOf(
            "autostart",
            "autostartmanagement",
            "appstart",
            "appstartmgr",
            "startupmanager",
            "startupappcontrol",
            "startmanage",
            "launchmanager",
            "applaunchmgr",
        )


        /** View ids seen on autostart / startup-management list containers. */
        val AUTOSTART_LIST_VIEW_IDS = listOf(
            "autostart",
            "auto_start",
            "app_start",
            "appstart",
            "startup_list",
            "start_manage",
            "startmanage",
            "launch_manager",
            "app_launch",
        )

        /** Localized titles of the autostart list (secondary narrowing signal only). */
        val AUTOSTART_LIST_TITLE_WORDS = listOf(
            // English
            "background autostart", "autostart", "auto-launch", "auto launch",
            "app launch", "startup manager", "app launch management",
            // Arabic
            "التشغيل التلقائي", "بدء التشغيل التلقائي", "التشغيل التلقائي في الخلفية",
            "إدارة تشغيل التطبيقات",
            // Kurdish (Sorani)
            "کردنەوەی خۆکار", "خۆکارکردن", "دەستپێکردنی خۆکار",
            // Kurdish (Sorani) — extended autostart vocabulary (both Kaf shapes)
            "دەستپێكردنی خۆکار", "ئۆتۆستارت", "دەستپێکی خۆکار",
            // Arabic — additional self-start synonyms
            "التشغيل الذاتي", "البدء التلقائي", "البدء الذاتي", "تشغيل تلقائي للتطبيقات",
            "الإطلاق التلقائي", "الاطلاق التلقائي", "إدارة بدء التشغيل",
            // English alternates
            "auto-start", "automatic start", "launch management", "launch app management",
            "auto start",
            // Turkish
            "otomatik başlatma", "otomatik baslatma",
            // Persian / Urdu / Hindi
            "اجرای خودکار", "راه‌اندازی خودکار", "خودکار آغاز", "स्वतः प्रारंभ",
            "شروع خودکار", "آغاز خودکار", "حالت شروع خودکار",
            // Chinese / Japanese / Korean
            "自启动管理", "开机自启", "开机自启动", "应用启动管理", "自動起動", "자동 실행",
        )



        /**
         * Sensitive settings destinations that must never be *suggested* by search while
         * protection is armed: downloaded-apps/accessibility management entries. Only ever
         * consulted on a search surface with a non-empty query, so normal pages are unaffected.
         */
        /**
         * Per-app battery usage/detail page classes. Identity-gated: third-party apps always
         * pass, only THIS app's page is a lock-point (after its Phase-1 setup corridor).
         */
        val BATTERY_PER_APP_SURFACE_CLASSES = listOf(
            "appbatteryusage", "appbattery", "apppowerusage", "batteryusagedetail",
            "powerusagedetail", "appbatterydetail", "batterydetailsettings", "appbatterysettings",
        )

        /**
         * System-wide battery-management activity classes (saver, device care, optimization
         * list, power keepers…). These are always gated while protection is armed, because a
         * single toggle there quietly throttles the background services.
         */
        val BATTERY_SYSTEM_SURFACE_CLASSES = listOf(
            "batterysaveractivity", "batterysaver", "batterysettingsactivity", "batterysettings",
            "batteryoptimization", "ignorebatteryoptimizations", "highpowerapplications",
            "optimizebattery",
        )

        /**
         * Multi-word battery phrases only (NEVER a bare "battery" — that word also appears on
         * the settings home row and would lock the whole app). Consulted on the gate and the
         * search-interception rule; covers the languages actually shipped by the OEMs.
         */
        val BATTERY_SURFACE_WORDS = listOf(
            "battery optimization", "battery optimisation", "battery saver",
            "app battery usage", "optimizing battery", "optimize battery",
            "ignore battery optimizations", "background restriction", "background restrictions",
            "battery usage details", "background activity", "battery usage",
            "تحسين البطارية", "تحسين استهلاك البطارية", "تحسين استخدام البطارية",
            "موفر البطارية", "موفّر البطارية", "قيود الخلفية للبطارية", "قيود الخلفية",
            "استخدام البطارية للتطبيق", "استخدام البطارية",
            "ئۆپتیمایزکردنی باتری", "بەکارهێنانی باتری", "پاراستنی باتری",
            "سنووردارکردنی پاشبنەما", "ڕێکخستنی وزە",
            "pil tasarrufu", "pil optimizasyonu", "arka plan kısıtlaması",
            "بهینه‌سازی باتری", "صرفه‌جویی باتری", "محدودیت پس‌زمینه",
            "économiseur de batterie", "optimización de batería", "akkuspar",
            "energiesparmodus", "экономия заряда", "电池优化", "バッテリー最適化", "배터리 최적화",
        )

        val SENSITIVE_SEARCH_RESULT_WORDS = AUTOSTART_LIST_TITLE_WORDS + BATTERY_SURFACE_WORDS + listOf(
            // Downloaded apps / services (the accessibility-management hop on many skins)
            "downloaded apps", "downloaded services", "downloaded applications",
            "التطبيقات التي تم تنزيلها", "الخدمات التي تم تنزيلها", "التطبيقات المنزلة",
            "ئەپە دابەزێنراوەکان", "ئەپەکانی دابەزراو",
            "indirilen uygulamalar", "بارگیری‌شدە", "برنامه‌های دانلود",
            // Scheduled power on/off (turning the device off on a schedule would pause all
            // protection — searched on some skins as its own sensitive destination).
            "جدولة التشغيل", "جدولة التشغيل/الإيقاف", "تشغيل وإيقاف مجدول",
            "التشغيل التلقائي والإيقاف", "schedule power on", "schedule power off",
            "scheduled power on", "power on and off schedule", "automatic power on off",
            "planned power on", "تشغيل تلقائي أو إيقاف", "تشغيل تلقائي وإيقاف",
            // Accessibility wording (labels already exist in ACCESSIBILITY_WORDS but a shared
            // pointer keeps this list self-contained for the search gating rule)
            "accessibility", "إمكانية الوصول", "سهولة الاستخدام", "دەستەیی",
            "accessibilité", "accesibilidad", "erişilebilirlik", "دسترسی‌پذیری",
            "специальные возможности", "无障碍", "アクセシビリティ", "접근성",
            // Installed / running / downloaded services naming across skins (the accessibility
            // management hop): extended multilingual coverage so search surfaces them at 0ms.
            "installed services", "running services", "downloaded services",
            "الخدمات الجاري تشغيلها", "الخدمات المنزلة", "الخدمات المنزّلة", "الخدمات الجارية",
            "الخدمات المثبتة", "الخدمات المثبتّة",
            "خزمەتگوزارییە دابەزێنراوەکان", "خزمەتگوزاری دابەزێنراو", "خزمەتگوزارییەکانت دابەزێنراو",
            "karîzdiger hizmetler", "çalışan hizmetler", "yüklü hizmetler", "indirilen hizmetler",
            "خدمات دانلود شده", "خدمات در حال اجرا", "خدمات نصب شده",
            "已安装的服务", "正在运行的服务", "ダウンロードしたサービス", "설치된 서비스",
            // Special-access entry wording (Settings → Special app access family on OEM skins).
            "special access", "special app access", "دەستگەیشتنی تایبەت", "دەستگەیشتن",
            "وصول خاص", "الوصول الخاص", "وصول التطبيقات الخاص", "özəl giriş", "özelleşmiş erişim",
            "دسترسی ویژه", "特殊访问", "特殊应用权限", "特別なアクセス", "특별한 접근",
        )


        /**
         * Bidi / zero-width / format control characters that OEM skins embed inside captions
         * when the device language is RTL (Arabic, Kurdish, Persian …) or when a label is
         * mirrored. They are invisible to the user but break plain `contains` identity checks.
         */
        val IDENTITY_STRIP_CHARS = setOf(
            '\u200B', '\u200C', '\u200D', '\u200E', '\u200F',
            '\u2028', '\u2029', '\u202A', '\u202B', '\u202C', '\u202D', '\u202E',
            '\u2066', '\u2067', '\u2068', '\u2069', '\u061C', '\uFEFF',
        )

        val STRUCTURAL_VIEW_IDS = listOf(
            "entity_header",
            "app_snippet",
            "app_detail",
            "appdetail",
            "app_info",
            "appinfo",
            "uninstall_button",
            "force_stop",
            "forcestop",
            "clear_data",
            "cleardata",
            "app_manager_detail",
            "button_uninstall",
            "action_buttons",
        )

        // "accessibility_switch" is intentionally NOT excluded any more: it is the toggle used to
        // turn this app's protection service off, which is exactly what must now be guarded.
        val EXCLUDED_VIEW_IDS = listOf(
            "search_src_text", "app_list_container",
            // Battery screen containers — must not trigger PIN.
            "battery_saver", "battery_settings", "battery_info", "battery_usage",
            "power_usage", "battery_optimization", "smart_battery", "battery_mode",
            "power_mode", "battery_care", "battery_protect", "power_saving",
        )

        val EXCLUDED_TEXTS = listOf(
            "accessibility shortcut", "downloaded apps", "all apps",
            // Battery-related screen titles — must never trigger PIN lock.
            "battery saver", "battery settings", "battery info", "battery usage",
            "power usage", "battery optimization", "optimize battery", "smart battery",
            "battery mode", "power mode", "power center", "battery care", "battery protect",
            "battery and device care", "device maintenance", "power saving", "power saving mode",
            "حافظ البطارية", "إعدادات البطارية", "معلومات البطارية", "استخدام البطارية",
            "تحسين البطارية", "وضع توفير الطاقة", "وضع الطاقة", "رعاية البطارية",
            "حفظ البطارية", "إدارة البطارية",
            "پاراستنی باتری", "ڕێکخستنی باتری", "زانیاریی باتری", "بەکارهێنانی باتری",
            "ئۆپتیمایزی باتری", "دۆخی پاراستنی باتری", "دۆخی وزە", "ڕێکخستنی وزە",
        )

        /** Localized labels for the destructive actions, covering common device languages. */
        val UNINSTALL_WORDS = listOf(
            "uninstall", "إلغاء التثبيت", "إزالة التثبيت", "لابردن", "لابردنی ئەپ",
            "desinstalar", "désinstaller", "deinstallieren", "disinstalla", "kaldır",
            "удалить", "अनइंस्टॉल", "卸载", "解除安裝", "アンインストール", "제거",
            "hapus instalasi", "حذف نصب", "desinstalação",
        )

        val FORCE_STOP_WORDS = listOf(
            "force stop", "force-stop", "فرض الإيقاف", "إيقاف قسري", "إيقاف التشغيل", "إيقاف التطبيق",
            "ڕاگرتنی بەزۆر", "ڕاگرتنی بەپەلە", "forzar detención", "forzar parada",
            "forcer l'arrêt", "arrêter de force", "beenden erzwingen", "zorla durdur",
            "остановить", "принудительно остановить", "फ़ोर्स स्टॉप", "强行停止",
            "強制停止", "強制終了", "강제 중지", "강제 종료",
            "paksa berhenti", "توقف اجباری",
        )

        val CLEAR_DATA_WORDS = listOf(
            "clear data", "clear storage", "clear cache", "محو البيانات", "مسح البيانات",
            "سڕینەوەی داتا", "borrar datos", "effacer les données", "daten löschen",
            "verileri temizle", "очистить данные", "डेटा साफ़", "清除数据", "清除資料",
            "データを削除", "데이터 삭제", "hapus data", "پاک کردن داده",
        )

        val PERMISSION_WORDS = listOf(
            "permissions", "app permissions", "الأذونات", "أذونات التطبيق", "مۆڵەتەکان",
            "permisos", "autorisations", "berechtigungen", "izinler", "разрешения",
            "अनुमतियां", "权限", "權限", "権限", "권한", "izin aplikasi", "مجوزها",
        )

        val APP_INFO_WORDS = listOf(
            "app info", "application info", "app details", "storage & cache", "storage and cache",
            "open by default", "معلومات التطبيق", "تفاصيل التطبيق", "زانیاری ئەپ",
            "información de la aplicación", "informations sur l'application", "app-info",
            "uygulama bilgileri", "о приложении", "ऐप्लिकेशन की जानकारी", "应用信息", "應用資訊",
            "アプリ情報", "앱 정보", "info aplikasi", "اطلاعات برنامه",
        )

        /** Mixed "Reset" menus (network / BT / accessibility / all settings) — never PIN on open. */
        val FACTORY_RESET_LIST_CLASSES = listOf(
            "resetdashboard",
            "resetdashboardfragment",
            "resetsettings",
            "resetoptions",
            "resetnetwork",
            "resetbluetooth",
            "resetaccessibility",
        )

        /** Dedicated erase/factory-reset screens after choosing that one list row. */
        val FACTORY_RESET_ENTRY_CLASSES = listOf(
            "masterclear",
            "masterclearsettings",
            "factoryreset",
            "factoryresetactivity",
            "erasealldata",
            "eraseactivity",
            "erasedata",
            "wipealldata",
            "hardreset",
            "phonereset",
            "devicereset",
            "mainclear",
            "secfactoryreset",
            "samsungfactoryreset",
        )

        val FACTORY_RESET_CONFIRM_CLASSES = listOf(
            "masterclearconfirm",
            "factoryresetconfirm",
            "eraseconfirm",
            "erasealldataconfirm",
            "wipeconfirm",
            "hardresetconfirm",
            "confirmmasterclear",
        )

        val FACTORY_RESET_ACTION_VIEW_IDS = listOf(
            "initiate_master_clear",
            "master_clear_button",
            "master_clear",
            "execute_reset",
            "btn_factory_reset",
            "factory_reset_button",
            "erase_button",
            "btn_erase",
            "reset_device_button",
            "sec_reset_button",
        )

        /** Soft resets on the same list as factory reset (must not trigger PIN alone). */
        val FACTORY_RESET_SOFT_RESET_WORDS = listOf(
            "reset mobile network",
            "reset bluetooth",
            "reset wi-fi",
            "reset wifi",
            "reset accessibility",
            "reset all settings",
            "reset network",
            "إعادة ضبط إعدادات الشبكة",
            "إعادة ضبط البلوتوث",
            "إعادة ضبط إمكانية الوصول",
            "إعادة ضبط كل الإعدادات",
        )

        /**
         * Labels that unambiguously mean wiping the *device* itself — every entry carries a
         * factory/reset/phone marker (Samsung: "Erase all data (factory reset)").
         * Not bare "Reset" title of the parent screen. These fire on click without extra context.
         */
        val FACTORY_RESET_STRONG_WORDS = listOf(
            "erase all data (factory reset)",
            "factory data reset",
            "factory reset",
            "reset phone",
            "reset tablet",
            "reset device",
            "erase device",
            "delete all data from phone",
            "erase all data from your phone",
            "erase all data and reset",
            "إعادة ضبط المصنع",
            "اعادة ضبط المصنع",
            "إعادة ضبط الجهاز",
            "اعادة ضبط الجهاز",
            "استعادة إعدادات المصنع",
            "استعادة اعدادات المصنع",
            "ڕێکخستنەوەی کارگە",
            "ڕیسێتی کارگە",
            "fabrika ayarlarına sıfırla",
            "fabrika ayarlarina sifirla",
            "cihazı sıfırla",
            "telefonu sıfırla",
            "réinitialisation d'usine",
            "reinitialisation d'usine",
            "restablecer datos de fábrica",
            "auf werkseinstellungen zurücksetzen",
            "reset pabrik",
            "恢复出厂设置",
            "工場出荷時にリセット",
            "공장 초기화",
            "сброс до заводских",
            "بازنشانی کارخانه",
        )

        /**
         * Generic "erase/delete all data" labels — the exact same wording is shown by per-app
         * storage dialogs (App info → Storage → Clear data) of *any* app on the device. Accepted
         * only when a [FACTORY_RESET_CONTEXT_WORDS] marker accompanies the click, so clearing
         * Chrome/YouTube data never prompts this app's PIN.
         */
        val FACTORY_RESET_STORAGE_WORDS = listOf(
            "erase all data",
            "delete all data",
            "wipe all data",
            "erase all data permanently",
            "مسح كل البيانات",
            "محو جميع البيانات",
            "محو كل البيانات",
            "حذف جميع البيانات",
            "سڕینەوەی هەموو داتاکان",
            "سڕینەوەی تەواوی داتا",
            "tüm verileri sil",
            "tum verileri sil",
            "effacer toutes les données",
            "borrar todos los datos",
            "alle daten löschen",
            "hapus semua data",
            "清除所有数据",
            "すべてのデータを消去",
            "모든 데이터 삭제",
        )

        /**
         * Markers proving the click happens inside a phone-wipe / factory-reset flow (screen
         * title, row sub-text or parent container holding wording such as "factory" / "reset" /
         * "إعادة ضبط المصنع"). Used to upgrade generic storage wording into a wipe action.
         */
        val FACTORY_RESET_CONTEXT_WORDS = listOf(
            "factory", "reset", "master clear", "masterclear", "wipe",
            "إعادة ضبط", "اعادة ضبط", "ضبط المصنع", "المصنع",
            "ڕیسێت", "ريسێت", "کارگە",
            "sıfırla", "sifirla",
            "usine", "fábrica", "fabrica", "werkseinstellung", "pabrik", "restablecer",
            "出厂", "重置", "工場出荷", "공장", "заводск", "بازنشانی",
        )

        val SYSTEM_UPDATE_HOST_PACKAGES = setOf(
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.google.android.apps.nbu.files",
            "com.android.fileexplorer",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.gms",
            "com.android.settings",
        )

        val SYSTEM_UPDATE_CLASSES = listOf(
            "alertdialog",
            "dialog",
            "documentsui",
            "file",
            "delete",
            "confirm",
        )

        // Only ids that are unambiguously system-update specific. Generic dialog ids
        // ("button1", "delete_button", "btn_delete") exist in EVERY Android confirmation
        // dialog — including the uninstall dialog of OTHER apps — so they must never mark a
        // surface as a system-update delete on their own; the real DocumentsUI dialog is
        // still caught by its localized wording below.
        val SYSTEM_UPDATE_VIEW_IDS = listOf(
            "system_update",
            "delete_ota",
            "ota_update",
        )

        /** Wording for the DocumentsUI "Delete system update?" dialog and OEM variants. */
        /**
         * Title-only wording that locates the dedicated factory-reset screen on OEM skins
         * which wrap the activity in a generic container class. Used together with
         * [FACTORY_RESET_SCREEN_ERASURE_WORDS] so the general "Reset" list (whose title is
         * merely "Reset" / "Reset your phone") never matches.
         */
        val FACTORY_RESET_TITLE_WORDS = listOf(
            "factory reset",
            "factory data reset",
            "reset phone",
            "reset device",
            "reset tablet",
            "erase all data (factory reset)",
            "إعادة ضبط المصنع",
            "اعادة ضبط المصنع",
            "إعادة ضبط الجهاز",
            "اعادة ضبط الجهاز",
            "استعادة إعدادات المصنع",
            "استعادة اعدادات المصنع",
            "سيستەمی ڕیسێت", "ڕیسێتی کارگە",
            "fabrika ayarlarina sifirla",
            "fabrika ayarlarına sıfırla",
            "réinitialisation d'usine",
            "reinitialisation d'usine",
            "restablecer datos de fábrica",
            "auf werkseinstellungen zurücksetzen",
            "reset pabrik",
            "恢复出厂设置",
            "工場出荷時にリセット",
            "공장 초기화",
            "сброс до заводских",
            "بازنشانی کارخانه",
        )

        /**
         * Confirmation action wording that must accompany the title to qualify. The single
         * presence of the title alone is not enough: the parent "Reset" dashboard contains
         * the same title.
         */
        val FACTORY_RESET_SCREEN_ERASURE_WORDS = listOf(
            "erase all data",
            "delete all data",
            "wipe all data",
            "erase all data permanently",
            "erase the following items",
            "erase all data and reset",
            "مسح كل البيانات",
            "محو جميع البيانات",
            "محو كل البيانات",
            "حذف جميع البيانات",
            "سڕینەوەی هەموو داتاکان",
            "سڕینەوەی تەواوی داتا",
            "tüm verileri sil",
            "tum verileri sil",
            "effacer toutes les données",
            "borrar todos los datos",
            "alle daten löschen",
            "hapus semua data",
            "清除所有数据",
            "すべてのデータを消去",
            "모든 데이터 삭제",
        )

        val SYSTEM_UPDATE_DELETE_WORDS = listOf(
            // English (exact title from the screenshot)
            "delete system update",
            "delete system update?",
            "remove system update",
            "delete the system update",
            "this will delete the system update",
            "system update package",
            "delete ota",
            "delete update package",
            "erase system update",
            // Arabic
            "حذف تحديث النظام",
            "حذف تحديث النظام؟",
            "إزالة تحديث النظام",
            "مسح تحديث النظام",
            "حذف حزمة التحديث",
            // Kurdish
            "سڕینەوەی نوێکردنەوەی سیستەم",
            "سڕینەوەی ئەپدەیتی سیستەم",
            "delete system update",
            // Turkish / common OEM
            "sistem güncellemesini sil",
            "sistem guncellemesini sil",
            // French / Spanish / German
            "supprimer la mise à jour système",
            "supprimer la mise a jour systeme",
            "eliminar actualización del sistema",
            "systemupdate löschen",
            "systemupdate loschen",
            // Chinese / Russian / Persian
            "删除系统更新",
            "删除系统更新？",
            "удалить обновление системы",
            "حذف به‌روزرسانی سیستم",
        )

        /** Packages that host the power menu / reboot UI where Safe Mode is offered. */
        val SAFE_MODE_HOST_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.systemui",
            "com.miui.systemui",
            "com.android.settings",
            // Xiaomi HyperOS + secondary SystemUI hosts
            "com.miui.securitycenter",
            // Vivo / iQOO (Funtouch / OriginOS)
            "com.vivo.systemui",
            "com.android.systemui.vivo",
            // Oppo / Realme / OnePlus (ColorOS / RealmeUI / OxygenOS)
            "com.oppo.systemui",
            "com.coloros.systemui",
            "com.oplus.systemui",
            "com.realme.systemui",
            "com.oneplus.systemui",
            // Huawei / Honor (EMUI / MagicOS)
            "com.huawei.systemui",
            "com.hihonor.systemui",
            // Motorola / Lenovo
            "com.motorola.systemui",
            // Transsion (Tecno / Infinix / itel)
            "com.transsion.systemui",
        )

        val SAFE_MODE_HOST_MARKERS = listOf(
            "systemui",
            "globalactions",
            "powermenu",
            "powerui",
            // OEM power/shutdown hosts that carry these fragments in the package name.
            "shutdown",
            "poweroff",
            "poweractions",
        )

        val SAFE_MODE_CLASSES = listOf(
            "globalactions",
            "globalactionsdialog",
            "globalactionsdialoglite",
            "miuiglobalactions",
            "shutdownui",
            "safemode",
            "safe_mode",
            "rebootconfirmdialog",
            "poweroff",
        )

        val POWER_MENU_CLASSES = listOf(
            "globalactions",
            "globalactionsdialog",
            "globalactionsdialoglite",
            "miuiglobalactions",
            "shutdownui",
            "rebootconfirmdialog",
            "poweroff",
            "shutdown",
            "restart",
            "globalaction",
        )

        val POWER_MENU_VIEW_IDS = listOf(
            "global_actions",
            "globalactions",
            "power_off",
            "poweroff",
            "restart",
            "reboot",
            "shutdown",
            "btn_power_off",
            "btn_restart",
        )

        /**
         * Structural ids of the notification shade / quick-settings panel across AOSP and OEM
         * skins. A subtree under any of these ids is skipped entirely by the power-menu scan.
         */
        val QUICK_SETTINGS_VIEW_IDS = listOf(
            "quick_settings",
            "quick_qs",
            "qs_panel",
            "qs_content",
            "qs_header",
            "qs_tile",
            "tile_layout",
            "tile_label",
            "notification_stack",
            "notification_panel",
            "notification_shade",
            "status_bar",
            "brightness",
        )

        /**
         * Power off / Restart labels. Avoid bare "power" alone so notification shade / battery
         * tiles are not matched. Bare state-words meaning just "Off" (إيقاف, کوژاندنەوە, kapat)
         * are intentionally absent: quick-settings tiles (flashlight, data …) expose exactly that
         * word as their state/toggle label, which used to mis-flag the notification shade as the
         * power menu and raise the PIN when the user merely toggled the torch.
         */
        val POWER_MENU_WORDS = listOf(
            // English
            "power off", "poweroff", "turn off", "shut down", "shutdown", "shut down device",
            "restart", "reboot", "restart phone", "restart device", "reboot device",
            "power off / restart", "power menu",
            // Arabic
            "إيقاف التشغيل", "ايقاف التشغيل", "إيقاف الجهاز", "ايقاف الجهاز",
            "إعادة التشغيل", "اعادة التشغيل", "إعادة تشغيل", "اعادة تشغيل",
            "إيقاف الطاقة", "ايقاف الطاقة",
            // Kurdish
            "کوژاندنەوەی ئامێر", "کوژاندنەوەی مۆبایل", "دووبارە هەڵکردنەوە",
            // Turkish
            "cihazı kapat", "yeniden başlat", "yeniden baslat", "telefonu kapat",
            // French / Spanish / German / Indonesian
            "éteindre", "eteindre", "redémarrer", "redemarrer",
            "apagar", "reiniciar",
            "ausschalten", "neu starten", "neustart",
            "matikan", "mulai ulang",
            // Chinese / Japanese / Korean / Russian / Persian
            "关机", "重新启动", "重启",
            "電源を切る", "再起動",
            "전원 끄기", "다시 시작",
            "выключить", "перезагрузить",
            "خاموش کردن", "راه‌اندازی مجدد", "راه اندازی مجدد",
        )

        /**
         * Localized "Safe mode" labels + common confirmation copy. Matched as substring so OEM
         * strings like "Reboot to safe mode" still hit.
         */
        val SAFE_MODE_WORDS = listOf(
            // English (AOSP / Samsung / Pixel / OEM variants + confirmation copy)
            "safe mode", "safemode", "safe boot", "reboot to safe mode", "restart in safe mode",
            "boot to safe mode", "enter safe mode", "reboot in safe mode", "restart to safe mode",
            "reboot to safe mode?", "reboot device to safe mode",
            "your device will restart in safe mode",
            "tap ok to reboot into safe mode", "do you want to reboot into safe mode",
            "third-party apps will be disabled", "third party apps will be disabled",
            // Arabic (+ common confirmation phrasing across OEMs)
            "الوضع الآمن", "وضع الامان", "وضع الأمان", "الوضع الامن", "الوضع الآمِن",
            "إعادة التشغيل في الوضع الآمن", "اعادة التشغيل في الوضع الامن",
            "إعادة التشغيل إلى الوضع الآمن", "اعادة التشغيل الى الوضع الامن",
            "هل تريد إعادة التشغيل في الوضع الآمن", "التمهيد في الوضع الآمن",
            "سيتم تعطيل التطبيقات", "سيتم إيقاف تطبيقات الطرف الثالث",
            // Kurdish (Sorani / common UI)
            "دۆخی سەلامەت", "دۆخی پارێزراو", "مۆدی سەلامەت", "دۆخی ئارام", "مۆدی پارێزراو",
            "دووبارە دەستپێکردنەوە بۆ دۆخی سەلامەت",
            // Turkish / common OEM
            "güvenli mod", "guvenli mod", "güvenli modda yeniden başlat", "güvenli önyükleme",
            // French / Spanish / German / Italian / Portuguese / Indonesian
            "mode sans échec", "mode sans echec", "redémarrer en mode sans échec",
            "modo seguro", "reiniciar en modo seguro",
            "abgesicherter modus", "im abgesicherten modus neu starten",
            "modalità provvisoria", "modo de segurança", "reiniciar no modo de segurança",
            "mode aman", "mulai ulang ke mode aman",
            // Russian / Ukrainian
            "безопасный режим", "перезагрузить в безопасном режиме", "безпечний режим",
            // Chinese (Simplified/Traditional) / Japanese / Korean
            "安全模式", "重新启动到安全模式", "以安全模式重新启动", "安全模式重新開機",
            "セーフモード", "セーフモードで再起動", "안전 모드", "안전 모드로 재부팅",
            // Persian
            "حالت امن", "حالت ایمن", "راه‌اندازی مجدد در حالت امن", "بوت در حالت امن",
            // Hindi / Urdu / Bengali (large user bases)
            "सुरक्षित मोड", "सेफ मोड", "محفوظ موڈ", "নিরাপদ মোড",
        )

        /**
         * The "Power off" / long-press power button that opens the "Reboot to safe mode?" dialog
         * on AOSP and Samsung. Catching a click/long-click on it lets us challenge one step early,
         * before the confirmation dialog is even drawn, on skins that expose that label.
         */
        val SAFE_MODE_TRIGGER_WORDS = listOf(
            "power off", "poweroff", "power off / restart", "tap and hold to reboot to safe mode",
            "إيقاف التشغيل", "ايقاف التشغيل", "اضغط مطولاً لإعادة التشغيل في الوضع الآمن",
            "کوژاندنەوە", "دۆخی سەلامەت",
        )

        /**
         * Activity/fragment class fragments for the system "Network & internet → VPN" settings
         * page across AOSP and every reviewed OEM skin (Samsung One UI, Xiaomi MIUI/HyperOS,
         * Honor MagicOS, Huawei EMUI, Oppo ColorOS, Vivo Funtouch/OriginOS, Realme UI). Every
         * skin reviewed keeps the "VpnSettings"/"VpnPreference" suffix from the shared AOSP
         * `vpn2.VpnSettings` module even when the enclosing package/prefix is renamed, so a
         * single substring family covers them all — language-independent by construction.
         */
        val SYSTEM_VPN_SETTINGS_CLASSES = listOf(
            "vpn2.vpnsettings",
            "vpnsettingsactivity",
            "vpnsettingsfragment",
            "vpnsettings",
            "managedvpnsettings",
            "vpnappmanagement",
            "vpndialogactivity",
            "configvpn",
            "vpnlist",
            "vpnpreference",
            "vpnprofile",
            "addvpnprofile",
            "addvpnnetwork",
            "vpnpicker",
        )

        /**
         * View-id tokens unique to the system VPN settings page (add/edit-profile form, the
         * profile list, the always-on switch …). Used as a structural fallback for skins that
         * host the page inside a generic container class (Samsung `SubSettings`, a bare
         * fragment host) whose class name carries no "vpn" marker at all.
         */
        val SYSTEM_VPN_VIEW_ID_TOKENS = listOf(
            "vpn_layout",
            "vpn_list",
            "vpn_profile",
            "vpn_settings",
            "add_vpn",
            "vpn_switch",
            "vpn_row",
            "vpn_app_row",
            "always_on_vpn",
        )

        /**
         * Short, VPN-specific heading/label wording used ONLY as a fallback on generic
         * container classes, and ONLY against short strings (see the 40-char cap at the call
         * site) so a long paragraph that merely mentions VPN in passing can never match.
         */
        val SYSTEM_VPN_TEXT_WORDS = listOf(
            // English
            "vpn", "vpn settings", "add vpn", "add vpn profile", "vpn profile",
            "always-on vpn", "vpn & apps", "vpn apps", "manage vpn",
            // Arabic
            "شبكة vpn", "إعدادات vpn", "اعدادات vpn", "إضافة vpn", "الشبكة الظاهرية الخاصة",
            // Kurdish (Sorani)
            "ڕێکخستنی vpn", "زیادکردنی vpn",
        )

        /** Activity/fragment class fragments unique to the developer-options screen. */
        val DEVELOPER_OPTIONS_CLASSES = listOf(
            "developmentsettings",
            "developeroptions",
            "developersettings",
            "developeroptionsactivity",
            "developmentsettingsdashboard",
        )

        /**
         * Developer-options + sensitive-toggle wording. Matched as exact or substring so OEM
         * copy still hits. Covers the screen title and the OEM-unlock / USB-/wireless-debugging
         * rows, which are the routes used to weaken or remove protection.
         */
        val DEVELOPER_OPTIONS_WORDS = listOf(
            // English
            "developer options", "developer mode", "development settings",
            "oem unlocking", "oem unlock", "allow oem unlock",
            "usb debugging", "wireless debugging", "adb debugging", "debugging",
            "revoke usb debugging", "default usb configuration", "bootloader",
            // Arabic
            "خيارات المطور", "وضع المطور", "إعدادات المطور",
            "إلغاء قفل oem", "الغاء قفل oem", "تصحيح أخطاء usb", "تصحيح اخطاء usb",
            "تصحيح usb", "تصحيح الأخطاء", "تصحيح لاسلكي",
            // Kurdish (Sorani)
            "بژاردەکانی گەشەپێدەر", "دۆخی گەشەپێدەر", "ڕێکخستنی گەشەپێدەر",
            "کردنەوەی قوفڵی oem", "دیبەگی usb", "دیبەگی بێ‌تەل",
            // Turkish
            "geliştirici seçenekleri", "gelistirici secenekleri", "oem kilidini aç",
            "usb hata ayıklama", "usb hata ayiklama", "kablosuz hata ayıklama",
            // French / Spanish / German / Indonesian / Portuguese
            "options pour les développeurs", "options pour les developpeurs",
            "opciones de desarrollador", "entwickleroptionen",
            "opsi pengembang", "opções do desenvolvedor",
            "déverrouillage oem", "desbloqueo de oem", "débogage usb", "depuración por usb",
            "usb-debugging",
            // Chinese / Japanese / Korean / Russian / Persian
            "开发者选项", "开发者模式", "usb调试", "oem解锁",
            "開発者向けオプション", "usbデバッグ",
            "개발자 옵션", "usb 디버깅",
            "для разработчиков", "отладка по usb", "разблокировка oem",
            "گزینه‌های برنامه‌نویس", "گزینه های برنامه نویس", "اشکال‌زدایی usb", "قفل‌گشایی oem",
        )
    }
}
