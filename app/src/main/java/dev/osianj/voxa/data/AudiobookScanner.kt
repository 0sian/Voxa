package dev.osianj.voxa.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Turns a folder of audiobooks into library entries. A folder holding audio files is one book (each file a
 * chapter, in natural filename order); a loose audio file in the root is a book on its own (an .m4b keeps its
 * chapter list inside, see [Mp4Chapters]). A book is a [Show] of kind audiobook and its files are [Episode]s,
 * so playback, resume, the queue and the mini player all work unchanged.
 *
 * Audio files are only ever READ. Nothing here, or in the code paths books go through, deletes or writes them.
 */
class AudiobookScanner(private val ctx: Context, private val db: VoxaDatabase) {

    private val coversDir = File(ctx.filesDir, "audiobook_covers").apply { mkdirs() }
    private val prefs = ctx.getSharedPreferences("voxa", Context.MODE_PRIVATE)

    data class Result(val added: Int, val updated: Int, val removed: Int)

    private class Candidate(val path: String, val files: List<File>, val dir: File?)

    /** Books the user removed from the library; scans skip them until they are restored. */
    fun ignore(show: Show) {
        val path = show.sourcePath ?: return
        prefs.edit().putStringSet(KEY_IGNORED, (ignored() + path).toSet()).apply()
    }

    fun clearIgnored() = prefs.edit().remove(KEY_IGNORED).apply()
    private fun ignored(): Set<String> = prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()

    suspend fun scan(root: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result = withContext(Dispatchers.IO) {
        if (!root.isDirectory) return@withContext Result(0, 0, 0)
        val skip = ignored()
        val found = discover(root).filter { it.path !in skip }
        val shows = db.showDao()
        val episodes = db.episodeDao()
        val existing = shows.getAudiobooks().associateBy { it.sourcePath }

        var added = 0
        var updated = 0
        found.forEachIndexed { i, c ->
            onProgress(i + 1, found.size)
            val sig = signature(c.files)
            val old = existing[c.path]
            if (old != null && old.scanSig == sig) return@forEachIndexed

            val book = read(c)
            val now = System.currentTimeMillis()
            db.withTransaction {
                val showId = if (old != null) {
                    shows.updateBook(old.id, book.title, book.author, book.artworkUrl, sig, now)
                    updated++
                    old.id
                } else {
                    val id = shows.insert(
                        Show(
                            feedUrl = "audiobook:${c.path}", title = book.title, author = book.author,
                            artworkUrl = book.artworkUrl, position = shows.nextPosition(), lastRefreshed = now,
                            kind = KIND_AUDIOBOOK, sourcePath = c.path, scanSig = sig,
                        )
                    )
                    if (id <= 0) return@withTransaction
                    // chapters must play in book order, not newest-first
                    prefs.edit().putBoolean("oldest_first_$id", true).apply()
                    added++
                    id
                }
                val known = episodes.getGuids(showId).toSet()
                val fresh = ArrayList<Episode>()
                for (ch in book.chapters) {
                    if (ch.guid in known) {
                        episodes.updateBookEpisode(showId, ch.guid, ch.title, ch.durationSec, ch.uri, ch.chaptersUrl, ch.order)
                    } else {
                        fresh += Episode(
                            showId = showId, guid = ch.guid, title = ch.title, audioUrl = ch.uri,
                            publishedAt = ch.order, durationSec = ch.durationSec, chaptersUrl = ch.chaptersUrl,
                        )
                    }
                }
                if (fresh.isNotEmpty()) episodes.insertAll(fresh)
                // files that were deleted or moved out of the book
                episodes.deleteMissing(showId, book.chapters.map { it.guid })
            }
        }

        // A book whose folder is gone leaves the library, with its progress. An empty result is treated as a
        // glitch (unmounted storage, permission revoked), never as "delete everything".
        var removed = 0
        if (found.isNotEmpty()) {
            val keep = found.map { it.path }.toSet()
            existing.values.filter { it.sourcePath !in keep && it.sourcePath !in skip }.forEach {
                shows.delete(it.id); removed++
            }
        }
        Result(added, updated, removed)
    }

    // ---- discovery -------------------------------------------------------------

    private fun discover(root: File): List<Candidate> {
        val out = ArrayList<Candidate>()
        fun audio(dir: File) = dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in AUDIO && !it.name.startsWith(".") }
            .sortedWith(compareBy(natural) { it.name })
        // loose files in the root are separate books, folders are one book each
        audio(root).forEach { out += Candidate(it.path, listOf(it), null) }
        fun walk(dir: File) {
            dir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }.forEach { sub ->
                val files = audio(sub)
                if (files.isNotEmpty()) out += Candidate(sub.path, files, sub)
                walk(sub)
            }
        }
        walk(root)
        return out
    }

    /** Changes when the files change, or when [SCAN_VERSION] is bumped so books are re-read with a better scanner. */
    private fun signature(files: List<File>): String =
        sha("v$SCAN_VERSION|" + files.joinToString("|") { "${it.name}:${it.length()}:${it.lastModified()}" })

    // ---- reading one book -------------------------------------------------------

    private class Chapter(val guid: String, val title: String, val uri: String, val durationSec: Int, val chaptersUrl: String?, val order: Long)
    private class Book(val title: String, val author: String, val artworkUrl: String?, val chapters: List<Chapter>)

    private fun read(c: Candidate): Book {
        val single = c.files.size == 1
        val tags = c.files.map { tagsOf(it) }
        val first = tags.first()

        val folderName = c.dir?.name?.replace('_', ' ')?.trim()
        val fileName = c.files.first().nameWithoutExtension.replace('_', ' ').trim()
        val title = (first.album.takeIf { it.isNotBlank() } ?: if (single) first.title.takeIf { it.isNotBlank() } else null)
            ?: folderName ?: fileName
        val author = first.author

        // tag titles are only useful when every file has its own; "Book Title" on all 40 files is not a chapter name
        val titleTags = tags.map { it.title }
        val usableTitles = !single && titleTags.all { it.isNotBlank() } && titleTags.toSet().size == titleTags.size

        val chapters = c.files.mapIndexed { i, f ->
            val chapterTitle = when {
                single -> title
                usableTitles -> titleTags[i]
                else -> f.nameWithoutExtension.replace('_', ' ').replace(Regex("^\\s*\\d+\\s*[-._)]*\\s*(?=\\S)"), "").trim().ifEmpty { "Part ${i + 1}" }
            }
            val embedded = single && f.extension.lowercase() in M4_EXT && Mp4Chapters.read(f).size >= 2
            Chapter(
                guid = f.path, title = chapterTitle, uri = Uri.fromFile(f).toString(),
                durationSec = (tags[i].durationMs / 1000).toInt(),
                chaptersUrl = if (embedded) "embedded:${f.path}" else null,
                order = SYNTHETIC_EPOCH + i * 60_000L,
            )
        }
        return Book(title, author, cover(c, title), chapters)
    }

    private class Tags(val album: String, val title: String, val author: String, val durationMs: Long)

    private fun tagsOf(f: File): Tags {
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(f.path)
            fun s(key: Int) = m.extractMetadata(key)?.trim().orEmpty()
            Tags(
                album = s(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                title = s(MediaMetadataRetriever.METADATA_KEY_TITLE),
                author = listOf(
                    MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, MediaMetadataRetriever.METADATA_KEY_ARTIST,
                    MediaMetadataRetriever.METADATA_KEY_AUTHOR, MediaMetadataRetriever.METADATA_KEY_WRITER,
                ).map(::s).firstOrNull { it.isNotBlank() }.orEmpty(),
                durationMs = s(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            Tags("", "", "", 0L)
        } finally {
            runCatching { m.release() }
        }
    }

    /** cover.jpg and friends in the book's folder, else the picture embedded in the first file. Saved as our own copy. */
    private fun cover(c: Candidate, title: String): String? {
        val out = File(coversDir, sha(c.path) + ".jpg")
        val bitmap: Bitmap? = c.dir?.listFiles().orEmpty()
            .firstOrNull { it.isFile && it.nameWithoutExtension.lowercase() in COVER_NAMES && it.extension.lowercase() in IMAGE_EXT }
            ?.let { BitmapFactory.decodeFile(it.path) }
            ?: embeddedPicture(c.files.first())
        if (bitmap == null) return null
        val scaled = if (bitmap.width > 600) Bitmap.createScaledBitmap(bitmap, 600, (bitmap.height * 600f / bitmap.width).toInt(), true) else bitmap
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(out).toString()
    }

    private fun embeddedPicture(f: File): Bitmap? {
        val m = MediaMetadataRetriever()
        return try {
            m.setDataSource(f.path)
            m.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { m.release() }
        }
    }

    // ---- helpers ----------------------------------------------------------------

    private val natural = Comparator<String> { a, b ->
        val x = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
        val y = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val r = if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger()) else p.compareTo(q)
            if (r != 0) return@Comparator r
        }
        x.size - y.size
    }

    private fun sha(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private const val KEY_IGNORED = "audiobook_ignored"
        /** Bump whenever the scanner learns something new (chapters, covers, titles) so existing books are re-read once. */
        private const val SCAN_VERSION = 2
        /** Sep 2001: chapters are ordered by this + index, and the UI never shows it as a date. */
        const val SYNTHETIC_EPOCH = 1_000_000_000_000L
        private val AUDIO = setOf("mp3", "m4b", "m4a", "aac", "ogg", "opus", "flac", "wav")
        private val M4_EXT = setOf("m4b", "m4a", "mp4")
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp")
        private val COVER_NAMES = setOf("cover", "folder", "front", "album", "artwork", "poster")
    }
}
