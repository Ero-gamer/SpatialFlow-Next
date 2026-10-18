package com.codetrio.spatialflow.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC, addedAt ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>): List<Long>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: String)

    @Query("UPDATE playlist_songs SET lufs = :lufs WHERE songId = :songId")
    suspend fun updateSongLufs(songId: String, lufs: Float)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deleteAllSongsInPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun getSongCountForPlaylistSync(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEvent(event: HistoryEventEntity): Long

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    fun getAllHistoryEventsFlow(): Flow<List<HistoryEventEntity>>

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC")
    suspend fun getAllHistoryEventsSync(): List<HistoryEventEntity>

    @Query("SELECT * FROM history_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistorySync(limit: Int): List<HistoryEventEntity>

    @Query("DELETE FROM history_events")
    suspend fun clearAllHistory()

    // Aggregate queries for Listening Recap (Instructions #12-16)
    @Query("SELECT COUNT(*) FROM history_events WHERE timestamp >= :since")
    fun getTotalPlaysCountFlow(since: Long = 0L): Flow<Int>

    @Query("SELECT COALESCE(SUM(duration), 0) FROM history_events WHERE timestamp >= :since")
    fun getTotalListeningDurationFlow(since: Long = 0L): Flow<Long>

    @Query("SELECT songId, title, artist, thumbnailUrl, COUNT(*) AS count FROM history_events WHERE timestamp >= :since GROUP BY songId ORDER BY count DESC LIMIT :limit")
    fun getTopSongsFlow(limit: Int = 10, since: Long = 0L): Flow<List<TopSongRow>>

    @Query("SELECT artist, COUNT(*) AS count, MIN(thumbnailUrl) AS thumbnailUrl FROM history_events WHERE timestamp >= :since GROUP BY artist ORDER BY count DESC LIMIT :limit")
    fun getTopArtistsFlow(limit: Int = 5, since: Long = 0L): Flow<List<TopArtistRow>>

    @Query("SELECT hourOfDay FROM history_events WHERE timestamp >= :since GROUP BY hourOfDay ORDER BY COUNT(*) DESC LIMIT 1")
    fun getPeakHourFlow(since: Long = 0L): Flow<Int?>
}

data class TopSongRow(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val count: Int
)

data class TopArtistRow(
    val artist: String,
    val count: Int,
    val thumbnailUrl: String?
)
