package com.codetrio.spatialflow

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.codetrio.spatialflow.data.innertube.InnerTubeClient
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.domain.repository.LocalCacheDataSource
import com.codetrio.spatialflow.ui.player.canvas.CanvasArtworkPlaybackCache
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SpatialFlowApplication : Application(), ImageLoaderFactory {

    companion object {
        lateinit var instance: SpatialFlowApplication
            private set
    }

    @Inject
    lateinit var localCacheDataSource: LocalCacheDataSource


    private var cachedImageLoader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader {
        return cachedImageLoader ?: synchronized(this) {
            cachedImageLoader ?: ImageLoader.Builder(this)
                .okHttpClient {
                    okhttp3.OkHttpClient.Builder()
                        .dispatcher(okhttp3.Dispatcher().apply {
                            maxRequests = 128
                            maxRequestsPerHost = 64
                        })
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .connectionPool(okhttp3.ConnectionPool(64, 5, java.util.concurrent.TimeUnit.MINUTES))
                        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
                        .build()
                }
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.25) // Balanced 25% memory cache quota to prevent GC pressure
                        .strongReferencesEnabled(true)
                        .weakReferencesEnabled(true)
                        .build()
                }
                .diskCache {
                    val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
                    val maxSizeMb = prefs.getInt("image_cache_max_size", 128)
                    val maxSizeBytes = (if (maxSizeMb <= 0) 128L else maxSizeMb.toLong()) * 1024L * 1024L
                    DiskCache.Builder()
                        .directory(this.cacheDir.resolve("image_cache"))
                        .maxSizeBytes(maxSizeBytes)
                        .build()
                }
                .components {
                    add(com.codetrio.spatialflow.util.AudioArtworkFetcher.Factory(this@SpatialFlowApplication))
                    add(com.codetrio.spatialflow.util.AudioArtworkFetcher.Keyer())
                    add(com.codetrio.spatialflow.util.AudioArtworkFetcher.SongItemMapper())
                    add(com.codetrio.spatialflow.util.AudioArtworkFetcher.SongItemKeyer())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(coil.decode.ImageDecoderDecoder.Factory())
                    } else {
                        add(coil.decode.GifDecoder.Factory())
                    }
                }
                .crossfade(true)
                .crossfade(200) // Fast 200ms crossfade transition
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) // Zero-copy GPU bitmaps on Android P+
                .allowRgb565(false)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build().also { cachedImageLoader = it }
        }
    }

    @SuppressLint("VisibleForTests")
    override fun onCreate() {
        // Initialize global crash interceptor to catch any unexpected errors
        com.codetrio.spatialflow.util.CrashHandler.init(this)

        super.onCreate()
        instance = this
        
        // Proactive Initialization: Connect localized persistence cache to high-speed networking engine
        InnerTubeClient.initialize(this)

        // Initialize canvas motion artwork disk cache
        CanvasArtworkPlaybackCache.init(this)

        // Initialize Japanese Language Pack Manager / Kuromoji Tokenizer
        com.codetrio.spatialflow.data.lyrics.JapaneseLanguagePackManager.initialize(this)

        // Warm up native FFmpeg binaries in background thread so playback can bind instantly later
        Thread {
            try {
                com.arthenica.ffmpegkit.FFmpegKitConfig.ignoreSignal(com.arthenica.ffmpegkit.Signal.SIGXCPU)
                com.arthenica.ffmpegkit.FFmpegKit.executeAsync("-version") { }
            } catch (_: Exception) {
            }
        }.start()
        
        // Initialize global theme mode
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Apply dynamic colors to all activities
        DynamicColors.applyToActivitiesIfAvailable(this)

        // Load saved YouTube Music cookies for logged-in features
        val savedCookie = prefs.getString("yt_cookies", null)
        if (savedCookie != null) {
            InnerTubeClient.cookie = savedCookie
        }

        // Background Warm-up: Initialize NewPipe, OkHttp, and TLS to YouTube CDN
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // 1. Warm up OkHttp TCP+TLS connection to YouTube CDN & InnerTube API
                InnerTubeClient.httpClient.newCall(
                    okhttp3.Request.Builder()
                        .url("https://www.youtube.com/generate_204")
                        .head()
                        .build()
                ).execute().close()

                InnerTubeClient.httpClient.newCall(
                    okhttp3.Request.Builder()
                        .url("https://music.youtube.com/generate_204")
                        .head()
                        .build()
                ).execute().close()
                Log.d("SpatialFlowApp", "Successfully warmed OkHttp connection to YouTube CDN")
            } catch (e: Exception) {
                Log.w("SpatialFlowApp", "Failed to warm up OkHttp connection: ${e.message}")
            }

            try {
                // 2. Initialize NewPipe Extractor and prime signature timestamp asynchronously
                NewPipeStreamExtractor.init(this@SpatialFlowApplication)
                InnerTubeClient.getCachedSignatureTimestamp("kJQP7kiw5Fk")
                Log.d("SpatialFlowApp", "Successfully initialized NewPipe Extractor and primed signature cache")
            } catch (e: Throwable) {
                Log.w("SpatialFlowApp", "Failed to warm up NewPipe Extractor: ${e.message}")
            }
        }

        // Suppress expected Glide warnings for missing album arts
        Glide.init(this, GlideBuilder().setLogLevel(Log.ERROR))

        // Clean up old temp files on app start
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            localCacheDataSource.clearOldCache()
        }

        // Pre-warm Source Pool leased accounts (Qobuz Hi-Res, Tidal, Deezer) for zero-latency lossless resolution
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                com.codetrio.spatialflow.data.sourcepool.SourcePoolAccountManager.refreshIfNeeded(this@SpatialFlowApplication)
            } catch (e: Throwable) {
                Log.w("SpatialFlowApp", "Failed to pre-warm source pool accounts: ${e.message}")
            }
        }

        // Schedule periodic 24-hour background update check via WorkManager
        com.codetrio.spatialflow.update.UpdateCheckWorker.schedulePeriodicCheck(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::localCacheDataSource.isInitialized) {
            localCacheDataSource.trimMemory(level)
        }
    }
}
