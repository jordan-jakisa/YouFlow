package dev.jordanempire.youflow.ui.data

import android.content.Context
import dev.jordanempire.youflow.ui.model.VideoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The YouTube videos Hacker News keeps recommending, from HackerTube's data (hackertube.org,
 * ODC-By 1.0, built from the open-index/hacker-news archive). Shipped in assets/hn_picks.json:
 * news-only, music, comedy and never-recommended videos are left out, and so are duplicate uploads.
 */
@Serializable
class HnPicks(val videos: List<Pick>, val related: Map<String, List<String>> = emptyMap()) {

    @Serializable
    class Pick(
        @SerialName("id") val id: String,
        @SerialName("t") val title: String,
        @SerialName("c") val channel: String,
        /** HackerTube's score: distinct months recommended, strong recommendations count extra. */
        @SerialName("s") val score: Double,
        /** Distinct months with at least one recommendation. */
        @SerialName("m") val months: Int,
        /** Topic words: primary topic, tags and category names. */
        @SerialName("k") val topics: String
    ) {
        val url get() = "https://www.youtube.com/watch?v=$id"
    }

    private val byId by lazy { videos.associateBy { it.id } }

    /**
     * A score-weighted sample, so the classics come up most but every refresh differs, raised for
     * picks that match [taste] and for picks HN people recommend alongside what you watched. [skip]
     * holds video ids already watched or dismissed.
     */
    fun pick(
        taste: TasteProfile,
        watchedIds: Set<String>,
        skip: Set<String>,
        count: Int,
        random: () -> Double = Math::random
    ): List<Pair<Pick, String>> {
        val alsoRecommended = HashMap<String, String>() // id -> title of the watched video it goes with
        watchedIds.forEach { seen -> related[seen]?.forEach { alsoRecommended.putIfAbsent(it, byId[seen]?.title.orEmpty()) } }
        return videos.asSequence()
            .filter { it.id !in skip && it.id !in watchedIds }
            .map { pick ->
                val match = if (taste.isEmpty) 0.0 else taste.overlap("${pick.title} ${pick.topics}")
                val weight = pick.score * (1 + 3 * match) * (if (pick.id in alsoRecommended) 3.0 else 1.0)
                pick to Math.pow(random(), 1.0 / weight.coerceAtLeast(0.01))
            }
            .sortedByDescending { it.second }
            .take(count)
            .map { (pick, _) -> pick to reason(pick, alsoRecommended[pick.id]) }
            .toList()
    }

    companion object {
        @Volatile
        private var instance: HnPicks? = null
        private val json = Json { ignoreUnknownKeys = true }

        fun get(context: Context): HnPicks = instance ?: synchronized(this) {
            instance ?: runCatching {
                context.assets.open("hn_picks.json").bufferedReader().use { json.decodeFromString(serializer(), it.readText()) }
            }.getOrDefault(HnPicks(emptyList())).also { instance = it }
        }

        fun parse(text: String): HnPicks = json.decodeFromString(serializer(), text)

        fun reason(pick: Pick, alongside: String?): String = when {
            !alongside.isNullOrBlank() -> "HN people who recommend \"${alongside.take(40)}\" also recommend this"
            pick.months > 1 -> "Recommended on Hacker News in ${pick.months} different months"
            else -> "Recommended on Hacker News"
        }

        private val idPattern = Regex("[?&]v=([A-Za-z0-9_-]{11})")

        /** The 11-character id in a YouTube watch link, or null. */
        fun videoId(url: String): String? = idPattern.find(url)?.groupValues?.get(1)
    }
}

/** Without YouTube's metadata in the data set, duration and views stay unknown until the video opens. */
fun HnPicks.Pick.toVideo(reason: String) = VideoItem(
    url = url,
    title = title,
    channel = channel,
    channelUrl = null,
    thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
    avatar = null,
    durationSeconds = 0,
    views = null,
    uploaded = null,
    isLive = false,
    isShort = false,
    reason = reason
)
