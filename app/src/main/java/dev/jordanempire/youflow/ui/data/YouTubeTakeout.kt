package dev.jordanempire.youflow.ui.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One video from the watch history in a Google Takeout of YouTube. [time] is epoch millis, 0 when unknown. */
@Serializable
data class ImportedWatch(val url: String, val title: String, val channel: String, val channelUrl: String?, val time: Long)

/**
 * Reads YouTube watch history from Google Takeout (watch-history.json or .html, alone or inside the
 * Takeout zip) and keeps it in app storage, where only the "For you" recommender reads it. It is not
 * added to History and never leaves the device.
 */
object YouTubeTakeout {
    const val MAX_ENTRIES = 5000
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ImportedWatch.serializer())

    private fun file(context: Context) = File(context.filesDir, "youtube_watch_history.json")

    fun load(context: Context): List<ImportedWatch> = runCatching { json.decodeFromString(serializer, file(context).readText()) }.getOrDefault(emptyList())

    fun save(context: Context, entries: List<ImportedWatch>) = file(context).writeText(json.encodeToString(serializer, entries))

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Parses [name]/[input] (a .json, .html or Takeout .zip). Newest first, at most [MAX_ENTRIES]. */
    fun parse(name: String, input: InputStream): List<ImportedWatch> {
        val bytes = input.readBytes()
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val entries = if (isZip) fromZip(bytes) else fromText(String(bytes, Charsets.UTF_8), name)
        return entries.distinctBy { it.url to it.time }.sortedByDescending { it.time }.take(MAX_ENTRIES)
    }

    private fun fromZip(bytes: ByteArray): List<ImportedWatch> {
        ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val path = entry.name.lowercase()
                if (path.endsWith("watch-history.json") || path.endsWith("watch-history.html")) {
                    return fromText(zip.readBytes().toString(Charsets.UTF_8), path)
                }
            }
        }
        return emptyList()
    }

    private fun fromText(text: String, name: String): List<ImportedWatch> = if (name.endsWith(".json") || text.trimStart().startsWith("[")) fromJson(text) else fromHtml(text)

    fun fromJson(text: String): List<ImportedWatch> = json.parseToJsonElement(text).jsonArray.mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull
        if (str("header") != null && str("header") != "YouTube") return@mapNotNull null // YouTube Music
        if (o["details"] != null) return@mapNotNull null // ads ("From Google Ads")
        val url = str("titleUrl")?.let(::normalizeUrl) ?: return@mapNotNull null
        val title = cleanTitle(str("title").orEmpty())
        if (title.isBlank() || title.contains("youtube.com/watch")) return@mapNotNull null // removed videos
        val sub = (o["subtitles"] as? JsonArray)?.firstOrNull()?.jsonObject
        ImportedWatch(
            url = url,
            title = title,
            channel = sub?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
            channelUrl = sub?.get("url")?.jsonPrimitive?.contentOrNull,
            time = str("time")?.let(::parseIso) ?: 0L
        )
    }

    private val htmlEntry = Regex(
        "<a href=\"(https://www\\.youtube\\.com/watch\\?v=[^\"]+)\">([^<]*)</a><br>(?:<a href=\"([^\"]+)\">([^<]*)</a><br>)?([^<]*)<br>"
    )

    fun fromHtml(text: String): List<ImportedWatch> = htmlEntry.findAll(text).mapNotNull { m ->
        val (url, title, channelUrl, channel, date) = m.destructured
        val clean = unescape(title)
        if (clean.isBlank() || clean.contains("youtube.com/watch")) return@mapNotNull null
        ImportedWatch(normalizeUrl(unescape(url)), clean, unescape(channel), channelUrl.ifBlank { null }, parseHtmlDate(unescape(date)))
    }.toList()

    private fun cleanTitle(title: String) = title.removePrefix("Watched ").trim()

    private fun normalizeUrl(url: String): String = HnPicks.videoId(url.replace("\\u003d", "="))?.let { "https://www.youtube.com/watch?v=$it" } ?: url

    private fun unescape(s: String) = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()

    private fun parseIso(s: String): Long? = runCatching { java.time.Instant.parse(s).toEpochMilli() }.getOrNull()

    /** English Takeout dates, e.g. "Jan 5, 2024, 10:00:00 AM GMT+03:00"; other languages fall back to 0. */
    private fun parseHtmlDate(s: String): Long {
        val text = s.replace(' ', ' ').replace(' ', ' ').trim()
        listOf("MMM d, yyyy, h:mm:ss a z", "MMM d, yyyy, h:mm:ss a").forEach { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(text)?.time }.getOrNull()?.let { return it }
        }
        return 0L
    }
}
