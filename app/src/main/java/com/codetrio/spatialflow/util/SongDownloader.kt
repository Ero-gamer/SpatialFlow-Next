package com.codetrio.spatialflow.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.ReturnCode
import com.bumptech.glide.Glide
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.innertube.NewPipeStreamExtractor
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object SongDownloader {
    private const val TAG = "SongDownloader"
    private const val CHANNEL_ID = "spatialflow_downloads"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    fun cleanTitle(title: String): String {
        var clean = title
        val regexes = listOf(
            "(?i)\\[official music video\\]",
            "(?i)\\(official music video\\)",
            "(?i)\\[official video\\]",
            "(?i)\\(official video\\)",
            "(?i)\\[lyrics\\]",
            "(?i)\\(lyrics\\)",
            "(?i)\\[lyric video\\]",
            "(?i)\\(lyric video\\)",
            "(?i)\\[official audio\\]",
            "(?i)\\(official audio\\)",
            "(?i)official music video",
            "(?i)official video",
            "(?i)lyric video",
            "(?i)\\[video\\]",
            "(?i)\\(video\\)",
            "(?i)\\[official release\\]",
            "(?i)\\(official release\\)"
        )
        for (regex in regexes) {
            clean = clean.replace(Regex(regex), "")
        }
        // Remove trailing dashes, slashes, and spaces
        return clean.trim().replace(Regex("\\s+"), " ").removeSuffix("-").removeSuffix("/").trim()
    }

    fun cleanArtist(artist: String): String {
        return artist.replace(Regex("(?i)\\s*-\\s*Topic"), "").trim().replace(Regex("\\s+"), " ")
    }

    fun cleanAlbum(title: String): String {
        var clean = cleanTitle(title)
        val albumRegexes = listOf(
            "(?i)\\s*\\((slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\)",
            "(?i)\\s*\\[(slowed|reverb|speed up|sped up|remix|slowed\\s*\\+\\s*reverb|slowed\\s*and\\s*reverb)\\]"
        )
        for (regex in albumRegexes) {
            clean = clean.replace(Regex(regex), "")
        }
        return clean.trim().replace(Regex("\\s+"), " ")
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager?.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager?.activeNetworkInfo
            return networkInfo != null && networkInfo.type == android.net.ConnectivityManager.TYPE_WIFI
        }
    }

    @JvmOverloads
    fun downloadSong(
        context: Context, 
        song: SongItem, 
        customSubFolder: String? = null, 
        overrideFormat: String? = null
    ) {
        val videoId = song.videoId
        if (videoId.isNullOrEmpty()) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Local songs are already offline")
            return
        }

        val notificationId = song.id.toInt().coerceAtLeast(1)
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)

        if (customSubFolder == null) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Extracting and starting download: $cleanTitleStr")
        }

        scope.launch {
            _downloadProgress.value += (videoId to 0)
            try {
                val prefs = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                val wifiOnly = prefs.getBoolean("wifi_only_download", false)
                if (wifiOnly && !isWifiConnected(context)) {
                    cancelNotification(context, notificationId)
                    _downloadProgress.value -= videoId
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download paused: Wi-Fi only mode enabled")
                    }
                    return@launch
                }

                val preferredFormat = overrideFormat ?: prefs.getString("download_audio_format", "Opus") ?: "Opus"
                val preferredQuality = prefs.getString("download_audio_quality", "High") ?: "High"
                val offlineLyrics = prefs.getBoolean("offline_lyrics", true)

                // Initialize/Update download notification
                updateNotification(context, cleanTitleStr, 0, notificationId)

                var isFlacSource = false
                var streamUrl: String? = null

                // If user prefers FLAC (Lossless) or specified FLAC override, resolve lossless stream
                val isFlacRequested = preferredFormat.equals("FLAC", ignoreCase = true)
                if (isFlacRequested) {
                    try {
                        ConsoleLogger.stream("Attempting Lossless FLAC download for: $cleanTitleStr")
                        val preferHiRes = preferredQuality.equals("Highest", ignoreCase = true)
                        val pipeline = QualityModeSelector.getActiveLosslessPriorityPipeline(context)
                        val durationSec = song.duration.takeIf { it > 0 }?.let { it / 1000L }
                        val losslessResult = com.codetrio.spatialflow.data.sourcepool.LosslessStreamResolver.resolveStream(
                            videoId = videoId,
                            title = cleanTitleStr,
                            artist = cleanArtistStr,
                            durationSeconds = durationSec,
                            preferHiRes = preferHiRes,
                            pipeline = pipeline,
                            context = context
                        )
                        if (losslessResult != null && losslessResult.url.isNotBlank()) {
                            streamUrl = losslessResult.url
                            isFlacSource = losslessResult.formatTag.contains("FLAC", ignoreCase = true)
                            ConsoleLogger.stream("⚡ ${losslessResult.providerName} download stream acquired: [${losslessResult.formatTag}]")
                        } else {
                            ConsoleLogger.stream("Lossless stream not matched for download, falling back to YouTube Opus/AAC")
                        }
                    } catch (e: Throwable) {
                        ConsoleLogger.stream("Lossless download resolution failed: ${e.message}")
                    }
                }

                val bestStream = if (!isFlacSource) {
                    val playerResult = NewPipeStreamExtractor.getHighQualityPlayerResult(videoId)
                    playerResult?.streams?.firstOrNull()
                } else null

                if (streamUrl == null) {
                    streamUrl = bestStream?.url ?: NewPipeStreamExtractor.getStreamUrl(videoId, forceHighQuality = true)
                }

                if (streamUrl == null) {
                    cancelNotification(context, notificationId)
                    _downloadProgress.value -= videoId
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Failed to extract download URL for $cleanTitleStr")
                    }
                    return@launch
                }

                val mimeType = bestStream?.mimeType ?: (if (isFlacSource) "audio/flac" else "")
                val streamUrlLower = streamUrl.lowercase()
                val isOpusSource = !isFlacSource && (mimeType.contains("webm", ignoreCase = true) || 
                                   mimeType.contains("opus", ignoreCase = true) ||
                                   streamUrlLower.contains("mime=audio/webm") ||
                                   streamUrlLower.contains("mime=audio%2fwebm") ||
                                   streamUrlLower.contains("codecs=opus") ||
                                   streamUrlLower.contains("codecs%3dopus") ||
                                   streamUrlLower.contains("itag=251") ||
                                   streamUrlLower.contains("itag=250") ||
                                   streamUrlLower.contains("itag=249"))

                // Create temp download file
                val tempDownloadFile = File(context.cacheDir, "temp_download_${videoId}.tmp")
                val url = URL(streamUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    cancelNotification(context, notificationId)
                    _downloadProgress.value -= videoId
                    withContext(Dispatchers.Main) {
                        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download server error: HTTP ${connection.responseCode}")
                    }
                    return@launch
                }

                val totalBytes = connection.contentLength
                connection.inputStream.use { input ->
                    FileOutputStream(tempDownloadFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var downloadedBytes = 0
                        var lastProgressUpdate = 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            if (totalBytes > 0) {
                                val progress = ((downloadedBytes.toFloat() / totalBytes) * 100).toInt()
                                if (_downloadProgress.value[videoId] != progress) {
                                    _downloadProgress.value += (videoId to progress)
                                }
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 400) { // Throttle updates to every 400ms for fluid CPU performance
                                    lastProgressUpdate = now
                                    updateNotification(context, cleanTitleStr, progress, notificationId)
                                }
                            }
                        }
                    }
                }

                // Load, auto-crop 1:1, and compress thumbnail to a temp file
                var artworkFile: File? = null
                val artworkUrl = song.getAlbumArtUri()?.toString()
                    ?: song.thumbnailUrl
                    ?: song.videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }

                if (!artworkUrl.isNullOrEmpty() && !artworkUrl.startsWith("content://")) {
                    try {
                        val future = Glide.with(context)
                            .asBitmap()
                            .load(artworkUrl)
                            .submit()
                        val originalBitmap = future.get()
                        if (originalBitmap != null) {
                            val size = originalBitmap.width.coerceAtMost(originalBitmap.height)
                            val x = (originalBitmap.width - size) / 2
                            val y = (originalBitmap.height - size) / 2
                            val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
                            
                            val artFile = File(context.cacheDir, "temp_art_${videoId}.jpg")
                            FileOutputStream(artFile).use { fos ->
                                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                            }
                            artworkFile = artFile
                            Log.d(TAG, "Thumbnail successfully cropped and saved to ${artFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to fetch and crop thumbnail from $artworkUrl", e)
                    }
                }

                val isMp3 = !isFlacSource && preferredFormat.equals("MP3", ignoreCase = true)
                val isFlac = isFlacSource || preferredFormat.equals("FLAC", ignoreCase = true)
                val ext = if (isFlac) "flac" else if (isMp3) "mp3" else "opus"
                val fileName = "$cleanTitleStr - $cleanArtistStr.$ext"
                val targetSubFolder = customSubFolder ?: "SpatialFlow/Songs"
                val outputFile = AudioFileManager.createOutputFile(context, fileName, targetSubFolder)
                val cleanAlbumStr = cleanAlbum(song.title)

                val pictureBase64: String = artworkFile?.let { art ->
                    try {
                        val jpegBytes = art.readBytes()
                        val mime = "image/jpeg"
                        val mimeBytes = mime.toByteArray(Charsets.US_ASCII)
                        val buf = java.nio.ByteBuffer.allocate(
                            32 + mimeBytes.size + jpegBytes.size
                        ).order(java.nio.ByteOrder.BIG_ENDIAN)
                        buf.putInt(3)
                        buf.putInt(mimeBytes.size)
                        buf.put(mimeBytes)
                        buf.putInt(0)
                        buf.putInt(0)
                        buf.putInt(0)
                        buf.putInt(0)
                        buf.putInt(0)
                        buf.putInt(jpegBytes.size)
                        buf.put(jpegBytes)
                        android.util.Base64.encodeToString(buf.array(), android.util.Base64.NO_WRAP)
                    } catch (e: Exception) {
                        Log.e(TAG, "METADATA_BLOCK_PICTURE encoding failed", e)
                        ""
                    }
                } ?: ""

                var embeddedLyricsText = ""
                if (offlineLyrics) {
                    val cachedLyrics = com.codetrio.spatialflow.data.lyrics.engine.LyricsHelper.getSingle(cleanTitleStr, cleanArtistStr)
                    if (!cachedLyrics.isNullOrBlank()) {
                        embeddedLyricsText = cachedLyrics
                        Log.d(TAG, "Using cached lyrics for $cleanTitleStr")
                    } else {
                        try {
                            val latch = java.util.concurrent.CountDownLatch(1)
                            val manager = com.codetrio.spatialflow.data.lyrics.engine.LyricsFetchManager.getInstance(context)
                            manager.fetchLyrics(
                                title = cleanTitleStr,
                                artist = cleanArtistStr,
                                album = cleanAlbumStr,
                                durationMs = song.duration,
                                filePath = "",
                                callback = object : com.codetrio.spatialflow.data.lyrics.engine.LyricsFetchManager.LyricsCallback {
                                    override fun onLyricsFound(result: com.codetrio.spatialflow.data.lyrics.LyricsResult) {
                                        val text = result.syncedLyrics ?: result.plainLyrics
                                        if (!text.isNullOrBlank()) {
                                            embeddedLyricsText = text
                                        }
                                        latch.countDown()
                                    }

                                    override fun onLyricsUpgraded(betterResult: com.codetrio.spatialflow.data.lyrics.LyricsResult) {
                                        onLyricsFound(betterResult)
                                    }

                                    override fun onLyricsNotFound(reason: String) {
                                        latch.countDown()
                                    }

                                    override fun onInstrumental() {
                                        latch.countDown()
                                    }
                                },
                                videoId = song.videoId
                            )
                            latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
                        } catch (e: Exception) {
                            Log.w(TAG, "Lyrics pre-fetch timed out or failed for $cleanTitleStr", e)
                        }
                    }
                }

                val ffmpegArgs = when {
                    isFlac -> {
                        FFmpegCommandBuilder.buildFlacPackageArgs(
                            inputPath = tempDownloadFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            title = cleanTitleStr,
                            artist = cleanArtistStr,
                            album = cleanAlbumStr,
                            pictureBase64 = pictureBase64,
                            lyrics = embeddedLyricsText
                        ).toTypedArray()
                    }
                    isMp3 -> {
                        val bitrate = when (preferredQuality.lowercase()) {
                            "low" -> "96k"
                            "medium" -> "160k"
                            else -> "256k"
                        }
                        FFmpegCommandBuilder.buildMp3PackageArgs(
                            inputPath = tempDownloadFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            bitrate = bitrate,
                            title = cleanTitleStr,
                            artist = cleanArtistStr,
                            album = cleanAlbumStr,
                            coverArtPath = artworkFile?.absolutePath,
                            lyrics = embeddedLyricsText
                        ).toTypedArray()
                    }
                    else -> {
                        FFmpegCommandBuilder.buildOpusPackageArgs(
                            inputPath = tempDownloadFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            isOpusSource = isOpusSource,
                            title = cleanTitleStr,
                            artist = cleanArtistStr,
                            album = cleanAlbumStr,
                            pictureBase64 = pictureBase64,
                            lyrics = embeddedLyricsText
                        ).toTypedArray()
                    }
                }

                Log.d(TAG, "Executing FFmpeg packaging (${ffmpegArgs.size} args, embeddedLyrics=${embeddedLyricsText.isNotEmpty()})")
                val session = com.arthenica.ffmpegkit.FFmpegKit.executeWithArguments(ffmpegArgs)
                val success = ReturnCode.isSuccess(session.returnCode)

                // Clean up temp files
                try { tempDownloadFile.delete() } catch (_: Exception) {}
                try { artworkFile?.delete() } catch (_: Exception) {}

                if (!success) {
                    Log.e(TAG, "FFmpeg packaging failed with code ${session.returnCode}\n${session.allLogsAsString}")
                    throw Exception("FFmpeg failed to remux audio stream")
                }

                // Copy file to MediaStore and request a media scan
                AudioFileManager.scanFile(context, outputFile, targetSubFolder)

                // Show completion status
                showFinishedNotification(context, cleanTitleStr, cleanArtistStr, notificationId)

                _downloadProgress.value -= videoId
                val formatLabel = if (isFlac) "Lossless FLAC" else ext.uppercase()
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage(
                        "Downloaded ($formatLabel): $cleanTitleStr",
                        iconResId = R.drawable.ic_downloaded
                    )
                }

            } catch (e: Exception) {
                _downloadProgress.value -= videoId
                Log.e(TAG, "Download failed for ${song.title}", e)
                cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    com.codetrio.spatialflow.ui.SnackbarController.showMessage("Download failed: ${e.message}")
                }
            }
        }
    }

    fun downloadPlaylist(context: Context, songs: List<SongItem>) {
        val downloadable = songs.filter { !it.videoId.isNullOrEmpty() && !isSongDownloaded(context, it) }
        if (downloadable.isEmpty()) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("All tracks in playlist are already downloaded")
            return
        }
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Queuing ${downloadable.size} tracks for download")
        scope.launch {
            downloadable.forEach { song ->
                downloadSong(context, song)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of active song downloads"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(context: Context, title: String, progress: Int, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("Downloading Track")
            .setContentText("$title ($progress%)")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun showFinishedNotification(context: Context, title: String, artist: String, notificationId: Int) {
        createNotificationChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle("Download Complete")
            .setContentText("$title — $artist")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun isSongDownloaded(context: Context, song: SongItem): Boolean {
        if (song.videoId.isNullOrEmpty()) return true // Local songs are always offline
        
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        
        val fileNames = listOf(
            "$cleanTitleStr - $cleanArtistStr.m4a",
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.mp3"
        )

        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

            val songsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Songs")
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
            val musicSongsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SpatialFlow/Songs")
            
            if (File(songsDir, cleanName).exists() || File(downloadsDir, cleanName).exists() || File(musicSongsDir, cleanName).exists()) return true

            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(cleanName)
            
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.count > 0) return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if song is downloaded", e)
            }
        }

        return false
    }    fun getDownloadedSongUri(context: Context, song: SongItem): Uri? {
        if (song.videoId.isNullOrEmpty()) return null
        
        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        
        val fileNames = listOf(
            "$cleanTitleStr - $cleanArtistStr.m4a",
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.mp3"
        )

        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")

            // 1. Query MediaStore Audio first for content:// URI
            val projection = arrayOf(MediaStore.Audio.Media._ID)
            val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(cleanName)
            
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val id = cursor.getLong(idIndex)
                        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving downloaded song Audio URI", e)
            }

            // 2. Query MediaStore Downloads (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    context.contentResolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                            val id = cursor.getLong(idIndex)
                            return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving downloaded song Downloads URI", e)
                }
            }

            // 3. Fallback to file:// URI if physical file exists on disk
            val songsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Songs")
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
            val musicSongsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SpatialFlow/Songs")
            val fileInSongs = File(songsDir, cleanName)
            val fileInDownloads = File(downloadsDir, cleanName)
            val fileInMusicSongs = File(musicSongsDir, cleanName)

            if (fileInSongs.exists()) return Uri.fromFile(fileInSongs)
            if (fileInDownloads.exists()) return Uri.fromFile(fileInDownloads)
            if (fileInMusicSongs.exists()) return Uri.fromFile(fileInMusicSongs)
        }
        return null
    }

    fun getDownloadedSongsCount(context: Context): Int {
        return getAllDownloadedSongs(context).size
    }

    fun getDownloadedStorageBytes(context: Context): Long {
        var total = 0L
        val projection = arrayOf(MediaStore.Audio.Media.SIZE)
        val selection = "(${MediaStore.Audio.Media.DATA} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?) AND (${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("%SpatialFlow%", "%SpatialFlow%", "%.m4a", "%.opus", "%.mp3")
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val sizeIdx = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                while (cursor.moveToNext()) {
                    if (sizeIdx != -1) {
                        total += cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating downloaded storage bytes", e)
        }
        return total
    }

    fun getAllDownloadedSongs(context: Context): List<SongItem> {
        try {
            context.contentResolver.delete(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("%.lrc", "%.ttml", "%.txt")
            )
        } catch (_: Exception) {}

        val list = mutableListOf<SongItem>()
        val seenPathsAndNames = mutableSetOf<String>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME
        )
        val selection = "(${MediaStore.Audio.Media.DATA} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?) AND (${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("%SpatialFlow%", "%SpatialFlow%", "%.m4a", "%.opus", "%.mp3")
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val title = cursor.getString(titleIdx) ?: "Unknown Track"
                    val artist = cursor.getString(artistIdx) ?: "<Unknown>"
                    val album = cursor.getString(albumIdx) ?: ""
                    val albumId = cursor.getLong(albumIdIdx)
                    val duration = cursor.getLong(durationIdx)
                    val dataPath = cursor.getString(dataIdx) ?: ""

                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val songItem = SongItem(
                        id = id,
                        rawTitle = title,
                        rawArtist = artist,
                        albumId = albumId,
                        path = dataPath,
                        duration = duration,
                        dateAdded = System.currentTimeMillis()
                    ).apply {
                        videoId = "downloaded_$id"
                        contentUri = uri
                        thumbnailUrl = uri.toString()
                        albumName = album
                    }
                    seenPathsAndNames.add(dataPath)
                    seenPathsAndNames.add(title)
                    list.add(songItem)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying downloaded songs", e)
        }

        // Also scan physical files in SpatialFlow folders to ensure any non-indexed song is shown!
        val songsDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Songs"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SpatialFlow/Songs")
        )

        for (dir in songsDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { _, name -> 
                    name.endsWith(".m4a", ignoreCase = true) || 
                    name.endsWith(".opus", ignoreCase = true) || 
                    name.endsWith(".mp3", ignoreCase = true) 
                }?.forEach { file ->
                    if (!seenPathsAndNames.contains(file.absolutePath)) {
                        val nameWithoutExt = file.nameWithoutExtension
                        var title = nameWithoutExt
                        var artist = "Unknown Artist"
                        if (nameWithoutExt.contains(" - ")) {
                            val parts = nameWithoutExt.split(" - ".toRegex(), 2)
                            title = parts[0].trim()
                            artist = parts[1].trim()
                        }
                        val songId = file.absolutePath.hashCode().toLong()
                        val songItem = SongItem(
                            id = songId,
                            rawTitle = title,
                            rawArtist = artist,
                            albumId = 0L,
                            path = file.absolutePath,
                            duration = 0L,
                            dateAdded = file.lastModified()
                        ).apply {
                            videoId = "downloaded_local_$songId"
                            contentUri = Uri.fromFile(file)
                            thumbnailUrl = Uri.fromFile(file).toString()
                        }
                        seenPathsAndNames.add(file.absolutePath)
                        list.add(songItem)
                    }
                }
            }
        }

        return list
    }

    fun deleteDownloadedSong(context: Context, song: SongItem): Boolean {
        var deleted = false
        val songUri = if (song.id > 0L) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id) else null
        val targetUri = getDownloadedSongUri(context, song) ?: song.contentUri ?: songUri

        // Strategy 1: Physical File Deletion
        if (!song.path.isNullOrEmpty()) {
            try {
                val file = File(song.path)
                if (file.exists() && file.delete()) {
                    deleted = true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Direct file delete skipped: ${e.message}")
            }
        }

        val cleanTitleStr = cleanTitle(song.title)
        val cleanArtistStr = cleanArtist(song.artist)
        val fileNames = listOf(
            "$cleanTitleStr - $cleanArtistStr.m4a",
            "$cleanTitleStr - $cleanArtistStr.opus",
            "$cleanTitleStr - $cleanArtistStr.mp3"
        )
        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
            val songsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow/Songs")
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SpatialFlow")
            val musicSongsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SpatialFlow/Songs")
            
            listOf(File(songsDir, cleanName), File(downloadsDir, cleanName), File(musicSongsDir, cleanName)).forEach { f ->
                if (f.exists() && f.delete()) {
                    deleted = true
                }
            }
        }

        // Strategy 2: MediaStore Deletion by Uri (only for content:// scheme)
        if (targetUri != null) {
            if (targetUri.scheme == "content") {
                try {
                    if (context.contentResolver.delete(targetUri, null, null) > 0) {
                        deleted = true
                    }
                } catch (e: SecurityException) {
                    if (!deleted) {
                        Log.w(TAG, "SecurityException while deleting $targetUri, prompting system deletion dialog", e)
                        requestDeletionPermission(context, listOf(targetUri), e)
                        return false
                    } else {
                        Log.d(TAG, "MediaProvider delete restricted, but physical file was already deleted: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ContentResolver delete failed for $targetUri", e)
                }
            } else if (targetUri.scheme == "file") {
                try {
                    val f = File(targetUri.path ?: "")
                    if (f.exists() && f.delete()) {
                        deleted = true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Direct file delete failed for $targetUri", e)
                }
            }
        }

        // Strategy 3: MediaStore Deletion by DISPLAY_NAME
        for (fileName in fileNames) {
            val cleanName = fileName.replace(Regex("[^a-zA-Z0-9._\\s()\\[\\]-]"), "_")
            try {
                val count = context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
                    arrayOf(cleanName)
                )
                if (count > 0) deleted = true
            } catch (_: Exception) {}

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val count = context.contentResolver.delete(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                        arrayOf(cleanName)
                    )
                    if (count > 0) deleted = true
                } catch (_: Exception) {}
            }
        }

        if (!deleted && targetUri != null && targetUri.scheme == "content") {
            requestDeletionPermission(context, listOf(targetUri))
        }

        if (deleted) {
            if (!song.path.isNullOrEmpty()) {
                AudioFileManager.scanDeletedFile(context, File(song.path))
            }
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deleted: ${song.title}")
        }
        return deleted
    }

    fun clearAllDownloadedSongs(context: Context) {
        val songs = getAllDownloadedSongs(context)
        if (songs.isEmpty()) return

        val uris = songs.mapNotNull { getDownloadedSongUri(context, it) ?: it.contentUri ?: ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                val pendingIntent = MediaStore.createTrashRequest(context.contentResolver, uris, true)
                val activity = context.findActivity()
                if (activity != null) {
                    activity.startIntentSenderForResult(pendingIntent.intentSender, 9999, null, 0, 0, 0)
                    return
                }
            } catch (_: Exception) {}
        }

        var count = 0
        songs.forEach { song ->
            if (deleteDownloadedSong(context, song)) count++
        }
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Cleared $count downloaded tracks")
    }

    fun requestDeletionPermission(context: Context, uris: List<Uri>, securityException: SecurityException? = null) {
        val activity = context.findActivity() ?: return
        try {
            if (securityException is android.app.RecoverableSecurityException) {
                val intentSender = securityException.userAction.actionIntent.intentSender
                activity.startIntentSenderForResult(intentSender, 9999, null, 0, 0, 0)
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, uris)
                activity.startIntentSenderForResult(
                    pendingIntent.intentSender,
                    9999,
                    null, 0, 0, 0
                )
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                val firstUri = uris.firstOrNull() ?: return
                try {
                    context.contentResolver.delete(firstUri, null, null)
                } catch (e: android.app.RecoverableSecurityException) {
                    activity.startIntentSenderForResult(
                        e.userAction.actionIntent.intentSender,
                        9999,
                        null, 0, 0, 0
                    )
                }
            } else {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        9999
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch deletion permission dialog", e)
        }
    }

    private fun Context.findActivity(): android.app.Activity? {
        var ctx = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun downloadPlaylist(
        context: Context,
        playlistTitle: String,
        songs: List<SongItem>,
        format: String // "opus" or "mp3"
    ) {
        if (songs.isEmpty()) {
            com.codetrio.spatialflow.ui.SnackbarController.showMessage("No songs to download in '$playlistTitle'")
            return
        }
        val cleanPlaylistName = playlistTitle.replace(Regex("[^a-zA-Z0-9._\\s-]"), "_").trim()
        val subFolder = "SpatialFlow/Playlists/$cleanPlaylistName"
        com.codetrio.spatialflow.ui.SnackbarController.showMessage("Starting batch download: ${songs.size} songs from '$playlistTitle' ($format)")
        
        scope.launch {
            songs.forEach { song ->
                downloadSong(
                    context = context,
                    song = song,
                    customSubFolder = subFolder,
                    overrideFormat = format
                )
            }
        }
    }
}
