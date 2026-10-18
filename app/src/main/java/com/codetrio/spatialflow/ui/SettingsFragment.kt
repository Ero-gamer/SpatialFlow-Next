@file:Suppress("DEPRECATION")
@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class
)

package com.codetrio.spatialflow.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.codetrio.spatialflow.ui.theme.concentricInner
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SignalWifiOff
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraphBuilder
import com.codetrio.spatialflow.BuildConfig
import com.codetrio.spatialflow.MainActivity
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.SpatialFlowApplication
import com.codetrio.spatialflow.composableWithBlur
import com.codetrio.spatialflow.domain.model.CacheBreakdown
import com.codetrio.spatialflow.domain.model.CacheCategory
import com.codetrio.spatialflow.domain.repository.LocalCacheDataSource
import com.codetrio.spatialflow.ui.SettingsViewModel.Companion.KEY_AWAITING_SUPPORTER_REWARD
import com.codetrio.spatialflow.ui.SettingsViewModel.Companion.KEY_IS_SUPPORTER
import com.codetrio.spatialflow.ui.SettingsViewModel.Companion.KEY_SUPPORTER_REWARD_LAUNCH_TIME
import com.codetrio.spatialflow.ui.SettingsViewModel.Companion.PREFS_NAME
import com.codetrio.spatialflow.ui.animation.aospSharedAxisEnter
import com.codetrio.spatialflow.ui.animation.aospSharedAxisExit
import com.codetrio.spatialflow.ui.animation.aospSharedAxisPopEnter
import com.codetrio.spatialflow.ui.animation.aospSharedAxisPopExit
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.settings.AboutScreen
import com.codetrio.spatialflow.ui.settings.AccountScreen
import com.codetrio.spatialflow.ui.settings.AppearanceScreen
import com.codetrio.spatialflow.ui.settings.BackupRestoreScreen
import com.codetrio.spatialflow.ui.settings.HapticsScreen
import com.codetrio.spatialflow.ui.settings.MusicManagementScreen
import com.codetrio.spatialflow.ui.settings.PlaybackScreen
import com.codetrio.spatialflow.ui.settings.SettingsGroupCard
import com.codetrio.spatialflow.ui.settings.SettingsHeader
import com.codetrio.spatialflow.ui.settings.WhatsNewScreen
import com.codetrio.spatialflow.ui.theme.SpatialShapes
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

private val SmoothSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS VIEW MODEL
// ═══════════════════════════════════════════════════════════════════════════════

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    @SuppressLint("StaticFieldLeak")
    private val context: Context = application.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cacheDataSource: LocalCacheDataSource by lazy {
        (application as SpatialFlowApplication).localCacheDataSource
    }

    // ── Dark Mode ────────────────────────────────────────────────────────────
    private val _darkMode = MutableStateFlow(
        prefs.getString("theme_mode", "system").let {
            if (it == "system") prefs.getBoolean(KEY_DARK_MODE, true) else it == "dark"
        }
    )
    val darkMode: StateFlow<Boolean> = _darkMode.asStateFlow()

    // ── Supporter State ───────────────────────────────────────────────────────
    private val _isSupporter = MutableStateFlow(prefs.getBoolean(KEY_IS_SUPPORTER, false))
    val isSupporter: StateFlow<Boolean> = _isSupporter.asStateFlow()

    fun markSupporterUnlocked() {
        _isSupporter.value = true
        prefs.edit { putBoolean(KEY_IS_SUPPORTER, true) }
    }

    fun setDarkMode(enabled: Boolean) {
        _darkMode.value = enabled
        prefs.edit {putBoolean(KEY_DARK_MODE, enabled)}
        prefs.edit {putString("theme_mode", if (enabled) "dark" else "light")}
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            if (enabled) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    // ── YouTube Music Cookies ──────────────────────────────────────────────────
    private val _ytCookies = MutableStateFlow(prefs.getString("yt_cookies", null))
    val ytCookies: StateFlow<String?> = _ytCookies.asStateFlow()

    fun setYtCookies(cookies: String?) {
        _ytCookies.value = cookies
        if (cookies != null) {
            prefs.edit {putString("yt_cookies", cookies)}
            com.codetrio.spatialflow.data.innertube.InnerTubeClient.cookie = cookies
        } else {
            prefs.edit {remove("yt_cookies")}
            com.codetrio.spatialflow.data.innertube.InnerTubeClient.cookie = null
        }
    }

    // ── Haptics ──────────────────────────────────────────────────────────────
    private val _hapticsEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_HAPTICS_ENABLED, false))
    val hapticsEnabled: StateFlow<Boolean> = _hapticsEnabled.asStateFlow()

    private val _vibrationStrength =
        MutableStateFlow(prefs.getFloat(KEY_VIBRATION_STRENGTH, 80f))
    val vibrationStrength: StateFlow<Float> = _vibrationStrength.asStateFlow()

    private val _hapticAudioMode =
        MutableStateFlow(prefs.getString(KEY_HAPTIC_AUDIO_MODE, "full") ?: "full")
    val hapticAudioMode: StateFlow<String> = _hapticAudioMode.asStateFlow()

    private val _hapticPunchBoost =
        MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_PUNCH_BOOST, true))
    val hapticPunchBoost: StateFlow<Boolean> = _hapticPunchBoost.asStateFlow()

    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    val hasHaptics: Boolean = vibrator?.hasVibrator() == true

    fun setHapticsEnabled(enabled: Boolean) {
        _hapticsEnabled.value = enabled
        prefs.edit { putBoolean(KEY_HAPTICS_ENABLED, enabled) }
    }

    fun setVibrationStrength(strength: Float) {
        _vibrationStrength.value = strength
        prefs.edit {putFloat(KEY_VIBRATION_STRENGTH, strength)}
        if (strength > 0 && hasHaptics && vibrator != null) {
            com.codetrio.spatialflow.util.PlayerHapticManager.triggerSliderTick(context)
        }
    }

    fun setHapticAudioMode(mode: String) {
        _hapticAudioMode.value = mode
        prefs.edit { putString(KEY_HAPTIC_AUDIO_MODE, mode) }
    }

    fun setHapticPunchBoost(enabled: Boolean) {
        _hapticPunchBoost.value = enabled
        prefs.edit { putBoolean(KEY_HAPTIC_PUNCH_BOOST, enabled) }
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    private val _crossfadeEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_CROSSFADE_ENABLED, false))
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeDuration =
        MutableStateFlow(prefs.getFloat(KEY_CROSSFADE_DURATION, 3f))
    val crossfadeDuration: StateFlow<Float> = _crossfadeDuration.asStateFlow()

    fun setCrossfadeEnabled(enabled: Boolean) {
        _crossfadeEnabled.value = enabled
        prefs.edit { putBoolean(KEY_CROSSFADE_ENABLED, enabled) }
        if (enabled && _autoMixEnabled.value) {
            setAutoMixEnabled(false)
        }
    }

    fun setCrossfadeDuration(duration: Float) {
        _crossfadeDuration.value = duration
        prefs.edit {putFloat(KEY_CROSSFADE_DURATION, duration)}
    }

    private val _crossfadeMode =
        MutableStateFlow(prefs.getString(KEY_CROSSFADE_MODE, "auto") ?: "auto")
    val crossfadeMode: StateFlow<String> = _crossfadeMode.asStateFlow()

    fun setCrossfadeMode(mode: String) {
        _crossfadeMode.value = mode
        prefs.edit {putString(KEY_CROSSFADE_MODE, mode)}
    }

    // ── AutoMix ────────────────────────────────────────────────────────────
    private val _autoMixEnabled =
        MutableStateFlow(prefs.getBoolean(KEY_AUTOMIX_ENABLED, false))
    val autoMixEnabled: StateFlow<Boolean> = _autoMixEnabled.asStateFlow()

    private val _autoMixPerformanceMode =
        MutableStateFlow(prefs.getString(KEY_AUTOMIX_PERFORMANCE_MODE, "BALANCED") ?: "BALANCED")
    val autoMixPerformanceMode: StateFlow<String> = _autoMixPerformanceMode.asStateFlow()

    fun setAutoMixEnabled(enabled: Boolean) {
        _autoMixEnabled.value = enabled
        prefs.edit { putBoolean(KEY_AUTOMIX_ENABLED, enabled) }
        if (enabled && _crossfadeEnabled.value) {
            setCrossfadeEnabled(false)
        }
    }

    fun setAutoMixPerformanceMode(mode: String) {
        _autoMixPerformanceMode.value = mode
        prefs.edit { putString(KEY_AUTOMIX_PERFORMANCE_MODE, mode) }
    }

    // ── Volume Normalization ─────────────────────────────────────────────────
    private val _volumeNormalizationEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, false))
    val volumeNormalizationEnabled: StateFlow<Boolean> = _volumeNormalizationEnabled.asStateFlow()

    private val _targetLufs = MutableStateFlow(prefs.getFloat(KEY_TARGET_LUFS, -14f))
    val targetLufs: StateFlow<Float> = _targetLufs.asStateFlow()

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        _volumeNormalizationEnabled.value = enabled
        prefs.edit {putBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, enabled)}
    }

    fun setTargetLufs(lufs: Float) {
        _targetLufs.value = lufs
        prefs.edit {putFloat(KEY_TARGET_LUFS, lufs)}
    }

    // ── Audio Focus ──────────────────────────────────────────────────────────
    private val _audioFocus = MutableStateFlow(prefs.getBoolean(KEY_AUDIO_FOCUS, true))
    val audioFocus: StateFlow<Boolean> = _audioFocus.asStateFlow()

    fun setAudioFocus(enabled: Boolean) {
        _audioFocus.value = enabled
        prefs.edit {putBoolean(KEY_AUDIO_FOCUS, enabled)}
    }

    // ── Headphones & Bluetooth ─────────────────────────────────────────────
    private val _disconnectAction = MutableStateFlow(prefs.getString(KEY_DISCONNECT_ACTION, "pause") ?: "pause")
    val disconnectAction: StateFlow<String> = _disconnectAction.asStateFlow()

    fun setDisconnectAction(action: String) {
        _disconnectAction.value = action
        prefs.edit { putString(KEY_DISCONNECT_ACTION, action) }
    }

    private val _resumeOnConnect = MutableStateFlow(prefs.getBoolean(KEY_RESUME_ON_CONNECT, false))
    val resumeOnConnect: StateFlow<Boolean> = _resumeOnConnect.asStateFlow()

    fun setResumeOnConnect(enabled: Boolean) {
        _resumeOnConnect.value = enabled
        prefs.edit { putBoolean(KEY_RESUME_ON_CONNECT, enabled) }
    }

    // ── Autoplay ─────────────────────────────────────────────────────────────
    private val _autoplayEnabled = MutableStateFlow(prefs.getBoolean("autoplay_enabled", true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        if (key == "autoplay_enabled") {
            val enabled = sharedPreferences.getBoolean("autoplay_enabled", true)
            if (_autoplayEnabled.value != enabled) {
                _autoplayEnabled.value = enabled
            }
        } else if (key == KEY_PLAYER_THEME) {
            _playerTheme.value = sharedPreferences.getString(KEY_PLAYER_THEME, "fluid") ?: "fluid"
        } else if (key == KEY_MINI_PLAYER_THEME) {
            _miniPlayerTheme.value = sharedPreferences.getString(KEY_MINI_PLAYER_THEME, "blur") ?: "blur"
        } else if (key == KEY_NAV_BAR_BLUR_OPTION) {
            _navBarBlurOption.value = sharedPreferences.getString(KEY_NAV_BAR_BLUR_OPTION, "blur") ?: "blur"
        } else if (key == KEY_NAVIGATION_BLUR) {
            _navigationBlur.value = sharedPreferences.getBoolean(KEY_NAVIGATION_BLUR, true)
        } else if (key == KEY_ALL_IN_ONE_LIQUID_GLASS) {
            _allInOneLiquidGlass.value = sharedPreferences.getBoolean(KEY_ALL_IN_ONE_LIQUID_GLASS, false)
        } else if (key == KEY_SEEKBAR_STYLE) {
            _seekBarStyle.value = sharedPreferences.getString(KEY_SEEKBAR_STYLE, "apple") ?: "apple"
        } else if (key == KEY_IS_SUPPORTER) {
            _isSupporter.value = sharedPreferences.getBoolean(KEY_IS_SUPPORTER, false)
        } else if (key == KEY_AUTOMIX_ENABLED) {
            _autoMixEnabled.value = sharedPreferences.getBoolean(KEY_AUTOMIX_ENABLED, false)
        } else if (key == KEY_AUTOMIX_PERFORMANCE_MODE) {
            _autoMixPerformanceMode.value = sharedPreferences.getString(KEY_AUTOMIX_PERFORMANCE_MODE, "BALANCED") ?: "BALANCED"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        if (_autoplayEnabled.value == enabled) return
        _autoplayEnabled.value = enabled
        prefs.edit {putBoolean("autoplay_enabled", enabled)}
    }

    // ── Pure AMOLED Black ──────────────────────────────────────────────────
    private val _amoledBlack = MutableStateFlow(prefs.getBoolean(KEY_AMOLED_BLACK, false))
    val amoledBlack: StateFlow<Boolean> = _amoledBlack.asStateFlow()

    fun setAmoledBlack(enabled: Boolean) {
        _amoledBlack.value = enabled
        prefs.edit {putBoolean(KEY_AMOLED_BLACK, enabled)}
    }

    // ── Hide Nav Labels ──────────────────────────────────────────────────
    private val _hideNavLabels = MutableStateFlow(prefs.getBoolean("hide_nav_labels", false))
    val hideNavLabels: StateFlow<Boolean> = _hideNavLabels.asStateFlow()

    fun setHideNavLabels(hide: Boolean) {
        _hideNavLabels.value = hide
        prefs.edit {putBoolean("hide_nav_labels", hide)}
    }

    // ── Slide Nav On Scroll ──────────────────────────────────────────────
    private val _slideNavOnScroll = MutableStateFlow(prefs.getBoolean("slide_nav_on_scroll", true))
    val slideNavOnScroll: StateFlow<Boolean> = _slideNavOnScroll.asStateFlow()

    fun setSlideNavOnScroll(enabled: Boolean) {
        _slideNavOnScroll.value = enabled
        prefs.edit { putBoolean("slide_nav_on_scroll", enabled) }
    }



    // ── Dynamic Nav Style ──────────────────────────────────────────────────
    private val _dynamicNavStyle = MutableStateFlow(prefs.getBoolean("dynamic_nav_style", false))
    val dynamicNavStyle: StateFlow<Boolean> = _dynamicNavStyle.asStateFlow()

    fun setDynamicNavStyle(enabled: Boolean) {
        _dynamicNavStyle.value = enabled
        prefs.edit {putBoolean("dynamic_nav_style", enabled)}
    }

    // ── Navigation Blur ──────────────────────────────────────────────────
    private val _navigationBlur = MutableStateFlow(prefs.getBoolean(KEY_NAVIGATION_BLUR, true))
    val navigationBlur: StateFlow<Boolean> = _navigationBlur.asStateFlow()

    fun setNavigationBlur(enabled: Boolean) {
        _navigationBlur.value = enabled
        prefs.edit { putBoolean(KEY_NAVIGATION_BLUR, enabled) }
        if (enabled) {
            if (_allInOneLiquidGlass.value) {
                _allInOneLiquidGlass.value = false
                prefs.edit { putBoolean(KEY_ALL_IN_ONE_LIQUID_GLASS, false) }
            }
            setNavBarBlurOption("blur")
            setMiniPlayerTheme("blur")
        } else {
            setNavBarBlurOption("static")
            setMiniPlayerTheme("static")
        }
    }

    // ── Tab Switch Blur ───────────────────────────────────────────────────
    // Controls whether blur is applied on main tab swipes (Explore↔Library).
    // Off by default since tab switches are frequent and GPU-heavy.
    private val _tabSwitchBlur = MutableStateFlow(prefs.getBoolean(KEY_TAB_SWITCH_BLUR, false))
    val tabSwitchBlur: StateFlow<Boolean> = _tabSwitchBlur.asStateFlow()

    fun setTabSwitchBlur(enabled: Boolean) {
        _tabSwitchBlur.value = enabled
        prefs.edit { putBoolean(KEY_TAB_SWITCH_BLUR, enabled) }
    }

    // ── Dynamic Album Theme ────────────────────────────────────────────────
    private val _dynamicAlbumTheme = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, true))
    val dynamicAlbumTheme: StateFlow<Boolean> = _dynamicAlbumTheme.asStateFlow()

    fun setDynamicAlbumTheme(enabled: Boolean) {
        _dynamicAlbumTheme.value = enabled
        prefs.edit {putBoolean(KEY_DYNAMIC_ALBUM_THEME, enabled)}
    }

    // ── All-in-One Liquid Glass ───────────────────────────────────────────
    private val _allInOneLiquidGlass = MutableStateFlow(prefs.getBoolean(KEY_ALL_IN_ONE_LIQUID_GLASS, false))
    val allInOneLiquidGlass: StateFlow<Boolean> = _allInOneLiquidGlass.asStateFlow()

    fun setAllInOneLiquidGlass(enabled: Boolean) {
        _allInOneLiquidGlass.value = enabled
        prefs.edit { putBoolean(KEY_ALL_IN_ONE_LIQUID_GLASS, enabled) }
        if (enabled) {
            if (_navigationBlur.value) {
                _navigationBlur.value = false
                prefs.edit { putBoolean(KEY_NAVIGATION_BLUR, false) }
            }
            setNavBarBlurOption("liquid")
            setMiniPlayerTheme("liquid")
        } else {
            setNavBarBlurOption("static")
            setMiniPlayerTheme("static")
            if (_playerTheme.value == "liquid") {
                setPlayerTheme("fluid")
            }
        }
    }

    fun setSolidState() {
        _allInOneLiquidGlass.value = false
        _navigationBlur.value = false
        prefs.edit {
            putBoolean(KEY_ALL_IN_ONE_LIQUID_GLASS, false)
            putBoolean(KEY_NAVIGATION_BLUR, false)
            putString(KEY_NAV_BAR_BLUR_OPTION, "static")
            putString(KEY_MINI_PLAYER_THEME, "static")
        }
        _navBarBlurOption.value = "static"
        _miniPlayerTheme.value = "static"
        if (_playerTheme.value == "liquid") {
            setPlayerTheme("fluid")
        }
    }

    // ── Player Theme ──────────────────────────────────────────────────
    private val _playerTheme = MutableStateFlow(prefs.getString(KEY_PLAYER_THEME, "fluid") ?: "fluid")
    val playerTheme: StateFlow<String> = _playerTheme.asStateFlow()

    fun setPlayerTheme(theme: String) {
        _playerTheme.value = theme
        prefs.edit { putString(KEY_PLAYER_THEME, theme) }
    }

    // ── Mini Player Theme ──────────────────────────────────────────────
    private val _miniPlayerTheme = MutableStateFlow(prefs.getString(KEY_MINI_PLAYER_THEME, "blur") ?: "blur")
    val miniPlayerTheme: StateFlow<String> = _miniPlayerTheme.asStateFlow()

    fun setMiniPlayerTheme(theme: String) {
        _miniPlayerTheme.value = theme
        prefs.edit { putString(KEY_MINI_PLAYER_THEME, theme) }
    }

    // ── Nav Bar Blur Option ────────────────────────────────────────────
    private val _navBarBlurOption = MutableStateFlow(prefs.getString(KEY_NAV_BAR_BLUR_OPTION, "blur") ?: "blur")
    val navBarBlurOption: StateFlow<String> = _navBarBlurOption.asStateFlow()

    fun setNavBarBlurOption(option: String) {
        _navBarBlurOption.value = option
        prefs.edit { putString(KEY_NAV_BAR_BLUR_OPTION, option) }
    }

    // ── Navigation Style (Docked vs Floating) ─────────────────────────
    private val _navStyle = MutableStateFlow(prefs.getString(KEY_NAV_STYLE, "docked") ?: "docked")
    val navStyle: StateFlow<String> = _navStyle.asStateFlow()

    fun setNavStyle(style: String) {
        _navStyle.value = style
        prefs.edit { putString(KEY_NAV_STYLE, style) }
    }

    // ── Seek Bar Style ────────────────────────────────────────────────
    private val _seekBarStyle = MutableStateFlow(prefs.getString(KEY_SEEKBAR_STYLE, "apple") ?: "apple")
    val seekBarStyle: StateFlow<String> = _seekBarStyle.asStateFlow()

    fun setSeekBarStyle(style: String) {
        _seekBarStyle.value = style
        prefs.edit { putString(KEY_SEEKBAR_STYLE, style) }
    }


    // ── Ignore Short Audio ──────────────────────────────────────────────────
    private val _ignoreShortAudio = MutableStateFlow(prefs.getBoolean(KEY_IGNORE_SHORT_AUDIO, false))
    val ignoreShortAudio: StateFlow<Boolean> = _ignoreShortAudio.asStateFlow()

    private val _ignoreShortAudioDuration = MutableStateFlow(prefs.getFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, 30f))
    val ignoreShortAudioDuration: StateFlow<Float> = _ignoreShortAudioDuration.asStateFlow()

    fun setIgnoreShortAudio(enabled: Boolean) {
        _ignoreShortAudio.value = enabled
        prefs.edit {putBoolean(KEY_IGNORE_SHORT_AUDIO, enabled)}
    }

    fun setIgnoreShortAudioDuration(duration: Float) {
        _ignoreShortAudioDuration.value = duration
        prefs.edit {putFloat(KEY_IGNORE_SHORT_AUDIO_DURATION, duration)}
    }

    // ── Data Saver ──────────────────────────────────────────────────────────
    private val _dataSaver = MutableStateFlow(prefs.getBoolean(KEY_DATA_SAVER, false))
    val dataSaver: StateFlow<Boolean> = _dataSaver.asStateFlow()

    fun setDataSaver(enabled: Boolean) {
        _dataSaver.value = enabled
        prefs.edit {putBoolean(KEY_DATA_SAVER, enabled)}
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearAllCaches()
    }

    // ── Audio Quality ────────────────────────────────────────────────────────
    private val _audioQuality = MutableStateFlow(prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High")
    val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

    fun setAudioQuality(quality: String) {
        _audioQuality.value = quality
        prefs.edit {putString(KEY_AUDIO_QUALITY, quality)}
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearAllCaches()
    }

    private val _forceOpus = MutableStateFlow(prefs.getBoolean(KEY_FORCE_OPUS, true))
    val forceOpus: StateFlow<Boolean> = _forceOpus.asStateFlow()

    fun setForceOpus(enabled: Boolean) {
        _forceOpus.value = enabled
        prefs.edit { putBoolean(KEY_FORCE_OPUS, enabled) }
        com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor.clearAllCaches()
    }

    // ── Lossless Streaming (Source Pool FLAC) ─────────────────────────────────
    private val _losslessStreamPool = MutableStateFlow(
        prefs.getBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_LOSSLESS_SOURCE_POOL, true)
    )
    val losslessStreamPool: StateFlow<Boolean> = _losslessStreamPool.asStateFlow()

    fun setLosslessStreamPool(enabled: Boolean) {
        _losslessStreamPool.value = enabled
        prefs.edit { putBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_LOSSLESS_SOURCE_POOL, enabled) }
        com.codetrio.spatialflow.data.innertube.YouTubeMusic.clearCache()
        com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.clearCache()
    }

    // ── Lossless Provider & Priority Order ───────────────────────────────────
    private val _losslessPriorityOrder = MutableStateFlow(
        com.codetrio.spatialflow.util.QualityModeSelector.getLosslessPriorityOrder(context)
    )
    val losslessPriorityOrder: StateFlow<List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider>> = _losslessPriorityOrder.asStateFlow()

    private val _disabledLosslessProviders = MutableStateFlow(
        com.codetrio.spatialflow.util.QualityModeSelector.getDisabledLosslessProviders(context)
    )
    val disabledLosslessProviders: StateFlow<Set<String>> = _disabledLosslessProviders.asStateFlow()

    fun setLosslessPriorityOrder(order: List<com.codetrio.spatialflow.data.sourcepool.LosslessProvider>) {
        _losslessPriorityOrder.value = order
        com.codetrio.spatialflow.util.QualityModeSelector.setLosslessPriorityOrder(context, order)
        com.codetrio.spatialflow.data.innertube.YouTubeMusic.clearCache()
        com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.clearCache()
    }

    fun toggleLosslessProvider(provider: com.codetrio.spatialflow.data.sourcepool.LosslessProvider, enabled: Boolean) {
        val current = _disabledLosslessProviders.value.toMutableSet()
        if (enabled) {
            current.remove(provider.id)
        } else {
            current.add(provider.id)
        }
        _disabledLosslessProviders.value = current
        com.codetrio.spatialflow.util.QualityModeSelector.setDisabledLosslessProviders(context, current)
        com.codetrio.spatialflow.data.innertube.YouTubeMusic.clearCache()
        com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.clearCache()
    }

    fun resetLosslessPriorityOrder() {
        val defaultOrder = com.codetrio.spatialflow.data.sourcepool.LosslessProvider.DEFAULT_PRIORITY_ORDER
        _losslessPriorityOrder.value = defaultOrder
        _disabledLosslessProviders.value = emptySet()
        com.codetrio.spatialflow.util.QualityModeSelector.setLosslessPriorityOrder(context, defaultOrder)
        com.codetrio.spatialflow.util.QualityModeSelector.setDisabledLosslessProviders(context, emptySet())
        com.codetrio.spatialflow.data.innertube.YouTubeMusic.clearCache()
        com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.clearCache()
    }

    // ── USB DAC & Bit-Perfect ────────────────────────────────────────────────
    private val _dacDirectMode = MutableStateFlow(
        prefs.getBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_DIRECT_MODE, true)
    )
    val dacDirectMode: StateFlow<Boolean> = _dacDirectMode.asStateFlow()

    fun setDacDirectMode(enabled: Boolean) {
        _dacDirectMode.value = enabled
        prefs.edit { putBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_DIRECT_MODE, enabled) }
    }

    private val _dacBitPerfect = MutableStateFlow(
        prefs.getBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_BIT_PERFECT, true)
    )
    val dacBitPerfect: StateFlow<Boolean> = _dacBitPerfect.asStateFlow()

    fun setDacBitPerfect(enabled: Boolean) {
        _dacBitPerfect.value = enabled
        com.codetrio.spatialflow.util.QualityModeSelector.setDacBitPerfect(context, enabled)
    }

    private val _nativeSampleRate = MutableStateFlow(
        com.codetrio.spatialflow.util.QualityModeSelector.isNativeSampleRateEnabled(context)
    )
    val nativeSampleRate: StateFlow<Boolean> = _nativeSampleRate.asStateFlow()

    fun setNativeSampleRate(enabled: Boolean) {
        _nativeSampleRate.value = enabled
        com.codetrio.spatialflow.util.QualityModeSelector.setNativeSampleRate(context, enabled)
    }

    private val _dacFixedVolume = MutableStateFlow(
        prefs.getBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_FIXED_VOLUME, false)
    )
    val dacFixedVolume: StateFlow<Boolean> = _dacFixedVolume.asStateFlow()

    fun setDacFixedVolume(enabled: Boolean) {
        _dacFixedVolume.value = enabled
        prefs.edit { putBoolean(com.codetrio.spatialflow.util.QualityModeSelector.KEY_DAC_FIXED_VOLUME, enabled) }
    }

    private val _dacBannerEnabled = MutableStateFlow(
        prefs.getBoolean("dac_connection_banner", true)
    )
    val dacBannerEnabled: StateFlow<Boolean> = _dacBannerEnabled.asStateFlow()

    fun setDacBannerEnabled(enabled: Boolean) {
        _dacBannerEnabled.value = enabled
        prefs.edit { putBoolean("dac_connection_banner", enabled) }
    }

    // ── Pause Listening History ────────────────────────────────────────────────
    private val _pauseHistory = MutableStateFlow(prefs.getBoolean("pause_history", false))
    val pauseHistory: StateFlow<Boolean> = _pauseHistory.asStateFlow()

    fun setPauseHistory(pause: Boolean) {
        _pauseHistory.value = pause
        prefs.edit {putBoolean("pause_history", pause)}
    }

    // ── High Refresh Rate ────────────────────────────────────────────────────
    private val _forceHighRefreshRate = MutableStateFlow(prefs.getBoolean("force_high_refresh_rate", false))
    val forceHighRefreshRate: StateFlow<Boolean> = _forceHighRefreshRate.asStateFlow()

    fun setForceHighRefreshRate(force: Boolean) {
        _forceHighRefreshRate.value = force
        prefs.edit {putBoolean("force_high_refresh_rate", force)}
    }

    // ── Animated Album Art ───────────────────────────────────────────────────
    private val _showAnimatedArt = MutableStateFlow(prefs.getBoolean("show_animated_art", true))
    val showAnimatedArt: StateFlow<Boolean> = _showAnimatedArt.asStateFlow()

    fun setShowAnimatedArt(show: Boolean) {
        _showAnimatedArt.value = show
        prefs.edit {putBoolean("show_animated_art", show)}
    }

    // ── Canvas Provider Priority Order ──────────────────────────────────────────
    private val _canvasProviderOrder = MutableStateFlow(
        (prefs.getString("canvas_provider_order", "spotify,apple_music") ?: "spotify,apple_music")
            .split(",")
            .map { it.trim() }
            .filter { it in listOf("spotify", "apple_music") }
    )
    val canvasProviderOrder: StateFlow<List<String>> = _canvasProviderOrder.asStateFlow()

    fun setCanvasProviderOrder(order: List<String>) {
        _canvasProviderOrder.value = order
        prefs.edit { putString("canvas_provider_order", order.joinToString(",")) }
        com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache.clear()
        com.codetrio.spatialflow.ui.player.canvas.SongCanvasProvider.clearCache()
    }

    // ── Haptics Granular ─────────────────────────────────────────────────────
    private val _hapticPlayPause = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_PLAY_PAUSE, true))
    val hapticPlayPause: StateFlow<Boolean> = _hapticPlayPause.asStateFlow()

    private val _hapticSliderTicks = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_SLIDER_TICKS, true))
    val hapticSliderTicks: StateFlow<Boolean> = _hapticSliderTicks.asStateFlow()

    private val _hapticQueue = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_QUEUE, true))
    val hapticQueue: StateFlow<Boolean> = _hapticQueue.asStateFlow()

    private val _hapticFavorite = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FAVORITE, true))
    val hapticFavorite: StateFlow<Boolean> = _hapticFavorite.asStateFlow()

    private val _hapticPauseInBackground = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_PAUSE_BACKGROUND, true))
    val hapticPauseInBackground: StateFlow<Boolean> = _hapticPauseInBackground.asStateFlow()

    private val _hapticBatterySaver = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_BATTERY_SAVER, true))
    val hapticBatterySaver: StateFlow<Boolean> = _hapticBatterySaver.asStateFlow()

    fun setHapticPlayPause(enabled: Boolean) {
        _hapticPlayPause.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_PLAY_PAUSE, enabled)}
    }

    fun setHapticSliderTicks(enabled: Boolean) {
        _hapticSliderTicks.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_SLIDER_TICKS, enabled)}
    }

    fun setHapticQueue(enabled: Boolean) {
        _hapticQueue.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_QUEUE, enabled)}
    }

    fun setHapticFavorite(enabled: Boolean) {
        _hapticFavorite.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_FAVORITE, enabled)}
    }

    fun setHapticPauseInBackground(enabled: Boolean) {
        _hapticPauseInBackground.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_PAUSE_BACKGROUND, enabled)}
    }

    fun setHapticBatterySaver(enabled: Boolean) {
        _hapticBatterySaver.value = enabled
        prefs.edit {putBoolean(KEY_HAPTIC_BATTERY_SAVER, enabled)}
    }



    // ── Library Paths ────────────────────────────────────────────────────────
    private val _libraryPaths = MutableStateFlow(
        (prefs.getString(KEY_LIBRARY_PATHS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val libraryPaths: StateFlow<List<String>> = _libraryPaths.asStateFlow()

    fun addLibraryPath(path: String) {
        val current = _libraryPaths.value
        if (current.contains(path)) return
        val newList = current + path
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    fun removeLibraryPath(path: String) {
        val current = _libraryPaths.value
        val newList = current.filter { it != path }
        _libraryPaths.value = newList
        prefs.edit {putString(KEY_LIBRARY_PATHS, newList.joinToString("||"))}
    }

    // ── Hidden Folders ────────────────────────────────────────────────────────
    private val _hiddenFolders = MutableStateFlow(
        (prefs.getString(KEY_HIDDEN_FOLDERS, "") ?: "").split("||").filter { it.isNotEmpty() }
    )
    val hiddenFolders: StateFlow<List<String>> = _hiddenFolders.asStateFlow()

    fun addHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        if (current.contains(path)) return
        val newList = current + path
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    fun removeHiddenFolder(path: String) {
        val current = _hiddenFolders.value
        val newList = current.filter { it != path }
        _hiddenFolders.value = newList
        prefs.edit {putString(KEY_HIDDEN_FOLDERS, newList.joinToString("||"))}
    }

    // ── Cache & Downloads ───────────────────────────────────────────────────
    private val _downloadFolder = MutableStateFlow(prefs.getString("download_folder", null))
    val downloadFolder: StateFlow<String?> = _downloadFolder.asStateFlow()

    fun setDownloadFolder(uri: String?) {
        _downloadFolder.value = uri
        if (uri != null) {
            prefs.edit {putString("download_folder", uri)}
        } else {
            prefs.edit {remove("download_folder")}
        }
    }

    private val _wifiOnlyDownload = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOAD, false))
    val wifiOnlyDownload: StateFlow<Boolean> = _wifiOnlyDownload.asStateFlow()

    fun setWifiOnlyDownload(enabled: Boolean) {
        _wifiOnlyDownload.value = enabled
        prefs.edit { putBoolean(KEY_WIFI_ONLY_DOWNLOAD, enabled) }
    }

    private val _downloadAudioQuality = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_AUDIO_QUALITY, "High") ?: "High")
    val downloadAudioQuality: StateFlow<String> = _downloadAudioQuality.asStateFlow()

    fun setDownloadAudioQuality(quality: String) {
        _downloadAudioQuality.value = quality
        prefs.edit { putString(KEY_DOWNLOAD_AUDIO_QUALITY, quality) }
    }

    private val _downloadAudioFormat = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_AUDIO_FORMAT, "Opus") ?: "Opus")
    val downloadAudioFormat: StateFlow<String> = _downloadAudioFormat.asStateFlow()

    fun setDownloadAudioFormat(format: String) {
        _downloadAudioFormat.value = format
        prefs.edit { putString(KEY_DOWNLOAD_AUDIO_FORMAT, format) }
    }

    private val _autoDownloadFavorites = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DOWNLOAD_FAVORITES, false))
    val autoDownloadFavorites: StateFlow<Boolean> = _autoDownloadFavorites.asStateFlow()

    fun setAutoDownloadFavorites(enabled: Boolean) {
        _autoDownloadFavorites.value = enabled
        prefs.edit { putBoolean(KEY_AUTO_DOWNLOAD_FAVORITES, enabled) }
    }

    private val _offlineLyrics = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_LYRICS, true))
    val offlineLyrics: StateFlow<Boolean> = _offlineLyrics.asStateFlow()

    fun setOfflineLyrics(enabled: Boolean) {
        _offlineLyrics.value = enabled
        prefs.edit { putBoolean(KEY_OFFLINE_LYRICS, enabled) }
    }

    private val _offlineOnlyMode = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_ONLY_MODE, false))
    val offlineOnlyMode: StateFlow<Boolean> = _offlineOnlyMode.asStateFlow()

    fun setOfflineOnlyMode(enabled: Boolean) {
        _offlineOnlyMode.value = enabled
        prefs.edit { putBoolean(KEY_OFFLINE_ONLY_MODE, enabled) }
    }

    // Sizes in MB. -1 or 0 could mean unlimited, or we can use large numbers. We'll use Int (MB). 0 = unlimited.
    private val _songCacheMaxSize = MutableStateFlow(prefs.getInt("song_cache_max_size", 256)) // Default 256MB
    val songCacheMaxSize: StateFlow<Int> = _songCacheMaxSize.asStateFlow()

    fun setSongCacheMaxSize(sizeMb: Int) {
        _songCacheMaxSize.value = sizeMb
        prefs.edit {putInt("song_cache_max_size", sizeMb)}
    }

    private val _imageCacheMaxSize = MutableStateFlow(prefs.getInt("image_cache_max_size", 128)) // Default 128MB
    val imageCacheMaxSize: StateFlow<Int> = _imageCacheMaxSize.asStateFlow()

    fun setImageCacheMaxSize(sizeMb: Int) {
        _imageCacheMaxSize.value = sizeMb
        prefs.edit {putInt("image_cache_max_size", sizeMb)}
    }

    // ── Database & Scanning Tools ─────────────────────────────────────────────
    private val _isLibraryScanning = MutableStateFlow(false)
    val isLibraryScanning: StateFlow<Boolean> = _isLibraryScanning.asStateFlow()

    fun performFullScan(playerSharedViewModel: PlayerSharedViewModel, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryScanning.value = true
            val pathsToScan = mutableListOf<File>()

            // Resolve configured library paths
            val currentPaths = libraryPaths.value
            currentPaths.forEach { pathUriStr ->
                try {
                    val uri = Uri.parse(pathUriStr)
                    val absolutePath = getPathFromUri(uri)
                    val file = File(absolutePath)
                    if (file.exists() && file.isDirectory) {
                        pathsToScan.add(file)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Invalid library path: $pathUriStr", e)
                }
            }

            // Fallback to standard dirs if no custom paths
            if (pathsToScan.isEmpty()) {
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                if (musicDir.exists()) pathsToScan.add(musicDir)
                val downloadDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
                if (downloadDir.exists()) pathsToScan.add(downloadDir)
            }

            // Find all audio files recursively
            val filesToScan = mutableListOf<String>()
            val audioExtensions = setOf("mp3", "flac", "wav", "m4a", "ogg", "aac", "opus", "wma", "amr")

            pathsToScan.forEach { dir ->
                try {
                    dir.walkTopDown().forEach { file ->
                        if (file.isFile && file.extension.lowercase() in audioExtensions) {
                            filesToScan.add(file.absolutePath)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Error walking directory: ${dir.absolutePath}", e)
                }
            }

            if (filesToScan.isNotEmpty()) {
                val latch = java.util.concurrent.CountDownLatch(1)
                val chunkSize = 100
                val chunks = filesToScan.chunked(chunkSize)

                val scannedCount = java.util.concurrent.atomic.AtomicInteger(0)
                chunks.forEach { chunk ->
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        chunk.toTypedArray(),
                        null
                    ) { _, _ ->
                        if (scannedCount.incrementAndGet() >= filesToScan.size) {
                            latch.countDown()
                        }
                    }
                }

                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            }

            // Reload the local media files in PlayerSharedViewModel
            playerSharedViewModel.rescanLocalFiles()

            _isLibraryScanning.value = false
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun rebuildDatabase(playerSharedViewModel: PlayerSharedViewModel, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLibraryScanning.value = true

            // 1. Clear song and image cache safely via cacheDataSource
            try {
                cacheDataSource.clearCategory(CacheCategory.SONGS)
                cacheDataSource.clearCategory(CacheCategory.IMAGES)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to clear caches during rebuild", e)
            }

            // 3. Force perform full scan
            performFullScan(playerSharedViewModel) {
                _isLibraryScanning.value = false
                onComplete()
            }
        }
    }

    // ── Backup & Restore ──────────────────────────────────────────────────────
    data class BackupSummaryData(
        val uri: Uri,
        val fileName: String = "",
        val fileSizeBytes: Long = 0L,
        val version: Int,
        val timestamp: Long,
        val device: String?,
        val settingsCount: Int,
        val favoritesCount: Int,
        val playlistsCount: Int,
        val songsCount: Int,
        val historyCount: Int,
        val downloadsManifestCount: Int,
        val rawJsonObject: com.google.gson.JsonObject
    )

    enum class RestoreMode {
        MERGE, OVERWRITE
    }

    val localBackups = MutableStateFlow<List<BackupSummaryData>>(emptyList())
    val discoveredBackupPrompt = MutableStateFlow<BackupSummaryData?>(null)
    val autoBackupEnabled = MutableStateFlow(prefs.getBoolean("auto_backup_enabled", false))
    val autoBackupFreq = MutableStateFlow(prefs.getString("auto_backup_freq", "weekly") ?: "weekly")

    init {
        scanLocalBackups(context)
    }

    fun dismissDiscoveredBackupPrompt() {
        prefs.edit().putBoolean("dismissed_restore_prompt", true).apply()
        discoveredBackupPrompt.value = null
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        autoBackupEnabled.value = enabled
    }

    fun setAutoBackupFreq(freq: String) {
        prefs.edit().putString("auto_backup_freq", freq).apply()
        autoBackupFreq.value = freq
    }

    fun getBackupsDir(): File {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun scanLocalBackups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidateDirs = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Backups"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SpatialFlow/Backups"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SpatialFlow"),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            val foundFiles = mutableSetOf<File>()
            candidateDirs.forEach { dir ->
                try {
                    if (dir.exists() && dir.isDirectory) {
                        val files = dir.listFiles { _, name ->
                            val lower = name.lowercase()
                            lower.endsWith(".json") && (
                                lower == "backup.json" ||
                                lower.contains("spatialflow") ||
                                lower.contains("backup")
                            )
                        } ?: emptyArray()
                        foundFiles.addAll(files)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Failed to access candidate dir: ${dir.absolutePath}", e)
                }
            }

            val list = mutableListOf<BackupSummaryData>()
            val gson = com.google.gson.Gson()
            foundFiles.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val backupObj = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java) ?: return@forEach

                    val hasBackupMarkers = backupObj.has("settings") ||
                        backupObj.has("favorites") ||
                        backupObj.has("playlists") ||
                        backupObj.has("playlist_songs") ||
                        backupObj.has("version") ||
                        backupObj.has("downloads_manifest") ||
                        file.name.equals("backup.json", ignoreCase = true)

                    if (!hasBackupMarkers) return@forEach

                    val version = if (backupObj.has("version")) backupObj.get("version").asInt else 1
                    val timestamp = if (backupObj.has("timestamp")) backupObj.get("timestamp").asLong else file.lastModified()
                    val device = if (backupObj.has("device")) backupObj.get("device").asString else null

                    val settingsCount = if (backupObj.has("settings")) backupObj.getAsJsonObject("settings")?.size() ?: 0 else 0
                    val favoritesCount = if (backupObj.has("favorites")) backupObj.getAsJsonArray("favorites")?.size() ?: 0 else 0
                    val playlistsCount = if (backupObj.has("playlists")) backupObj.getAsJsonArray("playlists")?.size() ?: 0 else 0
                    val songsCount = if (backupObj.has("playlist_songs")) backupObj.getAsJsonArray("playlist_songs")?.size() ?: 0 else 0
                    val historyCount = if (backupObj.has("history")) backupObj.getAsJsonArray("history")?.size() ?: 0 else 0
                    val downloadsManifestCount = if (backupObj.has("downloads_manifest")) backupObj.getAsJsonArray("downloads_manifest")?.size() ?: 0 else 0

                    list.add(
                        BackupSummaryData(
                            uri = Uri.fromFile(file),
                            fileName = file.name,
                            fileSizeBytes = file.length(),
                            version = version,
                            timestamp = timestamp,
                            device = device,
                            settingsCount = settingsCount,
                            favoritesCount = favoritesCount,
                            playlistsCount = playlistsCount,
                            songsCount = songsCount,
                            historyCount = historyCount,
                            downloadsManifestCount = downloadsManifestCount,
                            rawJsonObject = backupObj
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SettingsViewModel", "Skipping invalid backup file ${file.name}", e)
                }
            }
            val sorted = list.sortedByDescending { it.timestamp }
            withContext(Dispatchers.Main) {
                localBackups.value = sorted
                val hasRestored = prefs.getBoolean("has_restored_backup", false)
                val dismissedPrompt = prefs.getBoolean("dismissed_restore_prompt", false)
                if (!hasRestored && !dismissedPrompt && sorted.isNotEmpty()) {
                    discoveredBackupPrompt.value = sorted.firstOrNull()
                }
            }
        }
    }

    fun exportBackupDirectToDefaultFolder(
        context: Context,
        includeSettings: Boolean = true,
        includeFavorites: Boolean = true,
        includePlaylists: Boolean = true,
        includeHistory: Boolean = true,
        includeDownloadsManifest: Boolean = true,
        onResult: (Boolean, String, Uri?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val dir = getBackupsDir()
                val timeName = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                val destFile = File(dir, "spatialflow_backup_$timeName.json")

                val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()

                val backupObject = com.google.gson.JsonObject().apply {
                    addProperty("version", 2)
                    addProperty("timestamp", System.currentTimeMillis())
                    addProperty("app_version", "1.0.0")
                    addProperty("device", "${Build.MANUFACTURER} ${Build.MODEL}")

                    if (includeSettings) {
                        val settingsObj = com.google.gson.JsonObject()
                        prefs.all.forEach { (key, value) ->
                            when (value) {
                                is Boolean -> settingsObj.addProperty(key, value)
                                is Float -> settingsObj.addProperty(key, value)
                                is Int -> settingsObj.addProperty(key, value)
                                is Long -> settingsObj.addProperty(key, value)
                                is String -> settingsObj.addProperty(key, value)
                            }
                        }
                        add("settings", settingsObj)
                    }

                    if (includeFavorites) {
                        val favManager = com.codetrio.spatialflow.util.FavoritesManager(context)
                        val favArray = com.google.gson.JsonArray()
                        favManager.favoriteIds.forEach { favArray.add(it) }
                        add("favorites", favArray)
                    }

                    val gson = com.google.gson.Gson()
                    if (includePlaylists) {
                        val playlists = playlistDao.getAllPlaylists().first()
                        val playlistSongsList = mutableListOf<com.codetrio.spatialflow.data.db.PlaylistSongEntity>()
                        playlists.forEach { playlist ->
                            val songs = playlistDao.getSongsForPlaylist(playlist.id).first()
                            playlistSongsList.addAll(songs)
                        }
                        add("playlists", gson.toJsonTree(playlists))
                        add("playlist_songs", gson.toJsonTree(playlistSongsList))
                    }

                    if (includeHistory) {
                        val historyList = playlistDao.getAllHistoryEventsSync()
                        add("history", gson.toJsonTree(historyList))
                    }

                    if (includeDownloadsManifest) {
                        val downloadedSongs = com.codetrio.spatialflow.util.SongDownloader.getAllDownloadedSongs(context)
                        add("downloads_manifest", gson.toJsonTree(downloadedSongs))
                    }
                }

                destFile.writeText(backupObject.toString())
                scanLocalBackups(context)

                withContext(Dispatchers.Main) {
                    onResult(true, "Saved to SpatialFlow/Backups/${destFile.name}", Uri.fromFile(destFile))
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to export backup to default folder", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Export failed: ${e.localizedMessage}", null)
                }
            }
        }
    }

    fun deleteLocalBackup(context: Context, summary: BackupSummaryData) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (summary.uri.scheme == "file") {
                    val file = File(summary.uri.path ?: "")
                    if (file.exists()) file.delete()
                }
                scanLocalBackups(context)
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to delete backup file", e)
            }
        }
    }

    fun inspectBackupFile(context: Context, uri: Uri, onResult: (BackupSummaryData?, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader().use { it.readText() }
                } ?: throw Exception("Could not read file stream")

                val gson = com.google.gson.Gson()
                val backupObj = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)

                val version = if (backupObj.has("version")) backupObj.get("version").asInt else 1
                val timestamp = if (backupObj.has("timestamp")) backupObj.get("timestamp").asLong else 0L
                val device = if (backupObj.has("device")) backupObj.get("device").asString else null

                val settingsCount = if (backupObj.has("settings")) backupObj.getAsJsonObject("settings").size() else 0
                val favoritesCount = if (backupObj.has("favorites")) backupObj.getAsJsonArray("favorites").size() else 0
                val playlistsCount = if (backupObj.has("playlists")) backupObj.getAsJsonArray("playlists").size() else 0
                val songsCount = if (backupObj.has("playlist_songs")) backupObj.getAsJsonArray("playlist_songs").size() else 0
                val historyCount = if (backupObj.has("history")) backupObj.getAsJsonArray("history").size() else 0
                val downloadsManifestCount = if (backupObj.has("downloads_manifest")) backupObj.getAsJsonArray("downloads_manifest").size() else 0

                val fileName = if (uri.scheme == "file") File(uri.path ?: "").name else "External Backup"
                val fileSizeBytes = if (uri.scheme == "file") File(uri.path ?: "").length() else 0L

                val summary = BackupSummaryData(
                    uri = uri,
                    fileName = fileName,
                    fileSizeBytes = fileSizeBytes,
                    version = version,
                    timestamp = timestamp,
                    device = device,
                    settingsCount = settingsCount,
                    favoritesCount = favoritesCount,
                    playlistsCount = playlistsCount,
                    songsCount = songsCount,
                    historyCount = historyCount,
                    downloadsManifestCount = downloadsManifestCount,
                    rawJsonObject = backupObj
                )
                withContext(Dispatchers.Main) {
                    onResult(summary, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to inspect backup file", e)
                withContext(Dispatchers.Main) {
                    onResult(null, "Failed to read backup: ${e.localizedMessage}")
                }
            }
        }
    }

    fun exportBackup(
        context: Context,
        uri: Uri,
        includeSettings: Boolean = true,
        includeFavorites: Boolean = true,
        includePlaylists: Boolean = true,
        includeHistory: Boolean = true,
        includeDownloadsManifest: Boolean = true,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()

                val backupObject = com.google.gson.JsonObject().apply {
                    addProperty("version", 2)
                    addProperty("timestamp", System.currentTimeMillis())
                    addProperty("app_version", "1.0.0")
                    addProperty("device", "${Build.MANUFACTURER} ${Build.MODEL}")

                    if (includeSettings) {
                        val settingsObj = com.google.gson.JsonObject()
                        prefs.all.forEach { (key, value) ->
                            when (value) {
                                is Boolean -> settingsObj.addProperty(key, value)
                                is Float -> settingsObj.addProperty(key, value)
                                is Int -> settingsObj.addProperty(key, value)
                                is Long -> settingsObj.addProperty(key, value)
                                is String -> settingsObj.addProperty(key, value)
                            }
                        }
                        add("settings", settingsObj)
                    }

                    if (includeFavorites) {
                        val favManager = com.codetrio.spatialflow.util.FavoritesManager(context)
                        val favArray = com.google.gson.JsonArray()
                        favManager.favoriteIds.forEach { favArray.add(it) }
                        add("favorites", favArray)
                    }

                    val gson = com.google.gson.Gson()
                    if (includePlaylists) {
                        val playlists = playlistDao.getAllPlaylists().first()
                        val playlistSongsList = mutableListOf<com.codetrio.spatialflow.data.db.PlaylistSongEntity>()
                        playlists.forEach { playlist ->
                            val songs = playlistDao.getSongsForPlaylist(playlist.id).first()
                            playlistSongsList.addAll(songs)
                        }
                        add("playlists", gson.toJsonTree(playlists))
                        add("playlist_songs", gson.toJsonTree(playlistSongsList))
                    }

                    if (includeHistory) {
                        val historyList = playlistDao.getAllHistoryEventsSync()
                        add("history", gson.toJsonTree(historyList))
                    }

                    if (includeDownloadsManifest) {
                        val downloadedSongs = com.codetrio.spatialflow.util.SongDownloader.getAllDownloadedSongs(context)
                        add("downloads_manifest", gson.toJsonTree(downloadedSongs))
                    }
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(backupObject.toString().toByteArray())
                }

                withContext(Dispatchers.Main) {
                    onResult(true, "Backup exported successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to export backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Failed to export: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importBackup(
        context: Context,
        summary: BackupSummaryData,
        restoreMode: RestoreMode = RestoreMode.MERGE,
        restoreSettings: Boolean = true,
        restoreFavorites: Boolean = true,
        restorePlaylists: Boolean = true,
        restoreHistory: Boolean = true,
        restoreDownloadsManifest: Boolean = true,
        onResult: (Boolean, String, List<com.codetrio.spatialflow.model.SongItem>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupObject = summary.rawJsonObject
                val gson = com.google.gson.Gson()
                val db = com.codetrio.spatialflow.data.db.MusicDatabase.getDatabase(context)
                val playlistDao = db.playlistDao()
                val favManager = com.codetrio.spatialflow.util.FavoritesManager(context)

                if (restoreMode == RestoreMode.OVERWRITE) {
                    if (restoreHistory) playlistDao.clearAllHistory()
                }

                if (restoreSettings && backupObject.has("settings")) {
                    val settingsObj = backupObject.getAsJsonObject("settings")
                    prefs.edit {
                        settingsObj.entrySet().forEach { (key, jsonElement) ->
                            if (jsonElement.isJsonPrimitive) {
                                val prim = jsonElement.asJsonPrimitive
                                if (prim.isBoolean) putBoolean(key, prim.asBoolean)
                                else if (prim.isNumber) {
                                    val num = prim.asNumber
                                    when (key) {
                                        KEY_VIBRATION_STRENGTH, KEY_CROSSFADE_DURATION, KEY_TARGET_LUFS -> putFloat(key, num.toFloat())
                                        else -> {
                                            try {
                                                putInt(key, num.toInt())
                                            } catch (_: Exception) {
                                                putFloat(key, num.toFloat())
                                            }
                                        }
                                    }
                                }
                                else if (prim.isString) putString(key, prim.asString)
                            }
                        }
                    }

                    _darkMode.value = prefs.getBoolean(KEY_DARK_MODE, true)
                    _amoledBlack.value = prefs.getBoolean(KEY_AMOLED_BLACK, false)
                    _showAnimatedArt.value = prefs.getBoolean("show_animated_art", true)
                    val orderRaw = prefs.getString("canvas_provider_order", "spotify,apple_music") ?: "spotify,apple_music"
                    _canvasProviderOrder.value = orderRaw.split(",").map { it.trim() }.filter { it in listOf("spotify", "apple_music") }
                    _dynamicAlbumTheme.value = prefs.getBoolean(KEY_DYNAMIC_ALBUM_THEME, true)
                    _vibrationStrength.value = prefs.getFloat(KEY_VIBRATION_STRENGTH, 80f)
                    _crossfadeEnabled.value = prefs.getBoolean(KEY_CROSSFADE_ENABLED, false)
                    _crossfadeDuration.value = prefs.getFloat(KEY_CROSSFADE_DURATION, 3f)
                    _crossfadeMode.value = prefs.getString(KEY_CROSSFADE_MODE, "auto") ?: "auto"
                    _autoMixEnabled.value = prefs.getBoolean(KEY_AUTOMIX_ENABLED, false)
                    _autoMixPerformanceMode.value = prefs.getString(KEY_AUTOMIX_PERFORMANCE_MODE, "BALANCED") ?: "BALANCED"
                    _audioFocus.value = prefs.getBoolean(KEY_AUDIO_FOCUS, true)
                    _dataSaver.value = prefs.getBoolean(KEY_DATA_SAVER, false)
                    _audioQuality.value = prefs.getString(KEY_AUDIO_QUALITY, "High") ?: "High"
                    _pauseHistory.value = prefs.getBoolean("pause_history", false)
                    _downloadFolder.value = prefs.getString("download_folder", null)
                    _songCacheMaxSize.value = prefs.getInt("song_cache_max_size", 256)
                    _imageCacheMaxSize.value = prefs.getInt("image_cache_max_size", 128)
                    _hapticPlayPause.value = prefs.getBoolean(KEY_HAPTIC_PLAY_PAUSE, true)
                    _hapticQueue.value = prefs.getBoolean(KEY_HAPTIC_QUEUE, true)
                    _hapticFavorite.value = prefs.getBoolean(KEY_HAPTIC_FAVORITE, true)
                    _hapticPauseInBackground.value = prefs.getBoolean(KEY_HAPTIC_PAUSE_BACKGROUND, true)
                    _volumeNormalizationEnabled.value = prefs.getBoolean(KEY_VOLUME_NORMALIZATION_ENABLED, false)
                    _targetLufs.value = prefs.getFloat(KEY_TARGET_LUFS, -14f)
                    _navigationBlur.value = prefs.getBoolean(KEY_NAVIGATION_BLUR, true)
                    _forceHighRefreshRate.value = prefs.getBoolean("force_high_refresh_rate", false)
                    _playerTheme.value = prefs.getString(KEY_PLAYER_THEME, "fluid") ?: "fluid"
                    _miniPlayerTheme.value = prefs.getString(KEY_MINI_PLAYER_THEME, "blur") ?: "blur"
                    _navBarBlurOption.value = prefs.getString(KEY_NAV_BAR_BLUR_OPTION, "blur") ?: "blur"
                    _navStyle.value = prefs.getString(KEY_NAV_STYLE, "docked") ?: "docked"
                    _disconnectAction.value = prefs.getString(KEY_DISCONNECT_ACTION, "pause") ?: "pause"
                    _resumeOnConnect.value = prefs.getBoolean(KEY_RESUME_ON_CONNECT, false)
                    _seekBarStyle.value = prefs.getString(KEY_SEEKBAR_STYLE, "apple") ?: "apple"
                }

                if (restoreFavorites && backupObject.has("favorites")) {
                    val favArray = backupObject.getAsJsonArray("favorites")
                    favArray.forEach { element ->
                        val idStr = element.asString
                        idStr.toLongOrNull()?.let { idLong ->
                            favManager.setFavorite(idLong, true)
                        }
                    }
                }

                if (restorePlaylists && backupObject.has("playlists")) {
                    val typeTokenPlaylists = object :
                        com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.data.db.PlaylistEntity>>() {}.type
                    val playlistsList: List<com.codetrio.spatialflow.data.db.PlaylistEntity> =
                        gson.fromJson(backupObject.get("playlists"), typeTokenPlaylists)

                    playlistsList.forEach { playlist ->
                        playlistDao.insertPlaylist(playlist)
                    }
                }

                if (restorePlaylists && backupObject.has("playlist_songs")) {
                    val typeTokenSongs = object : com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.data.db.PlaylistSongEntity>>() {}.type
                    val songsList: List<com.codetrio.spatialflow.data.db.PlaylistSongEntity> = gson.fromJson(backupObject.get("playlist_songs"), typeTokenSongs)

                    songsList.forEach { song ->
                        playlistDao.insertPlaylistSong(song)
                    }
                }

                if (restoreHistory && backupObject.has("history")) {
                    val typeTokenHistory = object : com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.data.db.HistoryEventEntity>>() {}.type
                    val historyEvents: List<com.codetrio.spatialflow.data.db.HistoryEventEntity> = gson.fromJson(backupObject.get("history"), typeTokenHistory)

                    historyEvents.forEach { event ->
                        playlistDao.insertHistoryEvent(event)
                    }
                }

                val manifestSongs = mutableListOf<com.codetrio.spatialflow.model.SongItem>()
                if (restoreDownloadsManifest && backupObject.has("downloads_manifest")) {
                    val typeTokenManifest = object : com.google.gson.reflect.TypeToken<List<com.codetrio.spatialflow.model.SongItem>>() {}.type
                    val items: List<com.codetrio.spatialflow.model.SongItem> = gson.fromJson(backupObject.get("downloads_manifest"), typeTokenManifest)
                    manifestSongs.addAll(items)
                }

                withContext(Dispatchers.Main) {
                    prefs.edit().putBoolean("has_restored_backup", true).apply()
                    discoveredBackupPrompt.value = null
                    onResult(true, "Backup imported successfully", manifestSongs)
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to import backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Import failed: ${e.localizedMessage}", emptyList())
                }
            }
        }
    }

    // ── Cache ────────────────────────────────────────────────────────────────
    val cacheBreakdown: StateFlow<CacheBreakdown>
        get() = cacheDataSource.cacheBreakdown

    private val _songCacheSize = MutableStateFlow("Calculating...")
    val songCacheSize: StateFlow<String> = _songCacheSize.asStateFlow()

    private val _imageCacheSize = MutableStateFlow("Calculating...")
    val imageCacheSize: StateFlow<String> = _imageCacheSize.asStateFlow()

    init {
        viewModelScope.launch {
            cacheDataSource.cacheBreakdown.collect { breakdown ->
                _songCacheSize.value = breakdown.categories[CacheCategory.SONGS]?.formattedSize ?: "0 B"
                _imageCacheSize.value = breakdown.categories[CacheCategory.IMAGES]?.formattedSize ?: "0 B"
            }
        }
    }

    fun calculateCacheSize() {
        viewModelScope.launch {
            cacheDataSource.refreshCacheSizes()
        }
    }

    fun clearCategoryCache(category: CacheCategory, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            cacheDataSource.clearCategory(category)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clearAllCaches(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            cacheDataSource.clearAllCaches()
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun clearSongCache(onComplete: () -> Unit) {
        clearCategoryCache(CacheCategory.SONGS, onComplete)
    }

    fun clearImageCache(onComplete: () -> Unit) {
        clearCategoryCache(CacheCategory.IMAGES, onComplete)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isFile) file.length() else getDirSize(file)
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups =
            (log10(bytes.toDouble()) / log10(1024.0)).toInt()
                .coerceAtMost(units.size - 1)
        return DecimalFormat("#.##")
            .format(bytes / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }

    private fun deleteDir(dir: File?) {
        if (dir == null || !dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteDir(file)
            file.delete()
        }
    }

    companion object {
        const val PREFS_NAME = "AppSettings"
        const val KEY_IS_SUPPORTER = "is_supporter"
        const val KEY_AWAITING_SUPPORTER_REWARD = "awaiting_supporter_reward"
        const val KEY_SUPPORTER_REWARD_LAUNCH_TIME = "supporter_reward_launch_time"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_NAVIGATION_BLUR = "navigation_blur"
        const val KEY_TAB_SWITCH_BLUR = "tab_switch_blur"
        const val KEY_VIBRATION_STRENGTH = "vibration_strength"
        const val KEY_CROSSFADE_ENABLED = "crossfade_enabled"
        const val KEY_CROSSFADE_DURATION = "crossfade_duration"
        const val KEY_CROSSFADE_MODE = "crossfade_mode"
        const val KEY_AUTOMIX_ENABLED = "automix_enabled"
        const val KEY_AUTOMIX_PERFORMANCE_MODE = "automix_performance_mode"
        const val KEY_AUDIO_FOCUS = "audio_focus"
        const val KEY_LIBRARY_PATHS = "library_paths"
        const val KEY_HIDDEN_FOLDERS = "hidden_folders"
        const val KEY_AMOLED_BLACK = "amoled_black"
        const val KEY_IGNORE_SHORT_AUDIO = "ignore_short_audio"
        const val KEY_IGNORE_SHORT_AUDIO_DURATION = "ignore_short_audio_duration"
        const val KEY_DATA_SAVER = "data_saver"
        const val KEY_AUDIO_QUALITY = "audio_quality"
        const val KEY_SYNC_FREQUENCY = "sync_frequency"
        const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        const val KEY_HAPTIC_PLAY_PAUSE = "haptic_play_pause"
        const val KEY_HAPTIC_QUEUE = "haptic_queue"
        const val KEY_HAPTIC_FAVORITE = "haptic_favorite"
        const val KEY_HAPTIC_PAUSE_BACKGROUND = "haptic_pause_background"
        const val KEY_HAPTIC_AUDIO_MODE = "haptic_audio_mode"
        const val KEY_HAPTIC_PUNCH_BOOST = "haptic_punch_boost"
        const val KEY_HAPTIC_SLIDER_TICKS = "haptic_slider_ticks"
        const val KEY_HAPTIC_BATTERY_SAVER = "haptic_battery_saver"
        const val KEY_DYNAMIC_ALBUM_THEME = "dynamic_album_theme"
        const val KEY_VOLUME_NORMALIZATION_ENABLED = "volume_normalization_enabled"
        const val KEY_TARGET_LUFS = "target_lufs"
        const val KEY_ALL_IN_ONE_LIQUID_GLASS = "all_in_one_liquid_glass"
        const val KEY_PLAYER_THEME = "player_theme"
        const val KEY_MINI_PLAYER_THEME = "mini_player_theme"
        const val KEY_NAV_BAR_BLUR_OPTION = "nav_bar_blur_option"
        const val KEY_NAV_STYLE = "nav_style"
        const val KEY_WIFI_ONLY_DOWNLOAD = "wifi_only_download"
        const val KEY_DOWNLOAD_AUDIO_QUALITY = "download_audio_quality"
        const val KEY_DOWNLOAD_AUDIO_FORMAT = "download_audio_format"
        const val KEY_AUTO_DOWNLOAD_FAVORITES = "auto_download_favorites"
        const val KEY_OFFLINE_LYRICS = "offline_lyrics"
        const val KEY_OFFLINE_ONLY_MODE = "offline_only_mode"
        const val KEY_DISCONNECT_ACTION = "disconnect_action"
        const val KEY_RESUME_ON_CONNECT = "resume_on_connect"
        const val KEY_FORCE_OPUS = "force_opus"
        const val KEY_SEEKBAR_STYLE = "seekbar_style"
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SETTINGS FRAGMENT
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — ENTRY POINT
// ═══════════════════════════════════════════════════════════════════════════════

private fun getPathFromUri(uri: Uri): String {
    if (android.provider.DocumentsContract.isTreeUri(uri)) {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val split = docId.split(":")
        if (split.size >= 2) {
            val type = split[0]
            val relativePath = split[1]
            return if ("primary" == type.lowercase()) {
                Environment.getExternalStorageDirectory().toString() + "/" + relativePath
            } else {
                "/storage/$type/$relativePath"
            }
        }
    }
    return uri.path ?: uri.toString()
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@RequiresApi(Build.VERSION_CODES.Q)
fun NavGraphBuilder.settingsGraph(navController: androidx.navigation.NavController) {
    val enterAnim: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        aospSharedAxisEnter()
    }

    val exitAnim: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        aospSharedAxisExit()
    }

    val popEnterAnim: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.EnterTransition = {
        aospSharedAxisPopEnter()
    }

    val popExitAnim: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> androidx.compose.animation.ExitTransition = {
        aospSharedAxisPopExit()
    }

    composableWithBlur(SettingsRoute.Main.route) {
        SettingsMainScreen(navController = navController)
    }

    composableWithBlur(
        route = SettingsRoute.MusicManagement.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        val libraryPaths by viewModel.libraryPaths.collectAsStateWithLifecycle()
        val songCacheSize by viewModel.songCacheSize.collectAsStateWithLifecycle()
        val imageCacheSize by viewModel.imageCacheSize.collectAsStateWithLifecycle()
        val songCacheMaxSize by viewModel.songCacheMaxSize.collectAsStateWithLifecycle()
        val imageCacheMaxSize by viewModel.imageCacheMaxSize.collectAsStateWithLifecycle()
        val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()
        val ignoreShortAudio by viewModel.ignoreShortAudio.collectAsStateWithLifecycle()
        val ignoreShortAudioDuration by viewModel.ignoreShortAudioDuration.collectAsStateWithLifecycle()
        val hiddenFolders by viewModel.hiddenFolders.collectAsStateWithLifecycle()
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(350)
            viewModel.calculateCacheSize()
        }

        val directoryPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.addLibraryPath(it.toString())
            }
        }

        val downloadFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val path = getPathFromUri(it)
                viewModel.setDownloadFolder(path)
            }
        }

        val hiddenFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    val path = getPathFromUri(it)
                    viewModel.addHiddenFolder(path)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsScreenContent", "Failed to resolve hidden folder path", e)
                }
            }
        }

        val isScanning by viewModel.isLibraryScanning.collectAsStateWithLifecycle()
        val cacheBreakdown by viewModel.cacheBreakdown.collectAsStateWithLifecycle()
        val playerSharedViewModel: PlayerSharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        MusicManagementScreen(
            navController = navController,
            libraryPaths = libraryPaths,
            onRemovePath = { viewModel.removeLibraryPath(it) },
            onAddPathClick = { directoryPickerLauncher.launch(null) },
            cacheBreakdown = cacheBreakdown,
            onClearCategory = { category ->
                viewModel.clearCategoryCache(category) {
                    (context as? MainActivity)?.showSnackbar("${category.displayName} cleared", 0)
                }
            },
            onClearAllCaches = {
                viewModel.clearAllCaches {
                    (context as? MainActivity)?.showSnackbar("All cache cleared", 0)
                }
            },
            onRefreshCache = {
                viewModel.calculateCacheSize()
            },
            songCacheSize = songCacheSize,
            imageCacheSize = imageCacheSize,
            onClearSongCache = {
                viewModel.clearSongCache {
                    (context as? MainActivity)?.showSnackbar("Song cache cleared", 0)
                }
            },
            onClearImageCache = {
                viewModel.clearImageCache {
                    (context as? MainActivity)?.showSnackbar("Image cache cleared", 0)
                }
            },
            songCacheMaxSize = songCacheMaxSize,
            onSongCacheMaxSizeChange = { viewModel.setSongCacheMaxSize(it) },
            imageCacheMaxSize = imageCacheMaxSize,
            onImageCacheMaxSizeChange = { viewModel.setImageCacheMaxSize(it) },
            downloadFolder = downloadFolder,
            onDownloadFolderClick = { downloadFolderPickerLauncher.launch(null) },
            ignoreShortAudio = ignoreShortAudio,
            onIgnoreShortAudioChange = { viewModel.setIgnoreShortAudio(it) },
            ignoreShortAudioDuration = ignoreShortAudioDuration,
            onIgnoreShortAudioDurationChange = { viewModel.setIgnoreShortAudioDuration(it) },
            hiddenFolders = hiddenFolders,
            onAddHiddenFolderClick = { hiddenFolderPickerLauncher.launch(null) },
            onRemoveHiddenFolder = { viewModel.removeHiddenFolder(it) },
            isScanning = isScanning,
            onRescanClick = {
                viewModel.performFullScan(playerSharedViewModel) {
                    (context as? MainActivity)?.showSnackbar("Library rescan complete", 0)
                }
            },
            onRebuildDatabaseClick = {
                viewModel.rebuildDatabase(playerSharedViewModel) {
                    (context as? MainActivity)?.showSnackbar("Database rebuilt successfully", 0)
                }
            }
        )
    }

    composableWithBlur(
        route = SettingsRoute.BackupRestore.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        BackupRestoreScreen(
            navController = navController,
            viewModel = viewModel
        )
    }

    composableWithBlur(
        route = SettingsRoute.Account.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val ytCookies by viewModel.ytCookies.collectAsStateWithLifecycle()
        val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
        val pauseHistory by viewModel.pauseHistory.collectAsStateWithLifecycle()
        val accountViewModel: com.codetrio.spatialflow.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val spotifyViewModel: com.codetrio.spatialflow.viewmodel.SpotifyViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val lastFmViewModel: com.codetrio.spatialflow.viewmodel.LastFmViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        AccountScreen(
            navController = navController,
            ytCookies = ytCookies,
            onYtCookiesChange = { viewModel.setYtCookies(it) },
            dataSaver = dataSaver,
            onDataSaverChange = { viewModel.setDataSaver(it) },
            pauseHistory = pauseHistory,
            onPauseHistoryChange = { viewModel.setPauseHistory(it) },
            accountViewModel = accountViewModel,
            spotifyViewModel = spotifyViewModel,
            lastFmViewModel = lastFmViewModel
        )
    }

    composableWithBlur(
        route = SettingsRoute.LastFm.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val lastFmViewModel: com.codetrio.spatialflow.viewmodel.LastFmViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        com.codetrio.spatialflow.ui.settings.LastFmSettingsScreen(
            navController = navController,
            viewModel = lastFmViewModel
        )
    }

    composableWithBlur(
        route = SettingsRoute.LastFmSignIn.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val lastFmViewModel: com.codetrio.spatialflow.viewmodel.LastFmViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        com.codetrio.spatialflow.ui.lastfm.LastFmSignInScreen(
            lastFmViewModel = lastFmViewModel,
            onSignInSuccess = { navController.navigateUp() },
            onNavigateUp = { navController.navigateUp() }
        )
    }

    composableWithBlur(
        route = SettingsRoute.Appearance.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
        val amoledBlack by viewModel.amoledBlack.collectAsStateWithLifecycle()
        val showAnimatedArt by viewModel.showAnimatedArt.collectAsStateWithLifecycle()
        val canvasProviderOrder by viewModel.canvasProviderOrder.collectAsStateWithLifecycle()
        val dynamicAlbumTheme by viewModel.dynamicAlbumTheme.collectAsStateWithLifecycle()
        val hideNavLabels by viewModel.hideNavLabels.collectAsStateWithLifecycle()
        val slideNavOnScroll by viewModel.slideNavOnScroll.collectAsStateWithLifecycle()
        val dynamicNavStyle by viewModel.dynamicNavStyle.collectAsStateWithLifecycle()
        val navigationBlur by viewModel.navigationBlur.collectAsStateWithLifecycle()
        val tabSwitchBlur by viewModel.tabSwitchBlur.collectAsStateWithLifecycle()
        val playerTheme by viewModel.playerTheme.collectAsStateWithLifecycle()
        val miniPlayerTheme by viewModel.miniPlayerTheme.collectAsStateWithLifecycle()
        val navBarBlurOption by viewModel.navBarBlurOption.collectAsStateWithLifecycle()
        val navStyle by viewModel.navStyle.collectAsStateWithLifecycle()
        val allInOneLiquidGlass by viewModel.allInOneLiquidGlass.collectAsStateWithLifecycle()
        val forceHighRefreshRate by viewModel.forceHighRefreshRate.collectAsStateWithLifecycle()
        val seekBarStyle by viewModel.seekBarStyle.collectAsStateWithLifecycle()

        AppearanceScreen(
            navController = navController,
            darkMode = darkMode,
            onDarkModeChange = { viewModel.setDarkMode(it) },
            amoledBlack = amoledBlack,
            onAmoledBlackChange = { viewModel.setAmoledBlack(it) },
            showAnimatedArt = showAnimatedArt,
            onShowAnimatedArtChange = { viewModel.setShowAnimatedArt(it) },
            canvasProviderOrder = canvasProviderOrder,
            onCanvasProviderOrderChange = { viewModel.setCanvasProviderOrder(it) },
            dynamicAlbumTheme = dynamicAlbumTheme,
            onDynamicAlbumThemeChange = { viewModel.setDynamicAlbumTheme(it) },
            hideNavLabels = hideNavLabels,
            onHideNavLabelsChange = { viewModel.setHideNavLabels(it) },
            slideNavOnScroll = slideNavOnScroll,
            onSlideNavOnScrollChange = { viewModel.setSlideNavOnScroll(it) },
            dynamicNavStyle = dynamicNavStyle,
            onDynamicNavStyleChange = { viewModel.setDynamicNavStyle(it) },
            navigationBlur = navigationBlur,
            onNavigationBlurChange = { viewModel.setNavigationBlur(it) },
            tabSwitchBlur = tabSwitchBlur,
            onTabSwitchBlurChange = { viewModel.setTabSwitchBlur(it) },
            playerTheme = playerTheme,
            onPlayerThemeChange = { viewModel.setPlayerTheme(it) },
            miniPlayerTheme = miniPlayerTheme,
            onMiniPlayerThemeChange = { viewModel.setMiniPlayerTheme(it) },
            allInOneLiquidGlass = allInOneLiquidGlass,
            onAllInOneLiquidGlassChange = { viewModel.setAllInOneLiquidGlass(it) },
            onSolidStateSelect = { viewModel.setSolidState() },
            navBarBlurOption = navBarBlurOption,
            onNavBarBlurOptionChange = { viewModel.setNavBarBlurOption(it) },
            navStyle = navStyle,
            onNavStyleChange = { viewModel.setNavStyle(it) },
            seekBarStyle = seekBarStyle,
            onSeekBarStyleChange = { viewModel.setSeekBarStyle(it) },
            forceHighRefreshRate = forceHighRefreshRate,
            onForceHighRefreshRateChange = { viewModel.setForceHighRefreshRate(it) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.Playback.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val playerSharedViewModel: PlayerSharedViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
        val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
        val crossfadeMode by viewModel.crossfadeMode.collectAsStateWithLifecycle()
        val autoMixEnabled by viewModel.autoMixEnabled.collectAsStateWithLifecycle()
        val autoMixPerformanceMode by viewModel.autoMixPerformanceMode.collectAsStateWithLifecycle()
        val audioFocus by viewModel.audioFocus.collectAsStateWithLifecycle()
        val autoplayEnabled by viewModel.autoplayEnabled.collectAsStateWithLifecycle()
        val sleepTimerEndTime by playerSharedViewModel.sleepTimerEndTime.collectAsStateWithLifecycle()
        val sleepTimerMode by playerSharedViewModel.sleepTimerMode.collectAsStateWithLifecycle()
        val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
        val forceOpus by viewModel.forceOpus.collectAsStateWithLifecycle()
        val losslessStreamPool by viewModel.losslessStreamPool.collectAsStateWithLifecycle()
        val losslessPriorityOrder by viewModel.losslessPriorityOrder.collectAsStateWithLifecycle()
        val disabledLosslessProviders by viewModel.disabledLosslessProviders.collectAsStateWithLifecycle()
        val dacDirectMode by viewModel.dacDirectMode.collectAsStateWithLifecycle()
        val dacBitPerfect by viewModel.dacBitPerfect.collectAsStateWithLifecycle()
        val nativeSampleRate by viewModel.nativeSampleRate.collectAsStateWithLifecycle()
        val dacFixedVolume by viewModel.dacFixedVolume.collectAsStateWithLifecycle()
        val dacBannerEnabled by viewModel.dacBannerEnabled.collectAsStateWithLifecycle()
        val volumeNormalizationEnabled by viewModel.volumeNormalizationEnabled.collectAsStateWithLifecycle()
        val targetLufs by viewModel.targetLufs.collectAsStateWithLifecycle()
        val disconnectAction by viewModel.disconnectAction.collectAsStateWithLifecycle()
        val resumeOnConnect by viewModel.resumeOnConnect.collectAsStateWithLifecycle()
        PlaybackScreen(
            navController = navController,
            crossfadeEnabled = crossfadeEnabled,
            onCrossfadeToggle = { viewModel.setCrossfadeEnabled(it) },
            crossfadeDuration = crossfadeDuration,
            onCrossfadeDurationChange = { viewModel.setCrossfadeDuration(it) },
            crossfadeMode = crossfadeMode,
            onCrossfadeModeChange = { viewModel.setCrossfadeMode(it) },
            autoMixEnabled = autoMixEnabled,
            onAutoMixToggle = { viewModel.setAutoMixEnabled(it) },
            autoMixPerformanceMode = autoMixPerformanceMode,
            onAutoMixPerformanceModeChange = { viewModel.setAutoMixPerformanceMode(it) },
            audioFocus = audioFocus,
            onAudioFocusToggle = { viewModel.setAudioFocus(it) },
            disconnectAction = disconnectAction,
            onDisconnectActionChange = { viewModel.setDisconnectAction(it) },
            resumeOnConnect = resumeOnConnect,
            onResumeOnConnectChange = { viewModel.setResumeOnConnect(it) },
            autoplayEnabled = autoplayEnabled,
            onAutoplayToggle = { viewModel.setAutoplayEnabled(it) },
            sleepTimerEndTime = sleepTimerEndTime,
            sleepTimerMode = sleepTimerMode,
            onStartSleepTimer = { playerSharedViewModel.startCustomSleepTimer(it) },
            onCancelSleepTimer = { playerSharedViewModel.cancelSleepTimer() },
            onSetEndOfSong = { enable ->
                if (enable) playerSharedViewModel.setSleepTimerMode(PlayerSharedViewModel.SleepTimerMode.END_OF_SONG)
                else playerSharedViewModel.cancelSleepTimer()
            },
            audioQuality = audioQuality,
            onAudioQualityChange = { viewModel.setAudioQuality(it) },
            forceOpus = forceOpus,
            onForceOpusChange = { viewModel.setForceOpus(it) },
            losslessStreamPool = losslessStreamPool,
            onLosslessStreamPoolChange = { viewModel.setLosslessStreamPool(it) },
            losslessPriorityOrder = losslessPriorityOrder,
            disabledLosslessProviders = disabledLosslessProviders,
            onLosslessPriorityOrderChange = { viewModel.setLosslessPriorityOrder(it) },
            onToggleLosslessProvider = { provider, enabled -> viewModel.toggleLosslessProvider(provider, enabled) },
            onResetLosslessPriorityOrder = { viewModel.resetLosslessPriorityOrder() },
            dacDirectMode = dacDirectMode,
            onDacDirectModeChange = { viewModel.setDacDirectMode(it) },
            dacBitPerfect = dacBitPerfect,
            onDacBitPerfectChange = { viewModel.setDacBitPerfect(it) },
            nativeSampleRate = nativeSampleRate,
            onNativeSampleRateChange = { viewModel.setNativeSampleRate(it) },
            dacFixedVolume = dacFixedVolume,
            onDacFixedVolumeChange = { viewModel.setDacFixedVolume(it) },
            dacBannerEnabled = dacBannerEnabled,
            onDacBannerChange = { viewModel.setDacBannerEnabled(it) },
            volumeNormalizationEnabled = volumeNormalizationEnabled,
            onVolumeNormalizationChange = { viewModel.setVolumeNormalizationEnabled(it) },
            targetLufs = targetLufs,
            onTargetLufsChange = { viewModel.setTargetLufs(it) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.Haptics.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)
        val hapticsEnabled by viewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val vibrationStrength by viewModel.vibrationStrength.collectAsStateWithLifecycle()
        val hapticAudioMode by viewModel.hapticAudioMode.collectAsStateWithLifecycle()
        val hapticPunchBoost by viewModel.hapticPunchBoost.collectAsStateWithLifecycle()
        val hapticPlayPause by viewModel.hapticPlayPause.collectAsStateWithLifecycle()
        val hapticSliderTicks by viewModel.hapticSliderTicks.collectAsStateWithLifecycle()
        val hapticQueue by viewModel.hapticQueue.collectAsStateWithLifecycle()
        val hapticFavorite by viewModel.hapticFavorite.collectAsStateWithLifecycle()
        val hapticPauseInBackground by viewModel.hapticPauseInBackground.collectAsStateWithLifecycle()
        val hapticBatterySaver by viewModel.hapticBatterySaver.collectAsStateWithLifecycle()

        HapticsScreen(
            navController = navController,
            hasHaptics = viewModel.hasHaptics,
            hapticsEnabled = hapticsEnabled,
            onHapticsEnabledChange = { viewModel.setHapticsEnabled(it) },
            vibrationStrength = vibrationStrength,
            onVibrationStrengthChange = { viewModel.setVibrationStrength(it) },
            hapticAudioMode = hapticAudioMode,
            onHapticAudioModeChange = { viewModel.setHapticAudioMode(it) },
            hapticPunchBoost = hapticPunchBoost,
            onHapticPunchBoostChange = { viewModel.setHapticPunchBoost(it) },
            hapticPlayPause = hapticPlayPause,
            onHapticPlayPauseChange = { viewModel.setHapticPlayPause(it) },
            hapticSliderTicks = hapticSliderTicks,
            onHapticSliderTicksChange = { viewModel.setHapticSliderTicks(it) },
            hapticQueue = hapticQueue,
            onHapticQueueChange = { viewModel.setHapticQueue(it) },
            hapticFavorite = hapticFavorite,
            onHapticFavoriteChange = { viewModel.setHapticFavorite(it) },
            hapticPauseInBackground = hapticPauseInBackground,
            onHapticPauseInBackgroundChange = { viewModel.setHapticPauseInBackground(it) },
            hapticBatterySaver = hapticBatterySaver,
            onHapticBatterySaverChange = { viewModel.setHapticBatterySaver(it) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.About.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        AboutScreen(
            navController = navController,
            onCheckUpdate = {
                (context as? Activity)?.let { activity ->
                    (context as? MainActivity)?.updateManager?.checkForUpdate(
                        activity.findViewById(android.R.id.content),
                        BuildConfig.VERSION_NAME
                    )
                }
            },
            onWhatsNew = { navController.navigate(SettingsRoute.WhatsNew.route) },
            onOpenUrl = { url ->
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            }
        )
    }

    composableWithBlur(
        route = SettingsRoute.Feedback.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        FeedbackScreen(navController = navController)
    }

    composableWithBlur(
        route = SettingsRoute.DeveloperLogger.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        com.codetrio.spatialflow.ui.settings.DeveloperLoggerScreen(navController = navController)
    }

    composableWithBlur(
        route = SettingsRoute.WhatsNew.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        WhatsNewScreen(navController = navController)
    }

    composableWithBlur(
        route = SettingsRoute.Downloads.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(activity)

        val wifiOnlyDownload by viewModel.wifiOnlyDownload.collectAsStateWithLifecycle()
        val downloadAudioQuality by viewModel.downloadAudioQuality.collectAsStateWithLifecycle()
        val downloadAudioFormat by viewModel.downloadAudioFormat.collectAsStateWithLifecycle()
        val autoDownloadFavorites by viewModel.autoDownloadFavorites.collectAsStateWithLifecycle()
        val offlineLyrics by viewModel.offlineLyrics.collectAsStateWithLifecycle()
        val offlineOnlyMode by viewModel.offlineOnlyMode.collectAsStateWithLifecycle()
        val downloadFolder by viewModel.downloadFolder.collectAsStateWithLifecycle()

        val downloadFolderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            uri?.let {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val path = getPathFromUri(it)
                viewModel.setDownloadFolder(path)
            }
        }

        DownloadsScreen(
            navController = navController,
            wifiOnlyDownload = wifiOnlyDownload,
            onWifiOnlyDownloadChange = { viewModel.setWifiOnlyDownload(it) },
            downloadAudioQuality = downloadAudioQuality,
            onDownloadAudioQualityChange = { viewModel.setDownloadAudioQuality(it) },
            downloadAudioFormat = downloadAudioFormat,
            onDownloadAudioFormatChange = { viewModel.setDownloadAudioFormat(it) },
            autoDownloadFavorites = autoDownloadFavorites,
            onAutoDownloadFavoritesChange = { viewModel.setAutoDownloadFavorites(it) },
            offlineLyrics = offlineLyrics,
            onOfflineLyricsChange = { viewModel.setOfflineLyrics(it) },
            offlineOnlyMode = offlineOnlyMode,
            onOfflineOnlyModeChange = { viewModel.setOfflineOnlyMode(it) },
            downloadFolder = downloadFolder,
            onDownloadFolderClick = { downloadFolderPickerLauncher.launch(null) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.SourcePool.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        val context = LocalContext.current
        val activity = context.findActivity() as androidx.activity.ComponentActivity
        val viewModel: com.codetrio.spatialflow.ui.settings.sourcepool.SourcePoolViewModel =
            androidx.lifecycle.viewmodel.compose.viewModel(activity)

        com.codetrio.spatialflow.ui.settings.sourcepool.SourcePoolStatusScreen(
            navController = navController,
            viewModel = viewModel
        )
    }

    composableWithBlur(
        route = SettingsRoute.DeezerSettings.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        com.codetrio.spatialflow.ui.settings.deezer.DeezerSettingsScreen(
            navController = navController,
            onNavigateToSignIn = { navController.navigate(SettingsRoute.DeezerSignIn.route) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.DeezerSignIn.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        com.codetrio.spatialflow.ui.settings.deezer.DeezerSignInScreen(
            onSignInSuccess = { navController.navigateUp() },
            onNavigateUp = { navController.navigateUp() }
        )
    }

    composableWithBlur(
        route = SettingsRoute.TidalSettings.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        com.codetrio.spatialflow.ui.settings.tidal.TidalSettingsScreen(
            navController = navController,
            onNavigateToSignIn = { navController.navigate(SettingsRoute.TidalSignIn.route) }
        )
    }

    composableWithBlur(
        route = SettingsRoute.TidalSignIn.route,
        enterTransition = enterAnim,
        exitTransition = exitAnim,
        popEnterTransition = popEnterAnim,
        popExitTransition = popExitAnim
    ) {
        com.codetrio.spatialflow.ui.settings.tidal.TidalSignInScreen(
            onSignInSuccess = { navController.navigateUp() },
            onNavigateUp = { navController.navigateUp() }
        )
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — MAIN SETTINGS BODY
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDetailTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

sealed class SettingsRoute(val route: String) {
    object Main : SettingsRoute("settings")
    object MusicManagement : SettingsRoute("music_management")
    object Downloads : SettingsRoute("downloads")
    object Account : SettingsRoute("account")
    object Appearance : SettingsRoute("appearance")
    object Playback : SettingsRoute("playback")
    object Haptics : SettingsRoute("haptics")
    object About : SettingsRoute("about")
    object Feedback : SettingsRoute("feedback")
    object DeveloperLogger : SettingsRoute("developer_logger")
    object WhatsNew : SettingsRoute("whats_new")
    object BackupRestore : SettingsRoute("backup_restore")
    object LastFm : SettingsRoute("lastfm_settings")
    object LastFmSignIn : SettingsRoute("lastfm_signin")
    object SourcePool : SettingsRoute("source_pool_status")
    object DeezerSettings : SettingsRoute("deezer_settings")
    object DeezerSignIn : SettingsRoute("deezer_signin")
    object TidalSettings : SettingsRoute("tidal_settings")
    object TidalSignIn : SettingsRoute("tidal_signin")
}

@Composable
private fun SettingsMainScreen(navController: androidx.navigation.NavController) {
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset, available: Offset, source: NestedScrollSource
            ): Offset {
                if (!isLandscape && mainActivity != null) {
                    val delta = consumed.y
                    if (delta < -30f) mainActivity.hideBottomNavWithAnimation()
                    else if (delta > 30f) mainActivity.showBottomNavWithAnimation()
                }
                return super.onPostScroll(consumed, available, source)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
            .padding(bottom = 120.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp)
        )

        val prefs = remember(context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val isSupporter by prefs.observeKey(KEY_IS_SUPPORTER, false)
        var showSupportBottomSheet by remember { mutableStateOf(false) }

        SupporterBannerCard(
            isSupporter = isSupporter,
            onOpenSupportSheet = {
                showSupportBottomSheet = true
            }
        )

        if (showSupportBottomSheet) {
            com.codetrio.spatialflow.ui.settings.SupportBottomSheet(
                isSupporter = isSupporter,
                onDismissRequest = { showSupportBottomSheet = false },
                onOpenSupportSite = {
                    try {
                        prefs.edit()
                            .putBoolean(KEY_AWAITING_SUPPORTER_REWARD, true)
                            .putLong(KEY_SUPPORTER_REWARD_LAUNCH_TIME, System.currentTimeMillis())
                            .apply()
                        val intent = Intent(Intent.ACTION_VIEW, "https://supportspatialflow.vercel.app/".toUri())
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(context, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
        val onSecondaryContainer = MaterialTheme.colorScheme.onSecondaryContainer
        val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
        val onTertiaryContainer = MaterialTheme.colorScheme.onTertiaryContainer

        val primaryPastel = primaryContainer.copy(alpha = 0.12f)
        val secondaryPastel = secondaryContainer.copy(alpha = 0.12f)
        val tertiaryPastel = tertiaryContainer.copy(alpha = 0.12f)

        SettingsGroupCard(
            containerColors = listOf(
                primaryPastel,
                secondaryPastel,
                tertiaryPastel,
                primaryPastel,
                secondaryPastel,
                tertiaryPastel,
                primaryPastel,
                secondaryPastel,
                tertiaryPastel,
                primaryPastel
            ),
            items = listOf(
                {
                    SettingsCategoryItem(
                        title = "Account & Sync",
                        subtitle = "YouTube Music login, history sync",
                        icon = Icons.Rounded.AccountCircle,
                        iconContainerColor = primaryContainer,
                        iconContentColor = onPrimaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Account.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Playback",
                        subtitle = "Audio behavior, crossfade, audio focus",
                        icon = Icons.Rounded.PlayCircle,
                        iconContainerColor = secondaryContainer,
                        iconContentColor = onSecondaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Playback.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Music Management",
                        subtitle = "Manage folders, refresh library, storage",
                        icon = Icons.Rounded.LibraryMusic,
                        iconContainerColor = tertiaryContainer,
                        iconContentColor = onTertiaryContainer,
                        onClick = { navController.navigate(SettingsRoute.MusicManagement.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Downloads",
                        subtitle = "Download manager, storage, offline mode & audio format",
                        icon = Icons.Rounded.Download,
                        iconContainerColor = primaryContainer,
                        iconContentColor = onPrimaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Downloads.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Appearance",
                        subtitle = "Themes, layout, visual styles",
                        icon = Icons.Rounded.Palette,
                        iconContainerColor = secondaryContainer,
                        iconContentColor = onSecondaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Appearance.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Haptics",
                        subtitle = "Vibration strength, haptic feedback",
                        icon = Icons.Rounded.Vibration,
                        iconContainerColor = tertiaryContainer,
                        iconContentColor = onTertiaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Haptics.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Backup & Restore",
                        subtitle = "Export and import your library and settings",
                        icon = Icons.Rounded.SettingsBackupRestore,
                        iconContainerColor = primaryContainer,
                        iconContentColor = onPrimaryContainer,
                        onClick = { navController.navigate(SettingsRoute.BackupRestore.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "Feedback & Bug Reports",
                        subtitle = "Report issues, request features, export logs",
                        icon = Icons.Rounded.BugReport,
                        iconContainerColor = secondaryContainer,
                        iconContentColor = onSecondaryContainer,
                        onClick = { navController.navigate(SettingsRoute.Feedback.route) }
                    )
                },
                {
                    SettingsCategoryItem(
                        title = "About",
                        subtitle = "App version, credits, updates",
                        icon = Icons.Rounded.Info,
                        iconContainerColor = tertiaryContainer,
                        iconContentColor = onTertiaryContainer,
                        onClick = { navController.navigate(SettingsRoute.About.route) }
                    )
                }
            )
        )
        
        AppLogoSection(isLandscape = isLandscape)
    }
}

@Composable
private fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headlineColor: Color = MaterialTheme.colorScheme.onSurface,
    supportingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconContainerColor: Color = MaterialTheme.colorScheme.primary,
    iconContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = headlineColor)
        },
        supportingContent = {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = supportingColor)
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon, 
                    contentDescription = null, 
                    tint = iconContentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onClick() }
    )
}

@Composable
private fun SupporterBannerCard(
    isSupporter: Boolean,
    onOpenSupportSheet: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    if (isSupporter) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SpatialShapes.Large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenSupportSheet()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = SpatialShapes.Medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = "Supporter",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SpatialFlow Supporter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Surface(
                                shape = SpatialShapes.Pill,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Thank you for supporting SpatialFlow! Your supporter badge is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FilledTonalButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSupportSheet()
                    },
                    shape = SpatialShapes.MediumLarge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Support Again",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = SpatialShapes.Large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenSupportSheet()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = SpatialShapes.Medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = "Support",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Support SpatialFlow",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Star on GitHub, watch an offer, or direct tip via UPI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSupportSheet()
                    },
                    shape = SpatialShapes.MediumLarge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Support SpatialFlow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}







@Composable
private fun NavigationBlurRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Blur Effects",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Apply blur transitions to screen navigation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Opacity,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun TabSwitchBlurRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Tab Switch Blur",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Blur on Explore/Library/Effects/Settings tab swaps (GPU-heavy)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Opacity,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DynamicNavStyleRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Dynamic Navbar Style",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Compact height with bold, elevated icons",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}







// ═══════════════════════════════════════════════════════════════════════════════
// COMPOSE UI — COMPONENTS
// ═══════════════════════════════════════════════════════════════════════════════

// ── Theme ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeRow(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!isDark) },
        content = {
            Text(
                text = stringResource(R.string.setting_dark_mode),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = { ThemeSwitch(isDark, onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ThemeSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                },
                label = "theme_icon"
            ) { dark ->
                Icon(
                    imageVector = if (!dark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}



// ── Haptics ─────────────────────────────────────────────────────────────────



// ── Wavy Divider ────────────────────────────────────────────────────────────

// ── Crossfade ───────────────────────────────────────────────────────────────



// ── Library ─────────────────────────────────────────────────────────────────

@Composable
private fun LibrarySourceHeader() {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.setting_music_source),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_folder_open),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}





// SocialButton is replaced by ButtonGroup clickableItem

// ── App Logo ────────────────────────────────────────────────────────────────

@Composable
private fun AppLogoSection(isLandscape: Boolean) {
    val iconSize by animateDpAsState(
        targetValue = if (isLandscape) 160.dp else 250.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "logo_size"
    )
    val fontSize by animateFloatAsState(
        targetValue = if (isLandscape) 32f else 44f,
        animationSpec = SmoothSpring,
        label = "logo_text_size"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isLandscape) 8.dp else 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_applogo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            fontSize = fontSize.sp,
            letterSpacing = 0.sp
        )
        Text(
            text = "© 2026 Shubham Karande",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}


// ── Feedback & Bug Reports ──────────────────────────────────────────────────

@Composable
private fun FeedbackScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val debugInfo = remember {
        """
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        Android: ${Build.VERSION.RELEASE}
        SDK: ${Build.VERSION.SDK_INT}
        App Version: ${BuildConfig.VERSION_NAME}
        """.trimIndent()
    }

    Scaffold(
        topBar = { SettingsDetailTopBar("Feedback & Bug Reports") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            SettingsHeader("Report & Request")
            SettingsGroupCard(
                items = listOf(
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Report a Bug",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Open GitHub with pre-filled device information",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Bug] ", "UTF-8")
                                val body = URLEncoder.encode(
                                    "## Description\n\n\n## Device Information\n$debugInfo\n\n## Steps to Reproduce\n1.\n2.\n3.\n",
                                    "UTF-8"
                                )
                                val url =
                                    "https://github.com/MythicalSHUB/SpatialFlow/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Report Bug via Telegram",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Share pre-filled device and app details directly to Telegram",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_telegram),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val telegramMsg =
                                    "SPATIALFLOW BUG REPORT\n\n$debugInfo\n\nDescribe the bug/issue below:\n"
                                val url = "https://t.me/share/url?url=&text=${
                                    URLEncoder.encode(
                                        telegramMsg,
                                        "UTF-8"
                                    )
                                }"
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(url)
                                        )
                                    )
                                } catch (e: Exception) {
                                    val clipboardManager =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Bug Report", telegramMsg)
                                    clipboardManager.setPrimaryClip(clip)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Report template copied. Paste in Telegram.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://t.me/SpatialFlow")
                                        )
                                    )
                                }
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Request Feature",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Suggest a new feature for SpatialFlow",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.OpenInNew,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val title = URLEncoder.encode("[Feature] ", "UTF-8")
                                val body = URLEncoder.encode(
                                    "## Feature Description\n\n\n## Why is this needed?\n\n",
                                    "UTF-8"
                                )
                                val url =
                                    "https://github.com/MythicalSHUB/SpatialFlow/issues/new?title=$title&body=$body"
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Join Telegram Community",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Chat with the community and developer",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_telegram),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://t.me/SpatialFlow")
                                        )
                                    )
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Could not open Telegram link",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                )
            )

            SettingsHeader("Debug Information")
            SettingsGroupCard(
                items = listOf(
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Developer Logger",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Live on-device logcat viewer with Android Studio style filters",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                navController.navigate(SettingsRoute.DeveloperLogger.route)
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Copy Debug Information",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Copy device & app version to clipboard",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.ContentCopy,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val clipboardManager =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Debug Info", debugInfo)
                                clipboardManager.setPrimaryClip(clip)
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Export Logs",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Share raw debug logs to attach to an issue",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.Description,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, debugInfo)
                                    type = "text/plain"
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        sendIntent,
                                        "Export Logs"
                                    )
                                )
                            }
                        )
                    },
                    {
                        ListItem(
                            headlineContent = {
                                Text(
                                    "Simulate Crash",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            supportingContent = {
                                Text(
                                    "Force a runtime exception to test crash report dialog",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.Rounded.BugReport,
                                    null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                throw RuntimeException("Simulated App Crash: The user triggered a test exception.")
                            }
                        )
                    }
                )
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DOWNLOADS SCREEN (MATERIAL 3 EXPRESSIVE)
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsScreen(
    navController: androidx.navigation.NavController,
    wifiOnlyDownload: Boolean,
    onWifiOnlyDownloadChange: (Boolean) -> Unit,
    downloadAudioQuality: String,
    onDownloadAudioQualityChange: (String) -> Unit,
    downloadAudioFormat: String,
    onDownloadAudioFormatChange: (String) -> Unit,
    autoDownloadFavorites: Boolean,
    onAutoDownloadFavoritesChange: (Boolean) -> Unit,
    offlineLyrics: Boolean,
    onOfflineLyricsChange: (Boolean) -> Unit,
    offlineOnlyMode: Boolean,
    onOfflineOnlyModeChange: (Boolean) -> Unit,
    downloadFolder: String?,
    onDownloadFolderClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) }
    var downloadedSongs by remember { mutableStateOf(emptyList<com.codetrio.spatialflow.model.SongItem>()) }
    var storageBytes by remember { mutableStateOf(0L) }
    var songToDeleteConfirm by remember { mutableStateOf<com.codetrio.spatialflow.model.SongItem?>(null) }
    val downloadProgressMap by com.codetrio.spatialflow.util.SongDownloader.downloadProgress.collectAsStateWithLifecycle()

    fun refreshData() {
        scope.launch(Dispatchers.IO) {
            val songs = com.codetrio.spatialflow.util.SongDownloader.getAllDownloadedSongs(context)
            val bytes = com.codetrio.spatialflow.util.SongDownloader.getDownloadedStorageBytes(context)
            withContext(Dispatchers.Main) {
                downloadedSongs = songs
                storageBytes = bytes
            }
        }
    }

    fun formatStorage(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceAtMost(units.size - 1)
        return DecimalFormat("#.##").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (songToDeleteConfirm != null) {
        val s = songToDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { songToDeleteConfirm = null },
            title = { Text("Delete Track?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${s.title}' by ${s.artist} from offline storage?") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = songToDeleteConfirm
                        songToDeleteConfirm = null
                        if (target != null) {
                            com.codetrio.spatialflow.util.SongDownloader.deleteDownloadedSong(context, target)
                            refreshData()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = { SettingsDetailTopBar("Downloads") { navController.popBackStack() } }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val haptic = LocalHapticFeedback.current

            ButtonGroup(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                expandedRatio = 0.25f,
                overflowIndicator = {}
            ) {
                val scope = this
                val tabs = listOf(
                    Triple(0, Icons.Rounded.Download, "Manager & Format"),
                    Triple(1, Icons.Rounded.MusicNote, "Tracks (${downloadedSongs.size})")
                )
                tabs.forEach { (tabIdx, iconVector, label) ->
                    val checked = selectedTab == tabIdx
                    customItem(
                        buttonGroupContent = {
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val cornerRadius by animateDpAsState(
                                targetValue = if (isPressed) 6.dp else if (checked) 20.dp else 10.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "DownloadsTabCorner"
                            )

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTab = tabIdx
                                    refreshData()
                                },
                                modifier = with(scope) {
                                    Modifier
                                        .animateWidth(interactionSource)
                                        .weight(1f)
                                        .height(48.dp)
                                },
                                interactionSource = interactionSource,
                                shape = RoundedCornerShape(cornerRadius),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        },
                        menuContent = {}
                    )
                }
            }

            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 120.dp)
                ) {
                    SettingsHeader("Storage & Offline Status")
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text(
                                        text = "${downloadedSongs.size} Tracks Saved",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${formatStorage(storageBytes)} used by SpatialFlow downloads",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (downloadProgressMap.isNotEmpty()) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.DownloadDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            if (downloadProgressMap.isNotEmpty()) {
                                Text(
                                    text = "Active Downloading (${downloadProgressMap.size} files)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearWavyProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                val progressFloat = (storageBytes.toFloat() / (1024 * 1024 * 1024 * 5f)).coerceIn(0.02f, 1f)
                                LinearWavyProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Button(
                                onClick = { selectedTab = 1 },
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("View All Tracks")
                            }
                        }
                    }

                    SettingsHeader("Offline & Network Behavior")
                    SettingsGroupCard(
                        items = listOf(
                            {
                                ListItem(
                                    headlineContent = { Text("Offline Only Mode") },
                                    supportingContent = { Text("Hide remote streams and prioritize local downloads") },
                                    leadingContent = {
                                        ToggleButton(
                                            checked = offlineOnlyMode,
                                            onCheckedChange = onOfflineOnlyModeChange,
                                            shapes = ToggleButtonDefaults.shapes(shape = if (offlineOnlyMode) RoundedCornerShape(16.dp) else CircleShape),
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(Icons.Rounded.DownloadForOffline, contentDescription = null)
                                        }
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = offlineOnlyMode,
                                            onCheckedChange = onOfflineOnlyModeChange
                                        )
                                    }
                                )
                            },
                            {
                                ListItem(
                                    headlineContent = { Text("Download over Wi-Fi Only") },
                                    supportingContent = { Text("Prevent downloading songs on cellular mobile data") },
                                    leadingContent = {
                                        ToggleButton(
                                            checked = wifiOnlyDownload,
                                            onCheckedChange = onWifiOnlyDownloadChange,
                                            shapes = ToggleButtonDefaults.shapes(shape = if (wifiOnlyDownload) RoundedCornerShape(16.dp) else CircleShape),
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(if (wifiOnlyDownload) Icons.Rounded.Wifi else Icons.Rounded.SignalWifiOff, contentDescription = null)
                                        }
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = wifiOnlyDownload,
                                            onCheckedChange = onWifiOnlyDownloadChange
                                        )
                                    }
                                )
                            },
                            {
                                ListItem(
                                    headlineContent = { Text("Auto-Download Favorites") },
                                    supportingContent = { Text("Automatically download tracks when added to Favorites") },
                                    leadingContent = {
                                        ToggleButton(
                                            checked = autoDownloadFavorites,
                                            onCheckedChange = onAutoDownloadFavoritesChange,
                                            shapes = ToggleButtonDefaults.shapes(shape = if (autoDownloadFavorites) RoundedCornerShape(16.dp) else CircleShape),
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null)
                                        }
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = autoDownloadFavorites,
                                            onCheckedChange = onAutoDownloadFavoritesChange
                                        )
                                    }
                                )
                            },
                            {
                                ListItem(
                                    headlineContent = { Text("Offline Synced Lyrics") },
                                    supportingContent = { Text("Fetch and save LRC lyrics files alongside tracks") },
                                    leadingContent = {
                                        ToggleButton(
                                            checked = offlineLyrics,
                                            onCheckedChange = onOfflineLyricsChange,
                                            shapes = ToggleButtonDefaults.shapes(shape = if (offlineLyrics) RoundedCornerShape(16.dp) else CircleShape),
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(Icons.Rounded.Lyrics, contentDescription = null)
                                        }
                                    },
                                    trailingContent = {
                                        Switch(
                                            checked = offlineLyrics,
                                            onCheckedChange = onOfflineLyricsChange
                                        )
                                    }
                                )
                            }
                        )
                    )

                    SettingsHeader("Format & Quality")
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Audio Container Format",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Opus provides higher efficiency; MP3 offers maximum legacy compatibility",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Opus", "MP3", "FLAC").forEach { fmt ->
                                    val selected = downloadAudioFormat.equals(fmt, ignoreCase = true)
                                    FilledTonalButton(
                                        onClick = { onDownloadAudioFormatChange(fmt) },
                                        colors = if (selected)
                                            ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                                        else ButtonDefaults.filledTonalButtonColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text(fmt, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(Modifier.height(20.dp))

                            Text(
                                text = "Audio Bitrate Quality",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "High (256/320kbps), Medium (160kbps), Data Saver (96kbps)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("High", "Medium", "Low").forEach { q ->
                                    val selected = downloadAudioQuality.equals(q, ignoreCase = true)
                                    FilledTonalButton(
                                        onClick = { onDownloadAudioQualityChange(q) },
                                        colors = if (selected)
                                            ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                                        else ButtonDefaults.filledTonalButtonColors(),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Text(
                                            text = if (q == "Low") "Data Saver" else q,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsHeader("Storage Destination")
                    SettingsGroupCard(
                        items = listOf(
                            {
                                ListItem(
                                    headlineContent = { Text("Download Directory") },
                                    supportingContent = { Text(downloadFolder ?: "Default: Downloads / SpatialFlow / Songs") },
                                    leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { onDownloadFolderClick() }
                                )
                            }
                        )
                    )
                }

            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    SettingsHeader("Downloaded Offline Songs (${downloadedSongs.size})")

                    if (downloadedSongs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Rounded.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "No downloaded songs yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Downloaded tracks will appear here for offline playback",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            contentPadding = PaddingValues(bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(
                                items = downloadedSongs,
                                key = { idx, song -> "dl_song_${idx}_${song.id}" }
                            ) { _, song ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ListItem(
                                        headlineContent = { Text(song.title, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                        supportingContent = { Text(song.artist, maxLines = 1) },
                                        leadingContent = {
                                            Surface(
                                                shape = RoundedCornerShape(16.dp.concentricInner(8.dp)),
                                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                SpatialImage(
                                                    model = song,
                                                    contentDescription = null,
                                                    size = ImageSemanticSize.ThumbnailSmall,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                    error = painterResource(id = R.drawable.default_album_art)
                                                )
                                            }
                                        },
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    songToDeleteConfirm = song
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}