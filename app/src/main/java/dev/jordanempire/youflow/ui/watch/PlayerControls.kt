package dev.jordanempire.youflow.ui.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.jordanempire.youflow.media.engine.Phase
import dev.jordanempire.youflow.media.engine.PlaybackEngine
import dev.jordanempire.youflow.media.engine.PlayerState
import dev.jordanempire.youflow.ui.util.formatDuration
import kotlinx.coroutines.delay

private val Scrim = Color.Black.copy(alpha = 0.45f)

/**
 * Tap to show/hide, double tap left/right third to seek 10s. Everything sits on a dark scrim so it
 * reads on any video.
 */
@Composable
fun PlayerControls(
    engine: PlaybackEngine,
    state: PlayerState,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onCollapse: () -> Unit,
    onPull: (Float) -> Unit = {},
    onPullEnd: () -> Unit = {},
    onSettings: () -> Unit,
    onChapters: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableLongStateOf(0L) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Hide 3s after the last interaction while playing.
    LaunchedEffect(visible, state.isPlaying, interaction) {
        if (visible && state.isPlaying) {
            delay(3_000)
            visible = false
        }
    }
    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(600)
            seekFlash = null
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(fullscreen) {
                if (fullscreen) return@pointerInput
                // Dragging down moves the whole page with the finger; releasing decides in the app shell.
                detectVerticalDragGestures(
                    onDragEnd = onPullEnd,
                    onDragCancel = onPullEnd,
                    onVerticalDrag = { change, dy ->
                        change.consume()
                        onPull(dy)
                    }
                )
            }
            .pointerInput(fullscreen) {
                if (!fullscreen) return@pointerInput
                // Swipe the left half for brightness, the right half for volume.
                var startX = 0f
                var brightnessStart = 0f
                var volumeStart = 0f
                var accumulated = 0f
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        accumulated = 0f
                        val window = (context as? android.app.Activity)?.window
                        val current = window?.attributes?.screenBrightness ?: -1f
                        brightnessStart = if (current < 0) 0.5f else current
                        val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                        volumeStart = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() /
                            audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    },
                    onDragEnd = { gestureLabel = null },
                    onDragCancel = { gestureLabel = null },
                    onVerticalDrag = { _, dy ->
                        accumulated += -dy / size.height
                        if (startX < size.width / 2f) {
                            val value = (brightnessStart + accumulated).coerceIn(0.02f, 1f)
                            (context as? android.app.Activity)?.window?.let { w ->
                                w.attributes = w.attributes.also { it.screenBrightness = value }
                            }
                            gestureLabel = "Brightness ${(value * 100).toInt()}%"
                        } else {
                            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                            val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val value = (volumeStart + accumulated).coerceIn(0f, 1f)
                            audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (value * max).toInt(), 0)
                            gestureLabel = "Volume ${(value * 100).toInt()}%"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        visible = !visible
                        interaction++
                    },
                    onDoubleTap = { offset ->
                        when {
                            offset.x < size.width / 3f -> {
                                engine.seekBy(-10_000)
                                seekFlash = "-10s"
                            }

                            offset.x > size.width * 2f / 3f -> {
                                engine.seekBy(10_000)
                                seekFlash = "+10s"
                            }

                            else -> engine.togglePlayPause()
                        }
                        interaction++
                    }
                )
            }
    ) {
        // The inline player is only ~200dp tall, so everything shrinks to avoid overlapping.
        val compact = maxHeight < 300.dp
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Scrim, Color.Transparent, Color.Transparent, Scrim)))) {
                // Top bar
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Box(Modifier.weight(1f))
                    IconButton(onClick = onSettings, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Player settings", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                // Transport
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleButton(Icons.Rounded.SkipPrevious, "Previous", enabled = true, size = if (compact) 38.dp else 48.dp) {
                        engine.previous()
                        interaction++
                    }
                    PlayPauseButton(state, size = if (compact) 52.dp else 68.dp) {
                        engine.togglePlayPause()
                        interaction++
                    }
                    CircleButton(Icons.Rounded.SkipNext, "Next", enabled = state.hasNext, size = if (compact) 38.dp else 48.dp) {
                        engine.next()
                        interaction++
                    }
                }
                // Bottom: seek bar, time, fullscreen
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp)) {
                    SeekBar(engine, state, onInteraction = { interaction++ })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TimeLabel(engine, state, onChapters, Modifier.weight(1f))
                        IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // With the controls hidden, a hairline along the bottom edge still shows how far along it is.
        if (!state.isLive && state.durationMs > 0) {
            AnimatedVisibility(!visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                val position by engine.position.collectAsState()
                val buffered by engine.buffered.collectAsState()
                Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.2f))) {
                    Box(Modifier.fillMaxWidth((buffered.toFloat() / state.durationMs).coerceIn(0f, 1f)).height(3.dp).background(Color.White.copy(alpha = 0.35f)))
                    Box(Modifier.fillMaxWidth((position.toFloat() / state.durationMs).coerceIn(0f, 1f)).height(3.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        if (state.phase == Phase.Loading || state.phase == Phase.Buffering) {
            LoadingIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
        }
        gestureLabel?.let {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ) { Text(it, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
        seekFlash?.let {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ) { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) }
        }
        state.error?.takeIf { state.phase == Phase.Error }?.let { message ->
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Can't play this video", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(message, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                val context = androidx.compose.ui.platform.LocalContext.current
                val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { engine.skipTo(state.index) }
                if (state.recaptchaUrl != null) {
                    androidx.compose.material3.Button(onClick = {
                        launcher.launch(
                            android.content.Intent(context, dev.jordanempire.youflow.error.ReCaptchaActivity::class.java)
                                .putExtra(dev.jordanempire.youflow.error.ReCaptchaActivity.RECAPTCHA_URL_EXTRA, state.recaptchaUrl)
                        )
                    }) { Text("Verify") }
                } else {
                    IconButton(onClick = { engine.skipTo(state.index) }) { Icon(Icons.Rounded.Replay, "Retry", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "alpha")
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.35f * alpha),
        contentColor = Color.White.copy(alpha = alpha),
        modifier = Modifier.size(size)
    ) { Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(size * 0.55f)) } }
}

/** The play button squares off while playing and rounds again when paused. */
@Composable
private fun PlayPauseButton(state: PlayerState, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val corner by animateFloatAsState(if (state.playWhenReady) 28f else 50f, label = "corner")
    val container by animateColorAsState(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f), label = "container")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = corner.toInt()),
        color = container,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                when {
                    state.phase == Phase.Ended -> Icons.Rounded.Replay
                    state.playWhenReady -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                contentDescription = if (state.playWhenReady) "Pause" else "Play",
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

@Composable
private fun SeekBar(engine: PlaybackEngine, state: PlayerState, onInteraction: () -> Unit) {
    val position by engine.position.collectAsState()
    val buffered by engine.buffered.collectAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    if (state.isLive) return
    val progress = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val bufferedFraction = (buffered.toFloat() / duration).coerceIn(0f, 1f)
    val active = MaterialTheme.colorScheme.primary
    val trackHeight by animateFloatAsState(if (dragging != null) 8f else 4f, label = "track")
    var widthPx by remember { mutableFloatStateOf(1f) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    engine.seekTo((offset.x / size.width * duration).toLong())
                    onInteraction()
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = (it.x / size.width).coerceIn(0f, 1f)
                        onInteraction()
                    },
                    onHorizontalDrag = { change, _ ->
                        dragging = (change.position.x / size.width).coerceIn(0f, 1f)
                        onInteraction()
                    },
                    onDragEnd = {
                        dragging?.let { engine.seekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    onDragCancel = { dragging = null }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val h = trackHeight.dp.toPx()
            val y = size.height / 2
            val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
            val top = androidx.compose.ui.geometry.Offset(0f, y - h / 2)
            drawRoundRect(Color.White.copy(alpha = 0.3f), top, androidx.compose.ui.geometry.Size(size.width, h), r)
            drawRoundRect(Color.White.copy(alpha = 0.45f), top, androidx.compose.ui.geometry.Size(size.width * bufferedFraction, h), r)
            drawRoundRect(active, top, androidx.compose.ui.geometry.Size(size.width * progress, h), r)
            // Small gaps mark where chapters start.
            state.info?.streamSegments?.forEach { segment ->
                val f = (segment.startTimeSeconds * 1000f / duration)
                if (f > 0.01f && f < 0.99f) {
                    drawRect(Color.Black.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(size.width * f - 1.5.dp.toPx(), y - h / 2), androidx.compose.ui.geometry.Size(3.dp.toPx(), h))
                }
            }
            drawCircle(active, radius = (if (dragging != null) 9f else 6f).dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * progress, y))
        }
    }
}

@Composable
private fun TimeLabel(engine: PlaybackEngine, state: PlayerState, onChapters: () -> Unit, modifier: Modifier) {
    val position by engine.position.collectAsState()
    val segments = state.info?.streamSegments.orEmpty()
    val chapter = segments.getOrNull(currentChapter(segments, position))
    val time = if (state.isLive) "LIVE" else "${formatDuration(position / 1000).ifEmpty { "0:00" }} / ${formatDuration(state.durationMs / 1000).ifEmpty { "0:00" }}"
    Row(modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Color.White, style = MaterialTheme.typography.labelLarge)
        if (chapter != null) {
            Text(
                "  \u2022  ${chapter.title.orEmpty()}",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).pointerInput(Unit) { detectTapGestures { onChapters() } }
            )
        }
    }
}
