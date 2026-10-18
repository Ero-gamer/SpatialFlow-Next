package com.codetrio.spatialflow.ui.explore

import androidx.compose.runtime.Immutable
import com.codetrio.spatialflow.data.innertube.HomeSection
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchItem
import androidx.compose.ui.graphics.Color
import com.codetrio.spatialflow.data.spotify.SpotifyHomeSection

@Immutable
data class ImmutableHomeSections(val sections: List<HomeSection> = emptyList())

@Immutable
data class ImmutableSearchResults(val items: List<SearchItem> = emptyList())

@Immutable
data class ImmutableSpotifySections(val sections: List<SpotifyHomeSection> = emptyList())

@Immutable
data class ImmutableSongList(val songs: List<OnlineSong> = emptyList())

@Immutable
data class MoodVisuals(
    val gradientColors: List<Color>,
    val imageUrl: String
)

@Immutable
data class GenreUiModel(
    val id: String,
    val title: String,
    val browseId: String,
    val params: String?,
    val imageUrl: String,
    val gradientColors: List<Color>
)

