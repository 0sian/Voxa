package dev.osianj.voxa.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Minimal RSS 2.0 + iTunes namespace parser. No search, no directory: Voxa only ever
 * talks to feed URLs the user pasted in.
 */
object RssParser {

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title = ""
        var author = ""
        var description = ""
        var artwork: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        var inItem = false
        var iTitle = ""
        var iDesc = ""
        var iGuid = ""
        var iAudio = ""
        var iDate = 0L
        var iDuration = 0
        var iChapters: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        iTitle = ""; iDesc = ""; iGuid = ""; iAudio = ""; iDate = 0L; iDuration = 0; iChapters = null
                    }
                    "title" -> {
                        val text = parser.nextTextSafe()
                        if (inItem) iTitle = text else if (title.isEmpty()) title = text
                    }
                    "description", "itunes:summary" -> {
                        val text = parser.nextTextSafe()
                        if (inItem) { if (iDesc.isEmpty()) iDesc = text } else if (description.isEmpty()) description = text
                    }
                    "itunes:author" -> {
                        val text = parser.nextTextSafe()
                        if (!inItem && author.isEmpty()) author = text
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (!inItem && artwork == null) artwork = href
                    }
                    "url" -> {
                        val text = parser.nextTextSafe()
                        if (!inItem && artwork == null && text.isNotBlank()) artwork = text
                    }
                    "guid" -> if (inItem) iGuid = parser.nextTextSafe()
                    "enclosure" -> if (inItem) iAudio = parser.getAttributeValue(null, "url") ?: ""
                    "pubDate" -> if (inItem) iDate = parseDate(parser.nextTextSafe())
                    "podcast:chapters" -> if (inItem) iChapters = parser.getAttributeValue(null, "url")
                    "itunes:duration" -> if (inItem) iDuration = parseDuration(parser.nextTextSafe())
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    if (iAudio.isNotBlank()) {
                        episodes += ParsedEpisode(
                            guid = iGuid.ifBlank { iAudio },
                            title = iTitle,
                            description = iDesc,
                            audioUrl = iAudio,
                            publishedAt = iDate,
                            durationSec = iDuration,
                            chaptersUrl = iChapters,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return ParsedFeed(title, author, description, artwork, episodes)
    }

    private fun XmlPullParser.nextTextSafe(): String =
        try { nextText().trim() } catch (e: Exception) { "" }

    private fun parseDate(raw: String): Long {
        val formats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z")
        for (f in formats) {
            try {
                return SimpleDateFormat(f, Locale.US).parse(raw)?.time ?: continue
            } catch (_: Exception) { }
        }
        return 0L
    }

    /** Handles "3600", "59:30" and "1:02:03". */
    private fun parseDuration(raw: String): Int {
        val parts = raw.split(":").mapNotNull { it.trim().toIntOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0
        }
    }
}
