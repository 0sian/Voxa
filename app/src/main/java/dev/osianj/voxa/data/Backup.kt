package dev.osianj.voxa.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/** Backup/restore. JSON keeps subscriptions, order, progress and settings; OPML is subscriptions only. */
class Backup(private val context: Context, private val repo: VoxaRepository) {

    private val prefs = context.getSharedPreferences("voxa", Context.MODE_PRIVATE)

    data class Result(val added: Int, val existing: Int, val failed: Int)

    private val globalKeys = listOf("speed", "skip_back_s", "skip_fwd_s", "home_grid", "home_alpha", "skip_silence", "volume_boost", "mono_mix")

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject().put("app", "voxa").put("version", 1)
        val p = JSONObject()
        globalKeys.forEach { k ->
            when (val v = prefs.all[k]) {
                is Boolean, is Int, is Float, is Long, is String -> p.put(k, v)
            }
        }
        root.put("prefs", p)
        val shows = JSONArray()
        for (show in repo.allShows()) {
            val eps = JSONArray()
            repo.episodesOf(show.id).filter { it.positionMs > 0 || it.played || it.hidden }.forEach {
                eps.put(JSONObject().put("guid", it.guid).put("positionMs", it.positionMs).put("played", it.played).put("hidden", it.hidden))
            }
            shows.put(
                JSONObject()
                    .put("feedUrl", show.feedUrl)
                    .put("title", show.title)
                    .put("oldestFirst", prefs.getBoolean("oldest_first_${show.id}", false))
                    .put("showHidden", prefs.getBoolean("show_hidden_${show.id}", false))
                    .put("episodes", eps)
            )
        }
        root.put("shows", shows)
        write(uri, root.toString(2))
    }

    suspend fun importJson(uri: Uri): Result = withContext(Dispatchers.IO) {
        val root = JSONObject(read(uri))
        require(root.optString("app") == "voxa") { "That isn't a Voxa backup" }
        var added = 0; var existing = 0; var failed = 0
        val shows = root.optJSONArray("shows") ?: JSONArray()
        val edit = prefs.edit()
        val known = repo.allShows().associateBy { it.feedUrl }
        for (i in 0 until shows.length()) {
            val s = shows.getJSONObject(i)
            val url = s.getString("feedUrl")
            val id: Long = known[url]?.id?.also { existing++ } ?: try {
                repo.addShow(url).also { added++ }
            } catch (e: Exception) {
                failed++; continue
            }
            repo.setShowPosition(id, i)
            edit.putBoolean("oldest_first_$id", s.optBoolean("oldestFirst"))
            edit.putBoolean("show_hidden_$id", s.optBoolean("showHidden"))
            val eps = s.optJSONArray("episodes") ?: JSONArray()
            for (j in 0 until eps.length()) {
                val e = eps.getJSONObject(j)
                repo.applyEpisodeState(id, e.getString("guid"), e.optLong("positionMs"), e.optBoolean("played"), e.optBoolean("hidden"))
            }
        }
        root.optJSONObject("prefs")?.let { p ->
            p.keys().forEach { k ->
                if (k in globalKeys) when (val v = p.get(k)) {
                    is Boolean -> edit.putBoolean(k, v)
                    is Int -> if (k == "speed") edit.putFloat(k, v.toFloat()) else edit.putInt(k, v)
                    is Double -> edit.putFloat(k, v.toFloat())
                    is Long -> edit.putLong(k, v)
                    is String -> edit.putString(k, v)
                }
            }
        }
        edit.apply()
        Result(added, existing, failed)
    }

    suspend fun exportOpml(uri: Uri) = withContext(Dispatchers.IO) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val sb = StringBuilder("""<?xml version="1.0" encoding="UTF-8"?>""" + "\n")
        sb.append("""<opml version="2.0"><head><title>Voxa subscriptions</title></head><body>""" + "\n")
        repo.allShows().forEach { sb.append("""  <outline type="rss" text="${esc(it.title)}" title="${esc(it.title)}" xmlUrl="${esc(it.feedUrl)}"/>""" + "\n") }
        sb.append("</body></opml>\n")
        write(uri, sb.toString())
    }

    suspend fun importOpml(uri: Uri): Result = withContext(Dispatchers.IO) {
        val urls = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name.equals("outline", ignoreCase = true)) {
                    for (a in 0 until parser.attributeCount) {
                        if (parser.getAttributeName(a).equals("xmlUrl", ignoreCase = true)) urls += parser.getAttributeValue(a)
                    }
                }
                ev = parser.next()
            }
        } ?: error("Couldn't open that file")
        val known = repo.allShows().map { it.feedUrl }.toSet()
        var added = 0; var existing = 0; var failed = 0
        for (url in urls.distinct()) {
            if (url in known) { existing++; continue }
            try { repo.addShow(url); added++ } catch (e: Exception) { failed++ }
        }
        Result(added, existing, failed)
    }

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: error("Couldn't write the file")
    }

    private fun read(uri: Uri): String =
        context.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: error("Couldn't open that file")
}
