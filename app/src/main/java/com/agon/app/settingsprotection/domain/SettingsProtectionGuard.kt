package com.agon.app.settingsprotection.domain

import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import com.agon.app.data.ShieldRepository
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.ui.PinLockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides what the accessibility service should do when a protected system screen appears.
 *
 * Contract:
 *  - First open of App Info / Accessibility (etc.) → PIN challenge.
 *  - After a **successful** unlock → temporary access (grace) so the user can use that screen
 *    without being prompted again every event.
 *  - After the user **really leaves** settings (launcher / another app) → grace is cleared so the
 *    next visit requires PIN again.
 *  - Backing out of the PIN screen without unlocking → no grace; next open challenges immediately
 *    (no scroll required).
 */
@Singleton
class SettingsProtectionGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: SettingsScreenDetector,
    private val repository: SettingsProtectionRepository,
) {

    enum class Decision {
        IGNORE,
        ALLOW,
        CHALLENGE,
    }

    @Volatile
    private var lastPromptAt = 0L

    @Volatile
    private var promptInFlight = false

    /**
     * True only while the challenge Activity is ACTUALLY visible on screen — reported by
     * [PinLockActivity] itself, never assumed. Some ROMs (Samsung One UI and similar strict
     * background-launch enforcement) silently drop a background Activity launch from a
     * process without a visible window, so [promptInFlight] alone is not proof that the PIN
     * is in front of the user. Only this flag is — while it is false a protected surface is
     * never allowed through, no matter which device the code runs on.
     */
    @Volatile
    private var challengeActivityVisible = false

    @Volatile
    private var lastDetection: ProtectedScreenDetection? = null

    /** True after we have seen a protected surface; cleared when the user leaves settings. */
    @Volatile
    private var insideProtectedScreen = false

    /**
     * Timestamp of the last successful unlock. Used so a brief HOME / task switch right after
     * unlock does not wipe the grace window before settings is shown again.
     */
    @Volatile
    private var lastGrantAt = 0L

    /**
     * True only while the CURRENT factory-reset visit was unlocked with its own PIN challenge.
     * Factory reset never rides on grace earned by another surface (App info, accessibility …)
     * and — unlike the general grace — it ends as soon as the user leaves the wipe screen, even
     * when they stay inside Settings. Every fresh visit therefore challenges again, exactly like
     * re-entering App info after leaving it.
     */
    @Volatile
    private var factoryResetGrant = false

    /**
     * True once the factory-reset surface has actually been re-shown after its unlock. Needed to
     * distinguish the brief HOME/launcher flash right after the PIN closes (grant must survive)
     * from the user really leaving the wipe screen via recents/home later (grant must end).
     */
    @Volatile
    private var factoryResetSeenAfterGrant = false

    /**
     * Last package observed as foreground during a window transition. Used to detect a real
     * leave from the wipe screen (Recents shown by SystemUI, Home launcher, or any other app)
     * even when the user only briefly presses the Recent Apps button without ever leaving the
     * process tree of the Settings app. Without this, the second visit to Factory Reset after
     * a round trip through Recents would still silently be allowed.
     */
    @Volatile
    private var lastForegroundPackage: String = ""

    @Volatile
    private var lastForegroundClass: String = ""

    val detection: ProtectedScreenDetection? get() = lastDetection

    /** True while a challenge was dispatched and not yet resolved (PIN may or may not be visible). */
    val challengeDispatched: Boolean get() = promptInFlight

    /** True only while the PIN lock Activity is actually visible on screen. */
    val challengeVisible: Boolean get() = challengeActivityVisible

    /** Called by [PinLockActivity.onResume] — the PIN is really in front of the user now. */
    fun onChallengeActivityShown() {
        challengeActivityVisible = true
    }

    /** Called by [PinLockActivity.onStop] — the PIN is no longer on screen. */
    fun onChallengeActivityHidden() {
        challengeActivityVisible = false
    }

    /**
     * @param clickSource when non-null (from a click event), checks Erase/Factory-reset tap.
     * @param clickEventTexts AccessibilityEvent text list (Samsung often puts the row title here).
     */
    fun evaluate(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
        clickSource: AccessibilityNodeInfo? = null,
        clickEventTexts: List<CharSequence> = emptyList(),
        windowTransition: Boolean = false,
    ): Decision {
        // Credential-authorised 3-minute pause: honoured BEFORE any detection runs. While the
        // pause timestamp is valid, every protected surface (settings, panels, toggles, App
        // Manager…) is treated as free — no detection, no PIN, nothing — for the whole window.
        // This preamble is shared by all challenge entry points, so the pause applies
        // identically on every route.
        if (ShieldRepository.isProtectionPaused()) {
            clearSession()
            return Decision.IGNORE
        }
        val snapshot = repository.currentSnapshot()
        val strongActive = com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(context)
        // When the protection commitment has expired or been stopped, settings protection
        // must not enforce a PIN challenge — the user should have direct access until they
        // manually renew protection.
        val commitmentInactive = com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isExpired(context) ||
            com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStopped(context)
        // Onboarding corridor: while the setup wizard has not been finished (the first-run flag
        // is written by MainActivity once the wizard completes), nothing is ever intercepted —
        // the user must be able to grant accessibility / admin / battery / autostart without a
        // single PIN interposition midway through setup.
        val setupDone = context.getSharedPreferences("family_shield", Context.MODE_PRIVATE)
            .getBoolean("setup_wizard_done", false)
        // Strong (timed) protection must intercept the same surfaces as PIN protection even
        // when no PIN was ever configured — otherwise App Info / Accessibility stay unlocked.
        if ((!snapshot.active && !strongActive) || commitmentInactive || !setupDone) {
            clearSession()
            return Decision.IGNORE
        }

        // Never intercept our own UI (PIN lock, main app).
        if (packageName == context.packageName) {
            return Decision.IGNORE
        }

        val now = System.currentTimeMillis()

        // Track the foreground package on every window transition. Must run BEFORE the positive
        // detection branch so a window transition that brought us to / away from the wipe screen
        // is always recorded, even when the screen itself is no longer matched.
        if (windowTransition) {
            lastForegroundPackage = packageName
            lastForegroundClass = className.orEmpty()
        }

        // Click-time detections first (more reliable than a window scan on transient dialogs):
        //  - a "Power off / Reboot to safe mode" press, then
        //  - an explicit factory-reset wipe-row press,
        // finally the generic window/activity scan.
        val found = if (clickSource != null || clickEventTexts.isNotEmpty()) {
            detector.detectSafeModeActionClick(packageName, clickSource, clickEventTexts)
                // Ultra / Extreme / Super power-saving toggle — blocked in BOTH quick-settings
                // surfaces (main panel and tile-edit screen alike), at 0ms on the tap itself.
                ?: detector.detectUltraPowerSavingClick(packageName, clickSource, clickEventTexts)
                // Restrict-choice taps on THIS app's own battery page (phase: background work
                // not yet enabled) are challenged before the option is actually applied.
                ?: detector.detectBatteryRestrictClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                    clickEventTexts,
                )
                // General autostart list: never gated on open — only a tap that turns THIS
                // app's toggle from ON to OFF is challenged here.
                // Pre-click gate: the tap that OPENS the background-autostart section on a
                // security-center dashboard (any language — structural ids / event payload).
                ?: detector.detectAutostartEntryClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                    clickEventTexts,
                )
                // Settings-search results routing to the autostart section (any OEM, any
                // device language): challenged exactly like the entry gate itself.
                ?: detector.detectAutostartSearchResultClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                    clickEventTexts,
                )
                // Zero-touch capture: the "App Manager" entry row on security-center dashboards
                // (any language). Challenged on the tap itself — the host's home and every
                // harmless tool outside it stay completely free.
                ?: detector.detectSecurityAppManagerEntryClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                    clickEventTexts,
                )
                // First-click gates: the Accessibility entry row, and this app's own row inside
                // any system/host list (downloaded apps, app manager …). 0ms overlay on tap.
                ?: detector.detectAccessibilityEntryClick(packageName, className, clickSource, clickEventTexts)
                ?: detector.detectOwnAppRowClick(packageName, className, root, clickSource)
                ?: detector.detectAutostartDisableClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                    clickEventTexts,
                )
                // Fail-closed interception of THIS app's protection switch (service detail /
                // special-access toggles) on any OEM container, in any device language.
                ?: detector.detectOwnServiceToggleClick(
                    packageName,
                    className,
                    root,
                    clickSource,
                )
                // Factory-reset interception deliberately removed by product decision.
                ?: detector.detect(packageName, className, root)
        } else {
            detector.detect(packageName, className, root)
        }

        if (found == null) {
            handlePossibleLeave(packageName, className, windowTransition, now)
            return Decision.IGNORE
        }

        // Selective Settings Blocking (Samsung One UI critical targeted fix): while "قفل
        // إعدادات الهاتف" (Phone Settings Lock) is OFF, the Settings app itself must never be
        // blocked wholesale — only the three explicitly risky categories in
        // [SELECTIVE_SETTINGS_ALLOWED_TYPES] (Accessibility, Downloaded/installed apps, Device
        // Admin) stay PIN-gated. Every other screen (Wi-Fi, Bluetooth, Sound, Display, the
        // Settings home page, battery, developer options, …) is released immediately here,
        // regardless of which structural heuristic in the detector produced the match. The
        // full-app lock is reserved exclusively for the user's explicit Phone Settings Lock
        // toggle, enforced separately (and unconditionally) in ShieldAccessibilityService.
        if (isPhoneSettingsAppPackage(packageName) &&
            !com.agon.app.services.PhoneSettingsLock.isEnabled(context) &&
            found.type !in SELECTIVE_SETTINGS_ALLOWED_TYPES
        ) {
            handlePossibleLeave(packageName, className, windowTransition, now)
            return Decision.IGNORE
        }

        lastDetection = found
        insideProtectedScreen = true

        if (!strongActive) {
            // Factory-reset unlock is a single-use pass for the current visit. Once the user has
            // entered the correct PIN while the wipe screen is in front of them, the grant stays
            // valid for every subsequent event that detects the wipe screen.
            if (found.type == ProtectedScreenType.FACTORY_RESET && factoryResetGrant) {
                factoryResetSeenAfterGrant = true
                return Decision.ALLOW
            }

            // Successful unlock → full grace for any protected surface until the user leaves settings.
            // Single exception: AUTOSTART_TOGGLE never rides grace (by design). Each disable
            // attempt on this app's own protection switch is challenged again in every session
            // and every device language; otherwise the freshly unlocked 60s window would let a
            // child flick the switch straight off right after the PIN.
            if (snapshot.hasTemporaryAccess(now)) {
                if (found.type == ProtectedScreenType.AUTOSTART_TOGGLE) {
                    // No grace for protection-switch tappers: re-challenge immediately.
                } else if (found.type != ProtectedScreenType.FACTORY_RESET || factoryResetGrant) {
                    if (found.type == ProtectedScreenType.FACTORY_RESET) {
                        factoryResetSeenAfterGrant = true
                    }
                    return Decision.ALLOW
                }
            }
        }

        // A challenge was already dispatched for this visit.
        if (promptInFlight) {
            if (challengeActivityVisible) {
                // The PIN is really on screen: the protected surface is covered, allow
                // (this only dedupes — it never lets an unauthenticated user through).
                return Decision.ALLOW
            }
            // The dispatched challenge is NOT on screen: the ROM dropped the background
            // Activity launch, or the lock Activity died without a callback. We only reach
            // this point when the ACTIVE window is a protected surface (the user is on it),
            // so re-challenge — the service throttles the actual dispatch so the high-
            // frequency event stream cannot spam launches. An unconfirmed challenge must
            // never allow the surface through, on any device.
        }

        lastPromptAt = now
        promptInFlight = true
        return Decision.CHALLENGE
    }

    // (The SystemUI early-touch interception entry point was removed by product decision: Ultra
    // power-saving tiles are now free in the quick-settings panel; enforcement continues inside
    // Settings surfaces through [evaluate].)

    /**
     * Clears grace only when the user has actually left the settings app family — not when the
     * accessibility tree is briefly incomplete on the same settings package (resume / rotate).
     */
    private fun handlePossibleLeave(
        packageName: String,
        className: String?,
        windowTransition: Boolean,
        now: Long,
    ) {
        // Arm the leave-detector once the brief post-unlock settle window has passed. This also
        // covers OEM flows whose wipe screen is only ever detected through the row CLICK (generic
        // container classes): even without a positive re-detection, the grant becomes leave-bound
        // a few seconds after the unlock.
        if (factoryResetGrant && !factoryResetSeenAfterGrant &&
            now - lastGrantAt > ProtectionTiming.POST_GRANT_SETTLE_MILLIS
        ) {
            factoryResetSeenAfterGrant = true
        }
        if (factoryResetGrant && factoryResetSeenAfterGrant) {
            // Strict surface-bound guarantee: leaving the wipe screen for any other app
            // (Recents, Home, launcher, another app) immediately revokes the grant. Settling
            // frames inside Settings around the wipe screen (transitions to the surrounding
            // settings pages, generic container classes, or temporary content frames that do
            // not yet carry the wipe title) MUST NOT revoke the grant — those intermediate
            // frames are part of the same visit and clearing the grant here is what caused the
            // PIN loop on MIUI / HyperOS.
            if (windowTransition) {
                val isWipeScreenSurface = isFactoryResetSurface(packageName, className)
                val isSettingsFamily = isSettingsFamilyPackage(packageName)
                if (!isSettingsFamily) {
                    factoryResetGrant = false
                    factoryResetSeenAfterGrant = false
                }
            }
        }
        if (!insideProtectedScreen) return
        if (isSettingsFamilyPackage(packageName)) {
            // Still inside Settings (or installer); tree just did not match this frame.
            return
        }
        // Right after a successful unlock the flow often goes HOME for a moment before the
        // protected screen is brought back. Keep grace during that settle window.
        if (now - lastGrantAt < ProtectionTiming.POST_GRANT_SETTLE_MILLIS &&
            repository.currentSnapshot().hasTemporaryAccess(now)
        ) {
            return
        }

        insideProtectedScreen = false
        repository.revokeTemporaryAccess()
        lastGrantAt = 0L
        if (!promptInFlight) {
            lastPromptAt = 0L
        }
    }

    /**
     * Structural confirmation gate for
     * [com.agon.app.accessibility.ShieldAccessibilityService]'s floating-window pre-check
     * (Samsung One UI critical fix): a narrow/short Settings window must never be treated as a
     * bypass attempt on GEOMETRY alone — every dialog Settings ever shows (Wi-Fi password
     * prompts, "Forget network?", a plain confirmation …) is narrower than the full screen by
     * construction. This flips that heuristic from "block unless proven safe" to "block ONLY if
     * the SAME structural detector that governs every other surface in this app positively
     * identifies it as a protected screen" — and, while Phone Settings Lock is OFF, narrows
     * that further to [SELECTIVE_SETTINGS_ALLOWED_TYPES] so no other floating Settings dialog
     * can ever evacuate the whole app.
     */
    fun isConfirmedProtectedSurface(
        packageName: String,
        className: String?,
        root: AccessibilityNodeInfo?,
    ): Boolean {
        val found = detector.detect(packageName, className, root) ?: return false
        if (isPhoneSettingsAppPackage(packageName) &&
            !com.agon.app.services.PhoneSettingsLock.isEnabled(context)
        ) {
            return found.type in SELECTIVE_SETTINGS_ALLOWED_TYPES
        }
        return true
    }

    fun challengeIntent(): Intent {
        val current = lastDetection
        return Intent(context, PinLockActivity::class.java)
            .putExtra(PinLockActivity.EXTRA_SCREEN_TYPE, current?.type?.name ?: ProtectedScreenType.APP_INFO.name)
            .putExtra(PinLockActivity.EXTRA_SETTINGS_PACKAGE, current?.settingsPackage.orEmpty())
            .putExtra(PinLockActivity.EXTRA_SCREEN_CLASS, current?.screenClass.orEmpty())
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
    }

    /**
     * State enforcement bridge for the autostart speed trick: when the current detection is an
     * autostart-toggle attempt, flips this app's freshly-disabled switch back ON in the same
     * frame, before the challenge overlay presses HOME. No-op for every other detection type.
     */
    fun revertDetectedProtectionSwitch(root: AccessibilityNodeInfo?) {
        when (lastDetection?.type) {
            ProtectedScreenType.AUTOSTART_TOGGLE -> detector.revertOwnAutostartSwitch(root)
            // Same-frame auto re-enable after taps on OUR accessibility / special-access toggle
            // (Honor MagicOS, Xiaomi HyperOS, Samsung One UI, ColorOS — language-independent).
            ProtectedScreenType.ACCESSIBILITY_SETTINGS -> detector.revertOwnToggleIfOff(root)
            // Device-admin: confirm the ADD activation programmatically the instant the
            // challenge fires; deactivate/remove confirmations are never auto-pressed.
            ProtectedScreenType.DEVICE_ADMIN ->
                detector.confirmAdminActivation(root, lastDetection?.screenClass)
            else -> Unit
        }
    }

    /** PIN dismissed without success — re-arm so the next open challenges immediately. */
    fun onChallengeFinished() {
        promptInFlight = false
        challengeActivityVisible = false
    }

    /** PIN / biometric succeeded — grace is already stored in the repository. */
    fun onChallengeGranted() {
        promptInFlight = false
        challengeActivityVisible = false
        lastGrantAt = System.currentTimeMillis()
        // Only a challenge raised BY the factory-reset flow arms its surface-bound grant.
        factoryResetGrant = lastDetection?.type == ProtectedScreenType.FACTORY_RESET
        factoryResetSeenAfterGrant = false
        // User will land back on a protected surface; keep session marked so leave-detection works.
        insideProtectedScreen = true
    }

    fun reset() {
        clearSession()
        lastDetection = null
        lastForegroundPackage = ""
        lastForegroundClass = ""
    }

    private fun clearSession() {
        promptInFlight = false
        challengeActivityVisible = false
        insideProtectedScreen = false
        lastPromptAt = 0L
        lastGrantAt = 0L
        factoryResetGrant = false
        factoryResetSeenAfterGrant = false
    }

    /**
     * True when the current window is the factory-reset screen itself (MasterClear / Erase-all /
     * confirm activities). Used by the leave-detection above so that the grant is preserved
     * while the user merely scrolls or refocuses the wipe screen, but expires as soon as they
     * go to any other page.
     */
    private fun isFactoryResetSurface(packageName: String, className: String?): Boolean {
        if (!isSettingsFamilyPackage(packageName)) return false
        val cls = className?.lowercase().orEmpty()
        if (cls.isBlank()) return false
        val resetList = listOf(
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
            "masterclearconfirm",
            "factoryresetconfirm",
            "eraseconfirm",
            "erasealldataconfirm",
            "wipeconfirm",
            "hardresetconfirm",
            "confirmmasterclear",
        )
        return resetList.any { cls.contains(it) }
    }

    private fun isSettingsFamilyPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower.contains("settings") ||
            lower.contains("packageinstaller") ||
            lower.contains("permissioncontroller") ||
            lower.contains("securitycenter") ||
            lower.contains("safecenter") ||
            lower.contains("deviceadmin") ||
            lower.contains("documentsui") ||
            lower.contains("documents") ||
            lower in KNOWN_SETTINGS_PACKAGES
    }

    /**
     * True only for the actual system Settings app package — the one narrow surface Selective
     * Settings Blocking and Phone Settings Lock govern. `com.samsung.android.settings` is
     * included defensively per the product spec even though One UI's actual Settings app keeps
     * the AOSP package id (`com.android.settings`); this is strictly additive and can never
     * match a real system window that was not already the Settings app.
     */
    /**
     * True for the actual system Settings app package on any OEM skin — the surface Selective
     * Settings Blocking and Full Phone Settings Lock both govern. Every skin reviewed (AOSP/
     * Pixel, Samsung One UI, Xiaomi MIUI/HyperOS, Honor MagicOS, Huawei EMUI, Oppo ColorOS, Vivo
     * Funtouch/OriginOS, Realme UI) keeps its main Settings app under a package literally ending
     * in ".settings" (`com.android.settings`, `com.samsung.android.settings`, `com.miui.settings`,
     * `com.coloros.settings`, `com.oplus.settings`, `com.vivo.settings`, …) — deliberately
     * narrower than the broader settings-family matching used elsewhere in this class (which
     * also matches security-center / app-manager / app-store hosts that are NOT the Settings app
     * itself and must never be gated by either of these two features).
     */
    private fun isPhoneSettingsAppPackage(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val lower = packageName.lowercase()
        return lower == "com.android.settings" || lower.endsWith(".settings")
    }

    private companion object {
        val KNOWN_SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.securitycenter",
            "com.miui.permcenter",
            "com.huawei.systemmanager",
            "com.coloros.safecenter",
            "com.oppo.safe",
            "com.vivo.permissionmanager",
            "com.google.android.documentsui",
            "com.android.documentsui",
        )

        /**
         * Selective Settings Blocking allow-list (critical targeted fix): the ONLY screen
         * categories that stay PIN-gated inside the actual Settings app while "قفل إعدادات
         * الهاتف" (Phone Settings Lock) is OFF — Accessibility, Downloaded/installed apps, and
         * Device Admin. Every other category (App Info, Uninstall, Force stop, Clear data,
         * Permissions, Battery restriction, Autostart, Safe mode, Power menu, Factory reset,
         * System update delete, Developer options, Security settings, …) is released
         * immediately in that state so the Settings app is never blocked wholesale.
         */
        val SELECTIVE_SETTINGS_ALLOWED_TYPES = setOf(
            ProtectedScreenType.ACCESSIBILITY_SETTINGS,
            ProtectedScreenType.MANAGE_APPS,
            ProtectedScreenType.DEVICE_ADMIN,
        )
    }
}
