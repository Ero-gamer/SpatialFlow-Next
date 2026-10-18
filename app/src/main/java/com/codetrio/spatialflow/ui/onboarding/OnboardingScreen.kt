@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow.ui.onboarding
    
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import androidx.compose.ui.graphics.RectangleShape
    
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.graphics.shapes.Morph
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.explore.MorphShape
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun OnboardingScreen(
        onComplete: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(pageCount = { 10 })
        val prefs = remember { context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE) }
        val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
        val hasHaptics = remember { vibrator?.hasVibrator() == true }
        
        var isLoggedIn by remember { mutableStateOf(com.codetrio.spatialflow.data.innertube.AccountManager.isLoggedIn(context)) }
        var userName by remember { mutableStateOf("Connected User") }
        var userProfileUrl by remember { mutableStateOf<String?>(null) }
        
        var isSpotifyLoggedIn by remember { mutableStateOf(com.codetrio.spatialflow.data.spotify.SpotifyPrefs.isConnected(context)) }
        var spotifyUserName by remember { mutableStateOf<String?>(null) }
        var spotifyProfileUrl by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(isLoggedIn) {
            if (isLoggedIn) {
                val result = com.codetrio.spatialflow.data.innertube.YouTubeMusic.accountProfile()
                userName = result.getOrNull()?.name ?: "Connected User"
                userProfileUrl = result.getOrNull()?.avatarUrl
            }
        }
        
        LaunchedEffect(isSpotifyLoggedIn) {
            if (isSpotifyLoggedIn) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.codetrio.spatialflow.data.spotify.SpotifyClient.me().onSuccess { user ->
                        spotifyUserName = user.displayName ?: "Spotify User"
                        spotifyProfileUrl = user.images.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
                            ?: user.images.maxByOrNull { it.width ?: 0 }?.url
                            ?: user.images.firstOrNull()?.url
                    }
                }
            }
        }
        
        val activeProfileUrl = userProfileUrl ?: spotifyProfileUrl
        val activeUserName = if (isLoggedIn) userName else if (isSpotifyLoggedIn) (spotifyUserName ?: "Connected User") else "Connected User"
        
        var hapticsLevel by rememberSaveable { mutableFloatStateOf(prefs.getFloat("vibration_strength", 80f)) }
        var themeMode by rememberSaveable { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
        var hideNavLabels by rememberSaveable { mutableStateOf(prefs.getBoolean("hide_nav_labels", false)) }
        var navStyle by rememberSaveable { mutableStateOf(prefs.getString("nav_style", "docked") ?: "docked") }
        var navBarTheme by rememberSaveable { mutableStateOf(prefs.getString("nav_bar_blur_option", "blur") ?: "blur") }
        var miniPlayerTheme by rememberSaveable { mutableStateOf(prefs.getString("mini_player_theme", "blur") ?: "blur") }
        var selectedGenres by rememberSaveable { mutableStateOf(prefs.getStringSet("preferred_genres", emptySet()) ?: emptySet()) }
    
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        val notifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        val micPermission = Manifest.permission.RECORD_AUDIO
    
        var audioGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) }
        var notifGranted by remember { mutableStateOf(notifPermission?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true) }
        var micGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, micPermission) == PackageManager.PERMISSION_GRANTED) }
        
        val allPermissionsGranted = audioGranted && notifGranted && micGranted
    
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            audioGranted = permissions[audioPermission] ?: audioGranted
            if (notifPermission != null) {
                notifGranted = permissions[notifPermission] ?: notifGranted
            }
            micGranted = permissions[micPermission] ?: micGranted
        }
    
        Scaffold(
            bottomBar = {
                val isNextEnabled = if (pagerState.currentPage == 4) allPermissionsGranted else true
                SetupBottomBar(
                    pagerState = pagerState,
                    isNextEnabled = isNextEnabled,
                    onNextClicked = {
                        if (isNextEnabled && pagerState.currentPage < pagerState.pageCount - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage + 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onBackClicked = {
                        if (pagerState.currentPage > 0) {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = pagerState.currentPage - 1,
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                                )
                            }
                        }
                    },
                    onFinishClicked = {
                        prefs.edit {
                            putBoolean("has_seen_onboarding_1_8", true)
                                .putFloat("vibration_strength", hapticsLevel)
                                .putString("theme_mode", themeMode)
                                .putBoolean("hide_nav_labels", hideNavLabels)
                                .putString("nav_style", navStyle)
                                .putString("nav_bar_blur_option", navBarTheme)
                                .putString("mini_player_theme", miniPlayerTheme)
                                .putStringSet("preferred_genres", selectedGenres)
                        }
                        onComplete()
                    }
                )
            }
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) { page ->
                val pageOffsetProvider = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                }
    
                Box(modifier = Modifier.fillMaxSize()) {
                    when (page) {
                        0 -> WelcomePage(pageOffsetProvider)
                        1 -> EcosystemPage(pageOffsetProvider)
                        2 -> FeatureListPage(pageOffsetProvider)
                        3 -> SignInPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn,
                            isSpotifyLoggedIn = isSpotifyLoggedIn,
                            userName = activeUserName,
                            userProfileUrl = activeProfileUrl,
                            onLoginSuccess = { isLoggedIn = true },
                            onSpotifyLoginSuccess = { isSpotifyLoggedIn = true },
                            onGuestClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(4) 
                                }
                            }
                        )
                        4 -> PermissionsPage(
                            pageOffsetProvider = pageOffsetProvider,
                            audioGranted = audioGranted,
                            notifGranted = notifGranted,
                            micGranted = micGranted,
                            onRequestAudio = { permissionLauncher.launch(arrayOf(audioPermission)) },
                            onRequestNotif = { notifPermission?.let { permissionLauncher.launch(arrayOf(it)) } },
                            onRequestMic = { permissionLauncher.launch(arrayOf(micPermission)) }
                        )
                        5 -> ThemeSelectionPage(
                            pageOffsetProvider = pageOffsetProvider,
                            themeMode = themeMode,
                            onThemeChanged = { 
                                themeMode = it
                                prefs.edit { putString("theme_mode", it) }
                            }
                        )
                        6 -> NavigationStylePage(
                            pageOffsetProvider = pageOffsetProvider,
                            navStyle = navStyle,
                            hideNavLabels = hideNavLabels,
                            onNavStyleChanged = { style ->
                                navStyle = style
                                if (style == "floating") {
                                    hideNavLabels = false
                                    prefs.edit { putBoolean("hide_nav_labels", false) }
                                }
                                prefs.edit { putString("nav_style", style) }
                            },
                            onHideNavLabelsChanged = {
                                if (navStyle != "floating") {
                                    hideNavLabels = it
                                    prefs.edit { putBoolean("hide_nav_labels", it) }
                                }
                            }
                        )
                        7 -> GenreSelectionPage(
                            pageOffsetProvider = pageOffsetProvider,
                            selectedGenres = selectedGenres,
                            onGenreToggled = { genre ->
                                val updated = if (selectedGenres.contains(genre)) {
                                    selectedGenres - genre
                                } else {
                                    selectedGenres + genre
                                }
                                selectedGenres = updated
                                prefs.edit { putStringSet("preferred_genres", updated) }
                            }
                        )
                        8 -> PreferencesPage(
                            pageOffsetProvider = pageOffsetProvider,
                            hapticsLevel = hapticsLevel,
                            onHapticsLevelChanged = {
                                hapticsLevel = it
                                prefs.edit {putFloat("vibration_strength", it) }
                                if (it > 0 && hasHaptics && vibrator != null) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val amplitude = (it / 100f * 255).toInt().coerceIn(1, 255)
                                        vibrator.vibrate(VibrationEffect.createOneShot(50, amplitude))
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(50)
                                    }
                                }
                            }
                        )
                        9 -> FinishPage(
                            pageOffsetProvider = pageOffsetProvider,
                            isLoggedIn = isLoggedIn || isSpotifyLoggedIn,
                            userProfileUrl = activeProfileUrl
                        )
                    }
                }
            }
        }
    }
    
    @SuppressLint("ConfigurationScreenWidthHeight")
    @Composable
    fun ImmersivePageLayout(
        pageOffsetProvider: () -> Float,
        drawableRes: Int?,
        imageUrl: String? = null,
        imageScale: Float = 1f,
        iconTint: Color = MaterialTheme.colorScheme.onSurface,
        content: @Composable () -> Unit
    ) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp
        val horizontalPadding = if (screenWidth < 360.dp) 20.dp else if (screenWidth > 480.dp) 32.dp else 28.dp
        val verticalPadding = if (screenWidth < 360.dp) 16.dp else if (screenWidth > 480.dp) 24.dp else 20.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Image Canvas (takes all remaining space above text)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(
                        horizontal = if (imageUrl != null) 0.dp else 24.dp,
                        vertical = if (imageUrl != null) 0.dp else 24.dp
                    )
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 15f
                        rotationZ = offset * 2f
                        scaleX = imageScale + kotlin.math.abs(offset * 0.08f)
                        scaleY = imageScale + kotlin.math.abs(offset * 0.08f)
                    }
            ) {
                if (imageUrl != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SpatialImage(
                            model = imageUrl,
                            contentDescription = "Background Hero",
                            size = ImageSemanticSize.Hero,
                            shape = RectangleShape,
                            innerBorder = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Top status bar dark scrim for clear status bar visibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.55f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                        // Bottom gradient scrim for 100% clear text visibility
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.4f),
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }
                } else if (drawableRes != null) {
                    Image(
                        painter = painterResource(id = drawableRes),
                        contentDescription = "Background decoration",
                        contentScale = ContentScale.Fit,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint),
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else {
                    // Fallback / Placeholder surface if drawable is missing
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
                }
            }
    
            // Content Area at the bottom
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                content()
            }
        }
    }
    
    @Composable
    fun WelcomePage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_applogo,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Welcome\nto SpatialFlow.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Lets setup the everything for you",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 50f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun EcosystemPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_youtube_music,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Your\nComplete\nEcosystem.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Stream millions of tracks or play your local library, perfectly synced.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -60f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @Composable
    fun FeatureListPage(pageOffsetProvider: () -> Float) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_lyrics,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Stream",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 40f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
                Text(
                    text = "Sing Along.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * 60f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Karaoke lyrics, powerful search, and offline downloads all in one place.",
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * 80f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ThemeSelectionPage(
        pageOffsetProvider: () -> Float,
        themeMode: String,
        onThemeChanged: (String) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_appearance,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Style it\nyour way.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    Triple("system", "System Default", Icons.Default.Settings),
                    Triple("dark", "Dark Mode", Icons.Default.DarkMode),
                    Triple("light", "Light Mode", Icons.Default.LightMode)
                ).forEachIndexed { index, (mode, label, _) ->
                    Card(
                        onClick = { onThemeChanged(mode) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (themeMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * (50f + (index * 40f))
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (themeMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                if (themeMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (themeMode == mode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun NavigationStylePage(
        pageOffsetProvider: () -> Float,
        navStyle: String,
        hideNavLabels: Boolean,
        onNavStyleChanged: (String) -> Unit,
        onHideNavLabelsChanged: (Boolean) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_settings,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Navigate\nSeamlessly.",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer { 
                        val offset = pageOffsetProvider()
                        translationY = offset * -30f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )
        
                Spacer(modifier = Modifier.height(20.dp))
                
                // Real Live Preview Container (Mini Player + Navigation Bar)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * 25f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Real Representation: Live Nav Bar (Floating vs Docked)
                        if (navStyle == "floating") {
                            val spatialNavItems = remember {
                                listOf(
                                    com.codetrio.spatialflow.ui.components.SpatialNavItem("explore", "Explore", R.drawable.ic_explore),
                                    com.codetrio.spatialflow.ui.components.SpatialNavItem("library", "Library", R.drawable.ic_library_music),
                                    com.codetrio.spatialflow.ui.components.SpatialNavItem("effects", "Effects", R.drawable.ic_equalizer),
                                    com.codetrio.spatialflow.ui.components.SpatialNavItem("settings", "Settings", R.drawable.ic_settings)
                                )
                            }
                            var selectedRoute by remember { mutableStateOf("explore") }
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.height(64.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    com.codetrio.spatialflow.ui.components.SpatialFloatingNavBar(
                                        items = spatialNavItems,
                                        currentRoute = selectedRoute,
                                        onItemSelect = { selectedRoute = it },
                                        hideLabels = false
                                    )
                                    com.codetrio.spatialflow.ui.components.SpatialFloatingSearchButton(
                                        onClick = { selectedRoute = "explore" }
                                    )
                                }
                            }
                        } else {
                            var selectedIndex by remember { mutableIntStateOf(0) }
                            val items = listOf(
                                "Explore" to R.drawable.ic_explore,
                                "Library" to R.drawable.ic_library_music,
                                "Effects" to R.drawable.ic_equalizer,
                                "Settings" to R.drawable.ic_settings
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                NavigationBar(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(72.dp),
                                    containerColor = Color.Transparent,
                                    tonalElevation = 0.dp,
                                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0.dp)
                                ) {
                                    items.forEachIndexed { index, (label, iconRes) ->
                                        val showLabel = !hideNavLabels
                                        NavigationBarItem(
                                            selected = index == selectedIndex,
                                            onClick = { selectedIndex = index },
                                            icon = { Icon(painterResource(iconRes), contentDescription = label, modifier = Modifier.size(24.dp)) },
                                            label = if (showLabel) { { Text(label, style = MaterialTheme.typography.labelSmall) } } else null,
                                            alwaysShowLabel = showLabel
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        
                // Docked vs Floating Mode Selector
                Text(
                    text = "Navigation Bar Style",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        onClick = { onNavStyleChanged("docked") },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (navStyle == "docked") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.ViewStream,
                                    contentDescription = null,
                                    tint = if (navStyle == "docked") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Docked",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (navStyle == "docked") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Card(
                        onClick = { onNavStyleChanged("floating") },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (navStyle == "floating") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CropPortrait,
                                    contentDescription = null,
                                    tint = if (navStyle == "floating") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Floating",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (navStyle == "floating") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hide Nav Labels Switch (Automatically disabled when Floating nav is active)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (navStyle == "floating") 0.5f else 1f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hide Nav Labels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (navStyle == "floating") 0.5f else 1f)
                            )
                            if (navStyle == "floating") {
                                Text(
                                    text = "Always disabled in Floating Capsule mode",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Switch(
                            checked = if (navStyle == "floating") false else hideNavLabels,
                            enabled = navStyle != "floating",
                            onCheckedChange = onHideNavLabelsChanged
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    @Composable
    fun PreferencesPage(
        pageOffsetProvider: () -> Float,
        hapticsLevel: Float,
        onHapticsLevelChanged: (Float) -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_haptic,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Sensory\nExperience.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(48.dp))
    
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * 50f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    },
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Music Haptics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Adjust the intensity of beat-synced vibrations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Slider(
                        value = hapticsLevel,
                        onValueChange = onHapticsLevelChanged,
                        valueRange = 0f..100f,
                        steps = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionsPage(
        pageOffsetProvider: () -> Float,
        audioGranted: Boolean,
        notifGranted: Boolean,
        micGranted: Boolean,
        onRequestAudio: () -> Unit,
        onRequestNotif: () -> Unit,
        onRequestMic: () -> Unit
    ) {
        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_folder_open,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = "Enable\nPermissions.",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                lineHeight = 48.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { 
                    val offset = pageOffsetProvider()
                    translationY = offset * -30f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                }
            )
    
            Spacer(modifier = Modifier.height(32.dp))
    
            // Audio Permission Card
            PermissionCard(
                title = "Music Library",
                description = "Access local audio files for offline playback.",
                icon = Icons.Default.LibraryMusic,
                isGranted = audioGranted,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 200f,
                onRequest = onRequestAudio
            )
    
            Spacer(modifier = Modifier.height(12.dp))
    
            // Notification Permission Card
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Playback Notifications",
                    description = "Control music from your lock screen.",
                    icon = Icons.Default.Notifications,
                    isGranted = notifGranted,
                    pageOffsetProvider = pageOffsetProvider,
                    offsetMultiplier = 300f,
                    onRequest = onRequestNotif
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
    
            // Microphone Permission Card
            PermissionCard(
                title = "Audio Engine",
                description = "Required for immersive effects and synced lyrics.",
                icon = Icons.Default.Mic,
                isGranted = micGranted,
                pageOffsetProvider = pageOffsetProvider,
                offsetMultiplier = 400f,
                onRequest = onRequestMic
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
    
    @Composable
    fun PermissionCard(
        title: String,
        description: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isGranted: Boolean,
        pageOffsetProvider: () -> Float,
        offsetMultiplier: Float,
        onRequest: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 0.dp, vertical = 6.dp)
                .graphicsLayer {
                    val offset = pageOffsetProvider()
                    translationY = offset * offsetMultiplier * 0.2f
                    alpha = 1f - kotlin.math.abs(offset * 1.5f)
                },
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = { if (!isGranted) onRequest() }
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else icon,
                        contentDescription = "Permission icon",
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = if (isGranted) 1.1f else 1f
                                scaleY = if (isGranted) 1.1f else 1f
                                alpha = if (isGranted) 1f else 0.8f
                            },
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                if (!isGranted) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Grant $title permission",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    
    @Composable
    fun SignInPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        isSpotifyLoggedIn: Boolean,
        userName: String,
        userProfileUrl: String?,
        onLoginSuccess: () -> Unit,
        onSpotifyLoginSuccess: () -> Unit,
        onGuestClick: () -> Unit
    ) {
        val context = LocalContext.current
        var showWebView by remember { mutableStateOf(false) }
        var showSpotifyWebView by remember { mutableStateOf(false) }

        val spotifyGreen = Color(0xFF1DB954)
        val youtubeRed = Color(0xFFFF0000)

        if (showWebView) {
            val accountViewModel: com.codetrio.spatialflow.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.codetrio.spatialflow.ui.explore.GoogleSignInScreen(
                    accountViewModel = accountViewModel,
                    onSignInSuccess = { 
                        showWebView = false 
                        onLoginSuccess()
                    },
                    onNavigateUp = { showWebView = false },
                    isOnboarding = true
                )
            }
        }

        if (showSpotifyWebView) {
            val spotifyViewModel: com.codetrio.spatialflow.viewmodel.SpotifyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSpotifyWebView = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                com.codetrio.spatialflow.ui.spotify.SpotifySignInScreen(
                    spotifyViewModel = spotifyViewModel,
                    onSignInSuccess = { 
                        showSpotifyWebView = false 
                        onSpotifyLoginSuccess()
                    },
                    onNavigateUp = { showSpotifyWebView = false }
                )
            }
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = if ((isLoggedIn || isSpotifyLoggedIn) && userProfileUrl != null) null else R.drawable.ic_youtube_music,
            imageUrl = if (isLoggedIn || isSpotifyLoggedIn) userProfileUrl else null,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                if (isLoggedIn || isSpotifyLoggedIn) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (isLoggedIn) "Welcome,\n$userName" else "Welcome to\nSpatialFlow",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isLoggedIn) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(percent = 50)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("YouTube Music Connected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (isSpotifyLoggedIn) {
                            Surface(
                                color = spotifyGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(percent = 50)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = spotifyGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Spotify Connected", style = MaterialTheme.typography.labelMedium, color = spotifyGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(80.dp))
                } else {
                    Text(
                        text = "Sign In &\nSync.",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer { 
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )
        
                    Spacer(modifier = Modifier.height(16.dp))
        
                    Text(
                        text = "Connect your YouTube Music or Spotify account to sync playlists, liked songs, and recommendations.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 24.sp,
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -20f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // YouTube Music Button
                    androidx.compose.material3.Button(
                        onClick = { showWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -10f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = youtubeRed, contentColor = Color.White)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with YouTube Music",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Spotify Button
                    androidx.compose.material3.Button(
                        onClick = { showSpotifyWebView = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -8f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = spotifyGreen, contentColor = Color.Black)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_spotify_logo),
                            contentDescription = "Spotify",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Continue with Spotify",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.material3.OutlinedButton(
                        onClick = onGuestClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .graphicsLayer {
                                val offset = pageOffsetProvider()
                                translationY = offset * -5f
                                alpha = 1f - kotlin.math.abs(offset * 1.5f)
                            },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "Continue as Guest",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun GenreSelectionPage(
        pageOffsetProvider: () -> Float,
        selectedGenres: Set<String>,
        onGenreToggled: (String) -> Unit
    ) {
        val vibes = remember {
            listOf(
                VibeItem("Lo-Fi", "Chill & Beats", R.drawable.ic_equalizer, Color(0xFFAB47BC), MaterialShapes.Cookie6Sided),
                VibeItem("Hip-Hop", "Flow & Rhythm", R.drawable.ic_equalizer, Color(0xFFFF7043), MaterialShapes.Slanted),
                VibeItem("Pop", "Chart Toppers", R.drawable.ic_equalizer, Color(0xFFEC407A), MaterialShapes.Cookie9Sided),
                VibeItem("Rock", "High Voltage", R.drawable.ic_equalizer, Color(0xFFEF5350), MaterialShapes.SoftBurst),
                VibeItem("Electronic", "Synth & Bass", R.drawable.ic_equalizer, Color(0xFF26C6DA), MaterialShapes.PixelCircle),
                VibeItem("Bollywood", "Desi Melodies", R.drawable.ic_equalizer, Color(0xFFFFA726), MaterialShapes.Sunny),
                VibeItem("Indie", "Raw & Acoustic", R.drawable.ic_equalizer, Color(0xFF66BB6A), MaterialShapes.Clover4Leaf),
                VibeItem("Chill", "Ambient Oasis", R.drawable.ic_equalizer, Color(0xFF26A69A), MaterialShapes.PuffyDiamond),
                VibeItem("R&B", "Smooth Soul", R.drawable.ic_equalizer, Color(0xFF5C6BC0), MaterialShapes.ClamShell),
                VibeItem("Jazz", "Brass & Grooves", R.drawable.ic_equalizer, Color(0xFFFFCA28), MaterialShapes.Gem)
            )
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = R.drawable.ic_equalizer,
            iconTint = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pick Your\nVibe.",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 38.sp,
                        lineHeight = 44.sp,
                        letterSpacing = (-0.5).sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.graphicsLayer {
                            val offset = pageOffsetProvider()
                            translationY = offset * -30f
                            alpha = 1f - kotlin.math.abs(offset * 1.5f)
                        }
                    )

                    // M3 Expressive Counter Pill Badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selectedGenres.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedGenres.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = if (selectedGenres.isEmpty()) "Select vibes" else "${selectedGenres.size} selected",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedGenres.isNotEmpty()) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Select your favorite genres to personalize your daily mixes and recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 2-Column Grid of Expressive Vibe Cards with Spring Motion Press & Shape Morphing
                val chunkedVibes = remember(vibes) { vibes.chunked(2) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    chunkedVibes.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { vibe ->
                                val isSelected = selectedGenres.contains(vibe.name)
                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val pressScale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.94f else 1.0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    ),
                                    label = "vibePressScale"
                                )
                                val cardShape = if (isSelected) RoundedCornerShape(22.dp) else RoundedCornerShape(16.dp)

                                Surface(
                                    onClick = { onGenreToggled(vibe.name) },
                                    interactionSource = interactionSource,
                                    shape = cardShape,
                                    color = if (isSelected) vibe.accentColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    border = if (isSelected) BorderStroke(2.dp, vibe.accentColor) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            scaleX = pressScale
                                            scaleY = pressScale
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val morph = remember(vibe.targetShape) { Morph(MaterialShapes.Circle, vibe.targetShape) }
                                        val morphProgress by animateFloatAsState(
                                            targetValue = if (isSelected) 1f else 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            label = "vibeIconMorph"
                                        )
                                        val iconShape = MorphShape(morph, morphProgress)

                                        Surface(
                                            shape = iconShape,
                                            color = if (isSelected) vibe.accentColor else vibe.accentColor.copy(alpha = 0.15f),
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    painter = painterResource(id = vibe.iconRes),
                                                    contentDescription = vibe.name,
                                                    tint = if (isSelected) Color.White else vibe.accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = vibe.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = vibe.subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    private data class VibeItem(
        val name: String,
        val subtitle: String,
        val iconRes: Int,
        val accentColor: Color,
        @OptIn(ExperimentalMaterial3ExpressiveApi::class) val targetShape: androidx.graphics.shapes.RoundedPolygon
    )

    @Composable
    fun FinishPage(
        pageOffsetProvider: () -> Float,
        isLoggedIn: Boolean,
        userProfileUrl: String?
    ) {
        val context = LocalContext.current
        val expressiveSocials = remember {
            listOf(
                ExpressiveSocialItem(
                    title = "GitHub",
                    description = "Star SpatialFlow repo and follow releases.",
                    iconRes = R.drawable.ic_github,
                    url = "https://github.com/MythicalSHUB/SpatialFlow",
                    brandColor = Color(0xFFA855F7),
                    containerColor = Color(0xFFA855F7).copy(alpha = 0.12f)
                ),
                ExpressiveSocialItem(
                    title = "Telegram",
                    description = "Join our Telegram channel and community.",
                    iconRes = R.drawable.ic_telegram,
                    url = "https://t.me/SpatialFlow",
                    brandColor = Color(0xFF24A1DE),
                    containerColor = Color(0xFF24A1DE).copy(alpha = 0.12f)
                ),
                ExpressiveSocialItem(
                    title = "Ko-Fi",
                    description = "Support SpatialFlow development on Ko-Fi.",
                    iconRes = R.drawable.ic_kofi,
                    url = "https://ko-fi.com/mythicalshub",
                    brandColor = Color(0xFFFF5E5B),
                    containerColor = Color(0xFFFF5E5B).copy(alpha = 0.12f)
                ),
                ExpressiveSocialItem(
                    title = "Instagram",
                    description = "Follow development & updates on Instagram.",
                    iconRes = R.drawable.ic_instagram,
                    url = "https://instagram.com/mythicalshub",
                    brandColor = Color(0xFFE4405F),
                    containerColor = Color(0xFFE4405F).copy(alpha = 0.12f)
                ),
                ExpressiveSocialItem(
                    title = "YouTube",
                    description = "Watch updates and spatial audio demos.",
                    iconRes = R.drawable.ic_youtube,
                    url = "https://youtube.com/@8dmusic_s",
                    brandColor = Color(0xFFFF0000),
                    containerColor = Color(0xFFFF0000).copy(alpha = 0.12f)
                )
            )
        }

        ImmersivePageLayout(
            pageOffsetProvider = pageOffsetProvider,
            drawableRes = null,
            imageUrl = "https://github.com/MythicalSHUB.png"
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Stay connected",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Normal
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.graphicsLayer {
                        val offset = pageOffsetProvider()
                        translationY = offset * -20f
                        alpha = 1f - kotlin.math.abs(offset * 1.5f)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Follow development, report issues, or support SpatialFlow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Segmented Material 3 Expressive List with Tinted Colors
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    expressiveSocials.forEachIndexed { index, item ->
                        val itemShape = when (index) {
                            0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                            expressiveSocials.lastIndex -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                            else -> RoundedCornerShape(6.dp)
                        }

                        Surface(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.url))
                                context.startActivity(intent)
                            },
                            shape = itemShape,
                            color = item.containerColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = item.brandColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = item.iconRes),
                                            contentDescription = item.title,
                                            tint = item.brandColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = item.brandColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    private data class ExpressiveSocialItem(
        val title: String,
        val description: String,
        val iconRes: Int,
        val url: String,
        val brandColor: Color,
        val containerColor: Color
    )
    
    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun SetupBottomBar(
        modifier: Modifier = Modifier,
        pagerState: PagerState,
        isNextEnabled: Boolean = true,
        onNextClicked: () -> Unit,
        onBackClicked: () -> Unit,
        onFinishClicked: () -> Unit
    ) {
        val morphAnimationSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
        val rotationAnimationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)
    
        val targetShapeValues = when (pagerState.currentPage % 3) {
            0 -> listOf(50f, 50f, 50f, 50f)
            1 -> listOf(26f, 26f, 26f, 26f)
            else -> listOf(18f, 50f, 18f, 50f)
        }
    
        val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphAnimationSpec, label = "TopStart")
        val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphAnimationSpec, label = "TopEnd")
        val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphAnimationSpec, label = "BottomStart")
        val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphAnimationSpec, label = "BottomEnd")
    
        val animatedRotation by animateFloatAsState(
            targetValue = pagerState.currentPage * 360f,
            animationSpec = rotationAnimationSpec,
            label = "Rotation"
        )
    
        val shape = RoundedCornerShape(
            topEnd = 38.dp,
            topStart = 38.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp
        )
    
        Surface(
            modifier = modifier.shadow(elevation = 8.dp, shape = shape, clip = true),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = shape
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
            ) {
                val pageProgress = ((pagerState.currentPage + pagerState.currentPageOffsetFraction + 1f) / pagerState.pageCount).coerceIn(0f, 1f)
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { pageProgress },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedContent(
                        targetState = pagerState.currentPage,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp),
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { height -> height } + fadeIn()).togetherWith(slideOutVertically { height -> -height } + fadeOut())
                            } else {
                                (slideInVertically { height -> -height } + fadeIn()).togetherWith(slideOutVertically { height -> height } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        label = "StepTextAnimation"
                    ) { targetPage ->
                        if (targetPage == 0) {
                            Text(
                                text = "Let's Go!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.IconButton(
                                    onClick = onBackClicked,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Step $targetPage of ${pagerState.pageCount - 1}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
    
                    val isLastPage = pagerState.currentPage == pagerState.pageCount - 1
                    val fabAlpha by animateFloatAsState(if (isNextEnabled || isLastPage) 1f else 0.4f, label = "fab_alpha")
                    val fabEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

                    AnimatedContent(
                        targetState = isLastPage,
                        label = "FabOrExtended",
                        transitionSpec = {
                            fadeIn(animationSpec = fabEffectsSpec) togetherWith fadeOut(animationSpec = fabEffectsSpec)
                        }
                    ) { isFinish ->
                        if (isFinish) {
                            androidx.compose.material3.ExtendedFloatingActionButton(
                                onClick = onFinishClicked,
                                text = { Text("Start Listening") },
                                icon = { Icon(Icons.Default.Check, contentDescription = "Finish") },
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                elevation = FloatingActionButtonDefaults.elevation(0.dp)
                            )
                        } else {
                            FloatingActionButton(
                                onClick = { if (isNextEnabled) onNextClicked() },
                                shape = RoundedCornerShape(
                                    topStartPercent = animatedTopStart.toInt(),
                                    topEndPercent = animatedTopEnd.toInt(),
                                    bottomStartPercent = animatedBottomStart.toInt(),
                                    bottomEndPercent = animatedBottomEnd.toInt()
                                ),
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .rotate(animatedRotation)
                                    .graphicsLayer { alpha = fabAlpha }
                            ) {
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = "Next",
                                    modifier = Modifier.rotate(-animatedRotation)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
