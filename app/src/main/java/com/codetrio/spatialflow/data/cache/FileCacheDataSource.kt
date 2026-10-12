package com.codetrio.spatialflow.data.cache

import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import coil.imageLoader
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.EmptyResult
import com.codetrio.spatialflow.domain.error.Result
import com.codetrio.spatialflow.domain.model.CacheBreakdown
import com.codetrio.spatialflow.domain.model.CacheCategory
import com.codetrio.spatialflow.domain.model.CategoryCacheInfo
import com.codetrio.spatialflow.domain.repository.LocalCacheDataSource
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache
import com.codetrio.spatialflow.ui.player.canvas.CanvasPlayerResources
import com.codetrio.spatialflow.ui.player.canvas.SongCanvasProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class FileCacheDataSource @OptIn(UnstableApi::class)
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val simpleCacheProvider: Provider<SimpleCache>
) : LocalCacheDataSource {

    companion object {
        private const val TAG = "FileCacheDataSource"
        private val TEMP_FILE_PREFIXES = arrayOf("temp_", "8d_audio_", "8d", "canvas_probe_")
        private val CACHE_SUBDIRS = arrayOf("SpatialFlow_output", "images", "shared_recaps", "8d")
    }

    private val prefs by lazy {
        context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    }

    private val _cacheBreakdown = MutableStateFlow(CacheBreakdown.Initial)
    override val cacheBreakdown: StateFlow<CacheBreakdown> = _cacheBreakdown.asStateFlow()

    override suspend fun refreshCacheSizes() = withContext(Dispatchers.IO) {
        try {
            _cacheBreakdown.value = _cacheBreakdown.value.copy(isCalculating = true)

            // 1. Song audio cache
            val songMaxMb = prefs.getInt("song_cache_max_size", 256)
            val songQuotaBytes = if (songMaxMb <= 0) 0L else songMaxMb.toLong() * 1024L * 1024L
            val songBytes = getSongCacheBytes()

            // 2. Image cache
            val imageMaxMb = prefs.getInt("image_cache_max_size", 128)
            val imageQuotaBytes = if (imageMaxMb <= 0) 0L else imageMaxMb.toLong() * 1024L * 1024L
            val imageBytes = getImageCacheBytes()

            // 3. Canvas video loops
            val canvasQuotaBytes = 50L * 1024L * 1024L
            val canvasBytes = getCanvasCacheBytes()

            // 4. Network and temp files
            val networkTempQuotaBytes = 100L * 1024L * 1024L
            val networkTempBytes = getNetworkAndTempBytes()

            val categories = mapOf(
                CacheCategory.SONGS to CategoryCacheInfo(
                    category = CacheCategory.SONGS,
                    bytesUsed = songBytes,
                    formattedSize = formatSize(songBytes),
                    maxSizeMb = songMaxMb,
                    quotaBytes = songQuotaBytes
                ),
                CacheCategory.IMAGES to CategoryCacheInfo(
                    category = CacheCategory.IMAGES,
                    bytesUsed = imageBytes,
                    formattedSize = formatSize(imageBytes),
                    maxSizeMb = imageMaxMb,
                    quotaBytes = imageQuotaBytes
                ),
                CacheCategory.CANVAS to CategoryCacheInfo(
                    category = CacheCategory.CANVAS,
                    bytesUsed = canvasBytes,
                    formattedSize = formatSize(canvasBytes),
                    maxSizeMb = 50,
                    quotaBytes = canvasQuotaBytes
                ),
                CacheCategory.NETWORK_TEMP to CategoryCacheInfo(
                    category = CacheCategory.NETWORK_TEMP,
                    bytesUsed = networkTempBytes,
                    formattedSize = formatSize(networkTempBytes),
                    maxSizeMb = 100,
                    quotaBytes = networkTempQuotaBytes
                )
            )

            val totalBytes = songBytes + imageBytes + canvasBytes + networkTempBytes

            _cacheBreakdown.value = CacheBreakdown(
                categories = categories,
                totalBytes = totalBytes,
                formattedTotalSize = formatSize(totalBytes),
                isCalculating = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating cache sizes: ${e.message}", e)
            _cacheBreakdown.value = _cacheBreakdown.value.copy(isCalculating = false)
        }
    }

    override suspend fun clearCategory(category: CacheCategory): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            when (category) {
                CacheCategory.SONGS -> clearSongCacheInternal()
                CacheCategory.IMAGES -> clearImageCacheInternal()
                CacheCategory.CANVAS -> clearCanvasCacheInternal()
                CacheCategory.NETWORK_TEMP -> clearNetworkTempInternal()
            }
            refreshCacheSizes()
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear category $category: ${e.message}", e)
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override suspend fun clearAllCaches(): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            clearSongCacheInternal()
            clearImageCacheInternal()
            clearCanvasCacheInternal()
            clearNetworkTempInternal()

            // Sweep any remaining unmanaged transient files in cache root
            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.isFile && (shouldDeleteFile(file) || file.name.endsWith(".tmp") || file.name.endsWith(".part"))) {
                        runCatching { file.delete() }
                    }
                }
            }

            refreshCacheSizes()
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all caches: ${e.message}", e)
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private fun clearCanvasCacheInternal() {
        try {
            SongCanvasProvider.clearCache()
            CanvasPlayerResources.clearCache()
            runCatching {
                CanvasArtworkPlaybackCache.init(context)
                CanvasArtworkPlaybackCache.clear()
            }

            val canvasCache = File(context.cacheDir, "canvas_cache")
            if (canvasCache.exists()) {
                deleteDirectory(canvasCache)
            }

            val canvasStreamCache = File(context.cacheDir, "canvas_stream_cache")
            if (canvasStreamCache.exists()) {
                deleteDirectory(canvasStreamCache)
            }

            // Re-initialize playback cache so the directory is ready for subsequent usage
            runCatching { CanvasArtworkPlaybackCache.init(context) }

            Log.d(TAG, "Canvas cache cleared completely")
        } catch (e: Exception) {
            Log.w(TAG, "Canvas cache clear error: ${e.message}")
        }
    }

    private fun clearNetworkTempInternal() {
        try {
            InnerTubeClient.httpClient.cache?.evictAll()
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube HTTP cache evict error: ${e.message}")
        }

        val httpCacheDir = File(context.cacheDir, "http_cache_innertube")
        if (httpCacheDir.exists()) {
            deleteDirectory(httpCacheDir)
        }

        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                if (shouldDeleteFile(file)) {
                    file.delete()
                }
            }
            for (subdir in CACHE_SUBDIRS) {
                val dir = File(cacheDir, subdir)
                if (dir.exists()) {
                    deleteDirectory(dir)
                }
            }
        }
        Log.d(TAG, "Network and temp cache cleared")
    }

    override suspend fun clearOldCache(): EmptyResult<DataError.Local> = withContext(Dispatchers.IO) {
        try {
            var deletedCount = 0
            var freedBytes = 0L

            val cacheDir = context.cacheDir
            if (cacheDir != null && cacheDir.exists()) {
                val files = cacheDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (shouldDeleteFile(file)) {
                            val size = file.length()
                            if (file.delete()) {
                                deletedCount++
                                freedBytes += size
                            }
                        }
                    }
                }

                for (subdir in CACHE_SUBDIRS) {
                    val dir = File(cacheDir, subdir)
                    if (dir.exists() && dir.isDirectory) {
                        val dirSize = deleteDirectory(dir)
                        if (dirSize > 0) {
                            deletedCount++
                            freedBytes += dirSize
                        }
                    }
                }
            }

            try {
                Glide.get(context).clearDiskCache()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear Glide disk cache: ${e.message}")
            }

            if (deletedCount > 0) {
                Log.d(TAG, "Startup cache cleanup: deleted $deletedCount files, freed ${formatSize(freedBytes)}")
            }

            // Initial refresh of cache sizes
            refreshCacheSizes()

            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Startup cache cleanup failed: ${e.message}", e)
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    override fun trimMemory(level: Int) {
        try {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                context.imageLoader.memoryCache?.clear()
                Glide.get(context).trimMemory(level)
                SongCanvasProvider.clearCache()
                Log.d(TAG, "Trimmed in-memory image and canvas caches for level: $level")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error trimming memory: ${e.message}")
        }
    }

    // ── Internal Size Computations ───────────────────────────────────────────

    private fun getSongCacheBytes(): Long {
        return try {
            val simpleCache = simpleCacheProvider.get()
            simpleCache.cacheSpace
        } catch (_: Exception) {
            val mediaCacheDir = File(context.cacheDir, "media_cache")
            getDirSize(mediaCacheDir)
        }
    }

    private fun getImageCacheBytes(): Long {
        var size = 0L
        try {
            val coilSize = context.imageLoader.diskCache?.size ?: 0L
            size += coilSize
        } catch (_: Exception) {}

        val imageCacheDir = File(context.cacheDir, "image_cache")
        if (size == 0L && imageCacheDir.exists()) {
            size = getDirSize(imageCacheDir)
        }
        return size
    }

    private fun getCanvasCacheBytes(): Long {
        var size = 0L
        val canvasCache = File(context.cacheDir, "canvas_cache")
        if (canvasCache.exists()) {
            size += getDirSize(canvasCache)
        }
        val canvasStreamCache = File(context.cacheDir, "canvas_stream_cache")
        if (canvasStreamCache.exists()) {
            size += getDirSize(canvasStreamCache)
        }
        return size
    }

    private fun getNetworkAndTempBytes(): Long {
        var size = 0L
        // OkHttp InnerTube cache
        try {
            InnerTubeClient.httpClient.cache?.let { httpCache ->
                size += httpCache.size()
            }
        } catch (_: Exception) {}

        // Fallback reading http_cache_innertube
        val httpCacheDir = File(context.cacheDir, "http_cache_innertube")
        if (size == 0L && httpCacheDir.exists()) {
            size += getDirSize(httpCacheDir)
        }

        // Add transient files
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { file ->
                if (shouldDeleteFile(file)) {
                    size += file.length()
                }
            }
            for (subdir in CACHE_SUBDIRS) {
                val dir = File(cacheDir, subdir)
                if (dir.exists()) {
                    size += getDirSize(dir)
                }
            }
        }
        return size
    }

    // ── Internal Clear Operations ────────────────────────────────────────────

    private fun clearSongCacheInternal() {
        try {
            val simpleCache = simpleCacheProvider.get()
            val keys = simpleCache.keys.toList()
            keys.forEach { key ->
                try {
                    simpleCache.removeResource(key)
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing simpleCache resource $key: ${e.message}")
                }
            }
            Log.d(TAG, "Safe SimpleCache resource clear completed (${keys.size} items)")
        } catch (e: Exception) {
            Log.w(TAG, "SimpleCache keys clear failed, fallback to dir cleanup: ${e.message}")
            val mediaCacheDir = File(context.cacheDir, "media_cache")
            deleteDirectory(mediaCacheDir)
        }
    }

    private suspend fun clearImageCacheInternal() {
        try {
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
        } catch (e: Exception) {
            Log.w(TAG, "Coil cache clear error: ${e.message}")
        }

        try {
            Glide.get(context).clearDiskCache()
            withContext(Dispatchers.Main) {
                Glide.get(context).clearMemory()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Glide cache clear error: ${e.message}")
        }

        val imageCacheDir = File(context.cacheDir, "image_cache")
        if (imageCacheDir.exists()) {
            deleteDirectory(imageCacheDir)
        }
        Log.d(TAG, "Image cache cleared")
    }

    // ── Helper Utilities ─────────────────────────────────────────────────────

    private fun shouldDeleteFile(file: File?): Boolean {
        if (file == null || !file.isFile) return false
        val name = file.name
        return TEMP_FILE_PREFIXES.any { name.startsWith(it) }
    }

    private fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.listFiles()?.forEach { file ->
            total += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return total
    }

    private fun deleteDirectory(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var totalSize = 0L
        val files = dir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    totalSize += deleteDirectory(file)
                } else {
                    totalSize += file.length()
                    file.delete()
                }
            }
        }
        dir.delete()
        return totalSize
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        if (bytes < 1024L) return "$bytes B"
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
