package com.agon.app.timelimit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.domain.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One configured app limit joined with today's usage, ready for display. */
data class TimeLimitEntry(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val limitMinutes: Int,
    val usedMillis: Long,
    val createdAtMillis: Long = 0L,
) {
    val usedMinutes: Int get() = (usedMillis / AppTimeLimitStore.MINUTE_MS).toInt()
    val remainingMinutes: Int
        get() = kotlin.math.ceil(
            (limitMinutes * AppTimeLimitStore.MINUTE_MS - usedMillis).coerceAtLeast(0L) /
                AppTimeLimitStore.MINUTE_MS.toDouble(),
        ).toInt()
    val progress: Float
        get() = if (limitMinutes <= 0) 0f else {
            (usedMillis.toFloat() / (limitMinutes * AppTimeLimitStore.MINUTE_MS)).coerceIn(0f, 1f)
        }
    val exhausted: Boolean get() = usedMillis >= limitMinutes * AppTimeLimitStore.MINUTE_MS
}

data class TimeLimitUiState(
    val loading: Boolean = true,
    val entries: List<TimeLimitEntry> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
)

/**
 * Bridges [AppTimeLimitStore] (plain SharedPreferences) with the Compose UI. It resolves app
 * labels/icons from the same package-manager query used by the blocklist, then joins them with
 * the configured limits and today's usage. State is refreshed on demand — there is no polling.
 */
@HiltViewModel
class TimeLimitViewModel @Inject constructor(
    application: Application,
    private val blocklistRepository: BlocklistRepository,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(TimeLimitUiState())
    val state: StateFlow<TimeLimitUiState> = _state.asStateFlow()

    private var installedCache: List<InstalledApp> = emptyList()

    init {
        AppTimeLimitStore.initialize(application)
        refresh()
    }

    /** Reloads installed apps (once) and rebuilds the limit list with fresh usage. */
    fun refresh() {
        viewModelScope.launch {
            if (installedCache.isEmpty()) {
                installedCache = runCatching { blocklistRepository.installedApps() }.getOrDefault(emptyList())
            }
            rebuild()
        }
    }

    /** Recomputes only the derived entries from the store (cheap, no package-manager work). */
    private fun rebuild() {
        val limits = AppTimeLimitStore.limits()
        val usage = AppTimeLimitStore.usageMillisToday()
        val byPackage = installedCache.associateBy { it.packageName }
        val entries = limits.entries
            .map { (pkg, minutes) ->
                val app = byPackage[pkg]
                TimeLimitEntry(
                    packageName = pkg,
                    appName = app?.name ?: pkg,
                    icon = app?.icon,
                    limitMinutes = minutes,
                    usedMillis = usage[pkg] ?: 0L,
                    createdAtMillis = AppTimeLimitStore.createdAt(pkg),
                )
            }
            .sortedBy { it.appName.lowercase() }
        _state.value = TimeLimitUiState(
            loading = false,
            entries = entries,
            installedApps = installedCache.sortedBy { it.name.lowercase() },
        )
    }

    fun setLimit(packageName: String, minutes: Int) {
        AppTimeLimitStore.setLimit(packageName, minutes)
        rebuild()
    }

    fun removeLimit(packageName: String) {
        AppTimeLimitStore.setLimit(packageName, 0)
        rebuild()
    }

    /** Grants more time today by clearing the consumed minutes for one app. */
    fun resetUsage(packageName: String) {
        AppTimeLimitStore.resetUsage(packageName)
        rebuild()
    }
}
