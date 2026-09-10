package com.agon.app.blocklist.data

import android.content.Context

/**
 * Persistent toggle for "Force SafeSearch" (إجبار البحث الآمن): DNS-time rewrite of Google,
 * Bing and DuckDuckGo answers to their provider-operated safe endpoints, honoured by every
 * browser because the safe address arrives as the answer to the name the user asked for.
 * ON by default so upgrading installs keep the existing family-filtering behaviour.
 */
object SafeSearchForceStore {

    private const val PREFS = "safe_search_force"
    private const val KEY_ENABLED = "enabled"

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** True while search-engine answers must be rewritten to the safe endpoints. */
    fun isEnabled(): Boolean = prefs?.getBoolean(KEY_ENABLED, true) ?: true

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }
}
