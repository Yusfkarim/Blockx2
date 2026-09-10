package com.agon.app.settingsprotection.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.settingsprotection.data.SettingsProtectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Verifies the configured credential for protected actions without changing Device Admin state. */
@HiltViewModel
class AdminDisableViewModel @Inject constructor(
    private val repository: SettingsProtectionRepository,
) : ViewModel() {

    val pinConfigured: StateFlow<Boolean> = repository.snapshot
        .map { it.active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repository.currentSnapshot().active)

    /** Returns true when [pin] is correct without changing Device Admin state. */
    suspend fun verify(pin: String): Boolean = repository.verifyPin(pin)
}
