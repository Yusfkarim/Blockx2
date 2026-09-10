package com.agon.app.blocklist.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.blocklist.data.BlocklistRepository
import com.agon.app.blocklist.domain.BlockRule
import com.agon.app.blocklist.domain.BlocklistUiState
import com.agon.app.blocklist.domain.InstalledApp
import com.agon.app.blocklist.domain.ListMode
import com.agon.app.blocklist.domain.RuleSort
import com.agon.app.blocklist.domain.RuleType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlocklistViewModel @Inject constructor(private val repository: BlocklistRepository) : ViewModel() {
    private val controls = MutableStateFlow(BlocklistUiState())
    val state = combine(repository.data, controls) { (allRules, enabled), control ->
        val filtered = allRules.asSequence().filter { it.mode == control.mode }.filter { control.type == null || it.type == control.type }.filter { control.query.isBlank() || it.value.contains(control.query, true) || it.label.contains(control.query, true) }.let { sequence ->
            when (control.sort) { RuleSort.RECENT -> sequence.sortedByDescending { it.createdAt }; RuleSort.OLDEST -> sequence.sortedBy { it.createdAt }; RuleSort.AZ -> sequence.sortedBy { it.label.lowercase() }; RuleSort.ZA -> sequence.sortedByDescending { it.label.lowercase() } }
        }.toList()
        control.copy(loading = false, enabled = enabled, rules = filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BlocklistUiState())

    fun setMode(mode: ListMode) { controls.value = controls.value.copy(mode = mode, selectedIds = emptySet()) }
    fun setType(type: RuleType?) { controls.value = controls.value.copy(type = type, selectedIds = emptySet()) }
    fun setQuery(query: String) { controls.value = controls.value.copy(query = query) }
    fun setSort(sort: RuleSort) { controls.value = controls.value.copy(sort = sort) }
    fun toggleSelection(id: Long) { val selected = controls.value.selectedIds; controls.value = controls.value.copy(selectedIds = if (id in selected) selected - id else selected + id) }
    fun clearSelection() { controls.value = controls.value.copy(selectedIds = emptySet()) }
    fun add(values: List<Pair<String, String>>, type: RuleType, blockDays: Int = 0) { viewModelScope.launch { repository.add(values, type, controls.value.mode, blockDays) } }
    fun update(rule: BlockRule, value: String) { viewModelScope.launch { repository.update(rule, value) } }
    fun delete(rules: List<BlockRule>, after: (List<BlockRule>) -> Unit = {}) { viewModelScope.launch { repository.delete(rules); controls.value = controls.value.copy(selectedIds = emptySet()); after(rules) } }
    fun restore(rules: List<BlockRule>) { viewModelScope.launch { repository.restore(rules) } }

    /** Timed App Lock: strict, auto-expiring; the store's tamper-proof clock owns the window. */
    fun addTimedAppLock(packageName: String, label: String, durationMs: Long) {
        viewModelScope.launch { repository.addTimedAppLock(packageName, label, durationMs) }
    }
    suspend fun installedApps(): List<InstalledApp> = repository.installedApps()
}
