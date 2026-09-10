package com.agon.app.blocklist.domain

import android.content.Context

/** Always-on, memory-mapped domain index generated from the supplied site list. */
object BuiltInAdultDomains {
    /**
     * Small set of always-blocked hosts that are not (yet) present in the bundled hash index.
     * Checked with the same suffix walk as [AssetDomainHashIndex] so subdomains also match.
     */
    private val EXTRA_BLOCKED_DOMAINS = hashSetOf(
        "enjoyvideo.top",
    )

    @Volatile
    private var index: AssetDomainHashIndex = AssetDomainHashIndex.EMPTY

    @Synchronized
    fun initialize(context: Context) {
        if (!index.isEmpty) return
        index = AssetDomainHashIndex.load(context)
    }

    val size: Int get() = index.size + EXTRA_BLOCKED_DOMAINS.size

    fun matches(domain: String): Boolean {
        if (matchesExtra(domain)) return true
        return index.matches(domain)
    }

    private fun matchesExtra(rawDomain: String): Boolean {
        if (EXTRA_BLOCKED_DOMAINS.isEmpty()) return false
        var candidate = TextNormalizer.normalizeDomain(rawDomain)
        if (candidate.isEmpty() || !candidate.contains('.')) return false
        while (candidate.contains('.')) {
            if (candidate in EXTRA_BLOCKED_DOMAINS) return true
            candidate = candidate.substringAfter('.', missingDelimiterValue = "")
            if (candidate.isEmpty()) return false
        }
        return false
    }
}
