package dev.jordanempire.youflow.ui.shorts

import dev.jordanempire.youflow.ui.model.ChannelItem
import dev.jordanempire.youflow.ui.model.VideoItem
import kotlin.random.Random

/** One page of the Shorts pager: a short, or the closing "subscribe for more" page. */
sealed interface ShortsPage {
    data class Short(val video: VideoItem) : ShortsPage

    data object End : ShortsPage
}

/**
 * The pages for [shorts]: every short, then one closing page that asks for more subscriptions.
 * No shorts means no pages; the screen then shows its empty state instead of a pager.
 */
fun shortsPages(shorts: List<VideoItem>): List<ShortsPage> = if (shorts.isEmpty()) emptyList() else shorts.map { ShortsPage.Short(it) } + ShortsPage.End

/** Shorts from each subscribed channel, mixed and without duplicates. Adds nothing of its own. */
fun mergeChannelShorts(perChannel: List<List<VideoItem>>, random: Random = Random.Default): List<VideoItem> = perChannel.flatten().distinctBy { it.url }.shuffled(random)

/** [showing] stays in place; shorts from [fresh] that are not on screen yet are added after it. */
fun appendNewShorts(showing: List<VideoItem>, fresh: List<VideoItem>): List<VideoItem> {
    val known = showing.mapTo(HashSet()) { it.url }
    return showing + fresh.filter { it.url !in known }
}

/**
 * Only the shorts whose channel is in [subscribed]. Matched by channel URL, or by name for saved
 * shorts that have no channel URL.
 */
fun keepSubscribed(shorts: List<VideoItem>, subscribed: List<ChannelItem>): List<VideoItem> {
    val urls = subscribed.mapTo(HashSet()) { it.url }
    val names = subscribed.mapTo(HashSet()) { it.name }
    return shorts.filter { short -> short.channelUrl?.let { it in urls } ?: (short.channel in names) }
}
