package com.codetrio.spatialflow.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.codetrio.spatialflow.data.lastfm.ScrobbleItem

@Entity(tableName = "lastfm_scrobbles")
data class LastFmScrobbleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artist: String,
    val title: String,
    val album: String?,
    val durationSec: Long,
    val timestampSec: Long,
    val addedAtMs: Long = System.currentTimeMillis()
)

fun LastFmScrobbleEntity.toScrobbleItem(): ScrobbleItem = ScrobbleItem(
    artist = this.artist,
    title = this.title,
    album = this.album,
    durationSec = this.durationSec,
    timestampSec = this.timestampSec
)

fun ScrobbleItem.toEntity(): LastFmScrobbleEntity = LastFmScrobbleEntity(
    artist = this.artist,
    title = this.title,
    album = this.album,
    durationSec = this.durationSec,
    timestampSec = this.timestampSec
)
