package com.codetrio.spatialflow.data.lastfm

import android.content.Context
import com.codetrio.spatialflow.BuildConfig
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Handles Last.fm API 2.0 authentication, MD5 signature generation,
 * and OAuth / Direct token exchanges.
 */
object LastFmAuth {

    private const val TAG = "LastFmAuth"
    const val API_ENDPOINT = "https://ws.audioscrobbler.com/2.0/"
    const val AUTH_CALLBACK_URL = "spatialflow://lastfm-auth"
    val USER_AGENT = "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)"

    // Default working API credentials for SpatialFlow
    // (Users can also provide their own Custom API Key in LastFmPrefs)
    private const val DEFAULT_API_KEY = "e8296080c4c5062169c19c4fc21ab69d"
    private const val DEFAULT_API_SECRET = "0480a303d513b7b5dc159d7c9fb5e8fa"

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    fun getApiKey(context: Context): String {
        val custom = LastFmPrefs.getCustomApiKey(context)
        return custom.ifBlank { DEFAULT_API_KEY }
    }

    fun getApiSecret(context: Context): String {
        val custom = LastFmPrefs.getCustomApiSecret(context)
        return custom.ifBlank { DEFAULT_API_SECRET }
    }

    /**
     * Builds the Last.fm web authorization URL for Chrome Custom Tabs / System Browser.
     */
    fun buildAuthUrl(context: Context): String {
        val apiKey = getApiKey(context)
        return "https://www.last.fm/api/auth/?api_key=$apiKey&cb=$AUTH_CALLBACK_URL"
    }

    /**
     * Calculates the Last.fm MD5 API Signature (api_sig).
     * Rule: Alphabetize all parameters (except format and api_sig), concatenate key+value,
     * append apiSecret, and calculate lowercase MD5 hex string.
     */
    fun generateApiSignature(params: Map<String, String>, apiSecret: String): String {
        val sorted = params.filterKeys { it != "format" && it != "api_sig" }
            .toSortedMap()
        val sigBuilder = StringBuilder()
        for ((key, value) in sorted) {
            sigBuilder.append(key).append(value)
        }
        sigBuilder.append(apiSecret)
        return md5(sigBuilder.toString())
    }

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val result = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            result[i * 2] = HEX_CHARS[v ushr 4]
            result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(result)
    }

    /**
     * Exchanges an authorized token from the Web OAuth callback for a permanent Session Key.
     */
    suspend fun getSession(context: Context, token: String): Result<LastFmSession> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey(context)
            val apiSecret = getApiSecret(context)

            val params = mapOf(
                "method" to "auth.getSession",
                "api_key" to apiKey,
                "token" to token
            )
            val apiSig = generateApiSignature(params, apiSecret)

            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getSession HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val sessionResp = json.decodeFromString<LastFmSessionResponse>(responseBody)
            val session = sessionResp.session
            if (session != null && session.key.isNotBlank()) {
                LastFmPrefs.saveSessionKey(context, session.key)
                LastFmPrefs.saveUsername(context, session.name)
                Result.success(session)
            } else {
                val errorMsg = sessionResp.message ?: "Failed to acquire session (Error ${sessionResp.error})"
                Log.e(TAG, "getSession API error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSession exception", e)
            Result.failure(e)
        }
    }

    /**
     * Authenticates directly using username and password via auth.getMobileSession.
     */
    suspend fun getMobileSession(
        context: Context,
        username: String,
        password: String
    ): Result<LastFmSession> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getApiKey(context)
            val apiSecret = getApiSecret(context)

            val params = mapOf(
                "method" to "auth.getMobileSession",
                "api_key" to apiKey,
                "username" to username.trim(),
                "password" to password
            )
            val apiSig = generateApiSignature(params, apiSecret)

            val formBuilder = FormBody.Builder()
            params.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.add("api_sig", apiSig)
            formBuilder.add("format", "json")

            val request = Request.Builder()
                .url(API_ENDPOINT)
                .post(formBuilder.build())
                .header("User-Agent", USER_AGENT)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body.string()

            if (!response.isSuccessful) {
                Log.e(TAG, "getMobileSession HTTP error: ${response.code} body: $responseBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}: $responseBody"))
            }

            val sessionResp = json.decodeFromString<LastFmSessionResponse>(responseBody)
            val session = sessionResp.session
            if (session != null && session.key.isNotBlank()) {
                LastFmPrefs.saveSessionKey(context, session.key)
                LastFmPrefs.saveUsername(context, session.name)
                Result.success(session)
            } else {
                val errorMsg = sessionResp.message ?: "Invalid credentials (Error ${sessionResp.error})"
                Log.e(TAG, "getMobileSession API error: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMobileSession exception", e)
            Result.failure(e)
        }
    }

    /**
     * Manually saves an existing session key and username.
     */
    fun saveManualSession(context: Context, username: String, sessionKey: String) {
        LastFmPrefs.saveSessionKey(context, sessionKey.trim())
        LastFmPrefs.saveUsername(context, username.trim())
    }
}
