package dev.jordanempire.youflow.ui

import dev.jordanempire.youflow.ui.data.HnPicks
import dev.jordanempire.youflow.ui.data.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HnPicksTest {
    private val data = HnPicks.parse(
        """
        {"generated":"2026-10-31","videos":[
          {"id":"SxdOUGdseq4","t":"Simple Made Easy","c":"ClojureTV","s":20.0,"m":15,"k":"simplicity software design clojure"},
          {"id":"6avJHaC3C2U","t":"The Art of Code","c":"NDC Conferences","s":8.0,"m":6,"k":"programming culture talk"},
          {"id":"fNk_zzaMoSs","t":"Vectors, Essence of linear algebra","c":"3Blue1Brown","s":25.0,"m":18,"k":"linear algebra math"}
        ],"related":{"SxdOUGdseq4":["6avJHaC3C2U"]}}
        """.trimIndent()
    )

    @Test
    fun watchedAndSkippedVideosNeverComeBack() {
        val picks = data.pick(TasteProfile.from(emptyList()), setOf("SxdOUGdseq4"), setOf("fNk_zzaMoSs"), 10).map { it.first.id }
        assertEquals(listOf("6avJHaC3C2U"), picks)
    }

    @Test
    fun alsoRecommendedSaysWhichVideoItGoesWith() {
        val (_, reason) = data.pick(TasteProfile.from(emptyList()), setOf("SxdOUGdseq4"), setOf("fNk_zzaMoSs"), 1).single()
        assertTrue(reason, reason.contains("Simple Made Easy"))
    }

    @Test
    fun reasonCountsMonths() = assertEquals("Recommended on Hacker News in 18 different months", HnPicks.reason(data.videos[2], null))

    @Test
    fun videoIdComesFromWatchLinks() {
        assertEquals("SxdOUGdseq4", HnPicks.videoId("https://www.youtube.com/watch?v=SxdOUGdseq4&t=10"))
        assertEquals(null, HnPicks.videoId("https://www.youtube.com/channel/UCaLlzGqiPE2QRj6sSOawJRg"))
    }

    @Test
    fun matchingTasteRanksFirstMostOfTheTime() {
        val taste = TasteProfile.from(listOf("Linear algebra for programmers" to 3.0, "Linear algebra math" to 3.0))
        var mathFirst = 0
        repeat(200) { if (data.pick(taste, emptySet(), emptySet(), 1).single().first.id == "fNk_zzaMoSs") mathFirst++ }
        assertTrue("math first $mathFirst of 200", mathFirst > 120)
    }
}
