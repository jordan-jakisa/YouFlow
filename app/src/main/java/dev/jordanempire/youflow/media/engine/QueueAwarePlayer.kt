package dev.jordanempire.youflow.media.engine

import android.net.Uri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import dev.jordanempire.youflow.media.mediaitem.MediaItemTag

/**
 * The queue lives in [PlaybackEngine] (streams are resolved lazily), so ExoPlayer itself only ever
 * holds one item. This wrapper lets the media session notification show working next/previous.
 */
internal class QueueAwarePlayer(player: Player, private val engine: PlaybackEngine) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
        val s = engine.state.value
        if (s.hasNext) {
            builder.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            builder.removeAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        builder.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        return builder.build()
    }

    override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
    override fun hasNextMediaItem() = engine.state.value.hasNext
    override fun hasPreviousMediaItem() = true
    override fun seekToNext() {
        engine.next()
    }
    override fun seekToNextMediaItem() {
        engine.next()
    }
    override fun seekToPrevious() {
        engine.previous()
    }
    override fun seekToPreviousMediaItem() {
        engine.previous()
    }

    // The resolver only tags its items, so the session (notification, lock screen) would show no
    // title. Build the metadata from the tag.
    private fun withMetadata(item: MediaItem?): MediaItem? {
        val tag = item?.localConfiguration?.tag as? MediaItemTag ?: return item
        val metadata = MediaMetadata.Builder()
            .setTitle(tag.title)
            .setDisplayTitle(tag.title)
            .setArtist(tag.uploaderName)
            .setArtworkUri(tag.thumbnailUrl?.let(Uri::parse))
            .build()
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    override fun getCurrentMediaItem(): MediaItem? = withMetadata(super.getCurrentMediaItem())
    override fun getMediaMetadata(): MediaMetadata = withMetadata(super.getCurrentMediaItem())?.mediaMetadata ?: super.getMediaMetadata()
}
