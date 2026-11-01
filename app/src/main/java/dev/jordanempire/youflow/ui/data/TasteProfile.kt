package dev.jordanempire.youflow.ui.data

import kotlin.math.min

/**
 * What you tend to watch, as weighted words from the titles of liked and watched videos. It is
 * used to find similar videos with a search and to score candidates by how much they overlap.
 */
class TasteProfile(private val weights: Map<String, Double>) {

    /** The strongest terms, best first. */
    fun topTerms(count: Int): List<String> = weights.entries.sortedByDescending { it.value }.take(count).map { it.key }

    /** 0..1: how much of the profile this title covers. */
    fun overlap(title: String): Double {
        if (weights.isEmpty()) return 0.0
        val max = weights.values.max()
        val hit = tokens(title).distinct().sumOf { weights[it] ?: 0.0 }
        return min(1.0, hit / (max * 3))
    }

    val isEmpty get() = weights.isEmpty()

    companion object {
        private val stopWords = setOf(
            "the", "and", "for", "with", "from", "this", "that", "you", "your", "are", "was", "how", "what", "why",
            "who", "not", "but", "all", "can", "will", "official", "video", "full", "new", "best", "part", "episode",
            "live", "ft", "feat", "vs", "its", "just", "into", "out", "our", "has", "have", "more", "most", "now"
        )

        fun tokens(title: String): List<String> = Regex("[\\p{L}\\p{N}]{3,}").findAll(title.lowercase()).map { it.value }
            .filter { it !in stopWords && !it.all(Char::isDigit) }.toList()

        /** [titles] paired with how strongly each one should count. */
        fun from(titles: List<Pair<String, Double>>): TasteProfile {
            val weights = HashMap<String, Double>()
            titles.forEach { (title, weight) ->
                tokens(title).distinct().forEach { weights.merge(it, weight) { a, b -> a + b } }
            }
            // A term seen once is noise, keep only what shows up repeatedly or in liked videos.
            return TasteProfile(weights.filterValues { it >= 1.5 })
        }

        /**
         * Lasting interest: [titles] with a weight and the day they were watched. A term counts once
         * per day, at that day's strongest weight, so ten videos binged in one evening weigh less
         * than the same topic coming back over many days (HackerTube ranks by distinct months).
         */
        fun fromDays(titles: List<Triple<String, Double, Long>>): TasteProfile {
            val perDay = HashMap<Pair<String, Long>, Double>()
            titles.forEach { (title, weight, day) ->
                tokens(title).distinct().forEach { perDay.merge(it to day, weight) { a, b -> maxOf(a, b) } }
            }
            val weights = HashMap<String, Double>()
            perDay.forEach { (key, weight) -> weights.merge(key.first, weight) { a, b -> a + b } }
            return TasteProfile(weights.filterValues { it >= 1.5 })
        }

        /** Milliseconds since the epoch to a day number, for [fromDays]. */
        fun day(epochMillis: Long): Long = epochMillis / 86_400_000L
    }
}
