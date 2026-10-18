@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.settings.tidal

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.data.tidal.TidalAccountManager
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.util.QualityModeSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TidalCyan = Color(0xFF00E5FF)

/**
 * Full-screen Tidal sign-in screen designed with Material 3 Expressive.
 * Matches SpotifySignInScreen architecture:
 *  - Embedded WebView executing OAuth 2.0 PKCE code exchange (`https://login.tidal.com/authorize`)
 *  - Intercepts callback `https://tidal.com/android/login/auth?code=...`
 *  - Exchanges PKCE code for access token & refresh token
 *  - Supports manual token entry sheet for power users
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TidalSignInScreen(
    onSignInSuccess: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showManualTokenSheet by remember { mutableStateOf(false) }
    var hasCompletedAuth by remember { mutableStateOf(false) }

    val pkce = remember { TidalAccountManager.buildPkceChallenge() }
    val initialLoginUrl = remember(pkce) { pkce.authUrl }

    val animatedProgress by animateFloatAsState(
        targetValue = (webProgress / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TidalWebProgress"
    )

    fun handleCodeExchange(code: String) {
        if (hasCompletedAuth) return
        hasCompletedAuth = true
        isLoading = true

        scope.launch {
            val tokenResult = withContext(Dispatchers.IO) {
                TidalAccountManager.exchangePkceCode(code, pkce.verifier, pkce.uniqueKey)
            }

            if (tokenResult != null) {
                val subscription = withContext(Dispatchers.IO) {
                    TidalAccountManager.fetchSubscription(tokenResult.accessToken, tokenResult.userId ?: 0L)
                }

                QualityModeSelector.setTidalAccount(
                    context = context,
                    accessToken = tokenResult.accessToken,
                    refreshToken = tokenResult.refreshToken,
                    accountName = tokenResult.username,
                    subscription = if (subscription == TidalAccountManager.Subscription.PREMIUM) "PREMIUM" else "FREE",
                    userId = tokenResult.userId,
                    countryCode = tokenResult.countryCode
                )

                isLoading = false
                val subLabel = if (subscription == TidalAccountManager.Subscription.PREMIUM) "Hi-Fi FLAC" else "Standard"
                SnackbarController.showMessage("Tidal connected: $subLabel active!")
                onSignInSuccess()
            } else {
                isLoading = false
                hasCompletedAuth = false
                SnackbarController.showMessage("Tidal token exchange failed. Please try again.")
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
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
                            color = Color(0xFF111111),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_tidal),
                                    contentDescription = "Tidal Logo",
                                    tint = TidalCyan,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column {
                            Text(
                                "Tidal",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Hi-Fi Lossless Sign In",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showManualTokenSheet = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Manual Token Entry", tint = TidalCyan)
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Page")
                    }
                    IconButton(onClick = {
                        hasCompletedAuth = false
                        isLoading = false
                        QualityModeSelector.clearTidalAccount(context)
                        CookieManager.getInstance().removeAllCookies(null)
                        webViewRef?.loadUrl(initialLoginUrl)
                        SnackbarController.showMessage("Session reset. Reloading Tidal login...")
                    }) {
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
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setBackgroundColor(android.graphics.Color.parseColor("#000000"))

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportMultipleWindows(true)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                                try {
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                    }
                                } catch (e: Throwable) {
                                    android.util.Log.w("TidalWebView", "Header allowlist not supported: ${e.message}")
                                }

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        super.onProgressChanged(view, newProgress)
                                        webProgress = newProgress
                                    }

                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: android.os.Message?
                                    ): Boolean {
                                        val mainWebView = this@apply
                                        val popupWebView = WebView(ctx).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.userAgentString = mainWebView.settings.userAgentString
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    targetView: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val popupUrl = request?.url?.toString() ?: return false
                                                    mainWebView.loadUrl(popupUrl)
                                                    return true
                                                }
                                            }
                                        }
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = popupWebView
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString().orEmpty()
                                        if (url.startsWith("https://tidal.com/android/login/auth") || url.startsWith("tidal://login/auth")) {
                                            val uri = Uri.parse(url)
                                            val code = uri.getQueryParameter("code")
                                            if (!code.isNullOrBlank()) {
                                                handleCodeExchange(code)
                                                return true
                                            }
                                        }
                                        return super.shouldOverrideUrlLoading(view, request)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val current = url.orEmpty()
                                        if (current.startsWith("https://tidal.com/android/login/auth") || current.startsWith("tidal://login/auth")) {
                                            val uri = Uri.parse(current)
                                            val code = uri.getQueryParameter("code")
                                            if (!code.isNullOrBlank()) {
                                                handleCodeExchange(code)
                                            }
                                        }
                                    }
                                }

                                loadUrl(initialLoginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showManualTokenSheet) {
        ManualTidalTokenSheet(
            onDismiss = { showManualTokenSheet = false },
            onConnect = { token ->
                showManualTokenSheet = false
                isLoading = true
                scope.launch {
                    val session = withContext(Dispatchers.IO) {
                        TidalAccountManager.buildSessionFromBearer(token)
                    }
                    val sub = if (session?.userId != null) {
                        withContext(Dispatchers.IO) {
                            TidalAccountManager.fetchSubscription(token, session.userId)
                        }
                    } else TidalAccountManager.Subscription.PREMIUM

                    QualityModeSelector.setTidalAccount(
                        context = context,
                        accessToken = token,
                        refreshToken = null,
                        accountName = session?.username,
                        subscription = if (sub == TidalAccountManager.Subscription.PREMIUM) "PREMIUM" else "FREE",
                        userId = session?.userId,
                        countryCode = session?.countryCode
                    )
                    isLoading = false
                    SnackbarController.showMessage("Tidal Bearer token connected!")
                    onSignInSuccess()
                }
            }
        )
    }
}

@Composable
private fun ManualTidalTokenSheet(
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = TidalCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = TidalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "Manual Tidal Token",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "OAuth Bearer token login",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "Enter your Tidal user access token or Web Bearer token obtained from listen.tidal.com Network tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                label = { Text("Tidal Access Token") },
                placeholder = { Text("Bearer token string") },
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            tokenInput = clip.trim()
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (tokenInput.isNotBlank()) onConnect(tokenInput.trim())
                })
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (tokenInput.isNotBlank()) {
                        onConnect(tokenInput.trim())
                    }
                },
                enabled = tokenInput.length >= 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                    Text("Connect Tidal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
