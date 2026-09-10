package com.agon.app.blocklist.data

import android.content.Context
import com.agon.app.blocklist.domain.BlockingType
import com.agon.app.blocklist.domain.TimedBlockingIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent, process-wide source of truth for the "block VPN / circumvention apps" protection.
 *
 * Duration / lock / expiry are owned by [TimedBlockingStore].
 */
@Singleton
class VpnProtectionPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "vpn_protection",
        Context.MODE_PRIVATE,
    )

    private val _state = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, false))
    val state: StateFlow<Boolean> = _state.asStateFlow()

    init {
        sync()
    }

    /** True while VPN / circumvention-app blocking is on (and not yet expired). */
    fun isEnabled(): Boolean {
        sync()
        return preferences.getBoolean(KEY_ENABLED, false)
    }

    fun remainingMillis(): Long = TimedBlockingStore.remainingMillis(appContext, TimedBlockingIds.vpn())

    fun isLocked(): Boolean = TimedBlockingStore.isLocked(appContext, TimedBlockingIds.vpn())

    @Synchronized
    fun setEnabled(enabled: Boolean, blockDays: Int = 0, durationMs: Long = 0L) {
        val id = TimedBlockingIds.vpn()
        if (!enabled) {
            if (!TimedBlockingStore.tryDisable(appContext, id)) return
            preferences.edit().putBoolean(KEY_ENABLED, false).remove(KEY_EXPIRES_AT).apply()
            _state.value = false
            return
        }
        val editor = preferences.edit().putBoolean(KEY_ENABLED, true)
        val ms = when {
            durationMs > 0L -> durationMs
            blockDays > 0 -> blockDays.toLong() * DAY_MILLIS
            else -> 0L
        }
        if (ms > 0L) {
            TimedBlockingStore.startMs(
                appContext,
                id = id,
                type = BlockingType.VPN,
                target = "vpn_apps",
                durationMs = ms,
            )
            editor.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + ms)
        } else {
            editor.remove(KEY_EXPIRES_AT)
        }
        editor.apply()
        _state.value = true
    }

    private fun sync() {
        if (!preferences.getBoolean(KEY_ENABLED, false)) return
        val id = TimedBlockingIds.vpn()
        if (TimedBlockingStore.has(appContext, id)) {
            if (!TimedBlockingStore.isLocked(appContext, id) &&
                TimedBlockingStore.remainingMillis(appContext, id) <= 0L
            ) {
                preferences.edit().putBoolean(KEY_ENABLED, false).remove(KEY_EXPIRES_AT).apply()
                TimedBlockingStore.clear(appContext, id)
                _state.value = false
            }
            return
        }
        val expiresAt = preferences.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt > 0L) {
            val adopted = TimedBlockingStore.adoptLegacy(
                appContext,
                id = id,
                type = BlockingType.VPN,
                target = "vpn_apps",
                blockUntil = expiresAt,
            )
            if (adopted == null) {
                preferences.edit().putBoolean(KEY_ENABLED, false).remove(KEY_EXPIRES_AT).apply()
                _state.value = false
            }
        }
    }

    private companion object {
        const val KEY_ENABLED = "vpn_block_enabled_v1"
        const val KEY_EXPIRES_AT = "vpn_block_expires_at_v1"
        const val DAY_MILLIS = 86_400_000L
    }
}
