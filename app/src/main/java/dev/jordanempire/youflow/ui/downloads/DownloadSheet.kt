package dev.jordanempire.youflow.ui.downloads

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import dev.jordanempire.youflow.R
import dev.jordanempire.youflow.streams.io.StoredDirectoryHelper
import dev.jordanempire.youflow.streams.io.StoredFileHelper
import dev.jordanempire.youflow.ui.components.LoadingBox
import dev.jordanempire.youflow.ui.components.MessageBox
import dev.jordanempire.youflow.ui.settings.DownloadStorage
import dev.jordanempire.youflow.util.ExtractorHelper
import dev.jordanempire.youflow.util.FilenameUtils
import dev.jordanempire.youflow.util.ListHelper
import dev.jordanempire.youflow.util.SecondaryStreamHelper
import dev.jordanempire.youflow.util.StreamItemAdapter.StreamInfoWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.rx3.await
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream
import us.shandian.giga.get.MissionRecoveryInfo
import us.shandian.giga.postprocessing.Postprocessing
import us.shandian.giga.service.DownloadManager
import us.shandian.giga.service.DownloadManagerService

private enum class Kind(val label: String) { Video("Video"), Audio("Audio"), Subtitles("Subtitles") }

/** What can be downloaded from one video, grouped the way the download engine expects. */
private class Choices(context: Context, val info: StreamInfo) {
    private fun <S : Stream> direct(list: List<S>?): List<S> = ListHelper.getStreamsOfSpecifiedDelivery(list, DeliveryMethod.PROGRESSIVE_HTTP)

    private val audioTracks = ListHelper.getGroupedAudioStreams(context, direct(info.audioStreams))
    val audio: List<AudioStream> = audioTracks.getOrNull(ListHelper.getDefaultAudioTrackGroup(context, audioTracks)).orEmpty()
    val videos: List<VideoStream> = ListHelper.getSortedStreamVideosList(
        context,
        direct(info.videoStreams),
        direct(info.videoOnlyStreams),
        false,
        audioTracks.size > 1
    )
    val subtitles: List<SubtitlesStream> = direct(info.subtitles)

    val audioSizes = StreamInfoWrapper(audio, context)
    val videoSizes = StreamInfoWrapper(videos, context)
    val subtitleSizes = StreamInfoWrapper(subtitles, context)

    /** The audio stream that gets merged into a video-only stream. */
    val secondary: Map<VideoStream, AudioStream> = videos.filter { it.isVideoOnly }.mapNotNull { video ->
        SecondaryStreamHelper.getAudioStreamFor(context, audio, video)?.let { video to it }
    }.toMap()

    val defaultVideo = ListHelper.getDefaultResolutionIndex(context, videos).coerceAtLeast(0)
    val defaultAudio = audio.indices.maxByOrNull { audio[it].averageBitrate } ?: 0

    fun kinds() = Kind.entries.filter {
        when (it) {
            Kind.Video -> videos.isNotEmpty()
            Kind.Audio -> audio.isNotEmpty()
            Kind.Subtitles -> subtitles.isNotEmpty()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(url: String, onDismiss: () -> Unit) {
    var load by remember { mutableStateOf<Result<StreamInfo>?>(null) }
    LaunchedEffect(url) {
        load = runCatching {
            withContext(Dispatchers.IO) { ExtractorHelper.getStreamInfo(ServiceList.YouTube.serviceId, url, false).await() }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val result = load
        when {
            result == null -> LoadingBox(Modifier.height(220.dp))
            result.isFailure -> MessageBox("Couldn't load this video", "Check your connection and try again.", "Close", onDismiss)
            else -> DownloadOptions(result.getOrThrow(), onDismiss)
        }
    }
}

@Composable
private fun DownloadOptions(info: StreamInfo, onDone: () -> Unit) {
    val context = LocalContext.current
    val choices = remember(info) { Choices(context, info) }
    val kinds = remember(choices) { choices.kinds() }
    if (kinds.isEmpty()) {
        MessageBox("Nothing to download", "YouTube offers no downloadable stream for this video.", "Close", onDone)
        return
    }
    var kind by remember { mutableStateOf(kinds.first()) }
    var videoIndex by remember { mutableIntStateOf(choices.defaultVideo) }
    var audioIndex by remember { mutableIntStateOf(choices.defaultAudio) }
    var subtitleIndex by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(FilenameUtils.createFilename(context, info.name)) }
    var sizesLoaded by remember { mutableIntStateOf(0) }
    var folderTick by remember { mutableIntStateOf(0) }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val askEachTime = prefs.getBoolean(context.getString(R.string.downloads_storage_ask), false)

    // Sizes come from HEAD requests, so they fill in a moment after the list appears.
    LaunchedEffect(choices) {
        for (wrapper in listOf(choices.videoSizes, choices.audioSizes, choices.subtitleSizes)) {
            runCatching { StreamInfoWrapper.fetchMoreInfoForWrapper(wrapper).await() }
            sizesLoaded++
        }
    }

    val folderKey = context.getString(if (kind == Kind.Audio) R.string.download_path_audio_key else R.string.download_path_video_key)
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            if (DownloadStorage.save(context, folderKey, uri)) folderTick++ else Toast.makeText(context, R.string.no_available_dir, Toast.LENGTH_LONG).show()
        }
    }
    var pendingSaveAs by remember { mutableStateOf<Pending?>(null) }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pending = pendingSaveAs
        pendingSaveAs = null
        if (uri != null && pending != null) {
            val storage = StoredFileHelper(context, null, uri, "")
            if (storage.existsAsFile() || storage.create()) {
                start(context, info, pending, storage)
                onDone()
            } else {
                Toast.makeText(context, R.string.error_file_creation, Toast.LENGTH_LONG).show()
            }
        }
    }

    val folderSet = remember(folderTick, kind) { DownloadStorage.describe(context, folderKey) != null }

    Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Download", style = MaterialTheme.typography.titleLarge)
        Text(info.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            kinds.forEachIndexed { index, k ->
                SegmentedButton(
                    selected = kind == k,
                    onClick = { kind = k },
                    shape = SegmentedButtonDefaults.itemShape(index, kinds.size)
                ) { Text(k.label) }
            }
        }
        // Re-read sizes after each batch of HEAD requests lands.
        val sizeTick = sizesLoaded
        LazyColumn(Modifier.height(260.dp)) {
            when (kind) {
                Kind.Video -> itemsIndexed(choices.videos) { i, stream ->
                    val extra = choices.secondary[stream]
                    val bytes = choices.videoSizes.getSizeInBytes(i).let { v -> if (extra != null && v > 0) v + choices.audioSizes.getSizeInBytes(extra).coerceAtLeast(0) else v }
                    ChoiceRow(
                        "${stream.resolution} · ${stream.format?.getName().orEmpty()}",
                        listOfNotNull(size(context, bytes, sizeTick), if (stream.isVideoOnly && extra == null) "no sound" else null).joinToString(" · "),
                        i == videoIndex
                    ) { videoIndex = i }
                }

                Kind.Audio -> itemsIndexed(choices.audio) { i, stream ->
                    ChoiceRow(
                        "${stream.averageBitrate} kbps · ${stream.format?.getName().orEmpty()}",
                        size(context, choices.audioSizes.getSizeInBytes(i), sizeTick).orEmpty(),
                        i == audioIndex
                    ) { audioIndex = i }
                }

                Kind.Subtitles -> itemsIndexed(choices.subtitles) { i, stream ->
                    ChoiceRow(
                        listOfNotNull(stream.displayLanguageName, if (stream.isAutoGenerated) "auto-generated" else null).joinToString(" · "),
                        stream.format?.getName().orEmpty(),
                        i == subtitleIndex
                    ) { subtitleIndex = i }
                }
            }
        }
        OutlinedTextField(name, { name = it }, label = { Text("File name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (!askEachTime) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Save to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DownloadStorage.describe(context, folderKey) ?: "No folder chosen yet", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { folderPicker.launch(StoredDirectoryHelper.getPicker(context)) }) { Text(if (folderSet) "Change" else "Choose") }
            }
        }
        Button(
            onClick = {
                val pending = Pending(
                    kind,
                    when (kind) {
                        Kind.Video -> choices.videos[videoIndex]
                        Kind.Audio -> choices.audio[audioIndex]
                        Kind.Subtitles -> choices.subtitles[subtitleIndex]
                    },
                    if (kind == Kind.Video) choices.secondary[choices.videos[videoIndex]] else null,
                    name,
                    if (kind == Kind.Video) {
                        choices.videoSizes.getSizeInBytes(videoIndex)
                    } else {
                        0L
                    },
                    choices.secondary[choices.videos.getOrNull(videoIndex)]?.let { choices.audioSizes.getSizeInBytes(it) } ?: 0L
                )
                if (askEachTime) {
                    pendingSaveAs = pending
                    saveAs.launch(pending.fileName(context))
                } else {
                    val dir = directory(context, kind)
                    if (dir == null) {
                        folderPicker.launch(StoredDirectoryHelper.getPicker(context))
                    } else {
                        val storage = dir.createUniqueFile(pending.fileName(context), pending.mime())
                        if (storage == null) {
                            Toast.makeText(context, R.string.error_file_creation, Toast.LENGTH_LONG).show()
                        } else {
                            start(context, info, pending, storage)
                            onDone()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) { Text("Download") }
    }
}

@Composable
private fun ChoiceRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun size(context: Context, bytes: Long, @Suppress("UNUSED_PARAMETER") tick: Int): String? = if (bytes > 0) Formatter.formatShortFileSize(context, bytes) else null

/** One choice, ready to start. */
private class Pending(
    val kind: Kind,
    val stream: Stream,
    val secondary: AudioStream?,
    val name: String,
    val videoBytes: Long,
    val secondaryBytes: Long
) {
    private val format: MediaFormat? get() = stream.format

    fun extension(): String = when {
        kind == Kind.Audio && format == MediaFormat.WEBMA_OPUS -> "opus"
        kind == Kind.Subtitles && format == MediaFormat.TTML -> MediaFormat.SRT.suffix
        else -> format?.suffix.orEmpty()
    }

    fun mime(): String = when {
        kind == Kind.Audio && format == MediaFormat.WEBMA_OPUS -> "audio/ogg"
        else -> format?.mimeType ?: StoredFileHelper.DEFAULT_MIME
    }

    fun fileName(context: Context): String = FilenameUtils.createFilename(context, name) + "." + extension()
}

private fun directory(context: Context, kind: Kind): StoredDirectoryHelper? {
    val audio = kind == Kind.Audio
    val saved = PreferenceManager.getDefaultSharedPreferences(context)
        .getString(context.getString(if (audio) R.string.download_path_audio_key else R.string.download_path_video_key), null)
        ?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        StoredDirectoryHelper(context, Uri.parse(saved), if (audio) DownloadManager.TAG_AUDIO else DownloadManager.TAG_VIDEO).takeUnless { it.isInvalidSafStorage }
    } catch (e: java.io.IOException) {
        null
    }
}

/** Hands the choice to the download service, which runs it in the background. */
private fun start(context: Context, info: StreamInfo, pending: Pending, storage: StoredFileHelper) {
    if (!storage.canWrite()) {
        Toast.makeText(context, R.string.permission_denied, Toast.LENGTH_LONG).show()
        return
    }
    runCatching { if (storage.length() > 0) storage.truncate() }
    val stream = pending.stream
    var psName: String? = null
    var psArgs: Array<String>? = null
    var nearLength = 0L
    val kindChar: Char
    var threads = 3
    when (pending.kind) {
        Kind.Audio -> {
            kindChar = 'a'
            if (stream.format == MediaFormat.M4A) {
                psName = Postprocessing.ALGORITHM_M4A_NO_DASH
            } else if (stream.format == MediaFormat.WEBMA_OPUS) {
                psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER
            }
        }

        Kind.Video -> {
            kindChar = 'v'
            if (pending.secondary != null) {
                psName = if (stream.format == MediaFormat.MPEG_4) Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER else Postprocessing.ALGORITHM_WEBM_MUXER
                if (pending.videoBytes > 0 && pending.secondaryBytes > 0) nearLength = pending.videoBytes + pending.secondaryBytes
            }
        }

        Kind.Subtitles -> {
            kindChar = 's'
            threads = 1
            if (stream.format == MediaFormat.TTML) {
                psName = Postprocessing.ALGORITHM_TTML_CONVERTER
                psArgs = arrayOf(stream.format!!.suffix, "false")
            }
        }
    }
    val streams = listOfNotNull(stream, pending.secondary)
    DownloadManagerService.startMission(
        context,
        streams.map { it.content }.toTypedArray(),
        storage,
        kindChar,
        threads,
        info,
        psName,
        psArgs,
        nearLength,
        ArrayList(streams.map { MissionRecoveryInfo(it) })
    )
    Toast.makeText(context, R.string.download_has_started, Toast.LENGTH_SHORT).show()
}
