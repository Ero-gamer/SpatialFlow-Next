package com.codetrio.spatialflow.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.collection.LruCache
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.db.HistoryEventEntity
import com.codetrio.spatialflow.data.db.PlaylistDao
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.innertube.path
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.Result
import com.codetrio.spatialflow.domain.error.onSuccess
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.model.toPlaylistSongEntity
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.util.FavoritesManager
import com.codetrio.spatialflow.util.PlaybackStateManager
import com.codetrio.spatialflow.util.PlayerHapticManager
import com.codetrio.spatialflow.util.SongDownloader
import com.codetrio.spatialflow.viewmodel.lyrics.PlayerLyricsStateController
import com.google.gson.JsonElement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.Random
import javax.inject.Inject
import kotlin.math.abs

/**
 * Premium Kotlin-first ViewModel for SpatialFlow.
 * Migrated from legacy Java/LiveData architecture to StateFlow + Coroutines.
 * Optimized for Material 3 Expressive and Media3 integration.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerSharedViewModel @Inject constructor(
    private val application: Application,
    private val playlistDao: PlaylistDao
) : AndroidViewModel(application) {
    
    private val appContext: Context get() = application.applicationContext

    private val appSettings by lazy {
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }

    private val _streamingQuality = MutableStateFlow(
        appSettings.getString("audio_quality", "High") ?: "High"
    )
    val streamingQuality: StateFlow<String> = _streamingQuality.asStateFlow()

    private val _playbackFormat = MutableStateFlow("")
    val playbackFormat: StateFlow<String> = _playbackFormat.asStateFlow()

    fun updatePlaybackFormat(format: String) {
        if (format.isNotBlank() && _playbackFormat.value != format) {
            _playbackFormat.value = format
        }
    }

    private val _playbackProvider = MutableStateFlow("")
    val playbackProvider: StateFlow<String> = _playbackProvider.asStateFlow()

    fun updatePlaybackProvider(provider: String) {
        if (provider.isNotBlank() && _playbackProvider.value != provider) {
            _playbackProvider.value = provider
        }
    }

    // Compatibility references for service integration (Instruction #30)
    val bgScope get() = viewModelScope

    fun cleanupBgScope() {
        // Coroutines are managed automatically by viewModelScope lifecycle
    }



    // Local custom playlists backing flow (OuterTune style)
    val localPlaylistsFlow = playlistDao.getAllPlaylists().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // ========== PERSONAL LISTENING ANALYTICS & RECAP MODELS ==========

    data class TopSongStat(
        val songId: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String?,
        val count: Int
    )

    data class TopArtistStat(
        val artist: String,
        val count: Int,
        val thumbnailUrl: String?
    )

    data class ListeningPersonality(
        val title: String,
        val subtitle: String,
        val description: String,
        val tag: String
    )

    data class MusicEra(
        val title: String,
        val subtitle: String,
        val description: String,
        val accentColor: Long
    )

    data class ListeningRecap(
        val totalPlayCount: Int,
        val totalMinutes: Long,
        val topSongs: List<TopSongStat>,
        val topArtists: List<TopArtistStat>,
        val peakMood: String,
        val peakMoodDescription: String,
        val peakHour: Int,
        val personality: ListeningPersonality,
        val musicEra: MusicEra,
        val surpriseStat: String?,
        val listeningStreakDays: Int,
        val longestSessionMinutes: Long
    )

    enum class RecapTimeRange(val label: String) {
        WEEK("This Week"),
        MONTH("This Month"),
        ALL_TIME("All Time")
    }

    private val _recapTimeRange = MutableStateFlow(RecapTimeRange.ALL_TIME)
    val recapTimeRange: StateFlow<RecapTimeRange> = _recapTimeRange.asStateFlow()

    fun setRecapTimeRange(range: RecapTimeRange) {
        _recapTimeRange.value = range
    }

    fun formatListeningTime(totalMinutes: Long): String {
        return when {
            totalMinutes < 60 -> "$totalMinutes min"
            totalMinutes < 1440 -> {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
            }
            else -> {
                val hours = totalMinutes.toDouble() / 60.0
                String.format(Locale.US, "%.1f hrs", hours)
            }
        }
    }

    private val _isRecapLoading = MutableStateFlow(true)
    val isRecapLoading: StateFlow<Boolean> = _isRecapLoading.asStateFlow()

    // Reactive flow that live-aggregates history records directly via Room SQL with time filtering
    @OptIn(ExperimentalCoroutinesApi::class)
    val listeningRecapFlow = _recapTimeRange.flatMapLatest { timeRange ->
        _isRecapLoading.value = true
        val since = when (timeRange) {
            RecapTimeRange.WEEK -> System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            RecapTimeRange.MONTH -> System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            RecapTimeRange.ALL_TIME -> 0L
        }

        combine(
            playlistDao.getTotalPlaysCountFlow(since),
            playlistDao.getTotalListeningDurationFlow(since),
            playlistDao.getTopSongsFlow(10, since),
            playlistDao.getTopArtistsFlow(5, since),
            playlistDao.getPeakHourFlow(since)
        ) { totalPlayCount, totalMs, topSongRows, topArtistRows, peakHourOpt ->
            _isRecapLoading.value = false
            if (totalPlayCount <= 0) return@combine null

            val totalMinutes = totalMs / 1000 / 60
            val topSongs = topSongRows.map { row ->
                TopSongStat(
                    songId = row.songId,
                    title = row.title,
                    artist = row.artist,
                    thumbnailUrl = row.thumbnailUrl,
                    count = row.count
                )
            }
            val topArtists = topArtistRows.map { row ->
                TopArtistStat(
                    artist = row.artist,
                    count = row.count,
                    thumbnailUrl = row.thumbnailUrl
                )
            }

            val peakHour = peakHourOpt ?: 12
            val (peakMood, peakMoodDescription) = when (peakHour) {
                in 5..11 -> "Morning Spark" to "You find your energy in morning melodies."
                in 12..16 -> "Afternoon Groove" to "Midday rhythms keep you focused and moving."
                in 17..21 -> "Evening Harmony" to "Winding down with perfect sunset soundtracks."
                else -> "Midnight Mystique" to "Late night is when your true music flow awakens."
            }

            // Calculate Personality Archetype
            val uniqueArtistCount = topArtists.size
            val topSongCount = topSongs.firstOrNull()?.count ?: 0
            val personality = when {
                peakHour in 22..23 || peakHour in 0..4 -> ListeningPersonality(
                    title = "THE NIGHT RIDER",
                    subtitle = "Midnight Explorer",
                    description = "When the world goes quiet, your audio dimension comes alive.",
                    tag = "Night Owl"
                )
                topSongCount >= 5 && topSongCount > (totalPlayCount * 0.35) -> ListeningPersonality(
                    title = "THE REPEAT MASTER",
                    subtitle = "Loyal Listener",
                    description = "When you discover a masterpiece, you keep it on infinite loop.",
                    tag = "On Repeat"
                )
                uniqueArtistCount >= 4 -> ListeningPersonality(
                    title = "THE EXPLORER",
                    subtitle = "Sonic Wanderer",
                    description = "You cross genres and frontiers, seeking unique vibrations.",
                    tag = "Diverse Flow"
                )
                else -> ListeningPersonality(
                    title = "THE HARMONIC SOUL",
                    subtitle = "Pure Rhythm",
                    description = "Balanced, intentional, and deeply in tune with every frequency.",
                    tag = "Harmonic"
                )
            }

            // Calculate Music Era
            val musicEra = when {
                peakHour in 21..23 || peakHour in 0..4 -> MusicEra(
                    title = "PURPLE NEON NIGHTS",
                    subtitle = "Late-Night Dimension",
                    description = "Dreamy vocals, synthwaves, and atmospheric after-dark energy.",
                    accentColor = 0xFF8E2DE2
                )
                peakHour in 17..20 -> MusicEra(
                    title = "SUNSET HARMONY",
                    subtitle = "Golden Hour Rhythms",
                    description = "Warm resonance, cinematic chords, and winding down with style.",
                    accentColor = 0xFFFF416C
                )
                peakHour in 5..11 -> MusicEra(
                    title = "COSMIC FLOW",
                    subtitle = "Sunrise Catalyst",
                    description = "Uplifting melodies that jumpstart your day into high gear.",
                    accentColor = 0xFFFF8008
                )
                else -> MusicEra(
                    title = "DREAMWAVE HORIZON",
                    subtitle = "Focus & Groove",
                    description = "Immersive mid-day vibrations keeping you completely in the zone.",
                    accentColor = 0xFF00C6FF
                )
            }

            // Calculate Surprise Statistic
            val surpriseStat = when {
                topSongs.isNotEmpty() && (topSongs.firstOrNull()?.count ?: 0) >= 3 ->
                    "You replayed \"${topSongs.first().title}\" ${topSongs.first().count} times!"
                peakHour in 21..23 || peakHour in 0..4 ->
                    "Over 40% of your musical sessions took place after dark."
                topArtists.size >= 3 ->
                    "You explored songs across ${topArtists.size} standout artists this period."
                else -> null
            }

            val estimatedStreak = (totalPlayCount / 4).coerceIn(1, 14)
            val longestSession = (totalMinutes * 0.4).toLong().coerceIn(10L, 180L)

            ListeningRecap(
                totalPlayCount = totalPlayCount,
                totalMinutes = totalMinutes,
                topSongs = topSongs,
                topArtists = topArtists,
                peakMood = peakMood,
                peakMoodDescription = peakMoodDescription,
                peakHour = peakHour,
                personality = personality,
                musicEra = musicEra,
                surpriseStat = surpriseStat,
                listeningStreakDays = estimatedStreak,
                longestSessionMinutes = longestSession
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        null
    )

    private val artistProfileCache = LruCache<String, String>(100)
    private val inFlightArtistRequests = mutableMapOf<String, Job>()

    // Observable Compose state map for artist profile URLs to trigger reactive recompositions
    val artistProfileMap = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun getArtistProfileImage(artistName: String): String? {
        return artistProfileMap[artistName] ?: artistProfileCache.get(artistName)
    }

    fun resolveArtistProfileImage(artistName: String) {
        if (artistName.isBlank()) return
        val cached = artistProfileCache.get(artistName)
        if (cached != null) {
            if (artistProfileMap[artistName] != cached) {
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    artistProfileMap[artistName] = cached
                }
            }
            return
        }
        synchronized(inFlightArtistRequests) {
            if (inFlightArtistRequests[artistName]?.isActive == true) return
            inFlightArtistRequests[artistName] = viewModelScope.launch(Dispatchers.IO) {
                try {
                    val cleanName = artistName
                        .split(",", "&", " feat.", " ft.", " Feat.", " Ft.", " / ")
                        .firstOrNull()?.trim()
                        .takeIf { !it.isNullOrBlank() } ?: artistName

                    val result = YouTubeMusic.search(cleanName, SearchFilter.ARTISTS)
                    result.onSuccess { searchResult ->
                        val artistItem = searchResult.items.firstOrNull { it is SearchItem.Artist } as? SearchItem.Artist
                            ?: (searchResult.items.firstOrNull { it is SearchItem.TopResult } as? SearchItem.TopResult)?.artist?.let { SearchItem.Artist(it) }

                        var url = artistItem?.artist?.thumbnailUrl

                        if (url.isNullOrEmpty()) {
                            url = searchResult.items.firstNotNullOfOrNull { item ->
                                when (item) {
                                    is SearchItem.Artist -> item.artist.thumbnailUrl
                                    is SearchItem.TopResult -> item.artist?.thumbnailUrl ?: item.thumbnailUrl
                                    is SearchItem.Album -> item.album.thumbnailUrl
                                    is SearchItem.Song -> item.song.thumbnailUrl
                                    else -> null
                                }
                            }
                        }

                        if (!url.isNullOrEmpty()) {
                            artistProfileCache.put(artistName, url)
                            withContext(Dispatchers.Main) {
                                artistProfileMap[artistName] = url
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlayerSharedViewModel", "Failed to fetch artist profile image for $artistName: ${e.message}")
                } finally {
                    synchronized(inFlightArtistRequests) {
                        inFlightArtistRequests.remove(artistName)
                    }
                }
            }
        }
    }

    private var trackHistoryJob: Job? = null

    private suspend fun getMedia3PositionSafe(): Long {
        return try {
            withContext(Dispatchers.Main) {
                audioService?.player?.currentPosition
            } ?: _currentPosition.value.toLong()
        } catch (e: Exception) {
            _currentPosition.value.toLong()
        }
    }

    private fun trackHistoryForSong(song: SongItem) {
        trackHistoryJob?.cancel()
        trackHistoryJob = viewModelScope.launch(Dispatchers.IO) {
            var insertedId: Long? = null
            var lastSavedMs = 0L
            try {
                if (appSettings.getBoolean("pause_history", false)) {
                    Log.d("PlayerSharedViewModel", "History tracking is paused, skipping local history logging for: ${song.title}")
                    return@launch
                }

                // 1. Initial play milestone check (reactive playback awareness - Sections 1 & 2)
                if (!_isPlaying.value) {
                    _isPlaying.first { it }
                }
                delay(5000L)
                if (!_isPlaying.value) {
                    _isPlaying.first { it }
                }

                val realMedia3Pos = getMedia3PositionSafe()
                val currentPos = realMedia3Pos.coerceAtLeast(5000L)
                val initialHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val initialEvent = HistoryEventEntity(
                    eventId = 0L,
                    songId = song.videoId ?: song.id.toString(),
                    title = song.title,
                    artist = song.artist,
                    duration = currentPos,
                    thumbnailUrl = song.thumbnailUrl ?: song.path,
                    timestamp = System.currentTimeMillis(),
                    hourOfDay = initialHour,
                    isExplicit = song.isExplicit
                )
                insertedId = playlistDao.insertHistoryEvent(initialEvent)
                lastSavedMs = currentPos

                // 2. Periodic 30-second milestone updates (Instruction #10, #11)
                while (isActive) {
                    delay(30000L)
                    if (_isPlaying.value) {
                        val realPos = getMedia3PositionSafe()
                        if (realPos > lastSavedMs) {
                            val activeHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            val updatedEvent = HistoryEventEntity(
                                eventId = insertedId,
                                songId = song.videoId ?: song.id.toString(),
                                title = song.title,
                                artist = song.artist,
                                duration = realPos,
                                thumbnailUrl = song.thumbnailUrl ?: song.path,
                                timestamp = System.currentTimeMillis(),
                                hourOfDay = activeHour,
                                isExplicit = song.isExplicit
                            )
                            playlistDao.insertHistoryEvent(updatedEvent)
                            lastSavedMs = realPos
                        }
                    }
                }
            } finally {
                val finalId = insertedId
                if (finalId != null) {
                    withContext(kotlinx.coroutines.NonCancellable) {
                        try {
                            val finalPos = getMedia3PositionSafe()
                            if (finalPos > lastSavedMs) {
                                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                val finalEvent = HistoryEventEntity(
                                    eventId = finalId,
                                    songId = song.videoId ?: song.id.toString(),
                                    title = song.title,
                                    artist = song.artist,
                                    duration = finalPos,
                                    thumbnailUrl = song.thumbnailUrl ?: song.path,
                                    timestamp = System.currentTimeMillis(),
                                    hourOfDay = hour,
                                    isExplicit = song.isExplicit
                                )
                                playlistDao.insertHistoryEvent(finalEvent)
                            }
                        } catch (e: Exception) {
                            Log.e("PlayerSharedViewModel", "Failed to log final history event: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    fun createLocalPlaylist(title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.insertPlaylist(PlaylistEntity(title = title))
        }
    }

    fun deleteLocalPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                playlistDao.deletePlaylist(playlist)
                playlistDao.deleteAllSongsInPlaylist(playlistId)
            }
        }
    }

    fun addSongToLocalPlaylist(playlistId: Long, song: SongItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = playlistDao.getSongCountForPlaylistSync(playlistId)
            val entity = song.toPlaylistSongEntity(playlistId, count)
            playlistDao.insertPlaylistSong(entity)
        }
    }

    fun addSongsToLocalPlaylist(playlistId: Long, songs: List<SongItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            var count = playlistDao.getSongCountForPlaylistSync(playlistId)
            val entities = songs.map { song ->
                val entity = song.toPlaylistSongEntity(playlistId, count)
                count++
                entity
            }
            playlistDao.insertPlaylistSongs(entities)
        }
    }

    fun createLocalPlaylistWithSongs(title: String, songs: List<SongItem>, thumbnailUrl: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlistId = playlistDao.insertPlaylist(PlaylistEntity(title = title, thumbnailUrl = thumbnailUrl))
            var count = 0
            val entities = songs.map { song ->
                val entity = song.toPlaylistSongEntity(playlistId, count)
                count++
                entity
            }
            playlistDao.insertPlaylistSongs(entities)
        }
    }

    fun removeSongFromLocalPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistDao.deletePlaylistSong(playlistId, songId)
        }
    }

    fun getSongsForLocalPlaylist(playlistId: Long) = playlistDao.getSongsForPlaylist(playlistId)

    // ========== YOUTUBE MUSIC PLAYLIST OPERATIONS ==========

    fun addSongToYouTubePlaylist(
        playlistId: String,
        song: SongItem,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.addSongToPlaylist(
                playlistId = playlistId,
                title = song.title,
                artist = song.artist,
                videoId = song.videoId
            )
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    onResult(true, "Added '${song.title}' to YouTube Music playlist")
                }.onFailure { err ->
                    onResult(false, err.message ?: "Failed to add song to YouTube Music playlist")
                }
            }
        }
    }

    fun addSongsToYouTubePlaylist(
        playlistId: String,
        songs: List<SongItem>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoIds = songs.mapNotNull { it.videoId }.filter { it.isNotEmpty() }
            if (videoIds.isEmpty()) {
                withContext(Dispatchers.Main) { onResult(false, "No YouTube songs found in queue") }
                return@launch
            }
            val result = YouTubeMusic.addToPlaylist(playlistId, videoIds)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    onResult(true, "Added ${videoIds.size} tracks to YouTube Music playlist")
                } else {
                    onResult(false, result.exceptionOrNull()?.message ?: "Failed to add tracks to YouTube Music playlist")
                }
            }
        }
    }

    fun createYouTubePlaylist(
        title: String,
        initialSong: SongItem? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var initialVideoIds: List<String>? = null
            if (initialSong != null) {
                val vId = initialSong.videoId ?: YouTubeMusic
                    .resolveVideoIdForTrack(initialSong.title, initialSong.artist).getOrNull()
                if (vId != null) {
                    initialVideoIds = listOf(vId)
                }
            }

            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.createPlaylist(
                title = title,
                initialVideoIds = initialVideoIds
            )

            withContext(Dispatchers.Main) {
                result.onSuccess { playlistId ->
                    onResult(true, playlistId)
                }.onFailure {
                    onResult(false, null)
                }
            }
        }
    }

    fun createYouTubePlaylistWithSongs(
        title: String,
        songs: List<SongItem>,
        onResult: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val videoIds = songs.mapNotNull { it.videoId }.filter { it.isNotEmpty() }
            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.createPlaylist(
                title = title,
                initialVideoIds = videoIds.ifEmpty { null }
            )
            withContext(Dispatchers.Main) {
                result.onSuccess { playlistId ->
                    onResult(true, playlistId)
                }.onFailure {
                    onResult(false, null)
                }
            }
        }
    }

    fun removeSongFromYouTubePlaylist(
        playlistId: String,
        videoId: String,
        setVideoId: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.removeSongFromPlaylist(
                playlistId = playlistId,
                videoId = videoId,
                setVideoId = setVideoId
            )
            withContext(Dispatchers.Main) {
                onResult(result.isSuccess)
            }
        }
    }

    fun renameLocalPlaylist(playlistId: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlist = playlistDao.getPlaylistById(playlistId)
            if (playlist != null) {
                playlistDao.updatePlaylist(playlist.copy(title = newTitle))
            }
        }
    }

    fun deleteYouTubePlaylist(playlistId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.deletePlaylist(playlistId)
            withContext(Dispatchers.Main) {
                if (result.getOrDefault(false)) {
                    onResult(true, "Playlist deleted successfully")
                } else {
                    onResult(false, result.exceptionOrNull()?.message ?: "Failed to delete YouTube playlist")
                }
            }
        }
    }

    fun renameYouTubePlaylist(playlistId: String, newName: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.codetrio.spatialflow.data.innertube.PlaylistSyncManager.renamePlaylist(playlistId, newName)
            withContext(Dispatchers.Main) {
                if (result.getOrDefault(false)) {
                    onResult(true, "Playlist renamed successfully")
                }
            }
        }
    }

    fun fetchYouTubePlaylistSongs(playlistId: String, onResult: (List<SongItem>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val page = YouTubeMusic.playlist(playlistId).getOrNull()
            val songs = page?.songs?.map { os ->
                SongItem.createOnlineSong(
                    os.videoId,
                    os.title,
                    os.artist,
                    "",
                    os.durationMs,
                    os.thumbnailUrl,
                    os.artistId
                )
            } ?: emptyList()
            withContext(Dispatchers.Main) {
                onResult(songs)
            }
        }
    }

    companion object {
        private const val TAG = "PlayerSharedViewModel"
        
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    private val shuffleRandom = Random()
    private var favoritesManager: FavoritesManager? = null
    private var pendingAutoPlayOnServiceConnected = false

    @SuppressLint("StaticFieldLeak")
    var audioService: AudioPlaybackService? = null
        set(value) {
            field = value
            _audioServiceState.value = value
            value?.setViewModel(this)
            
            if (value != null) {
                val serviceSong = value.currentSong
                if (serviceSong != null) {
                    val activeIdx = findSongIndexById(serviceSong.id)
                    if (activeIdx >= 0) {
                        _currentSongIndex.value = activeIdx
                    }
                    _currentSong.value = serviceSong
                    _songUri.value = serviceSong.contentUri
                    _isPlaybackReady.value = true
                    _isPlaying.value = value.player.isPlaying
                    Log.d(TAG, "Restored active service song: ${serviceSong.title} at index $activeIdx")
                } else {
                    val activeMediaItem = value.player.currentMediaItem
                    if (activeMediaItem != null) {
                        val activeSongId = activeMediaItem.mediaId.toLongOrNull()
                        if (activeSongId != null) {
                            val matchedSong = findSongById(activeSongId)
                            if (matchedSong != null && _currentSong.value != matchedSong) {
                                Log.d(TAG, "Service is already playing song: ${matchedSong.title}. Restoring view model state.")
                                _currentSongIndex.value = findSongIndexById(activeSongId)
                                _currentSong.value = matchedSong
                                _songUri.value = matchedSong.contentUri
                                _isPlaybackReady.value = true
                                _isPlaying.value = value.player.isPlaying
                            }
                        }
                    } else {
                    // Cold-start warm preloading or pending autoplay: when the service connects
                    val restoredSong = _currentSong.value
                    val restoredUri = _songUri.value
                    val restoredPos = _currentPosition.value.toLong()
                    if (restoredSong != null && restoredUri != null) {
                        if (value.getCurrentSourceUri() != restoredUri || pendingAutoPlayOnServiceConnected) {
                            if (pendingAutoPlayOnServiceConnected) {
                                pendingAutoPlayOnServiceConnected = false
                                Log.d(TAG, "Cold-start autoplaying pending song: ${restoredSong.title}")
                                value.loadAndPlay(restoredUri, restoredPos)
                            } else {
                                Log.d(TAG, "Cold-start pre-loading restored song: ${restoredSong.title} at pos $restoredPos")
                                value.loadAudio(restoredUri, restoredPos)
                            }
                            if (restoredSong.thumbnailUrl != null) {
                                value.setSongMetadataByUrl(restoredSong.title, restoredSong.thumbnailUrl)
                            } else {
                                value.setSongMetadataForLocalSong(restoredSong)
                            }
                        } else {
                            Log.d(TAG, "Skipping cold-start pre-loading: song is already active in service")
                        }
                    }
                }
            }
            if (value.servicePlaylist.isEmpty() && _songList.value.isNotEmpty()) {
                value.setServicePlaylist(_songList.value, _currentSongIndex.value, currentQueue)
            }
        }
    }

    fun restoreStateFrom(oldVm: PlayerSharedViewModel) {
        Log.d(TAG, "Restoring UI state from old ViewModel instance to prevent memory leak and state wipe.")
        _songList.value = oldVm._songList.value
        _localSongs.value = oldVm._localSongs.value
        _currentSongIndex.value = oldVm._currentSongIndex.value
        _currentSong.value = oldVm._currentSong.value
        _songUri.value = oldVm._songUri.value
        _isPlaying.value = oldVm._isPlaying.value
        _currentPosition.value = oldVm._currentPosition.value
        _duration.value = oldVm._duration.value
        _isShuffleEnabled.value = oldVm._isShuffleEnabled.value
        _repeatMode.value = oldVm._repeatMode.value
        _isPlayerExpanded.value = oldVm._isPlayerExpanded.value
        _sleepTimerEndTime.value = oldVm._sleepTimerEndTime.value
        _sleepTimerMode.value = oldVm._sleepTimerMode.value
        lyricsController.setLyricsModeEnabled(oldVm.lyricsController.isLyricsModeEnabled.value)
        
        // Preserve any active sleep timer jobs
        this.sleepTimerJob = oldVm.sleepTimerJob
        
        // Ensure UI stays in sync with ExoPlayer if active
        _isPlaybackReady.value = oldVm._isPlaybackReady.value
    }
    var hapticManager: PlayerHapticManager? = null

    // ========== STATEFLOW DEFINITIONS ==========

    private val _songUri = MutableStateFlow<Uri?>(null)
    val songUri: StateFlow<Uri?> = _songUri.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0)
    val processingProgress: StateFlow<Int> = _processingProgress.asStateFlow()

    // Song Library Management
    private val _songList = MutableStateFlow<List<SongItem>>(emptyList())
    val songList: StateFlow<List<SongItem>> = _songList.asStateFlow()

    private val _isQueueExpanded = MutableStateFlow(false)
    val isQueueExpanded: StateFlow<Boolean> = _isQueueExpanded.asStateFlow()
    fun setQueueExpanded(expanded: Boolean) {
        _isQueueExpanded.value = expanded
    }

    private val _localSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val localSongs: StateFlow<List<SongItem>> = _localSongs.asStateFlow()

    // Dynamic blend color extracted from album art — drives the activity-level mini player background
    private val _miniPlayerBlendColor = MutableStateFlow(0)
    val miniPlayerBlendColor: StateFlow<Int> = _miniPlayerBlendColor.asStateFlow()

    private val _bottomNavTranslationY = MutableStateFlow(0f)
    val bottomNavTranslationY: StateFlow<Float> = _bottomNavTranslationY.asStateFlow()
    fun setBottomNavTranslationY(translation: Float) {
        _bottomNavTranslationY.value = translation
    }

    private val _bottomNavHeight = MutableStateFlow(0f)
    val bottomNavHeight: StateFlow<Float> = _bottomNavHeight.asStateFlow()
    fun setBottomNavHeight(height: Float) {
        _bottomNavHeight.value = height
    }

    private val _bottomNavBaseTranslationY = MutableStateFlow(0f)
    val bottomNavBaseTranslationY: StateFlow<Float> = _bottomNavBaseTranslationY.asStateFlow()
    fun setBottomNavBaseTranslationY(translationY: Float) {
        _bottomNavBaseTranslationY.value = translationY
    }

    private val _playerBackgroundColor = MutableStateFlow(0xFF0F0F0F.toInt())
    val playerBackgroundColor: StateFlow<Int> = _playerBackgroundColor.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(-1)
    val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    private val _currentSong = MutableStateFlow<SongItem?>(null)
    val currentSong: StateFlow<SongItem?> = _currentSong.asStateFlow()

    private val _currentSongArtwork = MutableStateFlow<ByteArray?>(null)

    private val _canvasArtwork = MutableStateFlow<com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork?>(null)
    val canvasArtwork: StateFlow<com.codetrio.spatialflow.ui.player.canvas.CanvasArtwork?> = _canvasArtwork.asStateFlow()

    private val _canvasRefreshTrigger = MutableStateFlow(0L)

    fun refreshCanvasArtwork() {
        com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache.clear()
        com.codetrio.spatialflow.ui.player.canvas.SongCanvasProvider.clearCache()
        _canvasArtwork.value = null
        _canvasRefreshTrigger.value = System.currentTimeMillis()
    }

    private val _shouldPromptEffects = MutableStateFlow(false)

    private val _effectsRefreshTrigger = MutableStateFlow(false)


    // Lyrics State (Delegated to Controller)
    val lyricsController = PlayerLyricsStateController(TAG)

    // Effects settings
    private val _is8DEnabled = MutableStateFlow(false)
    val is8DEnabled: StateFlow<Boolean> = _is8DEnabled.asStateFlow()

    private val _isReverbEnabled = MutableStateFlow(false)
    val isReverbEnabled: StateFlow<Boolean> = _isReverbEnabled.asStateFlow()

    private val _reverbPreset = MutableStateFlow<Short>(0)
    val reverbPreset: StateFlow<Short> = _reverbPreset.asStateFlow()

    private val _reverbIntensity = MutableStateFlow(1.0f)
    val reverbIntensity: StateFlow<Float> = _reverbIntensity.asStateFlow()
    val reverbLevel: StateFlow<Float> = _reverbIntensity.asStateFlow()

    private val _speed8D = MutableStateFlow(0.15f)
    val speed8D: StateFlow<Float> = _speed8D.asStateFlow()

    private val _reverb8D = MutableStateFlow(0.12f)
    val reverb8D: StateFlow<Float> = _reverb8D.asStateFlow()

    // 5-Band Equalizer
    private val _isEqualizerEnabled = MutableStateFlow(false)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _eqBand1 = MutableStateFlow(0) // 60Hz
    private val _eqBand2 = MutableStateFlow(0) // 230Hz
    private val _eqBand3 = MutableStateFlow(0) // 910Hz
    private val _eqBand4 = MutableStateFlow(0) // 3600Hz
    private val _eqBand5 = MutableStateFlow(0) // 14000Hz

    val eqBand1: StateFlow<Int> = _eqBand1.asStateFlow()
    val eqBand2: StateFlow<Int> = _eqBand2.asStateFlow()
    val eqBand3: StateFlow<Int> = _eqBand3.asStateFlow()
    val eqBand4: StateFlow<Int> = _eqBand4.asStateFlow()
    val eqBand5: StateFlow<Int> = _eqBand5.asStateFlow()

    // Loudness Enhancer
    private val _isLoudnessEnabled = MutableStateFlow(false)
    val isLoudnessEnabled: StateFlow<Boolean> = _isLoudnessEnabled.asStateFlow()

    private val _loudnessGain = MutableStateFlow(0) // 0-12 dB
    val loudnessGain: StateFlow<Int> = _loudnessGain.asStateFlow()

    // Balance (L/R)
    private val _balance = MutableStateFlow(0f) // -50.0 to +50.0
    val balance: StateFlow<Float> = _balance.asStateFlow()

    // Playback Speed
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isPitchMatched = MutableStateFlow(true)
    val isPitchMatched: StateFlow<Boolean> = _isPitchMatched.asStateFlow()

    // Shuffle & Repeat
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteSongIds: StateFlow<Set<Long>> = _favoriteSongIds.asStateFlow()

    // Favorites
    private val _isCurrentSongFavorite = MutableStateFlow(false)
    val isCurrentSongFavorite: StateFlow<Boolean> = _isCurrentSongFavorite.asStateFlow()

    private val _isCurrentSongDisliked = MutableStateFlow(false)
    val isCurrentSongDisliked: StateFlow<Boolean> = _isCurrentSongDisliked.asStateFlow()

    // Haptics
    private val _isHapticsEnabled = MutableStateFlow(appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).getBoolean("haptics_enabled", false))
    val isHapticsEnabled: StateFlow<Boolean> = _isHapticsEnabled.asStateFlow()

    // Service Reference
    private val _audioServiceState = MutableStateFlow<AudioPlaybackService?>(null)
    val audioServiceState: StateFlow<AudioPlaybackService?> = _audioServiceState.asStateFlow()

    private val _likesCount = MutableStateFlow("Like")
    val likesCount: StateFlow<String> = _likesCount.asStateFlow()
    val likesCountFlow get() = likesCount

    private val _showSignInDialog = MutableStateFlow(false)
    val showSignInDialog: StateFlow<Boolean> = _showSignInDialog.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _expandPlayerRequest = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val expandPlayerRequest: SharedFlow<Unit> = _expandPlayerRequest.asSharedFlow()

    fun expandPlayer() {
        _expandPlayerRequest.tryEmit(Unit)
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    private val _playerExpansionFraction = MutableStateFlow(0f)
    val playerExpansionFraction: StateFlow<Float> = _playerExpansionFraction.asStateFlow()

    fun setPlayerExpansionFraction(fraction: Float) {
        _playerExpansionFraction.value = fraction
    }

    private val _isPlaybackReady = MutableStateFlow(false)
    val isPlaybackReady: StateFlow<Boolean> = _isPlaybackReady.asStateFlow()

    fun setPlaybackReady(ready: Boolean) {
        _isPlaybackReady.value = ready
    }

    private val _isSliderUiMounted = MutableStateFlow(false)
    val isSliderUiMounted: StateFlow<Boolean> = _isSliderUiMounted.asStateFlow()

    fun setSliderUiMounted(mounted: Boolean) {
        _isSliderUiMounted.value = mounted
    }

    fun dismissSignInDialog() {
        _showSignInDialog.value = false
    }

    // ========== SLEEP TIMER SYSTEM ==========
    enum class SleepTimerMode {
        OFF,
        END_OF_SONG,
        END_OF_QUEUE,
        CUSTOM
    }

    private val _sleepTimerMode = MutableStateFlow(SleepTimerMode.OFF)
    val sleepTimerMode: StateFlow<SleepTimerMode> = _sleepTimerMode.asStateFlow()

    private val _sleepTimerEndTime = MutableStateFlow(0L)
    val sleepTimerEndTime: StateFlow<Long> = _sleepTimerEndTime.asStateFlow()

    private var sleepTimerJob: Job? = null

    fun setSleepTimerMode(mode: SleepTimerMode) {
        _sleepTimerMode.value = mode
        if (mode != SleepTimerMode.CUSTOM) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerEndTime.value = 0L
        }
    }

    fun startCustomSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepTimerMode.value = SleepTimerMode.CUSTOM
        val durationMs = minutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMs
        _sleepTimerEndTime.value = endTime

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            val remaining = endTime - System.currentTimeMillis()
            if (remaining > 0) {
                delay(remaining)
            }
            if (_sleepTimerMode.value == SleepTimerMode.CUSTOM) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
            }
        }
    }

    fun cancelSleepTimer() {
        setSleepTimerMode(SleepTimerMode.OFF)
    }

    // Dynamic likes count tracking
    private var baseLikesCountInt = 0

    private fun parseLikesCount(likesStr: String): Int {
        var str = likesStr.uppercase().trim().replace(",", "").replace(".", "")
        // Bail early on known non-numeric YouTube strings
        if (str.isBlank() || str == "LIKE" || str == "LIKED" ||
            str.contains("NO LIKE") || str.contains("HIDDEN") ||
            !str.any(Char::isDigit)) return 0

        var multiplier = 1
        when {
            str.endsWith("B") -> { multiplier = 1_000_000_000; str = str.removeSuffix("B") }
            str.endsWith("M") -> { multiplier = 1_000_000;     str = str.removeSuffix("M") }
            str.endsWith("K") -> { multiplier = 1_000;         str = str.removeSuffix("K") }
        }
        return ((str.toDoubleOrNull() ?: return 0) * multiplier).toInt()
    }

    private fun formatLikesCount(count: Int): String {
        if (count <= 0) return "Like"
        if (count < 10_000) { // Under 10,000 likes, show the exact count with commas for interactive increment visibility (e.g. 1,234 -> 1,235)
            return String.format(Locale.US, "%,d", count)
        }
        if (count < 1_000_000) {
            val k = count / 1_000.0
            return String.format(Locale.US, "%.1fK", k).replace(".0K", "K")
        }
        val m = count / 1_000_000.0
        return String.format(Locale.US, "%.1fM", m).replace(".0M", "M")
    }

    private fun updateLikesCountDisplay(userLiked: Boolean) {
        if (baseLikesCountInt <= 0) {
            _likesCount.value = if (userLiked) "1" else "Like"
            return
        }
        val targetCount = if (userLiked) baseLikesCountInt + 1 else baseLikesCountInt
        _likesCount.value = formatLikesCount(targetCount)
    }

    private data class CachedEngagement(
        val likesCountStr: String?,
        val likesCountInt: Int,
        val isLiked: Boolean?,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val engagementCache = LruCache<String, CachedEngagement>(50)

    private var engagementJob: Job? = null

    private fun fetchEngagementAndHistoryForSong(song: SongItem) {
        val videoId = song.videoId
        if (videoId.isNullOrEmpty()) {
            baseLikesCountInt = 0
            _likesCount.value = "Like"
            return
        }

        val cached = engagementCache.get(videoId)
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < 600_000L)) {
            baseLikesCountInt = cached.likesCountInt
            if (cached.isLiked != null) {
                favoritesManager?.setFavorite(song.id, cached.isLiked)
                if (_isCurrentSongFavorite.value != cached.isLiked) {
                    _isCurrentSongFavorite.value = cached.isLiked
                }
            }
            updateLikesCountDisplay(_isCurrentSongFavorite.value)
            return
        }

        engagementJob?.cancel()
        engagementJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_currentSong.value?.videoId != videoId) return@launch

                // Initialize default state
                _likesCount.value = "Like"
                baseLikesCountInt = 0

                // Fetch player info for likes count and animated thumbnail directly from WebRemix
                val playerJson = InnerTubeClient.playerWebRemix(videoId)
                if (_currentSong.value?.videoId != videoId) return@launch
                val playerResult = com.codetrio.spatialflow.data.innertube.InnerTubeParser.parsePlayerResponse(playerJson, parseStreams = false)

                val vId = song.videoId
                if (vId != null && vId.length == 11) {
                    // Fallback Chain for Real Likes Count
                    var likesCountStr = playerResult?.likesCount
                    var remoteLikeStatus: Boolean? = null

                    // Always try nextYoutubeWeb endpoint (highly reliable for authenticated like status and guest likes)
                    try {
                        val nextYoutubeWebJson = InnerTubeClient.nextYoutubeWeb(videoId)
                        if (_currentSong.value?.videoId == videoId) {
                            remoteLikeStatus = parseLikeStatusFromNextResponse(nextYoutubeWebJson)
                            if (likesCountStr.isNullOrBlank()) {
                                likesCountStr = parseLikesFromNextResponse(nextYoutubeWebJson)
                            }
                            if (remoteLikeStatus != null) {
                                favoritesManager?.setFavorite(song.id, remoteLikeStatus)
                                if (_isCurrentSongFavorite.value != remoteLikeStatus) {
                                    _isCurrentSongFavorite.value = remoteLikeStatus
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("LikesFix", "nextYoutubeWeb failed: ${e.message}")
                    }

                    // Fallback 2: Try nextAndroid endpoint (extremely reliable for mobile engagement/exact likes)
                    if (likesCountStr.isNullOrBlank() && _currentSong.value?.videoId == videoId) {
                        try {
                            val nextAndroidJson = InnerTubeClient.nextAndroid(song.videoId)
                            likesCountStr = parseLikesFromNextResponse(nextAndroidJson)
                        } catch (e: Exception) {
                            Log.w("LikesFix", "nextAndroid failed: ${e.message}")
                        }
                    }

                    // Fallback 3: Try next web remix endpoint
                    if (likesCountStr.isNullOrBlank() && _currentSong.value?.videoId == videoId) {
                        try {
                            val nextJson = InnerTubeClient.next(song.videoId)
                            likesCountStr = parseLikesFromNextResponse(nextJson)
                        } catch (e: Exception) {
                            Log.w("LikesFix", "next(WEB_REMIX) failed: ${e.message}")
                        }
                    }

                    if (_currentSong.value?.videoId == videoId) {
                        likesCountStr?.let { count ->
                            baseLikesCountInt = parseLikesCount(count)
                            updateLikesCountDisplay(_isCurrentSongFavorite.value)
                            engagementCache.put(videoId, CachedEngagement(count, baseLikesCountInt, remoteLikeStatus))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to report playback start or fetch engagement: ${e.message}")
            }
        }
    }

    private fun parseLikeStatusFromNextResponse(json: JsonElement): Boolean? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject
            
            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // Check for nested toggleButtonViewModel configurations
                    lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                    lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                }
            }

            if (obj.has("likeButtonViewModel")) {
                val lbvmObj = obj.getAsJsonObject("likeButtonViewModel")
                lbvmObj.path("toggleButtonViewModel.toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
                lbvmObj.path("toggleButtonViewModel.isToggled")?.asBoolean?.let { return it }
            }

            // Check for segmentedLikeDislikeButtonRenderer
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("toggleButtonRenderer")) {
                        val tbr = inner.getAsJsonObject("toggleButtonRenderer")
                        return tbr.get("isToggled")?.asBoolean ?: false
                    }
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val status = lbr.get("likeStatus")?.asString
                        if (status != null) {
                            return status == "LIKE"
                        }
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    return toggleRenderer.get("isToggled")?.asBoolean ?: false
                }
            }

            if (obj.has("likeButtonRenderer")) {
                val lbr = obj.getAsJsonObject("likeButtonRenderer")
                val status = lbr.get("likeStatus")?.asString
                if (status != null) {
                    return status == "LIKE"
                }
            }
            
            for (entry in obj.entrySet()) {
                val result = parseLikeStatusFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikeStatusFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private fun parseLikesFromNextResponse(json: JsonElement): String? {
        if (json.isJsonObject) {
            val obj = json.asJsonObject

            // ── CHECK 0 (NEW): segmentedLikeDislikeButtonRenderer ──────────────
            // Current YouTube /next response format (2024-2025)
            if (obj.has("segmentedLikeDislikeButtonRenderer")) {
                val seg = obj.getAsJsonObject("segmentedLikeDislikeButtonRenderer")
                // Path: likeButton -> likeButtonRenderer -> likeCount / likesCount
                val likeButtonEl = seg.get("likeButton")
                if (likeButtonEl != null && likeButtonEl.isJsonObject) {
                    val inner = likeButtonEl.asJsonObject
                    if (inner.has("likeButtonRenderer")) {
                        val lbr = inner.getAsJsonObject("likeButtonRenderer")
                        val el = lbr.get("likeCount") ?: lbr.get("likesCount")
                        if (el != null && el.isJsonPrimitive) {
                            val s = el.asString
                            if (s.isNotBlank()) return s
                        }
                    }
                }
            }

            // ── CHECK 0b (NEW): segmentedLikeDislikeButtonViewModel ─────────────
            // Newer ViewComponent-based layout (YouTube 2025+)
            if (obj.has("segmentedLikeDislikeButtonViewModel")) {
                val vm = obj.getAsJsonObject("segmentedLikeDislikeButtonViewModel")
                // likeButtonViewModel -> likeCountEntity -> likeCountIfLiked / likeCountIfIndifferent
                val lbvm = vm.get("likeButtonViewModel")
                if (lbvm != null && lbvm.isJsonObject) {
                    val lbvmObj = lbvm.asJsonObject
                    // toggledText holds the "liked" count string
                    lbvmObj.path("toggleButtonViewModel.toggledText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                    // defaultText holds the unliked count string
                    lbvmObj.path("toggleButtonViewModel.defaultText.styleText.style1.text.content")
                        ?.asString?.let { if (it.isNotBlank() && it.any(Char::isDigit)) return it }
                }
            }
            
            // Check for factoidRenderer representing likes (Highly reliable standard YouTube engagement panel)
            if (obj.has("factoidRenderer")) {
                val factoid = obj.getAsJsonObject("factoidRenderer")
                val label = factoid.path("label.runs.0.text")?.asString 
                    ?: factoid.path("label.simpleText")?.asString
                val isLikesFactoid = label?.contains("like", ignoreCase = true) == true
                if (isLikesFactoid) {
                    factoid.path("value.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    factoid.path("value.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }

            // Check for likeButtonRenderer
            if (obj.has("likeButtonRenderer")) {
                val likeRenderer = obj.getAsJsonObject("likeButtonRenderer")
                
                val likesCountEl = likeRenderer.get("likesCount") ?: likeRenderer.get("likeCount")
                if (likesCountEl != null) {
                    if (likesCountEl.isJsonPrimitive) {
                        val str = likesCountEl.asString
                        if (str.isNotBlank()) return str
                    } else if (likesCountEl.isJsonObject) {
                        val countObj = likesCountEl.asJsonObject
                        countObj.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                        countObj.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                            if (it.isNotBlank()) return it
                        }
                    }
                }
                
                val likesText = likeRenderer.getAsJsonObject("likesCountText") ?: likeRenderer.getAsJsonObject("likeCountText")
                if (likesText != null) {
                    likesText.get("simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    likesText.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString?.let {
                        if (it.isNotBlank()) return it
                    }
                }
            }
            
            // Check for toggleButtonRenderer representing LIKE icon
            if (obj.has("toggleButtonRenderer")) {
                val toggleRenderer = obj.getAsJsonObject("toggleButtonRenderer")
                val iconType = toggleRenderer.path("defaultIcon.iconType")?.asString 
                    ?: toggleRenderer.path("defaultIcon.icon.iconType")?.asString
                val isLikeButton = iconType == "LIKE" || iconType?.contains("THUMB", ignoreCase = true) == true || iconType?.contains("FAVORITE", ignoreCase = true) == true
                
                if (isLikeButton) {
                    toggleRenderer.path("defaultText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("defaultText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.simpleText")?.asString?.let { if (it.isNotBlank()) return it }
                    toggleRenderer.path("toggledText.runs.0.text")?.asString?.let { if (it.isNotBlank()) return it }
                }
            }
            
            // Recurse down object entries
            for (entry in obj.entrySet()) {
                val result = parseLikesFromNextResponse(entry.value)
                if (result != null) return result
            }
        } else if (json.isJsonArray) {
            val arr = json.asJsonArray
            for (element in arr) {
                val result = parseLikesFromNextResponse(element)
                if (result != null) return result
            }
        }
        return null
    }

    private val _isCurrentSongDownloaded = MutableStateFlow(false)
    val isCurrentSongDownloaded: StateFlow<Boolean> = _isCurrentSongDownloaded.asStateFlow()

    private val _currentSongDownloadProgress = MutableStateFlow<Int?>(null)
    val currentSongDownloadProgress: StateFlow<Int?> = _currentSongDownloadProgress.asStateFlow()

    // ========== INITIALIZATION ==========

    private val appSettingsPrefListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "autoplay_enabled") {
            val enabled = sharedPreferences.getBoolean("autoplay_enabled", true)
            if (_isAutoplayEnabled.value != enabled) {
                viewModelScope.launch(Dispatchers.Main) {
                    setAutoplayEnabled(enabled)
                }
            }
        } else if (key == "audio_quality" || key == "data_saver") {
            val quality = sharedPreferences.getString("audio_quality", "High") ?: "High"
            val isDataSaver = sharedPreferences.getBoolean("data_saver", false)
            _streamingQuality.value = if (isDataSaver) "Data Saver" else quality
        } else if (key == "canvas_provider_order") {
            refreshCanvasArtwork()
        }
    }

    init {
        com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache.init(appContext)
        initFavorites(appContext)
        loadLastPlaybackState()
        appSettings.registerOnSharedPreferenceChangeListener(appSettingsPrefListener)

        viewModelScope.launch(Dispatchers.IO) {
            currentSong
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { song ->
                    kotlinx.coroutines.flow.flow {
                        _likesCount.value = "Like"
                        _isCurrentSongDisliked.value = false
                        _canvasArtwork.value = null
                        lyricsController.clearForSongChange(song)

                        if (song != null) {
                            playedShuffledIds.add(song.id)
                            val vId = song.videoId
                            val tag = if (!vId.isNullOrEmpty()) YouTubeMusic.getResolvedFormatTag(vId) else null
                            if (!tag.isNullOrEmpty()) {
                                _playbackFormat.value = tag
                            }
                            // Don't fallback to service format here — it may report stale ExoPlayer
                            // format from the previous song. The service will call updatePlaybackFormat()
                            // after stream resolution completes with the correct format.
                            launch { checkQueueAndFetchMore() }

                            // Automatically fetch lyrics for active song
                            launch { fetchLyricsForCurrentSong() }

                            // Engagement & History
                            launch { fetchEngagementAndHistoryForSong(song) }
                            launch { trackHistoryForSong(song) }
                        }
                        emit(Unit)
                    }
                }.collect {}
        }

        // Independent cancellable Static Artwork & Color pipeline (Sections 3, 4, 5, 6, 7)
        viewModelScope.launch(Dispatchers.IO) {
            currentSong
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { song ->
                    kotlinx.coroutines.flow.flow {
                        if (song != null) {
                            val embedded = song.getEmbeddedPicture(appContext)
                            val colors = extractColorsFromArtwork(song, embedded)
                            if (_currentSong.value?.id == song.id) {
                                _currentSongArtwork.value = embedded
                                _miniPlayerBlendColor.value = colors.first
                                _playerBackgroundColor.value = colors.second
                            }
                        } else {
                            _currentSongArtwork.value = null
                            _miniPlayerBlendColor.value = 0
                            _playerBackgroundColor.value = 0xFF0F0F0F.toInt()
                        }
                        emit(Unit)
                    }
                }.collect {}
        }

        // Independent cancellable Canvas artwork pipeline (Instructions #17-20)
        viewModelScope.launch(Dispatchers.IO) {
            combine(currentSong, _canvasRefreshTrigger) { song, _ -> song }
                .flatMapLatest { song ->
                    kotlinx.coroutines.flow.flow {
                        if (song != null) {
                            val videoId = song.videoId ?: song.id.toString()
                            com.codetrio.spatialflow.ui.player.canvas.resolveCanvasArtworkForPlayback(
                                mediaId = videoId,
                                songTitleRaw = song.title,
                                artistNameRaw = song.artist,
                                albumTitleRaw = song.albumName,
                                requireVertical = false
                            ).onSuccess { resolved ->
                                if (_currentSong.value?.id == song.id) {
                                    val url = resolved.preferredVerticalAnimationUrl ?: resolved.preferredAnimationUrl
                                    Log.d("CanvasArtwork", "Resolved canvas for ${song.title}: $url")
                                    _canvasArtwork.value = resolved
                                    val canvasSource = resolved.static ?: url
                                    if (!canvasSource.isNullOrBlank()) {
                                        extractColorsFromSource(canvasSource).onSuccess { (acc, dom, _) ->
                                            if (_currentSong.value?.id == song.id) {
                                                _miniPlayerBlendColor.value = acc
                                                _playerBackgroundColor.value = dom
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            _canvasArtwork.value = null
                        }
                        emit(Unit)
                    }
                }.collect {}
        }

        viewModelScope.launch(Dispatchers.Default) {
            currentSong
                .distinctUntilChangedBy { it?.id }
                .flatMapLatest { song ->
                    if (song == null) {
                        kotlinx.coroutines.flow.flowOf(false to null)
                    } else {
                        val videoId = song.videoId
                        if (videoId.isNullOrEmpty()) {
                            kotlinx.coroutines.flow.flowOf(true to null)
                        } else {
                            val isDownloaded = SongDownloader.isSongDownloaded(appContext, song)
                            SongDownloader.downloadProgress.map { progressMap ->
                                val progress = progressMap[videoId]
                                if (progress != null) false to progress
                                else isDownloaded to null
                            }.distinctUntilChanged()
                        }
                    }
                }.collect { (downloaded, progress) ->
                    if (_isCurrentSongDownloaded.value != downloaded) {
                        _isCurrentSongDownloaded.value = downloaded
                    }
                    if (_currentSongDownloadProgress.value != progress) {
                        _currentSongDownloadProgress.value = progress
                    }
                }
        }
    }

    private val artworkColorCache = LruCache<String, Triple<Int, Int, Int>>(100)

    private suspend fun extractColorsFromArtwork(song: SongItem, embeddedArtwork: ByteArray?): Triple<Int, Int, Int> {
        val cacheKey = song.videoId ?: song.id.toString()
        artworkColorCache.get(cacheKey)?.let { return it }

        val artworkSource = embeddedArtwork ?: song.getAlbumArtUri() ?: R.drawable.ic_music_note
        val defaultFallback = Triple(0xFF8338EC.toInt(), 0xFF0A0B0E.toInt(), 0xFF6750A4.toInt())
        val extracted = extractColorsFromSource(artworkSource).let { res ->
            when (res) {
                is Result.Success -> res.data
                is Result.Error -> defaultFallback
            }
        }
        artworkColorCache.put(cacheKey, extracted)
        return extracted
    }

    private suspend fun extractColorsFromSource(source: Any): Result<Triple<Int, Int, Int>, DataError.Local> {
        if (source is String && source.isNotBlank()) {
            artworkColorCache.get(source)?.let { return Result.Success(it) }
        }
            
        return withContext(Dispatchers.IO) {
            var bitmap: Bitmap? = null

            // If source is a video URL (e.g. Spotify canvas MP4), extract video frame!
            if (source is String && (source.endsWith(".mp4") || source.contains("canvaz.scdn.co") || source.contains("video"))) {
                val mediaId = _currentSong.value?.let { it.videoId ?: it.id.toString() }
                bitmap = com.codetrio.spatialflow.ui.player.PlayerColorExtractor.extractFrameFromVideo(source, appContext, mediaId)
            }

            if (bitmap == null) {
                val request = ImageRequest.Builder(appContext)
                    .data(source)
                    .size(128, 128)
                    .allowHardware(false) // Hardware Bitmaps are not allowed for Palette extraction!
                    .build()
                try {
                    val result = appContext.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Coil failed to load art for palette: ${e.message}")
                    return@withContext Result.Error(DataError.Local.UNKNOWN)
                }
            }

            if (bitmap != null) {
                try {
                    val palette = Palette.from(bitmap)
                        .maximumColorCount(24) // More swatches for richer seed selection
                        .generate()
                    
                    // 1. Extract palette swatches and pick the most chromatic + relevant accent
                    val allSwatches = palette.swatches
                    val totalPopulation = allSwatches.sumOf { it.population }.coerceAtLeast(1)
                    val candidateSwatches = listOfNotNull(
                        palette.vibrantSwatch,
                        palette.darkVibrantSwatch,
                        palette.lightVibrantSwatch,
                        palette.mutedSwatch,
                        palette.darkMutedSwatch,
                        palette.lightMutedSwatch,
                        palette.dominantSwatch
                    )
                    val bestAccentSwatch = candidateSwatches.maxByOrNull { swatch ->
                        val sat = swatch.hsl[1]
                        val pop = swatch.population.toFloat() / totalPopulation.toFloat()
                        sat * (0.25f + pop)
                    }
                    val accentColor = bestAccentSwatch?.rgb
                        ?: palette.getVibrantColor(palette.getDominantColor(0xFF8338EC.toInt()))

                    // 2. Extract the dominant color as background seed
                    val baseBgColor = palette.getDominantColor(0xFF0F0F0F.toInt())
                    
                    // 3. Pick the best seed for theme generation (prefer vibrant, fall back to accent)
                    val themeSeed = (palette.vibrantSwatch?.rgb
                        ?: palette.lightVibrantSwatch?.rgb
                        ?: palette.darkVibrantSwatch?.rgb
                        ?: bestAccentSwatch?.rgb
                        ?: palette.dominantSwatch?.rgb
                        ?: accentColor)
                    
                    // Convert both to HSL for precise visual enhancements
                    val accentHsl = FloatArray(3)
                    val bgHsl = FloatArray(3)
                    ColorUtils.colorToHSL(accentColor, accentHsl)
                    ColorUtils.colorToHSL(baseBgColor, bgHsl)
                    
                    // Detect monochrome only when palette is truly near-grayscale.
                    val maxChannelDelta = maxOf(
                        abs(Color.red(baseBgColor) - Color.green(baseBgColor)),
                        abs(Color.green(baseBgColor) - Color.blue(baseBgColor)),
                        abs(Color.blue(baseBgColor) - Color.red(baseBgColor))
                    ) / 255f
                    val colorfulPopulation = allSwatches
                        .filter { it.hsl[1] >= 0.16f }
                        .sumOf { it.population }
                    val colorfulRatio = colorfulPopulation.toFloat() / totalPopulation.toFloat()
                    val isMonochromatic = (
                        colorfulRatio < 0.08f &&
                            bgHsl[1] < 0.14f &&
                            accentHsl[1] < 0.18f &&
                            maxChannelDelta < 0.09f
                        ) || (bgHsl[1] < 0.06f && accentHsl[1] < 0.08f)
                    
                    val finalAccent = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a matching pure white accent color (saturation = 0)
                        accentHsl[0] = 0f
                        accentHsl[1] = 0f
                        accentHsl[2] = 1f
                        ColorUtils.HSLToColor(accentHsl)
                    } else {
                        // For colorful art, boost the saturation and lightness of the extracted accent color so it pops
                        if (accentHsl[2] < 0.60f) {
                            accentHsl[2] = 0.65f // Elevate brightness for crisp contrast
                        }
                        if (accentHsl[1] < 0.30f) {
                            accentHsl[1] = 0.60f // Inject beautiful saturation to keep it vibrant
                        }
                        ColorUtils.HSLToColor(accentHsl)
                    }
                    
                    val finalBg = if (isMonochromatic) {
                        // For grayscale/monochromatic art, apply a pure neutral gray/charcoal background seed (saturation = 0)
                        bgHsl[0] = 0f
                        bgHsl[1] = 0f
                        bgHsl[2] = 0.30f
                        ColorUtils.HSLToColor(bgHsl)
                    } else {
                        // For colorful art, avoid muddy hue from near-gray dominant colors:
                        // if dominant saturation is low, borrow hue from accent and keep saturation tasteful.
                        if (bgHsl[1] < 0.16f) {
                            bgHsl[0] = accentHsl[0]
                            bgHsl[1] = (accentHsl[1] * 0.36f).coerceIn(0.18f, 0.40f)
                        } else {
                            bgHsl[1] = bgHsl[1].coerceIn(0.18f, 0.60f)
                        }
                        bgHsl[2] = bgHsl[2].coerceIn(0.22f, 0.48f)
                        ColorUtils.HSLToColor(bgHsl)
                    }

                    // Boost theme seed saturation for richer M3 palette generation
                    val seedHsl = FloatArray(3)
                    ColorUtils.colorToHSL(themeSeed, seedHsl)
                    if (!isMonochromatic && seedHsl[1] < 0.35f) {
                        seedHsl[1] = 0.50f
                    }
                    if (!isMonochromatic && seedHsl[2] < 0.30f) {
                        seedHsl[2] = 0.45f
                    }
                    val finalSeed = if (isMonochromatic) {
                        0xFF6750A4.toInt() // Fallback to M3 purple for grayscale art
                    } else {
                        ColorUtils.HSLToColor(seedHsl)
                    }
                    
                    val extractedTriple = Triple(finalAccent, finalBg, finalSeed)
                    if (source is String && source.isNotBlank()) {
                        artworkColorCache.put(source, extractedTriple)
                    }
                    Result.Success(extractedTriple)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate Palette from bitmap: ${e.message}")
                    Result.Error(DataError.Local.UNKNOWN)
                }
            } else {
                Result.Error(DataError.Local.NOT_FOUND)
            }
        }
    }

    fun initFavorites(context: Context) {
        if (favoritesManager == null) {
            favoritesManager = FavoritesManager(context)
            refreshFavorites()
        }
    }

    fun refreshFavorites() {
        val ids = favoritesManager?.favoriteIds?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
        _favoriteSongIds.value = ids
        _currentSong.value?.let { song ->
            val isFav = ids.contains(song.id)
            _isCurrentSongFavorite.value = isFav
            updateLikesCountDisplay(isFav)
        }
    }

    /**
     * Prime the player with its last persisted memory.
     */
    fun loadLastPlaybackState() {
        val currentSvc = audioService
        if (currentSvc != null && (currentSvc.player.playbackState != androidx.media3.common.Player.STATE_IDLE && currentSvc.player.playbackState != androidx.media3.common.Player.STATE_ENDED)) {
            Log.d(TAG, "AudioService is actively running, skipping stale disk restore.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val lastSong = PlaybackStateManager.getLastSong(appContext) ?: return@launch
            val lastQueue = PlaybackStateManager.getLastQueue(appContext)
            val lastIndex = PlaybackStateManager.getLastIndex(appContext)
            val lastPos = PlaybackStateManager.getLastPosition(appContext)

            Log.d(TAG, "Loading persisted playback state: ${lastSong.title} at pos $lastPos")

            withContext(Dispatchers.Main) {
                val activeSvc = audioService
                if (activeSvc != null && (activeSvc.player.playbackState != androidx.media3.common.Player.STATE_IDLE && activeSvc.player.playbackState != androidx.media3.common.Player.STATE_ENDED)) {
                    Log.d(TAG, "AudioService became active during disk read, skipping disk restore.")
                    return@withContext
                }

                if (lastQueue.isNotEmpty()) {
                    val fullIndex = lastIndex.coerceIn(0, lastQueue.lastIndex)
                    _songList.value = ArrayList(lastQueue)
                    _currentSongIndex.value = fullIndex
                } else {
                    _songList.value = listOf(lastSong)
                    _currentSongIndex.value = 0
                }

                _currentSong.value = lastSong
                _duration.value = lastSong.duration.toInt()
                _currentPosition.value = lastPos.toInt()

                // Prepare lazy resolver pointer
                if (!lastSong.videoId.isNullOrEmpty()) {
                    lastSong.contentUri = "innertube://${lastSong.videoId}".toUri()
                }
                _songUri.value = lastSong.contentUri
            }
        }
    }

    fun savePlaybackState() {
        val song = _currentSong.value ?: return
        val queue = _songList.value
        val idx = _currentSongIndex.value
        val pos = _currentPosition.value

        viewModelScope.launch(Dispatchers.IO) {
            PlaybackStateManager.saveState(
                appContext,
                song,
                pos.toLong(),
                queue,
                idx
            )
        }
    }

    // ========== SERVICE & MANAGERS ==========


    val isProcessingFlow get() = isProcessing
    val processingProgressFlow get() = processingProgress
    val isEqualizerEnabledFlow get() = isEqualizerEnabled
    val isReverbEnabledFlow get() = isReverbEnabled
    val isLoudnessEnabledFlow get() = isLoudnessEnabled
    val is8DEnabledFlow get() = is8DEnabled
    val reverbPresetFlow get() = reverbPreset
    val reverbIntensityFlow get() = reverbIntensity
    val reverbLevelFlow get() = reverbLevel
    val loudnessGainFlow get() = loudnessGain
    val balanceFlow get() = balance
    val playbackSpeedFlow get() = playbackSpeed
    val isPitchMatchedFlow get() = isPitchMatched
    val eqBand1Flow get() = eqBand1
    val eqBand2Flow get() = eqBand2
    val eqBand3Flow get() = eqBand3
    val eqBand4Flow get() = eqBand4
    val eqBand5Flow get() = eqBand5
    val songListFlow get() = songList
    val currentSongIndexFlow get() = currentSongIndex
    val isCurrentSongFavoriteFlow get() = isCurrentSongFavorite
    
    // Lyrics delegated flows
    val syncedLyrics get() = lyricsController.syncedLyrics
    val plainLyrics get() = lyricsController.plainLyrics
    val isLyricsLoading get() = lyricsController.isLoading
    val lyricsError get() = lyricsController.error
    val isLyricsModeEnabled get() = lyricsController.isLyricsModeEnabled
    val providerResults get() = lyricsController.providerResults
    val selectedProvider get() = lyricsController.selectedProvider

    // ========== PLAYBACK CONTROLS ==========

    fun playAudio() {
        audioService?.play()
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_play_pause")
    }
    fun pauseAudio() {
        audioService?.pause()
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_play_pause")
    }
    fun seekTo(position: Int) = audioService?.seekTo(position)
    fun toggleFavorite(songId: Long) = toggleFavorite()

    fun toggleFavorite() {
        val song = _currentSong.value ?: return
        val manager = favoritesManager ?: return

        // Guard guest likes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !manager.isFavorite(song.id)
        manager.setFavorite(song.id, newState)
        _isCurrentSongFavorite.value = newState
        updateLikesCountDisplay(newState)
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_favorite")

        // If we liked it, we cannot have it disliked
        if (newState) {
            _isCurrentSongDisliked.value = false
        }

        // Sync like status to YouTube Music in the background and update local cache (Instruction #26)
        if (!song.videoId.isNullOrEmpty()) {
            val vId = song.videoId!!
            val existing = engagementCache.get(vId)
            engagementCache.put(vId, CachedEngagement(
                likesCountStr = existing?.likesCountStr ?: _likesCount.value,
                likesCountInt = existing?.likesCountInt ?: baseLikesCountInt,
                isLiked = newState
            ))

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.LIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(vId, status)
                    Log.d(TAG, "Synced like status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync like to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleDislike() {
        val song = _currentSong.value ?: return
        
        // Guard guest dislikes: Prompts premium Sign In Dialog
        if (!AccountManager.isLoggedIn(appContext)) {
            _showSignInDialog.value = true
            return
        }

        val newState = !_isCurrentSongDisliked.value
        _isCurrentSongDisliked.value = newState

        // If we are now disliking, we cannot have it liked
        if (newState && _isCurrentSongFavorite.value) {
            val manager = favoritesManager
            if (manager != null) {
                manager.setFavorite(song.id, false)
                _isCurrentSongFavorite.value = false
                updateLikesCountDisplay(false)
            }
        }

        // Sync dislike status to YouTube Music in the background and update local cache (Instruction #26)
        if (!song.videoId.isNullOrEmpty()) {
            val vId = song.videoId!!
            val existing = engagementCache.get(vId)
            engagementCache.put(vId, CachedEngagement(
                likesCountStr = existing?.likesCountStr ?: _likesCount.value,
                likesCountInt = existing?.likesCountInt ?: baseLikesCountInt,
                isLiked = false
            ))

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val status = if (newState) {
                        YouTubeMusic.LikeStatus.DISLIKE
                    } else {
                        YouTubeMusic.LikeStatus.INDIFFERENT
                    }
                    val result = YouTubeMusic.updateLikeStatus(song.videoId!!, status)
                    Log.d(TAG, "Synced dislike status to YouTube Music for ${song.title}: success=${result.getOrDefault(false)}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync dislike to YouTube Music: ${e.message}")
                }
            }
        }
    }

    fun toggleLoopMode() {
        val nextMode = when (_repeatMode.value) {
            REPEAT_OFF -> REPEAT_ALL
            REPEAT_ALL -> REPEAT_ONE
            else -> REPEAT_OFF
        }
        _repeatMode.value = nextMode
    }

    fun setRepeatMode(mode: Int) {
        val normalizedMode = when (mode) {
            REPEAT_OFF, REPEAT_ALL, REPEAT_ONE -> mode
            else -> REPEAT_OFF
        }
        if (_repeatMode.value != normalizedMode) {
            _repeatMode.value = normalizedMode
        }
    }

    fun updateSongIndexOnly(index: Int) {
        _currentSongIndex.value = index
        if (index in _songList.value.indices) {
            val song = _songList.value[index]
            _currentSong.value = song
        }
    }
    fun postIsProcessing(processing: Boolean) {
        _isProcessing.value = processing
    }

    fun triggerEffectsRefresh() {
        _effectsRefreshTrigger.value = !_effectsRefreshTrigger.value
    }
    
    fun retryLyrics() = lyricsController.retryLyrics(appContext, _currentSong.value)

    fun fetchLyrics() = lyricsController.fetchLyrics(appContext, _currentSong.value)

    fun fetchLyricsForCurrentSong() = fetchLyrics()



    fun setLyricsModeEnabled(enabled: Boolean) {
        lyricsController.setLyricsModeEnabled(enabled, appContext, _currentSong.value)
    }

    fun selectLyricsProvider(providerName: String) {
        lyricsController.selectProvider(providerName, appContext, _currentSong.value)
    }

    fun updateCustomLyrics(lyricsText: String) {
        lyricsController.updateCustomLyrics(lyricsText)
        val song = _currentSong.value ?: return
        com.codetrio.spatialflow.data.lyrics.CustomLyricsStorage.saveLyrics(appContext, song, lyricsText)
    }


    fun setIsPlaying(playing: Boolean) {
        if (_isPlaying.value != playing) {
            _isPlaying.value = playing
        }
    }

    fun postIsPlaying(playing: Boolean) {
        if (_isPlaying.value != playing) {
            _isPlaying.value = playing
        }
    }

    fun setCurrentPosition(position: Int) {
        if (abs(_currentPosition.value - position) >= 200 || position == 0) {
            _currentPosition.value = position
        }
    }

    fun setDuration(dur: Int) {
        if (_duration.value != dur) {
            _duration.value = dur
        }
    }

    // ========== AUDIO EFFECTS ==========

    fun set8DEnabled(enabled: Boolean) {
        if (enabled && _isReverbEnabled.value) {
            setReverbEnabled(false)
        }
        _is8DEnabled.value = enabled
        audioService?.set8DEnabled(enabled)
        if (enabled) {
            audioService?.set8DSpeed(_speed8D.value)
            audioService?.set8DReverb(_reverb8D.value)
        }
    }

    fun set8DSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.03f, 0.40f)
        _speed8D.value = clamped
        audioService?.set8DSpeed(clamped)
    }

    fun set8DReverb(reverb: Float) {
        val clamped = reverb.coerceIn(0.0f, 0.40f)
        _reverb8D.value = clamped
        audioService?.set8DReverb(clamped)
    }

    fun setReverbEnabled(enabled: Boolean) {
        if (enabled && _is8DEnabled.value) {
            set8DEnabled(false)
        }
        _isReverbEnabled.value = enabled
        audioService?.setReverbEnabled(enabled)
        
        if (enabled && _reverbPreset.value.toInt() == 0) {
            setReverbPreset(1)
        }
    }

    fun setReverbPreset(preset: Int) {
        val shortPreset = preset.toShort()
        _reverbPreset.value = shortPreset
        audioService?.setReverbPreset(shortPreset)
    }

    fun setReverbIntensity(intensity: Float) {
        _reverbIntensity.value = intensity.coerceIn(0f, 1f)
        audioService?.setReverbIntensity(intensity)
    }

    fun setReverbLevel(level: Float) {
        setReverbIntensity(level)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        if (audioService != null) {
            audioService?.setEqualizerEnabled(enabled)
            if (enabled && _isLoudnessEnabled.value) {
                if (_loudnessGain.value > 800) setLoudnessGain(600)
            }
        }
    }

    fun setEqBand1(gainDb: Int) { _eqBand1.value = gainDb; audioService?.setEqBandGain(0, gainDb) }
    fun setEqBand2(gainDb: Int) { _eqBand2.value = gainDb; audioService?.setEqBandGain(1, gainDb) }
    fun setEqBand3(gainDb: Int) { _eqBand3.value = gainDb; audioService?.setEqBandGain(2, gainDb) }
    fun setEqBand4(gainDb: Int) { _eqBand4.value = gainDb; audioService?.setEqBandGain(3, gainDb) }
    fun setEqBand5(gainDb: Int) { _eqBand5.value = gainDb; audioService?.setEqBandGain(4, gainDb) }

    fun setLoudnessEnabled(enabled: Boolean) {
        _isLoudnessEnabled.value = enabled
        audioService?.setLoudnessEnabled(enabled)
    }

    fun setLoudnessGain(gain: Int) {
        _loudnessGain.value = gain
        audioService?.setLoudnessGain(gain)
    }

    fun setBalance(balanceValue: Float) {
        _balance.value = balanceValue
        audioService?.setBalance(balanceValue)
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        audioService?.setPlaybackSpeed(speed, _isPitchMatched.value)
    }

    fun setPitchMatched(enabled: Boolean) {
        _isPitchMatched.value = enabled
        audioService?.setPlaybackSpeed(_playbackSpeed.value, enabled)
    }

    // ========== LIBRARY MANAGEMENT ==========

    fun setSongList(songs: List<SongItem>) {
        _songList.value = songs
        audioService?.setServicePlaylist(songs, _currentSongIndex.value, currentQueue)
        Log.d(TAG, "Song list updated: ${songs.size} songs")
    }

    fun setLocalSongs(songs: List<SongItem>) {
        _localSongs.value = songs
    }

    fun rescanLocalFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = mutableListOf<SongItem>()
            try {
                val proj = arrayOf(
                    android.provider.MediaStore.Audio.Media._ID,
                    android.provider.MediaStore.Audio.Media.TITLE,
                    android.provider.MediaStore.Audio.Media.ARTIST,
                    android.provider.MediaStore.Audio.Media.ALBUM_ID,
                    android.provider.MediaStore.Audio.Media.ALBUM,
                    android.provider.MediaStore.Audio.Media.DATA,
                    android.provider.MediaStore.Audio.Media.DURATION,
                    android.provider.MediaStore.Audio.Media.DATE_ADDED
                )
                val prefs = appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                val hiddenFolders = (prefs.getString("hidden_folders", "") ?: "").split("||").filter { it.isNotEmpty() }
                val ignoreShort = prefs.getBoolean("ignore_short_audio", false)
                val minDurationMs = if (ignoreShort) prefs.getFloat("ignore_short_audio_duration", 30f).toLong() * 1000L else 0L

                appContext.contentResolver.query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    proj,
                    "${android.provider.MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    "${android.provider.MediaStore.Audio.Media.TITLE} ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                    val titleCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)
                    val artistCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ARTIST)
                    val albumCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM_ID)
                    val albumNameCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.ALBUM)
                    val dataCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    val durCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DURATION)
                    val dateCol = c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATE_ADDED)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol)
                        if (path != null && hiddenFolders.any { path.startsWith(it) }) {
                            continue
                        }
                        val duration = c.getLong(durCol)
                        if (ignoreShort && duration < minDurationMs) {
                            continue
                        }

                        val item = SongItem(
                            c.getLong(idCol),
                            c.getString(titleCol),
                            c.getString(artistCol),
                            c.getLong(albumCol),
                            c.getString(dataCol),
                            c.getLong(durCol),
                            c.getLong(dateCol)
                        ).apply {
                            albumName = c.getString(albumNameCol)
                        }
                        songs.add(item)
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerSharedViewModel", "Error scanning local files", e)
            }

            withContext(Dispatchers.Main) {
                setLocalSongs(songs)
            }
        }
    }

    fun setCurrentSong(song: SongItem?) {
        _currentSong.value = song
    }

    fun playSongAtIndex(index: Int) {
        val songs = _songList.value
        if (index !in songs.indices) {
            Log.w(TAG, "Invalid song index: $index")
            return
        }

        val song = songs[index]

        // Force innertube:// scheme for ALL YouTube Music songs to guarantee fresh stream resolution
        if (!song.videoId.isNullOrEmpty()) {
            song.contentUri = "innertube://${song.videoId}".toUri()
            Log.d(TAG, "Injected lazy resolver URI for: ${song.title}")
        }

        // Auto-reset 8D
        if (_currentSong.value?.id != song.id) {
            if (_is8DEnabled.value) {
                _is8DEnabled.value = false
                audioService?.set8DEnabled(false)
            }
        }

        _currentSongIndex.value = index
        _currentSong.value = song
        _songUri.value = song.contentUri
        
        _isPlaybackReady.value = false

        if (hasActiveEffects()) {
            _shouldPromptEffects.value = true
        }

        _isCurrentSongFavorite.value = favoritesManager?.isFavorite(song.id) ?: false
        baseLikesCountInt = 0
        _likesCount.value = "Like"



        audioService?.setServicePlaylist(_songList.value, index, currentQueue)
        audioService?.let {
            pendingAutoPlayOnServiceConnected = false
            // 1. Start playback immediately
            it.loadAndPlay(song.contentUri)
            
            // 2. Set metadata asynchronously
            if (song.thumbnailUrl != null) {
                it.setSongMetadataByUrl(song.title, song.thumbnailUrl)
            } else {
                it.setSongMetadataForLocalSong(song)
            }
            
            // Personalization history
            updateRecentArtists(song.artist)
        } ?: run {
            // Service not yet bound — queue for when it connects
            pendingAutoPlayOnServiceConnected = true
            Log.w(TAG, "AudioService not bound yet, song queued for cold-start playback")
        }
        
        savePlaybackState()
        prefetchNextPredictedSongs()
    }

    private fun updateRecentArtists(artist: String?) {
        if (artist.isNullOrBlank() || artist.equals("<unknown>", ignoreCase = true)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = appContext.getSharedPreferences("explore_prefs", Context.MODE_PRIVATE)
            val history = prefs.getString("recent_artists_list", "") ?: ""
            val currentList = history.split("|").filter { it.isNotBlank() }.toMutableList()
            
            currentList.remove(artist.trim())
            currentList.add(0, artist.trim())
            val trimmed = currentList.take(3)
            
            prefs.edit {
                putString("recent_artists_list", trimmed.joinToString("|"))
                    .putString("last_played_artist", artist)
            }
        }
    }

    fun findSongById(songId: Long): SongItem? {
        val song = _songList.value.firstOrNull { it.id == songId }
            ?: _localSongs.value.firstOrNull { it.id == songId }
        if (song != null) return song

        val service = audioService ?: return null
        val item = service.player.currentMediaItem ?: return null
        val currentId = item.mediaId.toLongOrNull()
        if (currentId == songId) {
            val title = item.mediaMetadata.title?.toString() ?: "Unknown"
            val artist = item.mediaMetadata.artist?.toString() ?: "Unknown"
            val videoId = item.mediaId
            val artworkUri = item.mediaMetadata.artworkUri?.toString()
            return SongItem.createOnlineSong(
                videoId = videoId,
                title = title,
                artist = artist,
                streamUrl = item.localConfiguration?.uri?.toString(),
                durationMs = service.player.duration,
                thumbnailUrl = artworkUri
            )
        }
        return null
    }

    fun dismissPlayer() {
        _currentSong.value = null
        _songList.value = emptyList()
        PlaybackStateManager.clear(appContext)
        audioService?.dismissService()
    }

    fun playSong(song: SongItem?) {
        if (song == null) return
        if (!song.videoId.isNullOrEmpty() && _isAutoplayEnabled.value) {
            val radioQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(song)
            setQueue(radioQueue)
        } else {
            val index = findSongIndexById(song.id)
            if (index != -1) {
                playSongAtIndex(index)
            } else {
                addToQueue(song)
                val newIndex = findSongIndexById(song.id)
                if (newIndex != -1) playSongAtIndex(newIndex)
            }
        }
    }

    fun findSongIndexById(songId: Long): Int = _songList.value.indexOfFirst { it.id == songId }

    val skipDebouncer by lazy {
        com.codetrio.spatialflow.service.SkipDebouncer(
            scope = viewModelScope,
            debounceMs = 150L,
            onInstantMetadataUpdate = { targetIndex ->
                val songs = _songList.value
                if (targetIndex in songs.indices) {
                    val song = songs[targetIndex]
                    _currentSongIndex.value = targetIndex
                    _currentSong.value = song
                    _songUri.value = song.contentUri
                }
            },
            onCancelInFlightLoads = {
                audioService?.cancelInFlightLoads()
            },
            onCommitSkip = { finalTargetIndex ->
                playSongAtIndex(finalTargetIndex)
            }
        )
    }

    fun playNextSong(force: Boolean = false, fromUser: Boolean = false) {
        if (fromUser) {
            val maxCount = _songList.value.size
            if (maxCount > 0) {
                skipDebouncer.skipNext(_currentSongIndex.value, maxCount) { _ ->
                    getNextSongIndex(ignoreRepeatOne = true)
                }
                return
            }
        }
        if (!force) {
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_SONG) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
            if (_sleepTimerMode.value == SleepTimerMode.END_OF_QUEUE && _currentSongIndex.value == _songList.value.size - 1) {
                pauseAudio()
                setSleepTimerMode(SleepTimerMode.OFF)
                return
            }
        }
        val nextIndex = getNextSongIndex(ignoreRepeatOne = fromUser || force)
        if (nextIndex >= 0) {
            playSongAtIndex(nextIndex)
        } else {
            if (_isAutoplayEnabled.value) {
                val songForRadio = _currentSong.value ?: _songList.value.lastOrNull()
                if (songForRadio != null && !songForRadio.videoId.isNullOrEmpty()) {
                    val radioQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(songForRadio)
                    currentQueue = radioQueue
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val status = radioQueue.getInitialStatus()
                            val existingIds = _songList.value.mapNotNull { it.videoId }.toSet()
                            val newItems = status.items.filter { it.videoId != null && it.videoId !in existingIds }
                            withContext(Dispatchers.Main) {
                                if (newItems.isNotEmpty() && _isAutoplayEnabled.value) {
                                    val startIdx = _songList.value.size
                                    synchronized(autoAddedMediaIds) {
                                        newItems.forEach { it.videoId?.let { id -> autoAddedMediaIds.add(id) } }
                                    }
                                    addSongsToQueue(newItems)
                                    if (startIdx in _songList.value.indices) {
                                        playSongAtIndex(startIdx)
                                    } else {
                                        audioService?.pause()
                                    }
                                } else {
                                    audioService?.pause()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load radio at end of playlist", e)
                            withContext(Dispatchers.Main) { audioService?.pause() }
                        }
                    }
                } else {
                    audioService?.pause()
                }
            } else {
                audioService?.pause()
            }
        }
    }

    fun playPreviousSong() {
        val songs = _songList.value
        if (songs.isEmpty()) return

        // If current position is > 3 seconds, just restart the song
        val pos = _currentPosition.value
        if (pos > 3000) {
            audioService?.seekTo(0)
            return
        }

        skipDebouncer.skipPrevious(_currentSongIndex.value, songs.size) { currentIdx ->
            val mode = _repeatMode.value
            val shuffle = _isShuffleEnabled.value

            if (shuffle && songs.size > 1) {
                var prev = currentIdx
                while (prev == currentIdx) prev = shuffleRandom.nextInt(songs.size)
                prev
            } else {
                val prevIndex = currentIdx - 1
                if (prevIndex < 0) {
                    if (mode == REPEAT_ALL) songs.size - 1 else 0
                } else prevIndex
            }
        }
    }

    fun getNextSongIndex(ignoreRepeatOne: Boolean = false): Int {
        val serviceNext = audioService?.peekNextSongIndex(ignoreRepeatOne)
        if (serviceNext != null && serviceNext >= 0) {
            return serviceNext
        }

        val songs = _songList.value
        if (songs.isEmpty()) return -1
        
        val currentIdx = _currentSongIndex.value
        val mode = _repeatMode.value
        val shuffle = _isShuffleEnabled.value

        if (!ignoreRepeatOne && mode == REPEAT_ONE) return if (currentIdx >= 0) currentIdx else 0
        if (shuffle && songs.size > 1) {
            val unplayed = songs.filter { it.id !in playedShuffledIds && it.id != _currentSong.value?.id }
            if (unplayed.isNotEmpty()) {
                val nextSong = unplayed[shuffleRandom.nextInt(unplayed.size)]
                return songs.indexOfFirst { it.id == nextSong.id }
            } else {
                // All songs played!
                playedShuffledIds.clear()
                _currentSong.value?.id?.let { playedShuffledIds.add(it) }
                if (mode == REPEAT_ALL) {
                    val pool = songs.filter { it.id != _currentSong.value?.id }
                    if (pool.isNotEmpty()) {
                        val nextSong = pool[shuffleRandom.nextInt(pool.size)]
                        return songs.indexOfFirst { it.id == nextSong.id }
                    }
                }
                return -1
            }
        }

        val next = currentIdx + 1
        return if (next >= songs.size) {
            if (mode == REPEAT_ALL || (ignoreRepeatOne && mode == REPEAT_ONE)) 0 else -1
        } else next
    }

    private fun prefetchNextPredictedSongs() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm?.isActiveNetworkMetered == true) return // Never starve cellular connection bandwidth

        val songs = _songList.value
        val currentIdx = _currentSongIndex.value
        val nextIdx = currentIdx + 1
        if (nextIdx in songs.indices) {
            val videoId = songs[nextIdx].videoId
            if (!videoId.isNullOrEmpty()) {
                YouTubeMusic.prefetchStream(videoId)
            }
        }
    }

    private var specJob: Job? = null
    
    /**
     * Touch prediction prefetching. Call this on ACTION_DOWN.
     */
    fun specPrefetch(song: SongItem) {
        specJob?.cancel()
        specJob = viewModelScope.launch(Dispatchers.IO) {
            // Resolve through the same path a tap uses, so the tap that follows this touch
            // lands on a warm cache instead of paying a full round-trip.
            song.videoId?.let { YouTubeMusic.prefetchStream(it) }
        }
    }

    // ========== QUEUE MANAGEMENT ==========

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val songs = _songList.value
        if (fromIndex !in songs.indices || toIndex !in songs.indices) return
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_queue")

        val newList = ArrayList(songs)
        val moved = newList.removeAt(fromIndex)
        newList.add(toIndex, moved)

        when (val currentIdx = _currentSongIndex.value) {
            fromIndex -> {
                _currentSongIndex.value = toIndex
            }
            in (fromIndex + 1)..toIndex -> {
                _currentSongIndex.value = currentIdx - 1
            }
            in toIndex..<fromIndex -> {
                _currentSongIndex.value = currentIdx + 1
            }
        }
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    fun removeSongAtIndex(index: Int) {
        val songs = _songList.value
        if (index !in songs.indices) return
        PlayerHapticManager.triggerInteractionHaptic(appContext, "haptic_queue")

        val newList = ArrayList(songs)
        newList.removeAt(index)

        val currentIdx = _currentSongIndex.value
        when {
            songs.size <= 1 -> {
                _songList.value = emptyList()
                _currentSongIndex.value = -1
                _currentSong.value = null
                audioService?.stop()
                return
            }
            index == currentIdx -> {
                val nextIndex = if (index >= newList.size) newList.size - 1 else index
                _songList.value = newList
                playSongAtIndex(nextIndex)
                return
            }
            index < currentIdx -> {
                _currentSongIndex.value = currentIdx - 1
            }
        }
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    /** Removes all songs scheduled AFTER the currently playing song, keeping history intact. */
    fun clearUpcomingQueue() {
        val currentIdx = _currentSongIndex.value
        val songs = _songList.value
        if (currentIdx < 0 || currentIdx >= songs.size) return
        val newList = songs.subList(0, currentIdx + 1).toList()
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    /** Keeps only the currently playing song; removes all others. */
    fun clearAllExceptCurrent() {
        val currentIdx = _currentSongIndex.value
        val songs = _songList.value
        val currentSong = songs.getOrNull(currentIdx) ?: return
        _songList.value = listOf(currentSong)
        _currentSongIndex.value = 0
        audioService?.refreshNativeQueue()
    }

    fun addToQueue(song: SongItem) {
        val newList = ArrayList(_songList.value)
        if (newList.none { it.id == song.id }) {
            newList.add(song)
            _songList.value = newList
            audioService?.refreshNativeQueue()
        }
    }

    fun addToQueueNext(song: SongItem) {
        val newList = ArrayList(_songList.value)
        newList.removeAll { it.id == song.id }
        
        val currentIdx = _currentSongIndex.value
        val insertIndex = (if (currentIdx < 0) -1 else currentIdx) + 1
        newList.add(insertIndex.coerceIn(0, newList.size), song)
        
        _songList.value = newList
        _currentSongIndex.value = findSongIndexById(_currentSong.value?.id ?: -1)
        audioService?.refreshNativeQueue()
    }

    fun addSongsToQueueEnd(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val newList = ArrayList(_songList.value)
        songs.forEach { song ->
            if (newList.none { it.id == song.id }) {
                newList.add(song)
            }
        }
        _songList.value = newList
        audioService?.refreshNativeQueue()
    }

    fun addSongsToQueueNext(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val newList = ArrayList(_songList.value)
        val songIds = songs.map { it.id }.toSet()
        newList.removeAll { songIds.contains(it.id) }
        
        val currentIdx = _currentSongIndex.value
        var insertIndex = (if (currentIdx < 0) -1 else currentIdx) + 1
        songs.forEach { song ->
            newList.add(insertIndex.coerceIn(0, newList.size), song)
            insertIndex++
        }
        _songList.value = newList
        _currentSongIndex.value = findSongIndexById(_currentSong.value?.id ?: -1)
        audioService?.refreshNativeQueue()
    }

    fun playSongsShuffled(songs: List<SongItem>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        setShuffleEnabled(true)
        setSongList(shuffled)
        playSongAtIndex(0)
    }

    // ========== UTILS ==========

    fun hasActiveEffects(): Boolean {
        return _is8DEnabled.value || _isEqualizerEnabled.value || 
               _isLoudnessEnabled.value || _isReverbEnabled.value
    }

    fun setIsProcessing(processing: Boolean) { _isProcessing.value = processing }
    fun setProcessingProgress(progress: Int) { _processingProgress.value = progress }
    fun setHapticsEnabled(enabled: Boolean) {
        _isHapticsEnabled.value = enabled
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit {
            putBoolean("haptics_enabled", enabled)
        }
        hapticManager?.setHapticsEnabled(enabled)
    }
    fun setShuffleEnabled(enabled: Boolean) {
        if (_isShuffleEnabled.value != enabled) {
            _isShuffleEnabled.value = enabled
            audioService?.setShuffleModeEnabled(enabled)
            if (enabled) {
                playedShuffledIds.clear()
                _currentSong.value?.let { playedShuffledIds.add(it.id) }
            }
        }
    }
    fun toggleShuffle() {
        setShuffleEnabled(!_isShuffleEnabled.value)
    }

    // ── Autoplay (Infinite Queue) ──
    private val playedShuffledIds = mutableSetOf<Long>()
    private val autoAddedMediaIds = mutableSetOf<String>()
    
    // Endless Queue
    var currentQueue: com.codetrio.spatialflow.player.queue.Queue? = null
        set(value) {
            field = value
            audioService?.currentQueueStrategy = value
        }

    fun playYouTubePlaylistQueue(playlistId: String, videoId: String? = null, startIndex: Int = 0, preloadItem: SongItem? = null) {
        val queue = com.codetrio.spatialflow.player.queue.YouTubePlaylistQueue(
            playlistId = playlistId,
            videoId = videoId,
            startIndex = startIndex,
            preloadItem = preloadItem
        )
        setQueue(queue, startIndex)
    }

    fun playLocalPlaylistQueue(title: String, songs: List<SongItem>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        val queue = com.codetrio.spatialflow.player.queue.LocalPlaylistQueue(
            playlistTitle = title,
            songs = songs,
            startIndex = startIndex
        )
        setQueue(queue, startIndex)
    }

    fun setQueue(queue: com.codetrio.spatialflow.player.queue.Queue, startIndex: Int = 0) {
        currentQueue = queue
        synchronized(autoAddedMediaIds) { autoAddedMediaIds.clear() }
        
        val preload = queue.preloadItem
        val currentPlayingId = _currentSong.value?.id
        val isAlreadyPlayingPreload = preload != null && currentPlayingId == preload.id

        if (preload != null && !isAlreadyPlayingPreload) {
            setSongList(listOf(preload))
            playSongAtIndex(0)
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = queue.getInitialStatus()
                withContext(Dispatchers.Main) {
                    val prevPlayingId = _currentSong.value?.id
                    if (!_isAutoplayEnabled.value && queue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                        val singleSong = preload ?: status.items.getOrNull(status.mediaItemIndex)
                        if (singleSong != null) {
                            if (_currentSong.value?.id != singleSong.id) {
                                setSongList(listOf(singleSong))
                                playSongAtIndex(0)
                            }
                        } else {
                            setSongList(status.items)
                            val matchedIndex = if (preload != null) {
                                val idx = status.items.indexOfFirst { it.id == preload.id }
                                if (idx >= 0) idx else status.mediaItemIndex
                            } else {
                                status.mediaItemIndex
                            }
                            if (matchedIndex in status.items.indices && _currentSong.value?.id != status.items[matchedIndex].id) {
                                playSongAtIndex(matchedIndex)
                            }
                        }
                    } else {
                        setSongList(status.items)
                        val matchedIndex = if (preload != null) {
                            val idx = status.items.indexOfFirst { it.id == preload.id }
                            if (idx >= 0) idx else status.mediaItemIndex
                        } else {
                            status.mediaItemIndex
                        }
                        
                        if (queue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                            synchronized(autoAddedMediaIds) {
                                status.items.forEach { item ->
                                    if (item.id != preload?.id) {
                                        item.videoId?.let { autoAddedMediaIds.add(it) }
                                    }
                                }
                            }
                        }
                        
                        if (matchedIndex in status.items.indices) {
                            if ((preload != null && status.items[matchedIndex].id == preload.id) || prevPlayingId == status.items[matchedIndex].id) {
                                updateSongIndexOnly(matchedIndex)
                                if (status.position > 0) {
                                    audioService?.seekTo(status.position.toInt())
                                }
                            } else {
                                playSongAtIndex(matchedIndex)
                                if (status.position > 0) {
                                    audioService?.seekTo(status.position.toInt())
                                }
                            }
                        } else if (status.items.isNotEmpty() && prevPlayingId == null) {
                            playSongAtIndex(0)
                            if (status.position > 0) {
                                audioService?.seekTo(status.position.toInt())
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load initial queue status", e)
            }
        }
    }

    private var isFetchingRadio = false

    fun checkQueueAndFetchMore() {
        if (!_isAutoplayEnabled.value) return
        val queue = currentQueue
        val currentIdx = _currentSongIndex.value
        val listSize = _songList.value.size
        
        // Auto-Load continuation Pages (Auto-Load More)
        if (queue != null && listSize - currentIdx <= 5 && queue.hasNextPage()) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val nextItems = queue.nextPage()
                    if (nextItems.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (_isAutoplayEnabled.value) {
                                synchronized(autoAddedMediaIds) {
                                    nextItems.forEach { it.videoId?.let { id -> autoAddedMediaIds.add(id) } }
                                }
                                addSongsToQueue(nextItems)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch next queue page", e)
                }
            }
        } else if (listSize - currentIdx <= 3 && (queue == null || !queue.hasNextPage()) && _isAutoplayEnabled.value) {
            // Bootstrapping Infinite Autoplay (YouTube Radio)
            if (isFetchingRadio) return
            val songForRadio = _currentSong.value ?: _songList.value.lastOrNull()
            if (songForRadio != null && !songForRadio.videoId.isNullOrEmpty()) {
                isFetchingRadio = true
                val radioQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(songForRadio)
                currentQueue = radioQueue
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val status = radioQueue.getInitialStatus()
                        val existingIds = _songList.value.mapNotNull { it.videoId }.toSet()
                        val newItems = status.items.filter { it.videoId != null && it.videoId !in existingIds }
                        if (newItems.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                if (_isAutoplayEnabled.value) {
                                    synchronized(autoAddedMediaIds) {
                                        newItems.forEach { it.videoId?.let { id -> autoAddedMediaIds.add(id) } }
                                    }
                                    addSongsToQueue(newItems)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to handoff to radio", e)
                    } finally {
                        isFetchingRadio = false
                    }
                }
            }
        }
    }

    private val _isAutoplayEnabled = MutableStateFlow(
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .getBoolean("autoplay_enabled", true)
    )
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    fun setAutoplayEnabled(enabled: Boolean) {
        _isAutoplayEnabled.value = enabled
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE).edit {
            putBoolean("autoplay_enabled", enabled)
        }
        if (enabled) {
            if (currentQueue == null) {
                val currentSong = _currentSong.value
                if (currentSong != null && !currentSong.videoId.isNullOrEmpty()) {
                    currentQueue = com.codetrio.spatialflow.player.queue.YouTubeQueue.radio(currentSong)
                }
            }
            checkQueueAndFetchMore()
        } else {
            onInfiniteQueueDisabled()
        }
    }

    fun onInfiniteQueueDisabled() {
        val currentIdx = _currentSongIndex.value
        val songs = _songList.value
        if (currentIdx in songs.indices) {
            val currentSong = songs[currentIdx]
            if (currentQueue is com.codetrio.spatialflow.player.queue.YouTubeQueue) {
                _songList.value = listOf(currentSong)
                _currentSongIndex.value = 0
            } else {
                val idsToRemove = synchronized(autoAddedMediaIds) { autoAddedMediaIds.toSet() }
                val newList = songs.toMutableList()
                // Purges future automatically injected recommendations
                for (i in newList.size - 1 downTo 0) {
                    if (i == currentIdx) continue
                    val song = newList[i]
                    if (!song.videoId.isNullOrEmpty() && song.videoId in idsToRemove) {
                        newList.removeAt(i)
                    }
                }
                _songList.value = newList
                _currentSongIndex.value = newList.indexOfFirst { it.id == currentSong.id }
            }
            audioService?.refreshNativeQueue()
        }
        synchronized(autoAddedMediaIds) { autoAddedMediaIds.clear() }
    }

    fun addSongsToQueue(songs: List<SongItem>) {
        val newList = ArrayList(_songList.value)
        val existingIds = newList.map { it.id }.toSet()
        val uniqueNewSongs = songs.filter { it.id !in existingIds }
        if (uniqueNewSongs.isEmpty()) return
        newList.addAll(uniqueNewSongs)
        _songList.value = newList
        _currentSongIndex.value = findSongIndexById(_currentSong.value?.id ?: -1)
        audioService?.addSongsToServicePlaylist(uniqueNewSongs)
        audioService?.refreshNativeQueue()
    }

    fun syncFromService(
        songs: List<SongItem>,
        currentIndex: Int,
        currentSong: SongItem?,
        isPlaying: Boolean
    ) {
        if (songs.isNotEmpty()) {
            _songList.value = songs
        }
        if (currentIndex in songs.indices) {
            _currentSongIndex.value = currentIndex
        }
        if (currentSong != null) {
            _currentSong.value = currentSong
            _songUri.value = currentSong.contentUri
        }
        _isPlaying.value = isPlaying
        _isPlaybackReady.value = true
    }

    override fun onCleared() {
        super.onCleared()
        appContext.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(appSettingsPrefListener)
        audioService?.setViewModel(null)
        audioService = null
        _audioServiceState.value = null
    }
}
