package dev.jordanempire.youflow.ui

import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.media.engine.PlaybackEngine
import dev.jordanempire.youflow.media.engine.QueueEntry
import dev.jordanempire.youflow.ui.channel.ChannelScreen
import dev.jordanempire.youflow.ui.downloads.DownloadSheet
import dev.jordanempire.youflow.ui.downloads.DownloadsScreen
import dev.jordanempire.youflow.ui.home.HomeScreen
import dev.jordanempire.youflow.ui.library.HistoryScreen
import dev.jordanempire.youflow.ui.library.LocalPlaylistScreen
import dev.jordanempire.youflow.ui.library.SaveToPlaylistSheet
import dev.jordanempire.youflow.ui.model.VideoItem
import dev.jordanempire.youflow.ui.playlist.PlaylistScreen
import dev.jordanempire.youflow.ui.search.SearchScreen
import dev.jordanempire.youflow.ui.settings.ContentSettingsScreen
import dev.jordanempire.youflow.ui.settings.DownloadSettingsScreen
import dev.jordanempire.youflow.ui.settings.NavBarScreen
import dev.jordanempire.youflow.ui.settings.NotificationsSettingsScreen
import dev.jordanempire.youflow.ui.settings.SettingsScreen
import dev.jordanempire.youflow.ui.shorts.ShortsScreen
import dev.jordanempire.youflow.ui.subscriptions.ManageSubscriptionsScreen
import dev.jordanempire.youflow.ui.subscriptions.SubscriptionsScreen
import dev.jordanempire.youflow.ui.watch.MiniPlayer
import dev.jordanempire.youflow.ui.watch.WatchScreen
import dev.jordanempire.youflow.ui.you.YouScreen
import kotlinx.coroutines.launch

/** A request that came from outside the app: a shared link or text to search for. */
sealed interface IncomingLink {
    data class Stream(val url: String) : IncomingLink
    data class Channel(val url: String) : IncomingLink
    data class Playlist(val url: String) : IncomingLink
    data class Search(val text: String) : IncomingLink

    data object Downloads : IncomingLink
}

/** Classifies shared text: a YouTube link opens the matching screen, anything else becomes a search. */
fun parseIncoming(text: String): IncomingLink {
    val url = Regex("https?://\\S+").find(text)?.value ?: text.takeIf { it.startsWith("vnd.youtube") }
        ?: return IncomingLink.Search(text)
    val youtube = org.schabi.newpipe.extractor.ServiceList.YouTube
    return when (runCatching { youtube.getLinkTypeByUrl(url) }.getOrNull()) {
        org.schabi.newpipe.extractor.StreamingService.LinkType.STREAM -> IncomingLink.Stream(url)
        org.schabi.newpipe.extractor.StreamingService.LinkType.CHANNEL -> IncomingLink.Channel(url)
        org.schabi.newpipe.extractor.StreamingService.LinkType.PLAYLIST -> IncomingLink.Playlist(url)
        else -> IncomingLink.Search(text)
    }
}

/** Window level state the activity needs to react to (PiP, fullscreen). */
class WatchWindowState {
    var incoming by mutableStateOf<IncomingLink?>(null)
    var expanded by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var inPip by mutableStateOf(false)
}

private fun VideoItem.toEntry() = QueueEntry(url, title, channel, thumbnail)

@OptIn(UnstableApi::class)
@Composable
fun YouFlowApp(window: WatchWindowState) {
    val context = LocalContext.current
    val engine = remember { PlaybackEngine.get(context.applicationContext as Application) }
    val playerState by engine.state.collectAsState()
    val repo = remember { dev.jordanempire.youflow.ui.data.YouTubeRepository(context.applicationContext) }

    val appSettings = remember { dev.jordanempire.youflow.ui.settings.AppSettings.get(context.applicationContext as Application) }
    val navOrder by appSettings.navOrder.collectAsState()
    val showShorts by appSettings.showShorts.collectAsState()
    val tabs = navOrder.mapNotNull { key -> Tab.entries.firstOrNull { it.name == key } }.filter { showShorts || it != Tab.Shorts }
    var tab by rememberSaveable { mutableStateOf(tabs.first()) }
    // The current tab can disappear from the bar (Shorts switched off in settings).
    if (tab !in tabs) tab = tabs.first()
    var searching by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf<VideoItem?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var searchText by rememberSaveable { mutableStateOf<String?>(null) }
    // Pushed screens on top of the tabs: "c|<channel url>" or "p|<playlist url>".
    var stack by rememberSaveable { mutableStateOf(ArrayList<String>()) }
    fun push(route: String) {
        stack = ArrayList(stack + route)
    }
    fun pop() {
        stack = ArrayList(stack.dropLast(1))
    }

    val actions = AppActions(
        openVideo = { video ->
            engine.play(listOf(video.toEntry()))
            window.expanded = true
        },
        openChannel = {
            push("c|$it")
            searching = false
            window.expanded = false
        },
        openPlaylist = {
            push("p|$it")
            searching = false
            window.expanded = false
        },
        openLocalPlaylist = {
            push("l|$it")
            searching = false
            window.expanded = false
        },
        openManageSubscriptions = {
            push("m|")
            window.expanded = false
        },
        hideChannel = { url ->
            repo.hideChannel(url)
            android.widget.Toast.makeText(context, "You'll see less from this channel", android.widget.Toast.LENGTH_SHORT).show()
        },
        notInterested = { video ->
            repo.markNotInterested(video.url)
            android.widget.Toast.makeText(context, "Got it, you won't see this again", android.widget.Toast.LENGTH_SHORT).show()
        },
        openHistory = {
            push("h|")
            window.expanded = false
        },
        download = { url -> downloadUrl = url },
        openDownloads = {
            push("d|")
            window.expanded = false
        },
        playNext = {
            engine.playNext(it.toEntry())
            android.widget.Toast.makeText(context, "Playing next", android.widget.Toast.LENGTH_SHORT).show()
        },
        enqueue = {
            engine.enqueue(it.toEntry())
            android.widget.Toast.makeText(context, "Added to queue", android.widget.Toast.LENGTH_SHORT).show()
        },
        saveVideo = { saving = it },
        playVideos = { videos, index ->
            engine.play(videos.map { it.toEntry() }, index)
            window.expanded = true
        },
        openSettings = {
            push("s|")
            window.expanded = false
        }
    )

    androidx.compose.runtime.LaunchedEffect(window.incoming) {
        when (val link = window.incoming) {
            is IncomingLink.Stream -> actions.openVideo(VideoItem(link.url, "", "", null, null, null, 0, null, null, false, false))

            is IncomingLink.Channel -> actions.openChannel(link.url)

            is IncomingLink.Playlist -> actions.openPlaylist(link.url)

            IncomingLink.Downloads -> actions.openDownloads()

            is IncomingLink.Search -> {
                searchText = link.text
                searching = true
            }

            null -> Unit
        }
        window.incoming = null
    }

    // Picture in picture shows only the video.
    if (window.inPip) {
        ContentFrame(
            player = engine.exo,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.fillMaxSize().background(Color.Black)
        )
        return
    }

    val hasVideo = playerState.entry != null
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (tab != Tab.Shorts) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_youflow_mark),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                "YouFlow",
                                style = MaterialTheme.typography.headlineSmallEmphasized,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        },
        bottomBar = {
            Column {
                if (hasVideo && !window.expanded) {
                    MiniPlayer(engine, onExpand = { window.expanded = true }, onClose = { engine.stop() })
                }
                ShortNavigationBar {
                    tabs.forEach { entry ->
                        ShortNavigationBarItem(
                            selected = tab == entry,
                            onClick = {
                                tab = entry
                                stack = ArrayList()
                            },
                            icon = { Icon(if (tab == entry) entry.selected else entry.unselected, contentDescription = null) },
                            label = { Text(entry.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (tab) {
            Tab.Home -> HomeScreen(actions, padding)
            Tab.Shorts -> ShortsScreen(actions, padding)
            Tab.Subscriptions -> SubscriptionsScreen(actions, padding)
            Tab.You -> YouScreen(actions, padding)
        }
    }

    val top = stack.lastOrNull()
    AnimatedVisibility(
        visible = top != null,
        enter = fadeIn() + slideInVertically { it / 12 },
        exit = fadeOut() + slideOutVertically { it / 12 }
    ) {
        val route = remember(top) { top } ?: return@AnimatedVisibility
        BackHandler(onBack = ::pop)
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Surface(Modifier.weight(1f)) {
                val kind = route.substringBefore('|')
                val target = route.substringAfter('|')
                when (kind) {
                    "c" -> ChannelScreen(target, actions, ::pop)
                    "l" -> LocalPlaylistScreen(target.toLong(), actions, ::pop)
                    "h" -> HistoryScreen(actions, ::pop)
                    "d" -> DownloadsScreen(::pop)
                    "m" -> ManageSubscriptionsScreen(actions, ::pop)
                    "s" -> SettingsScreen(onBack = ::pop, onOpenPage = ::push)
                    "n" -> NavBarScreen(onBack = ::pop)
                    "sd" -> DownloadSettingsScreen(onBack = ::pop)
                    "sc" -> ContentSettingsScreen(onBack = ::pop)
                    "sn" -> NotificationsSettingsScreen(onBack = ::pop)
                    else -> PlaylistScreen(target, actions, ::pop)
                }
            }
            if (hasVideo) MiniPlayer(engine, onExpand = { window.expanded = true }, onClose = { engine.stop() })
        }
    }

    saving?.let { SaveToPlaylistSheet(it, onDismiss = { saving = null }) }
    downloadUrl?.let { DownloadSheet(it, onDismiss = { downloadUrl = null }) }

    AnimatedVisibility(
        visible = searching,
        enter = fadeIn() + slideInVertically { it / 12 },
        exit = fadeOut() + slideOutVertically { it / 12 }
    ) {
        SearchScreen(actions = actions, initialQuery = searchText, onClose = {
            searching = false
            searchText = null
        })
    }

    // The watch page follows the finger while it is pulled down, then either settles back or slides away.
    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullScope = androidx.compose.runtime.rememberCoroutineScope()
    val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    LaunchedEffect(window.expanded) {
        if (window.expanded) pull.snapTo(0f)
    }
    AnimatedVisibility(
        visible = window.expanded && hasVideo,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        BackHandler {
            if (window.fullscreen) window.fullscreen = false else window.expanded = false
        }
        Box(
            Modifier.graphicsLayer {
                val progress = (pull.value / screenHeightPx).coerceIn(0f, 1f)
                translationY = pull.value
                scaleX = 1f - 0.1f * progress
                scaleY = 1f - 0.1f * progress
            }
        ) {
            WatchScreen(
                actions = actions,
                fullscreen = window.fullscreen,
                onToggleFullscreen = { window.fullscreen = !window.fullscreen },
                onCollapse = {
                    window.fullscreen = false
                    window.expanded = false
                },
                onPull = { dy -> pullScope.launch { pull.snapTo((pull.value + dy).coerceAtLeast(0f)) } },
                onPullEnd = {
                    pullScope.launch {
                        if (pull.value > screenHeightPx * 0.18f) {
                            pull.animateTo(screenHeightPx, androidx.compose.animation.core.tween(180))
                            window.fullscreen = false
                            window.expanded = false
                        } else {
                            pull.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    }
                }
            )
        }
    }
}
