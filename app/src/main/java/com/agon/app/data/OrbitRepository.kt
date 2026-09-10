package com.agon.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.orbitDataStore by preferencesDataStore(name = "orbit_preferences")

class OrbitRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<OrbitData> = context.orbitDataStore.data.map { values ->
        val tasks = values[Keys.tasks]?.let { encoded ->
            runCatching { json.decodeFromString<List<FocusTask>>(encoded) }.getOrNull()
        } ?: SampleTasks
        val themeMode = runCatching {
            ThemeMode.valueOf(values[Keys.themeMode] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)

        OrbitData(
            tasks = tasks,
            preferences = OrbitPreferences(
                themeMode = themeMode,
                notificationsEnabled = values[Keys.notifications] ?: true,
                soundEnabled = values[Keys.sound] ?: true,
                hapticsEnabled = values[Keys.haptics] ?: true,
                dailyGoal = values[Keys.dailyGoal] ?: 4,
            ),
        )
    }

    suspend fun saveTasks(tasks: List<FocusTask>) {
        context.orbitDataStore.edit { it[Keys.tasks] = json.encodeToString(tasks) }
    }

    suspend fun updateTheme(mode: ThemeMode) {
        context.orbitDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun updateNotifications(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.notifications] = enabled }
    }

    suspend fun updateSound(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.sound] = enabled }
    }

    suspend fun updateHaptics(enabled: Boolean) {
        context.orbitDataStore.edit { it[Keys.haptics] = enabled }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.orbitDataStore.edit { it[Keys.dailyGoal] = goal.coerceIn(1, 8) }
    }

    suspend fun resetAll() {
        context.orbitDataStore.edit { values ->
            values.clear()
            values[Keys.tasks] = json.encodeToString(SampleTasks)
        }
    }

    private object Keys {
        val tasks = stringPreferencesKey("tasks_json")
        val themeMode = stringPreferencesKey("theme_mode")
        val notifications = booleanPreferencesKey("notifications")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val dailyGoal = intPreferencesKey("daily_goal")
    }
}
