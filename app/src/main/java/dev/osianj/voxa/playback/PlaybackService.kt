package dev.osianj.voxa.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.osianj.voxa.VoxaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Owns the player so audio survives the activity. The app talks to it through a MediaController
 * (see PlaybackConnection). It is also a media library, so Android Auto can browse
 * Podcasts > Episodes and start playback from the car's screen.
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var exo: ExoPlayer? = null
    private var enhancer: LoudnessEnhancer? = null
    private val monoMix = MonoMixProcessor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { getSharedPreferences("voxa", Context.MODE_PRIVATE) }
    private val repo get() = (application as VoxaApplication).repository

    private fun <T> async(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try { future.set(block()) } catch (e: Throwable) { future.setException(e) }
        }
        return future
    }

    private fun applyBoost(on: Boolean) {
        enhancer?.release()
        enhancer = null
        val id = exo?.audioSessionId ?: return
        if (on && id != C.AUDIO_SESSION_ID_UNSET) {
            enhancer = runCatching {
                LoudnessEnhancer(id).apply { setTargetGain(BOOST_MILLIBELS); enabled = true }
            }.getOrNull()
        }
    }

    // ---- Android Auto browse tree: root > shows > episodes ----

    private fun browsable(id: String, title: String, artwork: String? = null, subtitle: String? = null) =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artwork?.let(Uri::parse))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    /** Episodes of a show in the order the user sorted it, hidden ones skipped. */
    private suspend fun orderedEpisodes(showId: Long) =
        repo.episodesOf(showId).filter { !it.hidden }.let {
            if (prefs.getBoolean("oldest_first_$showId", false)) it.reversed() else it
        }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_SKIP_SILENCE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_VOLUME_BOOST, Bundle.EMPTY))
                .add(SessionCommand(ACTION_MONO, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val on = args.getBoolean(EXTRA_ON)
            when (customCommand.customAction) {
                ACTION_SKIP_SILENCE -> {
                    prefs.edit().putBoolean(PREF_SKIP_SILENCE, on).apply()
                    exo?.skipSilenceEnabled = on
                }
                ACTION_VOLUME_BOOST -> {
                    prefs.edit().putBoolean(PREF_VOLUME_BOOST, on).apply()
                    applyBoost(on)
                }
                ACTION_MONO -> {
                    prefs.edit().putBoolean(PREF_MONO, on).apply()
                    monoMix.mono = on
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browsable(ROOT_ID, "Voxa"), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = async {
            val items: List<MediaItem> = when {
                parentId == ROOT_ID ->
                    repo.allShows().map { browsable(SHOW_PREFIX + it.id, it.title, it.artworkUrl, it.author) }
                parentId.startsWith(SHOW_PREFIX) -> {
                    val showId = parentId.removePrefix(SHOW_PREFIX).toLongOrNull()
                    val show = showId?.let { repo.getShow(it) }
                    if (show == null) emptyList() else orderedEpisodes(show.id).map { it.toMediaItem(show) }
                }
                else -> emptyList()
            }
            val from = (page.toLong() * pageSize).coerceAtMost(items.size.toLong()).toInt()
            val to = (from.toLong() + pageSize).coerceAtMost(items.size.toLong()).toInt()
            LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = async {
            val item: MediaItem? = when {
                mediaId == ROOT_ID -> browsable(ROOT_ID, "Voxa")
                mediaId.startsWith(SHOW_PREFIX) ->
                    mediaId.removePrefix(SHOW_PREFIX).toLongOrNull()?.let { repo.getShow(it) }
                        ?.let { browsable(SHOW_PREFIX + it.id, it.title, it.artworkUrl, it.author) }
                else -> mediaId.toLongOrNull()?.let { repo.getEpisode(it) }?.let { ep ->
                    repo.getShow(ep.showId)?.let { ep.toMediaItem(it) }
                }
            }
            if (item == null) LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE) else LibraryResult.ofItem(item, null)
        }

        /**
         * Items from the app already carry a URI and pass straight through. A bare media id (the car's
         * screen) is resolved to the episode, resumes where you left off, and queues the rest of the show.
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = async {
            if (mediaItems.all { it.localConfiguration != null }) {
                return@async MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            }
            val first = mediaItems.getOrNull(startIndex.coerceAtLeast(0))
            val episode = first?.mediaId?.toLongOrNull()?.let { repo.getEpisode(it) }
            val show = episode?.let { repo.getShow(it.showId) }
            if (episode == null || show == null) {
                MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            } else {
                val ordered = orderedEpisodes(show.id)
                val idx = ordered.indexOfFirst { it.id == episode.id }
                val upNext = if (idx < 0) emptyList() else ordered.drop(idx + 1).filter { !it.played }
                MediaItemsWithStartPosition(
                    (listOf(episode) + upNext).map { it.toMediaItem(show) },
                    0,
                    episode.positionMs,
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        monoMix.mono = prefs.getBoolean(PREF_MONO, false)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(monoMix))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        exo = player
        player.skipSilenceEnabled = prefs.getBoolean(PREF_SKIP_SILENCE, false)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                applyBoost(prefs.getBoolean(PREF_VOLUME_BOOST, false))
            }
        })
        applyBoost(prefs.getBoolean(PREF_VOLUME_BOOST, false))
        session = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        enhancer?.release()
        enhancer = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_SKIP_SILENCE = "voxa.skip_silence"
        const val ACTION_VOLUME_BOOST = "voxa.volume_boost"
        const val ACTION_MONO = "voxa.mono"
        const val EXTRA_ON = "on"
        const val PREF_SKIP_SILENCE = "skip_silence"
        const val PREF_VOLUME_BOOST = "volume_boost"
        const val PREF_MONO = "mono_mix"
        private const val BOOST_MILLIBELS = 600
        private const val ROOT_ID = "root"
        private const val SHOW_PREFIX = "show:"
    }
}
