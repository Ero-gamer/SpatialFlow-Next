package com.codetrio.spatialflow.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "media_cache")
        val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val maxSizeMb = prefs.getInt("song_cache_max_size", 256)
        val maxSizeBytes = (if (maxSizeMb <= 0) 256L else maxSizeMb.toLong()) * 1024L * 1024L
        val evictor = LeastRecentlyUsedCacheEvictor(maxSizeBytes)
        val databaseProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(): androidx.media3.datasource.okhttp.OkHttpDataSource.Factory {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .dns(com.codetrio.spatialflow.data.network.FastFallbackDns)
            .connectionPool(okhttp3.ConnectionPool(20, 5, java.util.concurrent.TimeUnit.MINUTES))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val profiled = com.codetrio.spatialflow.util.StreamClientUtils.applyRequestProfile(request.newBuilder(), request).build()
                val isMedia = request.url.host.contains("googlevideo.com")
                var response = chain.proceed(profiled)
                if (isMedia && response.code == 403) {
                    // Transparent 403 fallback: strip Origin/Referer and retry with ANDROID_VR client User-Agent
                    response.close()
                    val fallbackRequest = request.newBuilder()
                        .header("User-Agent", com.codetrio.spatialflow.util.StreamClientUtils.USER_AGENT_ANDROID_VR)
                        .removeHeader("Origin")
                        .removeHeader("Referer")
                        .build()
                    response = chain.proceed(fallbackRequest)
                }
                response
            }
            .build()

        return androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache,
        httpDataSourceFactory: androidx.media3.datasource.okhttp.OkHttpDataSource.Factory
    ): CacheDataSource.Factory {
        val defaultUpstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultUpstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun providePlaybackDataSourceFactory(
        @ApplicationContext context: Context,
        cacheDataSourceFactory: CacheDataSource.Factory,
        httpDataSourceFactory: androidx.media3.datasource.okhttp.OkHttpDataSource.Factory
    ): com.codetrio.spatialflow.data.sourcepool.SchemeRoutingDataSource.Factory {
        val directFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val deezerFactory = com.codetrio.spatialflow.data.deezer.DeezerDecryptingDataSource.Factory(httpDataSourceFactory)
        val tidalDashFactory = com.codetrio.spatialflow.data.tidal.TidalProgressiveDashDataSource.Factory(okhttp3.OkHttpClient())
        return com.codetrio.spatialflow.data.sourcepool.SchemeRoutingDataSource.Factory(
            directFactory = directFactory,
            cachedFactory = cacheDataSourceFactory,
            deezerFactory = deezerFactory,
            tidalDashFactory = tidalDashFactory
        )
    }

    @Provides
    @Singleton
    fun provideStreamRepository(@ApplicationContext context: Context): com.codetrio.spatialflow.domain.repository.StreamRepository {
        return com.codetrio.spatialflow.data.repository.InnerTubeStreamRepository(context)
    }
}

@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaEntryPoint {
    fun cacheDataSourceFactory(): CacheDataSource.Factory
    fun playbackDataSourceFactory(): com.codetrio.spatialflow.data.sourcepool.SchemeRoutingDataSource.Factory
    fun simpleCache(): SimpleCache
}
