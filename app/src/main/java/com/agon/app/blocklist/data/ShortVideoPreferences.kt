package com.agon.app.blocklist.data

import android.content.Context
import com.agon.app.blocklist.domain.BlockingType
import com.agon.app.blocklist.domain.ShortVideoPolicy
import com.agon.app.blocklist.domain.TimedBlockingIds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ShortVideoSettings(
    val enabledPlatforms: Set<ShortVideoPolicy.Platform>,
) {
    fun isEnabled(platform: ShortVideoPolicy.Platform): Boolean = platform in enabledPlatforms
}

/** Persistent, process-wide source of truth for per-platform short-video protection. */
@Singleton
class ShortVideoPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        "short_video_protection",
        Context.MODE_PRIVATE,
    )

    private val _state = MutableStateFlow(readSettings())
    val state: StateFlow<ShortVideoSettings> = _state.asStateFlow()

    init {
        // First launch: every platform starts DISABLED. The user must explicitly opt in to
        // short-video protection. Renewing install (clear data) honours the same default so
        // a kid device cannot inherit enabled protection from a previous parent decision.
        if (!preferences.getBoolean(KEY_SEEDED, false)) {
            val editor = preferences.edit()
            ShortVideoPolicy.Platform.entries.forEach { platform -> editor.putBoolean(key(platform), false) }
            editor.putBoolean(KEY_SEEDED, true)
            editor.apply()
        }
        migrateLegacyDurations()
        _state.value = readSettings()
    }

    fun isEnabled(platform: ShortVideoPolicy.Platform): Boolean {
        syncPlatform(platform)
        return preferences.getBoolean(key(platform), false)
    }

    fun remainingMillis(platform: ShortVideoPolicy.Platform): Long =
        TimedBlockingStore.remainingMillis(appContext, TimedBlockingIds.shortVideo(platform))

    fun isLocked(platform: ShortVideoPolicy.Platform): Boolean =
        TimedBlockingStore.isLocked(appContext, TimedBlockingIds.shortVideo(platform))

    /** True once the user has turned ON at least one platform at least once. */
    fun wasEverEnabled(): Boolean = preferences.getBoolean(KEY_EVER_ENABLED, false)

    @Synchronized
    fun setEnabled(
        platform: ShortVideoPolicy.Platform,
        enabled: Boolean,
        blockDays: Int = 0,
        durationMs: Long = 0L,
    ) {
        val id = TimedBlockingIds.shortVideo(platform)
        if (!enabled) {
            if (!TimedBlockingStore.tryDisable(appContext, id)) return
            preferences.edit().putBoolean(key(platform), false).remove(expiresAtKey(platform)).apply()
            _state.value = readSettings()
            return
        }
        val editor = preferences.edit().putBoolean(key(platform), true).putBoolean(KEY_EVER_ENABLED, true)
        val ms = when {
            durationMs > 0L -> durationMs
            blockDays > 0 -> blockDays.toLong() * DAY_MILLIS
            else -> 0L
        }
        if (ms > 0L) {
            TimedBlockingStore.startMs(
                appContext,
                id = id,
                type = BlockingType.SHORT_VIDEO,
                target = platform.name,
                durationMs = ms,
            )
            // Kept only as a legacy hint; enforcement uses TimedBlockingStore.
            editor.putLong(expiresAtKey(platform), System.currentTimeMillis() + ms)
        } else {
            editor.remove(expiresAtKey(platform))
        }
        editor.apply()
        _state.value = readSettings()
    }

    private fun syncPlatform(platform: ShortVideoPolicy.Platform) {
        if (!preferences.getBoolean(key(platform), false)) return
        val id = TimedBlockingIds.shortVideo(platform)
        if (TimedBlockingStore.has(appContext, id)) {
            if (!TimedBlockingStore.isLocked(appContext, id) &&
                TimedBlockingStore.remainingMillis(appContext, id) <= 0L
            ) {
                preferences.edit().putBoolean(key(platform), false).remove(expiresAtKey(platform)).apply()
                TimedBlockingStore.clear(appContext, id)
                _state.value = readSettings()
            }
            return
        }
        // Adopt a pre-upgrade wall-clock expiry once, then never trust wall-clock again.
        val expiresAt = preferences.getLong(expiresAtKey(platform), 0L)
        if (expiresAt > 0L) {
            val adopted = TimedBlockingStore.adoptLegacy(
                appContext,
                id = id,
                type = BlockingType.SHORT_VIDEO,
                target = platform.name,
                blockUntil = expiresAt,
            )
            if (adopted == null) {
                preferences.edit().putBoolean(key(platform), false).remove(expiresAtKey(platform)).apply()
                _state.value = readSettings()
            }
        }
    }

    private fun migrateLegacyDurations() {
        ShortVideoPolicy.Platform.entries.forEach { syncPlatform(it) }
    }

    private fun expiresAtKey(platform: ShortVideoPolicy.Platform): String =
        "${platform.name.lowercase()}_expires_at"

    private fun readSettings(): ShortVideoSettings = ShortVideoSettings(
        ShortVideoPolicy.Platform.entries
            .filter { preferences.getBoolean(key(it), false) }
            .toSet(),
    )

    private fun key(platform: ShortVideoPolicy.Platform): String =
        "${platform.name.lowercase()}_enabled"

    private companion object {
        const val KEY_SEEDED = "defaults_seeded_v1"
        const val KEY_EVER_ENABLED = "any_platform_ever_enabled_v1"
        const val DAY_MILLIS = 86_400_000L
    }
}
