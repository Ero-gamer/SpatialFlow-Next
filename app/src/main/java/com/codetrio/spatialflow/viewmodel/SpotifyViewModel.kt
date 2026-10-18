package com.codetrio.spatialflow.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.spotify.SpotifyAuth
import com.codetrio.spatialflow.data.spotify.SpotifyClient
import com.codetrio.spatialflow.data.spotify.SpotifyHomeFeed
import com.codetrio.spatialflow.data.spotify.SpotifyPlaylist
import com.codetrio.spatialflow.data.spotify.SpotifyPrefs
import com.codetrio.spatialflow.data.spotify.SpotifyTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SpotifyAuthState {
    NOT_CONNECTED,
    LOADING,
    CONNECTED,
    ERROR,
}

/**
 * Manages Spotify authentication state and library data.
 * Follows the same AndroidViewModel pattern as AccountViewModel.
 */
class SpotifyViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _authState = MutableStateFlow(SpotifyAuthState.NOT_CONNECTED)
    val authState = _authState.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    private val _likedTracks = MutableStateFlow<List<SpotifyTrack>>(emptyList())
    val likedTracks = _likedTracks.asStateFlow()

    private val _homeFeed = MutableStateFlow<SpotifyHomeFeed?>(null)
    val homeFeed = _homeFeed.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError = _homeError.asStateFlow()

    private val _accountName = MutableStateFlow("")
    val accountName = _accountName.asStateFlow()

    private val _accountAvatarUrl = MutableStateFlow<String?>(null)
    val accountAvatarUrl = _accountAvatarUrl.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        // Register auto-renewal hook with SpotifyClient
        SpotifyClient.setTokenRefresher {
            refreshTokenSilently()
        }

        // Restore session if we have a saved sp_dc
        if (SpotifyPrefs.isConnected(context)) {
            restoreSession()
        }
    }

    /**
     * Silently refreshes the access token using saved sp_dc / sp_key cookies.
     * Returns true if renewal succeeded, false otherwise.
     */
    suspend fun refreshTokenSilently(): Boolean {
        val spDc = SpotifyPrefs.getSpDc(context)
        val spKey = SpotifyPrefs.getSpKey(context)
        if (spDc.isBlank()) return false

        return SpotifyAuth.fetchAccessToken(spDc, spKey)
            .map { token ->
                SpotifyClient.setToken(token.accessToken)
                SpotifyPrefs.saveAccessToken(context, token.accessToken)
                SpotifyPrefs.saveTokenExpiresAt(context, token.accessTokenExpirationTimestampMs)
                true
            }
            .getOrDefault(false)
    }

    /** Restores a persisted session on startup. */
    private fun restoreSession() {
        _authState.value = SpotifyAuthState.LOADING
        // Restore cached account info immediately for instant UI
        _accountName.value = SpotifyPrefs.getAccountName(context)
        _accountAvatarUrl.value = SpotifyPrefs.getAccountAvatarUrl(context)

        viewModelScope.launch(Dispatchers.IO) {
            if (!SpotifyPrefs.isTokenExpiringSoon(context)) {
                SpotifyClient.setToken(SpotifyPrefs.getAccessToken(context))
                loadLibrary()
            } else {
                // Token expired or expiring soon, re-authenticate with saved sp_dc
                val spDc = SpotifyPrefs.getSpDc(context)
                val spKey = SpotifyPrefs.getSpKey(context)
                if (spDc.isNotBlank()) {
                    authenticateAndLoad(spDc, spKey)
                } else {
                    _authState.value = SpotifyAuthState.NOT_CONNECTED
                }
            }
        }
    }

    /**
     * Called when a valid access token is extracted directly from the webview session.
     */
    fun connectWithDirectToken(accessToken: String, expiresAtMs: Long, spDc: String = "", spKey: String = "") {
        if (accessToken.isBlank()) return
        _authState.value = SpotifyAuthState.LOADING
        _errorMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            if (spDc.isNotBlank()) SpotifyPrefs.saveSpDc(context, spDc)
            if (spKey.isNotBlank()) SpotifyPrefs.saveSpKey(context, spKey)
            SpotifyClient.setToken(accessToken)
            SpotifyPrefs.saveAccessToken(context, accessToken)
            val expiry = if (expiresAtMs > 0) expiresAtMs else System.currentTimeMillis() + 3600_000L
            SpotifyPrefs.saveTokenExpiresAt(context, expiry)
            loadLibrary()
        }
    }

    /**
     * Called from SpotifySignInScreen after the WebView captures the cookies.
     */
    fun connectWithCookies(spDc: String, spKey: String) {
        _authState.value = SpotifyAuthState.LOADING
        _errorMessage.value = null
        viewModelScope.launch(Dispatchers.IO) {
            // Persist cookies immediately
            SpotifyPrefs.saveSpDc(context, spDc)
            SpotifyPrefs.saveSpKey(context, spKey)
            authenticateAndLoad(spDc, spKey)
        }
    }

    /**
     * Connects with manually pasted token/cookie input.
     * Automatically extracts sp_dc and sp_key and provides a result callback.
     */
    fun loginWithManualToken(
        tokenInput: String,
        manualSpKey: String = "",
        onResult: (Result<String>) -> Unit = {}
    ) {
        val (spDc, spKey) = SpotifyAuth.parseCookieInput(tokenInput, manualSpKey)
        if (spDc.isBlank()) {
            val error = IllegalArgumentException("sp_dc token could not be found. Please check your input.")
            _errorMessage.value = error.message
            onResult(Result.failure(error))
            return
        }

        _authState.value = SpotifyAuthState.LOADING
        _errorMessage.value = null

        viewModelScope.launch(Dispatchers.IO) {
            SpotifyAuth.fetchAccessToken(spDc, spKey)
                .onSuccess { token ->
                    SpotifyPrefs.saveSpDc(context, spDc)
                    SpotifyPrefs.saveSpKey(context, spKey)
                    SpotifyClient.setToken(token.accessToken)
                    SpotifyPrefs.saveAccessToken(context, token.accessToken)
                    SpotifyPrefs.saveTokenExpiresAt(context, token.accessTokenExpirationTimestampMs)

                    loadLibrary()
                    val name = _accountName.value.ifEmpty { "Spotify User" }
                    withContext(Dispatchers.Main) {
                        onResult(Result.success(name))
                    }
                }
                .onFailure { e ->
                    android.util.Log.e("SpotifyViewModel", "Manual token auth failed", e)
                    _authState.value = SpotifyAuthState.ERROR
                    val errorMsg = if (e.message?.contains("anonymous", ignoreCase = true) == true) {
                        "Invalid or expired sp_dc token. Please copy a fresh token from your browser."
                    } else {
                        e.message ?: "Authentication failed. Please verify your token."
                    }
                    _errorMessage.value = errorMsg
                    withContext(Dispatchers.Main) {
                        onResult(Result.failure(Exception(errorMsg, e)))
                    }
                }
        }
    }

    private suspend fun authenticateAndLoad(spDc: String, spKey: String) {
        SpotifyAuth.fetchAccessToken(spDc, spKey)
            .onSuccess { token ->
                SpotifyClient.setToken(token.accessToken)
                SpotifyPrefs.saveAccessToken(context, token.accessToken)
                SpotifyPrefs.saveTokenExpiresAt(context, token.accessTokenExpirationTimestampMs)
                loadLibrary()
            }
            .onFailure { e ->
                android.util.Log.e("SpotifyViewModel", "Auth failed", e)
                _authState.value = SpotifyAuthState.ERROR
                _errorMessage.value = e.message ?: "Authentication failed"
            }
    }

    private suspend fun loadLibrary() {
        _authState.value = SpotifyAuthState.LOADING

        try {
            kotlinx.coroutines.supervisorScope {
                val profileDeferred = async { runCatching { SpotifyClient.me() }.getOrElse { Result.failure(it) } }
                val playlistsDeferred = async {
                    val allPlaylists = mutableListOf<SpotifyPlaylist>()
                    var offset = 0
                    val pageSize = 50
                    var hasMore = true
                    var playlistLoadError: Throwable? = null

                    while (hasMore) {
                        runCatching { SpotifyClient.myPlaylists(limit = pageSize, offset = offset) }
                            .getOrElse { Result.failure(it) }
                            .onSuccess { page ->
                                allPlaylists.addAll(page.items)
                                offset += page.items.size
                                hasMore = page.items.size == pageSize && offset < page.total
                            }
                            .onFailure { e ->
                                android.util.Log.e("SpotifyViewModel", "Failed to load playlists at offset $offset", e)
                                playlistLoadError = e
                                hasMore = false
                            }
                    }
                    Pair(allPlaylists, playlistLoadError)
                }
                val likedDeferred = async { runCatching { SpotifyClient.likedSongs(limit = 50, offset = 0) }.getOrElse { Result.failure(it) } }
                val homeDeferred = async { runCatching { SpotifyClient.home() }.getOrElse { Result.failure(it) } }

                // Await user profile
                profileDeferred.await()
                    .onSuccess { user ->
                        val name = user.displayName ?: user.id
                        val avatar = user.images.maxByOrNull { it.width ?: 0 }?.url ?: user.images.firstOrNull()?.url
                        _accountName.value = name
                        _accountAvatarUrl.value = avatar
                        SpotifyPrefs.saveAccountName(context, name)
                        SpotifyPrefs.saveAccountAvatarUrl(context, avatar)
                    }
                    .onFailure { e ->
                        android.util.Log.w("SpotifyViewModel", "Failed to load profile", e)
                    }

                // Await playlists
                val playlistRes = playlistsDeferred.await()
                val allPlaylists = playlistRes.first
                val playlistLoadError = playlistRes.second
                if (allPlaylists.isEmpty() && playlistLoadError != null) {
                    _errorMessage.value = playlistLoadError.message ?: "Failed to load Spotify playlists"
                    _authState.value = SpotifyAuthState.ERROR
                    return@supervisorScope
                }
                _playlists.value = allPlaylists

                // Await liked songs
                likedDeferred.await()
                    .onSuccess { page ->
                        _likedTracks.value = page.items.map { it.track }
                    }
                    .onFailure { e ->
                        android.util.Log.w("SpotifyViewModel", "Failed to load liked songs", e)
                    }

                // Await home feed
                homeDeferred.await()
                    .onSuccess {
                        _homeFeed.value = it
                        _homeError.value = null
                    }
                    .onFailure { e ->
                        android.util.Log.w("SpotifyViewModel", "Failed to load Spotify Home", e)
                        _homeError.value = e.message ?: "Failed to load Spotify Home"
                    }

                _authState.value = SpotifyAuthState.CONNECTED
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyViewModel", "Unhandled exception in loadLibrary", e)
            _errorMessage.value = e.message ?: "An unexpected error occurred while loading Spotify"
            _authState.value = SpotifyAuthState.ERROR
        }
    }

    /** Re-fetch library data from the server. */
    fun reload() {
        if (_authState.value == SpotifyAuthState.LOADING) return
        viewModelScope.launch(Dispatchers.IO) {
            val spDc = SpotifyPrefs.getSpDc(context)
            if (spDc.isBlank()) {
                _authState.value = SpotifyAuthState.NOT_CONNECTED
                return@launch
            }
            if (!SpotifyPrefs.isTokenExpiringSoon(context)) {
                SpotifyClient.setToken(SpotifyPrefs.getAccessToken(context))
                loadLibrary()
            } else {
                authenticateAndLoad(spDc, SpotifyPrefs.getSpKey(context))
            }
        }
    }

    /** Clears all Spotify credentials and state. */
    fun logout() {
        SpotifyPrefs.clearAll(context)
        SpotifyClient.clearToken()
        _authState.value = SpotifyAuthState.NOT_CONNECTED
        _playlists.value = emptyList()
        _likedTracks.value = emptyList()
        _homeFeed.value = null
        _homeError.value = null
        _accountName.value = ""
        _accountAvatarUrl.value = null
        _errorMessage.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
        if (_authState.value == SpotifyAuthState.ERROR) {
            _authState.value = if (SpotifyPrefs.isConnected(context))
                SpotifyAuthState.NOT_CONNECTED
            else
                SpotifyAuthState.NOT_CONNECTED
        }
    }
}
