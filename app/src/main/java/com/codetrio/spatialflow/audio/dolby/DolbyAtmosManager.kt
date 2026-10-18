package com.codetrio.spatialflow.audio.dolby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central coordinator for Dolby Atmos capability detection, routing changes, and playback state.
 *
 * Operates as a lifecycle-aware coordinator:
 * - Listens for audio-device connections/disconnections.
 * - Listens for Android Spatializer state transitions.
 * - Re-evaluates capabilities on background dispatcher.
 * - Caches static codec support to avoid redundant framework scans.
 */
object DolbyAtmosManager {

    private const val TAG = "DolbyAtmosManager"
    const val PREFS_NAME = "AppSettings"
    const val KEY_DOLBY_ATMOS_ENABLED = "dolby_atmos_enabled"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var audioManager: AudioManager? = null

    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var spatializerListener: Any? = null
    private var noisyReceiver: BroadcastReceiver? = null

    private val _liveDiagnostics = MutableStateFlow(DolbyAtmosDiagnostics())
    val liveDiagnostics: StateFlow<DolbyAtmosDiagnostics> = _liveDiagnostics.asStateFlow()

    private val _isAtmosActive = MutableStateFlow(false)
    val isAtmosActive: StateFlow<Boolean> = _isAtmosActive.asStateFlow()

    private val _isAtmosEnabledByUser = MutableStateFlow(true)
    val isAtmosEnabledByUser: StateFlow<Boolean> = _isAtmosEnabledByUser.asStateFlow()

    private var currentSongHasAtmos: Boolean = false
    private var currentSongMimeType: String? = null
    private var currentSongChannelCount: Int = 2

    /**
     * Initializes the manager with the Application Context.
     */
    fun init(context: Context) {
        if (appContext != null) return

        val app = context.applicationContext
        appContext = app

        val sp = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val isEnabled = sp.getBoolean(KEY_DOLBY_ATMOS_ENABLED, true)
        _isAtmosEnabledByUser.value = isEnabled

        audioManager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        registerListeners(app)
        refreshCapabilities()
    }

    private fun registerListeners(context: Context) {
        val am = audioManager ?: return

        // 1. Audio device routing listener (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioDeviceCallback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d(TAG, "Audio device added, re-evaluating Atmos route...")
                    refreshCapabilities()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    Log.d(TAG, "Audio device removed, re-evaluating Atmos route...")
                    refreshCapabilities()
                }
            }
            am.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        }

        // 2. Spatializer state listener (API 32+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            try {
                val spatializer = am.spatializer
                val listener = object : Spatializer.OnSpatializerStateChangedListener {
                    override fun onSpatializerAvailableChanged(spatializer: Spatializer, available: Boolean) {
                        Log.d(TAG, "Spatializer availability changed: $available")
                        refreshCapabilities()
                    }

                    override fun onSpatializerEnabledChanged(spatializer: Spatializer, enabled: Boolean) {
                        Log.d(TAG, "Spatializer enabled changed: $enabled")
                        refreshCapabilities()
                    }
                }
                spatializer.addOnSpatializerStateChangedListener(
                    { command -> mainHandler.post(command) },
                    listener
                )
                spatializerListener = listener
            } catch (e: Exception) {
                Log.w(TAG, "Failed registering Spatializer listener", e)
            }
        }

        // 3. Audio becoming noisy receiver (fallback for wired/BT disconnects)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (AudioManager.ACTION_AUDIO_BECOMING_NOISY == intent?.action) {
                    refreshCapabilities()
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(receiver, filter)
        noisyReceiver = receiver
    }

    /**
     * Re-evaluates capabilities asynchronously on Dispatchers.Default.
     */
    fun refreshCapabilities() {
        val context = appContext ?: return
        val isPreferred = _isAtmosEnabledByUser.value

        scope.launch {
            val diag = DolbyAtmosDetector.evaluate(context, isPreferred)
            val updatedDiag = diag.copy(
                activeMimeType = currentSongMimeType,
                channelCount = currentSongChannelCount
            )
            _liveDiagnostics.value = updatedDiag

            val canSpatialize = updatedDiag.capability == DolbyAtmosCapability.FULLY_SUPPORTED
            val active = isPreferred
            _isAtmosActive.value = active
        }
    }

    /**
     * Toggles Dolby Atmos preference.
     */
    fun setAtmosEnabled(enabled: Boolean) {
        _isAtmosEnabledByUser.value = enabled
        prefs?.edit()?.putBoolean(KEY_DOLBY_ATMOS_ENABLED, enabled)?.apply()
        refreshCapabilities()
    }

    /**
     * Informs manager about current track Atmos metadata.
     */
    fun updateCurrentTrackAtmosStatus(hasAtmosStream: Boolean, mimeType: String?, channelCount: Int = 2) {
        currentSongHasAtmos = hasAtmosStream
        currentSongMimeType = mimeType
        currentSongChannelCount = channelCount

        val currentDiag = _liveDiagnostics.value
        val isPreferred = _isAtmosEnabledByUser.value
        val active = isPreferred

        _isAtmosActive.value = active
        _liveDiagnostics.value = currentDiag.copy(
            activeMimeType = mimeType,
            channelCount = channelCount,
            currentMode = when {
                !isPreferred -> AtmosPlaybackMode.DISABLED
                hasAtmosStream -> AtmosPlaybackMode.ACTIVE_ATMOS
                active -> AtmosPlaybackMode.ACTIVE_ATMOS
                else -> AtmosPlaybackMode.NOT_ATMOS
            }
        )
    }

    /**
     * Decides whether the player should request the genuine Atmos stream for a given song.
     */
    fun shouldPlayAtmos(songItem: SongItem?): Boolean {
        if (songItem == null) return false
        if (!songItem.hasDolbyAtmos && songItem.atmosStreamUri.isNullOrBlank()) return false
        if (!_isAtmosEnabledByUser.value) return false

        val diag = _liveDiagnostics.value
        return diag.capability == DolbyAtmosCapability.FULLY_SUPPORTED
    }

    /**
     * Releases listeners during application shutdown.
     */
    fun release() {
        val context = appContext ?: return
        val am = audioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null && am != null) {
            am.unregisterAudioDeviceCallback(audioDeviceCallback)
            audioDeviceCallback = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 && spatializerListener != null && am != null) {
            try {
                (spatializerListener as? Spatializer.OnSpatializerStateChangedListener)?.let {
                    am.spatializer.removeOnSpatializerStateChangedListener(it)
                }
            } catch (_: Exception) {}
            spatializerListener = null
        }

        noisyReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            noisyReceiver = null
        }

        appContext = null
    }
}
