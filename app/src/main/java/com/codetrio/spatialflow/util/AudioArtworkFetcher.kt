package com.codetrio.spatialflow.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.map.Mapper
import coil.request.Options
import coil.size.Dimension
import com.codetrio.spatialflow.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom Coil 2 Fetcher and Keyer for loading embedded album artwork directly from audio files
 * and MediaStore audio content URIs (content://media/external/audio/media).
 *
 * Prevents cross-song artwork bleed caused by deprecated MediaStore albumart/albumId conflation.
 */
class AudioArtworkFetcher(
    private val context: Context,
    private val data: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val bitmap = extractArtworkBitmap(context, data, options) ?: return@withContext null
        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (isAudioUri(data, context)) {
                return AudioArtworkFetcher(context, data, options)
            }
            return null
        }
    }

    class Keyer : coil.key.Keyer<Uri> {
        override fun key(data: Uri, options: Options): String? {
            if (isAudioUri(data)) {
                val w = (options.size.width as? Dimension.Pixels)?.px
                val h = (options.size.height as? Dimension.Pixels)?.px
                return if (w != null && h != null) "audio_art:${data}_${w}x${h}" else "audio_art:${data}"
            }
            return null
        }
    }

    class SongItemMapper : Mapper<SongItem, Any> {
        override fun map(data: SongItem, options: Options): Any {
            val thumb = data.thumbnailUrl
            if (!thumb.isNullOrBlank() && (thumb.startsWith("http://") || thumb.startsWith("https://"))) {
                return thumb
            }
            return data.getAlbumArtUri() ?: data.contentUri
        }
    }

    class SongItemKeyer : coil.key.Keyer<SongItem> {
        override fun key(data: SongItem, options: Options): String? {
            val thumb = data.thumbnailUrl
            if (!thumb.isNullOrBlank() && (thumb.startsWith("http://") || thumb.startsWith("https://"))) {
                return thumb
            }
            val w = (options.size.width as? Dimension.Pixels)?.px
            val h = (options.size.height as? Dimension.Pixels)?.px
            val sizeTag = if (w != null && h != null) "_${w}x${h}" else ""
            return "song_art_${data.id}_${data.path?.hashCode() ?: 0}$sizeTag"
        }
    }

    companion object {
        private const val TAG = "AudioArtworkFetcher"

        fun isAudioUri(uri: Uri, context: Context? = null): Boolean {
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme == "content") {
                val path = uri.path?.lowercase() ?: ""
                val authority = uri.authority?.lowercase() ?: ""
                if ((authority == "media" || authority.contains("mediaprovider")) && path.contains("/audio/media/")) {
                    return true
                }
                if (context != null) {
                    try {
                        val mime = context.contentResolver.getType(uri)
                        if (mime?.startsWith("audio/") == true) return true
                    } catch (_: Exception) {}
                }
            } else if (scheme == "file") {
                val path = uri.path?.lowercase() ?: ""
                if (path.endsWith(".mp3") || path.endsWith(".flac") || path.endsWith(".m4a") ||
                    path.endsWith(".opus") || path.endsWith(".ogg") || path.endsWith(".wav") ||
                    path.endsWith(".aac") || path.endsWith(".wma")) {
                    return true
                }
            }
            return false
        }

        fun extractArtworkBitmap(
            context: Context,
            song: SongItem,
            options: Options? = null,
            targetWidth: Int = 512,
            targetHeight: Int = 512
        ): Bitmap? {
            val uri = song.getAlbumArtUri() ?: song.contentUri
            return extractArtworkBitmap(context, uri, options, targetWidth, targetHeight)
        }

        fun extractArtworkBitmap(
            context: Context,
            uri: Uri,
            options: Options? = null,
            targetWidth: Int = 512,
            targetHeight: Int = 512
        ): Bitmap? {
            val w = options?.size?.width?.let { (it as? Dimension.Pixels)?.px } ?: targetWidth
            val h = options?.size?.height?.let { (it as? Dimension.Pixels)?.px } ?: targetHeight

            // Tier 1: On Android 10+ (API 29+), loadThumbnail on the song's specific content URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    return context.contentResolver.loadThumbnail(uri, Size(w, h), null)
                } catch (_: Exception) {
                    // Fallback to MediaMetadataRetriever
                }
            }

            // Tier 2: Extract embedded ID3 / Vorbis / MP4 cover directly from the audio file
            val mmr = MediaMetadataRetriever()
            try {
                if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        mmr.setDataSource(pfd.fileDescriptor)
                    }
                } else if (uri.scheme == "file") {
                    val filePath = uri.path
                    if (filePath != null && File(filePath).exists()) {
                        mmr.setDataSource(filePath)
                    }
                }

                val picture = mmr.embeddedPicture
                if (picture != null && picture.isNotEmpty()) {
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(picture, 0, picture.size, boundsOpts)
                    val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, w, h)
                    val decodeOpts = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    return BitmapFactory.decodeByteArray(picture, 0, picture.size, decodeOpts)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Embedded artwork extraction non-critical skip: ${e.message}")
            } finally {
                try {
                    mmr.release()
                } catch (_: Exception) {}
            }

            // Tier 3: Verified Genuine Album Fallback (Strictly prohibited for generic/unknown albums)
            if (uri.scheme == "content") {
                val verifiedAlbumId = getVerifiedAlbumId(context, uri)
                if (verifiedAlbumId != null && verifiedAlbumId > 0) {
                    try {
                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"),
                            verifiedAlbumId
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            return context.contentResolver.loadThumbnail(albumArtUri, Size(w, h), null)
                        } else {
                            context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                                return BitmapFactory.decodeStream(stream)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Tier 4: No artwork exists for this track. Return null so Coil cleanly displays placeholder.
            return null
        }

        private fun getVerifiedAlbumId(context: Context, audioContentUri: Uri): Long? {
            try {
                val proj = arrayOf(
                    MediaStore.Audio.Media.ALBUM_ID,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST
                )
                context.contentResolver.query(audioContentUri, proj, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val albumCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                        val artistCol = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                        val albumIdCol = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)

                        val album = if (albumCol != -1) c.getString(albumCol)?.trim() else null
                        val artist = if (artistCol != -1) c.getString(artistCol)?.trim() else null

                        // If album is generic or unknown, REJECT fallback to prevent cross-song cover bleeding
                        if (isGenericAlbumOrArtist(album, artist)) {
                            return null
                        }

                        if (albumIdCol != -1) {
                            return c.getLong(albumIdCol)
                        }
                    }
                }
            } catch (_: Exception) {}
            return null
        }

        private fun isGenericAlbumOrArtist(album: String?, artist: String?): Boolean {
            if (album.isNullOrBlank()) return true
            val lowerAlbum = album.lowercase()
            if (lowerAlbum == "<unknown>" || lowerAlbum == "unknown" ||
                lowerAlbum == "download" || lowerAlbum == "downloads" ||
                lowerAlbum == "music" || lowerAlbum == "audio" ||
                lowerAlbum == "internal storage" || lowerAlbum == "sd card" ||
                lowerAlbum == "telegram" || lowerAlbum == "whatsapp audio" ||
                lowerAlbum == "media") {
                return true
            }

            if (artist.isNullOrBlank()) return true
            val lowerArtist = artist.lowercase()
            if (lowerArtist == "<unknown>" || lowerArtist == "unknown" || lowerArtist == "various artists") {
                return true
            }

            return false
        }

        private fun calculateInSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
            var inSampleSize = 1
            if (rawH > reqH || rawW > reqW) {
                val halfH = rawH / 2
                val halfW = rawW / 2
                while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize.coerceAtLeast(1)
        }
    }
}
