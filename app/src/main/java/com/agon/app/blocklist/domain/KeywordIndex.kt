package com.agon.app.blocklist.domain

/**
 * Aho-Corasick automaton for keyword matching.
 *
 * The previous implementation ran `text.contains(keyword)` once per keyword, i.e. O(rules x text)
 * on every accessibility event and every DNS packet, which is what drove the CPU cost. This scans
 * the haystack exactly once in O(text) regardless of how many keywords are configured.
 *
 * Two match modes are supported per requirement:
 *  - partial: the keyword appears anywhere in the text
 *  - exact:   the keyword occupies a whole token (word boundary on both sides)
 */
class KeywordIndex private constructor(
    private val goto: Array<HashMap<Char, Int>>,
    private val fail: IntArray,
    private val output: Array<List<String>?>,
    val size: Int,
) {

    val isEmpty: Boolean get() = size == 0

    /**
     * Returns the first keyword found in [text], which must already be normalized through
     * [TextNormalizer.normalize]. Word-boundary handling makes single-word rules behave as
     * whole-word matches while multi-word rules still match as substrings.
     */
    fun firstMatch(text: String): String? {
        if (size == 0 || text.isEmpty()) return null
        var state = 0
        for (index in text.indices) {
            val ch = text[index]
            state = step(state, ch)
            val hits = output[state] ?: continue
            for (keyword in hits) {
                val start = index - keyword.length + 1
                if (start < 0) continue
                if (isAcceptable(text, start, index)) return keyword
            }
        }
        return null
    }

    fun containsAny(text: String): Boolean = firstMatch(text) != null

    private fun step(from: Int, ch: Char): Int {
        var state = from
        while (true) {
            goto[state][ch]?.let { return it }
            if (state == 0) return 0
            state = fail[state]
        }
    }

    /**
     * Single-token keywords must align with word boundaries so that a rule like "sex" does not
     * fire inside "essex", while phrases containing separators keep substring semantics.
     */
    private fun isAcceptable(text: String, start: Int, endInclusive: Int): Boolean {
        val before = if (start == 0) null else text[start - 1]
        val after = if (endInclusive + 1 >= text.length) null else text[endInclusive + 1]
        val boundedStart = before == null || !before.isLetterOrDigit()
        val boundedEnd = after == null || !after.isLetterOrDigit()
        return boundedStart && boundedEnd
    }

    companion object {
        val EMPTY = KeywordIndex(arrayOf(HashMap()), IntArray(1), arrayOfNulls(1), 0)

        private const val MAX_KEYWORD_LENGTH = 96

        /** Compiles normalized keywords into an immutable automaton. */
        fun build(keywords: Collection<String>): KeywordIndex {
            val cleaned = LinkedHashSet<String>()
            for (raw in keywords) {
                val normalized = TextNormalizer.normalize(raw)
                if (normalized.isEmpty() || normalized.length > MAX_KEYWORD_LENGTH) continue
                // Punctuation/symbol-only entries (".", "?", "-", "()" …) are never words:
                // they would otherwise match everywhere, since separators form the word
                // boundaries. A rule must contain at least one letter or digit.
                if (normalized.none { it.isLetterOrDigit() }) continue
                cleaned.add(normalized)
            }
            if (cleaned.isEmpty()) return EMPTY

            val gotoList = ArrayList<HashMap<Char, Int>>(cleaned.sumOf { it.length } + 1)
            gotoList.add(HashMap())
            val outputList = ArrayList<MutableList<String>?>()
            outputList.add(null)

            for (keyword in cleaned) {
                var state = 0
                for (ch in keyword) {
                    val next = gotoList[state][ch]
                    state = if (next != null) {
                        next
                    } else {
                        gotoList.add(HashMap())
                        outputList.add(null)
                        val created = gotoList.size - 1
                        gotoList[state][ch] = created
                        created
                    }
                }
                val bucket = outputList[state] ?: ArrayList<String>(1).also { outputList[state] = it }
                bucket.add(keyword)
            }

            val stateCount = gotoList.size
            val fail = IntArray(stateCount)
            val queue = ArrayDeque<Int>(stateCount)

            for (entry in gotoList[0].entries) {
                fail[entry.value] = 0
                queue.addLast(entry.value)
            }

            while (queue.isNotEmpty()) {
                val state = queue.removeFirst()
                for ((ch, next) in gotoList[state]) {
                    var candidate = fail[state]
                    while (candidate != 0 && gotoList[candidate][ch] == null) candidate = fail[candidate]
                    val target = gotoList[candidate][ch]
                    fail[next] = if (target != null && target != next) target else 0
                    outputList[fail[next]]?.let { inherited ->
                        val bucket = outputList[next] ?: ArrayList<String>(inherited.size).also { outputList[next] = it }
                        bucket.addAll(inherited)
                    }
                    queue.addLast(next)
                }
            }

            @Suppress("UNCHECKED_CAST")
            val output = Array<List<String>?>(stateCount) { index -> outputList[index]?.toList() }
            return KeywordIndex(gotoList.toTypedArray(), fail, output, cleaned.size)
        }
    }
}
