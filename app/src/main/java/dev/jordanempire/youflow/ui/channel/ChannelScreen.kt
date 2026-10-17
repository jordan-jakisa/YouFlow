package dev.jordanempire.youflow.ui.channel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.jordanempire.youflow.ui.AppActions
import dev.jordanempire.youflow.ui.components.ChannelRow
import dev.jordanempire.youflow.ui.components.ErrorBox
import dev.jordanempire.youflow.ui.components.LoadingBox
import dev.jordanempire.youflow.ui.components.MessageBox
import dev.jordanempire.youflow.ui.components.PlaylistRow
import dev.jordanempire.youflow.ui.components.Thumbnail
import dev.jordanempire.youflow.ui.components.VideoCard
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.ChannelDetails
import dev.jordanempire.youflow.ui.model.ChannelItem
import dev.jordanempire.youflow.ui.model.ContentItem
import dev.jordanempire.youflow.ui.model.PlaylistItem
import dev.jordanempire.youflow.ui.model.UiState
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.util.formatCount
import dev.jordanempire.youflow.ui.util.keyedViewModel
import dev.jordanempire.youflow.ui.util.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class ChannelViewModel(app: Application, private val url: String) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)
    private var loaded: YouTubeRepository.LoadedChannel? = null

    private val _channel = MutableStateFlow<UiState<ChannelDetails>>(UiState.Loading)
    val channel = _channel.asStateFlow()
    private val _tab = MutableStateFlow(0)
    val tab = _tab.asStateFlow()
    private val _items = MutableStateFlow<UiState<List<ContentItem>>>(UiState.Loading)
    val items = _items.asStateFlow()
    private var loadingMore = false
    private var next: Page? = null

    val subscribed: StateFlow<Boolean> = repo.isSubscribed(url)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val notifying: StateFlow<Boolean> = repo.isNotifying(url)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleNotifications() {
        viewModelScope.launch { repo.setNotifications(url, !notifying.value) }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _channel.value = UiState.Loading
            try {
                val l = repo.channel(url)
                loaded = l
                _channel.value = UiState.Content(l.details)
                if (l.details.tabs.isNotEmpty()) loadTab(0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _channel.value = e.toUiError()
            }
        }
    }

    fun selectTab(index: Int) {
        if (index == _tab.value) return
        loadTab(index)
    }

    private fun loadTab(index: Int) {
        val handler = loaded?.handlers?.getOrNull(index) ?: return
        _tab.value = index
        next = null
        viewModelScope.launch {
            _items.value = UiState.Loading
            _items.value = try {
                val page = repo.channelTab(handler, null)
                next = page.next
                UiState.Content(withAvatar(page.items))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                e.toUiError()
            }
        }
    }

    fun loadMore() {
        val page = next ?: return
        val handler = loaded?.handlers?.getOrNull(_tab.value) ?: return
        val current = (_items.value as? UiState.Content)?.data ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val more = repo.channelTab(handler, page)
                next = more.next
                _items.value = UiState.Content(current + withAvatar(more.items))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                next = null
            }
            loadingMore = false
        }
    }

    // Videos on a channel tab carry no uploader picture; it is the channel's own.
    private fun withAvatar(items: List<ContentItem>): List<ContentItem> {
        val avatar = loaded?.details?.avatar ?: return items
        return items.map { if (it is VideoItem && it.avatar == null) it.copy(avatar = avatar) else it }
    }

    fun toggleSubscribe(details: ChannelDetails) {
        viewModelScope.launch {
            if (subscribed.value) {
                repo.unsubscribe(details.url)
            } else {
                repo.subscribe(ChannelItem(details.url, details.name, details.avatar, details.subscribers, details.description))
            }
        }
    }
}

@Composable
fun ChannelScreen(url: String, actions: AppActions, onBack: () -> Unit) {
    val vm = keyedViewModel("channel:$url") { app -> ChannelViewModel(app, url) }
    val channel by vm.channel.collectAsState()
    val tab by vm.tab.collectAsState()
    val items by vm.items.collectAsState()
    val subscribed by vm.subscribed.collectAsState()
    val notifying by vm.notifying.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(listState, items) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) vm.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        when (val c = channel) {
            UiState.Loading -> LoadingBox(Modifier.padding(padding))

            is UiState.Error -> ErrorBox(c, vm::load, Modifier.padding(padding), "Couldn't load this channel")

            is UiState.Content -> {
                val details = c.data
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                    item(key = "header") {
                        ChannelHeader(
                            details = details,
                            subscribed = subscribed,
                            notifying = notifying,
                            onToggleNotifications = vm::toggleNotifications,
                            onToggleSubscribe = { vm.toggleSubscribe(details) },
                            onPlayAll = { (items as? UiState.Content)?.data?.filterIsInstance<VideoItem>()?.let { if (it.isNotEmpty()) actions.playVideos(it, 0) } },
                            statusBarTop = padding.calculateTopPadding()
                        )
                    }
                    if (details.tabs.isNotEmpty()) {
                        item(key = "tabs") {
                            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                                details.tabs.forEachIndexed { index, t ->
                                    Tab(selected = tab == index, onClick = { vm.selectTab(index) }, text = { Text(t.label) })
                                }
                            }
                        }
                    }
                    when (val list = items) {
                        UiState.Loading -> item(key = "loading") { LoadingBox(Modifier.fillMaxWidth().height(240.dp)) }

                        is UiState.Error -> item(key = "error") {
                            MessageBox("Couldn't load this tab", list.message, modifier = Modifier.fillMaxWidth().height(280.dp))
                        }

                        is UiState.Content -> {
                            if (list.data.isEmpty()) {
                                item(key = "empty") {
                                    Text("Nothing here yet.", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(list.data, key = { it.url }) { item ->
                                when (item) {
                                    is VideoItem -> VideoCard(
                                        item,
                                        onClick = { actions.openVideo(item) },
                                        onChannelClick = {},
                                        onSave = { actions.saveVideo(item) },
                                        onPlayNext = { actions.playNext(item) },
                                        onEnqueue = { actions.enqueue(item) },
                                        modifier = Modifier.padding(top = 12.dp)
                                    )

                                    is PlaylistItem -> PlaylistRow(item, onClick = { actions.openPlaylist(item.url) })

                                    is ChannelItem -> ChannelRow(item, false, { actions.openChannel(item.url) }, {})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    details: ChannelDetails,
    subscribed: Boolean,
    notifying: Boolean,
    onToggleNotifications: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onPlayAll: () -> Unit,
    statusBarTop: androidx.compose.ui.unit.Dp
) {
    Column {
        Box {
            if (details.banner != null) {
                Thumbnail(
                    details.banner,
                    Modifier.fillMaxWidth().aspectRatio(16f / 6f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            } else {
                Spacer(Modifier.fillMaxWidth().height(statusBarTop + 56.dp + 40.dp).background(MaterialTheme.colorScheme.primaryContainer))
            }
            Thumbnail(
                details.avatar,
                Modifier.align(Alignment.BottomStart).padding(start = 16.dp).offset(y = 36.dp).size(88.dp)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            )
        }
        Spacer(Modifier.height(44.dp))
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(details.name, style = MaterialTheme.typography.headlineMediumEmphasized)
            details.subscribers?.let {
                Text(formatCount(it, "subscribers"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!details.description.isNullOrBlank()) {
                Text(
                    details.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (subscribed) {
                    OutlinedButton(onClick = onToggleSubscribe) { Text("Subscribed") }
                } else {
                    Button(onClick = onToggleSubscribe) { Text("Subscribe") }
                }
                if (subscribed) {
                    androidx.compose.material3.FilledIconToggleButton(checked = notifying, onCheckedChange = { onToggleNotifications() }) {
                        Icon(
                            if (notifying) Icons.Rounded.Notifications else Icons.Rounded.NotificationsNone,
                            contentDescription = if (notifying) "Turn off notifications" else "Notify me about new videos"
                        )
                    }
                }
                FilledTonalButton(onClick = onPlayAll) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Play all", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
