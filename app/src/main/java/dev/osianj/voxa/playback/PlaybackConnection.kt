package dev.osianj.voxa.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show
import dev.osianj.voxa.data.localUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Thin wrapper the UI uses to start playback, queue episodes and observe the controller. */
class PlaybackConnection(private val context: Context) {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller

    fun connect() {
        if (_controller.value != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ _controller.value = future.get() }, MoreExecutors.directExecutor())
    }

    /** Replace whatever is playing (and the queue) with this episode, resuming where you left off. */
    fun play(episode: Episode, show: Show, upNext: List<Episode> = emptyList()) {
        val c = _controller.value ?: return
        c.setMediaItems(listOf(buildItem(episode, show)) + upNext.map { buildItem(it, show) }, 0, episode.positionMs)
        c.prepare()
        c.play()
    }

    /** Append to the queue. If nothing is loaded yet this just plays it. */
    fun addToQueue(episode: Episode, show: Show) {
        val c = _controller.value ?: return
        if (c.mediaItemCount == 0) play(episode, show) else c.addMediaItem(buildItem(episode, show))
    }

    private fun buildItem(episode: Episode, show: Show): MediaItem = episode.toMediaItem(show)

    fun release() {
        _controller.value?.release()
        _controller.value = null
    }
}
