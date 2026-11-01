package dev.jordanempire.youflow.ui

import dev.jordanempire.youflow.ui.data.YouTubeTakeout
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTakeoutTest {
    private val json = """
        [
          {"header":"YouTube","title":"Watched Simple Made Easy","titleUrl":"https://www.youtube.com/watch?v=SxdOUGdseq4",
           "subtitles":[{"name":"ClojureTV","url":"https://www.youtube.com/channel/UCaLlzGqiPE2QRj6sSOawJRg"}],
           "time":"2024-01-05T10:00:00.123Z","products":["YouTube"]},
          {"header":"YouTube","title":"Watched An ad","titleUrl":"https://www.youtube.com/watch?v=aaaaaaaaaaa",
           "details":[{"name":"From Google Ads"}],"time":"2024-01-04T10:00:00Z"},
          {"header":"YouTube Music","title":"Watched A song","titleUrl":"https://www.youtube.com/watch?v=bbbbbbbbbbb","time":"2024-01-03T10:00:00Z"},
          {"header":"YouTube","title":"Watched https://www.youtube.com/watch?v=ccccccccccc","titleUrl":"https://www.youtube.com/watch?v=ccccccccccc","time":"2024-01-02T10:00:00Z"},
          {"header":"YouTube","title":"Watched The Art of Code","titleUrl":"https://www.youtube.com/watch?v=6avJHaC3C2U",
           "subtitles":[{"name":"NDC Conferences","url":"https://www.youtube.com/channel/UCTdw38Cw6jcm0atBPA39a0Q"}],
           "time":"2025-03-01T08:00:00Z"}
        ]
    """.trimIndent()

    private val html = """
        <div class="content-cell mdl-cell mdl-cell--6-col mdl-typography--body-1">Watched&nbsp;<a href="https://www.youtube.com/watch?v=SxdOUGdseq4">Simple Made Easy &amp; more</a><br><a href="https://www.youtube.com/channel/UCaLlzGqiPE2QRj6sSOawJRg">ClojureTV</a><br>Jan 5, 2024, 10:00:00 AM UTC<br></div>
        <div class="content-cell mdl-cell mdl-cell--6-col mdl-typography--body-1">Watched&nbsp;<a href="https://www.youtube.com/watch?v=ccccccccccc">https://www.youtube.com/watch?v=ccccccccccc</a><br>Jan 2, 2024, 10:00:00 AM UTC<br></div>
    """.trimIndent()

    @Test
    fun jsonKeepsOnlyYouTubeVideosNewestFirst() {
        val entries = YouTubeTakeout.parse("watch-history.json", json.byteInputStream())
        assertEquals(listOf("The Art of Code", "Simple Made Easy"), entries.map { it.title })
        assertEquals("https://www.youtube.com/watch?v=SxdOUGdseq4", entries[1].url)
        assertEquals("ClojureTV", entries[1].channel)
        assertTrue(entries[1].time > 0)
    }

    @Test
    fun htmlIsReadWithChannelAndDate() {
        val entries = YouTubeTakeout.parse("watch-history.html", html.byteInputStream())
        assertEquals(1, entries.size)
        assertEquals("Simple Made Easy & more", entries.single().title)
        assertEquals("https://www.youtube.com/channel/UCaLlzGqiPE2QRj6sSOawJRg", entries.single().channelUrl)
        assertTrue(entries.single().time > 0)
    }

    @Test
    fun theWholeTakeoutZipIsSearched() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("Takeout/YouTube and YouTube Music/subscriptions/subscriptions.csv"))
                zip.write("Channel Id,Channel Url,Channel Title\n".toByteArray())
                zip.putNextEntry(ZipEntry("Takeout/YouTube and YouTube Music/history/watch-history.json"))
                zip.write(json.toByteArray())
            }
        }.toByteArray()
        assertEquals(2, YouTubeTakeout.parse("takeout.zip", bytes.inputStream()).size)
    }

    @Test
    fun anUnrelatedFileGivesNothing() = assertTrue(YouTubeTakeout.parse("notes.txt", "hello".byteInputStream()).isEmpty())
}
