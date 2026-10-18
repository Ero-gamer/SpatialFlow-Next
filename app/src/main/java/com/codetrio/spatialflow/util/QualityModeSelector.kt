package com.codetrio.spatialflow.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QualityMode {
    LOW,
    MEDIUM,
    HIGH
}

enum class PlaybackMode {
    DSP,
    BIT_PERFECT
}

/**
 * Quality Mode Selector Manager for Audio & Data Saving.
 * Dynamically resolves target bitrate preferences, queue preload depth, artwork dimensions,
 * ExoPlayer buffer settings, and video canvas background toggles based on Network State and Preferences.
 */
object QualityModeSelector {

    private const val PREFS_NAME = "AppSettings"
    private const val KEY_AUDIO_QUALITY = "audio_quality"
    private const val KEY_DATA_SAVER = "data_saver"
    const val KEY_LOSSLESS_SOURCE_POOL = "source_pool_lossless_streaming"
    const val KEY_LOSSLESS_PROVIDER = "lossless_provider"
    const val KEY_LOSSLESS_PRIORITY_ORDER = "lossless_priority_order"
    const val KEY_LOSSLESS_DISABLED_PROVIDERS = "lossless_disabled_providers"
    const val KEY_DAC_DIRECT_MODE = "dac_direct_mode"
    const val KEY_DAC_BIT_PERFECT = "dac_bit_perfect"
    const val KEY_NATIVE_SAMPLE_RATE = "native_sample_rate"
    const val KEY_DAC_FIXED_VOLUME = "dac_fixed_volume"
    const val KEY_PRE_LINE_OUT_VOLUME = "pre_line_out_volume"
    const val KEY_USB_EXCLUSIVE_MODE = "usb_exclusive_mode"
    const val KEY_NATIVE_DSP_MODE = "native_dsp_mode"
    const val KEY_PLAYBACK_MODE = "playback_mode"

    fun isLosslessStreamingEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDataSaver = prefs.getBoolean(KEY_DATA_SAVER, false)
        if (isDataSaver) return false
        return prefs.getBoolean(KEY_LOSSLESS_SOURCE_POOL, true)
    }

    fun getLosslessPriorityOrder(context: Context): List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_LOSSLESS_PRIORITY_ORDER, null)
        if (raw.isNullOrBlank()) {
            return com.codetrio.spatialflow.data.sourcepool.LosslessProvider.DEFAULT_PRIORITY_ORDER
        }
        val ids = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val parsed = ids.map { com.codetrio.spatialflow.data.sourcepool.LosslessProvider.fromId(it) }.distinct()
        val missing = com.codetrio.spatialflow.data.sourcepool.LosslessProvider.DEFAULT_PRIORITY_ORDER.filter { !parsed.contains(it) }
        return parsed + missing
    }

    fun setLosslessPriorityOrder(context: Context, order: List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = order.joinToString(",") { it.id }
        prefs.edit().putString(KEY_LOSSLESS_PRIORITY_ORDER, serialized).apply()
    }

    fun getDisabledLosslessProviders(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_LOSSLESS_DISABLED_PROVIDERS, emptySet()) ?: emptySet()
    }

    fun setDisabledLosslessProviders(context: Context, disabled: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_LOSSLESS_DISABLED_PROVIDERS, disabled).apply()
    }

    fun getActiveLosslessPriorityPipeline(context: Context): List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider> {
        val order = getLosslessPriorityOrder(context)
        val disabled = getDisabledLosslessProviders(context)
        val active = order.filter { !disabled.contains(it.id) }
        return if (active.isNotEmpty()) active else listOf(com.codetrio.spatialflow.data.sourcepool.LosslessProvider.KOUZU)
    }

    fun getLosslessProvider(context: Context): com.codetrio.spatialflow.data.sourcepool.LosslessProvider {
        return getActiveLosslessPriorityPipeline(context).firstOrNull() ?: com.codetrio.spatialflow.data.sourcepool.LosslessProvider.KOUZU
    }

    fun setLosslessProvider(context: Context, provider: com.codetrio.spatialflow.data.sourcepool.LosslessProvider) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LOSSLESS_PROVIDER, provider.id).apply()
    }

    fun isDacDirectModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DAC_DIRECT_MODE, true)
    }

    fun setDacDirectMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DAC_DIRECT_MODE, enabled).apply()
        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.checkAndApplyDacRouting()
            com.codetrio.spatialflow.service.dac.UsbDacManager.inspectConnectedDevices()
        } catch (_: Throwable) {}
    }

    fun isDacBitPerfectEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DAC_BIT_PERFECT, false)
    }

    fun setDacBitPerfect(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = if (enabled) PlaybackMode.BIT_PERFECT else PlaybackMode.DSP
        prefs.edit()
            .putBoolean(KEY_DAC_BIT_PERFECT, enabled)
            .putString(KEY_PLAYBACK_MODE, mode.name)
            .putBoolean(KEY_NATIVE_DSP_MODE, !enabled)
            .apply()

        _playbackModeFlow.value = mode
        _isStudioSoundEnabledFlow.value = !enabled

        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.checkAndApplyDacRouting()
            com.codetrio.spatialflow.service.dac.UsbDacManager.reconfigureForCurrentTrack()
        } catch (_: Throwable) {}
    }

    fun isNativeSampleRateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NATIVE_SAMPLE_RATE, true)
    }

    fun setNativeSampleRate(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NATIVE_SAMPLE_RATE, enabled).apply()
        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.checkAndApplyDacRouting()
            com.codetrio.spatialflow.service.dac.UsbDacManager.reconfigureForCurrentTrack()
        } catch (_: Throwable) {}
    }

    fun isDacFixedVolumeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DAC_FIXED_VOLUME, false)
    }

    fun setDacFixedVolume(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_DAC_FIXED_VOLUME, enabled) }
        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.applyDacVolumeSettings()
            com.codetrio.spatialflow.service.dac.UsbDacManager.reconfigureForCurrentTrack()
        } catch (_: Throwable) {}
    }

    fun isUsbExclusiveModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USB_EXCLUSIVE_MODE, true)
    }

    fun setUsbExclusiveMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { 
            putBoolean(KEY_USB_EXCLUSIVE_MODE, enabled)
            putBoolean(KEY_DAC_DIRECT_MODE, enabled)
        }
        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.checkAndApplyDacRouting()
            com.codetrio.spatialflow.service.dac.UsbDacManager.reconfigureForCurrentTrack()
        } catch (_: Throwable) {}
    }

    private val _isStudioSoundEnabledFlow = MutableStateFlow(true)
    val isStudioSoundEnabledFlow: StateFlow<Boolean> = _isStudioSoundEnabledFlow.asStateFlow()
    val isNativeDspEnabledFlow: StateFlow<Boolean> get() = isStudioSoundEnabledFlow

    private val _playbackModeFlow = MutableStateFlow(PlaybackMode.DSP)
    val playbackModeFlow: StateFlow<PlaybackMode> = _playbackModeFlow.asStateFlow()

    fun getPlaybackMode(context: Context): PlaybackMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isBitPerfect = prefs.getBoolean(KEY_DAC_BIT_PERFECT, false)
        val mode = if (isBitPerfect) PlaybackMode.BIT_PERFECT else PlaybackMode.DSP
        if (_playbackModeFlow.value != mode) {
            _playbackModeFlow.value = mode
        }
        return mode
    }

    /**
     * Bit-Perfect hardware mode is strictly restricted to USB DAC / USB Audio devices.
     * For wireless (Bluetooth) devices and internal speakers, Bit-Perfect is always false.
     */
    fun isBitPerfectActive(context: Context): Boolean {
        val isUsbConnected = com.codetrio.spatialflow.service.dac.UsbDacManager.isDacConnected.value
        val isBitPerfect = isDacBitPerfectEnabled(context) || getPlaybackMode(context) == PlaybackMode.BIT_PERFECT
        return isUsbConnected && isBitPerfect
    }

    fun setPlaybackMode(context: Context, mode: PlaybackMode) {
        val isDsp = mode == PlaybackMode.DSP
        setStudioSoundMode(context, isDsp)
    }

    fun isStudioSoundModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isBitPerfect = prefs.getBoolean(KEY_DAC_BIT_PERFECT, false)
        val value = if (isBitPerfect) false else prefs.getBoolean(KEY_NATIVE_DSP_MODE, true)
        if (_isStudioSoundEnabledFlow.value != value) {
            _isStudioSoundEnabledFlow.value = value
        }
        return value
    }

    fun isNativeDspModeEnabled(context: Context): Boolean = isStudioSoundModeEnabled(context)

    fun setStudioSoundMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val mode = if (enabled) PlaybackMode.DSP else PlaybackMode.BIT_PERFECT
        prefs.edit()
            .putBoolean(KEY_NATIVE_DSP_MODE, enabled)
            .putBoolean(KEY_DAC_BIT_PERFECT, !enabled)
            .putString(KEY_PLAYBACK_MODE, mode.name)
            .apply()
        _isStudioSoundEnabledFlow.value = enabled
        _playbackModeFlow.value = mode
        try {
            com.codetrio.spatialflow.service.AudioPlaybackService.currentInstance?.checkAndApplyDacRouting()
            com.codetrio.spatialflow.service.dac.UsbDacManager.reconfigureForCurrentTrack()
        } catch (_: Throwable) {}
    }

    fun setNativeDspMode(context: Context, enabled: Boolean) = setStudioSoundMode(context, enabled)

    fun isWifiOrUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun getActiveQualityMode(context: Context): QualityMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDataSaverEnabled = prefs.getBoolean(KEY_DATA_SAVER, false)
        if (isDataSaverEnabled) return QualityMode.LOW

        val prefQuality = prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High"

        return when (prefQuality.lowercase()) {
            "low", "data saver" -> QualityMode.LOW
            "medium", "normal" -> QualityMode.MEDIUM
            "high", "highest" -> QualityMode.HIGH
            "auto" -> {
                if (isWifiOrUnmetered(context)) QualityMode.HIGH else QualityMode.MEDIUM
            }
            else -> QualityMode.HIGH
        }
    }

    fun getStreamQualityPreference(mode: QualityMode): String = when (mode) {
        QualityMode.LOW -> "Data Saver"
        QualityMode.MEDIUM -> "Normal"
        QualityMode.HIGH -> "High"
    }

    fun getPreloadDepth(mode: QualityMode): Int = when (mode) {
        QualityMode.LOW -> 1 // 1 track pre-resolved for instant 0ms skip without heavy byte pre-caching
        QualityMode.MEDIUM -> 2 // 2 tracks preloaded
        QualityMode.HIGH -> 3 // 3 tracks preloaded
    }

    fun getArtworkTargetSize(mode: QualityMode): Int = when (mode) {
        QualityMode.LOW -> 200 // 200px Micro-Thumb
        QualityMode.MEDIUM -> 512 // 512px WebP
        QualityMode.HIGH -> 1024 // 1024px Full HD
    }

    fun getBufferForPlaybackMs(mode: QualityMode): Int = 250 // 250ms instant playback start across all modes

    fun isCanvasVideoAllowed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDataSaverEnabled = prefs.getBoolean(KEY_DATA_SAVER, false)
        return !isDataSaverEnabled
    }

    // Deezer Account Settings
    const val KEY_DEEZER_ARL = "deezer_arl"
    const val KEY_DEEZER_ACCOUNT_NAME = "deezer_account_name"
    const val KEY_DEEZER_PREMIUM = "deezer_premium"

    fun getDeezerArl(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEEZER_ARL, null)?.takeIf { it.isNotBlank() }
    }

    fun setDeezerAccount(context: Context, arl: String?, accountName: String?, premium: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (arl.isNullOrBlank()) {
                remove(KEY_DEEZER_ARL)
                remove(KEY_DEEZER_ACCOUNT_NAME)
                remove(KEY_DEEZER_PREMIUM)
            } else {
                putString(KEY_DEEZER_ARL, arl.trim())
                putString(KEY_DEEZER_ACCOUNT_NAME, accountName)
                putBoolean(KEY_DEEZER_PREMIUM, premium)
            }
        }.apply()
        com.codetrio.spatialflow.data.deezer.DeezerAudioProvider.setManualArl(arl, premium)
    }

    fun getDeezerAccountName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEEZER_ACCOUNT_NAME, null)
    }

    fun isDeezerPremium(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEEZER_PREMIUM, false)
    }

    // Tidal Account Settings
    const val KEY_TIDAL_ACCESS_TOKEN = "tidal_access_token"
    const val KEY_TIDAL_REFRESH_TOKEN = "tidal_refresh_token"
    const val KEY_TIDAL_ACCOUNT_NAME = "tidal_account_name"
    const val KEY_TIDAL_SUBSCRIPTION = "tidal_subscription"
    const val KEY_TIDAL_USER_ID = "tidal_user_id"
    const val KEY_TIDAL_COUNTRY_CODE = "tidal_country_code"

    fun getTidalAccessToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TIDAL_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun setTidalAccount(
        context: Context,
        accessToken: String?,
        refreshToken: String?,
        accountName: String?,
        subscription: String?,
        userId: Long? = null,
        countryCode: String? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (accessToken.isNullOrBlank()) {
                remove(KEY_TIDAL_ACCESS_TOKEN)
                remove(KEY_TIDAL_REFRESH_TOKEN)
                remove(KEY_TIDAL_ACCOUNT_NAME)
                remove(KEY_TIDAL_SUBSCRIPTION)
                remove(KEY_TIDAL_USER_ID)
                remove(KEY_TIDAL_COUNTRY_CODE)
            } else {
                putString(KEY_TIDAL_ACCESS_TOKEN, accessToken)
                if (refreshToken != null) putString(KEY_TIDAL_REFRESH_TOKEN, refreshToken)
                putString(KEY_TIDAL_ACCOUNT_NAME, accountName)
                putString(KEY_TIDAL_SUBSCRIPTION, subscription)
                if (userId != null) putLong(KEY_TIDAL_USER_ID, userId)
                if (countryCode != null) putString(KEY_TIDAL_COUNTRY_CODE, countryCode)
            }
        }.apply()
    }

    fun isDeezerLoggedIn(context: Context): Boolean = !getDeezerArl(context).isNullOrBlank()

    fun clearDeezerAccount(context: Context) {
        setDeezerAccount(context, null, null, false)
    }

    fun isTidalLoggedIn(context: Context): Boolean = !getTidalAccessToken(context).isNullOrBlank()

    fun clearTidalAccount(context: Context) {
        setTidalAccount(context, null, null, null, null, null, null)
    }

    fun getTidalAccountName(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TIDAL_ACCOUNT_NAME, null)
    }

    fun getTidalSubscription(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TIDAL_SUBSCRIPTION, null)
    }

    fun getTidalUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getLong(KEY_TIDAL_USER_ID, 0L)
        return if (uid > 0) uid.toString() else ""
    }

    fun isTidalPremium(context: Context): Boolean {
        val sub = getTidalSubscription(context)?.uppercase() ?: return true
        return sub.contains("PREMIUM") || sub.contains("HIFI") || sub.contains("PLUS") || sub.contains("LOSSLESS") || sub == "PREMIUM"
    }
}
