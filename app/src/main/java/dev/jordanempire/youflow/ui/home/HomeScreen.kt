package dev.jordanempire.youflow.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jordanempire.youflow.ui.AppActions
import dev.jordanempire.youflow.ui.components.MessageBox
import dev.jordanempire.youflow.ui.components.StateHost
import dev.jordanempire.youflow.ui.components.VideoCard
import dev.jordanempire.youflow.ui.data.CommunityPacks
import dev.jordanempire.youflow.ui.data.ListCache
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.KioskRef
import dev.jordanempire.youflow.ui.model.UiState
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.settings.AppSettings
import dev.jordanempire.youflow.ui.util.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FEED_ID = "feed"
private const val FOR_YOU_ID = "foryou"

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)
    private val settings = AppSettings.get(app)

    /**
     * "For you" and "Following" are built on the device, "HN picks" and the community topics come
     * from the lists in [CommunityPacks], the rest are YouTube's own lists. Topics switched off in
     * settings are left out.
     */
    val kiosks: StateFlow<List<KioskRef>> = settings.hiddenHomeTopics.map { chips(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, chips(settings.hiddenHomeTopics.value))

    private fun chips(hidden: Set<String>): List<KioskRef> = listOf(KioskRef(FOR_YOU_ID, "", "For you"), KioskRef(FEED_ID, "", "Following")) +
        listOf(KioskRef(CommunityPacks.HN_ID, "", "HN picks")).filter { it.id !in hidden } +
        CommunityPacks.all.filter { it.id !in hidden }.map { KioskRef(it.id, "", it.title) } +
        repo.kiosks

    private val _selectedId = MutableStateFlow(repo.kiosks.firstOrNull()?.id ?: FOR_YOU_ID)
    val selectedId = _selectedId.asStateFlow()
    private val _state = MutableStateFlow<UiState<List<VideoItem>>>(UiState.Loading)
    val state = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            // Start on "For you" once there is something to base it on, otherwise on a YouTube list.
            val hasSignals = repo.subscriptions().first().isNotEmpty() ||
                repo.continueWatching().first().isNotEmpty() || repo.likedVideos().first().isNotEmpty() ||
                repo.hasImportedHistory()
            if (hasSignals) _selectedId.value = FOR_YOU_ID
            load(pullToRefresh = false)
        }
        // A topic switched off in settings while it is on screen: go back to "For you".
        viewModelScope.launch {
            kiosks.collect { list ->
                if (list.isNotEmpty() && list.none { it.id == _selectedId.value }) select(FOR_YOU_ID)
            }
        }
    }

    /** Takes a video (or every video of a channel) out of the list on screen and out of the saved copy. */
    fun dismiss(predicate: (VideoItem) -> Boolean) {
        val current = (_state.value as? UiState.Content)?.data ?: return
        val kept = current.filterNot(predicate)
        _state.value = UiState.Content(kept)
        viewModelScope.launch { ListCache.write(getApplication(), "home-${_selectedId.value}", kept) }
    }

    fun select(id: String) {
        if (id == _selectedId.value) return
        _selectedId.value = id
        load(pullToRefresh = false)
    }

    fun refresh() = load(pullToRefresh = true)

    private fun load(pullToRefresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val id = _selectedId.value
            val kiosk = repo.kiosks.firstOrNull { it.id == id }
            val pack = CommunityPacks.all.firstOrNull { it.id == id }
            val local = id == FOR_YOU_ID || id == FEED_ID || id == CommunityPacks.HN_ID || pack != null
            if (!local && kiosk == null) {
                _state.value = UiState.Error("No categories are available right now.")
                return@launch
            }
            val cacheKey = "home-$id"
            // Paint the last result straight away, then replace it with the fresh one.
            val cached = if (pullToRefresh) null else ListCache.read(getApplication(), cacheKey)
            val onScreen = (_state.value as? UiState.Content)?.data?.map { it.url }?.toSet().orEmpty()
            if (pullToRefresh) {
                _refreshing.value = true
            } else if (cached != null) {
                _state.value = UiState.Content(cached)
                _refreshing.value = true
            } else {
                _state.value = UiState.Loading
            }
            try {
                val avoid = if (pullToRefresh) onScreen else emptySet()
                val videos = when {
                    id == FOR_YOU_ID -> repo.recommendations(avoid)

                    id == FEED_ID -> {
                        if (pullToRefresh) runCatching { repo.refreshFeed() }
                        repo.feed()
                    }

                    id == CommunityPacks.HN_ID -> repo.hnPicks(avoid)

                    pack != null -> repo.packVideos(pack, avoid)

                    else -> repo.kioskVideosWithRetry(kiosk!!, forceLoad = pullToRefresh)
                }
                _state.value = UiState.Content(videos)
                if (videos.isNotEmpty()) ListCache.write(getApplication(), cacheKey, videos)
                if (id != FEED_ID) repo.prefetchStreams(videos.take(2))
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
    val selectedId by vm.selectedId.collectAsState()
    val kiosks by vm.kiosks.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

    // The chips stay put while a topic loads; only the video list below them changes.
    Column(Modifier.padding(top = contentPadding.calculateTopPadding())) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(kiosks, key = { it.id }) { kiosk ->
                FilterChip(selected = kiosk.id == selectedId, onClick = { vm.select(kiosk.id) }, label = { Text(kiosk.title) })
            }
        }
        // A new topic starts at the top of its list.
        val listState = remember(selectedId) { LazyListState() }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
            StateHost(
                state,
                onRetry = vm::refresh,
                isEmpty = { it.isEmpty() },
                emptyContent = {
                    MessageBox(
                        when (selectedId) {
                            FEED_ID -> "Nothing new yet"
                            FOR_YOU_ID -> "Nothing to recommend yet"
                            else -> "No videos"
                        },
                        when (selectedId) {
                            FEED_ID -> "Subscribe to channels, then pull down to fetch their latest uploads."
                            FOR_YOU_ID -> "Watch, like or subscribe, or import your YouTube history in Settings."
                            CommunityPacks.HN_ID -> null
                            else -> if (CommunityPacks.all.any { it.id == selectedId }) "Nothing new from these channels right now." else null
                        }
                    )
                }
            ) { videos ->
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                    items(videos, key = { it.url }) { video ->
                        VideoCard(
                            video,
                            reason = video.reason,
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
}
