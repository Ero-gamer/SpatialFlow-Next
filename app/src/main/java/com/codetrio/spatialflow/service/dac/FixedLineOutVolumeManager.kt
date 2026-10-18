package com.codetrio.spatialflow.service.dac

import android.content.SharedPreferences
import android.media.AudioManager
import android.util.Log

/**
 * Manages safe persistence and recovery of system media volume when Fixed Line-Out (0 dBFS)
 * mode is engaged for external DACs.
 */
class FixedLineOutVolumeManager(
    private val prefs: SharedPreferences,
    private val setStreamVolume: (stream: Int, index: Int, flags: Int) -> Unit,
    private val getStreamVolume: (stream: Int) -> Int,
    private val getStreamMaxVolume: (stream: Int) -> Int
) {
    companion object {
        const val PREF_KEY_PRE_LINE_OUT_VOLUME = "pre_line_out_volume"
        private const val TAG = "FixedLineOutVolume"
    }

    /**
     * Applies fixed line-out volume: locks digital stream to 1.0f and maximizes system media volume.
     * Saves previous system volume to SharedPreferences if not already stored.
     */
    fun applyFixedVolume(onPlayerMaxVolume: () -> Unit = {}) {
        onPlayerMaxVolume()
        val maxVol = getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = getStreamVolume(AudioManager.STREAM_MUSIC)
        val savedVol = prefs.getInt(PREF_KEY_PRE_LINE_OUT_VOLUME, -1)
        if (savedVol == -1 && currentVol < maxVol) {
            prefs.edit().putInt(PREF_KEY_PRE_LINE_OUT_VOLUME, currentVol).apply()
        }
        try {
            setStreamVolume(AudioManager.STREAM_MUSIC, maxVol, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set fixed line-out stream volume to max", e)
        }
    }

    /**
     * Restores saved system volume if present in SharedPreferences and clears the persisted key to -1.
     * Returns true if volume was restored, false otherwise.
     */
    fun restoreSavedVolume(): Boolean {
        val savedVol = prefs.getInt(PREF_KEY_PRE_LINE_OUT_VOLUME, -1)
        if (savedVol != -1) {
            try {
                setStreamVolume(AudioManager.STREAM_MUSIC, savedVol, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore pre-line-out volume: ${e.message}", e)
            }
            prefs.edit().putInt(PREF_KEY_PRE_LINE_OUT_VOLUME, -1).apply()
            return true
        }
        return false
    }

    /**
     * Called at service startup: if stored key is not -1 and DAC is not connected, restores saved volume.
     */
    fun restoreAtStartup(isDacConnected: Boolean): Boolean {
        val savedVol = prefs.getInt(PREF_KEY_PRE_LINE_OUT_VOLUME, -1)
        if (savedVol != -1 && !isDacConnected) {
            return restoreSavedVolume()
        }
        return false
    }
}
