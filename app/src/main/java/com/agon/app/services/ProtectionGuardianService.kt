package com.agon.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.agon.app.MainActivity
import com.agon.app.R
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.domain.BlockEngine
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.blocklist.domain.BuiltInAdultKeywords
import com.agon.app.data.ShieldRepository
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import com.agon.app.vpn.FamilyVpnService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Always-on supervisor. Owns the persistent notification and keeps protection alive
 * independently of any Activity: it revives the VPN when the user still wants it, surfaces a
 * warning when the accessibility service has been switched off, reloads the keyword engine after
 * process death, and schedules AlarmManager keep-alives so OEM killers cannot leave the device
 * unprotected for hours.
 */
@AndroidEntryPoint
class ProtectionGuardianService : Service() {

    @Inject lateinit var repository: BlocklistRepository

    @Inject lateinit var settingsProtectionRepository: SettingsProtectionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Tracks the last published notification text so we never post a redundant update. */
    private var lastNotificationText: String? = null

    private var accessibilityWarningShown = false

    /** True once the device-admin-lost alert has been posted for the current loss episode. */
    private var adminWarningShown = false

    private var wakeLock: PowerManager.WakeLock? = null

    // ------------------------------------------------ power-save mode recovery (all OEMs)

    /** Callback thread for the Settings observers. */
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    /** Dynamic twin of the manifest receiver — delivery guaranteed while the guardian lives. */
    private val powerSaveReceiver = PowerSaveModeRecoveryReceiver()

    /**
     * Vendors with no public power-save broadcast flip well-known Settings keys instead; one
     * observer covers every key below, on every ROM — entering OR exiting saving mode both
     * re-arm protection instantly. Unregistered keys on a given device are harmless no-ops.
     */
    private val powerModeObserver = object : ContentObserver(mainThreadHandler) {
        override fun onChange(selfChange: Boolean) {
            triggerPowerSaveRecovery()
        }
    }

    @Volatile
    private var lastPowerSaveRecoveryAt = 0L

    private fun registerPowerSaveRecovery() {
        runCatching {
            ContextCompat.registerReceiver(
                this,
                powerSaveReceiver,
                IntentFilter().apply {
                    PowerSaveModeRecoveryReceiver.HANDLED_ACTIONS.forEach { addAction(it) }
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        // AOSP battery saver (Pixel / stock / most OEM standard saver).
        runCatching { contentResolver.registerContentObserver(Settings.Global.getUriFor("low_power"), false, powerModeObserver) }
        // Samsung One UI: Ultra Power Saving + classic power-saving switch.
        runCatching { contentResolver.registerContentObserver(Settings.Global.getUriFor("ultra_power_saving_mode"), false, powerModeObserver) }
        runCatching { contentResolver.registerContentObserver(Settings.Global.getUriFor("psm_switch"), false, powerModeObserver) }
        // Xiaomi / Redmi / POCO (MIUI & HyperOS): Super Power Saving open flag.
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("power_supersave_mode_open"), false, powerModeObserver) }
        // ColorOS / OxygenOS / Realme UI super-saving flag and Vivo / iQOO extreme saver.
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("high_power_saving_mode"), false, powerModeObserver) }
        runCatching { contentResolver.registerContentObserver(Settings.System.getUriFor("super_power_saving_mode"), false, powerModeObserver) }
    }

    private fun unregisterPowerSaveRecovery() {
        runCatching { unregisterReceiver(powerSaveReceiver) }
        runCatching { contentResolver.unregisterContentObserver(powerModeObserver) }
    }

    /**
     * A power-save mode flip bursts through several channels at once (manifest receiver,
     * dynamic receiver, multiple Settings keys). One debounced, idempotent recovery wins —
     * the guardian, the settings/App-Manager blocking path and a desired VPN all come back
     * at 0ms instead of being restarted half a dozen times in a row.
     */
    private fun triggerPowerSaveRecovery() {
        val now = System.currentTimeMillis()
        if (now - lastPowerSaveRecoveryAt < POWER_SAVE_RECOVERY_DEBOUNCE_MS) return
        lastPowerSaveRecoveryAt = now
        runCatching { KeepAliveScheduler.ensureProtectionRunning(this) }
    }

    override fun onCreate() {
        super.onCreate()
        ShieldRepository.initialize(this)
        BuiltInAdultDomains.initialize(this)
        BuiltInAdultKeywords.initialize(this)
        createChannels()
        // Foreground-service bring-up can throw ForegroundServiceStartNotAllowedException /
        // SecurityException / IllegalStateException on Android 14+/15 under BAL or a type
        // mismatch — the guardian must survive, never crash the process.
        startForegroundCompat(ID, notification(statusText()))
        KeepAliveScheduler.schedule(this)
        registerPowerSaveRecovery()

        // Rules are pushed by Room; no polling needed to keep the engine current.
        scope.launch {
            repository.data
                .map { (rules, enabled) -> rules.size to enabled }
                .distinctUntilChanged()
                .collect { updateNotification() }
        }

        scope.launch {
            // Immediate first pass so a cold start after kill repairs the engine at once.
            runCatching { superviseProtection() }
            while (isActive) {
                delay(SUPERVISION_INTERVAL_MS)
                runCatching { superviseProtection() }
            }
        }
    }

    /**
     * One supervision pass. Cheap: a few state reads and, only when something is actually
     * wrong, one corrective action.
     */
    private suspend fun superviseProtection() {
        withWakeLock {
            ShieldRepository.refresh()
            BuiltInAdultDomains.initialize(this)
            BuiltInAdultKeywords.initialize(this)

            // Always re-assert the in-memory keyword / domain engine. BlockEngine.update is a
            // no-op when the snapshot is unchanged, so this is safe on every tick and repairs
            // process death where Room observers had not yet re-emitted.
            runCatching { com.agon.app.blocklist.data.TimedBlockingStore.checkpointAll(this) }
            runCatching { repository.refreshEngine() }

            val state = ShieldRepository.state.value

            // 0. Credential-authorised pause: suspend self-healing for up to three minutes.
            if (ShieldRepository.isProtectionPaused()) {
                updateNotification()
                KeepAliveScheduler.scheduleNext(this)
                return@withWakeLock
            }

            // 1. VPN desired but not running -> revive it without any UI.
            if (state.vpnEnabled && !state.vpnRunning) {
                runCatching {
                    ContextCompat.startForegroundService(this, Intent(this, FamilyVpnService::class.java))
                }
            }

            // 2. Accessibility service disconnected -> tell the user immediately.
            val accessibilityOn = isAccessibilityEnabled()
            if (!accessibilityOn && !accessibilityWarningShown) {
                accessibilityWarningShown = true
                notifyAccessibilityDisconnected()
                scope.launch {
                    com.agon.app.health.ParentAlertStore.notifyProtectionDown(
                        this@ProtectionGuardianService,
                        "Accessibility off — keyword protection paused",
                    )
                }
            } else if (accessibilityOn && accessibilityWarningShown) {
                accessibilityWarningShown = false
                runCatching {
                    getSystemService(NotificationManager::class.java)?.cancel(ALERT_ID)
                }
            }

            // 2b. Ensure guardian notification channel + keep-alive belts stay armed.
            com.agon.app.health.NotificationHealth.ensureChannels(this)
            KeepAliveScheduler.schedule(this)

            // 2c. Re-anchor the tamper-proof clock to real network time whenever internet is
            // available (SNTP, throttled internally; this loop runs on the IO dispatcher).
            runCatching { com.agon.app.security.TamperProofClock.maybeSyncWithNetwork(this) }

            // 2d. Advance the protection-commitment ledger by honestly elapsed time.
            runCatching { com.agon.app.settingsprotection.data.ProtectionCommitmentStore.tick(this) }

            // 2e. Safe Browsing Shield duration lock (درع التصفح الآمن).
            runCatching { com.agon.app.vpn.CommitmentTimerModule.tick(this) }
            if (com.agon.app.vpn.CommitmentTimerModule.isActive(this)) {
                ShieldRepository.setVpnDesired(true)
                if (!state.vpnRunning) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            this,
                            Intent(this, FamilyVpnService::class.java),
                        )
                    }
                }
            }

            // 3. Expired temporary access closes even if the user never returned to the screen.
            val snapshot = settingsProtectionRepository.currentSnapshot()
            if (snapshot.graceUntil > 0 && !snapshot.hasTemporaryAccess(System.currentTimeMillis())) {
                settingsProtectionRepository.revokeTemporaryAccess()
            }

            // 3b. Device-admin watchdog. The admin lock is what makes Play Store / adb /
            // uninstall-dialog removal of this app fail system-wide; the accessibility gate and
            // onDisableRequested PIN challenge protect the known deactivation screens, but if a
            // revocation ever slips through an uncovered OEM surface the loss must be detected
            // here instead of silently weakening uninstall protection for hours. Mirrors the
            // accessibility watchdog: one alert per loss episode + parent notification, and the
            // flag re-arms automatically once admin rights are restored.
            val protectionArmed = snapshot.active ||
                com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(this)
            val adminOn = com.agon.app.health.ProtectionHealth.isDeviceAdminActive(this)
            if (protectionArmed && !adminOn) {
                if (!adminWarningShown) {
                    adminWarningShown = true
                    notifyDeviceAdminLost()
                    scope.launch {
                        com.agon.app.health.ParentAlertStore.notifyProtectionDown(
                            this@ProtectionGuardianService,
                            "Device admin off — uninstall protection weakened",
                        )
                    }
                }
            } else if (adminOn && adminWarningShown) {
                adminWarningShown = false
                runCatching {
                    getSystemService(NotificationManager::class.java)?.cancel(ALERT_ADMIN_ID)
                }
            }

            // 4. Engine must stay ready for keyword matching.
            if (!BlockEngine.isInitialized()) {
                runCatching { repository.refreshEngine() }
            }

            updateNotification()
            KeepAliveScheduler.scheduleNext(this)
        }
    }

    private inline fun withWakeLock(block: () -> Unit) {
        val lock = wakeLock ?: run {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dira:guardian")?.also {
                it.setReferenceCounted(false)
                wakeLock = it
            }
        }
        runCatching { if (lock != null && !lock.isHeld) lock.acquire(15_000L) }
        try {
            block()
        } finally {
            runCatching { if (lock != null && lock.isHeld) lock.release() }
        }
    }

    private fun isAccessibilityEnabled(): Boolean = runCatching {
        Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .contains("$packageName/com.agon.app.accessibility.ShieldAccessibilityService", true)
    }.getOrDefault(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        KeepAliveScheduler.schedule(this)
        scope.launch {
            runCatching { repository.refreshEngine() }
            runCatching { superviseProtection() }
        }
        return START_STICKY
    }

    /** Swiping the app away must not weaken protection. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch { repository.refreshEngine() }
        KeepAliveScheduler.schedule(this)
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, ProtectionGuardianService::class.java))
        }
        // Deliberately not calling super, which would stop the service.
    }

    override fun onDestroy() {
        unregisterPowerSaveRecovery()
        KeepAliveScheduler.schedule(this)
        KeepAliveScheduler.ensureProtectionRunning(this)
        // Dual watchdog: if this supervisor is killed, bring it back AND re-assert the VPN when
        // the user still wants it. Each protection service revives the other, so neither can be
        // silently killed without the survivor restarting it.
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, ProtectionGuardianService::class.java))
        }
        val state = ShieldRepository.state.value
        if (state.vpnEnabled && !ShieldRepository.isProtectionPaused()) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, FamilyVpnService::class.java))
            }
        }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------------- notifications

    private fun statusText(): String {
        val state = ShieldRepository.state.value
        val language = state.language
        if (ShieldRepository.isProtectionPaused()) {
            val minutes = (ShieldRepository.pauseRemainingMillis() + 59_999L) / 60_000L
            return when (language) {
                "en" -> "Protection paused — resumes in $minutes min"
                "ar" -> "الحماية متوقفة مؤقتاً — تعود خلال $minutes دقيقة"
                else -> "پاراستن وەستاوە — دوای $minutes خولەک دەگەڕێتەوە"
            }
        }
        return when {
            !isAccessibilityEnabled() -> when (language) {
                "en" -> "Accessibility is off — tap to re-enable protection"
                "ar" -> "إمكانية الوصول متوقفة — اضغط لإعادة الحماية"
                else -> "Accessibility ناچالاکە — بۆ چالاککردنەوە دەست بنێ"
            }
            state.vpnRunning -> when (language) {
                "en" -> "Web, app and keyword protection is active"
                "ar" -> "حماية الويب والتطبيقات والكلمات نشطة"
                else -> "پاراستنی وێب و ئەپ و وشە چالاکە"
            }
            state.vpnEnabled -> when (language) {
                "en" -> "Reconnecting web protection…"
                "ar" -> "جارٍ إعادة اتصال حماية الويب…"
                else -> "پاراستنی وێب پەیوەندی دەکاتەوە…"
            }
            else -> when (language) {
                "en" -> "App and keyword protection is active"
                "ar" -> "حماية التطبيقات والكلمات نشطة"
                else -> "پاراستنی ئەپ و وشە چالاکە"
            }
        }
    }

    private fun updateNotification() {
        val text = statusText()
        if (text == lastNotificationText) return
        lastNotificationText = text
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(ID, notification(text))
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val language = ShieldRepository.state.value.language
        val protectionName = when (language) { "en" -> "Device protection"; "ar" -> "حماية الجهاز"; else -> "پاراستنی ئامێر" }
        val warningName = when (language) { "en" -> "Protection warnings"; "ar" -> "تحذيرات الحماية"; else -> "ئاگادارییەکانی پاراستن" }
        val warningDescription = when (language) {
            "en" -> "Warns when protection is disabled or interrupted"
            "ar" -> "تنبيه عند تعطيل الحماية أو انقطاعها"
            else -> "ئاگادارت دەکاتەوە کاتێک پاراستن ناچالاک یان پچڕاوە"
        }
        // The always-on guardian notice must be as quiet as Android allows: minimum-importance
        // channel (no status-bar icon, collapsed in the shade), silent and badge-free. A fresh
        // channel id is used because the importance of an existing channel cannot be changed.
        manager.deleteNotificationChannel(LEGACY_CHANNEL)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, protectionName, NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL, warningName, NotificationManager.IMPORTANCE_HIGH).apply {
                description = warningDescription
            },
        )
    }

    /**
     * Two-stage foreground bring-up (Android 14/15): declare BOTH service types first, and if
     * the platform refuses (BAL / ForegroundServiceStartNotAllowedException), fall back
     * instantly to the single specialUse type so the guardian never dies while starting.
     */
    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            val both = runCatching {
                startForeground(
                    id,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED or
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
            if (both.isSuccess) return
            runCatching {
                startForeground(
                    id,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            }
        } else {
            runCatching { startForeground(id, notification) }
        }
    }

    private fun notification(text: String): android.app.Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_app)
            .setContentTitle("BlockX LaAbrah")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** High-priority alert shown the moment the accessibility service is switched off. */
    private fun notifyAccessibilityDisconnected() {
        val language = ShieldRepository.state.value.language
        val title = when (language) { "en" -> "Protection is incomplete"; "ar" -> "الحماية غير مكتملة"; else -> "پاراستن تەواو نییە" }
        val summary = when (language) {
            "en" -> "Accessibility is off. App and keyword blocking is paused."
            "ar" -> "إمكانية الوصول متوقفة. تم إيقاف حظر التطبيقات والكلمات."
            else -> "Accessibility ناچالاکە. بلۆککردنی ئەپ و وشە وەستاوە."
        }
        val details = when (language) {
            "en" -> "BlockX LaAbrah can no longer monitor apps, browsers or typed keywords. Tap to turn the accessibility service back on."
            "ar" -> "لم يعد BlockX LaAbrah قادراً على مراقبة التطبيقات أو المتصفحات أو الكلمات المكتوبة. اضغط لإعادة تشغيل خدمة إمكانية الوصول."
            else -> "BlockX LaAbrah چیتر ناتوانێت ئەپ و براوزەر و وشە نووسراوەکان بپشکنێت. بۆ چالاککردنەوەی Accessibility دەست بنێ."
        }
        val settings = PendingIntent.getActivity(
            this,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_app)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(settings)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(ALERT_ID, notification)
        }
    }

    /** High-priority alert shown the moment the device-admin lock is revoked while armed. */
    private fun notifyDeviceAdminLost() {
        val language = ShieldRepository.state.value.language
        val title = when (language) { "en" -> "Protection is incomplete"; "ar" -> "الحماية غير مكتملة"; else -> "پاراستن تەواو نییە" }
        val summary = when (language) {
            "en" -> "Device admin is off. Uninstall protection is weakened."
            "ar" -> "مدير الجهاز متوقف. حماية منع إلغاء التثبيت أصبحت أضعف."
            else -> "Device Admin ناچالاکە. پاراستنی سڕینەوە لاوازە."
        }
        val details = when (language) {
            "en" -> "Without device-admin rights the app can be removed without the lock. Tap to re-activate the administrator and restore full uninstall protection."
            "ar" -> "بدون صلاحيات مدير الجهاز يمكن حذف التطبيق دون القفل. اضغط لإعادة تفعيل المدير واستعادة حماية منع الإزالة كاملة."
            else -> "بێ مافی Device Admin ئەپەکە دەتوانرێت بەبێ قوفڵ بسڕدرێتەوە. بۆ چالاککردنەوەی بەڕێوەبەر دەست بنێ بۆ گەڕانەوەی تەواوی پاراستنی سڕینەوە."
        }
        // Tap opens the official admin-activation screen for this app directly (system UI).
        val activate = PendingIntent.getActivity(
            this,
            2,
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    ComponentName(this, com.agon.app.admin.ShieldDeviceAdminReceiver::class.java),
                )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_app)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(activate)
            .build()
        runCatching {
            getSystemService(NotificationManager::class.java)?.notify(ALERT_ADMIN_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL = "protection_guardian_min"
        private const val LEGACY_CHANNEL = "protection_guardian"
        const val ALERT_CHANNEL = "protection_alerts"
        const val ID = 42
        const val ALERT_ID = 43
        const val ALERT_ADMIN_ID = 44
        /** Faster tick so a killed engine is repaired within minutes, not tens of minutes. */
        const val SUPERVISION_INTERVAL_MS = 8_000L

        /** Minimum gap between two power-save recovery runs (a flip bursts through channels). */
        const val POWER_SAVE_RECOVERY_DEBOUNCE_MS = 2_000L
    }
}
