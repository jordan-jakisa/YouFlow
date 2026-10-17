package dev.jordanempire.youflow.ui

import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.shorts.ShortsPage
import dev.jordanempire.youflow.ui.shorts.appendNewShorts
import dev.jordanempire.youflow.ui.shorts.mergeChannelShorts
import dev.jordanempire.youflow.ui.shorts.shortsPages
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsPagesTest {
    private fun short(id: String, channel: String = "Blender Studio") = VideoItem(
        url = "https://www.youtube.com/shorts/$id",
        title = "Short $id",
        channel = channel,
        channelUrl = "https://www.youtube.com/channel/$channel",
        thumbnail = null,
        avatar = null,
        durationSeconds = 30,
        views = null,
        uploaded = null,
        isLive = false,
        isShort = true
    )

    @Test
    fun noSubscriptionShortsMeansNoPagesSoTheEmptyStateShows() = assertTrue(shortsPages(emptyList()).isEmpty())

    @Test
    fun aFewShortsAreFollowedBySubscribePage() {
        val shorts = listOf(short("a"), short("b"))
        assertEquals(listOf(ShortsPage.Short(shorts[0]), ShortsPage.Short(shorts[1]), ShortsPage.End), shortsPages(shorts))
    }

    @Test
    fun subscribePageIsOnlyAtTheEnd() {
        val pages = shortsPages((1..20).map { short("$it") })
        assertEquals(1, pages.count { it == ShortsPage.End })
        assertEquals(ShortsPage.End, pages.last())
    }

    @Test
    fun mergingAddsNothingBeyondTheChannelsShorts() {
        val perChannel = listOf(listOf(short("a", "One"), short("b", "One")), emptyList(), listOf(short("c", "Two")))
        val merged = mergeChannelShorts(perChannel, Random(1))
        assertEquals(setOf("a", "b", "c"), merged.map { it.url.substringAfterLast('/') }.toSet())
        assertEquals(3, merged.size)
    }

    @Test
    fun mergingDropsDuplicates() {
        val merged = mergeChannelShorts(listOf(listOf(short("a")), listOf(short("a"), short("b"))), Random(1))
        assertEquals(2, merged.size)
    }

    @Test
    fun noChannelsGiveNoShorts() {
        assertTrue(mergeChannelShorts(emptyList()).isEmpty())
        assertTrue(mergeChannelShorts(listOf(emptyList(), emptyList())).isEmpty())
    }

    @Test
    fun newSubscriptionShortsGoAfterTheOnesOnScreen() {
        val showing = listOf(short("a"), short("b"))
        val fresh = listOf(short("c", "New"), short("b"), short("a"))
        assertEquals(listOf("a", "b", "c"), appendNewShorts(showing, fresh).map { it.url.substringAfterLast('/') })
    }

    @Test
    fun firstSubscriptionFillsAnEmptyScreen() {
        val fresh = listOf(short("a"), short("b"))
        assertEquals(fresh, appendNewShorts(emptyList(), fresh))
    }
}
