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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.util.formatCount
import dev.jordanempire.youflow.ui.util.formatDuration

/** Compact horizontal row used inside playlists and Shorts grids. */
@Composable
fun VideoRow(video: VideoItem, onClick: () -> Unit, modifier: Modifier = Modifier, index: Int? = null) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (index != null) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp).align(Alignment.CenterVertically)
            )
        }
        Box {
            VideoThumbnail(
                video,
                Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
            val label = if (video.isLive) "LIVE" else formatDuration(video.durationSeconds)
            if (label.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = if (video.isLive) Color(0xFFE53935) else Color.Black.copy(alpha = 0.72f),
                    contentColor = Color.White
                ) { Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(video.channel.takeIf { it.isNotBlank() }, video.views?.let { formatCount(it, "views") }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
