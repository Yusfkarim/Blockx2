package com.agon.app.blocklist.domain

/**
 * Reverse-label domain index giving O(labels) lookup instead of the previous O(rules) scan
 * over every rule for every DNS packet.
 *
 * Rules are stored from the TLD inwards, so `sub.example.com` walks `com -> example -> sub`
 * and stops as soon as a terminal rule is reached. Supported forms:
 *  - `example.com`      matches `example.com` and every subdomain
 *  - `www.example.com`  matches that host and its subdomains
 *  - `*.example.com`    matches subdomains only, not the apex
 *  - `sub.example.com`  exact host plus deeper subdomains
 */
class DomainIndex private constructor(private val root: Node?, val size: Int) {

    private class Node {
        var children: HashMap<String, Node>? = null

        /** Rule that matches this host and anything below it. */
        var terminalRule: String? = null

        /** Rule that matches strictly below this host (wildcard form). */
        var wildcardRule: String? = null

        fun child(label: String): Node {
            val map = children ?: HashMap<String, Node>(4).also { children = it }
            return map.getOrPut(label) { Node() }
        }
    }

    val isEmpty: Boolean get() = root == null

    /**
     * Returns the rule that blocks [domain], or null. [domain] must already be normalized
     * through [TextNormalizer.normalizeDomain].
     */
    fun match(domain: String): String? {
        val node = root ?: return null
        if (domain.isEmpty()) return null

        var current = node
        var end = domain.length
        var depth = 0

        while (end > 0) {
            val start = domain.lastIndexOf('.', end - 1) + 1
            val label = domain.substring(start, end)
            if (label.isEmpty()) return null

            val next = current.children?.get(label) ?: return null

            // A wildcard rule fires only when at least one further label exists below it.
            next.terminalRule?.let { return it }
            if (start > 0) next.wildcardRule?.let { return it }

            current = next
            depth++
            if (depth > MAX_LABELS) return null
            end = if (start == 0) 0 else start - 1
        }
        return null
    }

    fun matches(domain: String): Boolean = match(domain) != null

    companion object {
        private const val MAX_LABELS = 24

        val EMPTY = DomainIndex(null, 0)

        /** Compiles raw rule strings into an immutable index. */
        fun build(rules: Collection<String>): DomainIndex {
            if (rules.isEmpty()) return EMPTY
            val root = Node()
            var count = 0
            for (raw in rules) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) continue
                val wildcard = trimmed.startsWith("*.")
                var host = TextNormalizer.normalizeDomain(if (wildcard) trimmed.substring(2) else trimmed)
                if (host.isEmpty() || host == ".") continue

                // `www.` is a cosmetic prefix: a rule for www.example.com must also cover the
                // apex, otherwise dropping four characters from the address bar bypasses it.
                if (!wildcard && host.startsWith("www.") && host.length > 4) {
                    val apex = host.substring(4)
                    if (apex.contains('.')) host = apex
                }

                var current = root
                var end = host.length
                var depth = 0
                var valid = true
                while (end > 0) {
                    val start = host.lastIndexOf('.', end - 1) + 1
                    val label = host.substring(start, end)
                    if (label.isEmpty()) {
                        valid = false
                        break
                    }
                    current = current.child(label)
                    depth++
                    if (depth > MAX_LABELS) {
                        valid = false
                        break
                    }
                    end = if (start == 0) 0 else start - 1
                }
                if (!valid) continue

                if (wildcard) {
                    if (current.wildcardRule == null) current.wildcardRule = trimmed
                } else {
                    // A terminal rule supersedes a wildcard on the same host.
                    if (current.terminalRule == null) current.terminalRule = trimmed
                }
                count++
            }
            return if (count == 0) EMPTY else DomainIndex(root, count)
        }
    }
}
