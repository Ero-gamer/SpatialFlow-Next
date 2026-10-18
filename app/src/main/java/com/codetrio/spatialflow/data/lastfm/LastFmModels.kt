package com.codetrio.spatialflow.data.lastfm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LastFmSessionResponse(
    val session: LastFmSession? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmSession(
    val name: String = "",
    val key: String = "",
    val subscriber: Int = 0
)

@Serializable
data class LastFmUserResponse(
    val user: LastFmUser? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmUser(
    val name: String = "",
    val realname: String = "",
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
    val playcount: String = "0",
    val playlists: String = "0"
) {
    fun getAvatarUrl(): String? {
        val extraLarge = image.find { it.size.equals("extralarge", true) }?.text?.takeIf { it.isNotBlank() }
        if (extraLarge != null && !isPlaceholder(extraLarge)) return extraLarge
        val large = image.find { it.size.equals("large", true) }?.text?.takeIf { it.isNotBlank() }
        if (large != null && !isPlaceholder(large)) return large
        val medium = image.find { it.size.equals("medium", true) }?.text?.takeIf { it.isNotBlank() }
        if (medium != null && !isPlaceholder(medium)) return medium
        return image.lastOrNull { it.text.isNotBlank() && !isPlaceholder(it.text) }?.text
    }
}

private fun isPlaceholder(url: String): Boolean = url.contains("2a96cbd8b46e442fc41c2b86b821562f")

@Serializable
data class LastFmImage(
    @SerialName("#text") val text: String = "",
    val size: String = ""
)

@Serializable
data class LastFmNowPlayingResponse(
    val nowplaying: LastFmNowPlayingTrack? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmNowPlayingTrack(
    val artist: LastFmTextElement? = null,
    val track: LastFmTextElement? = null,
    val album: LastFmTextElement? = null,
    val ignoredMessage: LastFmIgnoredMessage? = null
)

@Serializable
data class LastFmTextElement(
    @SerialName("#text") val text: String = "",
    val corrected: String = "0"
)

@Serializable
data class LastFmIgnoredMessage(
    @SerialName("#text") val text: String = "",
    val code: String = "0"
)

@Serializable
data class LastFmScrobbleResponse(
    val scrobbles: LastFmScrobblesContainer? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmScrobblesContainer(
    @SerialName("@attr") val attr: LastFmScrobbleAttr? = null
)

@Serializable
data class LastFmScrobbleAttr(
    val accepted: Int = 0,
    val ignored: Int = 0
)

@Serializable
data class LastFmGenericResponse(
    val status: String = "",
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmRecentTracksResponse(
    val recenttracks: LastFmRecentTracks? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmRecentTracks(
    val track: List<LastFmRecentTrack> = emptyList(),
    @SerialName("@attr") val attr: LastFmRecentTracksAttr? = null
)

@Serializable
data class LastFmRecentTracksAttr(
    val user: String = "",
    val totalPages: String = "1",
    val total: String = "0"
)

@Serializable
data class LastFmRecentTrack(
    val name: String = "",
    val artist: LastFmTrackArtist? = null,
    val album: LastFmTrackAlbum? = null,
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
    val date: LastFmTrackDate? = null,
    @SerialName("@attr") val attr: LastFmNowPlayingAttr? = null,
    var resolvedCoverUrl: String? = null,
    var isLoved: Boolean = false
) {
    val isNowPlaying: Boolean get() = attr?.nowplaying == "true"
    
    fun getImageUrl(): String? {
        if (!resolvedCoverUrl.isNullOrBlank()) return resolvedCoverUrl
        val extraLarge = image.find { it.size.equals("extralarge", true) }?.text?.takeIf { it.isNotBlank() }
        if (extraLarge != null && !isPlaceholder(extraLarge)) return extraLarge
        val large = image.find { it.size.equals("large", true) }?.text?.takeIf { it.isNotBlank() }
        if (large != null && !isPlaceholder(large)) return large
        val medium = image.find { it.size.equals("medium", true) }?.text?.takeIf { it.isNotBlank() }
        if (medium != null && !isPlaceholder(medium)) return medium
        return image.lastOrNull { it.text.isNotBlank() && !isPlaceholder(it.text) }?.text
    }
}

@Serializable
data class LastFmTopTracksResponse(
    val toptracks: LastFmTopTracks? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmTopTracks(
    val track: List<LastFmTopTrack> = emptyList()
)

@Serializable
data class LastFmTopTrack(
    val name: String = "",
    val playcount: String = "0",
    val artist: LastFmTrackArtist? = null,
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
    @SerialName("@attr") val attr: LastFmRankAttr? = null,
    var resolvedCoverUrl: String? = null,
    var isLoved: Boolean = false
) {
    fun getImageUrl(): String? {
        if (!resolvedCoverUrl.isNullOrBlank()) return resolvedCoverUrl
        val extraLarge = image.find { it.size.equals("extralarge", true) }?.text?.takeIf { it.isNotBlank() }
        if (extraLarge != null && !isPlaceholder(extraLarge)) return extraLarge
        val large = image.find { it.size.equals("large", true) }?.text?.takeIf { it.isNotBlank() }
        if (large != null && !isPlaceholder(large)) return large
        val medium = image.find { it.size.equals("medium", true) }?.text?.takeIf { it.isNotBlank() }
        if (medium != null && !isPlaceholder(medium)) return medium
        return image.lastOrNull { it.text.isNotBlank() && !isPlaceholder(it.text) }?.text
    }
}

@Serializable
data class LastFmRankAttr(
    val rank: String = "1"
)

@Serializable
data class LastFmLovedTracksResponse(
    val lovedtracks: LastFmLovedTracks? = null,
    val error: Int? = null,
    val message: String? = null
)

@Serializable
data class LastFmLovedTracks(
    val track: List<LastFmLovedTrack> = emptyList()
)

@Serializable
data class LastFmLovedTrack(
    val name: String = "",
    val artist: LastFmTrackArtist? = null,
    val url: String = "",
    val image: List<LastFmImage> = emptyList(),
    val date: LastFmTrackDate? = null,
    var resolvedCoverUrl: String? = null
) {
    fun getImageUrl(): String? {
        if (!resolvedCoverUrl.isNullOrBlank()) return resolvedCoverUrl
        val extraLarge = image.find { it.size.equals("extralarge", true) }?.text?.takeIf { it.isNotBlank() }
        if (extraLarge != null && !isPlaceholder(extraLarge)) return extraLarge
        val large = image.find { it.size.equals("large", true) }?.text?.takeIf { it.isNotBlank() }
        if (large != null && !isPlaceholder(large)) return large
        val medium = image.find { it.size.equals("medium", true) }?.text?.takeIf { it.isNotBlank() }
        if (medium != null && !isPlaceholder(medium)) return medium
        return image.lastOrNull { it.text.isNotBlank() && !isPlaceholder(it.text) }?.text
    }
}

@Serializable
data class LastFmTrackArtist(
    val name: String = "",
    @SerialName("#text") val text: String = ""
) {
    val displayName: String get() = name.ifBlank { text }
}

@Serializable
data class LastFmTrackAlbum(
    @SerialName("#text") val text: String = ""
)

@Serializable
data class LastFmTrackDate(
    val uts: String = "",
    @SerialName("#text") val text: String = ""
)

@Serializable
data class LastFmNowPlayingAttr(
    val nowplaying: String = ""
)

/**
 * Domain model for a track to be scrobbled or cached.
 */
data class ScrobbleItem(
    val artist: String,
    val title: String,
    val album: String? = null,
    val durationSec: Long = 0L,
    val timestampSec: Long = System.currentTimeMillis() / 1000L
)
