package dev.osianj.voxa.data

/** Turns the long title in an audiobook's tags into one that fits on a card. */
object AudiobookTitles {
    private val bracketed = Regex("\\s*[\\(\\[\\{][^\\(\\)\\[\\]\\{\\}]*[\\)\\]\\}]")
    private val format = Regex("(?i)\\s*[-–—,:]?\\s*\\b(unabridged|abridged|audiobook|audio book)\\b\\s*$")

    /** Short, display-ready title. Never returns blank: falls back to the input. */
    fun short(raw: String): String {
        var t = raw.replace('_', ' ')
        var prev: String
        do { prev = t; t = t.replace(bracketed, "") } while (t != prev)
        t = t.replace(format, "").replace(Regex("\\s+"), " ").trim()
        // "Title: A Long Subtitle About The Book" -> "Title". A short subtitle ("Star Wars: Thrawn") is part of the name.
        val colon = t.indexOf(": ")
        if (colon > 0) {
            val head = t.substring(0, colon).trim()
            val subtitle = t.substring(colon + 2).trim()
            if (head.isNotEmpty() && subtitle.split(' ').size >= 4) t = head
        }
        return t.trim(' ', '-', '–', '—', '.', ',', ':', ';').ifBlank { raw.trim() }
    }

    /** The full cleaned title (brackets and format words removed, subtitle kept), for the About page. */
    fun full(raw: String): String {
        var t = raw.replace('_', ' ')
        var prev: String
        do { prev = t; t = t.replace(bracketed, "") } while (t != prev)
        return t.replace(format, "").replace(Regex("\\s+"), " ").trim().ifBlank { raw.trim() }
    }
}
