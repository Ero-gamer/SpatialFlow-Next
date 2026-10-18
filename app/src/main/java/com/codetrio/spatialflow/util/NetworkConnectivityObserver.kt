package com.codetrio.spatialflow.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionStatus {
    data object Hidden : ConnectionStatus
    data object Offline : ConnectionStatus
    data object BackOnline : ConnectionStatus
}

class NetworkConnectivityObserver(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _status = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Hidden)
    val status: StateFlow<ConnectionStatus> = _status.asStateFlow()

    private var autoDismissJob: Job? = null
    private var hasBeenOffline = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        val isInitiallyConnected = checkCurrentConnectivity()
        if (!isInitiallyConnected) {
            hasBeenOffline = true
            _status.value = ConnectionStatus.Offline
        } else {
            // Cold start with active network: suppress popup
            _status.value = ConnectionStatus.Hidden
        }
        startObserving()
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun startObserving() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                checkAndUpdateState()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                checkAndUpdateState()
            }

            override fun onLost(network: Network) {
                checkAndUpdateState()
            }

            override fun onUnavailable() {
                checkAndUpdateState()
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndUpdateState() {
        val isConnected = checkCurrentConnectivity()
        scope.launch {
            if (isConnected) {
                if (hasBeenOffline) {
                    hasBeenOffline = false
                    autoDismissJob?.cancel()
                    _status.value = ConnectionStatus.BackOnline
                    autoDismissJob = launch {
                        delay(3000L)
                        _status.value = ConnectionStatus.Hidden
                    }
                }
            } else {
                autoDismissJob?.cancel()
                hasBeenOffline = true
                _status.value = ConnectionStatus.Offline
            }
        }
    }

    fun stopObserving() {
        autoDismissJob?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
        networkCallback = null
    }
}

@Composable
fun rememberConnectionStatus(): State<ConnectionStatus> {
    val context = LocalContext.current
    val observer = remember { NetworkConnectivityObserver(context) }
    DisposableEffect(observer) {
        onDispose {
            observer.stopObserving()
        }
    }
    return observer.status.collectAsStateWithLifecycle()
}
