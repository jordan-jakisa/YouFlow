package dev.jordanempire.youflow.ui.data

import android.content.Context
import dev.jordanempire.youflow.NewPipeDatabase
import dev.jordanempire.youflow.database.feed.model.FeedGroupEntity
import dev.jordanempire.youflow.database.stream.model.StreamEntity
import dev.jordanempire.youflow.database.subscription.SubscriptionEntity
import dev.jordanempire.youflow.local.feed.FeedDatabaseManager
import dev.jordanempire.youflow.local.feed.service.FeedLoadManager
import dev.jordanempire.youflow.local.history.HistoryRecordManager
import dev.jordanempire.youflow.local.playlist.LocalPlaylistManager
import dev.jordanempire.youflow.local.subscription.SubscriptionManager
import dev.jordanempire.youflow.ui.model.ChannelDetails
import dev.jordanempire.youflow.ui.model.ChannelItem
import dev.jordanempire.youflow.ui.model.ChannelTab
import dev.jordanempire.youflow.ui.model.CommentItem
import dev.jordanempire.youflow.ui.model.ContentItem
import dev.jordanempire.youflow.ui.model.FeedGroupItem
import dev.jordanempire.youflow.ui.model.KioskRef
import dev.jordanempire.youflow.ui.model.PlaylistDetails
import dev.jordanempire.youflow.ui.model.PlaylistItem
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.shorts.mergeChannelShorts
import dev.jordanempire.youflow.util.ExtractorHelper
import dev.jordanempire.youflow.util.KioskTranslator
import dev.jordanempire.youflow.util.image.ImageStrategy
import io.reactivex.rxjava3.core.BackpressureStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.asFlow
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.rx3.awaitSingleOrNull
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Single entry point the Compose UI uses for YouTube data and the local library. */
class YouTubeRepository(private val context: Context) {
    companion object {
        const val WATCH_LATER = "Watch later"
        const val LIKED = "Liked videos"
    }

    private val serviceId = ServiceList.YouTube.serviceId
    private val database get() = NewPipeDatabase.getInstance(context)

    /** Kiosks that currently return videos, in the order the chips are shown. */
    val kiosks: List<KioskRef> by lazy {
        runCatching {
            val list = ServiceList.YouTube.kioskList
            val order = listOf("live", "trending_music", "trending_gaming", "trending_movies_and_shows")
            list.availableKiosks.filter { it in order }.sortedBy { order.indexOf(it) }.map { id ->
                KioskRef(
                    id = id,
                    url = list.getListLinkHandlerFactoryByType(id).fromId(id).url,
                    title = KioskTranslator.getTranslatedKioskName(id, context)
                )
            }
        }.getOrDefault(emptyList())
    }

    // The extractor helpers run their Single on the subscribing thread, so keep them off Main.
    suspend fun kioskVideos(kiosk: KioskRef, forceLoad: Boolean = false): List<VideoItem> = withContext(Dispatchers.IO) {
        val info = ExtractorHelper.getKioskInfo(serviceId, kiosk.url, forceLoad).await()
        info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }

    /**
     * YouTube sometimes answers with a burst of 3xx/4xx/5xx responses ("Too many follow-up
     * requests"). A short backoff and another try usually gets through.
     */
    suspend fun kioskVideosWithRetry(kiosk: KioskRef, forceLoad: Boolean = false): List<VideoItem> {
        var delayMs = 1_500L
        repeat(2) {
            try {
                return kioskVideos(kiosk, forceLoad || it > 0)
            } catch (e: java.io.IOException) {
                kotlinx.coroutines.delay(delayMs)
                delayMs *= 2
            }
        }
        return kioskVideos(kiosk, true)
    }

    /** Warms the stream info cache so opening one of these videos skips the network round trip. */
    suspend fun prefetchStreams(videos: List<VideoItem>) = withContext(Dispatchers.IO) {
        videos.forEach { runCatching { ExtractorHelper.getStreamInfo(serviceId, it.url, false).await() } }
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        ExtractorHelper.suggestionsFor(serviceId, query).await()
    }

    class SearchPage(val items: List<ContentItem>, val next: Page?, val suggestion: String?, val corrected: Boolean)

    /** [filter] is one of the extractor's content filters ("videos", "channels", "playlists") or null for all. */
    suspend fun search(query: String, filter: String? = null): SearchPage = withContext(Dispatchers.IO) {
        val filters = listOfNotNull(filter)
        val info = ExtractorHelper.searchFor(serviceId, query, filters, "").await()
        SearchPage(info.relatedItems.mapNotNull { it.toContent() }, info.nextPage, info.searchSuggestion, info.isCorrectedSearch)
    }

    suspend fun moreSearch(query: String, filter: String?, page: Page): Paged<ContentItem> = withContext(Dispatchers.IO) {
        val more = ExtractorHelper.getMoreSearchItems(serviceId, query, listOfNotNull(filter), "", page).await()
        Paged(more.items.mapNotNull { it.toContent() }, more.nextPage)
    }

    // region Likes, hidden channels and the local recommender

    private val hiddenPrefs by lazy { context.getSharedPreferences("youflow_recommender", Context.MODE_PRIVATE) }

    fun hiddenChannels(): Set<String> = hiddenPrefs.getStringSet("hidden_channels", emptySet()).orEmpty()

    fun notInterested(): Set<String> = hiddenPrefs.getStringSet("not_interested", emptySet()).orEmpty()

    /** The video never shows up in "For you" again. Kept to the latest 500 so the set stays small. */
    fun markNotInterested(videoUrl: String) {
        val updated = (notInterested() - videoUrl).toList() + videoUrl
        hiddenPrefs.edit().putStringSet("not_interested", updated.takeLast(500).toSet()).apply()
    }

    fun hideChannel(channelUrl: String) {
        hiddenPrefs.edit().putStringSet("hidden_channels", hiddenChannels() + channelUrl).apply()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun likedVideos(): Flow<List<VideoItem>> = playlistManager.playlists.toObservable().asFlow().flatMapLatest { lists ->
        val liked = lists.firstOrNull { it.orderingName == LIKED }
        if (liked == null) kotlinx.coroutines.flow.flowOf(emptyList()) else playlistVideos(liked.uid).map { rows -> rows.map { it.second } }
    }

    suspend fun setLiked(video: VideoItem, liked: Boolean) = withContext(Dispatchers.IO) {
        val existing = playlistManager.playlists.firstOrError().await().firstOrNull { it.orderingName == LIKED }
        if (liked) {
            if (existing == null) {
                playlistManager.createPlaylist(LIKED, listOf(video.toStreamEntity())).awaitSingleOrNull()
            } else if (playlistManager.getPlaylistStreams(existing.uid).firstOrError().await().none { it.streamEntity.url == video.url }) {
                playlistManager.appendToPlaylist(existing.uid, listOf(video.toStreamEntity())).awaitSingleOrNull()
            }
        } else if (existing != null) {
            val keep = playlistManager.getPlaylistStreams(existing.uid).firstOrError().await()
                .filter { it.streamEntity.url != video.url }.map { it.streamId }
            playlistManager.updateJoin(existing.uid, keep).await()
        }
    }

    /** True when YouTube watch history from Google Takeout has been imported. */
    fun hasImportedHistory(): Boolean = YouTubeTakeout.load(context).isNotEmpty()

    /** Reads a Takeout file the user picked and keeps its watch history. Returns how many videos. */
    suspend fun importTakeout(uri: android.net.Uri, name: String): Int = withContext(Dispatchers.IO) {
        val entries = context.contentResolver.openInputStream(uri)?.use { YouTubeTakeout.parse(name, it) }.orEmpty()
        if (entries.isNotEmpty()) YouTubeTakeout.save(context, entries)
        entries.size
    }

    fun clearImportedHistory() = YouTubeTakeout.clear(context)

    /** What the device knows you watch: YouFlow history plus imported YouTube history, as one list. */
    private class Signals(
        val taste: TasteProfile,
        val watched: Set<String>,
        /** Channel url -> distinct days you watched it, the lasting-interest signal. */
        val channelDays: Map<String?, Int>,
        val likedChannels: Set<String>,
        val subscribed: Set<String>,
        val hidden: Set<String>,
        val dismissed: Set<String>
    )

    private suspend fun signals(): Signals {
        val history = database.streamHistoryDAO().history.firstOrError().await()
        val states = database.streamStateDAO().getAll().firstOrError().await().associateBy { it.streamUid }
        val liked = likedVideos().first()
        val imported = YouTubeTakeout.load(context)
        val now = System.currentTimeMillis()
        fun progressOf(entry: dev.jordanempire.youflow.database.history.model.StreamHistoryEntry): Double {
            val duration = entry.streamEntity.duration.coerceAtLeast(1)
            return ((states[entry.streamId]?.progressMillis ?: 0L) / 1000.0 / duration).coerceIn(0.0, 1.0)
        }
        val today = TasteProfile.day(now)
        val titles = ArrayList<Triple<String, Double, Long>>()
        // Liked videos count every time, on a day of their own.
        liked.forEachIndexed { i, video -> titles += Triple(video.title, 3.0, -1L - i) }
        history.sortedByDescending { it.accessDate }.take(60).forEachIndexed { rank, entry ->
            val day = TasteProfile.day(entry.accessDate.toInstant().toEpochMilli())
            titles += Triple(entry.streamEntity.title, Math.pow(0.97, rank.toDouble()) * (0.7 + progressOf(entry)), day)
        }
        // Imported history fades with age: a year ago counts about a third of today.
        imported.take(1500).forEach { w ->
            val day = if (w.time > 0) TasteProfile.day(w.time) else today - 400
            titles += Triple(w.title, 0.9 * Math.exp(-(today - day).coerceAtLeast(0) / 365.0), day)
        }
        val channelDays = HashMap<String?, MutableSet<Long>>()
        history.forEach { channelDays.getOrPut(it.streamEntity.uploaderUrl) { HashSet() } += TasteProfile.day(it.accessDate.toInstant().toEpochMilli()) }
        imported.forEach { w -> if (w.time > 0) channelDays.getOrPut(w.channelUrl) { HashSet() } += TasteProfile.day(w.time) }
        return Signals(
            taste = TasteProfile.fromDays(titles),
            watched = history.map { it.streamEntity.url }.toSet() + liked.map { it.url } + imported.map { it.url },
            channelDays = channelDays.mapValues { it.value.size },
            likedChannels = liked.mapNotNull { it.channelUrl }.toSet(),
            subscribed = subscriptions().first().map { it.url }.toSet(),
            hidden = hiddenChannels(),
            dismissed = notInterested()
        )
    }

    /** Channel affinity from lasting interest: distinct days watched, not plays. */
    private fun Signals.affinity(channel: String?): Double = 1 + 0.25 * Math.log(1.0 + (channelDays[channel] ?: 0)) / Math.log(2.0) +
        (if (channel in subscribed) 0.5 else 0.0) +
        (if (channel in likedChannels) 0.5 else 0.0)

    private fun Signals.keep(video: VideoItem): Boolean = video.url !in dismissed && video.url !in watched && !video.isShort &&
        (video.durationSeconds == 0L || video.durationSeconds > 60) &&
        (video.channelUrl == null || video.channelUrl !in hidden)

    /** At most two per channel in the first 20, so one channel never fills the screen. */
    private fun varied(ranked: List<VideoItem>, limit: Int = 40): List<VideoItem> {
        val perChannel = HashMap<String, Int>()
        val top = ArrayList<VideoItem>()
        val rest = ArrayList<VideoItem>()
        ranked.forEach { video ->
            val key = video.channelUrl ?: video.channel
            val used = perChannel.getOrDefault(key, 0)
            if (top.size < 20 && used < 2) {
                top += video
                perChannel[key] = used + 1
            } else {
                rest += video
            }
        }
        return (top + rest).take(limit)
    }

    /**
     * "For you": videos related to what you watched and liked, imported YouTube history included,
     * unseen uploads of channels you follow, and Hacker News favourites that match your taste. Taste
     * and channel affinity count the distinct days you came back to something, not raw plays, so one
     * binge does not take over. Everything is computed on the device; nothing is sent anywhere except
     * the normal requests for related videos and searches. Each video says why it is there. Anything
     * in [avoid] (the list already on screen) sinks, so every refresh brings something new.
     */
    suspend fun recommendations(avoid: Set<String> = emptySet()): List<VideoItem> = withContext(Dispatchers.IO) {
        val history = database.streamHistoryDAO().history.firstOrError().await()
        val states = database.streamStateDAO().getAll().firstOrError().await().associateBy { it.streamUid }
        val liked = likedVideos().first()
        val imported = YouTubeTakeout.load(context)
        val signals = signals()

        // Seeds: url -> weight, with the title shown in "Because you watched ...".
        val seeds = LinkedHashMap<String, Double>()
        val seedTitles = HashMap<String, String>()
        val seedLiked = HashSet<String>()
        liked.forEach {
            seeds[it.url] = 3.0
            seedTitles[it.url] = it.title
            seedLiked += it.url
        }
        history.sortedByDescending { it.accessDate }.take(12).forEachIndexed { rank, entry ->
            val duration = entry.streamEntity.duration.coerceAtLeast(1)
            val progress = ((states[entry.streamId]?.progressMillis ?: 0L) / 1000.0 / duration).coerceIn(0.0, 1.0)
            val weight = Math.pow(0.9, rank.toDouble()) * (0.6 + progress) * (1 + 0.2 * (entry.repeatCount - 1).coerceIn(0, 3))
            seeds.merge(entry.streamEntity.url, weight) { a, b -> a + b }
            seedTitles[entry.streamEntity.url] = entry.streamEntity.title
        }
        // From imported history: what you came back to on the most different days, then the latest.
        imported.groupBy { it.url }.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<ImportedWatch>>> { (_, plays) -> plays.map { TasteProfile.day(it.time) }.distinct().size }
                    .thenByDescending { (_, plays) -> plays.maxOf { it.time } }
            )
            .take(10).forEachIndexed { rank, (url, plays) ->
                val days = plays.map { TasteProfile.day(it.time) }.distinct().size
                seeds.merge(url, Math.pow(0.9, rank.toDouble()) * (0.5 + 0.3 * days.coerceAtMost(4))) { a, b -> a + b }
                seedTitles.putIfAbsent(url, plays.first().title)
            }
        // Weighted random sample (Efraimidis-Spirakis) of the 24 strongest seeds: favourites come up most.
        val topSeeds = seeds.entries.sortedByDescending { it.value }.take(24)
            .sortedByDescending { Math.pow(Math.random(), 1.0 / it.value.coerceAtLeast(0.01)) }.take(8)

        val scores = HashMap<String, Double>()
        val items = HashMap<String, VideoItem>()
        val reasons = HashMap<String, Pair<Double, String>>() // url -> strongest contribution and its reason
        fun add(video: VideoItem, points: Double, reason: String) {
            scores.merge(video.url, points) { a, b -> a + b }
            items.putIfAbsent(video.url, video)
            val best = reasons[video.url]
            if (best == null || points > best.first) reasons[video.url] = points to reason
        }
        val limiter = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            topSeeds.map { (url, weight) ->
                async {
                    limiter.withPermit {
                        val related = runCatching {
                            ExtractorHelper.getStreamInfo(serviceId, url, false).await().relatedItems
                                .filterIsInstance<StreamInfoItem>()
                        }.getOrDefault(emptyList())
                        val title = seedTitles[url].orEmpty().take(50)
                        val why = if (url in seedLiked) "Because you liked \"$title\"" else "Because you watched \"$title\""
                        synchronized(scores) {
                            related.forEachIndexed { rank, item -> add(item.toVideo(), weight / (1 + rank * 0.15), why) }
                        }
                    }
                }
            }.awaitAll()
        }
        // Similar videos by taste: search for the words you came back to most.
        if (!signals.taste.isEmpty) {
            val terms = signals.taste.topTerms(8).shuffled().take(4)
            val queries = listOfNotNull(
                terms.take(2).joinToString(" ").takeIf { terms.size >= 2 },
                terms.drop(2).take(2).joinToString(" ").takeIf { terms.size >= 4 },
                terms.firstOrNull().takeIf { terms.size == 1 }
            )
            kotlinx.coroutines.coroutineScope {
                queries.map { q ->
                    async {
                        limiter.withPermit {
                            val found = runCatching { search(q, "videos").items.filterIsInstance<VideoItem>().take(12) }
                                .getOrDefault(emptyList())
                            synchronized(scores) {
                                found.forEachIndexed { rank, item -> add(item, 0.9 / (1 + rank * 0.1), "You often watch \"$q\"") }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        // Unseen uploads from followed channels are always candidates.
        feed().take(30).forEach { add(it, 1.2, "New from a channel you follow") }
        // Hacker News favourites that match your taste, a few per refresh.
        val hn = HnPicks.get(context)
        if (!signals.taste.isEmpty && hn.videos.isNotEmpty()) {
            val watchedIds = signals.watched.mapNotNull { HnPicks.videoId(it) }.toSet()
            val skip = (signals.dismissed + avoid).mapNotNull { HnPicks.videoId(it) }.toSet()
            hn.pick(signals.taste, watchedIds, skip, 6)
                .filter { (pick, _) -> signals.taste.overlap("${pick.title} ${pick.topics}") > 0 }
                .forEach { (pick, why) -> add(pick.toVideo(why), 0.8 + 0.1 * pick.score.coerceAtMost(10.0), why) }
        }

        val ranked = items.values
            .filter { signals.keep(it) }
            .map { video ->
                val jitter = 0.8 + 0.4 * Math.random()
                video.copy(reason = reasons[video.url]?.second) to (scores[video.url] ?: 0.0) * signals.affinity(video.channelUrl) *
                    (1 + 0.8 * signals.taste.overlap(video.title)) * jitter * (if (video.url in avoid) 0.05 else 1.0)
            }
            .sortedByDescending { it.second }
            .map { it.first }
        varied(ranked)
    }

    /** "HN picks": Hacker News favourites, a fresh score-weighted mix, raised where they match your taste. */
    suspend fun hnPicks(avoid: Set<String> = emptySet()): List<VideoItem> = withContext(Dispatchers.IO) {
        val signals = signals()
        val watchedIds = signals.watched.mapNotNull { HnPicks.videoId(it) }.toSet()
        val skip = (signals.dismissed + avoid).mapNotNull { HnPicks.videoId(it) }.toSet()
        val picks = HnPicks.get(context).pick(signals.taste, watchedIds, skip, 40).map { (pick, why) -> pick.toVideo(why) }
        varied(picks)
    }

    /**
     * A community topic ([CommunityPack]): the pack channels' latest uploads plus the videos several
     * of them independently point to, ranked by [CommunityPacks.rank] and raised where they match
     * your taste.
     */
    suspend fun packVideos(pack: CommunityPack, avoid: Set<String> = emptySet()): List<VideoItem> = withContext(Dispatchers.IO) {
        val signals = signals()
        val limiter = kotlinx.coroutines.sync.Semaphore(6)
        val uploads: Map<String, List<VideoItem>> = kotlinx.coroutines.coroutineScope {
            pack.channels.map { (name, url) ->
                async {
                    limiter.withPermit {
                        url to runCatching {
                            val loaded = channel(url)
                            val tab = loaded.details.tabs.firstOrNull { it.key == "videos" } ?: return@runCatching emptyList()
                            channelTab(loaded.handlers[tab.index], null).items.filterIsInstance<VideoItem>()
                                .filter { !it.isShort && (it.durationSeconds == 0L || it.durationSeconds > 60) }
                                .take(12)
                                .map { it.copy(channel = it.channel.ifBlank { name }, channelUrl = it.channelUrl ?: url, avatar = it.avatar ?: loaded.details.avatar) }
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().toMap()
        }
        if (uploads.values.all { it.isEmpty() }) throw java.io.IOException("Couldn't reach these channels")
        val items = HashMap<String, VideoItem>()
        uploads.values.flatten().forEach { items.putIfAbsent(it.url, it) }
        // Two uploads per channel (the newest and one of the rest) say what each channel points to.
        val sampled = uploads.flatMap { (url, list) -> listOfNotNull(list.firstOrNull(), list.drop(1).randomOrNull()).map { url to it } }
        val pointerFrom = HashMap<String, MutableSet<String>>() // related url -> pack channel names
        val pointers = kotlinx.coroutines.coroutineScope {
            sampled.map { (channelUrl, video) ->
                async {
                    limiter.withPermit {
                        val related = runCatching {
                            ExtractorHelper.getStreamInfo(serviceId, video.url, false).await().relatedItems
                                .filterIsInstance<StreamInfoItem>().take(15)
                        }.getOrDefault(emptyList())
                        val name = pack.channels.firstOrNull { it.second == channelUrl }?.first.orEmpty()
                        synchronized(items) {
                            related.forEach {
                                items.putIfAbsent(it.url, it.toVideo())
                                pointerFrom.getOrPut(it.url) { HashSet() } += name
                            }
                        }
                        CommunityPacks.Pointer(channelUrl, related.map { it.url })
                    }
                }
            }.awaitAll()
        }
        val packChannelIds = pack.channelUrls.mapNotNull { channelId(it) }.toSet()
        // What the topic is about: words that recur in its channels' own recent titles.
        val topic = TasteProfile.from(uploads.values.flatten().map { it.title to 1.0 })
        val ranked = CommunityPacks.rank(
            uploads.mapValues { (_, list) -> list.map { it.url } },
            pointers,
            channelOf = { url -> items[url]?.channelUrl?.let { channelId(it) } },
            packChannels = packChannelIds
        ).mapNotNull { (url, score) ->
            val video = items[url]?.takeIf { signals.keep(it) } ?: return@mapNotNull null
            val fromPack = video.channelUrl?.let { channelId(it) } in packChannelIds
            if (!CommunityPacks.onTopic(video.title, fromPack, topic)) return@mapNotNull null
            val from = pointerFrom[url].orEmpty().filter { it.isNotBlank() }
            val why = when {
                from.size >= 2 -> "Related to videos from ${from.take(2).joinToString(" and ")}" + (if (from.size > 2) " and ${from.size - 2} more" else "")
                else -> "From the ${pack.title} channels"
            }
            video.copy(reason = why) to score * (1 + topic.overlap(video.title)) * (1 + 0.5 * signals.taste.overlap(video.title)) *
                (if (url in avoid) 0.05 else 1.0)
        }
            .sortedByDescending { it.second }
            .map { it.first }
        varied(ranked)
    }

    private fun channelId(url: String): String? = Regex("channel/(UC[A-Za-z0-9_-]{22})").find(url)?.groupValues?.get(1)

    // endregion

    /** Videos from subscribed channels, newest first, with resume progress. [groupId] -1 means all. */
    suspend fun feed(groupId: Long = FeedGroupEntity.GROUP_ALL_ID): List<VideoItem> = withContext(Dispatchers.IO) {
        val streams = FeedDatabaseManager(context)
            .getStreams(groupId, true, true, false)
            .awaitSingleOrNull()
            .orEmpty()
        // Stored feed streams have no uploader picture; use the one saved with the subscription.
        val avatars = subscriptions().first().filter { !it.avatar.isNullOrBlank() }.associate { it.url to it.avatar }
        streams.map { it.stream.toVideo(it.stateProgressMillis).let { v -> v.copy(avatar = avatars[v.channelUrl]) } }
    }

    fun feedGroups(): Flow<List<FeedGroupItem>> = FeedDatabaseManager(context).groups().toObservable().asFlow().map { list ->
        list.map { FeedGroupItem(it.uid, it.name) }
    }

    suspend fun groupMembers(groupId: Long): List<Long> = withContext(Dispatchers.IO) {
        database.feedGroupDAO().getSubscriptionIdsFor(groupId).firstOrError().await()
    }

    /** Creates the group when [id] is null, otherwise renames it. Members are replaced by [subscriptionIds]. */
    suspend fun saveGroup(id: Long?, name: String, subscriptionIds: List<Long>) = withContext(Dispatchers.IO) {
        val manager = FeedDatabaseManager(context)
        val groupId = if (id == null) {
            manager.createGroup(name, dev.jordanempire.youflow.local.subscription.FeedGroupIcon.ALL).awaitSingleOrNull() ?: return@withContext
        } else {
            manager.getGroup(id).awaitSingleOrNull()?.let { manager.updateGroup(it.copy(name = name)).await() }
            id
        }
        manager.updateSubscriptionsForGroup(groupId, subscriptionIds).await()
    }

    suspend fun deleteGroup(id: Long) = withContext(Dispatchers.IO) {
        FeedDatabaseManager(context).deleteGroup(id).await()
    }

    /** Fetches new uploads for every subscription. Suspends until the refresh is done. */
    suspend fun refreshFeed() = withContext(Dispatchers.IO) {
        FeedLoadManager(context).startLoading(ignoreOutdatedThreshold = true).await()
    }

    fun subscriptions(): Flow<List<ChannelItem>> = SubscriptionManager(context).subscriptions().toObservable().asFlow().map { list ->
        list.map { it.toChannel() }
    }

    suspend fun subscribe(channel: ChannelItem) = withContext(Dispatchers.IO) {
        SubscriptionManager(context).insertSubscription(
            SubscriptionEntity().apply {
                serviceId = this@YouTubeRepository.serviceId
                url = channel.url
                name = channel.name
                avatarUrl = channel.avatar
                subscriberCount = channel.subscribers
                description = channel.description
            }
        )
    }

    suspend fun unsubscribe(channelUrl: String) = withContext(Dispatchers.IO) {
        SubscriptionManager(context).deleteSubscription(serviceId, channelUrl).await()
    }

    fun history(): Flow<List<VideoItem>> = database.streamHistoryDAO().history.toObservable().asFlow().map { list ->
        list.map { it.streamEntity.toVideo() }
    }

    /** Started but unfinished videos, most recently watched first. */
    fun continueWatching(): Flow<List<VideoItem>> = combine(
        database.streamHistoryDAO().history.toObservable().asFlow(),
        database.streamStateDAO().getAll().toObservable().asFlow()
    ) { history, states ->
        val byStream = states.associateBy { it.streamUid }
        history.mapNotNull { h ->
            val state = byStream[h.streamId] ?: return@mapNotNull null
            if (state.progressMillis <= 0 || state.isFinished(h.streamEntity.duration)) {
                null
            } else {
                h.streamEntity.toVideo(state.progressMillis)
            }
        }.distinctBy { it.url }.take(12)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        HistoryRecordManager(context).deleteWholeStreamHistory().await()
        HistoryRecordManager(context).deleteCompleteStreamStateHistory().await()
        Unit
    }

    private val playlistManager get() = LocalPlaylistManager(database)

    fun playlists(): Flow<List<PlaylistItem>> = playlistManager.playlists.toObservable().asFlow().map { list ->
        list.map {
            PlaylistItem(
                url = "local:${it.uid}",
                name = it.orderingName.orEmpty(),
                thumbnail = it.thumbnailUrl,
                uploader = null,
                streamCount = it.streamCount
            )
        }
    }

    private fun VideoItem.toStreamEntity() = StreamEntity(
        serviceId = serviceId,
        url = url,
        title = title,
        streamType = if (isLive) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM,
        duration = durationSeconds,
        uploader = channel,
        uploaderUrl = channelUrl,
        thumbnailUrl = thumbnail,
        viewCount = views,
        textualUploadDate = uploaded
    )

    /** Creates a playlist containing [first] when given. Returns its id. */
    suspend fun createPlaylist(name: String, first: VideoItem?): Long = withContext(Dispatchers.IO) {
        val ids = playlistManager.createPlaylist(name, listOfNotNull(first?.toStreamEntity())).awaitSingleOrNull()
        ids?.firstOrNull() ?: -1L
    }

    suspend fun addToPlaylist(playlistId: Long, video: VideoItem) = withContext(Dispatchers.IO) {
        playlistManager.appendToPlaylist(playlistId, listOf(video.toStreamEntity())).awaitSingleOrNull()
        Unit
    }

    /** The "Watch later" playlist, created on first use. */
    suspend fun addToWatchLater(video: VideoItem) = withContext(Dispatchers.IO) {
        val existing = playlistManager.playlists.firstOrError().await().firstOrNull { it.orderingName == WATCH_LATER }
        if (existing != null) {
            playlistManager.appendToPlaylist(existing.uid, listOf(video.toStreamEntity())).awaitSingleOrNull()
        } else {
            playlistManager.createPlaylist(WATCH_LATER, listOf(video.toStreamEntity())).awaitSingleOrNull()
        }
        Unit
    }

    fun playlistVideos(playlistId: Long): Flow<List<Pair<Long, VideoItem>>> = playlistManager.getPlaylistStreams(playlistId).toObservable().asFlow().map { list ->
        list.map { it.streamId to it.streamEntity.toVideo(it.progressMillis) }
    }

    fun playlistName(playlistId: Long): Flow<String> = playlists().map { list -> list.firstOrNull { it.url == "local:$playlistId" }?.name.orEmpty() }

    suspend fun removeFromPlaylist(playlistId: Long, streamId: Long) = withContext(Dispatchers.IO) {
        val remaining = playlistManager.getPlaylistStreams(playlistId).firstOrError().await()
            .filter { it.streamId != streamId }.map { it.streamId }
        playlistManager.updateJoin(playlistId, remaining).await()
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        database.playlistDAO().deletePlaylist(playlistId)
        Unit
    }

    suspend fun renamePlaylist(playlistId: Long, name: String) = withContext(Dispatchers.IO) {
        playlistManager.renamePlaylist(playlistId, name).awaitSingleOrNull()
        Unit
    }

    /** One page of a list plus the token for the next one. */
    class Paged<T>(val items: List<T>, val next: Page?)

    class LoadedChannel(val details: ChannelDetails, val handlers: List<ListLinkHandler>)

    suspend fun channel(url: String): LoadedChannel = withContext(Dispatchers.IO) {
        val info: ChannelInfo = ExtractorHelper.getChannelInfo(serviceId, url, false).await()
        val labels = mapOf(
            "videos" to "Videos",
            "shorts" to "Shorts",
            "livestreams" to "Live",
            "playlists" to "Playlists",
            "albums" to "Releases",
            "podcasts" to "Podcasts",
            "courses" to "Courses"
        )
        val handlers = mutableListOf<ListLinkHandler>()
        val tabs = mutableListOf<ChannelTab>()
        info.tabs.forEach { handler ->
            val key = handler.contentFilters.firstOrNull()
            val label = labels[key] ?: return@forEach
            tabs += ChannelTab(key.orEmpty(), label, handlers.size)
            handlers += handler
        }
        LoadedChannel(
            ChannelDetails(
                url = info.url,
                name = info.name,
                avatar = ImageStrategy.choosePreferredImage(info.avatars),
                banner = ImageStrategy.choosePreferredImage(info.banners),
                subscribers = info.subscriberCount.takeIf { it >= 0 },
                description = info.description,
                tabs = tabs
            ),
            handlers
        )
    }

    suspend fun channelTab(handler: ListLinkHandler, page: Page?): Paged<ContentItem> = withContext(Dispatchers.IO) {
        if (page == null) {
            val tab = ExtractorHelper.getChannelTab(serviceId, handler, false).await()
            Paged(tab.relatedItems.mapNotNull { it.toContent() }, tab.nextPage)
        } else {
            val more = ExtractorHelper.getMoreChannelTabItems(serviceId, handler, page).await()
            Paged(more.items.mapNotNull { it.toContent() }, more.nextPage)
        }
    }

    class LoadedPlaylist(val details: PlaylistDetails, val info: PlaylistInfo, val videos: List<VideoItem>, val next: Page?)

    suspend fun playlist(url: String): LoadedPlaylist = withContext(Dispatchers.IO) {
        val info = ExtractorHelper.getPlaylistInfo(serviceId, url, false).await()
        LoadedPlaylist(
            PlaylistDetails(
                url = info.url,
                name = info.name,
                thumbnail = ImageStrategy.choosePreferredImage(info.thumbnails),
                uploader = info.uploaderName,
                streamCount = info.streamCount,
                description = info.description?.content
            ),
            info,
            info.relatedItems.map { it.toVideo() },
            info.nextPage
        )
    }

    suspend fun morePlaylistVideos(url: String, page: Page): Paged<VideoItem> = withContext(Dispatchers.IO) {
        val more = ExtractorHelper.getMorePlaylistItems(serviceId, url, page).await()
        Paged(more.items.map { it.toVideo() }, more.nextPage)
    }

    class LoadedComments(val info: CommentsInfo, val items: List<CommentItem>, val next: Page?, val disabled: Boolean, val count: Int)

    suspend fun comments(videoUrl: String): LoadedComments = withContext(Dispatchers.IO) {
        val info = ExtractorHelper.getCommentsInfo(serviceId, videoUrl, false).await()
        LoadedComments(info, info.relatedItems.map { it.toComment() }, info.nextPage, info.isCommentsDisabled, info.commentsCount)
    }

    suspend fun moreComments(info: CommentsInfo, page: Page): Paged<CommentItem> = withContext(Dispatchers.IO) {
        val more = ExtractorHelper.getMoreCommentItems(serviceId, info, page).await()
        Paged(more.items.map { it.toComment() }, more.nextPage)
    }

    suspend fun replies(videoUrl: String, token: Any): Paged<CommentItem> = withContext(Dispatchers.IO) {
        val more = ExtractorHelper.getMoreCommentItems(serviceId, videoUrl, token as Page).await()
        Paged(more.items.map { it.toComment() }, more.nextPage)
    }

    private fun CommentsInfoItem.toComment() = CommentItem(
        id = commentId.orEmpty(),
        author = uploaderName.orEmpty(),
        avatar = ImageStrategy.choosePreferredImage(uploaderAvatars),
        text = commentText?.content?.let { androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString() }.orEmpty(),
        likes = textualLikeCount?.takeIf { it.isNotBlank() } ?: likeCount.takeIf { it > 0 }?.toString(),
        posted = textualUploadDate,
        hearted = isHeartedByUploader,
        pinned = isPinned,
        isOwner = isChannelOwner,
        replyCount = replyCount.coerceAtLeast(0),
        repliesToken = replies
    )

    /**
     * Shorts from the channels you subscribe to (their Shorts tab), shuffled. Nothing else: with no
     * subscriptions this is empty and the Shorts screen asks the user to subscribe, instead of filling
     * the feed with strangers' videos from a search.
     */
    suspend fun shorts(): List<VideoItem> {
        val channels = subscriptions().first().take(8)
        // Fetch a few channels at a time instead of one after the other.
        val limiter = kotlinx.coroutines.sync.Semaphore(4)
        val perChannel = kotlinx.coroutines.coroutineScope {
            channels.map { channel ->
                async {
                    limiter.withPermit {
                        runCatching {
                            val loaded = channel(channel.url)
                            val tab = loaded.details.tabs.firstOrNull { it.key == "shorts" }
                                ?: return@runCatching emptyList()
                            channelTab(loaded.handlers[tab.index], null).items.filterIsInstance<VideoItem>()
                                .map { it.copy(channel = it.channel.ifBlank { channel.name }, channelUrl = channel.url, avatar = it.avatar ?: channel.avatar) }
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll()
        }
        return mergeChannelShorts(perChannel)
    }

    fun isSubscribed(url: String): Flow<Boolean> = subscriptions().map { list -> list.any { it.url == url } }

    private fun SubscriptionEntity.toChannel() = ChannelItem(
        url = url.orEmpty(),
        name = name.orEmpty(),
        avatar = avatarUrl,
        subscribers = subscriberCount,
        description = description,
        notify = notificationMode == 1,
        uid = uid
    )

    /**
     * Channels that came from an import have no picture stored. Fetch their channel pages (a few at a
     * time) and save the avatar; the subscriptions list updates itself as each one lands.
     */
    suspend fun fillMissingAvatars() = withContext(Dispatchers.IO) {
        val manager = SubscriptionManager(context)
        val missing = manager.subscriptions().firstOrError().await().filter { it.avatarUrl.isNullOrBlank() && !it.url.isNullOrBlank() }
        if (missing.isEmpty()) return@withContext
        val limiter = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            missing.map { sub ->
                async {
                    limiter.withPermit {
                        runCatching {
                            val info = ExtractorHelper.getChannelInfo(serviceId, sub.url!!, false).await()
                            manager.updateChannelInfo(info).await()
                        }
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun setNotifications(channelUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        SubscriptionManager(context).updateNotificationMode(serviceId, channelUrl, if (enabled) 1 else 0).await()
    }

    fun isNotifying(channelUrl: String): Flow<Boolean> = subscriptions().map { list -> list.firstOrNull { it.url == channelUrl }?.notify == true }

    private fun InfoItem.toContent(): ContentItem? = when (this) {
        is StreamInfoItem -> toVideo()

        is ChannelInfoItem -> ChannelItem(
            url = url,
            name = name,
            avatar = ImageStrategy.choosePreferredImage(thumbnails),
            subscribers = subscriberCount.takeIf { it >= 0 },
            description = description
        )

        is PlaylistInfoItem -> PlaylistItem(
            url = url,
            name = name,
            thumbnail = ImageStrategy.choosePreferredImage(thumbnails),
            uploader = uploaderName,
            streamCount = streamCount
        )

        else -> null
    }

    private fun StreamInfoItem.toVideo() = VideoItem(
        url = url,
        title = name,
        channel = uploaderName.orEmpty(),
        channelUrl = uploaderUrl,
        thumbnail = ImageStrategy.choosePreferredImage(thumbnails),
        avatar = ImageStrategy.choosePreferredImage(uploaderAvatars),
        durationSeconds = duration,
        views = viewCount.takeIf { it >= 0 },
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = isShortFormContent
    )

    private fun StreamEntity.toVideo(progressMillis: Long? = null) = VideoItem(
        url = url,
        title = title,
        channel = uploader,
        channelUrl = uploaderUrl,
        thumbnail = thumbnailUrl,
        avatar = null,
        durationSeconds = duration,
        views = viewCount,
        uploaded = textualUploadDate,
        isLive = streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM,
        isShort = false,
        progress = if (progressMillis != null && duration > 0) {
            (progressMillis / 1000f / duration).coerceIn(0f, 1f)
        } else {
            0f
        }
    )
}
