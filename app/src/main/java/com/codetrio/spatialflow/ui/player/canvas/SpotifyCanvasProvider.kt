package com.codetrio.spatialflow.ui.player.canvas

import android.util.Log
import com.codetrio.spatialflow.data.spotify.SpotifyClient
import com.codetrio.spatialflow.data.network.KtorClientProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream

/**
 * Fetches official looping Spotify Canvas MP4 videos using Spotify Canvaz service
 * (Protobuf-encoded EntityCanvazRequest) and the Kouzu MLC Canvas Mirror API.
 */
internal object SpotifyCanvasProvider {

    private const val TAG = "SpotifyCanvasProvider"
    private const val CANVAS_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"

    private val json = KtorClientProvider.defaultJson
    private val httpClient = KtorClientProvider.httpClient

    /**
     * Resolves Spotify Canvas by track ID (e.g., "4cOdK2wGLETKBW3PvgPWqT" or "spotify:track:4cOdK2wGLETKBW3PvgPWqT").
     */
    suspend fun getByTrackId(trackId: String): CanvasArtwork? = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("spotify:track:").trim()
        if (cleanId.isBlank()) return@withContext null

        val token = SpotifyClient.getEffectiveToken()
        if (token.isNullOrBlank()) {
            Log.d(TAG, "No Spotify token available for track $cleanId, skipping direct Spotify Canvas")
            return@withContext null
        }

        // 1. Official Spotify Canvaz Client using Protobuf encoding
        val protoBytes = buildEntityCanvazRequest(cleanId)
        try {
            val response = httpClient.post(CANVAS_URL) {
                header("Authorization", "Bearer $token")
                header("Content-Type", "application/x-protobuf")
                header("Accept", "application/x-protobuf, application/protobuf, */*")
                header("User-Agent", "Spotify/8.8.84.518 Android/34")
                setBody(ByteArrayContent(protoBytes, ContentType.parse("application/x-protobuf")))
            }

            if (response.status == HttpStatusCode.OK) {
                val responseBytes = response.body<ByteArray>()
                val parsed = parseCanvazProtobufResponse(responseBytes)
                if (parsed?.videoUrl != null) {
                    Log.d(TAG, "Successfully resolved Spotify Canvas video from protobuf for track $cleanId (${parsed.videoUrl})")
                    return@withContext CanvasArtwork(
                        name = null,
                        artist = null,
                        static = parsed.staticUrl,
                        animated = parsed.videoUrl,
                        animatedVertical = parsed.videoUrl,
                        videoUrl = parsed.videoUrl,
                        videoUrlVertical = parsed.videoUrl
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Official Spotify Canvas protobuf query failed for $cleanId: ${e.message}")
        }

        // 2. Fallback: JSON query against canvaz-cache
        try {
            val requestBodyJson = """{"tracks":[{"track_uri":"spotify:track:$cleanId"}]}"""
            val jsonResponse = httpClient.post(CANVAS_URL) {
                header("Authorization", "Bearer $token")
                header("User-Agent", "Spotify/8.8.84.518 Android/34")
                setBody(TextContent(requestBodyJson, ContentType.Application.Json))
            }
            if (jsonResponse.status == HttpStatusCode.OK) {
                val text = jsonResponse.bodyAsText()
                val parsedJson = json.parseToJsonElement(text).jsonObject
                val canvases = parsedJson["canvases"]?.jsonArray
                if (canvases != null && canvases.isNotEmpty()) {
                    val canvasObj = canvases[0].jsonObject
                    val videoUrl = canvasObj["url"]?.jsonPrimitive?.content
                    val staticUrl = canvasObj["canvas_image_url"]?.jsonPrimitive?.content
                        ?: canvasObj["image_url"]?.jsonPrimitive?.content
                    if (!videoUrl.isNullOrBlank()) {
                        Log.d(TAG, "Successfully resolved Spotify Canvas video from JSON for track $cleanId ($videoUrl)")
                        return@withContext CanvasArtwork(
                            name = null,
                            artist = null,
                            static = staticUrl,
                            animated = videoUrl,
                            animatedVertical = videoUrl,
                            videoUrl = videoUrl,
                            videoUrlVertical = videoUrl
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Official Spotify Canvas JSON fallback failed for $cleanId: ${e.message}")
        }

        return@withContext null
    }

    /**
     * Resolves Spotify Canvas by song title and artist.
     */
    suspend fun getBySongArtist(song: String, artist: String, videoId: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        // 1. Direct Spotify track ID lookup if videoId looks like a Spotify ID
        if (!videoId.isNullOrBlank() && (videoId.startsWith("spotify:track:") || (videoId.length == 22 && videoId.all { it.isLetterOrDigit() }))) {
            val canvasFromVideoId = getByTrackId(videoId)
            if (canvasFromVideoId != null) return@withContext canvasFromVideoId.copy(name = song, artist = artist)
        }

        val cleanSong = song
            .replace(Regex("""\s*[\(\[][^()\[\]]*(feat|ft|featuring|with|official|remaster|audio|video|lyrics|version|hd|slowed|speed|reverb|nightcore|edit|remix|mix)[^()\[\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
        val cleanSongNoHash = cleanSong.replace("#", "").replace(Regex("\\s+"), " ").trim()
        val songNoHash = song.replace("#", "").replace(Regex("\\s+"), " ").trim()

        // 2. Search Spotify for track ID -> lookup canvas using official Canvaz API
        val searchQueries = linkedSetOf(
            "$cleanSong $artist",
            "$cleanSongNoHash $artist",
            "$song $artist",
            "$songNoHash $artist",
            "$artist $cleanSongNoHash",
            "$artist $cleanSong"
        ).filter { it.isNotBlank() }

        for (query in searchQueries) {
            try {
                val trackId = SpotifyClient.searchTrackId(
                    query = query,
                    expectedTitle = cleanSong,
                    expectedArtist = artist
                )
                if (!trackId.isNullOrBlank()) {
                    val canvas = getByTrackId(trackId)
                    if (canvas != null) {
                        return@withContext canvas.copy(name = song, artist = artist)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error searching Spotify track for query '$query': ${e.message}")
            }
        }

        return@withContext null
    }

    /**
     * Resolves Spotify Canvas / Header for an Artist.
     */
    suspend fun getArtistCanvas(artistName: String, artistId: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return@withContext null

        // Direct search for artist track to retrieve canvas visual
        val trackId = SpotifyClient.searchTrackId(cleanArtist)
        if (!trackId.isNullOrBlank()) {
            val canvas = getByTrackId(trackId)
            if (canvas != null) {
                return@withContext canvas.copy(name = cleanArtist, artist = cleanArtist)
            }
        }
        null
    }

    /**
     * Resolves Spotify Canvas for a Playlist.
     */
    suspend fun getPlaylistCanvas(playlistName: String, playlistId: String? = null): CanvasArtwork? = withContext(Dispatchers.IO) {
        val cleanPlaylist = playlistName.trim()
        if (cleanPlaylist.isBlank()) return@withContext null

        val trackId = SpotifyClient.searchTrackId(cleanPlaylist)
        if (!trackId.isNullOrBlank()) {
            val canvas = getByTrackId(trackId)
            if (canvas != null) {
                return@withContext canvas.copy(name = cleanPlaylist, artist = null)
            }
        }
        null
    }

    // ── Protobuf Helpers ────────────────────────────────────────────────────────

    private fun buildEntityCanvazRequest(trackId: String): ByteArray {
        val trackUri = if (trackId.startsWith("spotify:track:")) trackId else "spotify:track:$trackId"
        val uriBytes = trackUri.toByteArray(Charsets.UTF_8)

        // Entity message: field 1 (string entity_uri) -> tag = (1 << 3) | 2 = 0x0A
        val entityBos = ByteArrayOutputStream()
        entityBos.write(0x0A)
        writeVarint(entityBos, uriBytes.size)
        entityBos.write(uriBytes)
        val entityBytes = entityBos.toByteArray()

        // EntityCanvazRequest message: field 1 (repeated Entity entities) -> tag = (1 << 3) | 2 = 0x0A
        val reqBos = ByteArrayOutputStream()
        reqBos.write(0x0A)
        writeVarint(reqBos, entityBytes.size)
        reqBos.write(entityBytes)
        return reqBos.toByteArray()
    }

    private fun writeVarint(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while ((v and 0x7F.inv()) != 0) {
            out.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        out.write(v and 0x7F)
    }

    private data class ParsedCanvas(val videoUrl: String?, val staticUrl: String?)

    private fun parseCanvazProtobufResponse(bytes: ByteArray): ParsedCanvas? {
        if (bytes.isEmpty()) return null
        var videoUrl: String? = null
        var staticUrl: String? = null

        try {
            var i = 0
            while (i < bytes.size) {
                val tagVarint = readVarint(bytes, i)
                val tag = tagVarint.first
                i = tagVarint.second
                if (i > bytes.size) break
                val wireType = tag and 0x07

                when (wireType) {
                    0 -> { // Varint
                        val v = readVarint(bytes, i)
                        i = v.second
                    }
                    1 -> { // 64-bit
                        i += 8
                    }
                    2 -> { // Length-delimited
                        val lenVarint = readVarint(bytes, i)
                        val len = lenVarint.first
                        i = lenVarint.second
                        if (len in 1..(bytes.size - i)) {
                            val chunk = bytes.copyOfRange(i, i + len)
                            val str = runCatching { String(chunk, Charsets.UTF_8) }.getOrNull()
                            if (str != null && str.startsWith("http")) {
                                if (str.contains("canvaz.scdn.co") || str.contains(".mp4") || str.contains("video")) {
                                    if (videoUrl == null) videoUrl = str
                                } else if (str.contains("image") || str.contains("i.scdn.co") || str.contains(".jpg") || str.contains(".png")) {
                                    if (staticUrl == null) staticUrl = str
                                }
                            } else {
                                // Try recursively parsing nested message
                                val nested = parseCanvazProtobufResponse(chunk)
                                if (nested?.videoUrl != null && videoUrl == null) videoUrl = nested.videoUrl
                                if (nested?.staticUrl != null && staticUrl == null) staticUrl = nested.staticUrl
                            }
                            i += len
                        } else {
                            break
                        }
                    }
                    5 -> { // 32-bit
                        i += 4
                    }
                    else -> break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Protobuf parsing error: ${e.message}")
        }

        return if (videoUrl != null || staticUrl != null) ParsedCanvas(videoUrl, staticUrl) else null
    }

    private fun readVarint(bytes: ByteArray, startIndex: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var idx = startIndex
        while (idx < bytes.size) {
            val b = bytes[idx].toInt()
            idx++
            result = result or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 32) break
        }
        return Pair(result, idx)
    }
}
