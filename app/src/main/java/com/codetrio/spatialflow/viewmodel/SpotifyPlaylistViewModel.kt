package com.codetrio.spatialflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.spotify.SpotifyClient
import com.codetrio.spatialflow.data.spotify.SpotifyPlaylist
import com.codetrio.spatialflow.data.spotify.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for a single Spotify playlist screen.
 * Loads playlist metadata and full paginated track list.
 */
class SpotifyPlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val _playlist = MutableStateFlow<SpotifyPlaylist?>(null)
    val playlist = _playlist.asStateFlow()

    private val _tracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private var currentPlaylistId: String = ""

    fun load(playlistId: String) {
        if (playlistId == currentPlaylistId && _tracks.value.isNotEmpty()) return
        currentPlaylistId = playlistId
        reload()
    }

    fun reload() {
        val playlistId = currentPlaylistId.takeIf(String::isNotBlank) ?: return
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load playlist metadata
                SpotifyClient.playlist(playlistId)
                    .onSuccess { _playlist.value = it }
                    .onFailure {
                        android.util.Log.w("SpotifyPlaylistVM", "Failed to load playlist", it)
                        _errorMessage.value = it.message ?: "Failed to load playlist"
                    }

                // Load all tracks (paginated with proactive streaming)
                val allTracks = mutableListOf<SpotifyTrack>()
                var offset = 0
                val pageSize = 100
                var hasMore = true
                var isFirstPage = true

                while (hasMore) {
                    SpotifyClient.playlistTracks(playlistId, limit = pageSize, offset = offset)
                        .onSuccess { page ->
                            val fetched = page.items.mapNotNull { it.track?.takeUnless { t -> t.isLocal } }
                            allTracks.addAll(fetched)
                            _tracks.value = allTracks.toList()
                            if (isFirstPage) {
                                _isLoading.value = false
                                isFirstPage = false
                            }
                            offset += page.items.size
                            hasMore = page.items.size == pageSize && offset < page.total
                        }
                        .onFailure { e ->
                            android.util.Log.e("SpotifyPlaylistVM", "Failed to load tracks at offset $offset", e)
                            if (allTracks.isEmpty()) {
                                _errorMessage.value = e.message ?: "Failed to load playlist tracks"
                            }
                            hasMore = false
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("SpotifyPlaylistVM", "Unexpected error", e)
                _errorMessage.value = e.message ?: "Failed to load playlist"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
