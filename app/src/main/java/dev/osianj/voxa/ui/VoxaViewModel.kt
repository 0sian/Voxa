package dev.osianj.voxa.ui

import android.content.Context
import android.os.Bundle
import androidx.media3.session.SessionCommand
import dev.osianj.voxa.playback.PlaybackService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dev.osianj.voxa.VoxaApplication
import dev.osianj.voxa.data.Episode
import dev.osianj.voxa.data.Show
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VoxaViewModel(app: VoxaApplication) : ViewModel() {
    val repo = app.repository
    val playback = app.playback
    private val prefs = app.getSharedPreferences("voxa", Context.MODE_PRIVATE)

    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** null until the first database emission, so the UI can tell "loading" from "empty". */
    val shows: StateFlow<List<Show>?> = repo.observeShows().state(null)

    /** showId -> date of its newest episode. */
    val latestByShow: StateFlow<Map<Long, Long>> =
        repo.observeLatest().map { list -> list.associate { it.showId to it.latest } }.state(emptyMap())

    val recentEpisodes: StateFlow<List<Episode>> = repo.observeRecent().state(emptyList())
    val downloadedEpisodes: StateFlow<List<Episode>> = repo.observeDownloaded().state(emptyList())
    val downloadProgress = repo.downloadProgress

    private val _sleepEndsAt = MutableStateFlow<Long?>(null)
    val sleepEndsAt: StateFlow<Long?> = _sleepEndsAt
    private var sleepJob: Job? = null

    fun episodes(showId: Long) = repo.observeEpisodes(showId)

    /** Swap a show with its neighbour. delta is -1 (earlier) or +1 (later). */
    fun move(showId: Long, delta: Int) {
        val list = shows.value ?: return
        val from = list.indexOfFirst { it.id == showId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val ids = list.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { repo.reorder(ids) }
    }

    fun refresh(showId: Long) = viewModelScope.launch {
        try {
            repo.refresh(showId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline or bad feed: keep what we have
        }
    }

    fun removeShow(showId: Long) = viewModelScope.launch { repo.removeShow(showId) }
    fun deleteDownload(ep: Episode) = viewModelScope.launch { repo.deleteDownload(ep) }
    fun setHidden(ep: Episode, hidden: Boolean) = viewModelScope.launch { repo.setHidden(ep, hidden) }

    fun clearProgress(ep: Episode) {
        // If it's the episode currently loaded in the player, rewind too, otherwise the
        // 5-second position save would write the old progress straight back.
        playback.controller.value?.let { c ->
            if (c.currentMediaItem?.mediaId == ep.id.toString()) c.seekTo(0)
        }
        viewModelScope.launch { repo.clearProgress(ep) }
    }

    /** 0.75x, 1x, 1.25x ... 2x, then back round. Remembered between launches. */
    fun cycleSpeed() {
        val c = playback.controller.value ?: return
        val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        val next = speeds.firstOrNull { it > c.playbackParameters.speed + 0.01f } ?: speeds.first()
        c.setPlaybackSpeed(next)
        prefs.edit().putFloat("speed", next).apply()
    }

    /**
     * Play [ep] and queue the rest of the show after it, in the order the show page is sorted
     * (hidden and already-played episodes are skipped).
     */
    fun playFrom(ep: Episode, show: Show) {
        viewModelScope.launch {
            val all = repo.episodesOf(show.id) // newest first
            val oldestFirst = prefs.getBoolean("oldest_first_${show.id}", false)
            val ordered = if (oldestFirst) all.reversed() else all
            val idx = ordered.indexOfFirst { it.id == ep.id }
            val upNext = if (idx < 0) emptyList()
            else ordered.drop(idx + 1).filter { !it.hidden && !it.played }
            playback.play(ep, show, upNext)
        }
    }

    /** Pause playback after [minutes]; null cancels the timer. */
    fun setSleep(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _sleepEndsAt.value = null
            return
        }
        _sleepEndsAt.value = System.currentTimeMillis() + minutes * 60_000L
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            playback.controller.value?.pause()
            _sleepEndsAt.value = null
        }
    }

    init {
        // Pull fresh episodes for every show once per launch.
        viewModelScope.launch {
            val list = repo.observeShows().first()
            list.forEach { show ->
                try {
                    repo.refresh(show.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // offline: carry on
                }
            }
        }

        viewModelScope.launch {
            playback.controller.filterNotNull().collect { c ->
                c.setPlaybackSpeed(prefs.getFloat("speed", 1f))
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (!isPlaying) savePosition(c)
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
                            viewModelScope.launch {
                                repo.markPlayed(id, true)
                                repo.savePosition(id, 0)
                            }
                        }
                    }

                    // A queued episode finished and the next one started on its own.
                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int,
                    ) {
                        if (reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                        val id = oldPosition.mediaItem?.mediaId?.toLongOrNull() ?: return
                        viewModelScope.launch {
                            repo.markPlayed(id, true)
                            repo.savePosition(id, 0)
                        }
                    }

                    // Queued items start from 0; resume where you left off if you'd already started one.
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO &&
                            reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                        ) return
                        val id = item?.mediaId?.toLongOrNull() ?: return
                        viewModelScope.launch {
                            val ep = repo.getEpisode(id) ?: return@launch
                            if (ep.positionMs > 0 && !ep.played) c.seekTo(ep.positionMs)
                        }
                    }
                })
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                playback.controller.value?.let { if (it.isPlaying) savePosition(it) }
            }
        }
    }

    private val _skipSilence = MutableStateFlow(prefs.getBoolean(PlaybackService.PREF_SKIP_SILENCE, false))
    val skipSilence: StateFlow<Boolean> = _skipSilence
    private val _volumeBoost = MutableStateFlow(prefs.getBoolean(PlaybackService.PREF_VOLUME_BOOST, false))
    val volumeBoost: StateFlow<Boolean> = _volumeBoost

    private val _mono = MutableStateFlow(prefs.getBoolean(PlaybackService.PREF_MONO, false))
    val mono: StateFlow<Boolean> = _mono

    fun setMono(on: Boolean) {
        _mono.value = on
        sendToggle(PlaybackService.ACTION_MONO, on)
    }

    fun setSkipSilence(on: Boolean) {
        _skipSilence.value = on
        sendToggle(PlaybackService.ACTION_SKIP_SILENCE, on)
    }

    fun setVolumeBoost(on: Boolean) {
        _volumeBoost.value = on
        sendToggle(PlaybackService.ACTION_VOLUME_BOOST, on)
    }

    private fun sendToggle(action: String, on: Boolean) {
        playback.controller.value?.sendCustomCommand(
            SessionCommand(action, Bundle.EMPTY),
            Bundle().apply { putBoolean(PlaybackService.EXTRA_ON, on) },
        )
    }

    private val backup = dev.osianj.voxa.data.Backup(app, repo)

    /** Runs a backup/restore action and reports a one-line result for a toast. */
    fun backupAction(kind: String, uri: android.net.Uri, done: (String) -> Unit) {
        viewModelScope.launch {
            val message = try {
                when (kind) {
                    "export" -> { backup.exportJson(uri); "Backup saved" }
                    "opml_export" -> { backup.exportOpml(uri); "Subscriptions exported" }
                    "restore" -> backup.importJson(uri).summary("Restored")
                    else -> backup.importOpml(uri).summary("Imported")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Failed: ${e.message ?: "unknown error"}"
            }
            done(message)
        }
    }

    private fun dev.osianj.voxa.data.Backup.Result.summary(verb: String) =
        "$verb: $added added, $existing already there" + if (failed > 0) ", $failed couldn't load" else ""

    /** Swiped away: remember where you were, then stop and clear the queue. */
    fun dismissPlayer() {
        val c = playback.controller.value ?: return
        savePosition(c)
        c.pause()
        c.clearMediaItems()
    }

    private fun savePosition(c: MediaController) {
        if (c.playbackState == Player.STATE_ENDED) return
        val id = c.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = c.currentPosition
        viewModelScope.launch { repo.savePosition(id, pos) }
    }
}
