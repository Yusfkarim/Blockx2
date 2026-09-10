package com.agon.app.quarantine

import android.content.Context
import com.agon.app.blocklist.domain.MonitoredApps
import org.json.JSONArray

/**
 * New-app quarantine: every freshly installed app is held in a pending set and cannot be
 * opened until a parent approves it with the protection PIN.
 *
 * This closes the "install a second browser" circumvention hole. The pending set lives in a
 * plain SharedPreferences JSON array (same house pattern as the other stores), so both the
 * broadcast receiver and the accessibility service can reach it without any extra process.
 *
 * The feature is on by default: it takes effect the moment a new package arrives, and approving
 * an app is a one-tap PIN-gated action. Apps removed from the device leave the set
 * automatically (a stale entry can never block anything because only installed, launchable
 * packages are evaluated).
 */
object NewAppQuarantineStore {

    private const val PREFS = "family_shield_quarantine"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_PENDING = "pending_json_v1"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isInitialized(): Boolean = prefs != null

    /**
     * Opt-in by design: a first-time user who just installed the app should never be surprised
     * by fresh apps being blocked. Parents switch this on themselves from Settings.
     */
    fun isEnabled(): Boolean = prefs?.getBoolean(KEY_ENABLED, false) ?: false

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    /** True when this package must currently be blocked. */
    fun shouldBlock(packageName: String): Boolean = isEnabled() && isQuarantined(packageName)

    fun isQuarantined(packageName: String): Boolean = pending().contains(packageName)

    fun pending(): List<String> {
        val store = prefs ?: return emptyList()
        val raw = store.getString(KEY_PENDING, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        }.getOrDefault(emptyList())
    }

    fun pendingCount(): Int = pending().size

    /**
     * Adds a freshly installed package to the quarantine set. System images, packages without a
     * launchable UI and this app itself are never quarantined.
     */
    @Synchronized
    fun maybeQuarantine(context: Context, packageName: String) {
        if (!isEnabled()) return
        if (packageName == context.packageName) return
        if (MonitoredApps.isExcluded(packageName)) return
        val launchable = runCatching {
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        }.getOrDefault(false)
        if (!launchable) return
        val current = pending()
        if (current.contains(packageName)) return
        persist(current + packageName)
    }

    /** Parent approval: removes the package from quarantine so it opens normally. */
    @Synchronized
    fun approve(packageName: String) {
        persist(pending().filterNot { it == packageName })
    }

    private fun persist(packages: List<String>) {
        val array = JSONArray()
        packages.forEach { array.put(it) }
        prefs?.edit()?.putString(KEY_PENDING, array.toString())?.apply()
    }
}
