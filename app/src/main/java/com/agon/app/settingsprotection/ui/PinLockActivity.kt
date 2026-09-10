package com.agon.app.settingsprotection.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.agon.app.data.ShieldRepository
import com.agon.app.localization.LocalAppLanguage
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.settingsprotection.domain.ProtectedScreenDetection
import com.agon.app.settingsprotection.domain.ProtectedScreenType
import com.agon.app.settingsprotection.domain.SettingsProtectionGuard
import com.agon.app.settingsprotection.viewmodel.PinLockViewModel
import com.agon.app.ui.theme.AgonAppTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full screen PIN challenge raised on top of the protected system settings window.
 *
 * Extends [FragmentActivity] because [BiometricPrompt] requires a FragmentActivity host, and
 * unlike AppCompatActivity it does not force an AppCompat theme on this project.
 */
@AndroidEntryPoint
class PinLockActivity : FragmentActivity() {

    @Inject lateinit var guard: SettingsProtectionGuard

    @Inject lateinit var protectionRepository: SettingsProtectionRepository

    private val viewModel: PinLockViewModel by viewModels()

    private var resolved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ShieldRepository.initialize(this)

        viewModel.bind(readDetection())
        val language = language()

        setContent {
            val shieldState by ShieldRepository.state.collectAsStateWithLifecycle()
            val dark = when (shieldState.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            AgonAppTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalAppLanguage provides language,
                    LocalLayoutDirection provides if (language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl,
                ) {
                    PinLockScreen(
                        viewModel = viewModel,
                        language = language,
                        onCancel = { if (!resolved) viewModel.onCancelled() },
                    )
                }
            }
        }

        observeEvents()
        if (!com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(this)) {
            if (protectionRepository.currentSnapshot().biometricEnabled && canUseBiometrics()) {
                viewModel.requestBiometric()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The PIN is really on screen now: the guard may suppress duplicate challenges, and
        // the service stops retrying the launch.
        guard.onChallengeActivityShown()
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                viewModel.events.collectLatest { event ->
                    when (event) {
                        PinLockViewModel.Event.Granted -> allowAccess()
                        PinLockViewModel.Event.Denied -> denyAccess()
                        PinLockViewModel.Event.WrongPinFeedback -> Unit
                        PinLockViewModel.Event.LaunchBiometric -> showBiometricPrompt()
                    }
                }
            }
        }
    }

    private fun readDetection(): ProtectedScreenDetection {
        val type = runCatching {
            ProtectedScreenType.valueOf(
                intent.getStringExtra(EXTRA_SCREEN_TYPE) ?: ProtectedScreenType.APP_INFO.name,
            )
        }.getOrDefault(ProtectedScreenType.APP_INFO)
        return ProtectedScreenDetection(
            type = type,
            settingsPackage = intent.getStringExtra(EXTRA_SETTINGS_PACKAGE).orEmpty(),
            screenClass = intent.getStringExtra(EXTRA_SCREEN_CLASS).orEmpty(),
            confidence = 100,
        )
    }

    private fun canUseBiometrics(): Boolean = runCatching {
        BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    private fun showBiometricPrompt() {
        if (!canUseBiometrics()) {
            viewModel.onBiometricUnavailable()
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Cancellation keeps the PIN keypad available instead of denying immediately.
                    viewModel.onBiometricUnavailable()
                }

                override fun onAuthenticationFailed() {
                    viewModel.onBiometricRejected()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("BlockX LaAbrah")
            .setSubtitle(biometricSubtitle())
            .setNegativeButtonText(biometricNegative())
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure { viewModel.onBiometricUnavailable() }
    }

    private fun language(): String =
        getSharedPreferences("family_shield", MODE_PRIVATE).getString("language", "ku") ?: "ku"

    private fun biometricSubtitle(): String = when (language()) {
        "en" -> "Verify your identity to open protected settings"
        "ar" -> "أكد هويتك لفتح الإعدادات المحمية"
        else -> "ناسنامەت پشتڕاست بکەوە بۆ کردنەوەی ڕێکخستنی پارێزراو"
    }

    private fun biometricNegative(): String = when (language()) {
        "en" -> "Use PIN"
        "ar" -> "استخدم الرمز"
        else -> "PIN بەکاربهێنە"
    }

    /**
     * Correct PIN: dismiss the lock and reopen the screen the user was actually heading to.
     *
     * For the accessibility settings surface we must reopen the Accessibility settings page, not
     * the App info page, otherwise unlocking the accessibility screen wrongly redirects the user
     * to App info. Every other protected surface still lands on this app's App info page.
     */
    private fun allowAccess() {
        if (resolved) return
        if (com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(this)) {
            denyAccess()
            return
        }
        resolved = true
        // Grace was already written by the ViewModel; mark the guard so leave-detection and
        // in-flight state stay consistent and the user is not re-prompted on the next frame.
        guard.onChallengeGranted()
        // HOME was pressed before the PIN appeared — reopen the target surface under grace.
        when (readScreenType()) {
            ProtectedScreenType.ACCESSIBILITY_SETTINGS -> {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            ProtectedScreenType.SAFE_MODE,
            ProtectedScreenType.POWER_MENU,
            ProtectedScreenType.AUTOSTART_TOGGLE,
            -> {
                // Do not reopen the power menu or re-navigate into Security Center after an
                // autostart-toggle PIN: the user simply lands home and can reopen the list
                // themselves. (The OFF attempt was already blocked pre-toggle.)
            }
            ProtectedScreenType.FACTORY_RESET -> {
                // After a successful PIN, reopen System settings so the authorised adult can
                // navigate back to Reset options within the temporary access window.
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            ProtectedScreenType.SYSTEM_UPDATE_DELETE,
            ProtectedScreenType.SECURITY_SETTINGS,
            -> {
                // Do not reopen the delete dialog / security center automatically; grace lets
                // the user open that surface again within the temporary access window.
            }
            ProtectedScreenType.AUTOSTART_ENTRY -> {
                // Contract for the Xiaomi autostart entry gate: a correct PIN drops the
                // authorized adult straight into the real Background-autostart page (MIUI/
                // HyperOS). On non-MIUI hosts the intent simply does not resolve and nothing
                // happens — the user stays home with grace active.
                runCatching {
                    startActivity(
                        Intent()
                            .setComponent(
                                android.content.ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                                ),
                            )
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            else -> {
                // App info / admin / manage-apps style surfaces: open this app's details page.
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:${applicationContext.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
        finishAndRemoveTask()
    }

    private fun readScreenType(): ProtectedScreenType = runCatching {
        ProtectedScreenType.valueOf(
            intent.getStringExtra(EXTRA_SCREEN_TYPE) ?: ProtectedScreenType.APP_INFO.name,
        )
    }.getOrDefault(ProtectedScreenType.APP_INFO)

    /** Wrong PIN or back press: leave the protected screen and return to the home launcher. */
    private fun denyAccess() {
        if (resolved) return
        resolved = true
        protectionRepository.revokeTemporaryAccess()
        // Re-arm immediately so the next resume of App Info / Accessibility is challenged again
        // without requiring a scroll or any other user interaction.
        guard.onChallengeFinished()
        runCatching {
            startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        finishAndRemoveTask()
    }

    override fun onStop() {
        // The PIN is no longer on screen: the guard must stop treating the challenge as
        // visible and re-challenge the next protected-surface event.
        guard.onChallengeActivityHidden()
        // If the activity is finishing without a successful grant (back, home, recents kill),
        // re-arm before the next settings resume is delivered. onDestroy can be delayed by the
        // system, which previously left a window where App Info was reachable without a PIN.
        if (isFinishing && !resolved) {
            resolved = true
            protectionRepository.revokeTemporaryAccess()
            guard.onChallengeFinished()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!resolved) {
            protectionRepository.revokeTemporaryAccess()
            guard.onChallengeFinished()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SCREEN_TYPE = "screen_type"
        const val EXTRA_SETTINGS_PACKAGE = "settings_package"
        const val EXTRA_SCREEN_CLASS = "screen_class"
    }
}
