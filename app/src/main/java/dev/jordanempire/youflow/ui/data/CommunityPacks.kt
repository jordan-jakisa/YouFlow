package dev.jordanempire.youflow.ui.data

/**
 * Home topics built from a community's channels, ranked the HackerTube way: a video counts when
 * several of these channels independently point to it (their uploads list it as related), not when
 * one channel posts a lot. Channel ids were checked against YouTube on 2026-11-01.
 */
data class CommunityPack(val id: String, val title: String, val channels: List<Pair<String, String>>) {
    val channelUrls get() = channels.map { it.second }.toSet()
}

private fun ch(name: String, id: String) = name to "https://www.youtube.com/channel/$id"

object CommunityPacks {
    const val HN_ID = "hn"

    val all = listOf(
        CommunityPack(
            "developers",
            "Developers",
            listOf(
                ch("The PrimeTime", "UCUyeluBRhGPCW4rPe_UvBZQ"),
                ch("The Pragmatic Engineer", "UCPbwhExawYrn9xxI21TFfyw"),
                ch("Fireship", "UCsBjURrPoezykLs9EqgamOA"),
                ch("Theo - t3.gg", "UCbRP3c757lWg9M-U7TyEkXA"),
                ch("Computerphile", "UC9-y-6csu5WGm29I7JiwpnA"),
                ch("CodeAesthetic", "UCaSCt8s_4nfkRglWCvNSDrg"),
                ch("Low Level", "UC6biysICWOJ-C3P4Tyeggzg"),
                ch("Dreams of Code", "UCWQaM7SpSECp9FELz-cHzuQ"),
                ch("Boomer Ranting in the Woods", "UCMkMs3Val6p1J6ZJe-TJ65Q")
            )
        ),
        CommunityPack(
            "ai",
            "AI builders",
            listOf(
                ch("Andrej Karpathy", "UCXUPKJO5MZQN11PqgIvyuvQ"),
                ch("Yannic Kilcher", "UCZHmQk67mSJgfCCTn7xBfew"),
                ch("Two Minute Papers", "UCbfYPyITQ-7l4upoX8nvctg"),
                ch("AI Explained", "UCNJ1Ymd5yFuUPtn21xtRbbw"),
                ch("Latent Space", "UCxBcwypKK-W3GHd_RZ9FZrQ"),
                ch("Dwarkesh Patel", "UCXl4i9dYBrFOabk0xGmbkRA"),
                ch("Anthropic", "UCrDwWp7EBBv4NwvScIpBDOA")
            )
        ),
        CommunityPack(
            "startups",
            "Startups",
            listOf(
                ch("Y Combinator", "UCcefcZRL2oaA_uBNeo5UOWg"),
                ch("Lenny's Podcast", "UC6t1O76G0jYXOAoYCm153dA"),
                ch("a16z", "UC9cn0TuPq4dnbTY-CBsm8XA"),
                ch("Garry Tan", "UCIBgYfDjtWlbJhg--Z4sOgQ")
            )
        ),
        CommunityPack(
            "indie",
            "Indie hackers",
            listOf(
                ch("Starter Story", "UChhw6DlKKTQ9mYSpTfXUYqA"),
                ch("Greg Isenberg", "UCPjNBjflYl0-HQtUvOx0Ibw"),
                ch("Adam Lyttle", "UCKJn8oR4qN_0lDw8dKZ6wlg")
            )
        ),
        CommunityPack(
            "hackers",
            "Hackers",
            listOf(
                ch("Ben Vallack", "UC4NNPgQ9sOkBjw6GlkgCylg"),
                ch("Jeff Geerling", "UCR-DXc1voovS8nhAvccRZhg"),
                ch("Ben Eater", "UCS0N5baNlQWJCUrhCEo8WlA"),
                ch("LiveOverflow", "UClcE-kVhqyiHCcjYwcpfj9w"),
                ch("Tsoding Daily", "UCrqM0Ym_NbK1fqeQG2VIohg"),
                ch("Andreas Kling", "UC3ts8coMP645hZw9JSD3pqQ"),
                ch("Wolfgang's Channel", "UCsnGwSIHyoYN0kiINAGUKxg")
            )
        )
    )

    /**
     * YouTube's related lists also carry whatever is popular (a talk show clip next to an AI
     * paper review), so a video from outside the pack must share words with what the pack's own
     * channels are posting about. [topic] is built from their recent titles.
     */
    fun onTopic(title: String, fromPackChannel: Boolean, topic: TasteProfile): Boolean = fromPackChannel || topic.overlap(title) > 0

    /** One upload of a pack channel and the related videos YouTube lists beside it. */
    class Pointer(val fromChannel: String, val relatedUrls: List<String>)

    /**
     * Consensus scores, best first. Each pack channel's uploads score by recency; a related video
     * scores by how many different pack channels point to it, and needs at least two unless it is
     * itself from a pack channel. One channel pointing ten times still counts once.
     */
    fun rank(
        uploads: Map<String, List<String>>,
        pointers: List<Pointer>,
        channelOf: (String) -> String?,
        packChannels: Set<String>
    ): List<Pair<String, Double>> {
        val scores = HashMap<String, Double>()
        uploads.values.forEach { urls -> urls.forEachIndexed { i, url -> scores.merge(url, Math.pow(0.9, i.toDouble())) { a, b -> a + b } } }
        val pointedBy = HashMap<String, MutableSet<String>>()
        pointers.forEach { p -> p.relatedUrls.forEach { pointedBy.getOrPut(it) { HashSet() } += p.fromChannel } }
        pointedBy.forEach { (url, from) ->
            val fromPack = channelOf(url) in packChannels
            if (from.size >= 2 || fromPack) scores.merge(url, Math.pow(from.size.toDouble(), 1.5) * 0.8) { a, b -> a + b }
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}
