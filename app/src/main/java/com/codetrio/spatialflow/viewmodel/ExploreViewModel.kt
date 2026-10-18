package com.codetrio.spatialflow.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.innertube.AlbumPage
import com.codetrio.spatialflow.data.innertube.ArtistPage
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.OnlineAlbum
import com.codetrio.spatialflow.data.innertube.OnlineArtist
import com.codetrio.spatialflow.data.innertube.OnlinePlaylist
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.PlaylistPage
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.codetrio.spatialflow.ui.explore.resizeCDN
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.codetrio.spatialflow.data.db.MusicDatabase
import com.codetrio.spatialflow.data.db.PlaylistDao
import com.codetrio.spatialflow.data.db.PlaylistEntity
import com.codetrio.spatialflow.data.db.PlaylistSongEntity
import com.codetrio.spatialflow.data.innertube.AccountManager
import java.util.concurrent.ConcurrentHashMap
import com.codetrio.spatialflow.ui.explore.GenreUiModel
import com.codetrio.spatialflow.ui.explore.getMoodVisuals
import com.codetrio.spatialflow.ui.explore.createBrightGradientFromBase
import kotlin.time.Duration.Companion.milliseconds

enum class DetailType {
    ALBUM, PLAYLIST, ARTIST, SECTION, GENRES, MOOD
}

@Stable
data class DetailRoute(
    val type: DetailType,
    val id: String = "",
    val params: String? = null,
    val title: String? = null,
    val fallbackUrl: String? = null
)

@Stable
data class MoodDetail(
    val moodName: String,
    val sections: List<HomeSection>
)

@Stable
data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val searchFilter: SearchFilter? = null
)

@Stable
data class HomeUiState(
    val homeSections: List<HomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val currentMood: String? = null,
    val homeMoods: List<String> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMoreHome: Boolean = false,
    val pinnedSpeedDialIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
    val error: String? = null
)

@Stable
data class DetailUiState(
    val albumDetail: AlbumPage? = null,
    val artistDetail: ArtistPage? = null,
    val playlistDetail: PlaylistPage? = null,
    val sectionDetail: HomeSection? = null,
    val moodDetail: MoodDetail? = null,
    val genresSections: List<HomeSection> = emptyList(),
    val genresUiModels: List<GenreUiModel> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailStack: List<DetailRoute> = emptyList()
)

@Stable
data class PlaybackUiState(
    val isLoadingStream: Boolean = false,
    val currentOnlineSong: OnlineSong? = null,
    val streamUrl: String? = null,
    val streamError: String? = null,
    val onlineQueue: List<OnlineSong> = emptyList(),
    val currentOnlineIndex: Int = -1
)

/**
 * ViewModel for the Explore (Online Music) tab.
 * Manages search, home feed, and online playback state.
 */
class ExploreViewModel(application: Application) : AndroidViewModel(application) {

    var cameFromLibrary: Boolean = false

    private val TAG = "ExploreViewModel"

    // ========== Unified MVI State ==========

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    // Granular 4-way UI state flows for UI performance optimization
    val searchState: StateFlow<SearchUiState> = _uiState
        .map { SearchUiState(it.searchQuery, it.searchResults, it.suggestions, it.isSearching, it.isLoadingSuggestions, it.searchHistory, it.searchFilter) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState())

    val homeState: StateFlow<HomeUiState> = _uiState
        .map { HomeUiState(it.homeSections, it.isLoadingHome, it.currentMood, it.homeMoods, it.isRefreshing, it.isLoadingMoreHome, it.pinnedSpeedDialIds, it.notInterestedIds, it.error) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    val detailState: StateFlow<DetailUiState> = _uiState
        .map { DetailUiState(it.albumDetail, it.artistDetail, it.playlistDetail, it.sectionDetail, it.moodDetail, it.genresSections, it.genresUiModels, it.isLoadingDetail, it.detailStack) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, DetailUiState())

    val playbackState: StateFlow<PlaybackUiState> = _uiState
        .map { PlaybackUiState(it.isLoadingStream, it.currentOnlineSong, it.streamUrl, it.streamError, it.onlineQueue, it.currentOnlineIndex) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackUiState())

    private val _subscriptionChanged = MutableStateFlow(false)
    val subscriptionChanged = _subscriptionChanged.asStateFlow()

    fun consumeSubscriptionChanged() {
        _subscriptionChanged.value = false
    }

    // Public delegated StateFlows for external backward compatibility
    val currentOnlineSong: StateFlow<OnlineSong?> = uiState
        .map { it.currentOnlineSong }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val onlineQueue: StateFlow<List<OnlineSong>> = uiState
        .map { it.onlineQueue }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentOnlineIndex: StateFlow<Int> = uiState
        .map { it.currentOnlineIndex }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

    val searchFilter: StateFlow<SearchFilter?> = uiState
        .map { it.searchFilter }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingMoreHome: StateFlow<Boolean> = uiState
        .map { it.isLoadingMoreHome }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val error: StateFlow<String?> = uiState
        .map { it.error }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streamUrl: StateFlow<String?> = uiState
        .map { it.streamUrl }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isLoadingStream: StateFlow<Boolean> = uiState
        .map { it.isLoadingStream }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)



    private var searchContinuation: String? = null
    private var homeContinuationToken: String? = null
    private var homeLoaded = false

    fun setMood(mood: String?) {
        if (_uiState.value.currentMood == mood) return
        _uiState.update { it.copy(currentMood = mood) }
        homeLoaded = false // Force reload
        loadHomeFeed()
    }

    // ========== Search History ==========

    private val prefs = application.getSharedPreferences("explore_prefs", 0)
    private val SEARCH_HISTORY_KEY = "search_history"
    private val MAX_HISTORY = 20

    init {
        loadSearchHistory()
        val speedDialSongs = getPinnedSpeedDialSongs()
        val notInterested = prefs.getStringSet("not_interested_ids", null)?.toSet() ?: emptySet()
        _uiState.update { 
            it.copy(
                pinnedSpeedDialIds = speedDialSongs.map { s -> s.videoId }.toSet(),
                notInterestedIds = notInterested
            )
        }
    }

    private fun loadSearchHistory() {
        val historyString = prefs.getString("search_history_list", null)
        if (historyString != null) {
            val list = historyString.split("\n").filter { it.isNotBlank() }
            _uiState.update { it.copy(searchHistory = list) }
        } else {
            // Fallback & migration from old set-based format
            val oldSet = prefs.getStringSet(SEARCH_HISTORY_KEY, null)
            if (oldSet != null) {
                val list = oldSet.toList()
                _uiState.update { it.copy(searchHistory = list) }
                prefs.edit {
                        putString("search_history_list", list.joinToString("\n"))
                        .remove(SEARCH_HISTORY_KEY)
                }
            } else {
                _uiState.update { it.copy(searchHistory = emptyList()) }
            }
        }
    }

    private fun addToSearchHistory(query: String) {
        if (query.isBlank()) return
        val appSettings = getApplication<Application>().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
        if (appSettings.getBoolean("pause_history", false)) {
            return
        }
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query) // Remove duplicate
        current.add(0, query) // Add to top
        val trimmed = current.take(MAX_HISTORY)
        _uiState.update { it.copy(searchHistory = trimmed) }
        prefs.edit {
                putString("search_history_list", trimmed.joinToString("\n"))
            }
    }


    fun removeFromSearchHistory(query: String) {
        val current = _uiState.value.searchHistory.toMutableList()
        current.remove(query)
        _uiState.update { it.copy(searchHistory = current) }
        prefs.edit {
            putString("search_history_list", current.joinToString("\n"))
        }
    }

    fun clearSearchHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
        prefs.edit {
            remove("search_history_list")
                .remove(SEARCH_HISTORY_KEY)
        }
    }

    // Shared event to signal that the host fragment should propagate current state to Player service INSTANTLY!
    private val _playbackTriggerEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val playbackTriggerEvent = _playbackTriggerEvent.asSharedFlow()

    private val _events = Channel<ExploreEvent>()
    val events: Flow<ExploreEvent> = _events.receiveAsFlow()

    private val _isScrolledDown = MutableStateFlow(false)
    val isScrolledDown: StateFlow<Boolean> = _isScrolledDown.asStateFlow()

    fun setScrolledDown(scrolled: Boolean) {
        _isScrolledDown.value = scrolled
    }

    private val scrollPositions = HashMap<String, Pair<Int, Int>>()

    fun saveScrollPosition(key: String, index: Int, offset: Int) {
        if (scrollPositions.size > 100) {
            scrollPositions.clear()
        }
        scrollPositions[key] = index to offset
    }

    fun getScrollPosition(key: String): Pair<Int, Int>? {
        return scrollPositions[key]
    }

    private var cachedSearchSongs: List<OnlineSong> = emptyList()
    private var cachedSearchSongIndexMap: Map<String, Int> = emptyMap()

    private fun updateCachedSearchSongs(items: List<SearchItem>) {
        val songs = ArrayList<OnlineSong>()
        val map = HashMap<String, Int>()
        for (item in items) {
            when (item) {
                is SearchItem.Song -> {
                    if (!map.containsKey(item.song.videoId)) {
                        map[item.song.videoId] = songs.size
                        songs.add(item.song)
                    }
                }
                is SearchItem.TopResult -> {
                    if (item.song != null && !map.containsKey(item.song.videoId)) {
                        map[item.song.videoId] = songs.size
                        songs.add(item.song)
                    }
                }
                else -> {}
            }
        }
        cachedSearchSongs = songs
        cachedSearchSongIndexMap = map
    }

    fun playSearchSong(song: OnlineSong) {
        // 1. Play the selected song instantly for zero latency
        playOnlineSong(song)
        _uiState.update { 
            it.copy(
                onlineQueue = listOf(song),
                currentOnlineIndex = 0
            )
        }
        _playbackTriggerEvent.tryEmit(Unit)

        // 2. Fetch a tailored dynamic Radio station in the background to continue playback seamlessly
        viewModelScope.launch(Dispatchers.IO) {
            val result = YouTubeMusic.startRadio(videoId = song.videoId)
            result.onSuccess { radioSongs ->
                if (radioSongs.isNotEmpty()) {
                    val finalQueue = if (radioSongs.first().videoId == song.videoId) {
                        radioSongs
                    } else {
                        listOf(song) + radioSongs.filter { it.videoId != song.videoId }
                    }
                    _uiState.update { state ->
                        if (state.currentOnlineSong?.videoId == song.videoId) {
                            state.copy(
                                onlineQueue = finalQueue,
                                currentOnlineIndex = 0
                            )
                        } else {
                            state
                        }
                    }
                    if (finalQueue.size > 1) {
                        YouTubeMusic.prefetchStream(finalQueue[1].videoId)
                    }
                }
            }
        }
    }

    fun sendEvent(event: ExploreEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    fun onAction(action: ExploreAction) {
        when (action) {
            is ExploreAction.OnSearchQueryChange -> setSearchQuery(action.query)
            is ExploreAction.OnSearchFilterSelect -> setSearchFilter(action.filter)
            is ExploreAction.OnSearchSubmit -> search(action.query)
            is ExploreAction.OnClearSearch -> clearSearch()
            is ExploreAction.OnSongClick -> playOnlineSong(action.song)
            is ExploreAction.OnSongWithQueueClick -> playOnlineSongWithQueue(action.song, action.queue, action.index)
            is ExploreAction.OnAlbumClick -> loadAlbum(action.album.browseId)
            is ExploreAction.OnAlbumBrowseIdClick -> loadAlbum(action.browseId)
            is ExploreAction.OnAlbumForSongClick -> loadAlbumForSong(action.songTitle, action.artistName, action.albumName, action.videoId)
            is ExploreAction.OnArtistClick -> loadArtist(action.artist.browseId)
            is ExploreAction.OnArtistBrowseIdClick -> loadArtist(action.browseId)
            is ExploreAction.OnArtistNameClick -> loadArtistByName(action.artistName)
            is ExploreAction.OnPlaylistClick -> loadPlaylist(action.playlist.playlistId)
            is ExploreAction.OnPlaylistIdClick -> loadPlaylist(action.playlistId)
            is ExploreAction.OnSectionClick -> loadSectionDetails(action.browseId, action.params, action.title)
            is ExploreAction.OnMoodClick -> loadMood(action.title, action.browseId, action.params)
            is ExploreAction.OnSetMood -> setMood(action.mood)
            is ExploreAction.OnRefreshHomeFeed -> refreshHomeFeed()
            is ExploreAction.OnLoadMoreHomeSections -> loadMoreHomeSections()
            is ExploreAction.OnLoadMoreSearchResults -> loadMoreResults()
            is ExploreAction.OnLoadMorePlaylistSongs -> loadMorePlaylistSongs()
            is ExploreAction.OnPopDetail -> popDetailStack()
            is ExploreAction.OnResetToHome -> resetToHome()
            is ExploreAction.OnHandleDeepLink -> handleDeepLink(action.url)
            is ExploreAction.OnScrollToTop -> sendEvent(ExploreEvent.ScrollToTop)
            is ExploreAction.OnOpenSearch -> sendEvent(ExploreEvent.OpenSearch)
        }
    }

    private var searchJob: Job? = null
    private var loadMoreSearchJob: Job? = null
    private var suggestJob: Job? = null
    private var loadMoreHomeJob: Job? = null

    private fun handleDeepLink(urlString: String) {
        try {
            // Check for Spotify playlist links first
            val spotifyId = Regex("""(?:spotify:playlist:|open\.spotify\.com/(?:intl-[a-zA-Z0-9_-]+/)?playlist/)([a-zA-Z0-9]+)""").find(urlString)?.groupValues?.get(1)
            if (!spotifyId.isNullOrBlank()) {
                sendEvent(ExploreEvent.NavigateToSpotifyPlaylist(spotifyId))
                return
            }

            val uri = android.net.Uri.parse(urlString)
            val scheme = uri.scheme?.lowercase() ?: ""
            val host = uri.host?.lowercase() ?: ""

            if (scheme == "spatialflow") {
                val pathSegments = uri.pathSegments
                when (host) {
                    "playlist" -> {
                        val playlistId = pathSegments.firstOrNull() ?: uri.getQueryParameter("list")
                        if (!playlistId.isNullOrBlank()) {
                            loadPlaylist(playlistId)
                            return
                        }
                    }
                    "album" -> {
                        val albumId = pathSegments.firstOrNull() ?: uri.getQueryParameter("id")
                        if (!albumId.isNullOrBlank()) {
                            loadAlbum(albumId)
                            return
                        }
                    }
                    "song", "track" -> {
                        val songId = pathSegments.firstOrNull() ?: uri.getQueryParameter("v")
                        if (!songId.isNullOrBlank()) {
                            startRadio(songId)
                            return
                        }
                    }
                }
            }

            var videoId: String? = null
            var playlistId: String? = null

            if (host.contains("youtu.be")) {
                videoId = uri.lastPathSegment
                playlistId = uri.getQueryParameter("list")
            } else if (host.contains("youtube.com")) {
                val path = uri.path ?: ""
                when {
                    path.startsWith("/shorts/") -> {
                        videoId = uri.lastPathSegment
                    }
                    path.startsWith("/live/") -> {
                        videoId = uri.lastPathSegment
                    }
                    path.startsWith("/playlist") -> {
                        playlistId = uri.getQueryParameter("list")
                    }
                    else -> {
                        videoId = uri.getQueryParameter("v")
                        playlistId = uri.getQueryParameter("list")
                    }
                }
            }

            if (videoId != null && videoId.isNotBlank()) {
                startRadio(videoId)
            } else if (playlistId != null && playlistId.isNotBlank()) {
                loadPlaylist(playlistId)
            } else {
                sendEvent(ExploreEvent.ShowSnackbar(com.codetrio.spatialflow.ui.UiText.DynamicString("Invalid link")))
            }
        } catch (e: Exception) {
            sendEvent(ExploreEvent.ShowSnackbar(com.codetrio.spatialflow.ui.UiText.DynamicString("Failed to parse link")))
        }
    }

    // In-memory bounded LRU cache for instant repeat search results (max 32 queries)
    private val searchResultsCache = android.util.LruCache<String, Pair<List<SearchItem>, String?>>(32)
    private var lastSearchedKey: String? = null

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.length >= 2) {
            fetchSuggestions(query)
        } else {
            _uiState.update { it.copy(suggestions = emptyList(), isLoadingSuggestions = false) }
        }
    }

    fun setSearchFilter(filter: SearchFilter?) {
        if (_uiState.value.searchFilter == filter) return
        _uiState.update { it.copy(searchFilter = filter) }
        // Re-search with filter if we have a query
        val query = _uiState.value.searchQuery
        if (query.isNotBlank()) {
            search(query)
        }
    }

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            clearSearch()
            return
        }

        val filter = _uiState.value.searchFilter
        val cacheKey = "$trimmedQuery|${filter?.name ?: "ALL"}"

        // Instant return from LRU cache if identical query was previously fetched
        searchResultsCache.get(cacheKey)?.let { (cachedItems, cachedContinuation) ->
            searchJob?.cancel()
            updateCachedSearchSongs(cachedItems)
            searchContinuation = cachedContinuation
            lastSearchedKey = cacheKey
            _uiState.update {
                it.copy(
                    searchQuery = query,
                    isSearching = false,
                    suggestions = emptyList(),
                    searchResults = cachedItems
                )
            }
            return
        }

        searchJob?.cancel()
        _uiState.update { 
            it.copy(
                searchQuery = query,
                isSearching = true,
                suggestions = emptyList(),
                searchResults = emptyList()
            )
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (trimmedQuery.isNotEmpty()) {
                    addToSearchHistory(trimmedQuery)
                }
                val result = YouTubeMusic.search(trimmedQuery, filter)
                result.onSuccess { searchResult ->
                    // Filter duplicates to prevent duplicate keys in lazy lists
                    val seenIds = mutableSetOf<String>()
                    val uniqueItems = searchResult.items.filter { item ->
                        val id = when (item) {
                            is SearchItem.TopResult -> "topresult-${item.title}"
                            is SearchItem.Header -> "header-${item.title}"
                            is SearchItem.Song -> "song-${item.song.videoId}"
                            is SearchItem.Album -> "album-${item.album.browseId}"
                            is SearchItem.Artist -> "artist-${item.artist.browseId}"
                            is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                        }
                        val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                        !isNotInterested && seenIds.add(id)
                    }
                    updateCachedSearchSongs(uniqueItems)
                    searchContinuation = searchResult.continuation
                    lastSearchedKey = cacheKey
                    searchResultsCache.put(cacheKey, uniqueItems to searchResult.continuation)

                    _uiState.update { it.copy(searchResults = uniqueItems) }
                    prefetchThumbnails(uniqueItems.take(12))
                    
                    // Pre-resolve stream URLs for top 3 song results for instant playback
                    uniqueItems
                        .filterIsInstance<SearchItem.Song>()
                        .take(3)
                        .forEach { songItem ->
                            YouTubeMusic.prefetchStream(songItem.song.videoId)
                        }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Search failed", e)
                    _uiState.update { it.copy(error = "Search failed: ${e.message}") }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Search error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun loadMoreResults() {
        val cont = searchContinuation ?: return
        if (_uiState.value.isSearching) return
        
        _uiState.update { it.copy(isSearching = true) }

        loadMoreSearchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.searchContinuation(cont)
                result.onSuccess { searchResult ->
                    var newItemsToPrefetch = emptyList<SearchItem>()
                    _uiState.update { state ->
                        val currentList = state.searchResults
                        val seenIds = currentList.map { item ->
                            when (item) {
                                is SearchItem.TopResult -> "topresult-${item.title}"
                                is SearchItem.Header -> "header-${item.title}"
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                        }.toMutableSet()

                        val uniqueNewItems = searchResult.items.filter { item ->
                            val id = when (item) {
                                is SearchItem.TopResult -> "topresult-${item.title}"
                                is SearchItem.Header -> "header-${item.title}"
                                is SearchItem.Song -> "song-${item.song.videoId}"
                                is SearchItem.Album -> "album-${item.album.browseId}"
                                is SearchItem.Artist -> "artist-${item.artist.browseId}"
                                is SearchItem.Playlist -> "playlist-${item.playlist.playlistId}"
                            }
                            val isNotInterested = item is SearchItem.Song && _uiState.value.notInterestedIds.contains(item.song.videoId)
                            !isNotInterested && seenIds.add(id)
                        }

                        searchContinuation = searchResult.continuation
                        newItemsToPrefetch = uniqueNewItems
                        val updatedList = currentList + uniqueNewItems
                        updateCachedSearchSongs(updatedList)
                        state.copy(searchResults = updatedList)
                    }
                    prefetchThumbnails(newItemsToPrefetch.take(8))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    private fun fetchSuggestions(query: String) {
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch(Dispatchers.IO) {
            delay(150.milliseconds) // Reduced debounce for instant feel
            _uiState.update { it.copy(isLoadingSuggestions = true) }
            try {
                val result = YouTubeMusic.searchSuggestions(query)
                result.onSuccess { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions.take(8), isLoadingSuggestions = false) }
                }
                result.onFailure {
                    _uiState.update { it.copy(isLoadingSuggestions = false) }
                }
            } catch (_: Exception) {
                // Silently ignore suggestion errors
                _uiState.update { it.copy(isLoadingSuggestions = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { 
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                suggestions = emptyList(),
                searchFilter = null,
                isLoadingSuggestions = false
            )
        }
        searchContinuation = null
    }

    // ========== Home Feed ==========

    // (Removed getPersonalizedSections to rely purely on YouTube Explore)

    private fun syncCookieFromDisk() {
        val ctx = getApplication<Application>()
        val cookie = ctx.getSharedPreferences("AppSettings", 0).getString("yt_cookies", null)
        InnerTubeClient.cookie = cookie
    }

    fun forceReloadHomeFeed() {
        syncCookieFromDisk()
        homeLoaded = false
        loadHomeFeed()
    }

    private suspend fun fetchMergedHomeFeed(): List<HomeSection> = coroutineScope {
        val sections = mutableListOf<HomeSection>()
        val mood = _uiState.value.currentMood

        if (mood != null) {
            // Parallel Synthesis: Mood specific feed creation
            try {
                val playTask = async { YouTubeMusic.search(mood, SearchFilter.PLAYLISTS) }
                val songTask = async { YouTubeMusic.search("$mood music", SearchFilter.SONGS) }
                val albumTask = async { YouTubeMusic.search("$mood releases", SearchFilter.ALBUMS) }

                // Wait for all simultaneously
                val responses = awaitAll(playTask, songTask, albumTask)
                
                responses[0].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Curations", res.items.shuffled().take(12)))
                }
                responses[1].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("Top $mood Tracks", res.items.take(20)))
                }
                responses[2].onSuccess { res ->
                    if (res.items.isNotEmpty()) sections.add(HomeSection("$mood Spotlight", res.items.shuffled().take(12)))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to synthesize mood feed", e)
            }
            return@coroutineScope sections
        }

        // Standard Feed Scenario: Load pure YouTube Home with resilient explore() fallback
        var homePage = YouTubeMusic.home().getOrNull()
        if (homePage == null || homePage.sections.isEmpty()) {
            Log.w(TAG, "YouTubeMusic.home() empty or failed, trying explore() fallback")
            homePage = YouTubeMusic.explore().getOrNull()
        }

        homeContinuationToken = homePage?.continuation

        // Extract Moods
        val allMoods = mutableListOf<String>()
        homePage?.moods?.let { allMoods.addAll(it) }
        if (allMoods.isNotEmpty()) {
            _uiState.update { it.copy(homeMoods = allMoods.distinct()) }
        }

        // Add pure Home sections
        homePage?.sections?.let { sections.addAll(it) }

        // Proactively fetch initial continuation pages so user immediately gets 10-15+ rich sections
        var contToken = homeContinuationToken
        var attempts = 0
        while (contToken != null && sections.size < 12 && attempts < 2) {
            attempts++
            try {
                val contPage = YouTubeMusic.homeContinuation(contToken).getOrNull()
                if (contPage != null && contPage.sections.isNotEmpty()) {
                    contPage.sections.forEach { newSec ->
                        if (sections.none { it.title.equals(newSec.title, ignoreCase = true) }) {
                            sections.add(newSec)
                        }
                    }
                    contToken = contPage.continuation
                    homeContinuationToken = contPage.continuation
                } else {
                    break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Initial proactive continuation fetch failed (non-fatal)", e)
                break
            }
        }

        // Post-processing: Filter out disliked / not interested songs
        val notInterested = _uiState.value.notInterestedIds

        val processedSections = sections.map { sec ->
            sec.copy(items = sec.items.filterNot { item ->
                item is SearchItem.Song && notInterested.contains(item.song.videoId)
            })
        }.filter { it.items.isNotEmpty() }.toMutableList()

        processedSections
    }

    fun loadHomeFeed() {
        if (homeLoaded || _uiState.value.isLoadingHome) return

        syncCookieFromDisk() // Critical defensive hydration step
        _uiState.update { it.copy(isLoadingHome = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mergedSections = fetchMergedHomeFeed()
                _uiState.update { it.copy(homeSections = mergedSections) }
                homeLoaded = true
                prefetchThumbnails(mergedSections.take(4).flatMap { it.items.take(4) })
            } catch (e: Exception) {
                Log.e(TAG, "Home feed error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoadingHome = false) }
            }
        }
    }

    fun refreshHomeFeed() {
        syncCookieFromDisk()
        homeLoaded = false
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mergedSections = fetchMergedHomeFeed()
                _uiState.update { it.copy(homeSections = mergedSections) }
                homeLoaded = true
                prefetchThumbnails(mergedSections.take(4).flatMap { it.items.take(4) })
            } catch (e: Exception) {
                Log.e(TAG, "Refresh error", e)
                _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun loadMoreHomeSections() {
        val token = homeContinuationToken ?: return
        if (_uiState.value.isLoadingHome || _uiState.value.isLoadingMoreHome) return

        _uiState.update { it.copy(isLoadingMoreHome = true) }
        loadMoreHomeJob?.cancel()
        loadMoreHomeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.homeContinuation(token)
                result.onSuccess { page ->
                    val notInterested = _uiState.value.notInterestedIds
                    val filteredPageSections = page.sections.map { sec ->
                        sec.copy(items = sec.items.filterNot { item ->
                            item is SearchItem.Song && notInterested.contains(item.song.videoId)
                        })
                    }.filter { it.items.isNotEmpty() }

                    val current = _uiState.value.homeSections.toMutableList()
                    filteredPageSections.forEach { newSec ->
                        if (current.none { it.title.equals(newSec.title, ignoreCase = true) }) {
                            current.add(newSec)
                        }
                    }
                    _uiState.update { it.copy(homeSections = current) }
                    homeContinuationToken = page.continuation
                    prefetchThumbnails(filteredPageSections.take(3).flatMap { it.items.take(4) })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load more home sections failed", e)
            } finally {
                _uiState.update { it.copy(isLoadingMoreHome = false) }
            }
        }
    }

    // ========== Online Playback ==========

    fun playOnlineSong(song: OnlineSong) {
        _uiState.update { 
            it.copy(
                currentOnlineSong = song,
                isLoadingStream = false,
                streamError = null
            )
        }

        // Track played artist for home feed personalization asynchronously
        viewModelScope.launch(Dispatchers.IO) {
            val appSettings = getApplication<Application>().getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE)
            if (!appSettings.getBoolean("pause_history", false)) {
                if (song.artist.isNotBlank() && !song.artist.equals("<unknown>", ignoreCase = true)) {
                    val historyString = prefs.getString("recent_artists_list", "") ?: ""
                    val currentList = if (historyString.isBlank()) {
                        mutableListOf()
                    } else {
                        historyString.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                    }
                    currentList.remove(song.artist.trim())
                    currentList.add(0, song.artist.trim())
                    val trimmedList = currentList.take(3)
                    prefs.edit {
                        putString("recent_artists_list", trimmedList.joinToString("|"))
                            .putString("last_played_artist", song.artist) // Backwards compatibility
                    }
                }
            }
        }

        // EXTREME LATENCY REMOVAL: Deleted explicit blocking network call! 
        // ExoPlayer uses ResolvingDataSource to resolve URIs asynchronously in the background AFTER opening the player instantly!
    }

    /**
     * Play an online song and set queue context.
     */
    fun playOnlineSongWithQueue(song: OnlineSong, queue: List<OnlineSong>, index: Int) {
        _uiState.update { 
            it.copy(
                onlineQueue = queue,
                currentOnlineIndex = index
            )
        }
        playOnlineSong(song)

        // Fire-and-forget trigger informing ExploreFragment to hand over data to main player IMMEDIATELY without waiting.
        _playbackTriggerEvent.tryEmit(Unit)

        // PREFETCH NEXT SONG TO WARM UP THE CACHE INSTANTLY!
        if (index + 1 < queue.size) {
            val nextSong = queue[index + 1]
            YouTubeMusic.prefetchStream(nextSong.videoId)
        }
    }

    fun addToQueueNext(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        val currentIndex = _uiState.value.currentOnlineIndex
        val targetIndex = (if (currentIndex < 0) -1 else currentIndex) + 1
        val safeIndex = targetIndex.coerceIn(0, currentQueue.size)
        currentQueue.add(safeIndex, song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun addToQueueLast(song: OnlineSong) {
        val currentQueue = _uiState.value.onlineQueue.toMutableList()
        currentQueue.add(song)
        _uiState.update { it.copy(onlineQueue = currentQueue) }
    }

    fun updateActiveSongAndIndex(index: Int, song: OnlineSong?) {
        _uiState.update { 
            it.copy(
                currentOnlineIndex = index,
                currentOnlineSong = song
            )
        }
    }

    // ========== Request Deduplication ==========
    private val inFlightRequests = ConcurrentHashMap<String, Job>()

    private fun launchDeduplicated(
        key: String,
        onStart: () -> Unit = {},
        onFinally: () -> Unit = {},
        block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
    ) {
        if (inFlightRequests[key]?.isActive == true) return

        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                onStart()
                block()
            } finally {
                inFlightRequests.remove(key)
                onFinally()
            }
        }
        inFlightRequests[key] = job
    }

    // ========== Detail Pages ==========

    fun loadAlbum(browseId: String) {
        val route = DetailRoute(DetailType.ALBUM, id = browseId)
        launchDeduplicated(
            key = "album:$browseId",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        albumDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.album(browseId)
            result.onSuccess { res ->
                _uiState.update { it.copy(albumDetail = res) }
                viewModelScope.launch(Dispatchers.IO) {
                    prefetchUrls(listOfNotNull(res.album.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                }
            }
            result.onFailure { _uiState.update { it.copy(error = "Failed to load album") } }
        }
    }

    fun loadAlbumForSong(
        songTitle: String,
        artistName: String,
        albumName: String? = null,
        videoId: String? = null,
        fallbackThumbnailUrl: String? = null
    ) {
        val cleanSongTitle = songTitle.trim()
        val cleanArtist = artistName.trim().takeUnless { it.equals("Unknown Artist", ignoreCase = true) } ?: ""
        val cleanAlbum = albumName?.trim()?.takeUnless { it.isBlank() || it.equals("Unknown Album", ignoreCase = true) }
        val displayTitle = cleanAlbum ?: cleanSongTitle

        if (displayTitle.isBlank()) return

        val route = DetailRoute(DetailType.ALBUM, id = "", title = displayTitle, fallbackUrl = fallbackThumbnailUrl)
        launchDeduplicated(
            key = "album_resolve:$displayTitle:$cleanArtist:$videoId",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        albumDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val searchQueries = mutableListOf<String>()
            if (!cleanAlbum.isNullOrBlank()) {
                if (cleanArtist.isNotBlank()) {
                    searchQueries.add("$cleanAlbum $cleanArtist")
                }
                searchQueries.add(cleanAlbum)
            }
            if (cleanSongTitle.isNotBlank()) {
                if (cleanArtist.isNotBlank()) {
                    searchQueries.add("$cleanSongTitle $cleanArtist")
                }
                searchQueries.add(cleanSongTitle)
            }

            var foundAlbum: OnlineAlbum? = null
            for (query in searchQueries.distinct()) {
                val searchRes = YouTubeMusic.search(query, SearchFilter.ALBUMS)
                searchRes.onSuccess { result ->
                    val match = result.items.firstOrNull { it is SearchItem.Album } as? SearchItem.Album
                        ?: (result.items.firstOrNull { it is SearchItem.TopResult } as? SearchItem.TopResult)?.album?.let { SearchItem.Album(it) }
                    if (match != null) {
                        foundAlbum = match.album
                    }
                }
                if (foundAlbum != null) break
            }

            val browseId = foundAlbum?.browseId
            if (!browseId.isNullOrBlank()) {
                _uiState.update { state ->
                    val updatedStack = state.detailStack.mapIndexed { idx, item ->
                        if (idx == state.detailStack.lastIndex && item.type == DetailType.ALBUM && item.id.isBlank()) {
                            item.copy(id = browseId, fallbackUrl = foundAlbum?.thumbnailUrl ?: fallbackThumbnailUrl)
                        } else {
                            item
                        }
                    }
                    state.copy(detailStack = updatedStack)
                }

                val albumResult = YouTubeMusic.album(browseId)
                albumResult.onSuccess { albumPage ->
                    _uiState.update { state ->
                        val thumb = albumPage.album.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                            ?: foundAlbum?.thumbnailUrl
                            ?: fallbackThumbnailUrl
                        val updatedAlbum = albumPage.album.copy(thumbnailUrl = thumb)
                        state.copy(albumDetail = albumPage.copy(album = updatedAlbum))
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        prefetchUrls(listOfNotNull(albumPage.album.thumbnailUrl) + albumPage.songs.mapNotNull { it.thumbnailUrl })
                    }
                }
                albumResult.onFailure {
                    _uiState.update { it.copy(error = "Failed to load album details") }
                }
            } else {
                _uiState.update { it.copy(error = "Album not found") }
            }
        }
    }

    fun loadArtist(browseId: String, fallbackThumbnailUrl: String? = null) {
        val route = DetailRoute(DetailType.ARTIST, id = browseId, fallbackUrl = fallbackThumbnailUrl)
        launchDeduplicated(
            key = "artist:$browseId",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        artistDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.artist(browseId)
            result.onSuccess { res ->
                _uiState.update { state ->
                    val correctedThumbnailUrl = res.artist.thumbnailUrl.takeIf { !it.isNullOrBlank() } ?: fallbackThumbnailUrl
                    val updatedArtist = res.artist.copy(thumbnailUrl = correctedThumbnailUrl)
                    state.copy(artistDetail = res.copy(artist = updatedArtist))
                }
                viewModelScope.launch(Dispatchers.IO) {
                    prefetchUrls(listOfNotNull(res.artist.thumbnailUrl))
                    prefetchThumbnails(res.sections.flatMap { it.items.take(4) })
                }
            }
            result.onFailure { _uiState.update { it.copy(error = "Failed to load artist") } }
        }
    }

    fun loadArtistByName(artistName: String, fallbackThumbnailUrl: String? = null) {
        val cleanName = artistName.trim()
        if (cleanName.isBlank() || cleanName.equals("Unknown Artist", ignoreCase = true)) return

        val route = DetailRoute(DetailType.ARTIST, id = "", title = cleanName, fallbackUrl = fallbackThumbnailUrl)
        launchDeduplicated(
            key = "artist_name:$cleanName",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        artistDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val searchResult = YouTubeMusic.search(cleanName, SearchFilter.ARTISTS)
            searchResult.onSuccess { res ->
                val foundArtist = res.items.firstOrNull { it is SearchItem.Artist } as? SearchItem.Artist
                    ?: (res.items.firstOrNull { it is SearchItem.TopResult } as? SearchItem.TopResult)?.let { top ->
                        top.artist?.let { a -> SearchItem.Artist(a) }
                    }

                val browseId = foundArtist?.artist?.browseId
                if (!browseId.isNullOrBlank()) {
                    _uiState.update { state ->
                        val updatedStack = state.detailStack.mapIndexed { idx, item ->
                            if (idx == state.detailStack.lastIndex && item.type == DetailType.ARTIST && item.id.isBlank()) {
                                item.copy(id = browseId, fallbackUrl = foundArtist.artist.thumbnailUrl ?: fallbackThumbnailUrl)
                            } else {
                                item
                            }
                        }
                        state.copy(detailStack = updatedStack)
                    }

                    val artistResult = YouTubeMusic.artist(browseId)
                    artistResult.onSuccess { artistPage ->
                        _uiState.update { state ->
                            val thumb = artistPage.artist.thumbnailUrl.takeIf { !it.isNullOrBlank() }
                                ?: foundArtist.artist.thumbnailUrl
                                ?: fallbackThumbnailUrl
                            val updatedArtist = artistPage.artist.copy(thumbnailUrl = thumb)
                            state.copy(artistDetail = artistPage.copy(artist = updatedArtist))
                        }
                        viewModelScope.launch(Dispatchers.IO) {
                            prefetchUrls(listOfNotNull(artistPage.artist.thumbnailUrl))
                            prefetchThumbnails(artistPage.sections.flatMap { it.items.take(4) })
                        }
                    }
                    artistResult.onFailure {
                        _uiState.update { it.copy(error = "Failed to load artist details") }
                    }
                } else {
                    _uiState.update { it.copy(error = "Artist not found") }
                }
            }
            searchResult.onFailure {
                _uiState.update { it.copy(error = "Failed to search for artist") }
            }
        }
    }

    private var proactivePlaylistJob: Job? = null

    fun loadPlaylist(playlistId: String, forceRefresh: Boolean = false) {
        val route = DetailRoute(DetailType.PLAYLIST, id = playlistId)
        proactivePlaylistJob?.cancel()
        launchDeduplicated(
            key = "playlist:$playlistId",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        playlistDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.playlist(playlistId, forceRefresh = forceRefresh)
            result.onSuccess { res ->
                _uiState.update { it.copy(playlistDetail = res) }
                viewModelScope.launch(Dispatchers.IO) {
                    prefetchUrls(listOfNotNull(res.playlist.thumbnailUrl) + res.songs.map { it.thumbnailUrl })
                }
                if (!res.continuation.isNullOrBlank()) {
                    startProactivePlaylistLoading(playlistId, res.continuation)
                }
            }
            result.onFailure { _uiState.update { it.copy(error = "Failed to load playlist") } }
        }
    }

    fun loadMorePlaylistSongs() {
        val currentDetail = _uiState.value.playlistDetail ?: return
        val continuation = currentDetail.continuation?.takeIf { it.isNotBlank() } ?: return
        if (proactivePlaylistJob?.isActive == true) return
        startProactivePlaylistLoading(currentDetail.playlist.playlistId, continuation)
    }

    private fun startProactivePlaylistLoading(playlistId: String, initialContinuation: String) {
        proactivePlaylistJob?.cancel()
        proactivePlaylistJob = viewModelScope.launch(Dispatchers.IO) {
            var currentCont: String? = initialContinuation
            var iterations = 0
            val normalizedPlaylistId = playlistId.trim().removePrefix("VL")
            try {
                while (!currentCont.isNullOrBlank() && isActive && iterations < 250) {
                    iterations++
                    delay(60) // Respectful pacing to prevent socket resets/rate-limits on 900+ song playlists

                    val token = currentCont
                    var retryCount = 0
                    var contResult: Result<Pair<List<OnlineSong>, String?>>? = null
                    while (retryCount < 3 && isActive) {
                        val res = YouTubeMusic.playlistContinuation(token)
                        if (res.isSuccess) {
                            contResult = res
                            break
                        }
                        retryCount++
                        delay(300L * retryCount)
                    }

                    if (contResult == null || contResult.isFailure) {
                        Log.w("ExploreViewModel", "Proactive playlist loading stopped after retries at iteration $iterations for $playlistId")
                        break
                    }

                    contResult.onSuccess { (newSongs, nextCont) ->
                        if (newSongs.isEmpty()) {
                            currentCont = null
                            return@onSuccess
                        }
                        _uiState.update { state ->
                            val currentDetail = state.playlistDetail
                            if (currentDetail != null && currentDetail.playlist.playlistId.removePrefix("VL") == normalizedPlaylistId) {
                                val combinedSongs = (currentDetail.songs + newSongs).distinctBy { it.videoId }
                                Log.d("ExploreViewModel", "Loaded playlist continuation batch +${newSongs.size} songs (total ${combinedSongs.size}) for $normalizedPlaylistId")
                                val preservedCount = currentDetail.playlist.songCount?.takeIf { it.isNotBlank() } ?: "${combinedSongs.size} songs"
                                state.copy(
                                    playlistDetail = currentDetail.copy(
                                        playlist = currentDetail.playlist.copy(songCount = preservedCount),
                                        songs = combinedSongs,
                                        continuation = nextCont
                                    )
                                )
                            } else state
                        }
                        prefetchUrls(newSongs.map { it.thumbnailUrl })
                        if (nextCont == currentCont || nextCont.isNullOrBlank()) {
                            currentCont = null
                        } else {
                            currentCont = nextCont
                        }
                    }
                }
            } finally {
                // Terminate loading state and clear continuation if all items are processed or cancelled
                if (currentCont.isNullOrBlank()) {
                    _uiState.update { state ->
                        val currentDetail = state.playlistDetail
                        if (currentDetail != null && currentDetail.playlist.playlistId.removePrefix("VL") == normalizedPlaylistId) {
                            state.copy(playlistDetail = currentDetail.copy(continuation = null))
                        } else state
                    }
                }
            }
        }
    }

    fun loadSectionDetails(browseId: String, params: String?, fallbackTitle: String = "Section") {
        val route = DetailRoute(DetailType.SECTION, id = browseId, params = params, title = fallbackTitle)
        launchDeduplicated(
            key = "section:$browseId:${params ?: ""}",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        sectionDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.section(browseId, params)
            result.onSuccess { res ->
                _uiState.update {
                    val correctedTitle = if (res.title == "Section") fallbackTitle else res.title
                    it.copy(sectionDetail = res.copy(title = correctedTitle))
                }
                viewModelScope.launch(Dispatchers.IO) {
                    prefetchThumbnails(res.items.take(12))
                }
            }
            result.onFailure { _uiState.update { it.copy(error = "Failed to load section") } }
        }
    }

    private val genreArtworkCache = ConcurrentHashMap<String, String>()

    fun loadGenres() {
        launchDeduplicated(
            key = "genres",
            onStart = { _uiState.update { it.copy(isLoadingDetail = true) } },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.moodsAndGenres()
            result.onSuccess { sections ->
                val uiModels = withContext(Dispatchers.Default) {
                    sections.flatMap { section ->
                        section.items.mapNotNull { item ->
                            if (item !is SearchItem.Playlist) return@mapNotNull null
                            val parts = item.playlist.playlistId.split("::")
                            if (parts.size < 2 || parts[0] != "GENRE") return@mapNotNull null
                            val browseId = parts[1]
                            val params = parts.getOrNull(2)?.takeIf { it.isNotEmpty() }
                            val (fallbackGradient, fallbackImageUrl) = getMoodVisuals(item.playlist.title)
                            val cachedUrl = genreArtworkCache["$browseId::${params ?: ""}"]
                            val imageUrl = item.playlist.thumbnailUrl?.takeIf { it.isNotEmpty() }
                                ?: cachedUrl
                                ?: fallbackImageUrl

                            val gradientColors = item.playlist.color?.let { colorLong ->
                                val alpha = ((colorLong shr 24) and 0xFF).toInt().let { if (it == 0) 0xFF else it }
                                val r = ((colorLong shr 16) and 0xFF).toInt()
                                val g = ((colorLong shr 8) and 0xFF).toInt()
                                val b = (colorLong and 0xFF).toInt()
                                val baseColor = androidx.compose.ui.graphics.Color(android.graphics.Color.argb(alpha, r, g, b))
                                createBrightGradientFromBase(baseColor)
                            } ?: fallbackGradient

                            GenreUiModel(
                                id = item.playlist.playlistId,
                                title = item.playlist.title,
                                browseId = browseId,
                                params = params,
                                imageUrl = imageUrl,
                                gradientColors = gradientColors
                            )
                        }
                    }.distinctBy { it.id }
                }

                _uiState.update {
                    it.copy(
                        genresSections = sections,
                        genresUiModels = uiModels,
                        detailStack = pushDetailRoute(it, DetailRoute(DetailType.GENRES, id = "genres"))
                    )
                }

                resolveMissingGenreArtworks(uiModels)
            }
            result.onFailure {
                _uiState.update { it.copy(error = "Failed to load Moods & Genres") }
            }
        }
    }

    private fun resolveMissingGenreArtworks(uiModels: List<GenreUiModel>) {
        viewModelScope.launch(Dispatchers.IO) {
            uiModels.forEach { model ->
                val browseId = model.browseId
                val params = model.params
                val cacheKey = "$browseId::${params ?: ""}"
                val reqKey = "genre_art:$cacheKey"

                if (!genreArtworkCache.containsKey(cacheKey) && inFlightRequests[reqKey]?.isActive != true) {
                    val job = launch(Dispatchers.IO) {
                        try {
                            val catResult = YouTubeMusic.moodCategory(browseId, params)
                            catResult.onSuccess { page ->
                                val resolvedUrl = page.sections.flatMap { it.items }.firstNotNullOfOrNull { item ->
                                    when (item) {
                                        is SearchItem.Playlist -> item.playlist.thumbnailUrl
                                        is SearchItem.Album -> item.album.thumbnailUrl
                                        is SearchItem.Song -> item.song.thumbnailUrl
                                        is SearchItem.Artist -> item.artist.thumbnailUrl
                                        is SearchItem.TopResult -> item.thumbnailUrl
                                        is SearchItem.Header -> null
                                    }?.takeIf { it.isNotEmpty() }
                                }
                                if (!resolvedUrl.isNullOrEmpty()) {
                                    if (genreArtworkCache.size > 200) {
                                        genreArtworkCache.clear()
                                    }
                                    genreArtworkCache[cacheKey] = resolvedUrl
                                    val targetId = model.id
                                    _uiState.update { state ->
                                        val updatedList = state.genresUiModels.map { current ->
                                            if (current.id == targetId) current.copy(imageUrl = resolvedUrl) else current
                                        }
                                        state.copy(genresUiModels = updatedList)
                                    }
                                }
                            }
                        } catch (_: Exception) {} finally {
                            inFlightRequests.remove(reqKey)
                        }
                    }
                    inFlightRequests[reqKey] = job
                }
            }
        }
    }

    private var fetchOnlinePlaylistsJob: Job? = null
    private var lastOnlinePlaylistsFetchTime: Long = 0L
    private val PLAYLIST_CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

    fun loadOnlinePlaylists(forceRefresh: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val isStale = (currentTime - lastOnlinePlaylistsFetchTime) > PLAYLIST_CACHE_TTL_MS
        val hasCache = _uiState.value.onlinePlaylists.isNotEmpty()

        if (hasCache && !isStale && !forceRefresh) return
        if (fetchOnlinePlaylistsJob?.isActive == true) return

        fetchOnlinePlaylistsJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.libraryPlaylists()
                result.onSuccess { playlists ->
                    lastOnlinePlaylistsFetchTime = System.currentTimeMillis()
                    _uiState.update { it.copy(onlinePlaylists = playlists) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load online playlists", e)
            }
        }
    }

    private suspend fun synthesizeCategoryFeed(title: String): List<HomeSection> = coroutineScope {
        val sections = mutableListOf<HomeSection>()
        try {
            val (playQuery, songQuery, albumQuery) = when (title.lowercase()) {
                "charts" -> Triple("Top 100 Charts", "Top 50 Trending", "Top Hit Albums")
                "podcasts" -> Triple("Popular Podcasts", "Podcast Episodes", "Top Shows")
                "new releases" -> Triple("New Music Friday", "Latest Singles", "New Album Releases")
                else -> Triple(title, title, title)
            }

            val playTask = async { YouTubeMusic.search(playQuery, SearchFilter.PLAYLISTS) }
            val songTask = async { YouTubeMusic.search(songQuery, SearchFilter.SONGS) }
            val albumTask = async { YouTubeMusic.search(albumQuery, SearchFilter.ALBUMS) }

            val responses = awaitAll(playTask, songTask, albumTask)
            responses[0].onSuccess { res ->
                if (res.items.isNotEmpty()) sections.add(HomeSection("Featured $title Playlists", res.items.take(20)))
            }
            responses[1].onSuccess { res ->
                if (res.items.isNotEmpty()) sections.add(HomeSection("Top $title Tracks", res.items.take(20)))
            }
            responses[2].onSuccess { res ->
                if (res.items.isNotEmpty()) sections.add(HomeSection("Popular $title Albums", res.items.take(20)))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to synthesize category feed for $title", e)
        }
        sections
    }

    fun loadMood(title: String, browseId: String, params: String?) {
        val route = DetailRoute(DetailType.MOOD, id = browseId, params = params, title = title)
        launchDeduplicated(
            key = "mood:$browseId:${params ?: ""}",
            onStart = {
                _uiState.update {
                    it.copy(
                        isLoadingDetail = true,
                        moodDetail = null,
                        detailStack = pushDetailRoute(it, route)
                    )
                }
            },
            onFinally = { _uiState.update { it.copy(isLoadingDetail = false) } }
        ) {
            val result = YouTubeMusic.moodCategory(browseId, params)
            var fetchedSections = result.getOrNull()?.sections ?: emptyList()

            if (fetchedSections.isEmpty()) {
                fetchedSections = synthesizeCategoryFeed(title)
            }

            if (fetchedSections.isNotEmpty()) {
                _uiState.update {
                    it.copy(moodDetail = MoodDetail(title, fetchedSections))
                }
            } else {
                _uiState.update { it.copy(error = "Failed to load $title details") }
            }
        }
    }
    private fun pushDetailRoute(state: ExploreUiState, route: DetailRoute): List<DetailRoute> {
        val current = state.detailStack
        val maxDepth = 10
        return if (current.size >= maxDepth) {
            current.drop(1) + route
        } else {
            current + route
        }
    }

    fun popDetailStack() {
        _uiState.update { state ->
            if (state.detailStack.isEmpty()) return@update state
            val nextStack = state.detailStack.dropLast(1)
            val popped = state.detailStack.last()

            val reqKey = when (popped.type) {
                DetailType.ALBUM -> "album:${popped.id}"
                DetailType.PLAYLIST -> "playlist:${popped.id}"
                DetailType.ARTIST -> "artist:${popped.id}"
                DetailType.SECTION -> "section:${popped.id}:${popped.params ?: ""}"
                DetailType.GENRES -> "genres"
                DetailType.MOOD -> "mood:${popped.id}:${popped.params ?: ""}"
            }
            inFlightRequests[reqKey]?.cancel()
            inFlightRequests.remove(reqKey)

            state.copy(detailStack = nextStack)
        }
    }

    fun resetToHome() {
        inFlightRequests.entries.filter { 
            it.key.startsWith("album:") || 
            it.key.startsWith("artist:") || 
            it.key.startsWith("playlist:") || 
            it.key.startsWith("section:") || 
            it.key.startsWith("mood:") || 
            it.key.startsWith("genres") || 
            it.key.startsWith("search:")
        }.forEach { (key, job) ->
            job.cancel()
            inFlightRequests.remove(key)
        }
        searchJob?.cancel()
        loadMoreSearchJob?.cancel()

        _uiState.update { state ->
            state.copy(
                albumDetail = null,
                artistDetail = null,
                playlistDetail = null,
                sectionDetail = null,
                moodDetail = null,
                genresSections = emptyList(),
                genresUiModels = emptyList(),
                detailStack = emptyList(),
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                isLoadingSuggestions = false
            )
        }
        cameFromLibrary = false
    }
    // ========== Start Radio ==========

    fun startRadio(videoId: String) {
        _uiState.update { it.copy(isLoadingStream = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.startRadio(videoId = videoId)
                result.onSuccess { radioSongs ->
                    if (radioSongs.isNotEmpty()) {
                        playOnlineSongWithQueue(radioSongs.first(), radioSongs, 0)
                    }
                }
                result.onFailure { 
                    _uiState.update { it.copy(error = "Failed to start radio") }
                }
            } finally {
                _uiState.update { it.copy(isLoadingStream = false) }
            }
        }
    }

    fun startRadioForItem(item: SearchItem) {
        _uiState.update { it.copy(isLoadingStream = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (vId, pId) = when (item) {
                    is SearchItem.TopResult -> {
                        when {
                            item.song != null -> item.song.videoId to "RDAMVM${item.song.videoId}"
                            item.playlist != null -> null to "RDAMPL${item.playlist.playlistId}"
                            item.album != null -> null to "RDAMPL${item.album.playlistId?.takeIf { it.isNotEmpty() } ?: item.album.browseId}"
                            item.artist != null -> null to "RDAMPR${item.artist.browseId}"
                            else -> null to null
                        }
                    }
                    is SearchItem.Header -> null to null
                    is SearchItem.Song -> item.song.videoId to "RDAMVM${item.song.videoId}"
                    is SearchItem.Playlist -> null to "RDAMPL${item.playlist.playlistId}"
                    is SearchItem.Album -> null to "RDAMPL${item.album.playlistId?.takeIf { it.isNotEmpty() } ?: item.album.browseId}"
                    is SearchItem.Artist -> null to "RDAMPR${item.artist.browseId}"
                }
                
                if (vId == null && pId == null) return@launch

                val result = YouTubeMusic.startRadio(videoId = vId, playlistId = pId)
                result.onSuccess { radioSongs ->
                    if (radioSongs.isNotEmpty()) {
                        playOnlineSongWithQueue(radioSongs.first(), radioSongs, 0)
                    } else {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("No radio station available")
                    }
                }
                result.onFailure { 
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to start radio")
                }
            } finally {
                _uiState.update { it.copy(isLoadingStream = false) }
            }
        }
    }

    fun saveItemToLibrary(item: SearchItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val isLoggedIn = AccountManager.isLoggedIn(context)
                var savedOnline = false

                if (isLoggedIn) {
                    val result = when (item) {
                        is SearchItem.TopResult -> {
                            when {
                                item.song != null -> YouTubeMusic.updateLikeStatus(item.song.videoId, YouTubeMusic.LikeStatus.LIKE)
                                item.playlist != null -> YouTubeMusic.updateLikeStatus(item.playlist.playlistId, YouTubeMusic.LikeStatus.LIKE)
                                item.album != null -> YouTubeMusic.updateLikeStatus(item.album.playlistId ?: item.album.browseId, YouTubeMusic.LikeStatus.LIKE)
                                item.artist != null -> YouTubeMusic.subscribeArtist(item.artist.browseId)
                                else -> null
                            }
                        }
                        is SearchItem.Header -> null
                        is SearchItem.Song -> YouTubeMusic.updateLikeStatus(item.song.videoId, YouTubeMusic.LikeStatus.LIKE)
                        is SearchItem.Playlist -> YouTubeMusic.updateLikeStatus(item.playlist.playlistId, YouTubeMusic.LikeStatus.LIKE)
                        is SearchItem.Album -> YouTubeMusic.updateLikeStatus(item.album.playlistId ?: item.album.browseId, YouTubeMusic.LikeStatus.LIKE)
                        is SearchItem.Artist -> YouTubeMusic.subscribeArtist(item.artist.browseId)
                    }

                    if (result != null && result.isSuccess) {
                        savedOnline = true
                        val name = when (item) {
                            is SearchItem.TopResult -> item.title
                            is SearchItem.Header -> ""
                            is SearchItem.Song -> item.song.title
                            is SearchItem.Playlist -> item.playlist.title
                            is SearchItem.Album -> item.album.title
                            is SearchItem.Artist -> item.artist.title
                        }
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("'$name' saved to Library!")
                    }
                }

                if (!savedOnline) {
                    saveItemLocally(item)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving item to library, trying local save", e)
                saveItemLocally(item)
            }
        }
    }

    private suspend fun saveItemLocally(item: SearchItem) {
        try {
            val playlistDao = MusicDatabase.getDatabase(getApplication()).playlistDao()

            when (item) {
                is SearchItem.Playlist -> {
                    savePlaylistLocally(item.playlist, playlistDao)
                }
                is SearchItem.Album -> {
                    saveAlbumLocally(item.album, playlistDao)
                }
                is SearchItem.Song -> {
                    saveSongLocally(item.song, playlistDao)
                }
                is SearchItem.Artist -> {
                    saveArtistLocally(item.artist, playlistDao)
                }
                is SearchItem.TopResult -> {
                    when {
                        item.playlist != null -> savePlaylistLocally(item.playlist, playlistDao)
                        item.album != null -> saveAlbumLocally(item.album, playlistDao)
                        item.song != null -> saveSongLocally(item.song, playlistDao)
                        item.artist != null -> saveArtistLocally(item.artist, playlistDao)
                        else -> {
                            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Cannot save this item")
                        }
                    }
                }
                is SearchItem.Header -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save item locally", e)
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to save to local library")
        }
    }

    private suspend fun savePlaylistLocally(playlist: OnlinePlaylist, playlistDao: PlaylistDao) {
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saving '${playlist.title}' locally...")

        val pageResult = YouTubeMusic.playlist(playlist.playlistId)
        val songs = pageResult.getOrNull()?.songs ?: emptyList()
        val thumb = playlist.thumbnailUrl ?: pageResult.getOrNull()?.playlist?.thumbnailUrl

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                title = playlist.title,
                thumbnailUrl = thumb
            )
        )

        if (songs.isNotEmpty()) {
            val songEntities = songs.mapIndexed { index, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = playlist.title,
                    duration = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl,
                    position = index,
                    isExplicit = song.isExplicit
                )
            }
            playlistDao.insertPlaylistSongs(songEntities)
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${playlist.title}' (${songs.size} tracks) to Local Library!")
        } else {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${playlist.title}' to Local Library!")
        }
    }

    private suspend fun saveAlbumLocally(album: OnlineAlbum, playlistDao: PlaylistDao) {
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saving '${album.title}' locally...")

        val pageResult = YouTubeMusic.album(album.browseId)
        val songs = pageResult.getOrNull()?.songs ?: emptyList()
        val thumb = album.thumbnailUrl ?: pageResult.getOrNull()?.album?.thumbnailUrl

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                title = album.title,
                thumbnailUrl = thumb
            )
        )

        if (songs.isNotEmpty()) {
            val songEntities = songs.mapIndexed { index, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = album.title,
                    duration = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl ?: thumb,
                    position = index,
                    isExplicit = song.isExplicit
                )
            }
            playlistDao.insertPlaylistSongs(songEntities)
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${album.title}' (${songs.size} tracks) to Local Library!")
        } else {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${album.title}' to Local Library!")
        }
    }

    private suspend fun saveSongLocally(song: OnlineSong, playlistDao: PlaylistDao) {
        val allPlaylists = playlistDao.getAllPlaylists().firstOrNull() ?: emptyList()
        val favPlaylist = allPlaylists.firstOrNull { 
            it.title.equals("Liked Songs", ignoreCase = true) || it.title.equals("Favorites", ignoreCase = true) 
        }
        val targetPlaylistId = favPlaylist?.id ?: playlistDao.insertPlaylist(
            PlaylistEntity(
                title = "Liked Songs",
                thumbnailUrl = song.thumbnailUrl
            )
        )

        val currentCount = playlistDao.getSongCountForPlaylistSync(targetPlaylistId)
        val songEntity = PlaylistSongEntity(
            playlistId = targetPlaylistId,
            songId = song.videoId,
            title = song.title,
            artist = song.artist,
            album = song.albumName ?: "",
            duration = song.durationMs,
            thumbnailUrl = song.thumbnailUrl,
            position = currentCount,
            isExplicit = song.isExplicit
        )
        playlistDao.insertPlaylistSong(songEntity)
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${song.title}' to Liked Songs!")
    }

    private suspend fun saveArtistLocally(artist: OnlineArtist, playlistDao: PlaylistDao) {
        val artistPageResult = YouTubeMusic.artist(artist.browseId)
        val artistPage = artistPageResult.getOrNull()

        val topSongs = artistPage?.sections
            ?.firstOrNull { it.title.contains("songs", ignoreCase = true) || it.title.contains("popular", ignoreCase = true) }
            ?.items?.filterIsInstance<SearchItem.Song>()?.map { it.song }
            ?: emptyList()

        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                title = "${artist.title} (Top Tracks)",
                thumbnailUrl = artist.thumbnailUrl
            )
        )

        if (topSongs.isNotEmpty()) {
            val songEntities = topSongs.mapIndexed { index, song ->
                PlaylistSongEntity(
                    playlistId = playlistId,
                    songId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    album = "${artist.title} (Top Tracks)",
                    duration = song.durationMs,
                    thumbnailUrl = song.thumbnailUrl ?: artist.thumbnailUrl,
                    position = index,
                    isExplicit = song.isExplicit
                )
            }
            playlistDao.insertPlaylistSongs(songEntities)
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${artist.title}' to Local Library!")
        } else {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Saved '${artist.title}' to Local Library!")
        }
    }

    // ========== YouTube Playlist Management ==========

    fun subscribeToArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = true)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.subscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Subscribed to artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Subscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Subscribe error", e)
            }
        }
    }

    fun unsubscribeFromArtist(channelId: String) {
        // Optimistically update UI state
        _uiState.update { state ->
            if (state.artistDetail?.artist?.browseId == channelId) {
                state.copy(
                    artistDetail = state.artistDetail.copy(
                        artist = state.artistDetail.artist.copy(isSubscribed = false)
                    )
                )
            } else {
                state
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = YouTubeMusic.unsubscribeArtist(channelId)
                result.onSuccess {
                    Log.d(TAG, "Unsubscribed from artist $channelId")
                    _subscriptionChanged.value = true
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = false)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
                result.onFailure { e ->
                    Log.e(TAG, "Unsubscribe failed, reverting", e)
                    _uiState.update { state ->
                        if (state.artistDetail?.artist?.browseId == channelId) {
                            state.copy(
                                artistDetail = state.artistDetail.copy(
                                    artist = state.artistDetail.artist.copy(isSubscribed = true)
                                )
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unsubscribe error", e)
            }
        }
    }



    fun clearError() {
        _uiState.update { 
            it.copy(
                error = null,
                streamError = null
            )
        }
    }

    // ========== Speed Dial & Not Interested ==========

    private val gson = com.google.gson.Gson()

    fun getPinnedSpeedDialSongs(): List<OnlineSong> {
        val json = prefs.getString("speed_dial_songs_json", null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<OnlineSong>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun savePinnedSpeedDialSongs(songs: List<OnlineSong>) {
        prefs.edit { putString("speed_dial_songs_json", gson.toJson(songs)) }
        _uiState.update { state -> state.copy(pinnedSpeedDialIds = songs.map { it.videoId }.toSet()) }
    }

    fun pinToSpeedDial(song: OnlineSong) {
        val current = getPinnedSpeedDialSongs().toMutableList()
        val exists = current.any { it.videoId == song.videoId }
        if (exists) {
            current.removeAll { it.videoId == song.videoId }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.INDIFFERENT)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send indifferent status to YT", e)
                }
            }
        } else {
            current.add(0, song)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.LIKE)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send like status to YT", e)
                }
            }
        }
        savePinnedSpeedDialSongs(current)
        
        viewModelScope.launch(Dispatchers.IO) {
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
        }
    }

    fun setNotInterested(song: OnlineSong) {
        val current = _uiState.value.notInterestedIds.toMutableSet()
        current.add(song.videoId)
        _uiState.update { it.copy(notInterestedIds = current) }
        
        prefs.edit { putStringSet("not_interested_ids", current) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                YouTubeMusic.updateLikeStatus(song.videoId, YouTubeMusic.LikeStatus.DISLIKE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send not interested/dislike feedback to YouTube Music", e)
            }
            
            val mergedSections = fetchMergedHomeFeed()
            _uiState.update { it.copy(homeSections = mergedSections) }
        }

        val filteredSearchResults = _uiState.value.searchResults.filterNot { item ->
            item is SearchItem.Song && item.song.videoId == song.videoId
        }
        
        val updatedAlbumDetail = _uiState.value.albumDetail?.let { album ->
            album.copy(songs = album.songs.filterNot { it.videoId == song.videoId })
        }
        val updatedPlaylistDetail = _uiState.value.playlistDetail?.let { playlist ->
            playlist.copy(songs = playlist.songs.filterNot { it.videoId == song.videoId })
        }

        _uiState.update { state ->
            state.copy(
                searchResults = filteredSearchResults,
                albumDetail = updatedAlbumDetail,
                playlistDetail = updatedPlaylistDetail
            )
        }
    }

    private fun prefetchThumbnails(items: List<SearchItem>) {
        val urls = items.mapNotNull { item ->
            when (item) {
                is SearchItem.TopResult -> item.thumbnailUrl
                is SearchItem.Header -> null
                is SearchItem.Song -> item.song.thumbnailUrl
                is SearchItem.Album -> item.album.thumbnailUrl
                is SearchItem.Artist -> item.artist.thumbnailUrl
                is SearchItem.Playlist -> item.playlist.thumbnailUrl
            }
        }
        prefetchUrls(urls)
    }

    private fun prefetchUrls(urls: List<String?>) {
        val applicationContext = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val loader = applicationContext.imageLoader
            urls.forEach { url ->
                if (!url.isNullOrBlank()) {
                    val optimizedUrl = url.resizeCDN(288)
                    val req = ImageRequest.Builder(applicationContext)
                        .data(optimizedUrl)
                        .size(288)
                        .allowHardware(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    loader.enqueue(req)
                }
            }
        }
    }
}

@Stable
data class ExploreUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val homeSections: List<HomeSection> = emptyList(),
    val isLoadingHome: Boolean = false,
    val isLoadingStream: Boolean = false,
    val currentMood: String? = null,
    val currentOnlineSong: OnlineSong? = null,
    val error: String? = null,
    val albumDetail: AlbumPage? = null,
    val artistDetail: ArtistPage? = null,
    val playlistDetail: PlaylistPage? = null,
    val sectionDetail: HomeSection? = null,
    val moodDetail: MoodDetail? = null,
    val genresSections: List<HomeSection> = emptyList(),
    val genresUiModels: List<GenreUiModel> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val isRefreshing: Boolean = false,
    val searchHistory: List<String> = emptyList(),
    val homeMoods: List<String> = emptyList(),
    val searchFilter: SearchFilter? = null,
    val streamUrl: String? = null,
    val streamError: String? = null,
    val onlineQueue: List<OnlineSong> = emptyList(),
    val currentOnlineIndex: Int = -1,
    val isLoadingMoreHome: Boolean = false,
    val pinnedSpeedDialIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
    val detailStack: List<DetailRoute> = emptyList(),
    val onlinePlaylists: List<OnlinePlaylist> = emptyList()
)
