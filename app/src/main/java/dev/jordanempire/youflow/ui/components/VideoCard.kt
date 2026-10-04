package dev.jordanempire.youflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.util.formatCount
import dev.jordanempire.youflow.ui.util.formatDuration

private fun VideoItem.meta(): String = listOfNotNull(
    channel.takeIf { it.isNotBlank() },
    views?.let { formatCount(it, "views") },
    uploaded
).joinToString(" · ")

/** Full-width YouTube-style card: big 16:9 thumbnail, avatar, two-line title, meta line. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, a three dot menu offers Save, Play next and Add to queue. */
    onSave: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onHideChannel: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null
) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        Box(Modifier.padding(horizontal = 12.dp)) {
            Thumbnail(
                video.thumbnail,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentDescription = video.title
            )
            DurationBadge(video, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            if (video.progress > 0f) {
                LinearProgressIndicator(
                    progress = { video.progress },
                    color = Color(0xFFE53935),
                    trackColor = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp).clip(RoundedCornerShape(50))
                )
            }
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(video.avatar, video.channel, 40.dp, Modifier.clickable(onClick = onChannelClick))
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    video.meta(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onSave != null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save to playlist") }, onClick = {
                            menu = false
                            onSave()
                        })
                        if (onPlayNext != null) {
                            DropdownMenuItem(text = { Text("Play next") }, onClick = {
                                menu = false
                                onPlayNext()
                            })
                        }
                        if (onEnqueue != null) {
                            DropdownMenuItem(text = { Text("Add to queue") }, onClick = {
                                menu = false
                                onEnqueue()
                            })
                        }
                        if (onNotInterested != null) {
                            DropdownMenuItem(text = { Text("Not interested") }, onClick = {
                                menu = false
                                onNotInterested()
                            })
                        }
                        if (onHideChannel != null) {
                            DropdownMenuItem(text = { Text("Don't recommend channel") }, onClick = {
                                menu = false
                                onHideChannel()
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DurationBadge(video: VideoItem, modifier: Modifier) {
    val label = if (video.isLive) "LIVE" else formatDuration(video.durationSeconds)
    if (label.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (video.isLive) Color(0xFFE53935) else Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Compact card for horizontal shelves (history, continue watching). */
@Composable
fun VideoTile(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.width(168.dp).clickable(onClick = onClick)) {
        Box {
            Thumbnail(
                video.thumbnail,
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            DurationBadge(video, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            if (video.progress > 0f) {
                LinearProgressIndicator(
                    progress = { video.progress },
                    color = Color(0xFFE53935),
                    trackColor = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp).clip(RoundedCornerShape(50))
                )
            }
        }
        Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text(video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
