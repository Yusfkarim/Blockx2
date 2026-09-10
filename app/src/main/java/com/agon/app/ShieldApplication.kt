package com.agon.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.domain.BuiltInAdultDomains
import com.agon.app.blocklist.domain.BuiltInAdultKeywords
import com.agon.app.data.ShieldRepository
import com.agon.app.services.KeepAliveScheduler
import com.agon.app.services.ProtectionGuardianService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShieldApplication : Application() {
    @Inject lateinit var blocklistRepository: BlocklistRepository
    @Inject lateinit var uninstallProtection: com.agon.app.admin.UninstallProtectionManager
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        // Background-first protection must never surface the system "internal error" crash
        // dialog: schedule the watchdog, re-arm the service chain and retire the process
        // quietly — the guardian restarts protection on the next tick.
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            runCatching { KeepAliveScheduler.schedule(this) }
            runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, ProtectionGuardianService::class.java),
                )
            }
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        super.onCreate()
        // Cancel scope on process termination to prevent coroutine leaks.
        Runtime.getRuntime().addShutdownHook(Thread { applicationScope.cancel() })
        ShieldRepository.initialize(this)
        com.agon.app.applock.AppLockManager.initialize(this)
        com.agon.app.timelimit.AppTimeLimitStore.initialize(this)
        com.agon.app.blocklist.data.YoutubeRestrictStore.initialize(this)
        com.agon.app.blocklist.data.SafeSearchForceStore.initialize(this)
        com.agon.app.motivation.StreakStore.initialize(this)
        com.agon.app.blocklist.data.TimedBlockingStore.initialize(this)
        BuiltInAdultDomains.initialize(this)
        BuiltInAdultKeywords.initialize(this)
        applicationScope.launch { blocklistRepository.refreshEngine() }
        // Re-assert device-owner uninstall locks on every cold start; a no-op otherwise.
        applicationScope.launch { runCatching { uninstallProtection.applyStrongestProtection() } }

        // Every process start (cold launch, accessibility reconnect, alarm wake) must bring the
        // guardian back and arm the keep-alive schedule so keyword protection cannot stay dead.
        com.agon.app.health.NotificationHealth.ensureChannels(this)
        KeepAliveScheduler.schedule(this)
        runCatching { com.agon.app.services.KeepAliveWorker.enqueue(this) }
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, ProtectionGuardianService::class.java),
            )
        }

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Schedule a restart before the process dies so protection recovers after a crash.
            runCatching { KeepAliveScheduler.schedule(applicationContext) }
            runCatching { KeepAliveScheduler.ensureProtectionRunning(applicationContext) }
            previous?.uncaughtException(thread, error)
                ?: run {
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
        }
    }
}
