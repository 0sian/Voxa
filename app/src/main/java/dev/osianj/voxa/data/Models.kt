package dev.osianj.voxa.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A subscribed podcast. [position] is the user-controlled order (the "edit order" feature). */
@Entity(tableName = "shows", indices = [Index(value = ["feedUrl"], unique = true)])
data class Show(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val feedUrl: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val artworkUrl: String? = null,
    val position: Int = 0,
    val lastRefreshed: Long = 0,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [ForeignKey(Show::class, ["id"], ["showId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("showId"), Index(value = ["showId", "guid"], unique = true)],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val showId: Long,
    val guid: String,
    val title: String,
    val description: String = "",
    val audioUrl: String,
    val publishedAt: Long = 0,
    val durationSec: Int = 0,
    val positionMs: Long = 0,
    val played: Boolean = false,
    /** Absolute path of the downloaded audio file, null if streaming only. */
    val localPath: String? = null,
    /**
     * Hidden from the normal episode list (shown dimmed in Edit mode) and skipped by downloads.
     * The column keeps its old name "deleted" so existing installs need no migration.
     */
    @ColumnInfo(name = "deleted") val hidden: Boolean = false,
    /** Podcasting 2.0 chapters JSON, if the feed provides one. */
    val chaptersUrl: String? = null,
)

/** Which episodes a bulk download should fetch. */
sealed interface DownloadScope {
    data class Latest(val count: Int) : DownloadScope
    data object Unplayed : DownloadScope
    data object All : DownloadScope

    fun encode(): String = when (this) {
        is Latest -> "latest:$count"
        Unplayed -> "unplayed"
        All -> "all"
    }

    companion object {
        fun decode(s: String?): DownloadScope = when {
            s == "all" -> All
            s == "unplayed" -> Unplayed
            s != null && s.startsWith("latest:") -> Latest(s.removePrefix("latest:").toIntOrNull() ?: 5)
            else -> Latest(5)
        }
    }
}

/** Newest non-hidden episode date per show, for "Updated 2d ago". */
data class ShowLatest(val showId: Long, val latest: Long)

/** What the RSS parser returns before anything touches the database. */
data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String?,
    val episodes: List<ParsedEpisode>,
)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishedAt: Long,
    val durationSec: Int,
    val chaptersUrl: String? = null,
)

data class Chapter(val startMs: Long, val title: String)
