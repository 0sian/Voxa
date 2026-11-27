package dev.osianj.voxa.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.delay

/** Compose-observable snapshot of the player, shared by the mini player, full player and rows. */
@Stable
class PlayerUiState(val controller: MediaController?) {
    var hasItem by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf<String?>(null)
    var artist by mutableStateOf<String?>(null)
    var artwork by mutableStateOf<Uri?>(null)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)
    var currentIndex by mutableIntStateOf(0)
    var queue by mutableStateOf<List<MediaItem>>(emptyList())

    fun sync() {
        val c = controller ?: return
        hasItem = c.mediaItemCount > 0
        isPlaying = c.isPlaying
        mediaId = c.currentMediaItem?.mediaId
        title = c.mediaMetadata.title?.toString()
        artist = c.mediaMetadata.artist?.toString()
        artwork = c.mediaMetadata.artworkUri
        positionMs = c.currentPosition.coerceAtLeast(0)
        durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        speed = c.playbackParameters.speed
        currentIndex = c.currentMediaItemIndex
        queue = List(c.mediaItemCount) { c.getMediaItemAt(it) }
    }
}

@Composable
fun rememberPlayerUiState(controller: MediaController?): PlayerUiState {
    val state = remember(controller) { PlayerUiState(controller) }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.sync()
        }
        controller?.addListener(listener)
        state.sync()
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller) {
        while (controller != null) {
            state.sync()
            delay(500)
        }
    }
    return state
}
