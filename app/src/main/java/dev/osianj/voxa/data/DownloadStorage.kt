package dev.osianj.voxa.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** [Episode.localPath] is either an absolute file path (app storage) or a content:// URI (chosen folder). */
fun localUri(path: String): Uri =
    directFile(path)?.let(Uri::fromFile)
        ?: if (path.startsWith("content://")) Uri.parse(path) else Uri.fromFile(File(path))

private const val EXTERNAL_STORAGE_DOCS = "content://com.android.externalstorage.documents/"

/**
 * "volume" and relative path of a file stored through the folder picker, e.g.
 * .../tree/primary%3APodcasts/document/primary%3APodcasts%2FShow%2Fep.mp3 -> ("primary", "Podcasts/Show/ep.mp3").
 * Null for anything else (app-storage paths, other providers, paths trying to climb out with "..").
 */
fun documentPathOf(path: String): Pair<String, String>? {
    if (!path.startsWith(EXTERNAL_STORAGE_DOCS)) return null
    val encoded = path.substringAfter("/document/", "")
    if (encoded.isEmpty()) return null
    val docId = java.net.URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
    val volume = docId.substringBefore(':')
    val rel = docId.substringAfter(':', "")
    if (volume.isEmpty() || rel.isEmpty() || rel.split('/').any { it == ".." }) return null
    return volume to rel
}

/**
 * The real file behind a folder-picker download, when Voxa has "All files access". Android forgets the folder
 * permission whenever the app is reinstalled, but the files are still there and this can read them directly.
 * Null if there is no all-files access or the file isn't there.
 */
fun directFile(path: String): File? {
    if (!android.os.Environment.isExternalStorageManager()) return null
    val (volume, rel) = documentPathOf(path) ?: return null
    val root = if (volume == "primary") android.os.Environment.getExternalStorageDirectory() else File("/storage/$volume")
    return File(root, rel).takeIf { it.isFile }
}

/**
 * Where downloads go. By default the app's private storage; or a folder the user picks with the
 * system folder picker (Storage Access Framework), in which case every podcast gets its own
 * subfolder named after the show and files are named "2024-04-12 - Episode title.mp3".
 */
class DownloadStorage(private val context: Context, private val internalDir: File) {

    private val prefs = context.getSharedPreferences("voxa", Context.MODE_PRIVATE)

    /** The chosen folder, or null if none is set (or Android revoked our access to it). */
    fun activeTree(): Uri? {
        val uri = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission && it.isWritePermission
        }
        return uri.takeIf { granted }
    }

    fun setTree(uri: Uri) {
        activeTree()?.takeIf { it != uri }?.let { old ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        context.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(KEY_TREE, uri.toString()).putBoolean(KEY_APP_STORAGE, false).apply()
    }

    /** True once a folder is chosen, or the user explicitly chose app storage, so we only ask once. */
    fun hasChosenFolderOrOptedOut(): Boolean = activeTree() != null || prefs.getBoolean(KEY_APP_STORAGE, false)

    fun useAppStorage() {
        prefs.edit().remove(KEY_TREE).putBoolean(KEY_APP_STORAGE, true).apply()
    }

    /** Human-readable location, e.g. "Internal storage/Music/Podcasts". */
    fun label(): String {
        val tree = activeTree() ?: return "App storage (private to Voxa, not visible in file managers)"
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val volumeName = if (volume == "primary") "Internal storage" else "SD card ($volume)"
        return if (path.isEmpty()) volumeName else "$volumeName/$path"
    }

    class Destination(val out: OutputStream, val path: String, val abort: () -> Unit)

    /** Create the file for an episode download and open it for writing. */
    fun open(show: Show, episode: Episode): Destination {
        val tree = activeTree()
        if (tree == null) {
            val dir = File(internalDir, "episodes").apply { mkdirs() }
            val file = File(dir, "${episode.id}.audio")
            return Destination(FileOutputStream(file), file.absolutePath) { file.delete() }
        }
        val root = DocumentFile.fromTreeUri(context, tree) ?: error("Download folder is unavailable")
        val dirName = safeName(show.title).ifEmpty { "Podcast ${show.id}" }
        val dir = root.findFile(dirName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(dirName)
            ?: error("Couldn't create a folder in the download location")
        val ext = extensionOf(episode.audioUrl)
        val date = if (episode.publishedAt > 0)
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(episode.publishedAt)) + " - " else ""
        val base = safeName(date + episode.title).take(120).ifEmpty { "episode-${episode.id}" }
        val doc = dir.createFile(mimeFor(ext), "$base.$ext") ?: error("Couldn't create the file")
        val out = context.contentResolver.openOutputStream(doc.uri) ?: error("Couldn't open the file for writing")
        return Destination(out, doc.uri.toString()) { doc.delete() }
    }

    fun delete(path: String) {
        // without the folder permission the picker route can't delete; the file itself can still be removed
        directFile(path)?.let { if (it.delete()) return }
        if (path.startsWith("content://")) {
            runCatching { DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete() }
        } else {
            File(path).delete()
        }
    }

    /** After unsubscribing: remove the show's folder if nothing is left in it. */
    fun deleteShowFolderIfEmpty(show: Show) {
        val tree = activeTree() ?: return
        runCatching {
            val root = DocumentFile.fromTreeUri(context, tree) ?: return
            val dir = root.findFile(safeName(show.title).ifEmpty { "Podcast ${show.id}" }) ?: return
            if (dir.isDirectory && dir.listFiles().isEmpty()) dir.delete()
        }
    }

    private fun safeName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimStart('.')
            .trimEnd('.', ' ')

    private fun extensionOf(url: String): String {
        val last = Uri.parse(url).lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (last in KNOWN_EXTENSIONS) last else "mp3"
    }

    private fun mimeFor(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/x-wav"
        "flac" -> "audio/flac"
        else -> "audio/mpeg"
    }

    private companion object {
        const val KEY_TREE = "download_tree"
        const val KEY_APP_STORAGE = "download_app_storage"
        val KNOWN_EXTENSIONS = setOf("mp3", "m4a", "mp4", "aac", "ogg", "opus", "wav", "flac")
    }
}
