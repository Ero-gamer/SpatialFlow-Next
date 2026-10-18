package com.codetrio.spatialflow.data.lyrics.security

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages Cloudflare Turnstile token resolution and JWT authentication for
 * Unified Lyrics Streaming API (https://lyrics.api.dacubeking.com/).
 *
 * Provides:
 * - Off-screen headless WebView solver for Turnstile non-interactive challenge
 * - Automatic background token renewal
 * - SharedPreferences caching with expiry detection
 * - Manual token input / sync for user convenience
 */
object BetterLyricsTokenManager {
    private const val TAG = "BetterLyricsTokenMgr"
    private const val PREFS_NAME = "BetterLyricsSecurity"
    private const val KEY_JWT = "unified_jwt_token"
    private const val KEY_EXPIRES_AT = "unified_jwt_expires_at"

    private const val TURNSTILE_CHALLENGE_URL = "https://lyrics.api.dacubeking.com/challenge"
    private const val VERIFY_TURNSTILE_URL = "https://lyrics.api.dacubeking.com/verify-turnstile"

    private const val RENEWAL_BUFFER_SECONDS = 300L // 5 minutes buffer
    private const val SOLVER_TIMEOUT_MS = 25_000L

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cachedJwt: String? = null

    @Volatile
    private var cachedExpiresAt: Long = 0L

    private val isSolving = AtomicBoolean(false)

    data class TokenStatus(
        val hasToken: Boolean,
        val isValid: Boolean,
        val expiresInSeconds: Long,
        val tokenPreview: String
    )

    /**
     * Synchronously returns the currently cached or stored JWT if it is valid.
     */
    fun getToken(context: Context): String? {
        ensureLoaded(context)
        val nowSec = System.currentTimeMillis() / 1000
        if (cachedJwt != null && (cachedExpiresAt - nowSec) > RENEWAL_BUFFER_SECONDS) {
            return cachedJwt
        }
        return null
    }

    /**
     * Asynchronously returns a valid JWT, solving the Turnstile challenge if missing or expired.
     */
    suspend fun getOrRefreshToken(context: Context, forceRefresh: Boolean = false): String? {
        ensureLoaded(context)
        val nowSec = System.currentTimeMillis() / 1000

        if (!forceRefresh && cachedJwt != null && (cachedExpiresAt - nowSec) > RENEWAL_BUFFER_SECONDS) {
            return cachedJwt
        }

        // If another resolution is in flight, wait briefly or trigger new solve
        return withContext(Dispatchers.IO) {
            solveTurnstile(context)
        }
    }

    /**
     * Allows user to manually paste their JWT token.
     */
    fun setCustomJwt(context: Context, token: String): Boolean {
        val trimmed = token.trim()
        val exp = extractJwtExpiry(trimmed)
        if (exp != null) {
            saveToken(context, trimmed, exp)
            return true
        }
        return false
    }

    /**
     * Clears stored JWT token.
     */
    fun clearToken(context: Context) {
        cachedJwt = null
        cachedExpiresAt = 0L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun getTokenStatus(context: Context): TokenStatus {
        ensureLoaded(context)
        val token = cachedJwt
        if (token.isNullOrEmpty()) {
            return TokenStatus(hasToken = false, isValid = false, expiresInSeconds = 0L, tokenPreview = "None")
        }
        val nowSec = System.currentTimeMillis() / 1000
        val remaining = cachedExpiresAt - nowSec
        val isValid = remaining > 0L
        val preview = if (token.length > 20) "${token.take(10)}...${token.takeLast(6)}" else token
        return TokenStatus(
            hasToken = true,
            isValid = isValid,
            expiresInSeconds = remaining.coerceAtLeast(0L),
            tokenPreview = preview
        )
    }

    private fun ensureLoaded(context: Context) {
        if (cachedJwt == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_JWT, null)
            val exp = prefs.getLong(KEY_EXPIRES_AT, 0L)
            if (!stored.isNullOrEmpty() && exp > 0L) {
                cachedJwt = stored
                cachedExpiresAt = exp
            }
        }
    }

    private fun saveToken(context: Context, token: String, expiresAtSec: Long) {
        cachedJwt = token
        cachedExpiresAt = expiresAtSec
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_JWT, token)
            .putLong(KEY_EXPIRES_AT, expiresAtSec)
            .apply()
        Log.i(TAG, "New unified JWT stored, expires in ${expiresAtSec - System.currentTimeMillis() / 1000}s")
    }

    /**
     * Solves Cloudflare Turnstile challenge using a headless off-screen Android WebView.
     */
    private suspend fun solveTurnstile(context: Context): String? {
        if (!isSolving.compareAndSet(false, true)) {
            Log.d(TAG, "Turnstile solve already in progress, awaiting resolution...")
            // Wait up to 10 seconds for concurrent solve
            for (i in 0..20) {
                kotlinx.coroutines.delay(500)
                val token = getToken(context)
                if (token != null) return token
                if (!isSolving.get()) break
            }
            return getToken(context)
        }

        val turnstileDeferred = CompletableDeferred<String?>()

        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    val webView = WebView(context.applicationContext)
                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                    class TurnstileBridge {
                        @JavascriptInterface
                        fun onToken(token: String) {
                            Log.d(TAG, "Turnstile token intercepted: ${token.take(15)}...")
                            if (!turnstileDeferred.isCompleted) {
                                turnstileDeferred.complete(token)
                            }
                            destroyWebView(webView)
                        }

                        @JavascriptInterface
                        fun onError(error: String) {
                            Log.w(TAG, "Turnstile error: $error")
                            if (!turnstileDeferred.isCompleted) {
                                turnstileDeferred.complete(null)
                            }
                            destroyWebView(webView)
                        }
                    }

                    webView.addJavascriptInterface(TurnstileBridge(), "AndroidTurnstile")

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val js = """
                                (function() {
                                    window.addEventListener('message', function(e) {
                                        if (e.data && e.data.type === 'turnstile-token' && e.data.token) {
                                            AndroidTurnstile.onToken(e.data.token);
                                        } else if (e.data && e.data.type === 'turnstile-error') {
                                            AndroidTurnstile.onError(e.data.error || 'unknown');
                                        }
                                    });
                                    if (typeof onTurnstileSuccess === 'function') {
                                        var origSuccess = onTurnstileSuccess;
                                        window.onTurnstileSuccess = function(t) {
                                            AndroidTurnstile.onToken(t);
                                            origSuccess(t);
                                        };
                                    }
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(js, null)
                        }
                    }

                    webView.loadUrl(TURNSTILE_CHALLENGE_URL)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize WebView for Turnstile", e)
                    turnstileDeferred.complete(null)
                }
            }

            val turnstileToken = withTimeoutOrNull(SOLVER_TIMEOUT_MS) {
                turnstileDeferred.await()
            }

            if (turnstileToken.isNullOrEmpty()) {
                Log.w(TAG, "Turnstile challenge timed out or failed to yield token")
                return null
            }

            // Exchange turnstileToken for JWT via verify-turnstile
            val jwt = exchangeTurnstileTokenForJwt(turnstileToken)
            if (jwt != null) {
                val exp = extractJwtExpiry(jwt) ?: (System.currentTimeMillis() / 1000 + 86400)
                saveToken(context, jwt, exp)
                return jwt
            }
            return null
        } finally {
            isSolving.set(false)
        }
    }

    private fun destroyWebView(webView: WebView) {
        Handler(Looper.getMainLooper()).post {
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            } catch (_: Exception) {}
        }
    }

    private fun exchangeTurnstileTokenForJwt(turnstileToken: String): String? {
        return try {
            val jsonPayload = JsonObject().apply {
                addProperty("token", turnstileToken)
            }
            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(VERIFY_TURNSTILE_URL)
                .post(body)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "verify-turnstile failed with HTTP ${response.code}")
                    return null
                }
                val respStr = response.body?.string() ?: return null
                val json = JsonParser.parseString(respStr).asJsonObject
                json.get("jwt")?.asString
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging Turnstile token", e)
            null
        }
    }

    private fun extractJwtExpiry(jwt: String): Long? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payloadB64 = parts[1]
                .replace('-', '+')
                .replace('_', '/')
            val padded = payloadB64.padEnd(payloadB64.length + ((4 - (payloadB64.length % 4)) % 4), '=')
            val decoded = String(Base64.decode(padded, Base64.DEFAULT), StandardCharsets.UTF_8)
            val json = JsonParser.parseString(decoded).asJsonObject
            json.get("exp")?.asLong
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode JWT payload: ${e.message}")
            null
        }
    }
}
