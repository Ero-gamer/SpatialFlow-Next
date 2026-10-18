@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import android.os.Build
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import com.codetrio.spatialflow.ui.theme.observeKey
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.account.AppIconPickerBottomSheet
import androidx.compose.material.icons.rounded.GraphicEq
import com.codetrio.spatialflow.ui.player.NavStyleBottomSheet
import com.codetrio.spatialflow.ui.player.PlayerThemeBottomSheet
import com.codetrio.spatialflow.ui.player.SeekBarStyleBottomSheet
import com.codetrio.spatialflow.util.AppIconManager
import com.codetrio.spatialflow.util.rememberSupportedHighestFps
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem

@Composable
fun AppearanceScreen(
    navController: NavController,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    amoledBlack: Boolean,
    onAmoledBlackChange: (Boolean) -> Unit,
    dynamicAlbumTheme: Boolean,
    onDynamicAlbumThemeChange: (Boolean) -> Unit,
    navStyle: String,
    onNavStyleChange: (String) -> Unit,
    navBarBlurOption: String,
    onNavBarBlurOptionChange: (String) -> Unit,
    slideNavOnScroll: Boolean,
    onSlideNavOnScrollChange: (Boolean) -> Unit,
    dynamicNavStyle: Boolean,
    onDynamicNavStyleChange: (Boolean) -> Unit,
    hideNavLabels: Boolean,
    onHideNavLabelsChange: (Boolean) -> Unit,
    navigationBlur: Boolean,
    onNavigationBlurChange: (Boolean) -> Unit,
    tabSwitchBlur: Boolean,
    onTabSwitchBlurChange: (Boolean) -> Unit,
    forceHighRefreshRate: Boolean,
    onForceHighRefreshRateChange: (Boolean) -> Unit,
    playerTheme: String,
    onPlayerThemeChange: (String) -> Unit,
    miniPlayerTheme: String,
    onMiniPlayerThemeChange: (String) -> Unit,
    seekBarStyle: String = "apple",
    onSeekBarStyleChange: (String) -> Unit = {},
    allInOneLiquidGlass: Boolean = false,
    onAllInOneLiquidGlassChange: (Boolean) -> Unit = {},
    onSolidStateSelect: () -> Unit = {},
    showAnimatedArt: Boolean,
    onShowAnimatedArtChange: (Boolean) -> Unit,
    canvasProviderOrder: List<String> = listOf("spotify", "apple_music"),
    onCanvasProviderOrderChange: (List<String>) -> Unit = {}
) {
    SettingsScaffold(
        title = "Appearance",
        onBack = { navController.popBackStack() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 120.dp)
        ) {
            // ── Section 1: Theme & Colors ──
            SettingsHeader("Theme & Colors")
            SettingsGroupCard(buildList {
                add { ThemeRow(darkMode, onDarkModeChange) }
                if (darkMode) {
                    add { AmoledBlackRow(amoledBlack, onAmoledBlackChange) }
                }
                add { DynamicAlbumThemeRow(dynamicAlbumTheme, onDynamicAlbumThemeChange) }
                add { AppIconRow() }
            })

            // ── Section 2: Glass & Visual Effects ──
            SettingsHeader("Glass & Visual Effects")
            SettingsGroupCard(buildList {
                add {
                    AllInOneLiquidGlassRow(
                        checked = allInOneLiquidGlass,
                        onToggle = onAllInOneLiquidGlassChange
                    )
                }
                add {
                    FrostedGlassBlurRow(
                        checked = navigationBlur,
                        onToggle = onNavigationBlurChange
                    )
                }
                add {
                    SolidStateRow(
                        active = !allInOneLiquidGlass && !navigationBlur,
                        onClick = onSolidStateSelect
                    )
                }
                if (navigationBlur) {
                    add {
                        TabSwitchBlurRow(
                            checked = tabSwitchBlur,
                            onToggle = onTabSwitchBlurChange
                        )
                    }
                }
            })

            // ── Section 3: Now Playing Screen ──
            SettingsHeader("Now Playing Screen")
            SettingsGroupCard(buildList {
                add {
                    var showThemeSheet by remember { mutableStateOf(false) }
                    val themeText = when (playerTheme) {
                        "fluid" -> "Fluid Animation (Apple Fluid)"
                        "static" -> "Static Album Colors"
                        else -> "Fluid Animation (Apple Fluid)"
                    }
                    ListItem(
                        onClick = { showThemeSheet = true },
                        content = {
                            Column {
                                Text(
                                    text = "Player Theme",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = themeText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showThemeSheet) {
                        PlayerThemeBottomSheet(
                            onDismissRequest = { showThemeSheet = false },
                            currentTheme = playerTheme,
                            onThemeSelect = { selectedTheme ->
                                onPlayerThemeChange(selectedTheme)
                            }
                        )
                    }
                }
                add {
                    var showSeekBarStyleSheet by remember { mutableStateOf(false) }
                    val seekBarStyleText = when (seekBarStyle) {
                        "apple", "apple_music" -> "Apple Music (Expand on Scrub)"
                        "wavy" -> "Wavy (Expressive Wave)"
                        "classic", "line" -> "Classic Line"
                        "liquid", "spatialwave" -> "Expressive Waves (Fluid Fill)"
                        else -> "Apple Music (Expand on Scrub)"
                    }
                    ListItem(
                        onClick = { showSeekBarStyleSheet = true },
                        content = {
                            Column {
                                Text(
                                    text = "Seek Bar Style",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = seekBarStyleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showSeekBarStyleSheet) {
                        SeekBarStyleBottomSheet(
                            onDismissRequest = { showSeekBarStyleSheet = false },
                            currentStyle = seekBarStyle,
                            onStyleSelect = { selectedStyle ->
                                onSeekBarStyleChange(selectedStyle)
                            }
                        )
                    }
                }
                add {
                    ListItem(
                        headlineContent = { Text("Animated Album Art", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("Show looping video canvas on player screen if available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = { Switch(checked = showAnimatedArt, onCheckedChange = onShowAnimatedArtChange) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onShowAnimatedArtChange(!showAnimatedArt) }
                    )
                }
                if (showAnimatedArt) {
                    add {
                        CanvasPriorityReorderList(
                            order = canvasProviderOrder,
                            onOrderChange = onCanvasProviderOrderChange
                        )
                    }
                }
                add {
                    var showTransitionDialog by remember { mutableStateOf(false) }
                    val context = LocalContext.current
                    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
                    val transitionState = prefs.observeKey("artwork_transition", "shuffle")
                    val transition = transitionState.value
                    val subtitle = when (transition) {
                        "flip" -> "Flip (3D Card Flip)"
                        "push" -> "Push (Parallax Slide)"
                        "crossfade" -> "Crossfade (Scale Settle)"
                        "slide" -> "Slide (Horizontal Pager)"
                        else -> "Shuffle (3D Card)"
                    }

                    ListItem(
                        onClick = { showTransitionDialog = true },
                        content = {
                            Column {
                                Text(
                                    text = "Album Art Transition",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Layers,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    if (showTransitionDialog) {
                        val options = listOf(
                            "Shuffle (3D Card Deal)" to "shuffle",
                            "Flip (3D Card Flip)" to "flip",
                            "Push (Parallax Slide)" to "push",
                            "Crossfade (Scale Settle)" to "crossfade",
                            "Slide (Horizontal Pager)" to "slide"
                        )
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showTransitionDialog = false },
                            title = { Text("Album Art Transition") },
                            text = {
                                Column {
                                    options.forEach { (label, value) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    prefs.edit().putString("artwork_transition", value).apply()
                                                    showTransitionDialog = false
                                                }
                                                .padding(vertical = 12.dp, horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = (transition == value || (transition.isBlank() && value == "shuffle")),
                                                onClick = {
                                                    prefs.edit().putString("artwork_transition", value).apply()
                                                    showTransitionDialog = false
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = { showTransitionDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
                add {
                    val context = LocalContext.current
                    val prefs = remember(context) { context.getSharedPreferences("AppSettings", android.content.Context.MODE_PRIVATE) }
                    val filmGrainEnabled by prefs.observeKey("lyrics_film_grain_enabled", true)

                    ListItem(
                        headlineContent = { Text("Lyrics Background Film Grain", style = MaterialTheme.typography.bodyLarge) },
                        supportingContent = { Text("Apple Music-style analog texture that eliminates gradient banding", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Opacity,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = filmGrainEnabled,
                                onCheckedChange = { prefs.edit().putBoolean("lyrics_film_grain_enabled", it).apply() }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { prefs.edit().putBoolean("lyrics_film_grain_enabled", !filmGrainEnabled).apply() }
                    )
                }
            })

            // ── Section 4: Navigation Bar ──
            SettingsHeader("Navigation Bar")
            SettingsGroupCard(buildList {
                add {
                    var showNavStyleSheet by remember { mutableStateOf(false) }
                    val navStyleText = when (navStyle) {
                        "floating" -> "Floating Expressive Bar"
                        "docked" -> "Docked Navigation Bar"
                        else -> "Docked Navigation Bar"
                    }
                    ListItem(
                        onClick = { showNavStyleSheet = true },
                        content = {
                            Column {
                                Text(
                                    text = "Navigation Bar Style",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = navStyleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (showNavStyleSheet) {
                        NavStyleBottomSheet(
                            onDismissRequest = { showNavStyleSheet = false },
                            currentStyle = navStyle,
                            onStyleSelect = { selectedStyle ->
                                onNavStyleChange(selectedStyle)
                            }
                        )
                    }
                }
                add { SlideNavOnScrollRow(checked = slideNavOnScroll, onToggle = onSlideNavOnScrollChange) }
                if (navStyle == "docked") {
                    add {
                        ListItem(
                            onClick = { onDynamicNavStyleChange(!dynamicNavStyle) },
                            content = {
                                Column {
                                    Text(
                                        text = "Expressive Docked Nav Bar",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = "Compact height with bold, elevated icons",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingContent = {
                                Switch(
                                    checked = dynamicNavStyle,
                                    onCheckedChange = onDynamicNavStyleChange
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
                add { HideNavLabelsRow(checked = if (navStyle == "floating") false else hideNavLabels, enabled = navStyle != "floating", onToggle = onHideNavLabelsChange) }
            })

            // ── Section 5: Display & Performance ──
            SettingsHeader("Display & Performance")
            SettingsGroupCard(buildList {
                add { HighRefreshRateRow(forceHighRefreshRate, onForceHighRefreshRateChange) }
            })

            // ── Section 6: Library & Layout ──
            SettingsHeader("Library & Layout")
            SettingsGroupCard(buildList {
                add { DefaultLibraryTabRow() }
                add { ExploreSectionPriorityRow() }
            })
        }
    }
}

@Composable
fun CanvasPriorityReorderList(
    order: List<String>,
    onOrderChange: (List<String>) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var list by remember(order) {
        val filtered = order.filter { it in listOf("spotify", "apple_music") }
        val completeList = (filtered + listOf("spotify", "apple_music")).distinct()
        mutableStateOf(completeList)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Canvas Resolution Priority",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Drag to reorder",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        ReorderableColumn(
            list = list,
            onSettle = { fromIndex: Int, toIndex: Int ->
                val updated = list.toMutableList()
                val moved = updated.removeAt(fromIndex)
                updated.add(toIndex, moved)
                list = updated
                onOrderChange(updated)
            },
            modifier = Modifier.fillMaxWidth()
        ) { index, providerId, isDragging ->
            val (title, subtitle) = when (providerId) {
                "spotify" -> "Spotify Canvas" to "Official Spotify Canvas video loops"
                "apple_music" -> "Apple Music" to "Apple Music animated album artwork"
                else -> providerId to ""
            }

            ReorderableItem {
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 6.dp else 0.dp,
                    label = "canvas_drag_elevation"
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDragging) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
                    },
                    shadowElevation = elevation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "#${index + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder priority",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeRow(isDark: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!isDark) },
        content = {
            Text(
                text = stringResource(R.string.setting_dark_mode),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        trailingContent = { ThemeSwitch(isDark, onToggle) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun ThemeSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) togetherWith
                        fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                },
                label = "theme_icon"
            ) { dark ->
                Icon(
                    imageVector = if (!dark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        }
    )
}

@Composable
fun HideNavLabelsRow(checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { if (enabled) onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Hide Navigation Labels",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = if (!enabled) "Always disabled in Floating Navbar mode" else "Remove text labels from the bottom navigation bar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SlideNavOnScrollRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Slide Navigation on Scroll",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Slide navigation bar out of view when scrolling down",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun DynamicAlbumThemeRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Dynamic Album Theme",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Match app colors to current album artwork",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AmoledBlackRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    ListItem(
        onClick = { onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Pure Black (AMOLED)",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Use pure black for dark theme surfaces to save battery on OLED displays",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun HighRefreshRateRow(checked: Boolean, onToggle: (Boolean) -> Unit) {
    val supportedHighestFps = rememberSupportedHighestFps()
    val isHighRefreshRateSupported = supportedHighestFps > 60.5f

    ListItem(
        onClick = { if (isHighRefreshRateSupported) onToggle(!checked) },
        content = {
            Column {
                Text(
                    text = "Force high refresh rate",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = if (isHighRefreshRateSupported) "Max supported: ${supportedHighestFps.roundToInt()} Hz" else "Not supported on this device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = isHighRefreshRateSupported
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AppIconRow() {
    var showAppIconSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentAlias = remember(showAppIconSheet) { AppIconManager.getCurrentIconAlias(context) }
    val currentIconOption = remember(currentAlias) {
        AppIconManager.availableIcons.firstOrNull { it.aliasName == currentAlias } ?: AppIconManager.availableIcons.first()
    }

    ListItem(
        onClick = { showAppIconSheet = true },
        content = {
            Column {
                Text(
                    text = "App Icon",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = currentIconOption.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                painter = painterResource(id = currentIconOption.previewResId),
                contentDescription = "App Icon",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showAppIconSheet) {
        AppIconPickerBottomSheet(
            onDismissRequest = { showAppIconSheet = false }
        )
    }
}

@Composable
fun AllInOneLiquidGlassRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ListItem(
        onClick = {
            if (isBlurSupported) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!checked)
            }
        },
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Liquid Glass Optics",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (checked && isBlurSupported) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Apply real-time translucent liquid glass refraction across Navigation Bar and Mini Player",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isBlurSupported) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Requires Android 12 or above. Real-time liquid glass blur is not supported on this Android version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.WaterDrop,
                contentDescription = null,
                tint = if (checked && isBlurSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isBlurSupported) 1f else 0.5f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked && isBlurSupported,
                enabled = isBlurSupported,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun FrostedGlassBlurRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isBlurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    ListItem(
        onClick = {
            if (isBlurSupported) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!checked)
            }
        },
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Frosted Glass Blur (Haze)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (checked && isBlurSupported) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Real-time frosted blur behind Navigation Bar and Mini Player",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isBlurSupported) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Requires Android 12 or above. Real-time frosted blur is not supported on this Android version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Opacity,
                contentDescription = null,
                tint = if (checked && isBlurSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isBlurSupported) 1f else 0.5f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked && isBlurSupported,
                enabled = isBlurSupported,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SolidStateRow(
    active: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Solid State Surfaces",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (active) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Opaque album-harmonized surface across Navigation Bar and Mini Player (Maximum performance & zero blur)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun TabSwitchBlurRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    ListItem(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onToggle(!checked)
        },
        content = {
            Column {
                Text(
                    text = "Tab Switching Blur",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Blur background when switching main navigation tabs (GPU-intensive)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Rounded.Opacity,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle(it)
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

