package com.codetrio.spatialflow.data.innertube

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Controller responsible for managing background playlist synchronization operations
 * with locking, Mutex safety, and automatic search resolution for non-YouTube tracks.
 */
object PlaylistSyncManager {

    private const val TAG = "PlaylistSyncManager"

    // Global semaphore to throttle concurrent bulk InnerTube HTTP operations (max 15 concurrent)
    private val globalOperationSemaphore = Semaphore(15)

    // Per-playlist mutex map to guarantee strict sequential ordering for single playlist modifications
    private val playlistMutexMap = ConcurrentHashMap<String, Mutex>()

    private fun getPlaylistMutex(playlistId: String): Mutex {
        val cleanId = playlistId.removePrefix("VL")
        return playlistMutexMap.computeIfAbsent(cleanId) { Mutex() }
    }

    /**
     * Add a song (either by existing videoId or by resolving title + artist) to a YouTube Music playlist.
     */
    suspend fun addSongToPlaylist(
        playlistId: String,
        title: String,
        artist: String,
        videoId: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val mutex = getPlaylistMutex(cleanId)

        globalOperationSemaphore.withPermit {
            mutex.withLock {
                try {
                    val resolvedVideoId = if (!videoId.isNullOrBlank()) {
                        videoId
                    } else {
                        Log.d(TAG, "Resolving videoId for local/non-YouTube track: '$title - $artist'")
                        YouTubeMusic.resolveVideoIdForTrack(title, artist).getOrElse { err ->
                            Log.e(TAG, "Failed to resolve videoId for track '$title': ${err.message}")
                            return@withContext Result.failure(err)
                        }
                    }

                    Log.d(TAG, "Executing InnerTube addToPlaylist for playlist $cleanId with videoId $resolvedVideoId")
                    YouTubeMusic.addToPlaylist(cleanId, listOf(resolvedVideoId))
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding song to YouTube Music playlist $cleanId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Add multiple songs to a YouTube Music playlist safely.
     */
    suspend fun addSongsToPlaylist(
        playlistId: String,
        videoIds: List<String>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (videoIds.isEmpty()) return@withContext Result.success(true)
        val cleanId = playlistId.removePrefix("VL")
        val mutex = getPlaylistMutex(cleanId)

        globalOperationSemaphore.withPermit {
            mutex.withLock {
                try {
                    Log.d(TAG, "Executing InnerTube addToPlaylist for playlist $cleanId (${videoIds.size} songs)")
                    YouTubeMusic.addToPlaylist(cleanId, videoIds)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding multiple songs to playlist $cleanId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Create a new YouTube Music playlist.
     */
    suspend fun createPlaylist(
        title: String,
        description: String = "",
        privacyStatus: String = "PRIVATE",
        initialVideoIds: List<String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        globalOperationSemaphore.withPermit {
            try {
                Log.d(TAG, "Creating new YouTube Music playlist '$title'")
                YouTubeMusic.createPlaylist(title, description, privacyStatus, initialVideoIds ?: emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "Error creating playlist '$title': ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Remove a song from a YouTube Music playlist.
     */
    suspend fun removeSongFromPlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val mutex = getPlaylistMutex(cleanId)

        globalOperationSemaphore.withPermit {
            mutex.withLock {
                try {
                    Log.d(TAG, "Removing videoId $videoId (setVideoId: $setVideoId) from playlist $cleanId")
                    YouTubeMusic.removeFromPlaylist(cleanId, videoId, setVideoId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing song from playlist $cleanId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Rename a YouTube Music playlist.
     */
    suspend fun renamePlaylist(
        playlistId: String,
        newName: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val mutex = getPlaylistMutex(cleanId)

        globalOperationSemaphore.withPermit {
            mutex.withLock {
                try {
                    Log.d(TAG, "Renaming playlist $cleanId to '$newName'")
                    YouTubeMusic.renamePlaylist(cleanId, newName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error renaming playlist $cleanId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Delete a YouTube Music playlist.
     */
    suspend fun deletePlaylist(
        playlistId: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("VL")
        val mutex = getPlaylistMutex(cleanId)

        globalOperationSemaphore.withPermit {
            mutex.withLock {
                try {
                    Log.d(TAG, "Deleting playlist $cleanId")
                    YouTubeMusic.deletePlaylist(cleanId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting playlist $cleanId: ${e.message}", e)
                    Result.failure(e)
                }
            }
        }
    }
}
