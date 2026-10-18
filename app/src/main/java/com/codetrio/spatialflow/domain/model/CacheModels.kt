package com.codetrio.spatialflow.domain.model

enum class CacheCategory(val displayName: String) {
    SONGS("Song Audio"),
    IMAGES("Images & Artwork"),
    CANVAS("Canvas Visuals"),
    NETWORK_TEMP("Network & Temp Files")
}

data class CategoryCacheInfo(
    val category: CacheCategory,
    val bytesUsed: Long,
    val formattedSize: String,
    val maxSizeMb: Int, // 0 = unlimited
    val quotaBytes: Long
) {
    val usageRatio: Float
        get() = if (quotaBytes > 0L) {
            (bytesUsed.toFloat() / quotaBytes.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

data class CacheBreakdown(
    val categories: Map<CacheCategory, CategoryCacheInfo>,
    val totalBytes: Long,
    val formattedTotalSize: String,
    val isCalculating: Boolean = false
) {
    companion object {
        val Initial = CacheBreakdown(
            categories = emptyMap(),
            totalBytes = 0L,
            formattedTotalSize = "Calculating...",
            isCalculating = true
        )
    }
}
