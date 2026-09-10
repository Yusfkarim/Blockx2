package com.agon.app.blocklist.domain

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Immutable, lock-free decision engine shared by the VPN service and the accessibility service.
 *
 * Rules are compiled once per change into O(1)/O(n) lookup structures ([DomainIndex],
 * [KeywordIndex]) and published atomically, so hot paths never allocate, never take a lock and
 * never rescan the raw rule lists. Every public entry point of the previous implementation is
 * preserved so existing callers keep compiling unchanged.
 */
object BlockEngine {

    /**
     * Raw rule snapshot. Kept as the public input type for source compatibility with
     * `BlocklistRepository.updateEngine`.
     */
    data class Snapshot(
        val enabled: Boolean = true,
        val blockedWebsites: Set<String> = emptySet(),
        val allowedWebsites: Set<String> = emptySet(),
        val blockedApps: Set<String> = emptySet(),
        val allowedApps: Set<String> = emptySet(),
        val blockedKeywords: Set<String> = emptySet(),
        val allowedKeywords: Set<String> = emptySet(),
    )

    /** Compiled form of a [Snapshot]; everything here is read-only after construction. */
    private class Compiled(
        val enabled: Boolean,
        val blockedDomains: DomainIndex,
        val allowedDomains: DomainIndex,
        val blockedApps: Set<String>,
        val allowedApps: Set<String>,
        val blockedKeywords: KeywordIndex,
        val allowedKeywords: KeywordIndex,
        val revision: Long,
    ) {
        val hasWebsiteRules: Boolean = !blockedDomains.isEmpty
        val hasKeywordRules: Boolean = !blockedKeywords.isEmpty
    }

    private val EMPTY = Compiled(
        enabled = true,
        blockedDomains = DomainIndex.EMPTY,
        allowedDomains = DomainIndex.EMPTY,
        blockedApps = emptySet(),
        allowedApps = emptySet(),
        blockedKeywords = KeywordIndex.EMPTY,
        allowedKeywords = KeywordIndex.EMPTY,
        revision = 0L,
    )

    private val state = AtomicReference(EMPTY)
    private val initialized = AtomicBoolean(false)
    private val lastSource = AtomicReference<Snapshot?>(null)

    /** Monotonic counter consumers use to detect that rules actually changed. */
    @Volatile
    private var revisionCounter: Long = 0L

    val revision: Long get() = state.get().revision

    val isEnabled: Boolean get() = state.get().enabled

    /**
     * Recompiles and publishes [snapshot]. Identical snapshots are ignored so that repeated
     * `refreshEngine()` calls from the VPN, the guardian service and Room emissions do not
     * rebuild the automata or bump the revision.
     */
    fun update(snapshot: Snapshot) {
        if (lastSource.get() == snapshot && initialized.get()) return
        val compiled = Compiled(
            enabled = snapshot.enabled,
            blockedDomains = DomainIndex.build(snapshot.blockedWebsites),
            allowedDomains = DomainIndex.build(snapshot.allowedWebsites),
            blockedApps = snapshot.blockedApps.mapTo(HashSet(snapshot.blockedApps.size)) { it.trim() },
            allowedApps = snapshot.allowedApps.mapTo(HashSet(snapshot.allowedApps.size)) { it.trim() },
            blockedKeywords = KeywordIndex.build(snapshot.blockedKeywords),
            allowedKeywords = KeywordIndex.build(snapshot.allowedKeywords),
            revision = ++revisionCounter,
        )
        lastSource.set(snapshot)
        state.set(compiled)
        initialized.set(true)
    }

    fun isInitialized(): Boolean = initialized.get()

    /** True when there is at least one active user-managed or bundled rule. */
    fun hasActiveRules(): Boolean {
        val current = state.get()
        return current.enabled && (
            current.hasWebsiteRules || current.hasKeywordRules ||
                BuiltInAdultDomains.size > 0 || !BuiltInAdultKeywords.isEmpty
            )
    }

    /** True when user-managed or bundled keyword rules need text inspection. */
    fun shouldInspectText(): Boolean {
        val current = state.get()
        return current.enabled && (current.hasKeywordRules || !BuiltInAdultKeywords.isEmpty)
    }

    // ---------------------------------------------------------------- websites

    fun isWebsiteAllowed(domain: String): Boolean {
        val current = state.get()
        if (current.allowedDomains.isEmpty) return false
        return current.allowedDomains.matches(TextNormalizer.normalizeDomain(domain))
    }

    fun matchingWebsiteRule(domain: String): String? {
        val current = state.get()
        if (!current.enabled) return null
        val normalized = TextNormalizer.normalizeDomain(domain)
        if (normalized.isEmpty()) return null
        if (current.allowedDomains.matches(normalized)) return null
        current.blockedDomains.match(normalized)?.let { return it }
        return if (BuiltInAdultDomains.matches(normalized)) normalized else null
    }

    fun isWebsiteBlocked(domain: String): Boolean = matchingWebsiteRule(domain) != null

    /**
     * Reason a host should be blocked, or null. Checks the domain index first, then applies the
     * keyword index to the hostname itself so keyword rules also cover DNS lookups.
     */
    fun websiteBlockReason(domain: String): String? {
        val current = state.get()
        if (!current.enabled) return null
        val normalized = TextNormalizer.normalizeDomain(domain)
        if (normalized.isEmpty()) return null
        if (!current.allowedDomains.isEmpty && current.allowedDomains.matches(normalized)) return null
        current.blockedDomains.match(normalized)?.let { return "Blocked domain rule: $it" }
        if (BuiltInAdultDomains.matches(normalized)) return "Blocked bundled site: $normalized"
        val hostText = normalized.replace('.', ' ')
        if (current.hasKeywordRules) {
            if (current.allowedKeywords.isEmpty || !current.allowedKeywords.containsAny(hostText)) {
                current.blockedKeywords.firstMatch(hostText)?.let { return "Blocked keyword: $it" }
                BuiltInAdultKeywords.firstMatch(hostText)?.let { return "Blocked keyword: $it" }
            }
        } else {
            BuiltInAdultKeywords.firstMatch(hostText)?.let { return "Blocked keyword: $it" }
        }
        return null
    }

    // -------------------------------------------------------------------- apps

    fun isAppBlocked(packageName: String): Boolean {
        val current = state.get()
        if (!current.enabled) return false
        if (packageName in current.allowedApps) return false
        return packageName in current.blockedApps || BuiltInBlockedApps.isBlocked(packageName)
    }

    // ---------------------------------------------------------------- keywords

    /**
     * Finds a blocked keyword inside free text. The input is normalized (Unicode folded,
     * diacritics stripped, digits unified) before scanning, so Arabic, Kurdish and English all
     * match consistently regardless of how the text was typed.
     */
    fun matchingBlockedKeyword(text: String): String? {
        val current = state.get()
        if (!current.enabled || text.isEmpty()) return null
        val normalized = TextNormalizer.normalize(text)
        if (normalized.isEmpty()) return null
        if (!current.allowedKeywords.isEmpty && current.allowedKeywords.containsAny(normalized)) return null
        current.blockedKeywords.firstMatch(normalized)?.let { return it }
        return BuiltInAdultKeywords.firstMatch(normalized)
    }

    /** Pre-normalized variant used by hot paths that already normalized their input once. */
    fun matchingBlockedKeywordNormalized(normalized: String): String? {
        val current = state.get()
        if (!current.enabled || normalized.isEmpty()) return null
        if (!current.allowedKeywords.isEmpty && current.allowedKeywords.containsAny(normalized)) return null
        current.blockedKeywords.firstMatch(normalized)?.let { return it }
        return BuiltInAdultKeywords.firstMatch(normalized)
    }

    /**
     * Full URL inspection covering every surface required by the filter contract: the raw URL,
     * its percent-decoded form, the host, the path, each query parameter and the search terms
     * carried by common search engines.
     */
    fun matchingUrlKeyword(rawUrl: String): String? {
        val current = state.get()
        if (!current.enabled || rawUrl.isEmpty()) return null

        val decoded = TextNormalizer.decodeUrl(rawUrl)
        val normalizedUrl = TextNormalizer.normalize(decoded.replace('.', ' ').replace('/', ' '))
        if (normalizedUrl.isEmpty()) return null
        if (!current.allowedKeywords.isEmpty && current.allowedKeywords.containsAny(normalizedUrl)) return null
        current.blockedKeywords.firstMatch(normalizedUrl)?.let { return it }
        return BuiltInAdultKeywords.firstMatch(normalizedUrl)
    }

    /** Extracts the search terms carried by well known engines, empty when not a search URL. */
    fun extractSearchQuery(rawUrl: String): String {
        val queryStart = rawUrl.indexOf('?')
        if (queryStart < 0 || queryStart == rawUrl.length - 1) return ""
        val query = rawUrl.substring(queryStart + 1).substringBefore('#')
        if (query.isEmpty()) return ""
        val builder = StringBuilder(32)
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0 || separator == pair.length - 1) continue
            val key = pair.substring(0, separator).lowercase()
            if (key !in SEARCH_PARAMETERS) continue
            val value = TextNormalizer.decodeUrl(pair.substring(separator + 1))
            if (value.isEmpty()) continue
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(value)
        }
        return builder.toString()
    }

    /**
     * Real-time multi-surface scan used before a search result or link is allowed to open.
     * Checks domain/URL first (O(1)/O(host)), then keywords across title, URL and snippet in a
     * single normalized pass so the accessibility hot path stays allocation-light.
     *
     * @return Pair of blocked value to [RuleType], or null when the surface is clean.
     */
    fun evaluateNavigationSurface(
        url: String = "",
        title: String = "",
        snippet: String = "",
    ): Pair<String, RuleType>? {
        val current = state.get()
        if (!current.enabled) return null

        val host = TextNormalizer.normalizeDomain(url)
        if (host.isNotEmpty()) {
            if (!current.allowedDomains.isEmpty && current.allowedDomains.matches(host)) {
                // Explicit allow still wins for the host itself, but keyword rules on title/snippet
                // continue below so a permitted domain cannot carry blocked search result text.
            } else {
                current.blockedDomains.match(host)?.let { return it to RuleType.WEBSITE }
                if (BuiltInAdultDomains.matches(host)) return host to RuleType.WEBSITE
            }
        }

        if (!url.isEmpty()) {
            matchingUrlKeyword(url)?.let { return it to RuleType.KEYWORD }
            val searchTerms = extractSearchQuery(url)
            if (searchTerms.isNotEmpty()) {
                matchingBlockedKeyword(searchTerms)?.let { return it to RuleType.KEYWORD }
            }
        }

        if (!shouldInspectText()) return null

        // Title + snippet (+ host tokens) in one haystack keeps Aho-Corasick O(text).
        val combined = buildString(title.length + snippet.length + host.length + 8) {
            if (title.isNotEmpty()) append(title)
            if (snippet.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(snippet)
            }
            if (host.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append(host.replace('.', ' '))
            }
        }
        if (combined.isEmpty()) return null
        matchingBlockedKeyword(combined)?.let { return it to RuleType.KEYWORD }
        return null
    }

    /** Query parameter names used by the supported search engines and site search forms. */
    private val SEARCH_PARAMETERS = setOf(
        "q", "query", "search", "search_query", "searchquery", "p", "text", "wd", "kw",
        "keyword", "keywords", "term", "s", "k", "qs", "eq",
    )
}
