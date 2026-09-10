package com.agon.app.health

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.agon.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Optional parent webhook. If a URL is saved, posts JSON when protection drops.
 * Without a URL, raises a high-priority local notification only (no backend required).
 */
object ParentAlertStore {

    private const val PREFS = "parent_alert"
    private const val KEY_URL = "webhook_url"
    private const val KEY_ENABLED = "enabled"
    private const val CHANNEL = "parent_alerts"
    private const val NOTIF_ID = 91

    fun setWebhookUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url.trim())
            .putBoolean(KEY_ENABLED, url.isNotBlank())
            .apply()
    }

    fun getWebhookUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "").orEmpty()

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    suspend fun notifyProtectionDown(context: Context, reason: String) {
        showLocal(context, reason)
        val url = getWebhookUrl(context)
        if (url.isBlank()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject()
                    .put("event", "protection_down")
                    .put("reason", reason)
                    .put("package", context.packageName)
                    .put("time", System.currentTimeMillis())
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val client = OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder().url(url).post(body).build()
                client.newCall(req).execute().close()
            }
        }
    }

    private fun showLocal(context: Context, reason: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Parent alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_app)
            .setContentTitle("BlockX LaAbrah")
            .setContentText(reason)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIF_ID, n) }
    }
}
