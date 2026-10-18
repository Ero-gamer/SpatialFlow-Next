@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@file:Suppress("DEPRECATION")

package com.codetrio.spatialflow

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.codetrio.spatialflow.model.SongItem
import com.codetrio.spatialflow.service.AudioPlaybackService
import com.codetrio.spatialflow.ui.EffectsScreenEntryPoint
import com.codetrio.spatialflow.ui.PlayerBottomSheetCompose
import com.codetrio.spatialflow.ui.TagEditorScreenEntryPoint
import com.codetrio.spatialflow.ui.components.CrashReportBottomSheet
import com.codetrio.spatialflow.ui.components.TelegramJoinDialog
import com.codetrio.spatialflow.ui.explore.ExploreScreen
import com.codetrio.spatialflow.ui.explore.GoogleSignInScreen
import com.codetrio.spatialflow.data.repository.SharedSongRepository
import com.codetrio.spatialflow.data.repository.SharedSongMetadata
import com.codetrio.spatialflow.domain.error.DataError
import com.codetrio.spatialflow.domain.error.onFailure
import com.codetrio.spatialflow.domain.error.onSuccess
import com.codetrio.spatialflow.player.queue.YouTubeQueue
import com.codetrio.spatialflow.util.DeepLinkManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.net.toUri
import android.view.MotionEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.codetrio.spatialflow.ui.animation.BackGestureTracker
import com.codetrio.spatialflow.ui.animation.BackSwipeEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.codetrio.spatialflow.ui.animation.LocalNavController
import com.codetrio.spatialflow.ui.animation.ScreenWrapper
import com.codetrio.spatialflow.ui.animation.aospSharedAxisEnter
import com.codetrio.spatialflow.ui.animation.aospSharedAxisExit
import com.codetrio.spatialflow.ui.animation.aospSharedAxisPopEnter
import com.codetrio.spatialflow.ui.animation.aospSharedAxisPopExit
import com.codetrio.spatialflow.ui.animation.mainRootDirection
import com.codetrio.spatialflow.ui.animation.mainRootEnterTransition
import com.codetrio.spatialflow.ui.animation.mainRootExitTransition
import com.codetrio.spatialflow.ui.library.LibraryScreen
import com.codetrio.spatialflow.ui.onboarding.OnboardingScreen
import com.codetrio.spatialflow.ui.settingsGraph
import com.codetrio.spatialflow.ui.spotify.SpotifyPlaylistScreen
import com.codetrio.spatialflow.ui.spotify.SpotifySignInScreen
import com.codetrio.spatialflow.ui.theme.LocalAppBackdrop
import com.codetrio.spatialflow.ui.theme.LocalAppHazeState
import com.codetrio.spatialflow.ui.theme.SpatialFlowTheme
import com.codetrio.spatialflow.ui.theme.observeKey
import com.codetrio.spatialflow.ui.theme.rememberSpatialHazeState
import com.codetrio.spatialflow.ui.theme.spatialHazeEffect
import com.codetrio.spatialflow.ui.theme.spatialHazeSource
import com.codetrio.spatialflow.update.GitHubReleaseClient
import com.codetrio.spatialflow.update.UpdateBottomSheet
import com.codetrio.spatialflow.update.UpdateManager
import com.codetrio.spatialflow.update.VersionUtils
import com.codetrio.spatialflow.util.CrashHandler
import com.codetrio.spatialflow.viewmodel.ExploreViewModel
import com.codetrio.spatialflow.viewmodel.PlayerSharedViewModel
import com.codetrio.spatialflow.viewmodel.SpotifyViewModel
import com.codetrio.spatialflow.ui.components.YoutubeConnectionBanner
import com.codetrio.spatialflow.util.ConnectionStatus
import com.codetrio.spatialflow.util.rememberConnectionStatus
import com.codetrio.spatialflow.ui.components.rememberThrottledBackdrop
import com.codetrio.spatialflow.ui.components.throttledLayerBackdrop
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val playerViewModel: PlayerSharedViewModel by viewModels()
    private val exploreViewModel: ExploreViewModel by viewModels()
    private val spotifyViewModel: SpotifyViewModel by viewModels()
    private val lastFmViewModel: com.codetrio.spatialflow.viewmodel.LastFmViewModel by viewModels()

    private var navController: NavController? = null
    private var previousDestination = "explore"
    private var isNavigating = false

    var audioService: AudioPlaybackService? = null
    var isServiceBound = false
    lateinit var updateManager: UpdateManager

    var isBottomNavVisible by mutableStateOf(true)
        private set

    fun hideBottomNavWithAnimation() {
        isBottomNavVisible = false
    }

    fun showBottomNavWithAnimation() {
        isBottomNavVisible = true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as? AudioPlaybackService.LocalBinder
            audioService = binder?.getService()
            isServiceBound = true
            audioService?.let {
                playerViewModel.audioService = it
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            isServiceBound = false
            audioService = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        val splashStartTime = System.currentTimeMillis()
        splashScreen.setKeepOnScreenCondition {
            System.currentTimeMillis() - splashStartTime < SPLASH_DURATION
        }

        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)

        setupSystemBars()

        val runningService = AudioPlaybackService.currentInstance
        if (runningService == null || runningService.player.playbackState == androidx.media3.common.Player.STATE_IDLE || runningService.player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            playerViewModel.loadLastPlaybackState()
        }

        // Request the highest refresh rate the display supports (90Hz / 120Hz / VRR).
        // Without this, Android's default rate selector stays at 60Hz for most apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                preferredRefreshRate = display?.supportedModes
                    ?.maxByOrNull { it.refreshRate }?.refreshRate ?: 0f
            }
        }

        startAudioService()
        // Permissions are now handled in Onboarding Screen

        setContent {
            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)
            
            val prefs = remember { getSharedPreferences("AppSettings", MODE_PRIVATE) }
            val hasSeenOnboarding by prefs.observeKey("has_seen_onboarding_1_8", false)
            val themeMode by prefs.observeKey("theme_mode", "system")
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            var crashLog by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                if (CrashHandler.hasCrashReport(this@MainActivity)) {
                    crashLog = CrashHandler.getCrashReport(this@MainActivity)
                }
            }

            val currentSong by playerViewModel.currentSong.collectAsStateWithLifecycle()
            val playerBackgroundColor by playerViewModel.playerBackgroundColor.collectAsStateWithLifecycle()

            // Debounce dynamic album color updates to prevent full-app root recomposition storms
            // when artwork palette and canvas extract colors in rapid succession during track change.
            var settledDynamicColor by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(currentSong?.id, playerBackgroundColor) {
                if (currentSong == null) {
                    settledDynamicColor = null
                } else {
                    if (settledDynamicColor == null) {
                        kotlinx.coroutines.delay(200)
                    } else {
                        kotlinx.coroutines.delay(350)
                    }
                    settledDynamicColor = playerBackgroundColor
                }
            }
            val dynamicAlbumColor = if (currentSong != null) settledDynamicColor else null

            var showTelegramDialog by remember { mutableStateOf(false) }
            var showSpotifyUpdateBottomSheet by remember { mutableStateOf(false) }
            var showSupportBottomSheet by remember { mutableStateOf(false) }
            val isSupporter by prefs.observeKey("is_supporter", false)
            val hasSeenSpotifyAnnouncement by prefs.observeKey("has_seen_spotify_announcement_1_8_2", false)

            LaunchedEffect(hasSeenOnboarding, hasSeenSpotifyAnnouncement) {
                if (hasSeenOnboarding && !hasSeenSpotifyAnnouncement && !com.codetrio.spatialflow.data.spotify.SpotifyPrefs.isConnected(this@MainActivity)) {
                    kotlinx.coroutines.delay(500)
                    showSpotifyUpdateBottomSheet = true
                } else if (hasSeenOnboarding) {
                    val tgPrefs = getSharedPreferences("telegram_prefs", MODE_PRIVATE)
                    val hasShownDialog = tgPrefs.getBoolean("has_shown_join_dialog", false)
                    if (!hasShownDialog) {
                        showTelegramDialog = true
                    } else {
                        // At most 1x per day at random times (e.g. 35% probability per session)
                        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                        val lastPromptDate = prefs.getString("last_support_sheet_date", "")
                        if (lastPromptDate != todayStr) {
                            if (kotlin.random.Random.nextFloat() < 0.35f) {
                                kotlinx.coroutines.delay(2500)
                                prefs.edit().putString("last_support_sheet_date", todayStr).apply()
                                showSupportBottomSheet = true
                            }
                        }
                    }
                }
            }

            val forceHighRefreshRate by prefs.observeKey("force_high_refresh_rate", false)
            val supportedHighestFps = com.codetrio.spatialflow.util.rememberSupportedHighestFps()
            val isHighRefreshRateSupported = supportedHighestFps > 60.5f

            com.codetrio.spatialflow.util.ApplyRefreshRate(
                isEnabled = forceHighRefreshRate && isHighRefreshRateSupported,
                targetFps = supportedHighestFps
            )

            SpatialFlowTheme(
                darkTheme = isDarkTheme, 
                dynamicAlbumColor = dynamicAlbumColor,
                windowSizeClass = windowSizeClass
            ) {
                if (showSpotifyUpdateBottomSheet) {
                    com.codetrio.spatialflow.ui.spotify.SpotifyFeatureUpdateBottomSheet(
                        onConnectSpotify = {
                            prefs.edit().putBoolean("has_seen_spotify_announcement_1_8_2", true).apply()
                            showSpotifyUpdateBottomSheet = false
                            navigateToSpotifySignIn()
                        },
                        onOpenAccount = {
                            prefs.edit().putBoolean("has_seen_spotify_announcement_1_8_2", true).apply()
                            showSpotifyUpdateBottomSheet = false
                            navController?.navigate("account")
                        },
                        onDismiss = {
                            prefs.edit().putBoolean("has_seen_spotify_announcement_1_8_2", true).apply()
                            showSpotifyUpdateBottomSheet = false
                        }
                    )
                }

                if (showTelegramDialog) {
                    TelegramJoinDialog(
                        onDismiss = {
                            val tgPrefs = getSharedPreferences("telegram_prefs", MODE_PRIVATE)
                            tgPrefs.edit().putBoolean("has_shown_join_dialog", true).apply()
                            showTelegramDialog = false
                        },
                        onJoin = {
                            val tgPrefs = getSharedPreferences("telegram_prefs", MODE_PRIVATE)
                            tgPrefs.edit().putBoolean("has_shown_join_dialog", true).apply()
                            showTelegramDialog = false
                            try {
                                com.codetrio.spatialflow.util.TelegramHelper.openTelegram(this@MainActivity, domain = "SpatialFlow")
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(this@MainActivity, "Could not open Telegram", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                if (showSupportBottomSheet) {
                    com.codetrio.spatialflow.ui.settings.SupportBottomSheet(
                        isSupporter = isSupporter,
                        onDismissRequest = { showSupportBottomSheet = false },
                        onOpenSupportSite = {
                            try {
                                prefs.edit()
                                    .putBoolean("awaiting_supporter_reward", true)
                                    .putLong("supporter_reward_launch_time", System.currentTimeMillis())
                                    .apply()
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://supportspatialflow.vercel.app/"))
                                this@MainActivity.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(this@MainActivity, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                crashLog?.let { log ->
                    CrashReportBottomSheet(
                        crashLog = log,
                        onDismiss = {
                            CrashHandler.clearCrashReport(this@MainActivity)
                            crashLog = null
                        },
                        onReport = {
                            CrashHandler.clearCrashReport(this@MainActivity)
                            crashLog = null
                        }
                    )
                }
                val hideNavLabels by prefs.observeKey("hide_nav_labels", false)
                val dynamicNavStyle by prefs.observeKey("dynamic_nav_style", false)
                val isBlurEnabled by prefs.observeKey("navigation_blur", true)
                val navBarBlurOption by prefs.observeKey("nav_bar_blur_option", "blur")
                val slideNavOnScroll by prefs.observeKey("slide_nav_on_scroll", true)
                val navStyle by prefs.observeKey("nav_style", "docked")

                val appHazeState = rememberSpatialHazeState(isBlurEnabled)

                val globalFrostedBg = if (isDarkTheme) androidx.compose.ui.graphics.Color(0xFF101014) else androidx.compose.ui.graphics.Color(0xFFFAFAFD)
                val backdropNavGlass = rememberThrottledBackdrop(globalFrostedBg)

                CompositionLocalProvider(
                    LocalAppHazeState provides appHazeState,
                    LocalAppBackdrop provides backdropNavGlass
                ) {
                    val currentNavController = rememberNavController()
                LaunchedEffect(currentNavController) {
                    navController = currentNavController
                }

                val isPlayerExpanded by playerViewModel.isPlayerExpanded.collectAsStateWithLifecycle()

                var editingSong by remember { mutableStateOf<SongItem?>(null) }

                val playerExpansionFractionState = remember { mutableStateOf(0f) }
                LaunchedEffect(playerViewModel) {
                    playerViewModel.playerExpansionFraction.collect {
                        playerExpansionFractionState.value = it
                    }
                }

                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val isWideScreen = windowSizeClass?.widthSizeClass != androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact
                val useNavRail = isWideScreen || isLandscape

                val isRailVisible by remember(useNavRail, isPlayerExpanded) {
                    derivedStateOf { useNavRail && !isPlayerExpanded && (playerExpansionFractionState.value < 0.1f) }
                }

                    Row(modifier = Modifier.fillMaxSize()) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isRailVisible,
                            enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                            exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                        ) {
                            val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            val isRailBlurActive = appHazeState != null && isBlurEnabled && navBarBlurOption == "blur"
                            androidx.compose.material3.NavigationRail(
                                modifier = Modifier.spatialHazeEffect(
                                    state = appHazeState,
                                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                                    enabled = isRailBlurActive
                                ),
                                containerColor = if (isRailBlurActive) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                val items = listOf(
                                    Triple("explore", "Explore", R.drawable.ic_explore),
                                    Triple("library", "Library", R.drawable.ic_library_music),
                                    Triple("effects", "Effects", R.drawable.ic_equalizer),
                                    Triple("settings", "Settings", R.drawable.ic_settings)
                                )
                                items.forEach { (route, label, iconResId) ->
                                    val selected = currentDestination?.route == route
                                    androidx.compose.material3.NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            if (route == "explore") exploreViewModel.resetToHome()
                                            if (!selected) {
                                                currentNavController.navigate(route) {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(painter = painterResource(id = iconResId), contentDescription = label, modifier = Modifier.size(26.dp)) },
                                        label = if (hideNavLabels) null else { { Text(label) } }
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }

                        if (useNavRail) {
                            LaunchedEffect(Unit) {
                                playerViewModel.setBottomNavHeight(0f)
                                playerViewModel.setBottomNavTranslationY(0f)
                            }
                        }

                        Scaffold(
                            modifier = Modifier.weight(1f),
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (!useNavRail) {
                            val connectionStatus by rememberConnectionStatus()
                            val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                            val currentDestination = navBackStackEntry?.destination

                            val showBottomBar = currentDestination?.route in listOf("explore", "library", "effects", "settings", "appearance")

                            val density = LocalDensity.current
                            val navigationBarsHeightPx = WindowInsets.navigationBars.getBottom(density).toFloat()
                            val isFloatingNav = navStyle == "floating"
                            val isBannerActive = connectionStatus != ConnectionStatus.Hidden
                            val bannerLiftExtraDp by animateDpAsState(
                                targetValue = if (isBannerActive) 24.dp else 0.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "BannerLiftExtraDp"
                            )
                            val bottomNavHeight = (if (isFloatingNav) 68.dp else 80.dp) + bannerLiftExtraDp
                            val bottomNavHeightPx = with(density) { bottomNavHeight.toPx() }
                            val floatingBottomPadding = if (isFloatingNav) 12.dp else 0.dp
                            val floatingBottomPaddingPx = with(density) { floatingBottomPadding.toPx() }
                            val totalSlideDistPx = bottomNavHeightPx + navigationBarsHeightPx + floatingBottomPaddingPx


                            // This is the target for general bottom nav visibility (like scrolling or settings screen)
                            val isNavVisibleForBase = if (slideNavOnScroll) isBottomNavVisible else true
                            val baseTargetTranslation = if (showBottomBar && isNavVisibleForBase) 0f else totalSlideDistPx

                            val animatedBaseTranslationState = animateFloatAsState(
                                targetValue = baseTargetTranslation,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "BottomNavTranslationY"
                            )

                            // Monitor translations without triggering recomposition every frame
                            LaunchedEffect(showBottomBar, totalSlideDistPx) {
                                playerViewModel.setBottomNavHeight(if (showBottomBar) totalSlideDistPx else 0f)
                                launch {
                                    snapshotFlow { animatedBaseTranslationState.value }.collect { baseTranslationY ->
                                        playerViewModel.setBottomNavBaseTranslationY(baseTranslationY)
                                    }
                                }
                                launch {
                                    snapshotFlow {
                                        val currentBase = animatedBaseTranslationState.value
                                        val currentFraction = playerExpansionFractionState.value
                                        (currentBase + (totalSlideDistPx * currentFraction)).coerceAtMost(totalSlideDistPx)
                                    }.collect { translationY ->
                                        playerViewModel.setBottomNavTranslationY(translationY)
                                    }
                                }
                            }

                            val navBarTransition = updateTransition(
                                targetState = dynamicNavStyle,
                                label = "NavBarStyle"
                            )
                            val navBarHeight by navBarTransition.animateDp(
                                label = "navBarHeight",
                                transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
                            ) { if (it) 64.dp else 80.dp }
                            val navIconSize by navBarTransition.animateDp(
                                label = "navIconSize",
                                transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
                            ) { if (it) 34.dp else 26.dp }
                            val navElevation by navBarTransition.animateDp(
                                label = "navElevation",
                                transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
                            ) { if (it) 8.dp else 3.dp }
                            val navCornerRadius by navBarTransition.animateDp(
                                label = "navCornerRadius",
                                transitionSpec = { spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow) }
                            ) { if (it) 20.dp else 28.dp }
                            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                            val isNavBarFrostedActive by remember(isBlurEnabled, navBarBlurOption, appHazeState) {
                                derivedStateOf {
                                    appHazeState != null && isBlurEnabled && (navBarBlurOption == "frosted" || navBarBlurOption == "blur") && (playerExpansionFractionState.value < 0.5f)
                                }
                            }
                            val isNavBarLiquidActive by remember(navBarBlurOption) {
                                derivedStateOf {
                                    (navBarBlurOption == "liquid") && (playerExpansionFractionState.value < 0.5f)
                                }
                            }
                            val isNavBarBlurActive = isNavBarFrostedActive || isNavBarLiquidActive
                            val isDockedNavBlurActive = isNavBarFrostedActive

                            val isDark = isSystemInDarkTheme()
                            val navBarShape = RoundedCornerShape(
                                topStart = navCornerRadius,
                                topEnd = navCornerRadius,
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp
                            )
                            val navBarFrostedBg = if (isDark) androidx.compose.ui.graphics.Color(0xFF101014) else androidx.compose.ui.graphics.Color(0xFFFAFAFD)
                            val navBarFrostedTint = if (isDark) androidx.compose.ui.graphics.Color(0xCC121218) else androidx.compose.ui.graphics.Color(0xE6FFFFFF)

                            if (isFloatingNav) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            val currentBase = animatedBaseTranslationState.value
                                            val currentFraction = playerExpansionFractionState.value
                                            val translationY = (currentBase + (totalSlideDistPx * currentFraction)).coerceAtMost(totalSlideDistPx)
                                            this.translationY = translationY
                                            this.alpha = if (translationY >= totalSlideDistPx - 1f) 0f else 1f
                                        }
                                        .padding(bottom = bottomPadding + 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val spatialNavItems = remember {
                                        listOf(
                                            com.codetrio.spatialflow.ui.components.SpatialNavItem("explore", "Explore", R.drawable.ic_explore),
                                            com.codetrio.spatialflow.ui.components.SpatialNavItem("library", "Library", R.drawable.ic_library_music),
                                            com.codetrio.spatialflow.ui.components.SpatialNavItem("effects", "Effects", R.drawable.ic_equalizer),
                                            com.codetrio.spatialflow.ui.components.SpatialNavItem("settings", "Settings", R.drawable.ic_settings)
                                        )
                                    }
                                    val activeTabRoute = when (val route = currentDestination?.route) {
                                        "appearance", "playback", "music_management", "account", "haptics", "about", "feedback", "whats_new", "backup_restore" -> "settings"
                                        "tag_editor" -> "library"
                                        else -> route
                                    }
                                    val navVisibilityFractionState = remember(totalSlideDistPx) {
                                        derivedStateOf {
                                            val total = if (totalSlideDistPx > 0f) totalSlideDistPx else 1f
                                            (1f - (animatedBaseTranslationState.value / total)).coerceIn(0f, 1f)
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.height(64.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        com.codetrio.spatialflow.ui.components.SpatialFloatingNavBar(
                                            items = spatialNavItems,
                                            currentRoute = activeTabRoute,
                                            onItemSelect = { route ->
                                                if (route == "explore") {
                                                    exploreViewModel.resetToHome()
                                                }
                                                if (activeTabRoute != route) {
                                                    currentNavController.navigate(route) {
                                                        popUpTo(currentNavController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                            hideLabels = false,
                                            navVisibilityFraction = if (isFloatingNav) navVisibilityFractionState.value else 1f,
                                            hazeState = appHazeState,
                                            isBlurActive = isNavBarFrostedActive,
                                            frostedBg = navBarFrostedBg,
                                            frostedTint = navBarFrostedTint,
                                            backdrop = backdropNavGlass,
                                            useBackdropGlass = isNavBarLiquidActive
                                        )

                                        val isExploreScrolledDown by exploreViewModel.isScrolledDown.collectAsStateWithLifecycle()
                                        val isScrolledTopActive = activeTabRoute == "explore" && isExploreScrolledDown

                                        com.codetrio.spatialflow.ui.components.SpatialFloatingSearchButton(
                                            onClick = {
                                                if (isScrolledTopActive) {
                                                    exploreViewModel.onAction(com.codetrio.spatialflow.viewmodel.ExploreAction.OnScrollToTop)
                                                } else {
                                                    if (activeTabRoute != "explore") {
                                                        currentNavController.navigate("explore") {
                                                            popUpTo(currentNavController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                    exploreViewModel.onAction(com.codetrio.spatialflow.viewmodel.ExploreAction.OnOpenSearch)
                                                }
                                            },
                                            isScrollToTop = isScrolledTopActive,
                                            hazeState = appHazeState,
                                            isBlurActive = isNavBarFrostedActive,
                                            frostedBg = navBarFrostedBg,
                                            frostedTint = navBarFrostedTint,
                                            backdrop = backdropNavGlass,
                                            useBackdropGlass = isNavBarLiquidActive
                                        )
                                    }
                                    YoutubeConnectionBanner(
                                        status = connectionStatus,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            val currentBase = animatedBaseTranslationState.value
                                            val currentFraction = playerExpansionFractionState.value
                                            val translationY = (currentBase + (totalSlideDistPx * currentFraction)).coerceAtMost(totalSlideDistPx)
                                            this.translationY = translationY
                                            this.alpha = if (translationY >= totalSlideDistPx - 1f) 0f else 1f
                                        }
                                        .clip(navBarShape)
                                        .spatialHazeEffect(
                                            state = appHazeState,
                                            backgroundColor = navBarFrostedBg,
                                            tint = navBarFrostedTint,
                                            enabled = isDockedNavBlurActive
                                        ),
                                    color = if (isDockedNavBlurActive) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                                    shadowElevation = if (isDockedNavBlurActive) 0.dp else navElevation,
                                    tonalElevation = if (isDockedNavBlurActive) 0.dp else navElevation
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        NavigationBar(
                                            windowInsets = WindowInsets(0, 0, 0, 0),
                                            modifier = Modifier
                                                .height(navBarHeight)
                                                .fillMaxWidth(),
                                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                            tonalElevation = 0.dp
                                        ) {

                                            val items = listOf(
                                                Triple("explore", "Explore", R.drawable.ic_explore),
                                                Triple("library", "Library", R.drawable.ic_library_music),
                                                Triple("effects", "Effects", R.drawable.ic_equalizer),
                                                Triple("settings", "Settings", R.drawable.ic_settings)
                                            )
                                            items.forEach { (route, label, iconResId) ->
                                                val selected = currentDestination?.route == route
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = {
                                                        if (route == "explore") {
                                                            exploreViewModel.resetToHome()
                                                        }
                                                        if (!selected) {
                                                            currentNavController.navigate(route) {
                                                                popUpTo(currentNavController.graph.findStartDestination().id) {
                                                                    saveState = true
                                                                }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                    },
                                                    icon = { Icon(painter = painterResource(id = iconResId), contentDescription = label, modifier = Modifier.size(navIconSize)) },
                                                    label = if (hideNavLabels) null else { { Text(label) } }
                                                )
                                            }
                                        }
                                        YoutubeConnectionBanner(
                                            status = connectionStatus,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        val animatedBottomSpacer by animateDpAsState(
                                            targetValue = if (isBannerActive) (bottomPadding - 10.dp).coerceAtLeast(4.dp) else bottomPadding,
                                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                                            label = "NavBottomSpacer"
                                        )
                                        Spacer(modifier = Modifier.height(animatedBottomSpacer))
                                    }
                                }
                            }


                        }
                    }
                ) { paddingValues ->
                        val navBackStackEntry by currentNavController.currentBackStackEntryAsState()
                        val currentDestination = navBackStackEntry?.destination
                        val showBottomBar = currentDestination?.route in listOf("explore", "library", "effects", "settings")

                        val playerExpansionFractionState = remember { mutableStateOf(0f) }
                        LaunchedEffect(playerViewModel) {
                            playerViewModel.playerExpansionFraction.collect {
                                playerExpansionFractionState.value = it
                            }
                        }

                        // Base padding target not considering expansion
                        val baseTargetPadding = if (showBottomBar && isBottomNavVisible) {
                            paddingValues.calculateBottomPadding()
                        } else {
                            0.dp
                        }

                        val animatedBasePaddingState = animateDpAsState(
                            targetValue = baseTargetPadding,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "BottomNavPadding"
                        )

                        // Final padding does not scale down during expansion to prevent massive Layout invalidation
                        // The player sheet covers the NavHost anyway, so keeping padding static is optimal
                        val finalPadding = animatedBasePaddingState.value

                        val isHazeSourceEnabled by remember(isBlurEnabled) {
                            derivedStateOf {
                                isBlurEnabled && (playerExpansionFractionState.value < 0.99f)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull()
                                            if (change != null && change.pressed) {
                                                val width = size.width
                                                BackGestureTracker.lastEdge = if (change.position.x > width / 2f) {
                                                    BackSwipeEdge.RIGHT
                                                } else {
                                                    BackSwipeEdge.LEFT
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .throttledLayerBackdrop(backdropNavGlass)
                                    .spatialHazeSource(
                                        state = appHazeState,
                                        enabled = isHazeSourceEnabled
                                    )

                                    .graphicsLayer {
                                        val fraction = playerExpansionFractionState.value

                                        // 1. Hide NavHost entirely when sheet is fully expanded to save drawing cycles.
                                        this.alpha = if (fraction >= 0.99f) 0f else 1f
                                    }
                                    .drawWithContent {
                                        val fraction = playerExpansionFractionState.value
                                        if (fraction < 0.99f) {
                                            drawContent()
                                            if (isBlurEnabled && fraction > 0.01f) {
                                                drawRect(color = androidx.compose.ui.graphics.Color.Black.copy(alpha = fraction * 0.6f))
                                            }
                                        }
                                    }
                            ) {
                                CompositionLocalProvider(
                                    LocalNavController provides currentNavController,
                                    LocalAppBackdrop provides null
                                ) {
                                    NavHost(
                                        navController = currentNavController,
                                        startDestination = if (hasSeenOnboarding) "explore" else "onboarding",
                                        modifier = Modifier.fillMaxSize(),
                                        enterTransition = {
                                            val from = initialState.destination.route
                                            val to = targetState.destination.route
                                            val dir = mainRootDirection(from, to)
                                            if (dir != null) {
                                                mainRootEnterTransition(from, to, aospSharedAxisEnter())
                                            } else {
                                                aospSharedAxisEnter()
                                            }
                                        },
                                        exitTransition = {
                                            val from = initialState.destination.route
                                            val to = targetState.destination.route
                                            val dir = mainRootDirection(from, to)
                                            if (dir != null) {
                                                mainRootExitTransition(from, to, aospSharedAxisExit())
                                            } else {
                                                aospSharedAxisExit()
                                            }
                                        },
                                        popEnterTransition = {
                                            val from = initialState.destination.route
                                            val to = targetState.destination.route
                                            val dir = mainRootDirection(from, to)
                                            if (dir != null) {
                                                mainRootEnterTransition(from, to, aospSharedAxisPopEnter())
                                            } else {
                                                aospSharedAxisPopEnter()
                                            }
                                        },
                                        popExitTransition = {
                                            val from = initialState.destination.route
                                            val to = targetState.destination.route
                                            val dir = mainRootDirection(from, to)
                                            if (dir != null) {
                                                mainRootExitTransition(from, to, aospSharedAxisPopExit())
                                            } else {
                                                aospSharedAxisPopExit()
                                            }
                                        }
                                    ) {
                                    composableWithBlur("onboarding") {
                                        OnboardingScreen(
                                            onComplete = {
                                                currentNavController.navigate("explore") {
                                                    popUpTo("onboarding") { inclusive = true }
                                                }
                                            }
                                        )
                                    }
                                    composableWithBlur("explore") {
                                        ExploreScreen(
                                            viewModel = exploreViewModel,
                                            playerSharedViewModel = playerViewModel,
                                            spotifyViewModel = spotifyViewModel,
                                            onNavigateToLibrary = {
                                                currentNavController.navigate("library") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            onNavigateToSpotifySignIn = {
                                                if (currentNavController.currentDestination?.route != "spotify_signin") {
                                                    currentNavController.navigate("spotify_signin")
                                                }
                                            },
                                            onNavigateToSpotifyPlaylist = { playlistId ->
                                                currentNavController.navigate("spotify_playlist/$playlistId")
                                            },
                                        )
                                    }
                                    composableWithBlur("library") {
                                        LibraryScreen(
                                            viewModel = playerViewModel,
                                            navController = currentNavController,
                                            spotifyViewModel = spotifyViewModel,
                                            onEditSong = { song ->
                                                editingSong = song
                                                currentNavController.navigate("tag_editor")
                                            },
                                            onNavigateToExplore = {
                                                currentNavController.navigate("explore") {
                                                    popUpTo(currentNavController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                    composableWithBlur("effects") {
                                        EffectsScreenEntryPoint(viewmodel = playerViewModel)
                                    }
                                    settingsGraph(currentNavController)
                                    composableWithBlur("tag_editor") {
                                        editingSong?.let { song ->
                                            TagEditorScreenEntryPoint(
                                                song = song,
                                                onNavigateUp = {
                                                    currentNavController.navigateUp()
                                                }
                                            )
                                        }
                                    }
                                    composableWithBlur("google_signin") {
                                        val accountViewModel: com.codetrio.spatialflow.viewmodel.AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                                        GoogleSignInScreen(
                                            accountViewModel = accountViewModel,
                                            onSignInSuccess = {
                                                currentNavController.navigate("explore") {
                                                    popUpTo("explore") { inclusive = true }
                                                }
                                            },
                                            onNavigateUp = {
                                                currentNavController.navigateUp()
                                            }
                                        )
                                    }
                                    composableWithBlur("spotify_signin") {
                                        SpotifySignInScreen(
                                            spotifyViewModel = spotifyViewModel,
                                            onSignInSuccess = { currentNavController.navigateUp() },
                                            onNavigateUp = { currentNavController.navigateUp() },
                                        )
                                    }
                                    composableWithBlur("lastfm_signin") {
                                        com.codetrio.spatialflow.ui.lastfm.LastFmSignInScreen(
                                            lastFmViewModel = lastFmViewModel,
                                            onSignInSuccess = { currentNavController.navigateUp() },
                                            onNavigateUp = { currentNavController.navigateUp() },
                                        )
                                    }
                                    composableWithBlur("deezer_signin") {
                                        com.codetrio.spatialflow.ui.settings.deezer.DeezerSignInScreen(
                                            onSignInSuccess = { currentNavController.navigateUp() },
                                            onNavigateUp = { currentNavController.navigateUp() },
                                        )
                                    }
                                    composableWithBlur("tidal_signin") {
                                        com.codetrio.spatialflow.ui.settings.tidal.TidalSignInScreen(
                                            onSignInSuccess = { currentNavController.navigateUp() },
                                            onNavigateUp = { currentNavController.navigateUp() },
                                        )
                                    }
                                    composableWithBlur("spotify_playlist/{playlistId}") { backStackEntry ->
                                        val playlistId = backStackEntry.arguments?.getString("playlistId").orEmpty()
                                        SpotifyPlaylistScreen(
                                            playlistId = playlistId,
                                            navController = currentNavController,
                                            playerViewModel = playerViewModel,
                                        )
                                    }
                                }
                                }
                            }

                            val currentRoute = currentDestination?.route
                            if (currentRoute != "onboarding" && currentRoute != "google_signin" && currentRoute != "spotify_signin" && currentRoute != "lastfm_signin") {
                                PlayerBottomSheetCompose(
                                    activity = this@MainActivity,
                                    viewModel = playerViewModel
                                )
                            }

                            val dacBannerEnabled by prefs.observeKey("dac_connection_banner", true)
                            if (dacBannerEnabled) {
                                com.codetrio.spatialflow.ui.player.DacConnectedBannerHost()
                            }
                            }
                        }
                    }
            }
        }
    }

        updateManager = UpdateManager(this)
        if (!handleIntent(intent)) {
            checkForUpdateOnLaunch()
        }
    }

    fun navigateToSettings() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "settings") {
            controller.navigate("settings") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "settings"
        }
    }

    fun navigateToEffects() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "effects") {
            controller.navigate("effects") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "effects"
        }
    }

    fun navigateToGoogleSignIn() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "google_signin") {
            controller.navigate("google_signin")
        }
    }

    fun navigateToSpotifySignIn() {
        val controller = navController ?: return
        if (controller.currentDestination?.route != "spotify_signin") {
            controller.navigate("spotify_signin")
        }
    }

    fun showArtistPage(artistId: String?, artistName: String?) {
        val controller = navController ?: return
        val cameFromOutside = controller.currentDestination?.route != "explore"
        
        if (cameFromOutside) {
            isNavigating = true
            controller.navigate("explore") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "explore"
            isNavigating = false
        }

        if (!artistId.isNullOrBlank()) {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadArtist(artistId)
        } else if (!artistName.isNullOrBlank() && artistName != "Unknown Artist") {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadArtistByName(artistName)
        }
    }

    fun showAlbumPage(
        albumId: String?,
        albumTitle: String?,
        artistName: String? = null,
        albumName: String? = null,
        videoId: String? = null
    ) {
        val controller = navController ?: return
        val cameFromOutside = controller.currentDestination?.route != "explore"
        
        if (cameFromOutside) {
            isNavigating = true
            controller.navigate("explore") {
                popUpTo(controller.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            previousDestination = "explore"
            isNavigating = false
        }

        val isDirectBrowseId = !albumId.isNullOrBlank() && 
            (albumId.startsWith("MPREb_") || albumId.startsWith("FEmusic_") || albumId.startsWith("PL") || albumId.startsWith("OLAK5uy_") || albumId.toLongOrNull() == null)

        if (isDirectBrowseId) {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadAlbum(albumId!!)
        } else if (!albumTitle.isNullOrBlank() || !albumName.isNullOrBlank() || !artistName.isNullOrBlank()) {
            if (cameFromOutside) {
                exploreViewModel.cameFromLibrary = true
            }
            exploreViewModel.loadAlbumForSong(
                songTitle = albumTitle ?: "",
                artistName = artistName ?: "",
                albumName = albumName ?: albumTitle,
                videoId = videoId
            )
        }
    }

    private fun checkForUpdateOnLaunch() {
        if (!shouldCheckForUpdate()) {
            Log.d(TAG, "Update check skipped (checked recently)")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = GitHubReleaseClient("MythicalSHUB", "SpatialFlow")
                val release = client.latestRelease ?: return@launch

                val currentVersion = BuildConfig.VERSION_NAME
                if (VersionUtils.isNewer(release.tagName, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(release)
                        updateManager.showUpdateNotification(release.tagName, release.changelog ?: "", release.apkUrl ?: "")
                    }
                } else {
                    Log.d(TAG, "App is up to date (current: $currentVersion, latest: ${release.tagName})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    private fun showUpdateDialog(release: GitHubReleaseClient.ReleaseInfo) {
        val sheet = UpdateBottomSheet.newInstance(
            tagName = release.tagName,
            changelog = release.changelog ?: "",
            apkUrl = release.apkUrl ?: ""
        )
        sheet.setOnUpdateClickListener(object : UpdateBottomSheet.OnUpdateClickListener {
            override fun onUpdateClick(apkUrl: String): Long {
                val rootView = findViewById<View>(android.R.id.content)
                return updateManager.startDownload(rootView, apkUrl)
            }

            override fun onLaterClick() {}
        })
        sheet.show(supportFragmentManager, "UpdateBottomSheet")
    }

    private fun shouldCheckForUpdate(): Boolean {
        val prefs = getSharedPreferences("update_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("update_check_disabled", false)) return false

        val lastCheck = prefs.getLong("last_update_check", 0)
        val currentTime = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        return if (currentTime - lastCheck > oneDayMillis) {
            prefs.edit { putLong("last_update_check", currentTime) }
            true
        } else false
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioPlaybackService::class.java)
        try {
            startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(TAG, "startService failed, relying on bind: ${e.message}")
        }
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        Log.d(TAG, "Audio service started and bound")
    }

    private fun ensureServiceRunning() {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val isRunning = manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == AudioPlaybackService::class.java.name
        }
        if (!isRunning) startAudioService()
    }

    private fun checkAudioPermission() {
        val requiredPermissions = getRequiredRuntimePermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), AUDIO_PERMISSION_REQUEST)
        }
    }

    private fun getRequiredRuntimePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    override fun onResume() {
        super.onResume()
        setupSystemBars()
        checkAwaitingSupporterStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupSystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation / resize is handled without Activity recreation (see android:configChanges
        // in the manifest). Re-apply transparent system bars so they stay correct after the
        // orientation change; Compose re-lays-out automatically from the updated Configuration.
        setupSystemBars()
    }

    fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDarkMode
            isAppearanceLightNavigationBars = !isDarkMode
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PREVIOUS_DESTINATION, previousDestination)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController?.navigateUp() == true || super.onSupportNavigateUp()
    }

    override fun onStart() {
        super.onStart()
        ensureServiceRunning()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (_: Exception) {
                Log.d(TAG, "Service not bound, skipping unbind")
            }
            isServiceBound = false
        }
    }
    fun showSnackbar(message: String, duration: Int) {
        com.codetrio.spatialflow.ui.SnackbarController.showMessage(message)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        if (prefs.getBoolean("awaiting_supporter_reward", false)) {
            unlockSupporterStatus()
        }
        handleIntent(intent)
    }

    private fun checkAwaitingSupporterStatus() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val isAwaiting = prefs.getBoolean("awaiting_supporter_reward", false)
        if (isAwaiting) {
            val launchTime = prefs.getLong("supporter_reward_launch_time", 0L)
            val elapsed = System.currentTimeMillis() - launchTime
            // If at least 8 seconds have elapsed since opening the supporter website (covers navigating site + ad countdown)
            if (elapsed >= 8_000L) {
                unlockSupporterStatus()
            }
        }
    }

    private fun unlockSupporterStatus() {
        val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
        val alreadySupporter = prefs.getBoolean("is_supporter", false)
        prefs.edit()
            .putBoolean("is_supporter", true)
            .putBoolean("awaiting_supporter_reward", false)
            .apply()
        if (!alreadySupporter) {
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Thank you for supporting SpatialFlow! Supporter badge unlocked.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun handleIntent(intent: Intent?): Boolean {
        if (intent == null) return false

        val shareId = DeepLinkManager.consumeShareId(intent)
        if (!shareId.isNullOrBlank()) {
            handleIncomingSongShare(shareId)
            return true
        }

        try {
            val showUpdateSheet = intent.getBooleanExtra("show_update_sheet", false)
            if (showUpdateSheet) {
                val tagName = intent.getStringExtra("update_tag_name")
                val changelog = intent.getStringExtra("update_changelog")
                val apkUrl = intent.getStringExtra("update_apk_url")
                if (tagName != null && apkUrl != null) {
                    intent.removeExtra("show_update_sheet")
                    val release = GitHubReleaseClient.ReleaseInfo(
                        tagName = tagName,
                        changelog = changelog,
                        apkUrl = apkUrl,
                        checksumUrl = null
                    )
                    window.decorView.post {
                        if (!isFinishing && !isDestroyed) {
                            showUpdateDialog(release)
                        }
                    }
                    return true
                }
            }

            if (Intent.ACTION_SEND == intent.action && "text/plain" == intent.type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrEmpty()) {
                    val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
                    val url = urlRegex.find(sharedText)?.value ?: sharedText.trim()

                    // Check for Spotify playlist link shared via share sheet
                    val spotifyPlaylistId = extractSpotifyPlaylistId(url.ifBlank { sharedText })
                    if (!spotifyPlaylistId.isNullOrBlank()) {
                        try {
                            navController?.navigate("spotify_playlist/$spotifyPlaylistId")
                        } catch (e: Exception) {
                            Log.e(TAG, "Navigation to spotify playlist failed in ACTION_SEND", e)
                        }
                        return true
                    }

                    if (url.contains("youtube.com") || url.contains("youtu.be") || url.startsWith("spatialflow://")) {
                        exploreViewModel.onAction(com.codetrio.spatialflow.viewmodel.ExploreAction.OnHandleDeepLink(url))
                        try {
                            if (navController?.currentDestination?.route != "explore") {
                                navController?.navigate("explore") {
                                    popUpTo(navController?.graph?.findStartDestination()?.id ?: 0) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Navigation to explore failed in ACTION_SEND", e)
                        }
                        return true
                    }
                }
            }

            val prefs = getSharedPreferences("AppSettings", MODE_PRIVATE)
            val isSupporterReward = intent.getBooleanExtra("supporter_reward", false) ||
                intent.getStringExtra("browser_fallback_url")?.contains("supportspatialflow") == true ||
                intent.dataString?.contains("supportspatialflow") == true ||
                intent.dataString?.contains("reward") == true ||
                (intent.action == Intent.ACTION_MAIN && prefs.getBoolean("awaiting_supporter_reward", false))
            if (isSupporterReward) {
                unlockSupporterStatus()
            }

            if (Intent.ACTION_VIEW == intent.action || "android.intent.action.MUSIC_PLAYER" == intent.action) {
                intent.data?.let { uri ->
                    val scheme = uri.scheme?.lowercase()
                    val host = uri.host?.lowercase()
                    if (scheme == "spatialflow" && (host == "supporter" || host == "reward")) {
                        unlockSupporterStatus()
                        return true
                    }
                    if (host == "supportspatialflow.vercel.app" && uri.path?.contains("reward") == true) {
                        unlockSupporterStatus()
                        return true
                    }
                    if (scheme == "spotify") {
                        val playlistId = extractSpotifyPlaylistId(uri.toString())
                        if (!playlistId.isNullOrBlank()) {
                            try {
                                navController?.navigate("spotify_playlist/$playlistId")
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation to spotify playlist failed for spotify scheme", e)
                            }
                            return true
                        }
                    }
                    if (scheme == "spatialflow") {
                        if (uri.host == "lastfm-auth") {
                            val token = uri.getQueryParameter("token")
                            if (!token.isNullOrBlank()) {
                                lastFmViewModel.loginWithToken(token) { success, error ->
                                    runOnUiThread {
                                        if (success) {
                                            android.widget.Toast.makeText(this@MainActivity, "Connected to Last.fm successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(this@MainActivity, "Last.fm authorization failed: $error", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                return true
                            }
                        } else {
                            exploreViewModel.onAction(com.codetrio.spatialflow.viewmodel.ExploreAction.OnHandleDeepLink(uri.toString()))
                            try {
                                if (navController?.currentDestination?.route != "explore") {
                                    navController?.navigate("explore") {
                                        popUpTo(navController?.graph?.findStartDestination()?.id ?: 0) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation to explore failed for spatialflow scheme", e)
                            }
                            return true
                        }
                    } else if (scheme == "http" || scheme == "https") {
                        val hostStr = uri.host?.lowercase() ?: ""
                        if (hostStr.contains("spotify.com")) {
                            val playlistId = extractSpotifyPlaylistId(uri.toString())
                            if (!playlistId.isNullOrBlank()) {
                                try {
                                    navController?.navigate("spotify_playlist/$playlistId")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Navigation to spotify playlist failed for web url", e)
                                }
                                return true
                            }
                        }
                        if (hostStr.contains("youtube.com") || hostStr.contains("youtu.be")) {
                            exploreViewModel.onAction(com.codetrio.spatialflow.viewmodel.ExploreAction.OnHandleDeepLink(uri.toString()))
                            try {
                                if (navController?.currentDestination?.route != "explore") {
                                    navController?.navigate("explore") {
                                        popUpTo(navController?.graph?.findStartDestination()?.id ?: 0) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Navigation to explore failed for web url", e)
                            }
                            return true
                        }
                    } else if (scheme == "content" || scheme == "file") {
                        playExternalUri(uri)
                        return true
                    }
                }
            }

            if (navController != null && intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
                try {
                    navController?.navigate("library")
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation request failed in handleIntent", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling intent safely", e)
        }
        return false
    }

    private fun extractSpotifyPlaylistId(text: String): String? {
        val regex = Regex("""(?:spotify:playlist:|open\.spotify\.com/(?:intl-[a-zA-Z0-9_-]+/)?playlist/)([a-zA-Z0-9]+)""")
        return regex.find(text)?.groupValues?.get(1)
    }

    private val sharedSongRepository by lazy { SharedSongRepository() }

    private suspend fun awaitAudioService(timeoutMs: Long = 4000L): AudioPlaybackService? {
        playerViewModel.audioService?.let { return it }
        withContext(Dispatchers.Main) {
            ensureServiceRunning()
        }
        return withTimeoutOrNull(timeoutMs) {
            playerViewModel.audioServiceState.filterNotNull().first()
        }
    }

    private fun handleIncomingSongShare(shareId: String) {
        showSnackbar("Loading shared song...", 2500)
        lifecycleScope.launch(Dispatchers.IO) {
            val result = sharedSongRepository.getSharedSong(shareId)

            result.onSuccess { metadata ->
                try {
                    val resolvedSong = resolveSharedSong(metadata)
                    if (resolvedSong != null) {
                        awaitAudioService(4000L)

                        withContext(Dispatchers.Main) {
                            val songUri = "innertube://${resolvedSong.videoId}".toUri()
                            resolvedSong.contentUri = songUri

                            val radioQueue = YouTubeQueue.radio(resolvedSong)
                            playerViewModel.setQueue(radioQueue)
                            playerViewModel.expandPlayer()

                            playerViewModel.audioService?.loadAndPlay(songUri)
                            showSnackbar("Playing: ${resolvedSong.title}", 2500)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showSnackbar("Unable to resolve song playback source", 3000)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed resolving shared song", e)
                    withContext(Dispatchers.Main) {
                        showSnackbar("Failed to play shared song", 3000)
                    }
                }
            }

            result.onFailure { error ->
                withContext(Dispatchers.Main) {
                    val message = when (error) {
                        DataError.Network.NOT_FOUND -> "Shared song not found or invalid link"
                        DataError.Network.NO_INTERNET -> "Unable to connect. Check your internet connection."
                        else -> "Could not load shared song: ${error.name.lowercase().replace('_', ' ')}"
                    }
                    showSnackbar(message, 3500)
                }
            }
        }
    }

    private suspend fun resolveSharedSong(metadata: SharedSongMetadata): SongItem? {
        val durationMs = (metadata.duration ?: 0L) * 1000L

        // 1. YouTube provider: videoId is directly provided
        if (metadata.provider.equals("youtube", ignoreCase = true) && metadata.providerId.isNotBlank()) {
            val song = SongItem.createOnlineSong(
                videoId = metadata.providerId,
                title = metadata.title,
                artist = metadata.artist,
                streamUrl = "",
                durationMs = durationMs,
                thumbnailUrl = metadata.artworkUrl
            )
            song.contentUri = "innertube://${metadata.providerId}".toUri()
            song.albumName = metadata.album
            return song
        }

        // 2. Non-YouTube provider or missing videoId: resolve via YouTubeMusic search
        try {
            val query = "${metadata.title} ${metadata.artist}".trim()
            if (query.isNotBlank()) {
                val resolvedVideoId = com.codetrio.spatialflow.data.innertube.YouTubeMusic
                    .resolveVideoIdForTrack(metadata.title, metadata.artist).getOrNull()

                if (!resolvedVideoId.isNullOrBlank()) {
                    val song = SongItem.createOnlineSong(
                        videoId = resolvedVideoId,
                        title = metadata.title,
                        artist = metadata.artist,
                        streamUrl = "",
                        durationMs = durationMs,
                        thumbnailUrl = metadata.artworkUrl
                    )
                    song.contentUri = "innertube://$resolvedVideoId".toUri()
                    song.albumName = metadata.album
                    return song
                }

                val searchResult = com.codetrio.spatialflow.data.innertube.YouTubeMusic.search(
                    query,
                    com.codetrio.spatialflow.data.innertube.SearchFilter.SONGS
                ).getOrNull() ?: com.codetrio.spatialflow.data.innertube.YouTubeMusic.search(query).getOrNull()

                val matched = searchResult?.items
                    ?.filterIsInstance<com.codetrio.spatialflow.data.innertube.SearchItem.Song>()?.firstOrNull()?.song
                    ?: searchResult?.items
                    ?.filterIsInstance<com.codetrio.spatialflow.data.innertube.SearchItem.TopResult>()?.firstOrNull()?.song

                if (matched != null && !matched.videoId.isNullOrBlank()) {
                    val song = SongItem.createOnlineSong(
                        videoId = matched.videoId,
                        title = matched.title.ifBlank { metadata.title },
                        artist = matched.artist.ifBlank { metadata.artist },
                        streamUrl = "",
                        durationMs = if (matched.durationMs > 0) matched.durationMs else durationMs,
                        thumbnailUrl = matched.thumbnailUrl ?: metadata.artworkUrl,
                        artistId = matched.artistId
                    )
                    song.contentUri = "innertube://${matched.videoId}".toUri()
                    song.albumName = metadata.album
                    return song
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving non-YouTube provider song", e)
        }

        // 3. Fallback: if providerId looks like a YouTube videoId (e.g. 11 characters)
        if (metadata.providerId.isNotBlank() && metadata.providerId.length in 10..15) {
            val song = SongItem.createOnlineSong(
                videoId = metadata.providerId,
                title = metadata.title,
                artist = metadata.artist,
                streamUrl = "",
                durationMs = durationMs,
                thumbnailUrl = metadata.artworkUrl
            )
            song.contentUri = "innertube://${metadata.providerId}".toUri()
            song.albumName = metadata.album
            return song
        }

        return null
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun playExternalUri(uri: Uri) {
        var displayName = "External Track"
        if ("content" == uri.scheme) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.takeIf { it.isNotEmpty() }?.let {
                                displayName = it
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query metadata for external uri", e)
            }
        } else if ("file" == uri.scheme) {
            displayName = uri.lastPathSegment ?: displayName
        }

        if (displayName.contains(".")) {
            val dotIdx = displayName.lastIndexOf('.')
            if (dotIdx > 0) displayName = displayName.substring(0, dotIdx)
        }

        val externalId = -System.currentTimeMillis()
        val externalSong = SongItem(
            externalId,
            displayName,
            "External Source",
            -1L,
            uri.toString(),
            0L,
            System.currentTimeMillis() / 1000
        ).apply { contentUri = uri }

        val triggerPlayTask = Runnable {
            playerViewModel.playSong(externalSong)
            navController?.let { controller ->
                try {
                    controller.navigate("library")
                } catch (e: Exception) {
                    Log.e(TAG, "Navigation route to PlayerFragment failed in playExternalUri", e)
                }
            }
        }

        if (!isServiceBound) {
            lifecycleScope.launch {
                playerViewModel.audioServiceState.collect { service ->
                    if (service != null) {
                        triggerPlayTask.run()
                        this@launch.coroutineContext[kotlinx.coroutines.Job]?.cancel()
                    }
                }
            }
        } else {
            triggerPlayTask.run()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 9999) {
            if (resultCode == RESULT_OK) {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Track deleted successfully")
            } else {
                com.codetrio.spatialflow.ui.SnackbarController.showMessage("Deletion cancelled")
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.actionMasked == MotionEvent.ACTION_DOWN) {
            val width = resources.displayMetrics.widthPixels
            BackGestureTracker.lastEdge = if (ev.rawX > width / 2f) {
                BackSwipeEdge.RIGHT
            } else {
                BackSwipeEdge.LEFT
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val AUDIO_PERMISSION_REQUEST = 100
        private const val SPLASH_DURATION = 800L
        private const val KEY_PREVIOUS_DESTINATION = "key_previous_destination"
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}

fun NavGraphBuilder.composableWithBlur(
    route: String,
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
    composable(
        route = route,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition
    ) { backStackEntry ->
        val navController = LocalNavController.current
        ScreenWrapper(
            navController = navController,
            animatedVisibilityScope = this
        ) {
            content(backStackEntry)
        }
    }
}
