package com.codetrio.spatialflow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        HistoryEventEntity::class,
        LastFmScrobbleEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun lastFmScrobbleDao(): LastFmScrobbleDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "spatialflow_music_database"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(false)
                .build()
                INSTANCE = instance
                instance
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN lufs REAL DEFAULT NULL")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlist_songs ADD COLUMN isExplicit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE history_events ADD COLUMN isExplicit INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lastfm_scrobbles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        artist TEXT NOT NULL,
                        title TEXT NOT NULL,
                        album TEXT,
                        durationSec INTEGER NOT NULL,
                        timestampSec INTEGER NOT NULL,
                        addedAtMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_events_timestamp ON history_events(timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_events_songId ON history_events(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_history_events_artist ON history_events(artist)")
            }
        }
    }
}
