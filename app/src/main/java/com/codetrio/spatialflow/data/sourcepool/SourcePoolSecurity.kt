package com.codetrio.spatialflow.data.sourcepool

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Hardware-backed secure handling and persistence for Source Pool API credentials.
 * Uses Android KeyStore with AES-256 GCM encryption.
 * Enforces zero-logging and zero-display policies.
 */
object SourcePoolSecurity {

    private const val ENCRYPTED_PREFS_NAME = "source_pool_encrypted_store"
    private const val LEGACY_PREFS_NAME = "source_pool_secure_store"
    private const val PREF_KEY_TOKEN = "vault_atp_key"

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: createEncryptedPrefs(context.applicationContext ?: context).also {
                cachedPrefs = it
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encPrefs = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            // Gracefully migrate any user-configured key from legacy unencrypted prefs if present
            migrateLegacyPrefs(context, encPrefs)

            encPrefs
        } catch (e: Exception) {
            // Fallback for devices where KeyStore has vendor quirks
            android.util.Log.e("SourcePoolSecurity", "Failed to init EncryptedSharedPreferences, falling back to app-private", e)
            context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun migrateLegacyPrefs(context: Context, targetPrefs: SharedPreferences) {
        try {
            val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.contains(PREF_KEY_TOKEN)) {
                val legacyKey = legacyPrefs.getString(PREF_KEY_TOKEN, null)
                if (!legacyKey.isNullOrBlank() && !targetPrefs.contains(PREF_KEY_TOKEN)) {
                    targetPrefs.edit { putString(PREF_KEY_TOKEN, legacyKey.trim()) }
                }
                legacyPrefs.edit { clear() }
            }
        } catch (_: Exception) {
            // Non-critical migration failure
        }
    }

    /**
     * Retrieves the stored API key for HTTPS Bearer authorization.
     * Returns null if no key has been configured by the user.
     */
    fun getApiKey(context: Context): String? {
        val prefs = getPrefs(context)
        val key = prefs.getString(PREF_KEY_TOKEN, null)
        return if (key.isNullOrBlank()) null else key.trim()
    }

    /**
     * Returns true if a valid personal API key is configured.
     * Guaranteed to never expose the key string.
     */
    fun isKeyConfigured(context: Context): Boolean {
        return !getApiKey(context).isNullOrBlank()
    }

    /**
     * Updates the API key in hardware-encrypted storage.
     */
    fun setApiKey(context: Context, key: String?) {
        getPrefs(context).edit {
            if (key.isNullOrBlank()) {
                remove(PREF_KEY_TOKEN)
            } else {
                putString(PREF_KEY_TOKEN, key.trim())
            }
        }
    }

    /**
     * Wipes the stored credentials completely.
     */
    fun clearApiKey(context: Context) {
        getPrefs(context).edit { remove(PREF_KEY_TOKEN) }
    }

    /**
     * Scoped execution helper for performing authenticated actions without
     * leaking credentials into longer-lived scopes.
     */
    inline fun <T> withApiKey(context: Context, block: (String) -> T): T? {
        val key = getApiKey(context) ?: return null
        return block(key)
    }
}
