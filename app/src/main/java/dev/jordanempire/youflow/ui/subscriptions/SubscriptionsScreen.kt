package dev.jordanempire.youflow.ui.subscriptions

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jordanempire.youflow.ui.AppActions
import dev.jordanempire.youflow.ui.components.Avatar
import dev.jordanempire.youflow.ui.components.MessageBox
import dev.jordanempire.youflow.ui.components.VideoCard
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.ChannelItem
import dev.jordanempire.youflow.ui.model.FeedGroupItem
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.util.toUiError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val ALL_GROUPS = -1L

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)

    val channels: StateFlow<List<ChannelItem>?> = repo.subscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val groups: StateFlow<List<FeedGroupItem>> = repo.feedGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _group = MutableStateFlow(ALL_GROUPS)
    val group = _group.asStateFlow()
    private val _feed = MutableStateFlow<List<VideoItem>>(emptyList())
    val feed = _feed.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        viewModelScope.launch { runCatching { repo.fillMissingAvatars() } }
        viewModelScope.launch {
            reload()
            if (_feed.value.isEmpty()) refresh()
        }
    }

    fun selectGroup(id: Long) {
        _group.value = id
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        _feed.value = runCatching { repo.feed(_group.value) }.getOrDefault(emptyList())
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            _error.value = null
            try {
                repo.refreshFeed()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _error.value = e.toUiError().message
            }
            reload()
            _refreshing.value = false
        }
    }

    suspend fun members(groupId: Long): List<Long> = repo.groupMembers(groupId)

    fun saveGroup(id: Long?, name: String, subscriptionIds: List<Long>) {
        viewModelScope.launch {
            repo.saveGroup(id, name, subscriptionIds)
            reload()
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            repo.deleteGroup(id)
            if (_group.value == id) selectGroup(ALL_GROUPS)
        }
    }
}

@Composable
fun SubscriptionsScreen(actions: AppActions, contentPadding: PaddingValues, vm: SubscriptionsViewModel = viewModel()) {
    val channels by vm.channels.collectAsState()
    val groups by vm.groups.collectAsState()
    val group by vm.group.collectAsState()
    val feed by vm.feed.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val error by vm.error.collectAsState()
    var editing by remember { mutableStateOf<FeedGroupItem?>(null) }
    var creating by remember { mutableStateOf(false) }

    val top = Modifier.padding(top = contentPadding.calculateTopPadding())
    when {
        channels == null -> Unit

        channels!!.isEmpty() -> Box(top.fillMaxSize()) {
            MessageBox(
                title = "No subscriptions yet",
                body = "Search for a channel and tap Subscribe, or import your subscriptions from a Google Takeout or a NewPipe export with the menu.",
                modifier = Modifier
            )
            Box(Modifier.align(Alignment.TopEnd)) { SubscriptionToolsMenu(onManage = actions.openManageSubscriptions) }
        }

        else -> PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = top) {
            LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
                item(key = "tools") {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Subscriptions  ${channels!!.size}",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            modifier = Modifier.weight(1f)
                        )
                        SubscriptionToolsMenu(onManage = actions.openManageSubscriptions)
                    }
                }
                item(key = "strip") { ChannelStrip(channels!!, actions) }
                item(key = "groups") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item { FilterChip(selected = group == ALL_GROUPS, onClick = { vm.selectGroup(ALL_GROUPS) }, label = { Text("All") }) }
                        items(groups, key = { it.id }) { g ->
                            FilterChip(selected = group == g.id, onClick = { vm.selectGroup(g.id) }, label = { Text(g.name) })
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { creating = true },
                                label = { Text("New group") },
                                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) }
                            )
                        }
                        groups.firstOrNull { it.id == group }?.let { selected ->
                            item {
                                IconButton(onClick = { editing = selected }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit group") }
                            }
                        }
                    }
                }
                if (refreshing) {
                    item(key = "progress") {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                }
                if (error != null) {
                    item(key = "error") {
                        Text(
                            "Some channels failed to update: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                if (feed.isEmpty() && !refreshing) {
                    item(key = "empty") {
                        Text(
                            "No new videos yet. Pull down to refresh.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(feed, key = { it.url }) { video ->
                    VideoCard(
                        video,
                        onClick = { actions.openVideo(video) },
                        onChannelClick = { video.channelUrl?.let(actions.openChannel) },
                        onSave = { actions.saveVideo(video) },
                        onPlayNext = { actions.playNext(video) },
                        onEnqueue = { actions.enqueue(video) }
                    )
                }
            }
        }
    }

    if (creating) {
        FeedGroupSheet(
            title = "New group",
            initialName = "",
            channels = channels.orEmpty(),
            loadMembers = { emptyList() },
            onDismiss = { creating = false },
            onSave = { name, ids -> vm.saveGroup(null, name, ids) },
            onDelete = null
        )
    }
    editing?.let { g ->
        FeedGroupSheet(
            title = "Edit group",
            initialName = g.name,
            channels = channels.orEmpty(),
            loadMembers = { vm.members(g.id) },
            onDismiss = { editing = null },
            onSave = { name, ids -> vm.saveGroup(g.id, name, ids) },
            onDelete = { vm.deleteGroup(g.id) }
        )
    }
}

@Composable
private fun ChannelStrip(channels: List<ChannelItem>, actions: AppActions) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(channels, key = { it.url }) { channel ->
            Column(
                Modifier.width(72.dp).clip(CircleShape.let { androidx.compose.foundation.shape.RoundedCornerShape(12.dp) }).clickable { actions.openChannel(channel.url) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(channel.avatar, channel.name, 64.dp)
                Text(
                    channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
