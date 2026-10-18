package com.codetrio.spatialflow.service.live

import android.content.Context
import android.util.Log
import com.codetrio.spatialflow.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class LivePresenceStatus {
    DISABLED,
    ACTIVE,
    IDLE
}

data class ActiveListenersData(
    val totalListeners: Int,
    val updatedAt: String,
    val locations: List<LiveLocation>
)

data class LiveLocation(
    val city: String?,
    val region: String?,
    val country: String?,
    val countryCode: String?,
    val count: Int
)

/**
 * Lightweight, battery-efficient manager for SpatialFlow Live listener-presence tracking.
 * Connects to the Cloudflare Worker live presence API over hardened TLS 1.2/1.3.
 */
class SpatialFlowLiveManager(private val context: Context) {

    companion object {
        private const val TAG = "SpatialFlowLive"
        private const val PREFS_NAME = "spatialflow_live"
        private const val KEY_ENABLED = "spatialflow_live_enabled"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val BASE_URL = "https://spatialflow-live-api.shubhamjkarande.workers.dev"
        private const val HEARTBEAT_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
        private const val STOP_DEBOUNCE_MS = 1000L // 1 second smoothing for track changes/scrubbing
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val USER_AGENT = "SpatialFlow/${BuildConfig.VERSION_NAME} (Android; LivePresence)"
        private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

        /**
         * Hardened OkHttpClient enforcing TLS 1.2/1.3, safe timeouts, and redirect restrictions.
         */
        val secureHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
        }

        private val _liveStatus = MutableStateFlow(LivePresenceStatus.IDLE)
        val liveStatus: StateFlow<LivePresenceStatus> = _liveStatus.asStateFlow()

        @Volatile
        var activeInstance: SpatialFlowLiveManager? = null
            private set

        fun isValidUUID(uuid: String?): Boolean {
            return !uuid.isNullOrBlank() && UUID_REGEX.matches(uuid)
        }

        fun buildSecureRequest(url: String): Request.Builder {
            return Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
        }

        fun isPresenceEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_ENABLED, true)
        }

        fun setPresenceEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
            activeInstance?.onPresenceSettingChanged(enabled) ?: run {
                _liveStatus.value = if (enabled) LivePresenceStatus.IDLE else LivePresenceStatus.DISABLED
            }
        }

        /**
         * Pings Cloudflare Worker GET /health to verify API connectivity.
         */
        suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
            try {
                val request = buildSecureRequest("$BASE_URL/health")
                    .get()
                    .build()

                secureHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body.string()
                    org.json.JSONObject(body).optBoolean("ok", false)
                }
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Fetches live listener counts and active regions from Cloudflare Worker GET /v1/listeners/active.
         */
        suspend fun fetchActiveListeners(): Result<ActiveListenersData> = withContext(Dispatchers.IO) {
            try {
                val request = buildSecureRequest("$BASE_URL/v1/listeners/active")
                    .get()
                    .build()

                secureHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }

                    val bodyString = response.body.string()
                    val json = org.json.JSONObject(bodyString)
                    val count = json.optInt("activeListeners", 0)
                    val updatedAt = json.optString("updatedAt", "")
                    val locationsJson = json.optJSONArray("locations")
                    val list = mutableListOf<LiveLocation>()

                    if (locationsJson != null) {
                        for (i in 0 until locationsJson.length()) {
                            val item = locationsJson.optJSONObject(i) ?: continue
                            list.add(
                                LiveLocation(
                                    city = item.optString("city").takeIf { it.isNotBlank() && it != "null" },
                                    region = item.optString("region").takeIf { it.isNotBlank() && it != "null" },
                                    country = item.optString("country").takeIf { it.isNotBlank() && it != "null" },
                                    countryCode = item.optString("countryCode").takeIf { it.isNotBlank() && it != "null" },
                                    count = item.optInt("count", 0)
                                )
                            )
                        }
                    }

                    Result.success(ActiveListenersData(totalListeners = count, updatedAt = updatedAt, locations = list))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installationId: String by lazy { getOrCreateInstallationId() }
    @Volatile private var currentSessionId: String? = null
    @Volatile private var isAudioPlaying = false
    private var heartbeatJob: Job? = null
    private var stopJob: Job? = null

    init {
        activeInstance = this
        val enabled = isPresenceEnabled(context)
        _liveStatus.value = if (enabled) LivePresenceStatus.IDLE else LivePresenceStatus.DISABLED
    }

    /**
     * Called when audio playback starts or resumes.
     */
    fun onPlaybackStarted() {
        isAudioPlaying = true

        if (!isPresenceEnabled(context)) {
            _liveStatus.value = LivePresenceStatus.DISABLED
            return
        }

        stopJob?.cancel()
        stopJob = null

        _liveStatus.value = LivePresenceStatus.ACTIVE

        if (currentSessionId != null && heartbeatJob?.isActive == true) {
            return
        }

        val sessionId = currentSessionId ?: UUID.randomUUID().toString().also { currentSessionId = it }
        startHeartbeatLoop(sessionId)
    }

    /**
     * Called when audio playback pauses, stops, or completes.
     */
    fun onPlaybackStopped() {
        isAudioPlaying = false

        if (!isPresenceEnabled(context)) {
            _liveStatus.value = LivePresenceStatus.DISABLED
            return
        }

        _liveStatus.value = LivePresenceStatus.IDLE

        heartbeatJob?.cancel()
        heartbeatJob = null

        val sessionId = currentSessionId ?: return

        stopJob?.cancel()
        stopJob = scope.launch {
            delay(STOP_DEBOUNCE_MS)
            sendStop(sessionId, installationId)
            if (currentSessionId == sessionId) {
                currentSessionId = null
            }
        }
    }

    /**
     * Responds to user enabling/disabling presence in Settings.
     */
    fun onPresenceSettingChanged(enabled: Boolean) {
        if (!enabled) {
            _liveStatus.value = LivePresenceStatus.DISABLED
            val sessionId = currentSessionId
            heartbeatJob?.cancel()
            heartbeatJob = null
            stopJob?.cancel()
            stopJob = null
            currentSessionId = null
            if (sessionId != null) {
                scope.launch {
                    sendStop(sessionId, installationId)
                }
            }
        } else {
            _liveStatus.value = if (isAudioPlaying) LivePresenceStatus.ACTIVE else LivePresenceStatus.IDLE
            if (isAudioPlaying) {
                onPlaybackStarted()
            }
        }
    }

    /**
     * Called when the service is destroyed. Ensures any active session is immediately ended.
     */
    fun release() {
        if (activeInstance === this) {
            activeInstance = null
        }

        _liveStatus.value = if (isPresenceEnabled(context)) LivePresenceStatus.IDLE else LivePresenceStatus.DISABLED

        heartbeatJob?.cancel()
        heartbeatJob = null
        stopJob?.cancel()
        stopJob = null

        val sessionId = currentSessionId
        currentSessionId = null

        if (sessionId != null) {
            scope.launch {
                sendStop(sessionId, installationId)
            }
        }
    }

    private fun startHeartbeatLoop(sessionId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            sendHeartbeat(sessionId, installationId)
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!isActive || currentSessionId != sessionId) break
                sendHeartbeat(sessionId, installationId)
            }
        }
    }

    private fun sendHeartbeat(sessionId: String, installId: String) {
        if (!isValidUUID(sessionId) || !isValidUUID(installId)) {
            Log.w(TAG, "Heartbeat aborted: invalid UUID format")
            return
        }

        try {
            val json = "{\"sessionId\":\"$sessionId\",\"installationId\":\"$installId\"}"
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = buildSecureRequest("$BASE_URL/v1/presence/heartbeat")
                .post(body)
                .build()

            secureHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "Heartbeat response code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Heartbeat network call ignored: ${e.message}")
        }
    }

    private fun sendStop(sessionId: String, installId: String) {
        if (!isValidUUID(sessionId) || !isValidUUID(installId)) {
            Log.w(TAG, "Stop aborted: invalid UUID format")
            return
        }

        try {
            val json = "{\"sessionId\":\"$sessionId\",\"installationId\":\"$installId\"}"
            val body = json.toRequestBody(JSON_MEDIA_TYPE)
            val request = buildSecureRequest("$BASE_URL/v1/presence/stop")
                .post(body)
                .build()

            secureHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(TAG, "Stop response code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Stop network call ignored: ${e.message}")
        }
    }

    private fun getOrCreateInstallationId(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_INSTALLATION_ID, null)
        if (id == null || !isValidUUID(id)) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_INSTALLATION_ID, id).apply()
        }
        return id
    }
}
