package dev.jordanempire.youflow.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import dev.jordanempire.youflow.media.engine.PlaybackEngine

/** Docked above the navigation bar while a video plays but the watch page is collapsed. */
@Composable
fun MiniPlayer(engine: PlaybackEngine, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by engine.state.collectAsState()
    val position by engine.position.collectAsState()
    val entry = state.entry ?: return
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable(onClick = onExpand),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column {
            Row(Modifier.height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                val audioOnly by engine.audioOnly.collectAsState()
                Box(Modifier.width(114.dp).height(64.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                    if (audioOnly) {
                        dev.jordanempire.youflow.ui.components.VideoThumbnail(
                            entry.url,
                            state.info?.thumbnails?.let { dev.jordanempire.youflow.util.image.ImageStrategy.choosePreferredImage(it) } ?: entry.thumbnail,
                            Modifier.fillMaxSize()
                        )
                    } else {
                        // The same player that plays on the watch page keeps drawing in the mini player.
                        ContentFrame(
                            player = engine.exo,
                            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.info?.name ?: entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(state.info?.uploaderName ?: entry.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = engine::togglePlayPause) {
                    Icon(if (state.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play or pause")
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
            }
            if (state.durationMs > 0 && !state.isLive) {
                LinearProgressIndicator(
                    progress = { (position.toFloat() / state.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp)
                )
            }
        }
    }
}
