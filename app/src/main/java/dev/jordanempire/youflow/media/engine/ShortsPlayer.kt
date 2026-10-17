package dev.jordanempire.youflow.media.engine

import android.app.Application
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dev.jordanempire.youflow.media.PlayerDataSource
import dev.jordanempire.youflow.media.resolver.VideoPlaybackResolver
import dev.jordanempire.youflow.util.ExtractorHelper
import dev.jordanempire.youflow.util.ListHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * Three session-less players in a ring, one per page near the one on screen. While you watch short
 * N, short N+1 (and N-1) are already resolved and prepared, so a swipe shows a frame at once
 * instead of waiting for the network.
 */
class ShortsPlayer(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dataSource = PlayerDataSource(app, DefaultBandwidthMeter.getSingletonInstance(app))
    private val resolver = VideoPlaybackResolver(
        app,
        dataSource,
        object : VideoPlaybackResolver.QualityResolver {
            // Shorts are watched small and full screen: pick the lowest resolution that is still sharp.
            override fun getDefaultResolutionIndex(sortedVideos: List<VideoStream>): Int = ListHelper.getResolutionIndex(app, sortedVideos, "480p").takeIf { it >= 0 }
                ?: ListHelper.getDefaultResolutionIndex(app, sortedVideos)

            override fun getOverrideResolutionIndex(sortedVideos: List<VideoStream>, playbackQuality: String) = ListHelper.getResolutionIndex(app, sortedVideos, playbackQuality)
        }
    )

    private class Slot(val exo: ExoPlayer) {
        var url: String? = null
        var job: Job? = null
    }

    private fun buildPlayer(): ExoPlayer = ExoPlayer.Builder(app)
        .setLoadControl(
            // Start playing after half a second of data; keep the buffer small, shorts are tiny.
            DefaultLoadControl.Builder().setBufferDurationsMs(2_000, 10_000, 500, 1_000).build()
        )
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { it.repeatMode = Player.REPEAT_MODE_ONE }

    private val slots = List(SLOTS) { Slot(buildPlayer()) }

    private val _loading = MutableStateFlow<Set<Int>>(emptySet())

    /** Slots that have a short assigned but no frame ready yet. */
    val loading: StateFlow<Set<Int>> = _loading.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())

    /** Shorts that could not be loaded (removed, private, blocked in this country). */
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    init {
        slots.forEachIndexed { index, slot ->
            slot.exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) _loading.value = _loading.value - index
                }
            })
        }
    }

    private fun slotOf(page: Int) = page.mod(SLOTS)

    /** The player that shows [page] (valid for the page on screen and its neighbours). */
    fun exoFor(page: Int): ExoPlayer = slots[slotOf(page)].exo

    fun isLoading(page: Int) = slotOf(page) in _loading.value

    /** [page] is now on screen: play it and make sure its neighbours are prepared. */
    fun focus(page: Int, urls: List<String>) {
        val current = slotOf(page)
        for (offset in listOf(0, 1, -1)) {
            val i = page + offset
            if (i in urls.indices) ensureLoaded(i, urls[i])
        }
        slots.forEachIndexed { index, slot -> slot.exo.playWhenReady = index == current && slot.url != null }
        // Warm the stream info of the one after next as well, it is cheap and cached for an hour.
        urls.getOrNull(page + 2)?.let { url ->
            scope.launch(Dispatchers.IO) {
                runCatching { ExtractorHelper.getStreamInfo(ServiceList.YouTube.serviceId, url, false).await() }
            }
        }
    }

    private fun ensureLoaded(page: Int, url: String) {
        val index = slotOf(page)
        val slot = slots[index]
        if (slot.url == url) return
        slot.url = url
        slot.job?.cancel()
        slot.exo.stop()
        _loading.value = _loading.value + index
        slot.job = scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    ExtractorHelper.getStreamInfo(ServiceList.YouTube.serviceId, url, false).await()
                }
                val source = resolver.resolve(info)
                if (source == null) {
                    _failed.value = _failed.value + url
                    _loading.value = _loading.value - index
                    return@launch
                }
                slot.exo.setMediaSource(source)
                slot.exo.prepare()
                // Whether it plays is decided by focus(): only the slot on screen has playWhenReady.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e("ShortsPlayer", "load failed for $url", e)
                _failed.value = _failed.value + url
                slot.url = null
                _loading.value = _loading.value - index
            }
        }
    }

    fun togglePlayPause(page: Int) {
        val exo = exoFor(page)
        exo.playWhenReady = !exo.playWhenReady
    }

    fun stop() {
        slots.forEach {
            it.job?.cancel()
            it.url = null
            it.exo.stop()
        }
        _loading.value = emptySet()
    }

    companion object {
        private const val SLOTS = 3

        @Volatile
        private var instance: ShortsPlayer? = null

        fun get(app: Application): ShortsPlayer = instance ?: synchronized(this) { instance ?: ShortsPlayer(app).also { instance = it } }
    }
}
