package com.codetrio.spatialflow.util

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.collection.LruCache
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.YouTubeMusic
import com.codetrio.spatialflow.data.repository.CreateShareRequest
import com.codetrio.spatialflow.data.repository.SharedSongRepository
import com.codetrio.spatialflow.domain.error.onFailure
import com.codetrio.spatialflow.domain.error.onSuccess
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.ui.SnackbarController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates sharing a SongItem via the Cloudflare Worker API
 * and presenting the native Android share sheet.
 */
object SongShareHelper {

    private const val TAG = "SongShareHelper"
    private val repository = SharedSongRepository()
    private val shareUrlCache = LruCache<String, String>(64)
    private val appShareScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    fun shareSong(
        context: Context,
        song: SongItem?,
        coroutineScope: CoroutineScope = appShareScope
    ) {
        if (song == null) {
            SnackbarController.showMessage("No song currently selected to share")
            return
        }

        SnackbarController.showMessage("Creating share link...")

        coroutineScope.launch {
            shareSongDirect(context, song)
        }
    }

    suspend fun shareSongDirect(context: Context, song: SongItem) {
        val cacheKey = "${song.id}_${song.videoId ?: ""}"
        val cachedUrl = shareUrlCache[cacheKey]

        if (cachedUrl != null) {
            launchShareSheet(context, song, cachedUrl)
            return
        }

        try {
            val (provider, providerId) = withContext(Dispatchers.IO) {
                resolveProviderInfo(song)
            }

            val durationSec = if (song.duration > 0) song.duration / 1000L else null
            val artwork = song.thumbnailUrl
                ?: song.getAlbumArtUri()?.takeIf { it.scheme?.startsWith("http") == true }?.toString()

            val request = CreateShareRequest(
                title = song.title.ifBlank { "Unknown Title" },
                artist = song.artist.ifBlank { "Unknown Artist" },
                album = song.albumName,
                artworkUrl = artwork,
                duration = durationSec,
                provider = provider,
                providerId = providerId
            )

            val result = withContext(Dispatchers.IO) {
                repository.createShare(request)
            }

            result.onSuccess { shareResult ->
                shareUrlCache.put(cacheKey, shareResult.url)
                launchShareSheet(context, song, shareResult.url)
            }

            result.onFailure { error ->
                Log.w(TAG, "Failed to create share link: $error, falling back to local link")
                val fallbackUrl = getFallbackShareUrl(song)
                launchShareSheet(context, song, fallbackUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error sharing song", e)
            val fallbackUrl = getFallbackShareUrl(song)
            launchShareSheet(context, song, fallbackUrl)
        }
    }

    private suspend fun resolveProviderInfo(song: SongItem): Pair<String, String> {
        val existingVideoId = song.videoId
            ?: song.contentUri.takeIf { it.scheme == "innertube" }?.host

        if (!existingVideoId.isNullOrBlank()) {
            return "youtube" to existingVideoId
        }

        // If local song or song without videoId, find matching YouTube stream so recipients can play it
        try {
            val query = "${song.title} ${song.artist}".trim()
            if (query.isNotBlank()) {
                val searchResult = YouTubeMusic.search(query, SearchFilter.SONGS).getOrNull()
                    ?: YouTubeMusic.search(query).getOrNull()

                val matchedVideoId = searchResult?.items
                    ?.filterIsInstance<SearchItem.Song>()?.firstOrNull()?.song?.videoId
                    ?: searchResult?.items?.filterIsInstance<SearchItem.TopResult>()?.firstOrNull()?.song?.videoId

                if (!matchedVideoId.isNullOrBlank()) {
                    return "youtube" to matchedVideoId
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving YouTube provider for share", e)
        }

        // Fallback to spatialflow internal ID
        return "spatialflow" to song.id.toString()
    }

    fun getFallbackShareUrl(song: SongItem?): String {
        if (song == null) return "https://${SharedSongRepository.SHARE_HOST}"
        val id = song.videoId ?: song.id.toString()
        return "https://${SharedSongRepository.SHARE_HOST}/s/$id"
    }

    suspend fun getOrGenerateShareUrl(song: SongItem): String {
        val cacheKey = "${song.id}_${song.videoId ?: ""}"
        val cachedUrl = shareUrlCache[cacheKey]
        if (cachedUrl != null) return cachedUrl

        val fallbackUrl = getFallbackShareUrl(song)

        return try {
            val (provider, providerId) = withContext(Dispatchers.IO) {
                resolveProviderInfo(song)
            }

            val durationSec = if (song.duration > 0) song.duration / 1000L else null
            val artwork = song.thumbnailUrl
                ?: song.getAlbumArtUri()?.takeIf { it.scheme?.startsWith("http") == true }?.toString()

            val request = CreateShareRequest(
                title = song.title.ifBlank { "Unknown Title" },
                artist = song.artist.ifBlank { "Unknown Artist" },
                album = song.albumName,
                artworkUrl = artwork,
                duration = durationSec,
                provider = provider,
                providerId = providerId
            )

            val result = withContext(Dispatchers.IO) {
                repository.createShare(request)
            }

            var url = fallbackUrl
            result.onSuccess { shareResult ->
                url = shareResult.url
                shareUrlCache.put(cacheKey, url)
            }
            result.onFailure { error ->
                Log.w(TAG, "Failed to create share link: $error, falling back to $fallbackUrl")
            }
            url
        } catch (e: Exception) {
            Log.e(TAG, "Error generating share url", e)
            fallbackUrl
        }
    }

    fun shareToInstagramStory(context: Context, imageUri: android.net.Uri, shareUrl: String) {
        try {
            val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
                setDataAndType(imageUri, "image/png")
                putExtra("interactive_asset_uri", imageUri)
                putExtra("content_url", shareUrl)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Fallback to generic image share if Instagram app is not installed
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Song Story").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Instagram story", e)
            SnackbarController.showMessage("Instagram is not installed")
        }
    }

    fun shareStoryImage(context: Context, imageUri: android.net.Uri, song: SongItem?, shareUrl: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                putExtra(Intent.EXTRA_SUBJECT, "${song?.title} - ${song?.artist}")
                putExtra(Intent.EXTRA_TEXT, "Listen to \"${song?.title}\" by ${song?.artist} on SpatialFlow:\n$shareUrl")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Song Story").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing story image", e)
            SnackbarController.showMessage("Could not share image")
        }
    }

    fun copyShareLink(context: Context, shareUrl: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("SpatialFlow Song Link", shareUrl)
            clipboard?.setPrimaryClip(clip)
            SnackbarController.showMessage("Link copied to clipboard")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying link", e)
        }
    }

    fun shareLink(context: Context, song: SongItem, shareUrl: String) {
        launchShareSheet(context, song, shareUrl)
    }

    private fun launchShareSheet(context: Context, song: SongItem, shareUrl: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "${song.title} - ${song.artist}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Listen to \"${song.title}\" by ${song.artist} on SpatialFlow:\n$shareUrl"
                )
            }
            val chooser = Intent.createChooser(shareIntent, "Share Song").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching share sheet", e)
            SnackbarController.showMessage("Could not open share dialog")
        }
    }
}
