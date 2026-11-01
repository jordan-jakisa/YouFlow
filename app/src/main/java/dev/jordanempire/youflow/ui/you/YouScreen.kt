package dev.jordanempire.youflow.ui.you

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jordanempire.youflow.ui.AppActions
import dev.jordanempire.youflow.ui.components.PlaylistRow
import dev.jordanempire.youflow.ui.components.VideoTile
import dev.jordanempire.youflow.ui.data.YouTubeRepository
import dev.jordanempire.youflow.ui.model.PlaylistItem
import dev.jordanempire.youflow.ui.model.VideoItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class YouViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = YouTubeRepository(app)
    val history: StateFlow<List<VideoItem>> = repo.history().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val continueWatching: StateFlow<List<VideoItem>> = repo.continueWatching().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val playlists: StateFlow<List<PlaylistItem>> = repo.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun YouScreen(actions: AppActions, contentPadding: PaddingValues, vm: YouViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val continueWatching by vm.continueWatching.collectAsState()

    LazyColumn(contentPadding = contentPadding) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-header") { SectionHeader("Continue watching") }
            item(key = "continue") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continueWatching, key = { it.url }) { video -> VideoTile(video, onClick = { actions.openVideo(video) }) }
                }
            }
        }
        item(key = "history-header") {
            Row(Modifier.fillMaxWidth().clickable(onClick = actions.openHistory), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader("History") }
                Text("View all", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 16.dp))
            }
        }
        item(key = "history") {
            if (history.isEmpty()) {
                Text("Videos you watch will show up here.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history.take(20), key = { it.url }) { video -> VideoTile(video, onClick = { actions.openVideo(video) }) }
                }
            }
        }
        item(key = "playlists-header") { SectionHeader("Playlists") }
        if (playlists.isEmpty()) {
            item(key = "playlists-empty") {
                Text("Playlists you create will show up here.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(playlists, key = { it.url }) { playlist -> PlaylistRow(playlist, onClick = { actions.openLocalPlaylist(playlist.url.removePrefix("local:").toLong()) }) }
        }
        item(key = "more-header") { SectionHeader("More") }
        item(key = "downloads") {
            ListItem(
                headlineContent = { Text("Downloads") },
                leadingContent = { Icon(Icons.Rounded.Download, contentDescription = null) },
                modifier = Modifier.clickable(onClick = actions.openDownloads)
            )
        }
        item(key = "settings") {
            ListItem(
                headlineContent = { Text("Settings") },
                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                modifier = Modifier.clickable(onClick = actions.openSettings)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp))
}
