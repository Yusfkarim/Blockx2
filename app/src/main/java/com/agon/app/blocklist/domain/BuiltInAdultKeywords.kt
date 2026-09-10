package com.agon.app.blocklist.domain

import android.content.Context

/** Always-on keyword rules bundled as a compact UTF-8 asset and compiled once at startup. */
object BuiltInAdultKeywords {
    @Volatile
    private var index: KeywordIndex = KeywordIndex.EMPTY

    @Synchronized
    fun initialize(context: Context) {
        if (!index.isEmpty) return
        val keywords = runCatching {
            context.assets.open("blocked_keywords.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(String::trim).filter(String::isNotEmpty).toList()
            }
        }.getOrDefault(emptyList())
        index = KeywordIndex.build(keywords)
    }

    val isEmpty: Boolean get() = index.isEmpty

    fun firstMatch(normalizedText: String): String? = index.firstMatch(normalizedText)
}
