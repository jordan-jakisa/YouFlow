package dev.jordanempire.youflow.ui

import androidx.compose.runtime.Immutable
import dev.jordanempire.youflow.ui.model.VideoItem

/** Navigation side effects the screens trigger. */
@Immutable
data class AppActions(
    val openVideo: (video: VideoItem) -> Unit,
    val openChannel: (url: String) -> Unit,
    val openPlaylist: (url: String) -> Unit,
    /** Local playlist by id. */
    val openLocalPlaylist: (id: Long) -> Unit,
    val openHistory: () -> Unit,
    val hideChannel: (channelUrl: String) -> Unit,
    val notInterested: (video: VideoItem) -> Unit,
    val openManageSubscriptions: () -> Unit,
    val download: (url: String) -> Unit,
    val openDownloads: () -> Unit,
    val playNext: (video: VideoItem) -> Unit,
    val enqueue: (video: VideoItem) -> Unit,
    /** Opens the "Save to" sheet. */
    val saveVideo: (video: VideoItem) -> Unit,
    /** Plays [videos] as a queue starting at [index]. */
    val playVideos: (videos: List<VideoItem>, index: Int) -> Unit,
    val openSettings: () -> Unit
)
