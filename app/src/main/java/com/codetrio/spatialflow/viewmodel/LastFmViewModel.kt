package com.codetrio.spatialflow.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.db.LastFmScrobbleDao
import com.codetrio.spatialflow.data.lastfm.LastFmArtworkResolver
import com.codetrio.spatialflow.data.lastfm.LastFmAuth
import com.codetrio.spatialflow.data.lastfm.LastFmClient
import com.codetrio.spatialflow.data.lastfm.LastFmLovedTrack
import com.codetrio.spatialflow.data.lastfm.LastFmPrefs
import com.codetrio.spatialflow.data.lastfm.LastFmRecentTrack
import com.codetrio.spatialflow.data.lastfm.LastFmTopTrack
import com.codetrio.spatialflow.domain.lastfm.LastFmScrobbleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LastFmTab {
    RECENT,
    TOP_TRACKS,
    LOVED_TRACKS
}

@HiltViewModel
class LastFmViewModel @Inject constructor(
    private val application: Application,
    private val lastFmScrobbleDao: LastFmScrobbleDao,
    private val lastFmScrobbleManager: LastFmScrobbleManager
) : AndroidViewModel(application) {

    private val appContext: Context get() = application.applicationContext
    private val TAG = "LastFmViewModel"

    private val _isLoggedIn = MutableStateFlow(LastFmPrefs.isLoggedIn(appContext))
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _username = MutableStateFlow(LastFmPrefs.getUsername(appContext))
    val username: StateFlow<String> = _username.asStateFlow()

    private val _avatarUrl = MutableStateFlow(LastFmPrefs.getAvatarUrl(appContext))
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()

    private val _scrobbleCount = MutableStateFlow(LastFmPrefs.getScrobbleCount(appContext))
    val scrobbleCount: StateFlow<Long> = _scrobbleCount.asStateFlow()

    private val _isScrobblingEnabled = MutableStateFlow(LastFmPrefs.isScrobblingEnabled(appContext))
    val isScrobblingEnabled: StateFlow<Boolean> = _isScrobblingEnabled.asStateFlow()

    private val _isNowPlayingEnabled = MutableStateFlow(LastFmPrefs.isNowPlayingEnabled(appContext))
    val isNowPlayingEnabled: StateFlow<Boolean> = _isNowPlayingEnabled.asStateFlow()

    private val _thresholdPercent = MutableStateFlow(LastFmPrefs.getScrobbleThresholdPercent(appContext))
    val thresholdPercent: StateFlow<Int> = _thresholdPercent.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(LastFmPrefs.isWifiOnly(appContext))
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _isSyncLovedTracks = MutableStateFlow(LastFmPrefs.isSyncLovedTracks(appContext))
    val isSyncLovedTracks: StateFlow<Boolean> = _isSyncLovedTracks.asStateFlow()

    val pendingScrobblesCount: StateFlow<Int> = lastFmScrobbleDao.getPendingCountFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _selectedTab = MutableStateFlow(LastFmTab.RECENT)
    val selectedTab: StateFlow<LastFmTab> = _selectedTab.asStateFlow()

    private val _topTracksPeriod = MutableStateFlow("7day")
    val topTracksPeriod: StateFlow<String> = _topTracksPeriod.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<LastFmRecentTrack>>(emptyList())
    val recentTracks: StateFlow<List<LastFmRecentTrack>> = _recentTracks.asStateFlow()

    private val _topTracks = MutableStateFlow<List<LastFmTopTrack>>(emptyList())
    val topTracks: StateFlow<List<LastFmTopTrack>> = _topTracks.asStateFlow()

    private val _lovedTracks = MutableStateFlow<List<LastFmLovedTrack>>(emptyList())
    val lovedTracks: StateFlow<List<LastFmLovedTrack>> = _lovedTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        if (_isLoggedIn.value) {
            refreshAll()
        }
    }

    fun setSelectedTab(tab: LastFmTab) {
        _selectedTab.value = tab
        if (_isLoggedIn.value) {
            when (tab) {
                LastFmTab.RECENT -> if (_recentTracks.value.isEmpty()) refreshRecentTracks()
                LastFmTab.TOP_TRACKS -> if (_topTracks.value.isEmpty()) refreshTopTracks()
                LastFmTab.LOVED_TRACKS -> if (_lovedTracks.value.isEmpty()) refreshLovedTracks()
            }
        }
    }

    fun setTopTracksPeriod(period: String) {
        _topTracksPeriod.value = period
        if (_isLoggedIn.value) {
            refreshTopTracks(period)
        }
    }

    fun getAuthUrl(): String {
        return LastFmAuth.buildAuthUrl(appContext)
    }

    fun loginWithToken(token: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = LastFmAuth.getSession(appContext, token)
            _isLoading.value = false
            if (result.isSuccess) {
                val session = result.getOrNull()
                _isLoggedIn.value = true
                _username.value = session?.name ?: LastFmPrefs.getUsername(appContext)
                refreshAll()
                onResult(true, null)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Authorization failed"
                _errorMessage.value = error
                onResult(false, error)
            }
        }
    }

    fun loginWithCredentials(
        usernameInput: String,
        passwordInput: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (usernameInput.isBlank() || passwordInput.isBlank()) {
            onResult(false, "Username and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = LastFmAuth.getMobileSession(appContext, usernameInput, passwordInput)
            _isLoading.value = false
            if (result.isSuccess) {
                val session = result.getOrNull()
                _isLoggedIn.value = true
                _username.value = session?.name ?: usernameInput.trim()
                refreshAll()
                onResult(true, null)
            } else {
                val error = result.exceptionOrNull()?.message ?: "Invalid username or password"
                _errorMessage.value = error
                onResult(false, error)
            }
        }
    }

    fun logout() {
        LastFmPrefs.clearSession(appContext)
        _isLoggedIn.value = false
        _username.value = ""
        _avatarUrl.value = null
        _scrobbleCount.value = 0L
        _recentTracks.value = emptyList()
        _topTracks.value = emptyList()
        _lovedTracks.value = emptyList()
    }

    fun refreshAll() {
        val user = _username.value
        if (user.isBlank()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshUserInfoInternal(user)
            refreshRecentTracksInternal(user)
            refreshTopTracksInternal(user, _topTracksPeriod.value)
            refreshLovedTracksInternal(user)
            _isRefreshing.value = false
        }
    }

    fun refreshUserInfo() {
        val user = _username.value
        if (user.isBlank()) return
        viewModelScope.launch {
            refreshUserInfoInternal(user)
        }
    }

    private suspend fun refreshUserInfoInternal(user: String) {
        val result = LastFmClient.getUserInfo(appContext, user)
        result.onSuccess { userInfo ->
            _avatarUrl.value = userInfo.getAvatarUrl()
            userInfo.playcount.toLongOrNull()?.let { count ->
                _scrobbleCount.value = count
            }
        }
    }

    fun refreshRecentTracks() {
        val user = _username.value
        if (user.isBlank()) return
        viewModelScope.launch {
            refreshRecentTracksInternal(user)
        }
    }

    private suspend fun refreshRecentTracksInternal(user: String) {
        val result = LastFmClient.getRecentTracks(appContext, user, limit = 20)
        result.onSuccess { tracks ->
            _recentTracks.value = tracks
            // Concurrently resolve missing covers via iTunes Search API with LRU cache
            val resolvedTracks = LastFmArtworkResolver.resolveRecentTracks(tracks)
            _recentTracks.value = resolvedTracks
        }
    }

    fun refreshTopTracks(period: String = _topTracksPeriod.value) {
        val user = _username.value
        if (user.isBlank()) return
        viewModelScope.launch {
            refreshTopTracksInternal(user, period)
        }
    }

    private suspend fun refreshTopTracksInternal(user: String, period: String) {
        val result = LastFmClient.getTopTracks(appContext, user, period = period, limit = 20)
        result.onSuccess { tracks ->
            _topTracks.value = tracks
            val resolved = LastFmArtworkResolver.resolveTopTracks(tracks)
            _topTracks.value = resolved
        }
    }

    fun refreshLovedTracks() {
        val user = _username.value
        if (user.isBlank()) return
        viewModelScope.launch {
            refreshLovedTracksInternal(user)
        }
    }

    private suspend fun refreshLovedTracksInternal(user: String) {
        val result = LastFmClient.getLovedTracks(appContext, user, limit = 20)
        result.onSuccess { tracks ->
            _lovedTracks.value = tracks
            val resolved = LastFmArtworkResolver.resolveLovedTracks(tracks)
            _lovedTracks.value = resolved
        }
    }

    fun toggleLoveTrack(artist: String, track: String, currentlyLoved: Boolean) {
        val newLoved = !currentlyLoved
        // Optimistic UI updates
        _recentTracks.update { list ->
            list.map {
                if (it.artist?.displayName.equals(artist, ignoreCase = true) && it.name.equals(track, ignoreCase = true)) {
                    it.copy(isLoved = newLoved)
                } else it
            }
        }
        _topTracks.update { list ->
            list.map {
                if (it.artist?.displayName.equals(artist, ignoreCase = true) && it.name.equals(track, ignoreCase = true)) {
                    it.copy(isLoved = newLoved)
                } else it
            }
        }

        viewModelScope.launch {
            if (newLoved) {
                LastFmClient.loveTrack(appContext, artist, track)
            } else {
                LastFmClient.unloveTrack(appContext, artist, track)
            }
            val user = _username.value
            if (user.isNotBlank()) {
                refreshLovedTracksInternal(user)
            }
        }
    }

    fun flushPendingScrobbles() {
        lastFmScrobbleManager.flushPendingScrobbles()
    }

    fun setScrobblingEnabled(enabled: Boolean) {
        LastFmPrefs.setScrobblingEnabled(appContext, enabled)
        _isScrobblingEnabled.value = enabled
    }

    fun setNowPlayingEnabled(enabled: Boolean) {
        LastFmPrefs.setNowPlayingEnabled(appContext, enabled)
        _isNowPlayingEnabled.value = enabled
    }

    fun setThresholdPercent(percent: Int) {
        val clamped = percent.coerceIn(50, 100)
        LastFmPrefs.setScrobbleThresholdPercent(appContext, clamped)
        _thresholdPercent.value = clamped
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        LastFmPrefs.setWifiOnly(appContext, wifiOnly)
        _isWifiOnly.value = wifiOnly
    }

    fun setSyncLovedTracks(syncLoved: Boolean) {
        LastFmPrefs.setSyncLovedTracks(appContext, syncLoved)
        _isSyncLovedTracks.value = syncLoved
    }

    fun saveCustomApiCredentials(apiKey: String, apiSecret: String) {
        LastFmPrefs.saveCustomApiKey(appContext, apiKey.trim())
        LastFmPrefs.saveCustomApiSecret(appContext, apiSecret.trim())
    }

    fun getCustomApiKey(): String = LastFmPrefs.getCustomApiKey(appContext)

    fun getCustomApiSecret(): String = LastFmPrefs.getCustomApiSecret(appContext)

    fun loginWithSessionKey(
        usernameInput: String,
        sessionKeyInput: String,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (usernameInput.isBlank() || sessionKeyInput.isBlank()) {
            onResult(false, "Username and session key cannot be empty")
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            LastFmAuth.saveManualSession(appContext, usernameInput.trim(), sessionKeyInput.trim())
            val userResult = LastFmClient.getUserInfo(appContext, usernameInput.trim())
            _isLoading.value = false
            if (userResult.isSuccess) {
                val user = userResult.getOrNull()
                _isLoggedIn.value = true
                _username.value = user?.name ?: usernameInput.trim()
                refreshAll()
                onResult(true, null)
            } else {
                _isLoggedIn.value = true
                _username.value = usernameInput.trim()
                refreshAll()
                onResult(true, null)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
