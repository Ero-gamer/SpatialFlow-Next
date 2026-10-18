package com.codetrio.spatialflow.domain.repository

import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.EmptyResult
import com.codetrio.spatialflow.domain.model.CacheBreakdown
import com.codetrio.spatialflow.domain.model.CacheCategory
import kotlinx.coroutines.flow.StateFlow

interface LocalCacheDataSource {
    /**
     * Observable state flow of the current cache consumption and limits.
     */
    val cacheBreakdown: StateFlow<CacheBreakdown>

    /**
     * Recalculates cache sizes across all subsystems asynchronously.
     */
    suspend fun refreshCacheSizes()

    /**
     * Safely clears cache for a specific category.
     */
    suspend fun clearCategory(category: CacheCategory): EmptyResult<DataError.Local>

    /**
     * Safely clears all cache categories (audio streams, images, canvas, network, temp).
     */
    suspend fun clearAllCaches(): EmptyResult<DataError.Local>

    /**
     * Clears old temporary files (e.g. FFmpeg output, temp audio copies) on startup
     * to prevent app size bloat.
     */
    suspend fun clearOldCache(): EmptyResult<DataError.Local>

    /**
     * Trims in-memory LRU and image caches when the operating system signals low memory.
     */
    fun trimMemory(level: Int)
}

