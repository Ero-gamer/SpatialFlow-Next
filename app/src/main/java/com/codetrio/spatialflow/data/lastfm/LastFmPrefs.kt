package com.codetrio.spatialflow.data.lastfm

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * SharedPreferences persistence manager for all Last.fm integration data and settings.
 * Uses the primary "AppSettings" preferences file for consistency across SpatialFlow.
 */
object LastFmPrefs {

    private const val PREFS_NAME = "AppSettings"

    private const val KEY_SESSION_KEY = "lastfm_session_key"
    private const val KEY_USERNAME = "lastfm_username"
    private const val KEY_AVATAR_URL = "lastfm_avatar_url"
    private const val KEY_SCROBBLE_COUNT = "lastfm_scrobble_count"
    private const val KEY_SCROBBLING_ENABLED = "lastfm_scrobbling_enabled"
    private const val KEY_NOW_PLAYING_ENABLED = "lastfm_now_playing_enabled"
    private const val KEY_THRESHOLD_PCT = "lastfm_threshold_percent"
    private const val KEY_WIFI_ONLY = "lastfm_wifi_only"
    private const val KEY_SYNC_LOVED = "lastfm_sync_loved_tracks"
    private const val KEY_CUSTOM_API_KEY = "lastfm_custom_api_key"
    private const val KEY_CUSTOM_API_SECRET = "lastfm_custom_api_secret"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Session & Account ───────────────────────────────────────────────────

    fun getSessionKey(context: Context): String =
        prefs(context).getString(KEY_SESSION_KEY, "").orEmpty()

    fun saveSessionKey(context: Context, sessionKey: String) =
        prefs(context).edit { putString(KEY_SESSION_KEY, sessionKey) }

    fun getUsername(context: Context): String =
        prefs(context).getString(KEY_USERNAME, "").orEmpty()

    fun saveUsername(context: Context, username: String) =
        prefs(context).edit { putString(KEY_USERNAME, username) }

    fun getAvatarUrl(context: Context): String? =
        prefs(context).getString(KEY_AVATAR_URL, null)?.takeIf { it.isNotBlank() }

    fun saveAvatarUrl(context: Context, url: String?) =
        prefs(context).edit { putString(KEY_AVATAR_URL, url) }

    fun getScrobbleCount(context: Context): Long =
        prefs(context).getLong(KEY_SCROBBLE_COUNT, 0L)

    fun saveScrobbleCount(context: Context, count: Long) =
        prefs(context).edit { putLong(KEY_SCROBBLE_COUNT, count) }

    fun isLoggedIn(context: Context): Boolean =
        getSessionKey(context).isNotBlank() && getUsername(context).isNotBlank()

    // ── Toggles & Scrobbler Behavior ────────────────────────────────────────

    fun isScrobblingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCROBBLING_ENABLED, true)

    fun setScrobblingEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_SCROBBLING_ENABLED, enabled) }

    fun isNowPlayingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOW_PLAYING_ENABLED, true)

    fun setNowPlayingEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_NOW_PLAYING_ENABLED, enabled) }

    fun getScrobbleThresholdPercent(context: Context): Int =
        prefs(context).getInt(KEY_THRESHOLD_PCT, 50).coerceIn(50, 100)

    fun setScrobbleThresholdPercent(context: Context, percent: Int) =
        prefs(context).edit { putInt(KEY_THRESHOLD_PCT, percent.coerceIn(50, 100)) }

    fun isWifiOnly(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WIFI_ONLY, false)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) =
        prefs(context).edit { putBoolean(KEY_WIFI_ONLY, wifiOnly) }

    fun isSyncLovedTracks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_LOVED, true)

    fun setSyncLovedTracks(context: Context, syncLoved: Boolean) =
        prefs(context).edit { putBoolean(KEY_SYNC_LOVED, syncLoved) }

    // ── Custom API Credentials (Optional) ───────────────────────────────────

    fun getCustomApiKey(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_API_KEY, "").orEmpty()

    fun saveCustomApiKey(context: Context, key: String) =
        prefs(context).edit { putString(KEY_CUSTOM_API_KEY, key.trim()) }

    fun getCustomApiSecret(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_API_SECRET, "").orEmpty()

    fun saveCustomApiSecret(context: Context, secret: String) =
        prefs(context).edit { putString(KEY_CUSTOM_API_SECRET, secret.trim()) }

    // ── Logout ──────────────────────────────────────────────────────────────

    fun clearSession(context: Context) = prefs(context).edit {
        remove(KEY_SESSION_KEY)
        remove(KEY_USERNAME)
        remove(KEY_AVATAR_URL)
        remove(KEY_SCROBBLE_COUNT)
    }
}
