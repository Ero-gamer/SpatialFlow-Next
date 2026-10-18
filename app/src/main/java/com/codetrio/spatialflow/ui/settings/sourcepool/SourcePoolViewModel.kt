package com.codetrio.spatialflow.ui.settings.sourcepool

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codetrio.spatialflow.data.sourcepool.DiscoveryResponse
import com.codetrio.spatialflow.data.sourcepool.SourceCategoryStatus
import com.codetrio.spatialflow.data.sourcepool.SourcePoolClient
import com.codetrio.spatialflow.data.sourcepool.SourcePoolSecurity
import com.codetrio.spatialflow.data.sourcepool.SourcePoolSummary
import com.codetrio.spatialflow.data.sourcepool.SourcesFeedResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SourcePoolUiState {
    object Loading : SourcePoolUiState
    data class Success(
        val summary: SourcePoolSummary,
        val categories: List<SourceCategoryStatus>,
        val sourcesFeed: SourcesFeedResponse?,
        val tidalDiscovery: DiscoveryResponse?,
        val qobuzDiscovery: DiscoveryResponse?,
        val isKeyConfigured: Boolean,
        val isRefreshing: Boolean = false,
        val lastFetchTimeMs: Long = System.currentTimeMillis()
    ) : SourcePoolUiState
    data class Error(val message: String) : SourcePoolUiState
}

class SourcePoolViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<SourcePoolUiState>(SourcePoolUiState.Loading)
    val uiState: StateFlow<SourcePoolUiState> = _uiState.asStateFlow()

    init {
        loadData(isInitial = true)
    }

    fun refresh() {
        val current = _uiState.value
        if (current is SourcePoolUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        }
        loadData(isInitial = false)
    }

    fun disconnectKey() {
        SourcePoolSecurity.clearApiKey(getApplication())
        refresh()
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        SourcePoolSecurity.setApiKey(getApplication(), if (trimmed.isEmpty()) null else trimmed)
        refresh()
    }

    private fun loadData(isInitial: Boolean) {
        viewModelScope.launch {
            if (isInitial && _uiState.value !is SourcePoolUiState.Success) {
                _uiState.value = SourcePoolUiState.Loading
            }

            val context = getApplication<Application>()
            val apiKey = SourcePoolSecurity.getApiKey(context)
            val isKeySet = !apiKey.isNullOrBlank()

            val statusDeferred = async { SourcePoolClient.fetchStatus(context) }
            val feedDeferred = async { SourcePoolClient.fetchSourcesFeed(context, apiKey) }
            val tidalDiscDeferred = async { SourcePoolClient.fetchDiscovery(context, "tidal") }
            val qobuzDiscDeferred = async { SourcePoolClient.fetchDiscovery(context, "qobuz") }

            val statusResult = statusDeferred.await()
            val feedResult = feedDeferred.await()
            val tidalDisc = tidalDiscDeferred.await().getOrNull()
            val qobuzDisc = qobuzDiscDeferred.await().getOrNull()

            statusResult.onSuccess { statusResp ->
                val summary = SourcePoolClient.computeSummary(statusResp)
                val feed = feedResult.getOrNull()
                _uiState.value = SourcePoolUiState.Success(
                    summary = summary,
                    categories = statusResp.categories,
                    sourcesFeed = feed,
                    tidalDiscovery = tidalDisc,
                    qobuzDiscovery = qobuzDisc,
                    isKeyConfigured = isKeySet,
                    isRefreshing = false,
                    lastFetchTimeMs = System.currentTimeMillis()
                )
            }.onFailure { error ->
                if (_uiState.value !is SourcePoolUiState.Success) {
                    _uiState.value = SourcePoolUiState.Error(
                        error.message ?: "Failed to connect to Source Pool"
                    )
                } else {
                    val current = _uiState.value as SourcePoolUiState.Success
                    _uiState.value = current.copy(isRefreshing = false)
                }
            }
        }
    }
}
