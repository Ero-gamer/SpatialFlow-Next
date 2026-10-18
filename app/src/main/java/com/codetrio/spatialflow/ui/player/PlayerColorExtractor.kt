package com.codetrio.spatialflow.ui.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

data class ExtractedPaletteColors(
    val dominant: Color,
    val vibrant: Color,
    val darkMuted: Color,
    val lightVibrant: Color,
    val accent: Color,
    val darkVibrant: Color = dominant
) {
    companion object {
        val Default = ExtractedPaletteColors(
            dominant = Color(0xFF1A1A1A),
            vibrant = Color(0xFF333333),
            darkMuted = Color(0xFF121212),
            lightVibrant = Color(0xFFEEEEEE),
            accent = Color(0xFFFFFFFF),
            darkVibrant = Color(0xFF1A1A1A)
        )
    }
}

/**
 * Extracts and caches rich ambient palette colors from album art bitmaps using AndroidX Palette.
 */
object PlayerColorExtractor {

    private val cache = LruCache<String, ExtractedPaletteColors>(50)

    fun getCached(key: String?): ExtractedPaletteColors? {
        if (key == null) return null
        return cache.get(key)
    }

    fun putInCache(key: String?, colors: ExtractedPaletteColors) {
        if (key != null) {
            cache.put(key, colors)
        }
    }

    fun extractFromBitmap(key: String?, bitmap: Bitmap?): ExtractedPaletteColors {
        if (key != null) {
            cache.get(key)?.let { return it }
        }
        if (bitmap == null) return ExtractedPaletteColors.Default

        // Downscale bitmap to max area 6400 (80x80) for <3ms extraction latency
        val palette = Palette.from(bitmap)
            .resizeBitmapArea(6400)
            .maximumColorCount(16)
            .generate()

        val dominantInt = palette.getDominantColor(0xFF1A1A1A.toInt())
        val darkVibrantInt = palette.getDarkVibrantColor(dominantInt)
        val darkMutedInt = palette.getDarkMutedColor(darkVibrantInt)
        val vibrantInt = palette.getVibrantColor(palette.getLightVibrantColor(dominantInt))
        val lightVibrantInt = palette.getLightVibrantColor(palette.getVibrantColor(0xFFFFFFFF.toInt()))
        val accentInt = palette.getVibrantColor(dominantInt)

        val colors = ExtractedPaletteColors(
            dominant = Color(dominantInt),
            vibrant = Color(vibrantInt),
            darkMuted = Color(darkMutedInt),
            lightVibrant = Color(lightVibrantInt),
            accent = Color(accentInt),
            darkVibrant = Color(darkVibrantInt)
        )

        if (key != null) {
            cache.put(key, colors)
        }

        return colors
    }

    fun extractFrameFromVideo(
        videoUrl: String,
        context: android.content.Context,
        mediaId: String? = null
    ): Bitmap? {
        val retriever = android.media.MediaMetadataRetriever()
        val providerName = when {
            videoUrl.contains("scdn.co") || videoUrl.contains("spotify") -> "Spotify"
            videoUrl.contains("apple") || videoUrl.contains("mzstatic") -> "Apple Music"
            else -> "Canvas"
        }
        android.util.Log.d("CanvasColorExtraction", "Capturing frame from [$providerName] URL: $videoUrl")

        var tempFile: java.io.File? = null
        return try {
            if (videoUrl.startsWith("http://") || videoUrl.startsWith("https://")) {
                val cacheDir = java.io.File(context.cacheDir, "canvas_cache")
                val urlHash = (videoUrl.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
                val cachedFile = cacheDir.listFiles()?.firstOrNull { f ->
                    f.isFile && f.length() > 0 && (f.name.contains(urlHash) || (!mediaId.isNullOrBlank() && f.name.startsWith(mediaId)))
                }

                val videoPath = if (cachedFile != null && cachedFile.exists()) {
                    android.util.Log.d("CanvasColorExtraction", "Using cached canvas video: ${cachedFile.absolutePath}")
                    cachedFile.absolutePath
                } else {
                    tempFile = java.io.File.createTempFile("canvas_probe_", ".mp4", context.cacheDir)
                    val conn = java.net.URL(videoUrl).openConnection() as java.net.HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    conn.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var totalRead = 0
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalRead > 4 * 1024 * 1024) break // 4MB safety limit
                            }
                        }
                    }
                    android.util.Log.d("CanvasColorExtraction", "Downloaded probe video (${tempFile.length()} bytes)")
                    tempFile.absolutePath
                }

                retriever.setDataSource(videoPath)
            } else {
                retriever.setDataSource(videoUrl)
            }

            val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 160)
                    ?: retriever.frameAtTime
            } else {
                retriever.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            }

            if (bitmap != null) {
                android.util.Log.d("CanvasColorExtraction", "SUCCESS: Captured frame (${bitmap.width}x${bitmap.height}) from [$providerName]!")
            } else {
                android.util.Log.w("CanvasColorExtraction", "WARNING: Frame decode returned null from [$providerName]!")
            }
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("CanvasColorExtraction", "FAILED to extract frame from [$providerName]: ${e.message}", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
            try { tempFile?.delete() } catch (_: Exception) {}
        }
    }
}
