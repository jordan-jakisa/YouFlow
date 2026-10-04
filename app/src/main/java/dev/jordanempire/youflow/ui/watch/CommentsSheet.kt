package dev.jordanempire.youflow.ui.watch

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jordanempire.youflow.ui.components.LoadingBox
import dev.jordanempire.youflow.ui.components.Thumbnail
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.CommentItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

data class CommentsUi(
    val loading: Boolean = true,
    val disabled: Boolean = false,
    val error: String? = null,
    val count: Int = 0,
    val items: List<CommentItem> = emptyList(),
    val replies: Map<String, List<CommentItem>> = emptyMap()
)

class CommentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)
    private var loaded: YouTubeRepository.LoadedComments? = null
    private var next: Page? = null
    private var url: String? = null
    private var loadingMore = false
    private val replyTokens = HashMap<String, Page?>()

    private val _ui = MutableStateFlow(CommentsUi())
    val ui = _ui.asStateFlow()

    fun load(videoUrl: String) {
        if (url == videoUrl) return
        url = videoUrl
        _ui.value = CommentsUi()
        viewModelScope.launch {
            try {
                val l = repo.comments(videoUrl)
                loaded = l
                next = l.next
                _ui.value = CommentsUi(loading = false, disabled = l.disabled, count = l.count, items = l.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = CommentsUi(loading = false, error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun loadMore() {
        val page = next ?: return
        val info = loaded?.info ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val more = repo.moreComments(info, page)
                next = more.next
                _ui.value = _ui.value.copy(items = _ui.value.items + more.items)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                next = null
            }
            loadingMore = false
        }
    }

    fun toggleReplies(comment: CommentItem) {
        val videoUrl = url ?: return
        if (_ui.value.replies.containsKey(comment.id)) {
            _ui.value = _ui.value.copy(replies = _ui.value.replies - comment.id)
            return
        }
        val token = comment.repliesToken ?: return
        viewModelScope.launch {
            val page = runCatching { repo.replies(videoUrl, token) }.getOrNull() ?: return@launch
            _ui.value = _ui.value.copy(replies = _ui.value.replies + (comment.id to page.items))
        }
    }
}

/** The teaser card shown on the watch page: count plus the first comment. */
@Composable
fun CommentsTeaser(url: String, onClick: () -> Unit, vm: CommentsViewModel = viewModel()) {
    LaunchedEffect(url) { vm.load(url) }
    val ui by vm.ui.collectAsState()
    if (ui.disabled) {
        Text("Comments are turned off", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (ui.count > 0) "Comments  ${ui.count}" else "Comments",
                style = MaterialTheme.typography.titleSmallEmphasized
            )
            val first = ui.items.firstOrNull()
            when {
                ui.loading -> Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                ui.error != null -> Text("Couldn't load comments", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                first != null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Thumbnail(first.avatar, Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer))
                    Text(first.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Comments docked under the player, so the video stays visible and keeps playing above them. */
@Composable
fun CommentsPanel(onClose: () -> Unit, modifier: Modifier = Modifier, vm: CommentsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val listState = rememberLazyListState()
    BackHandler(onBack = onClose)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 4) vm.loadMore() }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ui.count > 0) "Comments  ${ui.count}" else "Comments",
                style = MaterialTheme.typography.titleMediumEmphasized,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close comments") }
        }
        when {
            ui.loading -> LoadingBox(Modifier.height(240.dp))

            ui.items.isEmpty() -> Text("No comments yet.", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = WindowInsets.navigationBars.asPaddingValues()) {
                items(ui.items, key = { it.id.ifEmpty { it.text + it.author } }) { comment ->
                    CommentRow(comment, onToggleReplies = { vm.toggleReplies(comment) }, expandedReplies = ui.replies[comment.id])
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommentItem, onToggleReplies: () -> Unit, expandedReplies: List<CommentItem>?, indent: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) 56.dp else 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Thumbnail(comment.avatar, Modifier.size(if (indent) 28.dp else 36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (comment.pinned) Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    comment.author,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (comment.isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                comment.posted?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                comment.likes?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.ThumbUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (comment.hearted) Icon(Icons.Rounded.Favorite, contentDescription = "Liked by creator", modifier = Modifier.size(14.dp), tint = androidx.compose.ui.graphics.Color(0xFFE53935))
            }
            if (comment.replyCount > 0 && comment.repliesToken != null) {
                TextButton(onClick = onToggleReplies, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(if (expandedReplies != null) "Hide replies" else "${comment.replyCount} replies")
                }
            }
            expandedReplies?.forEach { reply -> CommentRow(reply, {}, null, indent = true) }
        }
    }
}
