package dev.jordanempire.youflow.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jordanempire.youflow.ui.AppActions
import dev.jordanempire.youflow.ui.components.MessageBox
import dev.jordanempire.youflow.ui.components.StateHost
import dev.jordanempire.youflow.ui.components.VideoCard
import dev.jordanempire.youflow.ui.components.VideoTile
import dev.jordanempire.youflow.ui.data.ListCache
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.KioskRef
import dev.jordanempire.youflow.ui.model.UiState
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.util.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FEED_ID = "feed"
private const val FOR_YOU_ID = "foryou"

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)

    /** "Following" shows your subscription feed, the rest are YouTube's own lists. */
    val kiosks = listOf(KioskRef(FOR_YOU_ID, "", "For you"), KioskRef(FEED_ID, "", "Following")) + repo.kiosks

    private val _selected = MutableStateFlow(2.coerceAtMost(kiosks.lastIndex))
    val selected = _selected.asStateFlow()
    private val _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private var job: Job? = null

    val continueWatching: kotlinx.coroutines.flow.StateFlow<List<VideoItem>> = repo.continueWatching()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Start on the subscription feed when there is one, otherwise on the first YouTube list.
        viewModelScope.launch {
            // Start on "For you" once there is something to base it on, otherwise on a YouTube list.
            val hasSignals = repo.subscriptions().first().isNotEmpty() ||
                repo.continueWatching().first().isNotEmpty() || repo.likedVideos().first().isNotEmpty()
            if (hasSignals) _selected.value = 0
            load(pullToRefresh = false)
        }
    }

    /** Takes a video (or every video of a channel) out of the list on screen and out of the saved copy. */
    fun dismiss(predicate: (VideoItem) -> Boolean) {
        val current = (_state.value as? UiState.Content)?.data ?: return
        val kept = current.filterNot(predicate)
        _state.value = UiState.Content(kept)
        val kiosk = kiosks.getOrNull(_selected.value) ?: return
        viewModelScope.launch { ListCache.write(getApplication(), "home-${kiosk.id}", kept) }
    }

    fun select(index: Int) {
        if (index == _selected.value) return
        _selected.value = index
        load(pullToRefresh = false)
    }

    fun refresh() = load(pullToRefresh = true)

    private fun load(pullToRefresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val kiosk = kiosks.getOrNull(_selected.value)
            if (kiosk == null) {
                _state.value = UiState.Error("No categories are available right now.")
                return@launch
            }
            val cacheKey = "home-${kiosk.id}"
            // Paint the last result straight away, then replace it with the fresh one.
            val cached = if (pullToRefresh) null else ListCache.read(getApplication(), cacheKey)
            if (pullToRefresh) {
                _refreshing.value = true
            } else if (cached != null) {
                _state.value = UiState.Content(cached)
                _refreshing.value = true
            } else {
                _state.value = UiState.Loading
            }
            try {
                val videos = if (kiosk.id == FOR_YOU_ID) {
                    repo.recommendations(
                        if (pullToRefresh) (_state.value as? UiState.Content)?.data?.map { it.url }?.toSet().orEmpty() else emptySet()
                    )
                } else if (kiosk.id == FEED_ID) {
                    if (pullToRefresh) runCatching { repo.refreshFeed() }
                    repo.feed()
                } else {
                    repo.kioskVideosWithRetry(kiosk, forceLoad = pullToRefresh)
                }
                _state.value = UiState.Content(videos)
                if (videos.isNotEmpty()) ListCache.write(getApplication(), cacheKey, videos)
                if (kiosk.id != FEED_ID) repo.prefetchStreams(videos.take(2))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Keep showing the cached list when the refresh fails.
                if (cached == null) _state.value = e.toUiError()
            }
            _refreshing.value = false
        }
    }
}

@Composable
fun HomeScreen(actions: AppActions, contentPadding: PaddingValues, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val selected by vm.selected.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.padding(top = contentPadding.calculateTopPadding())) {
        StateHost(
            state,
            onRetry = vm::refresh,
            isEmpty = { it.isEmpty() },
            emptyContent = {
                MessageBox(
                    when (vm.kiosks.getOrNull(selected)?.id) {
                        FEED_ID -> "Nothing new yet"
                        FOR_YOU_ID -> "Nothing to recommend yet"
                        else -> "No videos"
                    },
                    when (vm.kiosks.getOrNull(selected)?.id) {
                        FEED_ID -> "Subscribe to channels, then pull down to fetch their latest uploads."
                        FOR_YOU_ID -> "Watch and like a few videos and subscribe to channels, then recommendations show up here."
                        else -> null
                    }
                )
            }
        ) { videos ->
            LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                if (continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        Column(Modifier.padding(top = 8.dp)) {
                            Text("Continue watching", style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(continueWatching, key = { it.url }) { video -> VideoTile(video, onClick = { actions.openVideo(video) }) }
                            }
                        }
                    }
                }
                item(key = "chips") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(vm.kiosks) { index, kiosk ->
                            FilterChip(selected = index == selected, onClick = { vm.select(index) }, label = { Text(kiosk.title) })
                        }
                    }
                }
                items(videos, key = { it.url }) { video ->
                    VideoCard(
                        video,
                        onClick = { actions.openVideo(video) },
                        onChannelClick = { video.channelUrl?.let(actions.openChannel) },
                        onSave = { actions.saveVideo(video) },
                        onPlayNext = { actions.playNext(video) },
                        onEnqueue = { actions.enqueue(video) },
                        onHideChannel = video.channelUrl?.let { url ->
                            {
                                actions.hideChannel(url)
                                vm.dismiss { it.channelUrl == url }
                            }
                        },
                        onNotInterested = {
                            actions.notInterested(video)
                            vm.dismiss { it.url == video.url }
                        }
                    )
                }
            }
        }
    }
}
