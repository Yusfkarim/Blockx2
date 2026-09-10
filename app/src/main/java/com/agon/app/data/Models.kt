package com.agon.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class TaskPriority { LOW, MEDIUM, HIGH }

@Serializable
data class FocusTask(
    val id: Long,
    val title: String,
    val category: String,
    val estimatedMinutes: Int,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueLabel: String = "Today",
    val completed: Boolean = false,
)

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class OrbitPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val dailyGoal: Int = 4,
)

data class OrbitData(
    val tasks: List<FocusTask> = emptyList(),
    val preferences: OrbitPreferences = OrbitPreferences(),
)

val SampleTasks = listOf(
    FocusTask(1, "Shape the product brief", "Deep work", 40, TaskPriority.HIGH, "9:30 AM"),
    FocusTask(2, "Review weekly metrics", "Planning", 25, TaskPriority.MEDIUM, "11:00 AM"),
    FocusTask(3, "Reply to project notes", "Admin", 15, TaskPriority.LOW, "2:00 PM"),
    FocusTask(4, "Outline tomorrow's priorities", "Planning", 20, TaskPriority.MEDIUM, "4:30 PM"),
)
