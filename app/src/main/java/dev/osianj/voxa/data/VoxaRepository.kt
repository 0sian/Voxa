package dev.osianj.voxa.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request

class VoxaRepository(
    private val db: VoxaDatabase,
    private val storage: DownloadStorage,
    private val scanner: AudiobookScanner? = null,
    private val http: OkHttpClient = OkHttpClient(),
) {
    /**
     * Live download progress for the UI: episodeId -> 0f..1f. An episode is only present
     * while it is downloading, so `episode.id in progress` means "downloading".
     */
    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<Long, Float>> = _downloadProgress

    suspend fun getShow(id: Long): Show? = shows.getShow(id)

    private val shows get() = db.showDao()
    private val episodes get() = db.episodeDao()

    fun observeShows(): Flow<List<Show>> = shows.observeShows()
    fun observeAudiobooks(): Flow<List<Show>> = shows.observeAudiobooks()
    fun observeEveryShow(): Flow<List<Show>> = shows.observeEveryShow()
    fun observeAudiobookEpisodes(): Flow<List<Episode>> = episodes.observeAudiobookEpisodes()

    /** Scan the audiobooks folder. Books are added, updated or dropped; audio files are only ever read. */
    suspend fun scanAudiobooks(root: java.io.File, onProgress: (Int, Int) -> Unit = { _, _ -> }) =
        scanner?.scan(root, onProgress)

    fun restoreIgnoredAudiobooks() = scanner?.clearIgnored()
    fun observeEpisodes(showId: Long): Flow<List<Episode>> = episodes.observeEpisodes(showId)
    fun observeEpisode(id: Long): Flow<Episode?> = episodes.observeEpisode(id)
    fun observeRecent(): Flow<List<Episode>> = episodes.observeRecent()
    fun observeDownloaded(): Flow<List<Episode>> = episodes.observeDownloaded()
    fun observeLatest(): Flow<List<ShowLatest>> = episodes.observeLatest()

    /** Add a show from a pasted RSS URL. Returns the new show id, or throws on a bad feed. */
    suspend fun addShow(feedUrl: String): Long = withContext(Dispatchers.IO) {
        val url = feedUrl.trim()
        val feed = fetch(url)
        val id = shows.insert(
            Show(
                feedUrl = url,
                title = feed.title,
                author = feed.author,
                description = feed.description,
                artworkUrl = feed.artworkUrl,
                position = shows.nextPosition(),
                lastRefreshed = System.currentTimeMillis(),
            )
        )
        if (id == -1L) error("Already subscribed to that feed")
        storeEpisodes(id, feed)
        id
    }

    /** Re-reads the feed. Returns the episodes that weren't known before (for notifications). */
    suspend fun refresh(showId: Long): List<ParsedEpisode> = withContext(Dispatchers.IO) {
        val show = shows.getShow(showId) ?: return@withContext emptyList()
        if (show.kind == KIND_AUDIOBOOK) return@withContext emptyList() // books are rescanned, not fetched
        val feed = fetch(show.feedUrl)
        shows.updateMeta(showId, feed.title, feed.author, feed.description, feed.artworkUrl, System.currentTimeMillis())
        storeEpisodes(showId, feed)
    }

    suspend fun allShows(): List<Show> = shows.getAll()

    suspend fun setShowPosition(id: Long, position: Int) = shows.setPosition(id, position)

    suspend fun applyEpisodeState(showId: Long, guid: String, positionMs: Long, played: Boolean, hidden: Boolean) =
        episodes.setState(showId, guid, positionMs, played, hidden)

    /** Podcasting 2.0 chapters JSON: {"chapters":[{"startTime":0,"title":"Intro"}]}. */
    suspend fun fetchChapters(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        // an audiobook file with its chapter list inside it (m4b)
        if (url.startsWith("embedded:")) return@withContext Mp4Chapters.read(java.io.File(url.removePrefix("embedded:")))
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Chapters returned HTTP ${response.code}" }
            val json = org.json.JSONObject(response.body?.string().orEmpty())
            val arr = json.optJSONArray("chapters") ?: return@use emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title").trim()
                if (title.isEmpty() || o.optBoolean("toc", true).not()) null
                else Chapter((o.optDouble("startTime", 0.0) * 1000).toLong(), title)
            }.sortedBy { it.startMs }
        }
    }

    /** Gear menu > Remove: unsubscribes and deletes any downloaded audio. */
    suspend fun removeShow(showId: Long) = withContext(Dispatchers.IO) {
        val show = shows.getShow(showId)
        if (show?.kind == KIND_AUDIOBOOK) {
            // Remove from the library only. The audio files belong to the user and are never deleted.
            scanner?.ignore(show)
            shows.delete(showId)
            return@withContext
        }
        episodes.getDownloaded(showId).forEach { it.localPath?.let(storage::delete) }
        if (show != null) storage.deleteShowFolderIfEmpty(show)
        shows.delete(showId)
    }

    /**
     * Bulk download. Skips hidden episodes and ones already on disk. Downloads in the order the
     * list is shown ([oldestFirst]). One bad episode doesn't stop the rest; returns how many failed.
     * Cancellation (the notification's Cancel button) propagates.
     */
    suspend fun downloadScope(
        showId: Long,
        scope: DownloadScope,
        oldestFirst: Boolean,
        /** Called as each episode starts: (episode, 1-based index, total). */
        onEpisodeStart: (Episode, Int, Int) -> Unit = { _, _, _ -> },
        /** Called with 0f..1f for the current episode, at most once per percent. */
        onProgress: (Float) -> Unit = {},
    ): Int = withContext(Dispatchers.IO) {
        if (shows.getShow(showId)?.kind == KIND_AUDIOBOOK) return@withContext 0 // already on the phone
        val pending = episodes.getUndownloaded(showId, Int.MAX_VALUE) // newest first, non-hidden
        val chosen = when (scope) {
            is DownloadScope.Latest -> pending.take(scope.count)
            DownloadScope.Unplayed -> pending.filter { !it.played }
            DownloadScope.All -> pending
        }
        val todo = if (oldestFirst) chosen.reversed() else chosen
        var failed = 0
        todo.forEachIndexed { i, ep ->
            onEpisodeStart(ep, i + 1, todo.size)
            try {
                downloadEpisode(ep, onProgress)
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
            }
        }
        failed
    }

    suspend fun downloadEpisode(episode: Episode, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            val show = shows.getShow(episode.showId) ?: error("Show no longer exists")
            if (show.kind == KIND_AUDIOBOOK) return@withContext // nothing to download, and nothing of the user's to overwrite
            var dest: DownloadStorage.Destination? = null
            var saved = false
            try {
                val request = Request.Builder().url(episode.audioUrl).build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                    val body = response.body ?: error("Empty response")
                    // Only create the file once the server has said yes, so failures leave nothing behind.
                    val target = storage.open(show, episode).also { dest = it }
                    val total = body.contentLength()
                    val buffer = ByteArray(32 * 1024)
                    var read = 0L
                    var lastPercent = -1
                    target.out.use { out ->
                        body.byteStream().use { input ->
                            while (true) {
                                coroutineContext.ensureActive() // lets Cancel in the notification work
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                read += n
                                if (total > 0) {
                                    val percent = (read * 100 / total).toInt()
                                    if (percent != lastPercent) {
                                        lastPercent = percent
                                        val fraction = percent / 100f
                                        _downloadProgress.update { it + (episode.id to fraction) }
                                        onProgress(fraction)
                                    }
                                }
                            }
                        }
                    }
                }
                episodes.setLocalPath(episode.id, dest!!.path)
                saved = true
            } finally {
                _downloadProgress.update { it - episode.id }
                if (!saved) dest?.abort() // failed or cancelled: remove the partial file
            }
        }

    /** Edit mode: delete just the downloaded file, keep the episode (streams again). */
    suspend fun deleteDownload(episode: Episode) = withContext(Dispatchers.IO) {
        // an audiobook episode is the user's own file, not a download: never delete it
        if (shows.getShow(episode.showId)?.kind == KIND_AUDIOBOOK) return@withContext
        episode.localPath?.let(storage::delete)
        episodes.setLocalPath(episode.id, null)
    }

    /**
     * Edit mode: hide or unhide an episode. Hidden episodes disappear from the normal list and
     * are skipped by downloads, but nothing is deleted (any downloaded file stays), and a feed
     * refresh won't bring them back.
     */
    suspend fun setHidden(episode: Episode, hidden: Boolean) = episodes.setHidden(episode.id, hidden)

    /** Persist a new order after the user drags shows around. */
    suspend fun reorder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> shows.setPosition(id, index) }
    }

    /** Edit mode: forget how far through an episode you got and mark it unplayed. */
    suspend fun clearProgress(episode: Episode) = episodes.clearProgress(episode.id)

    suspend fun savePosition(episodeId: Long, positionMs: Long) = episodes.savePosition(episodeId, positionMs)
    suspend fun markPlayed(episodeId: Long, played: Boolean) = episodes.setPlayed(episodeId, played)
    suspend fun getEpisode(id: Long) = episodes.getEpisode(id)
    suspend fun episodesOf(showId: Long) = episodes.observeEpisodes(showId).first()

    private suspend fun storeEpisodes(showId: Long, feed: ParsedFeed): List<ParsedEpisode> {
        val known = episodes.getGuids(showId).toHashSet()
        val fresh = feed.episodes.filter { it.guid !in known }
        // Existing rows are never overwritten (that would lose progress), but chapters can appear later.
        feed.episodes.filter { it.guid in known && it.chaptersUrl != null }
            .forEach { episodes.setChaptersUrl(showId, it.guid, it.chaptersUrl) }
        episodes.insertAll(
            feed.episodes.map {
                Episode(
                    showId = showId,
                    guid = it.guid,
                    title = it.title,
                    description = it.description,
                    audioUrl = it.audioUrl,
                    publishedAt = it.publishedAt,
                    durationSec = it.durationSec,
                    chaptersUrl = it.chaptersUrl,
                )
            }
        )
        return fresh
    }

    private fun fetch(url: String): ParsedFeed {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Feed returned HTTP ${response.code}" }
            val body = response.body ?: error("Empty response")
            return RssParser.parse(body.byteStream())
        }
    }
}
