package dev.jordanempire.youflow.ui

import dev.jordanempire.youflow.ui.data.CommunityPacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityPacksTest {
    private val channelOf = mapOf("talk" to "UCother", "own" to "UCpackB", "loner" to "UCother")

    private fun rank(pointers: List<CommunityPacks.Pointer>) = CommunityPacks.rank(
        uploads = mapOf("A" to listOf("a1", "a2"), "B" to listOf("b1")),
        pointers = pointers,
        channelOf = { channelOf[it] },
        packChannels = setOf("UCpackA", "UCpackB")
    ).map { it.first }

    @Test
    fun aVideoTwoChannelsPointToIsKept() {
        val ranked = rank(listOf(CommunityPacks.Pointer("A", listOf("talk")), CommunityPacks.Pointer("B", listOf("talk"))))
        assertTrue("talk" in ranked)
    }

    @Test
    fun oneChannelPointingManyTimesIsNotEnough() {
        val ranked = rank(List(5) { CommunityPacks.Pointer("A", listOf("loner")) })
        assertTrue("loner" !in ranked)
    }

    @Test
    fun aPackChannelsVideoNeedsOnlyOnePointer() = assertTrue("own" in rank(listOf(CommunityPacks.Pointer("A", listOf("own")))))

    @Test
    fun consensusOutranksASingleNewUpload() {
        val three = (listOf("A", "B", "C")).map { CommunityPacks.Pointer(it, listOf("talk")) }
        assertEquals("talk", rank(three).first())
    }

    @Test
    fun everyPackChannelIsAChannelLink() = CommunityPacks.all.flatMap { it.channels }.forEach { (_, url) ->
        assertTrue(url, Regex("https://www\\.youtube\\.com/channel/UC[A-Za-z0-9_-]{22}").matches(url))
    }

    @Test
    fun offTopicPopularVideosAreDropped() {
        val topic = dev.jordanempire.youflow.ui.data.TasteProfile.from(
            listOf("New open model beats GPT", "Open model reasoning benchmarks", "Why model evals lie").map { it to 1.0 }
        )
        assertTrue(CommunityPacks.onTopic("This open model changes everything", false, topic))
        assertTrue(!CommunityPacks.onTopic("Trump Faces His Biggest Enemy Ahead of the Midterms", false, topic))
        assertTrue(CommunityPacks.onTopic("Behind the scenes at our office", true, topic))
    }
}
