package com.agon.app.applock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Re-challenge helper for individual sensitive actions (disable protection, edit blocklist,
 * open logs, change time limits …).
 *
 * Usage:
 * ```
 * val gate = rememberSensitiveActionGate(language)
 * // …
 * ToggleSetting(...) { gate.run { performDisableProtection() } }
 * gate.Host()
 * ```
 *
 * When the app lock is enabled and the unlocked window has expired, [SensitiveActionGate.run]
 * shows a modal PIN gate and only invokes the action after a successful unlock. When the app
 * lock is disabled, or the session is still unlocked, the action runs immediately — so the extra
 * prompt appears exactly when protection requires it and never gets in the way otherwise.
 */
class SensitiveActionGate(private val language: String) {

    private var pendingAction: (() -> Unit)? by mutableStateOf(null)
    private var showing: Boolean by mutableStateOf(false)

    /** Always verifies the configured credential before running [action]. */
    fun requireCredential(action: () -> Unit) {
        pendingAction = action
        showing = true
    }

    /** Runs [action] immediately, or behind a PIN challenge when the app lock demands it. */
    fun run(action: () -> Unit) {
        if (!AppLockManager.mustChallenge()) {
            // Either the lock is off or the short unlocked window is still valid: proceed, and
            // refresh the window so a burst of actions does not re-prompt mid-flow.
            if (AppLockManager.isEnabled()) AppLockManager.markUnlocked()
            action()
            return
        }
        pendingAction = action
        showing = true
    }

    @Composable
    fun Host() {
        if (!showing) return
        Dialog(
            onDismissRequest = { showing = false; pendingAction = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            AppLockGate(
                language = language,
                onBack = {
                    showing = false
                    pendingAction = null
                },
                onUnlocked = {
                    val action = pendingAction
                    showing = false
                    pendingAction = null
                    action?.invoke()
                },
            )
        }
    }
}

@Composable
fun rememberSensitiveActionGate(language: String): SensitiveActionGate =
    remember(language) { SensitiveActionGate(language) }
