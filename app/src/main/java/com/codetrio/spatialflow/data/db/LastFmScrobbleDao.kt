package com.codetrio.spatialflow.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LastFmScrobbleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScrobble(scrobble: LastFmScrobbleEntity): Long

    @Query("SELECT * FROM lastfm_scrobbles ORDER BY timestampSec ASC")
    fun getPendingScrobblesFlow(): Flow<List<LastFmScrobbleEntity>>

    @Query("SELECT * FROM lastfm_scrobbles ORDER BY timestampSec ASC")
    suspend fun getPendingScrobblesList(): List<LastFmScrobbleEntity>

    @Query("SELECT COUNT(*) FROM lastfm_scrobbles")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM lastfm_scrobbles")
    suspend fun getPendingCount(): Int

    @Query("DELETE FROM lastfm_scrobbles WHERE id IN (:ids)")
    suspend fun deleteScrobbles(ids: List<Long>)

    @Query("DELETE FROM lastfm_scrobbles")
    suspend fun clearAll()
}
