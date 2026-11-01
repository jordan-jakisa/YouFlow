package dev.jordanempire.youflow.ui

import dev.jordanempire.youflow.ui.data.TasteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteProfileTest {
    private val profile = TasteProfile.from(
        listOf(
            "Building a Rust web server from scratch" to 3.0,
            "Rust async explained in 10 minutes" to 1.0,
            "Learning Rust: ownership and borrowing" to 1.0,
            "Cooking pasta" to 0.5
        )
    )

    @Test
    fun repeatedTermsRankFirst() = assertEquals("rust", profile.topTerms(1).single())

    @Test
    fun stopWordsAndSingleMentionsAreIgnored() {
        assertTrue("the" !in profile.topTerms(20))
        assertTrue("pasta" !in profile.topTerms(20))
    }

    @Test
    fun matchingTitlesScoreHigherThanUnrelatedOnes() {
        val related = profile.overlap("Rust web framework comparison")
        val unrelated = profile.overlap("Funny cat compilation")
        assertTrue(related > unrelated)
        assertEquals(0.0, unrelated, 0.0)
    }

    @Test
    fun emptyProfileScoresZero() {
        assertTrue(TasteProfile.from(emptyList()).isEmpty)
        assertEquals(0.0, TasteProfile.from(emptyList()).overlap("anything"), 0.0)
    }

    @Test
    fun tokensDropNumbersAndShortWords() {
        assertEquals(listOf("minute", "challenge"), TasteProfile.tokens("A 10 minute challenge"))
    }

    @Test
    fun aTopicOverManyDaysOutweighsOneBinge() {
        val binge = List(10) { Triple("Chess opening traps", 1.0, 1L) }
        val lasting = (1L..4L).map { Triple("Sourdough bread baking", 1.0, it) }
        val profile = TasteProfile.fromDays(binge + lasting)
        assertTrue(profile.overlap("sourdough bread") > profile.overlap("chess opening"))
    }
}
