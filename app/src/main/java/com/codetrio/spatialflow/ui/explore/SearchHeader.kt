@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.explore

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.SearchFilter
import com.codetrio.spatialflow.data.innertube.SearchItem
import com.codetrio.spatialflow.data.innertube.UserProfile
import com.codetrio.spatialflow.data.spotify.SpotifyPrefs

private val SearchFilterChips = listOf(
    null to "All",
    SearchFilter.SONGS to "Songs",
    SearchFilter.ALBUMS to "Albums",
    SearchFilter.ARTISTS to "Artists",
    SearchFilter.PLAYLISTS to "Playlists"
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchHeader(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    isSearchActive: Boolean,
    searchListState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    accountHistory: List<OnlineSong>,
    onAccountHistorySongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    searchResults: List<SearchItem>,
    searchHistory: List<String>,
    onClearSearchHistory: () -> Unit,
    onRemoveFromSearchHistory: (String) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    suggestions: List<String>,
    isLoadingSuggestions: Boolean,
    onSuggestionClick: (String) -> Unit,
    onAccountVisibleChange: (Boolean) -> Unit,
    userProfile: UserProfile?,
    homeMoods: List<String>,
    currentMood: String?,
    onMoodClick: (String?) -> Unit,
    currentFilter: SearchFilter?,
    onFilterClick: (SearchFilter?) -> Unit,
    isLandscape: Boolean,
    onClearSearch: () -> Unit,
    onCategoryClick: (String, String, String?) -> Unit = { _, _, _ -> },
    onGenresClick: () -> Unit = {}
) {
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onQueryChange(spokenText)
                onSearch(spokenText)
            }
        }
    }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val horizontalPad by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 16.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "SearchHorizontalPad"
    )
    val verticalPad by animateDpAsState(
        targetValue = if (isSearchActive) 0.dp else 6.dp,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "SearchVerticalPad"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .padding(
                top = if (isSearchActive) 0.dp else statusBarPadding.calculateTopPadding(),
                bottom = verticalPad,
                start = horizontalPad,
                end = horizontalPad
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onQueryChange,
                        onSearch = onSearch,
                        expanded = isSearchActive,
                        onExpandedChange = onSearchActiveChange,
                        modifier = Modifier.focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                "Search Music",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            when {
                                isSearchActive -> IconButton(onClick = {
                                    onSearchActiveChange(false); onClearSearch()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                searchQuery.isNotBlank() -> IconButton(onClick = { onClearSearch() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                else -> Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingIcon = {
                            when {
                                searchQuery.isNotBlank() -> IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                !isSearchActive -> {
                                    val avatarSize = 32.dp
                                    val activeAvatarUrl = if (!userProfile?.avatarUrl.isNullOrEmpty()) {
                                        userProfile.avatarUrl
                                    } else {
                                        SpotifyPrefs.getAccountAvatarUrl(LocalContext.current)
                                    }

                                    IconButton(onClick = { onAccountVisibleChange(true) }) {
                                        if (!activeAvatarUrl.isNullOrEmpty()) {
                                            SpatialImage(
                                                model = activeAvatarUrl,
                                                contentDescription = "Account",
                                                size = ImageSemanticSize.AvatarSmall,
                                                shape = CircleShape,
                                                modifier = Modifier.size(avatarSize),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = "Account",
                                                modifier = Modifier.size(avatarSize),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                isSearchActive -> {
                                    IconButton(
                                        onClick = {
                                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                                            }
                                            try {
                                                voiceLauncher.launch(intent)
                                            } catch (e: Exception) {
                                                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Voice search is not supported on this device")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                },
                expanded = isSearchActive,
                onExpandedChange = onSearchActiveChange,
                modifier = Modifier.fillMaxWidth(),
                windowInsets = if (isSearchActive) SearchBarDefaults.windowInsets else WindowInsets(0, 0, 0, 0),
                colors = SearchBarDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    dividerColor = Color.Transparent
                )
            ) {
                LazyColumn(
                    state = searchListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (searchQuery.isBlank()) {
                        item(key = "search_categories_grid") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Categories",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CategoryCard(
                                        title = "New Releases",
                                        icon = Icons.Default.MusicNote,
                                        onClick = { onCategoryClick("New Releases", "FEmusic_new_releases_albums", null) },
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CategoryCard(
                                        title = "Charts",
                                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                                        onClick = { onCategoryClick("Charts", "FEmusic_charts", "ggMGCgQIgAQ%3D") },
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CategoryCard(
                                        title = "Moods & Genres",
                                        icon = Icons.Default.Favorite,
                                        onClick = onGenresClick,
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        iconTint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    CategoryCard(
                                        title = "Podcasts",
                                        icon = Icons.Default.Mic,
                                        onClick = { onCategoryClick("Podcasts", "FEmusic_podcasts", null) },
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        iconContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        if (searchHistory.isNotEmpty()) {
                            item(key = "recent_header") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Recent Searches",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = onClearSearchHistory) { Text("Clear all") }
                                }
                            }
                            itemsIndexed(searchHistory, key = { idx, historyItem -> "hist_${idx}_${historyItem}" }) { _, historyItem ->
                                ListItem(
                                    headlineContent = { Text(historyItem) },
                                    leadingContent = { Icon(Icons.Default.History, null) },
                                    trailingContent = {
                                        IconButton(onClick = { onRemoveFromSearchHistory(historyItem) }) {
                                            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { onHistoryItemClick(historyItem) }
                                )
                            }
                        }

                        if (userProfile != null && accountHistory.isNotEmpty()) {
                            item(key = "yt_history_header") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Recently Played (YouTube Music)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            itemsIndexed(
                                items = accountHistory,
                                key = { idx, song -> "yt_${idx}_${song.videoId}" }
                            ) { idx, song ->
                                ListItem(
                                    headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        SpatialImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = null,
                                            size = ImageSemanticSize.ThumbnailSmall,
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.size(40.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable {
                                        onSearchActiveChange(false)
                                        onAccountHistorySongClick(song, accountHistory, idx)
                                    }
                                )
                            }
                        }
                    } else {
                        item(key = "suggestions_animated_content") {
                            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                            AnimatedContent(
                                targetState = Pair(isLoadingSuggestions, suggestions),
                                transitionSpec = {
                                    fadeIn(animationSpec = effectsSpec) togetherWith fadeOut(animationSpec = effectsSpec)
                                },
                                label = "suggestions_animation",
                                modifier = Modifier.fillMaxWidth()
                            ) { (loading, sugs) ->
                                Column {
                                    if (loading && sugs.isEmpty()) {
                                        UnifiedShimmerProvider {
                                            Column {
                                                repeat(5) {
                                                    SuggestionSkeletonLoader()
                                                }
                                            }
                                        }
                                    } else {
                                        sugs.forEach { suggestion ->
                                            ListItem(
                                                headlineContent = { Text(suggestion) },
                                                leadingContent = { Icon(Icons.Default.Search, null) },
                                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                                modifier = Modifier.clickable { onSuggestionClick(suggestion) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isSearchActive && searchQuery.isBlank() && searchResults.isEmpty() && homeMoods.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(homeMoods, key = { idx, label -> "mood_${idx}_$label" }) { _, label ->
                    val selected = currentMood == label
                    FilterChip(
                        selected = selected,
                        onClick = { onMoodClick(if (selected) null else label) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        shape = RoundedCornerShape(8.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }

        if (!isSearchActive && searchQuery.isNotBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(SearchFilterChips, key = { idx, (_, label) -> "filter_${idx}_$label" }) { _, (filter, label) ->
                    val selected = currentFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterClick(filter) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        shape = RoundedCornerShape(8.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

@Composable
fun SuggestionSkeletonLoader(modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            ShimmerModifier(width = 240.dp, height = 20.dp, shape = RoundedCornerShape(4.dp))
        },
        leadingContent = {
            ShimmerModifier(width = 28.dp, height = 28.dp, shape = CircleShape)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
    )
}
