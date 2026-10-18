package com.codetrio.spatialflow.data.sourcepool

import com.google.gson.annotations.SerializedName

/**
 * Public Source Pool aggregate health response from GET /api/status.
 */
data class SourcePoolStatusResponse(
    @SerializedName("generatedAt") val generatedAt: String? = null,
    @SerializedName("categories") val categories: List<SourceCategoryStatus> = emptyList()
)

/**
 * Health statistics for a specific service and kind (e.g. Qobuz Account, Tidal API).
 */
data class SourceCategoryStatus(
    @SerializedName("service") val service: String = "",
    @SerializedName("kind") val kind: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("total") val total: Int = 0,
    @SerializedName("alive") val alive: Int = 0,
    @SerializedName("premium") val premium: Int = 0,
    @SerializedName("dead") val dead: Int = 0,
    @SerializedName("pending") val pending: Int = 0,
    @SerializedName("uptimePct") val uptimePct: Double? = null,
    @SerializedName("lastCheckedAt") val lastCheckedAt: String? = null,
    @SerializedName("health") val health: String = "unknown"
) {
    val isOperational: Boolean
        get() = health.equals("operational", ignoreCase = true)

    val isDown: Boolean
        get() = health.equals("down", ignoreCase = true)
}

/**
 * Key-gated live leased sources response from GET /api/sources.
 */
data class SourcesFeedResponse(
    @SerializedName("version") val version: Int = 1,
    @SerializedName("generatedAt") val generatedAt: String? = null,
    @SerializedName("encrypted") val encrypted: Boolean = false,
    @SerializedName("encryption") val encryption: String? = null,
    @SerializedName("accountsFeed") val accountsFeed: String? = null,
    @SerializedName("instancesFeed") val instancesFeed: String? = null,
    @SerializedName("tidal") val tidal: ServiceSources? = null,
    @SerializedName("qobuz") val qobuz: ServiceSources? = null,
    @SerializedName("deezer") val deezer: ServiceSources? = null,
    @SerializedName("apple-music") val appleMusic: ServiceSources? = null
)

data class ServiceSources(
    @SerializedName("apis") val apis: List<SourceApiEntry> = emptyList(),
    @SerializedName("accounts") val accounts: List<SourceAccountEntry> = emptyList()
)

data class SourceApiEntry(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("premium") val premium: Boolean = false,
    @SerializedName("status") val status: String = "unknown",
    @SerializedName("latencyMs") val latencyMs: Long? = null,
    @SerializedName("lastCheckedAt") val lastCheckedAt: String? = null,
    @SerializedName("baseUrl") val baseUrl: String? = null,
    @SerializedName("healthPath") val healthPath: String? = null
)

data class SourceAccountEntry(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("premium") val premium: Boolean = false,
    @SerializedName("status") val status: String = "unknown",
    @SerializedName("latencyMs") val latencyMs: Long? = null,
    @SerializedName("lastCheckedAt") val lastCheckedAt: String? = null,
    @SerializedName("token") val token: String? = null,
    @SerializedName("appId") val appId: String? = null,
    @SerializedName("appSecret") val appSecret: String? = null,
    @SerializedName("refreshToken") val refreshToken: String? = null,
    @SerializedName("countryCode") val countryCode: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("arl") val arl: String? = null,
    @SerializedName("note") val note: String? = null
)

/**
 * Public discovery endpoint response from GET /api/discovery/{service}.
 */
data class DiscoveryResponse(
    @SerializedName("streaming") val streaming: List<String> = emptyList(),
    @SerializedName("api") val api: List<String> = emptyList()
)

/**
 * Aggregated health verdict and KPI metrics across all community pools.
 */
data class SourcePoolSummary(
    val totalEntries: Int,
    val totalAlive: Int,
    val totalPremium: Int,
    val totalDead: Int,
    val totalPending: Int,
    val averageUptimePct: Double?,
    val overallHealth: HealthVerdict,
    val lastGeneratedAt: String?
)

enum class HealthVerdict {
    OPERATIONAL,
    DEGRADED,
    DOWN,
    UNKNOWN
}
