package com.codetrio.spatialflow.data.sourcepool

import android.content.Context
import com.codetrio.spatialflow.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object SourcePoolClient {

    private val communityGatewayBase: String
        get() = BuildConfig.COMMUNITY_GATEWAY_URL.trim().trimEnd('/')

    private val gson = Gson()

    val isCommunityGatewayConfigured: Boolean
        get() = communityGatewayBase.isNotBlank()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun fetchStatus(context: Context? = null): Result<SourcePoolStatusResponse> = withContext(Dispatchers.IO) {
        if (!isCommunityGatewayConfigured) {
            return@withContext Result.failure(IllegalStateException("Cloudflare Worker Gateway is not configured on this build."))
        }

        try {
            val url = "$communityGatewayBase/api/status"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)")

            if (context != null) {
                SourcePoolSecurityHandshake.buildHandshakeHeaders(context).forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }
            }

            httpClient.newCall(requestBuilder.get().build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body.string()
                    if (bodyString.isNotBlank()) {
                        val parsed = gson.fromJson(bodyString, SourcePoolStatusResponse::class.java)
                        return@withContext Result.success(parsed)
                    }
                }
                Result.failure(IllegalStateException("Worker Gateway returned HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchSourcesFeed(context: Context? = null, apiKey: String? = null): Result<SourcesFeedResponse> = withContext(Dispatchers.IO) {
        if (!isCommunityGatewayConfigured) {
            return@withContext Result.failure(
                IllegalStateException("Cloudflare Worker Gateway is not configured on this build.")
            )
        }

        try {
            val targetUrl = "$communityGatewayBase/api/sources"
            val isCustomKey = !apiKey.isNullOrBlank()

            val requestBuilder = Request.Builder()
                .url(targetUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)")
                .header("X-Pool-Client", "v2")

            if (isCustomKey) {
                requestBuilder.header("Authorization", "Bearer ${apiKey.trim()}")
            } else if (context != null) {
                SourcePoolSecurityHandshake.buildHandshakeHeaders(context).forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }
            }

            httpClient.newCall(requestBuilder.get().build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Worker sources feed HTTP ${response.code}: ${response.message}")
                    )
                }
                val bodyString = response.body.string()
                val parsed = gson.fromJson(bodyString, SourcesFeedResponse::class.java)
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchDiscovery(context: Context? = null, service: String): Result<DiscoveryResponse> = withContext(Dispatchers.IO) {
        if (!isCommunityGatewayConfigured) {
            return@withContext Result.failure(IllegalStateException("Cloudflare Worker Gateway is not configured on this build."))
        }

        try {
            val url = "$communityGatewayBase/api/discovery/$service"
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "SpatialFlow/${BuildConfig.VERSION_NAME} (Android)")
                .header("X-Pool-Client", "v2")

            if (context != null) {
                SourcePoolSecurityHandshake.buildHandshakeHeaders(context).forEach { (k, v) ->
                    requestBuilder.header(k, v)
                }
            }

            httpClient.newCall(requestBuilder.get().build()).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body.string()
                    if (bodyString.isNotBlank()) {
                        val parsed = gson.fromJson(bodyString, DiscoveryResponse::class.java)
                        return@withContext Result.success(parsed)
                    }
                }
                Result.failure(IllegalStateException("Worker discovery for $service failed with HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun computeSummary(response: SourcePoolStatusResponse): SourcePoolSummary {
        val categories = response.categories
        val total = categories.sumOf { it.total }
        val alive = categories.sumOf { it.alive }
        val premium = categories.sumOf { it.premium }
        val dead = categories.sumOf { it.dead }
        val pending = categories.sumOf { it.pending }

        val uptimes = categories.mapNotNull { it.uptimePct }
        val avgUptime = if (uptimes.isNotEmpty()) uptimes.average() else null

        val hasDown = categories.any { it.isDown }
        val allOperational = categories.isNotEmpty() && categories.all { it.isOperational }

        val verdict = when {
            allOperational -> HealthVerdict.OPERATIONAL
            hasDown && alive > 0 -> HealthVerdict.DEGRADED
            alive == 0 && total > 0 -> HealthVerdict.DOWN
            else -> HealthVerdict.OPERATIONAL
        }

        return SourcePoolSummary(
            totalEntries = total,
            totalAlive = alive,
            totalPremium = premium,
            totalDead = dead,
            totalPending = pending,
            averageUptimePct = avgUptime,
            overallHealth = verdict,
            lastGeneratedAt = response.generatedAt
        )
    }
}
