package com.agon.app.blocklist.domain

import java.net.URLDecoder
import java.text.Normalizer

/**
 * Unicode aware normalizer shared by the website and keyword matchers.
 *
 * Matching used to be a plain `contains(ignoreCase = true)` which made keyword filtering
 * inconsistent: the same word written with Arabic presentation forms, tatweel, diacritics,
 * Eastern Arabic digits or the Kurdish/Persian variants of Kaf and Yeh produced a different
 * string and slipped through. Everything is folded to one canonical form before comparison.
 */
object TextNormalizer {

    private const val TATWEEL = '\u0640'
    private const val ZWNJ = '\u200C'
    private const val ZWJ = '\u200D'
    private const val RLM = '\u200F'
    private const val LRM = '\u200E'

    /** Arabic/Kurdish letter variants folded onto a single representative code point. */
    private val letterFolding: Map<Char, Char> = buildMap {
        // Alef family
        put('\u0622', '\u0627'); put('\u0623', '\u0627'); put('\u0625', '\u0627'); put('\u0671', '\u0627')
        // Yeh family (Arabic Yeh, Farsi Yeh, Alef Maksura, Kurdish Yeh)
        put('\u064A', '\u06CC'); put('\u0649', '\u06CC'); put('\u06D2', '\u06CC'); put('\u06CD', '\u06CC')
        // Kaf family (Arabic Kaf vs Keheh)
        put('\u0643', '\u06A9')
        // Heh family
        put('\u0629', '\u0647'); put('\u06C0', '\u0647'); put('\u06D5', '\u0647')
        // Waw family
        put('\u0624', '\u0648')
        // Hamza carriers
        put('\u0626', '\u06CC')
    }

    /** Diacritics and invisible marks that must never influence a match. */
    private fun isIgnorable(ch: Char): Boolean = when {
        ch == TATWEEL || ch == ZWNJ || ch == ZWJ || ch == RLM || ch == LRM -> true
        ch in '\u064B'..'\u065F' -> true // Arabic harakat
        ch == '\u0670' -> true // superscript alef
        ch in '\u06D6'..'\u06ED' -> true // Quranic annotation marks
        ch == '\uFEFF' -> true // BOM
        else -> false
    }

    /**
     * Canonical form used for every comparison: NFKC folded, lower-cased, diacritic free,
     * with Eastern Arabic digits mapped to ASCII and whitespace collapsed.
     */
    fun normalize(input: String): String {
        if (input.isEmpty()) return ""
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFKC)
        val builder = StringBuilder(decomposed.length)
        var lastWasSpace = false
        for (raw in decomposed) {
            if (isIgnorable(raw)) continue
            var ch = raw.lowercaseChar()
            ch = letterFolding[ch] ?: ch
            ch = foldDigit(ch)
            if (ch.isWhitespace()) {
                if (!lastWasSpace && builder.isNotEmpty()) {
                    builder.append(' ')
                    lastWasSpace = true
                }
                continue
            }
            lastWasSpace = false
            builder.append(ch)
        }
        while (builder.isNotEmpty() && builder.last() == ' ') builder.setLength(builder.length - 1)
        return builder.toString()
    }

    /** Maps Arabic-Indic and Extended Arabic-Indic digits onto ASCII digits. */
    private fun foldDigit(ch: Char): Char = when (ch) {
        in '\u0660'..'\u0669' -> ('0' + (ch - '\u0660'))
        in '\u06F0'..'\u06F9' -> ('0' + (ch - '\u06F0'))
        else -> ch
    }

    /**
     * Percent-decodes a URL, tolerating malformed input and repeated encoding layers so that
     * `%D9%BE` style escaping cannot be used to smuggle a blocked keyword past the filter.
     */
    fun decodeUrl(value: String): String {
        if (value.isEmpty()) return ""
        var current = value
        repeat(2) {
            if (!current.contains('%') && !current.contains('+')) return current
            val decoded = runCatching { URLDecoder.decode(current, "UTF-8") }.getOrNull() ?: return current
            if (decoded == current) return current
            current = decoded
        }
        return current
    }

    /**
     * Splits normalized text into comparable tokens. Separators cover URL syntax so that
     * `example.com/adult-content?q=term` yields the individual words.
     */
    fun tokenize(normalized: String): List<String> {
        if (normalized.isEmpty()) return emptyList()
        val tokens = ArrayList<String>(8)
        val builder = StringBuilder(16)
        for (ch in normalized) {
            if (ch.isLetterOrDigit()) {
                builder.append(ch)
            } else if (builder.isNotEmpty()) {
                tokens.add(builder.toString())
                builder.setLength(0)
            }
        }
        if (builder.isNotEmpty()) tokens.add(builder.toString())
        return tokens
    }

    /** Normalizes a hostname: strips scheme, credentials, port, path and the trailing dot. */
    fun normalizeDomain(value: String): String {
        if (value.isEmpty()) return ""
        var host = value.trim()
        val schemeIndex = host.indexOf("://")
        if (schemeIndex >= 0) host = host.substring(schemeIndex + 3)
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        val at = host.lastIndexOf('@')
        if (at >= 0) host = host.substring(at + 1)
        if (host.startsWith("[")) {
            val close = host.indexOf(']')
            if (close > 0) return host.substring(1, close).lowercase()
        }
        host = host.substringBefore(':').trim().trimEnd('.')
        return normalize(host)
    }
}
