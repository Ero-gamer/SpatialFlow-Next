package com.codetrio.spatialflow.data.sourcepool

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages leasing and in-memory caching of authenticated accounts from Source Pool.
 * Automatically handles decryption of leased credentials (Tidal, Qobuz, Deezer).
 */
object SourcePoolAccountManager {

    private const val TAG = "SourcePoolAccounts"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    @Volatile private var lastRefreshTime = 0L
    @Volatile private var cachedTidalAccounts: List<TidalAccount> = emptyList()
    @Volatile private var cachedQobuzTokens: List<QobuzToken> = emptyList()
    @Volatile private var cachedDeezerArls: List<String> = emptyList()

    data class TidalAccount(
        val token: String,
        val refreshToken: String?,
        val countryCode: String?,
        val premium: Boolean
    )

    suspend fun refreshIfNeeded(context: Context? = null, force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshTime < CACHE_TTL_MS &&
            (cachedTidalAccounts.isNotEmpty() || cachedQobuzTokens.isNotEmpty() || cachedDeezerArls.isNotEmpty())) {
            return@withContext
        }

        try {
            val apiKey = (context?.let { SourcePoolSecurity.getApiKey(it) })
                ?: com.codetrio.spatialflow.BuildConfig.SOURCE_POOL_KEY.takeIf { it.isNotBlank() }
            val feedResult = SourcePoolClient.fetchSourcesFeed(context, apiKey)
            val feed = feedResult.getOrNull()
            if (feed == null) {
                Log.w(TAG, "SourcePool feed returned null: ${feedResult.exceptionOrNull()?.message}")
                return@withContext
            }

            val derivedKey = apiKey?.let { PoolCrypto.deriveClientKey(it) }

            // 1. Tidal Accounts
            val tidalList = mutableListOf<TidalAccount>()
            feed.tidal?.accounts?.forEach { acc ->
                val rawToken = acc.token ?: return@forEach
                val decToken = PoolCrypto.maybeDecryptWith(rawToken, derivedKey) ?: return@forEach
                if (decToken.isNotBlank() && !decToken.startsWith("enc:1:")) {
                    val decCountry = PoolCrypto.maybeDecryptWith(acc.countryCode, derivedKey) ?: "US"
                    val decRefresh = PoolCrypto.maybeDecryptWith(acc.refreshToken, derivedKey)
                    tidalList.add(TidalAccount(decToken, decRefresh, decCountry, acc.premium))
                }
            }
            cachedTidalAccounts = tidalList

            // 2. Qobuz Accounts
            val qobuzList = mutableListOf<QobuzToken>()
            feed.qobuz?.accounts?.forEach { acc ->
                val rawToken = acc.token ?: return@forEach
                val rawAppId = acc.appId ?: return@forEach
                val rawSecret = acc.appSecret ?: return@forEach

                val decToken = PoolCrypto.maybeDecryptWith(rawToken, derivedKey) ?: return@forEach
                val decAppId = PoolCrypto.maybeDecryptWith(rawAppId, derivedKey) ?: return@forEach
                val decSecret = PoolCrypto.maybeDecryptWith(rawSecret, derivedKey) ?: return@forEach

                if (decToken.isNotBlank() && !decToken.startsWith("enc:1:") &&
                    decAppId.isNotBlank() && !decAppId.startsWith("enc:1:") &&
                    decSecret.isNotBlank() && !decSecret.startsWith("enc:1:")) {
                    val label = acc.note?.takeIf { it.isNotBlank() } ?: "Source Pool"
                    qobuzList.add(
                        QobuzToken(
                            token = decToken,
                            appId = decAppId,
                            appSecret = decSecret,
                            label = label,
                            subscription = if (acc.premium) "premium" else "",
                            poolId = acc.id
                        )
                    )
                }
            }
            // Sort Qobuz tokens so full Studio streaming accounts (e.g. poolId=54, qbdlx-shared) are evaluated first
            qobuzList.sortByDescending { token ->
                var score = 0
                if (token.poolId == 54L) score += 100
                if (token.label.contains("qbdlx", ignoreCase = true)) score += 50
                if (token.subscription.equals("premium", ignoreCase = true)) score += 20
                score
            }
            cachedQobuzTokens = qobuzList

            // 3. Deezer Accounts
            val deezerList = mutableListOf<String>()
            feed.deezer?.accounts?.forEach { acc ->
                val rawArl = acc.arl ?: return@forEach
                val decArl = PoolCrypto.maybeDecryptWith(rawArl, derivedKey) ?: return@forEach
                if (decArl.isNotBlank() && !decArl.startsWith("enc:1:")) {
                    deezerList.add(decArl)
                }
            }
            cachedDeezerArls = deezerList

            lastRefreshTime = now
            Log.d(TAG, "Refreshed pool accounts: Tidal=${tidalList.size}, Qobuz=${qobuzList.size}, Deezer=${deezerList.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh source pool accounts: ${e.message}")
        }
    }

    fun getTidalAccounts(): List<TidalAccount> = cachedTidalAccounts
    fun getQobuzTokens(): List<QobuzToken> = cachedQobuzTokens
    fun getDeezerArls(): List<String> = cachedDeezerArls
}
