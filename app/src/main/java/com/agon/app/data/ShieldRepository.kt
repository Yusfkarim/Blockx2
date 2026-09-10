package com.agon.app.data

import android.content.Context
import com.agon.app.security.TamperProofClock
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ShieldRepository {
    private lateinit var appContext: Context
    private lateinit var prefs: android.content.SharedPreferences

    /**
     * Last foreground package seen by the accessibility service. Lets the DNS tunnel scope its
     * block overlay to active browsing moments instead of interrupting the user when another
     * app's background SDK (analytics, ads) silently resolves a listed domain.
     */
    @Volatile
    var foregroundPackage: String? = null
    private lateinit var database: ShieldDatabase
    private val _state = MutableStateFlow(ShieldState())
    val state: StateFlow<ShieldState> = _state.asStateFlow()

    // DNS resolution is a hot path (hundreds of lookups per minute). The running request total is
    // kept in memory and flushed to disk at most once per REQUEST_PERSIST_INTERVAL_MS, so the hot
    // path performs no SQLite query and no per-request SharedPreferences write.
    private val requestCounter = java.util.concurrent.atomic.AtomicLong(0)
    @Volatile private var lastRequestPersistAt = 0L

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("family_shield", Context.MODE_PRIVATE)
        database = ShieldDatabase(appContext)
        requestCounter.set(prefs.getLong("requests", 0))
        refresh()
    }

    fun refresh() {
        if (!::prefs.isInitialized) return
        _state.value = ShieldState(
            vpnEnabled = prefs.getBoolean("vpn_enabled", false),
            vpnRunning = prefs.getBoolean("vpn_running", false),
            accessibilityEnabled = isAccessibilityEnabled(),
            autoStart = prefs.getBoolean("auto_start", true),
            theme = prefs.getString("theme", "system") ?: "system",
            language = prefs.getString("language", "ku") ?: "ku",
            filteredRequests = requestCounter.get(),
            blockedDomains = prefs.getLong("blocked", 0),
            startedAt = prefs.getLong("started_at", 0),
            pausedUntil = prefs.getLong("paused_until", 0),
            logs = database.recentLogs(),
        )
    }

    /**
     * Temporary protection pause. When set, protection self-healing and background blocking are
     * suspended for at most three minutes after successful credential verification. Protection
     * resumes automatically when the window expires.
     */
    fun pauseProtection(durationMillis: Long = PAUSE_MAX_MILLIS) {
        if (!::prefs.isInitialized) return
        if (com.agon.app.settingsprotection.data.ProtectionCommitmentStore.isStrongActive(appContext)) return
        // VPN Safe Browsing lock is independent — pause of general protection is still allowed.
        // The extended duration dialog deliberately widens the pause window up to "forever"
        // (DURATION_FOREVER == -1L); every other value is clamped to the extended maximum.
        val capped = when {
            durationMillis == com.agon.app.settingsprotection.ui.DURATION_FOREVER -> Long.MAX_VALUE
            else -> durationMillis.coerceIn(0L, PAUSE_EXTENDED_MAX_MILLIS)
        }
        // Never add the clock to Long.MAX_VALUE (overflow): the sentinel is stored directly.
        val untilMillis = if (capped == Long.MAX_VALUE) Long.MAX_VALUE else TamperProofClock.now(appContext) + capped
        // Tamper-proof: moving the device clock must not shorten or stretch a pause window.
        prefs.edit().putLong("paused_until", untilMillis).apply()
        refresh()
    }

    /** Ends the pause early and brings protection back immediately. */
    fun resumeProtection() {
        if (!::prefs.isInitialized) return
        prefs.edit().putLong("paused_until", 0L).apply()
        refresh()
    }

    /** True while a valid, non-expired protection pause is active. */
    fun isProtectionPaused(now: Long = if (::appContext.isInitialized) TamperProofClock.now(appContext) else System.currentTimeMillis()): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.getLong("paused_until", 0L) > now
    }

    /** Milliseconds remaining in the current pause window, or 0 when not paused. */
    fun pauseRemainingMillis(now: Long = if (::appContext.isInitialized) TamperProofClock.now(appContext) else System.currentTimeMillis()): Long {
        if (!::prefs.isInitialized) return 0L
        return (prefs.getLong("paused_until", 0L) - now).coerceAtLeast(0L)
    }

    fun setVpnDesired(enabled: Boolean) {
        // VPN is fully independent of Standard/Strong protection.
        // Only the Safe Browsing commitment timer may refuse disconnect.
        if (!enabled && ::appContext.isInitialized &&
            com.agon.app.vpn.CommitmentTimerModule.isActive(appContext)
        ) {
            return
        }
        prefs.edit().putBoolean("vpn_enabled", enabled).apply()
        refresh()
    }
    fun setVpnRunning(running: Boolean) {
        val editor = prefs.edit().putBoolean("vpn_running", running)
        if (running && prefs.getLong("started_at", 0) == 0L) editor.putLong("started_at", System.currentTimeMillis())
        if (!running) editor.putLong("started_at", 0)
        editor.apply(); refresh()
    }
    fun setAutoStart(value: Boolean) { prefs.edit().putBoolean("auto_start", value).apply(); refresh() }
    fun setTheme(value: String) { prefs.edit().putString("theme", value).apply(); refresh() }
    fun setLanguage(value: String) { prefs.edit().putString("language", value).apply(); refresh() }
    fun recordRequest() {
        if (!::prefs.isInitialized) return
        val total = requestCounter.incrementAndGet()
        // A request never changes the log list, so reflect only the new count and skip the
        // recentLogs() query that refresh() would otherwise run on every single DNS lookup.
        _state.value = _state.value.copy(filteredRequests = total)
        val now = System.currentTimeMillis()
        if (now - lastRequestPersistAt >= REQUEST_PERSIST_INTERVAL_MS) {
            lastRequestPersistAt = now
            prefs.edit().putLong("requests", total).apply()
        }
    }
    fun recordBlocked(domain: String, source: String) {
        prefs.edit().putLong("blocked", prefs.getLong("blocked", 0) + 1).apply()
        database.addLog(domain.take(200), source)
        refresh()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(appContext.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains("${appContext.packageName}/com.agon.app.accessibility.ShieldAccessibilityService", ignoreCase = true)
    }

    /** Upper bound for a temporary protection pause: three minutes. */
    const val PAUSE_MAX_MILLIS = 3L * 60L * 1000L

    /** Upper bound for extended pauses (366 days); "forever" is handled separately. */
    const val PAUSE_EXTENDED_MAX_MILLIS = 366L * 24L * 60L * 60L * 1000L

    /** How often the in-memory request counter is persisted to disk from the DNS hot path. */
    private const val REQUEST_PERSIST_INTERVAL_MS = 5_000L
}

data class ShieldState(
    val vpnEnabled: Boolean = false,
    val vpnRunning: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val autoStart: Boolean = true,
    val theme: String = "system",
    val language: String = "ku",
    val filteredRequests: Long = 0,
    val blockedDomains: Long = 0,
    val startedAt: Long = 0,
    val pausedUntil: Long = 0,
    val logs: List<BlockLog> = emptyList(),
)

data class BlockLog(val id: Long, val domain: String, val source: String, val timestamp: Long) {
    fun dateLabel(): String = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private class ShieldDatabase(context: Context) : SQLiteOpenHelper(context, "shield.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE block_logs(id INTEGER PRIMARY KEY AUTOINCREMENT, domain TEXT NOT NULL, source TEXT NOT NULL, timestamp INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_block_time ON block_logs(timestamp DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun addLog(domain: String, source: String) {
        writableDatabase.execSQL("INSERT INTO block_logs(domain,source,timestamp) VALUES(?,?,?)", arrayOf(domain, source, System.currentTimeMillis()))
        writableDatabase.execSQL("DELETE FROM block_logs WHERE id NOT IN (SELECT id FROM block_logs ORDER BY timestamp DESC LIMIT 500)")
    }
    fun recentLogs(): List<BlockLog> {
        val result = mutableListOf<BlockLog>()
        readableDatabase.rawQuery("SELECT id,domain,source,timestamp FROM block_logs ORDER BY timestamp DESC LIMIT 100", null).use { cursor ->
            while (cursor.moveToNext()) result += BlockLog(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))
        }
        return result
    }
}
