package dev.jordanempire.youflow.ui.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface ContentItem {
    val url: String
}

@Immutable
@kotlinx.serialization.Serializable
data class VideoItem(
    override val url: String,
    val title: String,
    val channel: String,
    val channelUrl: String?,
    val thumbnail: String?,
    val avatar: String?,
    val durationSeconds: Long,
    val views: Long?,
    val uploaded: String?,
    val isLive: Boolean,
    val isShort: Boolean,
    /** 0f..1f watched fraction, 0f when unwatched. */
    val progress: Float = 0f,
    /** Why a recommended video is in the list ("Because you watched ..."), null elsewhere. */
    val reason: String? = null
) : ContentItem

@Immutable
data class ChannelItem(
    override val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long?,
    val description: String?,
    /** 1 when new-upload notifications are on for this subscription. */
    val notify: Boolean = false,
    /** Database id of the subscription, 0 for channels that are not subscribed. */
    val uid: Long = 0
) : ContentItem

@Immutable
data class PlaylistItem(
    override val url: String,
    val name: String,
    val thumbnail: String?,
    val uploader: String?,
    val streamCount: Long
) : ContentItem

@Immutable
data class KioskRef(val id: String, val url: String, val title: String)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val message: String, val recaptchaUrl: String? = null) : UiState<Nothing>
}

@Immutable
data class ChannelTab(val key: String, val label: String, val index: Int)

@Immutable
data class ChannelDetails(
    val url: String,
    val name: String,
    val avatar: String?,
    val banner: String?,
    val subscribers: Long?,
    val description: String?,
    val tabs: List<ChannelTab>
)

@Immutable
data class PlaylistDetails(
    val url: String,
    val name: String,
    val thumbnail: String?,
    val uploader: String?,
    val streamCount: Long,
    val description: String?
)

@Immutable
data class CommentItem(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val likes: String?,
    val posted: String?,
    val hearted: Boolean,
    val pinned: Boolean,
    val isOwner: Boolean,
    val replyCount: Int,
    /** Opaque token to load replies, null when there are none. */
    val repliesToken: Any?
)

@Immutable
data class FeedGroupItem(val id: Long, val name: String)
