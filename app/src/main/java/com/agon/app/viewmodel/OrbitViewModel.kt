package com.agon.app.viewmodel

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agon.app.data.FocusTask
import com.agon.app.data.OrbitPreferences
import com.agon.app.data.OrbitRepository
import com.agon.app.data.SampleTasks
import com.agon.app.data.TaskPriority
import com.agon.app.data.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch


data class OrbitUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val tasks: List<FocusTask> = emptyList(),
    val preferences: OrbitPreferences = OrbitPreferences(),
    val focusRemainingSeconds: Int = 25 * 60,
    val focusTotalSeconds: Int = 25 * 60,
    val focusTaskId: Long? = null,
    val isTimerRunning: Boolean = false,
    val sessionsCompleted: Int = 2,
)

class OrbitViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OrbitRepository(application)
    private val _uiState = mutableStateOf(OrbitUiState())
    val uiState: State<OrbitUiState> = _uiState
    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.data
                .catch { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = error.message ?: "Your workspace could not be loaded.",
                    )
                }
                .collect { data ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadError = null,
                        tasks = data.tasks,
                        preferences = data.preferences,
                    )
                }
        }
    }

    fun addTask(
        title: String,
        category: String,
        minutes: Int,
        priority: TaskPriority,
    ) {
        if (title.isBlank()) return
        updateTasks { current ->
            current + FocusTask(
                id = (current.maxOfOrNull { it.id } ?: 0L) + 1L,
                title = title.trim(),
                category = category,
                estimatedMinutes = minutes.coerceIn(5, 120),
                priority = priority,
                dueLabel = "Today",
            )
        }
    }

    fun toggleTask(taskId: Long) {
        updateTasks { tasks -> tasks.map { if (it.id == taskId) it.copy(completed = !it.completed) else it } }
    }

    fun deleteTask(taskId: Long) {
        if (_uiState.value.focusTaskId == taskId) resetTimer()
        updateTasks { tasks -> tasks.filterNot { it.id == taskId } }
    }

    fun startTaskFocus(taskId: Long) {
        val task = _uiState.value.tasks.firstOrNull { it.id == taskId } ?: return
        timerJob?.cancel()
        val total = task.estimatedMinutes * 60
        _uiState.value = _uiState.value.copy(
            focusTaskId = taskId,
            focusTotalSeconds = total,
            focusRemainingSeconds = total,
            isTimerRunning = true,
        )
        launchCountdown()
    }

    fun toggleTimer() {
        if (_uiState.value.isTimerRunning) {
            timerJob?.cancel()
            _uiState.value = _uiState.value.copy(isTimerRunning = false)
        } else {
            if (_uiState.value.focusRemainingSeconds <= 0) {
                _uiState.value = _uiState.value.copy(
                    focusRemainingSeconds = _uiState.value.focusTotalSeconds,
                )
            }
            _uiState.value = _uiState.value.copy(isTimerRunning = true)
            launchCountdown()
        }
    }

    fun resetTimer() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isTimerRunning = false,
            focusRemainingSeconds = _uiState.value.focusTotalSeconds,
        )
    }

    private fun launchCountdown() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_uiState.value.isTimerRunning && _uiState.value.focusRemainingSeconds > 0) {
                delay(1_000)
                val next = (_uiState.value.focusRemainingSeconds - 1).coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(focusRemainingSeconds = next)
                if (next == 0) {
                    _uiState.value = _uiState.value.copy(
                        isTimerRunning = false,
                        sessionsCompleted = _uiState.value.sessionsCompleted + 1,
                    )
                }
            }
        }
    }

    fun updateTheme(mode: ThemeMode) = viewModelScope.launch { repository.updateTheme(mode) }
    fun updateNotifications(enabled: Boolean) = viewModelScope.launch { repository.updateNotifications(enabled) }
    fun updateSound(enabled: Boolean) = viewModelScope.launch { repository.updateSound(enabled) }
    fun updateHaptics(enabled: Boolean) = viewModelScope.launch { repository.updateHaptics(enabled) }
    fun updateDailyGoal(goal: Int) = viewModelScope.launch { repository.updateDailyGoal(goal) }

    fun resetAll() {
        timerJob?.cancel()
        _uiState.value = OrbitUiState(isLoading = false, tasks = SampleTasks)
        viewModelScope.launch { repository.resetAll() }
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(loadError = null)
    }

    private fun updateTasks(transform: (List<FocusTask>) -> List<FocusTask>) {
        val updated = transform(_uiState.value.tasks)
        _uiState.value = _uiState.value.copy(tasks = updated)
        viewModelScope.launch { repository.saveTasks(updated) }
    }
}
