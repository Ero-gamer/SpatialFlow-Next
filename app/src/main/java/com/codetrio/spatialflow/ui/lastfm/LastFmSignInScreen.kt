@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.lastfm

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.viewmodel.LastFmViewModel

private val LastFmRed = Color(0xFFD51007)

/**
 * Full-screen Last.fm sign-in screen designed with Material 3 Expressive.
 * Matches the GoogleSignInScreen and SpotifySignInScreen UX/UI architecture.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LastFmSignInScreen(
    lastFmViewModel: LastFmViewModel,
    onSignInSuccess: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showManualConfigSheet by remember { mutableStateOf(false) }
    var localErrorMsg by remember { mutableStateOf<String?>(null) }

    val isLoggedIn by lastFmViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val vmErrorMessage by lastFmViewModel.errorMessage.collectAsStateWithLifecycle()

    val animatedProgress by animateFloatAsState(
        targetValue = (webProgress / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "LastFmWebProgressAnimation"
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            SnackbarController.showMessage("Connected to Last.fm successfully!")
            onSignInSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = LastFmRed,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lastfm),
                                    contentDescription = "Last.fm Logo",
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column {
                            Text("Last.fm", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Scrobbler & Account Sign In", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showManualConfigSheet = true }
                    ) {
                        Icon(Icons.Default.Key, contentDescription = "Manual / API Key Login", tint = LastFmRed)
                    }
                    IconButton(
                        onClick = {
                            localErrorMsg = null
                            lastFmViewModel.clearError()
                            webViewRef?.reload()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Page")
                    }
                    IconButton(
                        onClick = {
                            localErrorMsg = null
                            isLoading = false
                            lastFmViewModel.logout()
                            CookieManager.getInstance().removeAllCookies(null)
                            webViewRef?.loadUrl(lastFmViewModel.getAuthUrl())
                            SnackbarController.showMessage("Session cleared. Refreshing Last.fm login...")
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear Session")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Animated Progress Bar
            if (webProgress in 1..99 && !isLoading) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Surface Container for Integrated Modern WebView
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.setSupportMultipleWindows(false)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 " +
                                    "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        webProgress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url ?: return false
                                        if (url.scheme == "spatialflow" && url.host == "lastfm-auth") {
                                            val token = url.getQueryParameter("token")
                                            if (!token.isNullOrBlank()) {
                                                isLoading = true
                                                lastFmViewModel.loginWithToken(token) { success, err ->
                                                    isLoading = false
                                                    if (success) {
                                                        onSignInSuccess()
                                                    } else {
                                                        localErrorMsg = err ?: "Authorization failed"
                                                    }
                                                }
                                                return true
                                            }
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (url != null && url.startsWith("spatialflow://lastfm-auth")) {
                                            val uri = Uri.parse(url)
                                            val token = uri.getQueryParameter("token")
                                            if (!token.isNullOrBlank() && !isLoading) {
                                                isLoading = true
                                                lastFmViewModel.loginWithToken(token) { success, err ->
                                                    isLoading = false
                                                    if (success) {
                                                        onSignInSuccess()
                                                    } else {
                                                        localErrorMsg = err ?: "Authorization failed"
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                loadUrl(lastFmViewModel.getAuthUrl())
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Error display if any
            val activeError = localErrorMsg ?: vmErrorMessage
            AnimatedVisibility(visible = activeError != null) {
                if (activeError != null) {
                    val isSignatureError = activeError.contains("Invalid method signature", ignoreCase = true) || activeError.contains("error\":13", ignoreCase = true)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (isSignatureError) {
                                        "Last.fm requires matching API credentials. Please configure your API Key & Secret or use Direct Login."
                                    } else {
                                        activeError
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (isSignatureError) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    FilledTonalButton(
                                        onClick = { showManualConfigSheet = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Connection Options")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom status info bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign in & tap 'Allow Access' on Last.fm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { showManualConfigSheet = true }) {
                        Text("Options", color = LastFmRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Manual Sign In & Custom API Key Configuration
    if (showManualConfigSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManualConfigSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            LastFmManualConfigSheet(
                viewModel = lastFmViewModel,
                onSuccess = {
                    showManualConfigSheet = false
                    onSignInSuccess()
                },
                onReloadWebView = {
                    showManualConfigSheet = false
                    webViewRef?.loadUrl(lastFmViewModel.getAuthUrl())
                }
            )
        }
    }
}

@Composable
private fun LastFmManualConfigSheet(
    viewModel: LastFmViewModel,
    onSuccess: () -> Unit,
    onReloadWebView: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Direct Login fields
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Custom API Credentials fields
    var customApiKey by remember { mutableStateOf(viewModel.getCustomApiKey()) }
    var customApiSecret by remember { mutableStateOf(viewModel.getCustomApiSecret()) }

    // Session Key fields
    var manualSessionUser by remember { mutableStateOf("") }
    var manualSessionKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Last.fm Connection Options",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose direct login, custom API credentials, or manual session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0; errorMsg = null },
                text = { Text("Direct Login") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1; errorMsg = null },
                text = { Text("Custom API Key") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2; errorMsg = null },
                text = { Text("Session Key") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMsg != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = errorMsg.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // Direct Username & Password Login
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Last.fm Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Last.fm Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            errorMsg = "Please enter both username and password"
                            return@Button
                        }
                        isLoading = true
                        errorMsg = null
                        viewModel.loginWithCredentials(username, password) { success, error ->
                            isLoading = false
                            if (success) {
                                onSuccess()
                            } else {
                                errorMsg = error ?: "Login failed"
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Sign In Directly", fontWeight = FontWeight.Bold)
                    }
                }
            }

            1 -> {
                // Custom API Key Configuration
                Text(
                    text = "If you have your own registered Last.fm API Key, configure it here:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customApiKey,
                    onValueChange = { customApiKey = it },
                    label = { Text("API Key (32 hex characters)") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customApiSecret,
                    onValueChange = { customApiSecret = it },
                    label = { Text("Shared Secret (32 hex characters)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.last.fm/api/account/create"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Get Free API Key from Last.fm")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        viewModel.saveCustomApiCredentials(customApiKey, customApiSecret)
                        SnackbarController.showMessage("API credentials saved! Reloading...")
                        onReloadWebView()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("Save & Reload Sign In", fontWeight = FontWeight.Bold)
                }
            }

            2 -> {
                // Direct Session Key
                Text(
                    text = "If you already have a 32-character session key from another client, paste it below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualSessionUser,
                    onValueChange = { manualSessionUser = it },
                    label = { Text("Last.fm Username") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = manualSessionKey,
                    onValueChange = { manualSessionKey = it },
                    label = { Text("Session Key (sk)") },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (manualSessionUser.isBlank() || manualSessionKey.isBlank()) {
                            errorMsg = "Please enter username and session key"
                            return@Button
                        }
                        isLoading = true
                        errorMsg = null
                        viewModel.loginWithSessionKey(manualSessionUser, manualSessionKey) { success, err ->
                            isLoading = false
                            if (success) {
                                onSuccess()
                            } else {
                                errorMsg = err ?: "Session validation failed"
                            }
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text("Connect with Session Key", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
