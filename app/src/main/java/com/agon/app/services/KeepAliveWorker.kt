package com.agon.app.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.agon.app.health.ProtectionHealth
import java.util.concurrent.TimeUnit

/** WorkManager belt alongside AlarmManager — survives some OEM deferrals better when combined. */
class KeepAliveWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        KeepAliveScheduler.ensureProtectionRunning(applicationContext)
        ProtectionHealth.restartGuardian(applicationContext)
        KeepAliveScheduler.scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "dira_keepalive_periodic"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
