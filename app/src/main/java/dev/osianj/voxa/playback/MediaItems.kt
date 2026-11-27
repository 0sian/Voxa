package dev.osianj.voxa.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show
import dev.osianj.voxa.data.localUri

/** The playable item for an episode: the local file if downloaded, otherwise the stream URL. */
fun Episode.toMediaItem(show: Show): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(localPath?.let(::localUri) ?: Uri.parse(audioUrl))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(show.title)
                .setArtworkUri(show.artworkUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
