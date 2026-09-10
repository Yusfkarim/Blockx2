package com.agon.app.blocklist.domain

import android.graphics.drawable.Drawable

enum class RuleType { WEBSITE, APP, KEYWORD }
enum class ListMode { BLOCK, ALLOW }
enum class RuleSort { RECENT, OLDEST, AZ, ZA }

data class BlockRule(val id: Long, val value: String, val label: String, val type: RuleType, val mode: ListMode, val createdAt: Long, val updatedAt: Long, val blockUntil: Long = 0)
data class InstalledApp(val packageName: String, val name: String, val icon: Drawable)

data class BlocklistUiState(
    val loading: Boolean = true,
    val enabled: Boolean = true,
    val mode: ListMode = ListMode.BLOCK,
    val type: RuleType? = null,
    val query: String = "",
    val sort: RuleSort = RuleSort.RECENT,
    val rules: List<BlockRule> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
)
