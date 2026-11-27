package dev.osianj.voxa.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {
    @Query("SELECT * FROM shows ORDER BY position ASC")
    fun observeShows(): Flow<List<Show>>

    @Query("SELECT * FROM shows ORDER BY position ASC")
    suspend fun getAll(): List<Show>

    @Query("SELECT * FROM shows WHERE id = :id")
    suspend fun getShow(id: Long): Show?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shows")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(show: Show): Long

    @Query("UPDATE shows SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    @Query("UPDATE shows SET title = :title, author = :author, description = :description, artworkUrl = :artworkUrl, lastRefreshed = :now WHERE id = :id")
    suspend fun updateMeta(id: Long, title: String, author: String, description: String, artworkUrl: String?, now: Long)

    @Query("DELETE FROM shows WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE deleted = 0 ORDER BY publishedAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE localPath IS NOT NULL AND deleted = 0 ORDER BY publishedAt DESC")
    fun observeDownloaded(): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    fun observeEpisode(id: Long): Flow<Episode?>

    @Query("SELECT showId, MAX(publishedAt) AS latest FROM episodes WHERE deleted = 0 GROUP BY showId")
    fun observeLatest(): Flow<List<ShowLatest>>

    /** All episodes including hidden ones (the UI filters), newest first. */
    @Query("SELECT * FROM episodes WHERE showId = :showId ORDER BY publishedAt DESC")
    fun observeEpisodes(showId: Long): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE showId = :showId AND deleted = 0 AND localPath IS NULL ORDER BY publishedAt DESC LIMIT :limit")
    suspend fun getUndownloaded(showId: Long, limit: Int): List<Episode>

    @Query("SELECT * FROM episodes WHERE showId = :showId AND localPath IS NOT NULL")
    suspend fun getDownloaded(showId: Long): List<Episode>

    @Query("UPDATE episodes SET localPath = :path WHERE id = :id")
    suspend fun setLocalPath(id: Long, path: String?)

    @Query("UPDATE episodes SET deleted = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisode(id: Long): Episode?

    @Query("SELECT guid FROM episodes WHERE showId = :showId")
    suspend fun getGuids(showId: Long): List<String>

    @Query("UPDATE episodes SET chaptersUrl = :url WHERE showId = :showId AND guid = :guid")
    suspend fun setChaptersUrl(showId: Long, guid: String, url: String?)

    @Query("UPDATE episodes SET positionMs = :positionMs, played = :played, deleted = :hidden WHERE showId = :showId AND guid = :guid")
    suspend fun setState(showId: Long, guid: String, positionMs: Long, played: Boolean, hidden: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(episodes: List<Episode>)

    @Query("UPDATE episodes SET positionMs = :positionMs WHERE id = :id")
    suspend fun savePosition(id: Long, positionMs: Long)

    @Query("UPDATE episodes SET positionMs = 0, played = 0 WHERE id = :id")
    suspend fun clearProgress(id: Long)

    @Query("UPDATE episodes SET played = :played WHERE id = :id")
    suspend fun setPlayed(id: Long, played: Boolean)
}

@Database(entities = [Show::class, Episode::class], version = 2, exportSchema = false)
abstract class VoxaDatabase : RoomDatabase() {
    abstract fun showDao(): ShowDao
    abstract fun episodeDao(): EpisodeDao

    companion object {
        fun build(context: Context): VoxaDatabase =
            Room.databaseBuilder(context, VoxaDatabase::class.java, "voxa.db").addMigrations(MIGRATION_1_2).build()
    }
}

val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN chaptersUrl TEXT")
    }
}
