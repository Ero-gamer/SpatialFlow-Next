@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.explore

import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import com.codetrio.spatialflow.ui.components.LiquidGlassActionPill
import com.codetrio.spatialflow.ui.components.LiquidGlassPillIconButton
import com.codetrio.spatialflow.ui.components.rememberThrottledBackdrop
import com.codetrio.spatialflow.ui.components.throttledLayerBackdrop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.account.AppIconPickerBottomSheet
import com.codetrio.spatialflow.data.innertube.AccountManager
import com.codetrio.spatialflow.data.innertube.OnlineSong
import com.codetrio.spatialflow.data.innertube.UserProfile
import com.codetrio.spatialflow.data.spotify.SpotifyPrefs
import com.codetrio.spatialflow.ui.spotify.SpotifyManualTokenBottomSheet
import com.codetrio.spatialflow.viewmodel.AccountViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyAuthState
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated account portal presenting library details, YouTube Music & Spotify accounts with avatars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    spotifyViewModel: SpotifyViewModel? = null,
    onBack: () -> Unit,
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToSpotifySignIn: () -> Unit = {}
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (AccountManager.isLoggedIn(context)) {
            viewModel.refreshAll()
        }
    }
    
    val surfaceColor = MaterialTheme.colorScheme.surface
    val accountBackdrop = rememberThrottledBackdrop(surfaceColor)
    val topBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 68.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .throttledLayerBackdrop(accountBackdrop)
        ) {
            AccountSettingsSection(
                viewModel = viewModel,
                spotifyViewModel = spotifyViewModel,
                userProfile = userProfile,
                history = history,
                onSongClick = onSongClick,
                onClose = onBack,
                onNavigateToSignIn = onNavigateToSignIn,
                onNavigateToSpotifySignIn = onNavigateToSpotifySignIn,
                contentTopPadding = topBarPadding
            )
        }

        // Floating Liquid Glass Action Pill Header (Sibling on top)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassActionPill(backdrop = accountBackdrop) {
                LiquidGlassPillIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountSettingsSection(
    viewModel: AccountViewModel,
    spotifyViewModel: SpotifyViewModel? = null,
    userProfile: UserProfile?,
    history: List<OnlineSong> = emptyList(),
    onSongClick: (OnlineSong, List<OnlineSong>, Int) -> Unit = { _, _, _ -> },
    onClose: () -> Unit,
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToSpotifySignIn: () -> Unit = {},
    contentTopPadding: androidx.compose.ui.unit.Dp = 16.dp
) {
    val context = LocalContext.current
    val isYtLoggedIn = remember(userProfile) { AccountManager.isLoggedIn(context) }
    val ytAccountName = userProfile?.name ?: AccountManager.getAccountName(context)
    val token = remember { AccountManager.getAuthCookie(context) ?: "No persistent credentials detected" }

    // Spotify State
    val spotifyAuthState by (spotifyViewModel?.authState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(SpotifyAuthState.NOT_CONNECTED) })
    val spotifyAccountNameState by (spotifyViewModel?.accountName?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") })
    val spotifyAccountAvatarUrlState by (spotifyViewModel?.accountAvatarUrl?.collectAsStateWithLifecycle() ?: remember { mutableStateOf<String?>(null) })

    val isSpotifyConnected = spotifyAuthState == SpotifyAuthState.CONNECTED || SpotifyPrefs.isConnected(context)
    val spotifyName = spotifyAccountNameState.ifEmpty { SpotifyPrefs.getAccountName(context).ifEmpty { "Spotify User" } }
    val spotifyAvatarUrl = spotifyAccountAvatarUrlState ?: SpotifyPrefs.getAccountAvatarUrl(context)

    var syncEnabled by remember { mutableStateOf(AccountManager.isSyncEnabled(context)) }
    var showTokenAlert by remember { mutableStateOf(false) }
    var showAppIconPicker by remember { mutableStateOf(false) }
    var showManualYtSheet by remember { mutableStateOf(false) }
    var showManualSpotifySheet by remember { mutableStateOf(false) }

    val youtubeRed = Color(0xFFFF0000)
    val spotifyGreen = Color(0xFF1DB954)

    var extractedColor by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(userProfile?.avatarUrl) {
        val url = userProfile?.avatarUrl
        if (!url.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(url.resizeCDN(120))
                        .size(120)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val palette = Palette.from(bitmap).generate()
                            val vibrantColor = palette.getVibrantColor(0)
                            val dominantColor = palette.getDominantColor(0)
                            val extractedInt = if (vibrantColor != 0) vibrantColor else dominantColor
                            if (extractedInt != 0) {
                                extractedColor = Color(extractedInt)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AccountColor", "Failed to extract color: ${e.message}")
                }
            }
        } else {
            extractedColor = null
        }
    }

    val backdropColor = extractedColor ?: MaterialTheme.colorScheme.primaryContainer
    val animatedBackdropColor by animateColorAsState(
        targetValue = backdropColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "backdrop_color"
    )

    val avatarMorphState = rememberExpressiveShapeMorph(
        segmentDurationMillis = 1000,
        rotationDurationMillis = 12000
    )
    val avatarMorphShape = avatarMorphState.morphShape

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = contentTopPadding, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Avatar Hero
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Expressive shape morphing backdrop BEHIND the avatar
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(avatarMorphShape)
                            .background(animatedBackdropColor.copy(alpha = 0.5f))
                    )

                    // Hero circular avatar floating on top
                    Box(
                        modifier = Modifier
                            .size(145.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isYtLoggedIn && !userProfile?.avatarUrl.isNullOrEmpty()) {
                            SpatialImage(
                                model = userProfile.avatarUrl,
                                contentDescription = "Profile Picture",
                                size = ImageSemanticSize.ThumbnailLarge,
                                shape = CircleShape,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isSpotifyConnected && !spotifyAvatarUrl.isNullOrEmpty()) {
                            SpatialImage(
                                model = spotifyAvatarUrl,
                                contentDescription = "Spotify Picture",
                                size = ImageSemanticSize.ThumbnailLarge,
                                shape = CircleShape,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                Icons.Default.PersonOutline,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isYtLoggedIn) ytAccountName else if (isSpotifyConnected) spotifyName else "Guest Mode",
                    style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isYtLoggedIn && isSpotifyConnected) "YouTube Music & Spotify Connected"
                           else if (isYtLoggedIn) "YouTube Music Connected"
                           else if (isSpotifyConnected) "Spotify Connected"
                           else "Link your streaming accounts to sync library",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Connected Accounts Section using Material 3 Expressive ListItem Segmented Shapes
        item {
            Column {
                Text(
                    text = "Connected Accounts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    // YouTube Music ListItem
                    val ytCardBg = if (isYtLoggedIn) youtubeRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer
                    ListItem(
                        onClick = {
                            if (!isYtLoggedIn) onNavigateToSignIn()
                        },
                        leadingContent = {
                            if (isYtLoggedIn && !userProfile?.avatarUrl.isNullOrEmpty()) {
                                SpatialImage(
                                    model = userProfile.avatarUrl,
                                    contentDescription = "YT Avatar",
                                    size = ImageSemanticSize.AvatarSmall,
                                    shape = CircleShape,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = youtubeRed,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_youtube_music),
                                            contentDescription = "YouTube Music",
                                            tint = Color.White,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            if (isYtLoggedIn) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val clipboardManager = LocalClipboardManager.current
                                    IconButton(
                                        onClick = {
                                            val ytCookie = AccountManager.getAuthCookie(context) ?: ""
                                            if (ytCookie.isNotEmpty()) {
                                                clipboardManager.setText(AnnotatedString(ytCookie))
                                                Toast.makeText(context, "YouTube Music cookie copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy YouTube Cookie",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.logout() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PowerSettingsNew,
                                            contentDescription = "Disconnect YouTube",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { showManualYtSheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = "Manual Cookie Login",
                                            tint = youtubeRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Button(
                                        onClick = onNavigateToSignIn,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = youtubeRed, contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                        colors = ListItemDefaults.colors(containerColor = ytCardBg),
                        supportingContent = {
                            Text(
                                text = if (isYtLoggedIn) ytAccountName else "Tap to sign in to YouTube Music",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("YouTube Music", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isYtLoggedIn) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = if (isYtLoggedIn) "Connected" else "Not Linked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isYtLoggedIn) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Spotify ListItem
                    val spotifyCardBg = if (isSpotifyConnected) spotifyGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainer
                    ListItem(
                        onClick = {
                            if (!isSpotifyConnected) onNavigateToSpotifySignIn()
                        },
                        leadingContent = {
                            if (isSpotifyConnected && !spotifyAvatarUrl.isNullOrEmpty()) {
                                SpatialImage(
                                    model = spotifyAvatarUrl,
                                    contentDescription = "Spotify Avatar",
                                    size = ImageSemanticSize.AvatarSmall,
                                    shape = CircleShape,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = spotifyGreen,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_spotify_logo),
                                            contentDescription = "Spotify Logo",
                                            tint = Color.Black,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        },
                        trailingContent = {
                            if (isSpotifyConnected) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val clipboardManager = LocalClipboardManager.current
                                    IconButton(
                                        onClick = {
                                            val spDc = SpotifyPrefs.getSpDc(context)
                                            val spToken = SpotifyPrefs.getAccessToken(context)
                                            val copyText = if (spDc.isNotEmpty()) "sp_dc=$spDc" else spToken
                                            if (copyText.isNotEmpty()) {
                                                clipboardManager.setText(AnnotatedString(copyText))
                                                Toast.makeText(context, "Spotify token copied to clipboard", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy Spotify Token",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            spotifyViewModel?.logout() ?: SpotifyPrefs.clearAll(context)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.PowerSettingsNew,
                                            contentDescription = "Disconnect Spotify",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { showManualSpotifySheet = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = "Manual Token Login",
                                            tint = spotifyGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Button(
                                        onClick = onNavigateToSpotifySignIn,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = spotifyGreen, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Connect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                        colors = ListItemDefaults.colors(containerColor = spotifyCardBg),
                        supportingContent = {
                            Text(
                                text = if (isSpotifyConnected) spotifyName else "Tap to sign in to Spotify",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Spotify", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSpotifyConnected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = if (isSpotifyConnected) "Connected" else "Not Linked",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSpotifyConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Recent Listening History", 
                        style = MaterialTheme.typography.titleLarge, 
                        fontWeight = FontWeight.Bold, 
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        this.itemsIndexed(
                            items = history,
                            key = { index, song -> "acct_history_${index}_${song.videoId}" }
                        ) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSongClick(song, history, index) }
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                                        SpatialImage(
                                            model = song.thumbnailUrl,
                                            contentDescription = song.title,
                                            size = ImageSemanticSize.ThumbnailMedium,
                                            shape = RoundedCornerShape(12.dp),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ListItem(
                    onClick = {
                        val next = !syncEnabled
                        syncEnabled = next
                        viewModel.toggleSync(next)
                    },
                    leadingContent = { Icon(Icons.Default.CloudSync, null) },
                    trailingContent = {
                        Switch(checked = syncEnabled, onCheckedChange = null)
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Sync Playlists", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                            Text("Auto-update library states with remote servers", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )

                ListItem(
                    onClick = { showTokenAlert = true },
                    leadingContent = { Icon(Icons.Default.VpnKey, null) },
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    content = {
                        Column {
                            Text("Diagnostics Tool", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                            Text("Inspect background security context", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
    }

    if (showTokenAlert) {
        AlertDialog(
            onDismissRequest = { showTokenAlert = false },
            title = { Text("Authentication Signature") },
            text = { Text(token, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall) },
            confirmButton = {
                TextButton(onClick = { showTokenAlert = false }) { Text("Done") }
            }
        )
    }

    if (showManualYtSheet) {
        YouTubeManualCookieBottomSheet(
            onDismissRequest = { showManualYtSheet = false },
            accountViewModel = viewModel,
            onLoginSuccess = { showManualYtSheet = false }
        )
    }

    if (showManualSpotifySheet && spotifyViewModel != null) {
        SpotifyManualTokenBottomSheet(
            onDismissRequest = { showManualSpotifySheet = false },
            spotifyViewModel = spotifyViewModel,
            onLoginSuccess = { showManualSpotifySheet = false }
        )
    }
}
