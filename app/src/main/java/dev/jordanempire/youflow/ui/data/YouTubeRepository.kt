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

    /**
     * "For you": videos related to what you watched and liked recently, plus unseen uploads of
     * channels you follow. Everything is computed on the device from local data, nothing is sent
     * anywhere except the normal requests for related videos. Seeds, search terms and scores are
     * sampled with some randomness, and anything in [avoid] (the list already on screen) sinks to
     * the bottom, so every refresh brings something new.
     */
    suspend fun recommendations(avoid: Set<String> = emptySet()): List<VideoItem> = withContext(Dispatchers.IO) {
        val history = database.streamHistoryDAO().history.firstOrError().await()
        val states = database.streamStateDAO().getAll().firstOrError().await().associateBy { it.streamUid }
        val liked = likedVideos().first()
        val subscribed = subscriptions().first()
        val hidden = hiddenChannels()
        val dismissed = notInterested()
        val watched = history.map { it.streamEntity.url }.toSet() + liked.map { it.url }

        val seeds = LinkedHashMap<String, Double>() // url -> weight
        liked.forEach { seeds[it.url] = 3.0 }
        history.sortedByDescending { it.accessDate }.take(12).forEachIndexed { rank, entry ->
            val duration = entry.streamEntity.duration.coerceAtLeast(1)
            val progress = ((states[entry.streamId]?.progressMillis ?: 0L) / 1000.0 / duration).coerceIn(0.0, 1.0)
            val weight = Math.pow(0.9, rank.toDouble()) * (0.6 + progress) * (1 + 0.2 * (entry.repeatCount - 1).coerceIn(0, 3))
            seeds.merge(entry.streamEntity.url, weight) { a, b -> a + b }
        }
        // Weighted random sample (Efraimidis-Spirakis) of the 20 strongest seeds: favourites come up most.
        val topSeeds = seeds.entries.sortedByDescending { it.value }.take(20)
            .sortedByDescending { Math.pow(Math.random(), 1.0 / it.value.coerceAtLeast(0.01)) }.take(8)

        val taste = TasteProfile.from(
            liked.map { it.title to 3.0 } + history.sortedByDescending { it.accessDate }.take(30).mapIndexed { rank, entry ->
                val duration = entry.streamEntity.duration.coerceAtLeast(1)
                val progress = ((states[entry.streamId]?.progressMillis ?: 0L) / 1000.0 / duration).coerceIn(0.0, 1.0)
                entry.streamEntity.title to Math.pow(0.95, rank.toDouble()) * (0.7 + progress)
            }
        )
        val channelWatches = history.groupingBy { it.streamEntity.uploaderUrl }.eachCount()
        val likedChannels = liked.mapNotNull { it.channelUrl }.toSet()
        val subscribedUrls = subscribed.map { it.url }.toSet()

        val scores = HashMap<String, Double>()
        val items = HashMap<String, VideoItem>()
        val limiter = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            topSeeds.map { (url, weight) ->
                async {
                    limiter.withPermit {
                        val related = runCatching {
                            ExtractorHelper.getStreamInfo(serviceId, url, false).await().relatedItems
                                .filterIsInstance<StreamInfoItem>()
                        }.getOrDefault(emptyList())
                        synchronized(scores) {
                            related.forEachIndexed { rank, item ->
                                scores.merge(item.url, weight / (1 + rank * 0.15)) { a, b -> a + b }
                                items.putIfAbsent(item.url, item.toVideo())
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        // Similar videos by taste: search for the words you favour most.
        if (!taste.isEmpty) {
            val terms = taste.topTerms(8).shuffled().take(4)
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
                                found.forEachIndexed { rank, item ->
                                    scores.merge(item.url, 0.9 / (1 + rank * 0.1)) { a, b -> a + b }
                                    items.putIfAbsent(item.url, item)
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        // Unseen uploads from followed channels are always candidates.
        feed().take(30).forEach { video ->
            scores.merge(video.url, 1.2) { a, b -> a + b }
            items.putIfAbsent(video.url, video)
        }

        val ranked = items.values
            .filter { it.url !in dismissed }
            .filter { it.url !in watched && !it.isShort && (it.durationSeconds == 0L || it.durationSeconds > 60) }
            .filter { it.channelUrl == null || it.channelUrl !in hidden }
            .map { video ->
                val channel = video.channelUrl
                val affinity = 1 + 0.3 * (channelWatches[channel] ?: 0).coerceAtMost(3) +
                    (if (channel in subscribedUrls) 0.5 else 0.0) +
                    (if (channel in likedChannels) 0.5 else 0.0)
                val jitter = 0.8 + 0.4 * Math.random()
                video to (scores[video.url] ?: 0.0) * affinity * (1 + 0.8 * taste.overlap(video.title)) * jitter *
                    (if (video.url in avoid) 0.05 else 1.0)
            }
            .sortedByDescending { it.second }
            .map { it.first }

        // Keep the list varied: at most two per channel in the first 20.
        val perChannel = HashMap<String?, Int>()
        val top = ArrayList<VideoItem>()
        val rest = ArrayList<VideoItem>()
        ranked.forEach { video ->
            val used = perChannel.getOrDefault(video.channelUrl, 0)
            if (top.size < 20 && used < 2) {
                top += video
                perChannel[video.channelUrl] = used + 1
            } else {
                rest += video
            }
        }
        (top + rest).take(40)
    }

    // endregion

    /** Videos from subscribed channels, newest first, with resume progress. [groupId] -1 means all. */
    suspend fun feed(groupId: Long = FeedGroupEntity.GROUP_ALL_ID): List<VideoItem> = withContext(Dispatchers.IO) {
        val streams = FeedDatabaseManager(context)
            .getStreams(groupId, true, true, false)
            .awaitSingleOrNull()
            .orEmpty()
        streams.map { it.stream.toVideo(it.stateProgressMillis) }
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
     * Shorts from the channels you subscribe to (their Shorts tab), newest mix first. With no
     * subscriptions it falls back to a search for #shorts, because the extractor has no global feed.
     */
    suspend fun shorts(): List<VideoItem> {
        val channels = subscriptions().first().take(8)
        // Fetch a few channels at a time instead of one after the other.
        val limiter = kotlinx.coroutines.sync.Semaphore(4)
        val fromChannels = kotlinx.coroutines.coroutineScope {
            channels.map { channel ->
                async {
                    limiter.withPermit {
                        runCatching {
                            val loaded = channel(channel.url)
                            val tab = loaded.details.tabs.firstOrNull { it.key == "shorts" }
                                ?: return@runCatching emptyList()
                            channelTab(loaded.handlers[tab.index], null).items.filterIsInstance<VideoItem>()
                                .map { it.copy(channel = it.channel.ifBlank { channel.name }, avatar = it.avatar ?: channel.avatar) }
                        }.getOrDefault(emptyList())
                    }
                }
            }.awaitAll().flatten()
        }
        if (fromChannels.size >= 6) return fromChannels.shuffled()
        val searched = runCatching { search("#shorts").items }.getOrDefault(emptyList())
            .filterIsInstance<VideoItem>().filter { it.isShort || (it.durationSeconds in 1..61) }
        return (fromChannels + searched).distinctBy { it.url }.shuffled()
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
