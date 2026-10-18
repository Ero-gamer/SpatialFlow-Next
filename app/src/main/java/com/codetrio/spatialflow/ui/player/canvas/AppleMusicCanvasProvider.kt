package com.codetrio.spatialflow.ui.player.canvas

import android.util.Log
import com.codetrio.spatialflow.data.network.KtorClientProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches Apple Music live animated motion artwork / editorial video loops
 * for Songs, Albums, Artists, and Playlists.
 */
internal object AppleMusicCanvasProvider {

    private const val TAG = "AppleMusicCanvas"

    // Public read-only token extracted from Apple Music Web Player
    private const val APPLE_MUSIC_TOKEN =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ" +
            ".eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzg2NjMyOTI0LCJleHAiOjE3OTI2" +
            "ODA5MjQsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ" +
            ".hBgj61sZf-y7bmuvT-joXAUAcf7TVJ51732xnH5vFkLHOmsQHxVqGMYUuI4h8c0-RX3fRY3moylhLW8fewFJyw"

    private const val AMP_BASE_URL = "https://amp-api.music.apple.com"
    private const val CACHE_TTL_MS = 1000L * 60 * 60 * 24 // 24 Hours

    private val json = KtorClientProvider.defaultJson

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
                register(ContentType.Text.JavaScript, KotlinxSerializationConverter(json))
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 25_000
                socketTimeoutMillis = 25_000
            }
            install(ContentEncoding) {
                gzip()
                deflate()
            }
            expectSuccess = false
        }
    }

    private data class CacheEntry(val value: CanvasArtwork?, val expiresAtMs: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    private fun cacheKey(prefix: String, vararg parts: String): String =
        "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }

    /**
     * Resolves live motion artwork for an Album by album title and artist.
     */
    suspend fun getByAlbumArtist(
        album: String,
        artist: String,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val key = cacheKey("sa", album, artist, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }
        val result = searchAndFetchMotion(album, artist, album, storefront, "albums")
        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        result
    }

    /**
     * Resolves live motion artwork for a Song.
     */
    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val key = cacheKey("song", song, artist, album ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }
        val result = searchAndFetchMotion(song, artist, album, storefront, "songs")
        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        result
    }

    /**
     * Resolves live motion artwork for an Album by Apple Music Album ID.
     */
    suspend fun getByAlbumId(
        albumId: String,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        val key = cacheKey("id", albumId, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }
        val result = fetchMotionArtwork(albumId, storefront, null)
        cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        result
    }

    /**
     * Resolves live motion/hero video loop for an Artist.
     */
    suspend fun getByArtist(
        artistName: String,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext null
        val key = cacheKey("artist", artistName, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        val result = runCatching {
            val searchUrl = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            val response = client.get(searchUrl) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", artistName)
                parameter("types", "artists")
                parameter("limit", "5")
                parameter("extend", "editorialVideo,hero")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null

            val root = response.body<JsonObject>()
            val artists = root["results"]?.jsonObject?.get("artists")?.jsonObject?.get("data")?.jsonArray
                ?: return@runCatching null

            for (elem in artists) {
                val artistObj = elem.jsonObject
                val attributes = artistObj["attributes"]?.jsonObject ?: continue
                val name = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val artistId = artistObj["id"]?.jsonPrimitive?.contentOrNull

                // Check direct editorialVideo or hero
                val ev = attributes["editorialVideo"]?.jsonObject
                    ?: attributes["hero"]?.jsonObject
                var videoUrls = ev?.let { extractEditorialVideoUrls(it) }

                if (videoUrls?.animated.isNullOrBlank() && !artistId.isNullOrBlank()) {
                    // Try direct artist endpoint lookup
                    videoUrls = fetchArtistEditorialVideo(artistId, storefront)
                }

                if (videoUrls != null && (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank())) {
                    val artworkObj = attributes["artwork"]?.jsonObject
                    val artworkTemplate = artworkObj?.get("url")?.jsonPrimitive?.contentOrNull
                    val staticArtworkUrl = artworkTemplate?.replace("{w}", "800")?.replace("{h}", "800")?.replace("{f}", "jpg")
                    val video = videoUrls.animated ?: videoUrls.animatedVertical
                    return@runCatching CanvasArtwork(
                        name = name,
                        artist = name,
                        albumId = artistId,
                        albumName = name,
                        static = staticArtworkUrl,
                        animated = video,
                        animatedVertical = videoUrls.animatedVertical ?: video,
                        videoUrl = video,
                        videoUrlVertical = videoUrls.animatedVertical ?: video
                    )
                }
            }
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "Error fetching artist motion for '$artistName': ${it.message}")
        }.getOrNull()

        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        result
    }

    /**
     * Resolves live motion/animated cover loop for a Playlist (e.g., Apple Music Editorial Playlists).
     */
    suspend fun getByPlaylist(
        playlistTitle: String,
        curator: String? = null,
        storefront: String = "us",
    ): CanvasArtwork? = withContext(Dispatchers.IO) {
        if (playlistTitle.isBlank()) return@withContext null
        val key = cacheKey("playlist", playlistTitle, curator ?: "", storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let { return@withContext it.value }

        val result = runCatching {
            val query = if (!curator.isNullOrBlank() && !playlistTitle.contains(curator, ignoreCase = true)) {
                "$curator $playlistTitle"
            } else playlistTitle

            val searchUrl = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            val response = client.get(searchUrl) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", query)
                parameter("types", "playlists")
                parameter("limit", "8")
                parameter("extend", "editorialVideo")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null

            val root = response.body<JsonObject>()
            val playlists = root["results"]?.jsonObject?.get("playlists")?.jsonObject?.get("data")?.jsonArray
                ?: return@runCatching null

            for (elem in playlists) {
                val playlistObj = elem.jsonObject
                val attributes = playlistObj["attributes"]?.jsonObject ?: continue
                val name = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val curatorName = attributes["curatorName"]?.jsonPrimitive?.contentOrNull ?: curator
                val playlistId = playlistObj["id"]?.jsonPrimitive?.contentOrNull

                val ev = attributes["editorialVideo"]?.jsonObject
                var videoUrls = ev?.let { extractEditorialVideoUrls(it) }

                if (videoUrls?.animated.isNullOrBlank() && !playlistId.isNullOrBlank()) {
                    videoUrls = fetchPlaylistEditorialVideo(playlistId, storefront)
                }

                if (videoUrls != null && (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank())) {
                    val artworkObj = attributes["artwork"]?.jsonObject
                    val artworkTemplate = artworkObj?.get("url")?.jsonPrimitive?.contentOrNull
                    val staticArtworkUrl = artworkTemplate?.replace("{w}", "800")?.replace("{h}", "800")?.replace("{f}", "jpg")
                    val video = videoUrls.animated ?: videoUrls.animatedVertical
                    return@runCatching CanvasArtwork(
                        name = name,
                        artist = curatorName,
                        albumId = playlistId,
                        albumName = name,
                        static = staticArtworkUrl,
                        animated = video,
                        animatedVertical = videoUrls.animatedVertical ?: video,
                        videoUrl = video,
                        videoUrlVertical = videoUrls.animatedVertical ?: video
                    )
                }
            }
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.w(TAG, "Error fetching playlist motion for '$playlistTitle': ${it.message}")
        }.getOrNull()

        if (result != null) cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        result
    }

    private suspend fun fetchArtistEditorialVideo(artistId: String, storefront: String): EditorialVideoUrls? {
        return runCatching {
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/artists/$artistId"
            val response = client.get(url) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo,hero")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null
            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = data["attributes"]?.jsonObject ?: return@runCatching null
            val ev = attributes["editorialVideo"]?.jsonObject ?: attributes["hero"]?.jsonObject ?: return@runCatching null
            extractEditorialVideoUrls(ev)
        }.getOrNull()
    }

    private suspend fun fetchPlaylistEditorialVideo(playlistId: String, storefront: String): EditorialVideoUrls? {
        return runCatching {
            val url = "$AMP_BASE_URL/v1/catalog/$storefront/playlists/$playlistId"
            val response = client.get(url) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null
            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = data["attributes"]?.jsonObject ?: return@runCatching null
            val ev = attributes["editorialVideo"]?.jsonObject ?: return@runCatching null
            extractEditorialVideoUrls(ev)
        }.getOrNull()
    }

    private suspend fun searchAndFetchMotion(
        term: String,
        artist: String,
        album: String?,
        storefront: String,
        type: String,
    ): CanvasArtwork? {
        return runCatching {
            var query = if (term.contains(artist, ignoreCase = true)) term else "$artist $term"
            if (!album.isNullOrBlank() && !query.contains(album, ignoreCase = true)) query = "$query $album"

            val searchUrl = "$AMP_BASE_URL/v1/catalog/$storefront/search"
            val response = client.get(searchUrl) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("term", query)
                parameter("types", type)
                parameter("limit", "10")
                parameter("extend", "editorialVideo")
                parameter("include", "albums")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null

            val root = response.body<JsonObject>()
            val results = root["results"]
                ?.jsonObject
                ?.get(type)
                ?.jsonObject
                ?.get("data")
                ?.jsonArray
                ?: return@runCatching null

            val scoredResults = results
                .mapNotNull { scoreAndFilterItem(it.jsonObject, term, artist, album) }
                .sortedByDescending { it.first }

            for ((score, obj) in scoredResults) {
                if (score < com.codetrio.spatialflow.data.sourcepool.TrackMatching.MIN_MATCH_SCORE) continue

                val attributes = obj["attributes"]?.jsonObject ?: continue
                val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
                val itemType = obj["type"]?.jsonPrimitive?.contentOrNull

                val targetAlbumId = resolveAlbumId(obj, attributes, itemType)
                if (targetAlbumId == null) continue

                val ev = attributes["editorialVideo"]?.jsonObject
                if (ev != null) {
                    val videoUrls = extractEditorialVideoUrls(ev)
                    if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                        val name = attributes["name"]?.jsonPrimitive?.contentOrNull
                        val collName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull
                        val resolvedAlbumName = if (itemType == "songs") collName else name
                        val artworkObj = attributes["artwork"]?.jsonObject
                        val artworkTemplate = artworkObj?.get("url")?.jsonPrimitive?.contentOrNull
                        val staticArtworkUrl = artworkTemplate?.replace("{w}", "800")?.replace("{h}", "800")?.replace("{f}", "jpg")
                        val video = videoUrls.animated ?: videoUrls.animatedVertical
                        return@runCatching CanvasArtwork(
                            name = name,
                            artist = resultArtistName,
                            albumId = targetAlbumId,
                            albumName = resolvedAlbumName,
                            static = staticArtworkUrl,
                            animated = video,
                            animatedVertical = videoUrls.animatedVertical ?: video,
                            videoUrl = video,
                            videoUrlVertical = videoUrls.animatedVertical ?: video
                        )
                    }
                }

                val fetched = fetchMotionArtwork(
                    albumId = targetAlbumId,
                    storefront = storefront,
                    fallbackArtist = resultArtistName,
                    titleOverride = if (itemType == "songs") attributes["name"]?.jsonPrimitive?.contentOrNull else null,
                    artistOverride = if (itemType == "songs") resultArtistName else null,
                )
                if (fetched != null) return@runCatching fetched
            }
            null
        }.onFailure {
            if (it is CancellationException) throw it
            Log.e(TAG, "Error searching motion artwork for $term: ${it.message}")
        }.getOrNull()
    }

    private suspend fun fetchMotionArtwork(
        albumId: String,
        storefront: String,
        fallbackArtist: String?,
        titleOverride: String? = null,
        artistOverride: String? = null,
    ): CanvasArtwork? {
        return runCatching {
            val albumUrl = "$AMP_BASE_URL/v1/catalog/$storefront/albums/$albumId"
            val response = client.get(albumUrl) {
                header("Authorization", "Bearer $APPLE_MUSIC_TOKEN")
                header("Origin", "https://music.apple.com")
                header("Referer", "https://music.apple.com/")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                parameter("extend", "editorialVideo")
                parameter("include", "tracks")
            }
            if (response.status != HttpStatusCode.OK) return@runCatching null

            val root = response.body<JsonObject>()
            val data = root["data"]?.jsonArray
            if (data.isNullOrEmpty()) return@runCatching null

            val albumObj = data.firstOrNull()?.jsonObject ?: return@runCatching null
            val attributes = albumObj["attributes"]?.jsonObject
            val albumName = attributes?.get("name")?.jsonPrimitive?.contentOrNull ?: ""
            val artistName = attributes?.get("artistName")?.jsonPrimitive?.contentOrNull ?: fallbackArtist

            val finalTitle = titleOverride ?: albumName
            val finalArtist = artistOverride ?: artistName

            val ev = attributes?.get("editorialVideo")?.jsonObject
            if (ev != null) {
                val videoUrls = extractEditorialVideoUrls(ev)
                if (!videoUrls.animated.isNullOrBlank() || !videoUrls.animatedVertical.isNullOrBlank()) {
                    val artworkObj = attributes?.get("artwork")?.jsonObject
                    val artworkTemplate = artworkObj?.get("url")?.jsonPrimitive?.contentOrNull
                    val staticArtworkUrl = artworkTemplate?.replace("{w}", "800")?.replace("{h}", "800")?.replace("{f}", "jpg")
                    val video = videoUrls.animated ?: videoUrls.animatedVertical
                    return@runCatching CanvasArtwork(
                        name = finalTitle,
                        artist = finalArtist,
                        albumId = albumId,
                        albumName = albumName,
                        static = staticArtworkUrl,
                        animated = video,
                        animatedVertical = videoUrls.animatedVertical ?: video,
                        videoUrl = video,
                        videoUrlVertical = videoUrls.animatedVertical ?: video
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun scoreAndFilterItem(
        obj: JsonObject,
        term: String,
        artist: String,
        album: String?,
    ): Pair<Int, JsonObject>? {
        val attributes = obj["attributes"]?.jsonObject ?: return null
        val resultName = attributes["name"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultArtistName = attributes["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
        val resultCollectionName = attributes["collectionName"]?.jsonPrimitive?.contentOrNull ?: ""

        val matchScore = com.codetrio.spatialflow.data.sourcepool.TrackMatching.score(
            wantedTitle = term,
            wantedArtists = if (artist.isNotBlank()) listOf(artist) else emptyList(),
            candidateTitle = resultName,
            candidateArtist = resultArtistName,
            wantedDurationMs = null,
            candidateDurationMs = null
        )

        if (matchScore < com.codetrio.spatialflow.data.sourcepool.TrackMatching.MIN_MATCH_SCORE) return null

        var score = matchScore

        if (!album.isNullOrBlank() && resultCollectionName.isNotBlank()) {
            val albumMatch = resultCollectionName.equals(album, ignoreCase = true)
            val albumFuzzy = resultCollectionName.contains(album, ignoreCase = true) ||
                album.contains(resultCollectionName, ignoreCase = true)
            score += when {
                albumMatch -> 20
                albumFuzzy -> 10
                else -> 0
            }
        }

        return score to obj
    }

    private fun resolveAlbumId(
        obj: JsonObject,
        attributes: JsonObject,
        itemType: String?,
    ): String? {
        if (itemType == "albums") return obj["id"]?.jsonPrimitive?.contentOrNull
        if (itemType != "songs") return null

        val relationships = obj["relationships"]?.jsonObject
        var albumId = relationships
            ?.get("albums")
            ?.jsonObject
            ?.get("data")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("id")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: attributes["collectionId"]?.jsonPrimitive?.contentOrNull

        if (albumId == null) {
            val url = attributes["url"]?.jsonPrimitive?.contentOrNull
            if (url != null) {
                val albumPart = url.substringAfter("/album/", "").substringBefore("?")
                val id = albumPart.substringAfterLast("/", "")
                if (id.isNotBlank() && id.all { it.isDigit() }) albumId = id
            }
        }
        return albumId
    }

    private data class EditorialVideoUrls(val animated: String?, val animatedVertical: String?)

    private fun extractEditorialVideoUrls(ev: JsonObject): EditorialVideoUrls {
        fun JsonObject.videoUrl(): String? =
            this["video"]?.jsonPrimitive?.contentOrNull
                ?: this["videoUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["hlsUrl"]?.jsonPrimitive?.contentOrNull
                ?: this["url"]?.jsonPrimitive?.contentOrNull
                ?: this["streamUrl"]?.jsonPrimitive?.contentOrNull

        val raw = ev["motionDetailRaw"]?.jsonObject?.videoUrl()
            ?: ev["motionRaw"]?.jsonObject?.videoUrl()
        val square = ev["motionDetailSquare"]?.jsonObject?.videoUrl()
            ?: ev["motionSquare"]?.jsonObject?.videoUrl()
            ?: ev["motionSquareVideo1x1"]?.jsonObject?.videoUrl()
        val tall = ev["motionDetailTall"]?.jsonObject?.videoUrl()
            ?: ev["motionTall"]?.jsonObject?.videoUrl()
            ?: ev["motionTallVideo3x4"]?.jsonObject?.videoUrl()
            ?: ev["hero"]?.jsonObject?.videoUrl()
        val static = ev["motionDetailStatic"]?.jsonObject?.videoUrl()
        val direct = ev.videoUrl()
        val animated = raw ?: square ?: tall ?: direct ?: static

        return EditorialVideoUrls(animated = animated, animatedVertical = tall ?: animated)
    }
}
