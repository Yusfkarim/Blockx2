package com.agon.app.blocklist.domain

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device content classifier.
 *
 * A fully offline scorer that inspects the signals extracted from a page (domain, URL, title,
 * visible text and search query) and decides whether they describe content in one of the
 * [ContentCategory] classes. It never sends anything to a network; the optional cloud moderation
 * layer is a separate, additional stage.
 *
 * How the score is produced:
 *  - Each category owns a set of multilingual marker phrases. The large, hot ADULT vocabulary is
 *    compiled into an Aho-Corasick [KeywordIndex] so a page is scanned in a single O(text) pass no
 *    matter how many terms exist; the smaller categories use a direct phrase check. Only explicit,
 *    unambiguous terms are included so ordinary everyday words never trigger a false block.
 *  - A marker found in the domain / search query / title (the "strong" surface) weighs more than
 *    one buried in body text, because the former almost always describes the page itself.
 *  - The weighted evidence maps to a 0–100 confidence. Blocking happens only when the category is
 *    enabled AND the confidence reaches that category's configured threshold, so the per-category
 *    settings screen (enable + threshold slider) drives the behaviour exactly as its UI promises.
 *
 * The public contract ([Signals], [Result], [CategoryConfig], [classify], [updateConfiguration],
 * [invalidate]) is unchanged so every existing caller keeps compiling and behaving.
 */
@Singleton
class AiContentClassifier @Inject constructor() {
    data class Signals(
        val domain: String = "",
        val url: String = "",
        val title: String = "",
        val description: String = "",
        val visibleText: String = "",
        val searchQuery: String = "",
    )

    data class CategoryConfig(
        val category: ContentCategory,
        val enabled: Boolean,
        val threshold: Int,
    )

    data class Result(
        val category: ContentCategory,
        val confidence: Int,
        val reason: String,
    )

    /** Per-category enable + threshold, published atomically from the repository. */
    private val configs = AtomicReference(
        // Only Adult content is ON by default; every other category is opt-in from the
        // per-category settings screen, so a clean drug/gambling/news article is never
        // blocked before the user explicitly asks for that protection level.
        ContentCategory.entries.associateWith {
            CategoryConfig(it, enabled = it == ContentCategory.ADULT, threshold = DEFAULT_THRESHOLD)
        },
    )

    fun updateConfiguration(configList: List<CategoryConfig>) {
        if (configList.isEmpty()) return
        configs.set(configList.associateBy { it.category })
    }

    /** No local vocabulary is user-mutable, so cache invalidation is a no-op by design. */
    fun invalidate(affectedTerms: Collection<String>) = Unit

    /**
     * Classifies [signals]. Returns the highest-confidence category that is enabled and reaches
     * its threshold, or null when nothing crosses the bar (fail-open: the caller then falls back
     * to the other engines / cloud layer).
     */
    fun classify(signals: Signals): Result? {
        val domain = TextNormalizer.normalizeDomain(signals.domain)
        val domainWords = TextNormalizer.normalize(domain.replace('.', ' '))
        val strong = TextNormalizer.normalize(
            buildString {
                append(domainWords)
                if (signals.searchQuery.isNotBlank()) { append(' '); append(signals.searchQuery) }
                if (signals.title.isNotBlank()) { append(' '); append(signals.title) }
            },
        )
        val body = TextNormalizer.normalize(
            buildString {
                if (signals.visibleText.isNotBlank()) { append(signals.visibleText) }
                if (signals.description.isNotBlank()) { append(' '); append(signals.description) }
                if (signals.url.isNotBlank()) { append(' '); append(signals.url) }
            },
        )
        if (strong.isBlank() && body.isBlank()) return null

        val activeConfigs = configs.get()
        var best: Result? = null

        // ADULT is evaluated first with the Aho-Corasick index (fast, large multilingual set).
        activeConfigs[ContentCategory.ADULT]?.let { config ->
            if (config.enabled) {
                val strongHit = strong.isNotEmpty() && ADULT_INDEX.firstMatch(strong) != null
                val bodyHit = !strongHit && body.isNotEmpty() && ADULT_INDEX.firstMatch(body) != null
                if (strongHit || bodyHit) {
                    val confidence = confidenceOf(if (strongHit) STRONG_WEIGHT else BODY_WEIGHT)
                    if (confidence >= config.threshold) {
                        best = Result(ContentCategory.ADULT, confidence, "${ContentCategory.ADULT.displayName} (${confidence}%)")
                    }
                }
            }
        }

        for ((category, markers) in CATEGORY_MARKERS) {
            val config = activeConfigs[category] ?: continue
            if (!config.enabled) continue

            var score = 0
            var hits = 0
            for (marker in markers) {
                val inStrong = strong.isNotEmpty() && containsPhrase(strong, marker)
                val inBody = !inStrong && body.isNotEmpty() && containsPhrase(body, marker)
                if (inStrong || inBody) {
                    hits++
                    score += if (inStrong) STRONG_WEIGHT else BODY_WEIGHT
                }
            }
            if (hits == 0) continue

            val confidence = confidenceOf(score)
            val currentBest = best
            if (confidence >= config.threshold && (currentBest == null || confidence > currentBest.confidence)) {
                best = Result(category, confidence, "${category.displayName} (${confidence}%)")
            }
        }
        return best
    }

    /** Whole-token / phrase containment so "sex" never matches inside "essex" or "sussex". */
    private fun containsPhrase(haystack: String, needle: String): Boolean {
        if (needle.isEmpty()) return false
        var from = 0
        while (true) {
            val idx = haystack.indexOf(needle, from)
            if (idx < 0) return false
            val before = idx - 1
            val after = idx + needle.length
            val boundaryBefore = before < 0 || haystack[before] == ' '
            val boundaryAfter = after >= haystack.length || haystack[after] == ' '
            if (boundaryBefore && boundaryAfter) return true
            from = idx + needle.length
        }
    }

    /** Maps accumulated marker weight to a bounded 0–100 confidence. */
    private fun confidenceOf(score: Int): Int {
        val base = 44 + score
        return base.coerceIn(0, 99)
    }

    private companion object {
        const val DEFAULT_THRESHOLD = 72
        const val STRONG_WEIGHT = 38
        const val BODY_WEIGHT = 20

        /**
         * Explicit, unambiguous adult terms across English, Arabic, Kurdish, Persian, Turkish,
         * Urdu and Hindi. Everyday words are deliberately excluded so normal browsing is never
         * blocked. Written in the canonical folded form produced by [TextNormalizer.normalize].
         */
        private val ADULT_MARKERS: List<String> = listOf(
        "porn", "porno", "pornhub", "pornographic",
        "xxx", "xnxx", "xvideos", "xhamster",
        "redtube", "youporn", "brazzers", "onlyfans",
        "hentai", "camgirl", "camsex", "sexcam",
        "sextape", "nudes", "nudity", "blowjob",
        "handjob", "deepthroat", "creampie", "cumshot",
        "gangbang", "bukkake", "masturbation", "masturbate",
        "dildo", "fleshlight", "bdsm", "fetish porn",
        "milf porn", "teen porn", "anal sex", "oral sex",
        "hardcore porn", "softcore porn", "erotic video", "erotica",
        "nsfw", "adult movies", "adult videos", "sex video",
        "sex videos", "sex movie", "fuck video", "fucking video",
        "naked girls", "naked women", "nude girls", "nude photos",
        "اباحی", "اباحیه", "افلام اباحیه", "افلام سکس",
        "افلام جنس", "سکس", "سیکس", "نیک",
        "نیاکه", "شرموطه", "شرامیط", "قحبه",
        "قحاب", "طیز", "مص زب", "لحس کس",
        "خلاعه", "خلاعیه", "دعاره", "عاهره",
        "بورن", "بورنو", "جنس فموی", "جماع",
        "سێکس", "ییباحی", "پۆرن", "پۆرنۆ",
        "فیلمی سێکس", "ڤیدیۆی سێکس", "کیر", "کۆس",
        "قووز", "گان", "گاییدن", "قهحپه",
        "قهحبه", "سکسی", "فیلم سکسی", "پورن",
        "پورنو", "جنده", "کون", "لخت",
        "برهنه", "سوپر سکسی", "sikis", "sikiş",
        "am resimleri", "pornosu", "seks video", "çıplak kadın",
        "porno izle", "فحش", "فحش فلمیں", "عریاں",
        "ننگی", "nangi", "chudai", "blue film",
        "sexy video", "gandi film",
        )

        /** Compiled once; scans any page in O(text) regardless of vocabulary size. */
        val ADULT_INDEX: KeywordIndex = KeywordIndex.build(ADULT_MARKERS)

        /**
         * Smaller supplementary categories. Written in canonical folded form. Only explicit terms
         * are listed so ordinary pages are never misclassified.
         */
        val CATEGORY_MARKERS: Map<ContentCategory, List<String>> = mapOf(
            ContentCategory.GAMBLING to listOf(
                "casino", "betting", "gambling", "poker", "roulette", "slots", "jackpot",
                "bet365", "sportsbook", "blackjack", "baccarat",
                "قمار", "رهان", "مراهنات", "کازینو", "رولیت", "بوکر",
                "قومار", "کازینۆ",
                "شرط بندی", "قمارخانه",
            ),
            ContentCategory.DRUGS to listOf(
                "cocaine", "heroin", "cannabis", "marijuana", "meth", "narcotics",
                "buy drugs", "lsd", "ecstasy", "mdma",
                "مخدرات", "کوکایین", "هیروین", "حشیش", "ماریجوانا",
                "مادده هۆشبهر", "حهشیش", "کۆکایین",
                "مواد مخدر", "هرویین", "کوکایین",
            ),
            ContentCategory.VIOLENCE to listOf(
                "gore", "beheading", "massacre", "graphic violence", "torture video",
                "قطع راس", "مذبحه", "تعذیب",
                "توندوتیژی", "سهربڕین", "کوشتار", "یهشکهنجه",
                "سر بریدن", "شکنجه",
            ),
            ContentCategory.HATE to listOf(
                "hate speech", "racial slur", "nazi", "white supremacy",
                "کراهیه", "عنصریه", "معاداه",
                "ناحهزی", "ڕهگهزپهرستی",
                "نفرت پراکنی", "نژادپرستی",
            ),
            ContentCategory.SCAM to listOf(
                "free money", "get rich quick", "double your money", "lottery winner",
                "prize claim", "investment scheme", "guaranteed profit",
                "احتیال", "ربح سریع", "جایزه وهمیه", "مال مجانی",
                "قازانجی خێرا", "پارهی بهخۆڕایی",
                "کلاهبرداری", "پول رایگان",
            ),
            ContentCategory.PHISHING to listOf(
                "verify your account", "confirm your password", "account suspended",
                "update payment", "login to claim", "reset your bank",
                "تحقق من حسابک", "تاکید کلمه المرور", "حسابک موقوف",
                "تایید رمز عبور",
            ),
            ContentCategory.MALWARE to listOf(
                "download crack", "keygen", "free hack", "torrent crack",
                "cracked apk", "mod hack download",
                "تحمیل کراک", "هکر مجانی",
                "دانلود کرک",
            ),
            ContentCategory.EXTREMISM to listOf(
                "join jihad", "terror attack guide", "bomb making", "extremist recruitment",
                "صنع قنبله",
                "دروستکردنی بۆمب",
                "ساخت بمب",
            ),
        )
    }
}

enum class ContentCategory(val displayName: String) {
    ADULT("Adult content"),
    GAMBLING("Gambling"),
    DRUGS("Drugs"),
    VIOLENCE("Violence"),
    HATE("Hate"),
    SCAM("Scam"),
    PHISHING("Phishing"),
    MALWARE("Malware"),
    EXTREMISM("Extremism"),
}
