@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.codetrio.spatialflow.ui.settings.deezer

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Warning
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
import com.codetrio.spatialflow.data.deezer.DeezerAudioProvider
import com.codetrio.spatialflow.ui.SnackbarController
import com.codetrio.spatialflow.util.QualityModeSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "DeezerSignIn"
private val DeezerPurple = Color(0xFFA238FF)
private const val DEEZER_LOGIN_URL = "https://www.deezer.com/login"

/** Domains to probe for the `arl` cookie — covers all Deezer variants. */
private val COOKIE_PROBE_DOMAINS = listOf(
    "https://www.deezer.com",
    "https://deezer.com",
    "https://connect.deezer.com",
    "https://auth.deezer.com"
)

/**
 * Full-screen Deezer sign-in screen designed with Material 3 Expressive.
 * Matches SpotifySignInScreen architecture:
 *  - Embedded WebView capturing `arl` cookie on successful login
 *  - Manual ARL token entry sheet for users in geo-restricted countries (e.g. India)
 *  - Verified FLAC account credential storage via QualityModeSelector
 *
 * Geo-block handling:
 *  - Deezer is unavailable in some countries (e.g. India). After login,
 *    the WebView may redirect to a blank or error page. A background
 *    coroutine polls for the `arl` cookie every 1.5 seconds so even
 *    if page-event based detection fails the cookie is still captured.
 *  - If the page appears blank/geo-blocked post-login, a prominent
 *    banner guides the user to the manual ARL entry.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerSignInScreen(
    onSignInSuccess: () -> Unit,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var webProgress by remember { mutableIntStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showManualArlSheet by remember { mutableStateOf(false) }
    var hasCapturedArl by remember { mutableStateOf(false) }
    var showGeoBlockBanner by remember { mutableStateOf(false) }
    var pagesLoaded by remember { mutableIntStateOf(0) }

    val animatedProgress by animateFloatAsState(
        targetValue = (webProgress / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "DeezerWebProgress"
    )

    /**
     * Try to extract the `arl` cookie from CookieManager across multiple domains.
     * Returns the token if found, null otherwise.
     */
    fun extractArlFromCookies(): String? {
        val cookieManager = CookieManager.getInstance()
        for (domain in COOKIE_PROBE_DOMAINS) {
            val cookies = cookieManager.getCookie(domain) ?: continue
            if (cookies.contains("arl=")) {
                val arl = cookies.split(";")
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("arl=") }
                    ?.substringAfter("arl=")
                if (!arl.isNullOrBlank() && arl.length >= 20) {
                    Log.d(TAG, "ARL cookie found from domain: $domain (len=${arl.length})")
                    return arl
                }
            }
        }
        return null
    }

    fun handleArlFound(arl: String) {
        if (hasCapturedArl) return
        hasCapturedArl = true
        isLoading = true
        showGeoBlockBanner = false
        Log.d(TAG, "ARL captured (len=${arl.length}), verifying with Deezer API...")

        scope.launch {
            val user = withContext(Dispatchers.IO) {
                DeezerAudioProvider.verifyArl(arl)
            }
            isLoading = false
            if (user != null) {
                Log.d(TAG, "ARL verified: ${user.name} (lossless=${user.lossless})")
                QualityModeSelector.setDeezerAccount(
                    context = context,
                    arl = arl,
                    accountName = user.name,
                    premium = user.lossless
                )
                SnackbarController.showMessage("Deezer connected: ${user.name} (${if (user.lossless) "HiFi FLAC" else "Free"})")
                onSignInSuccess()
            } else {
                Log.w(TAG, "ARL verification failed — invalid or expired token")
                hasCapturedArl = false
                SnackbarController.showMessage("Deezer verification failed. Try manual ARL entry or check credentials.")
            }
        }
    }

    // Background ARL cookie poller — catches cookies even when page events don't fire
    LaunchedEffect(hasCapturedArl) {
        if (hasCapturedArl) return@LaunchedEffect
        while (isActive && !hasCapturedArl) {
            delay(1500L)
            val arl = extractArlFromCookies()
            if (arl != null && !hasCapturedArl) {
                Log.d(TAG, "Background poller found ARL cookie")
                handleArlFound(arl)
                break
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
                            color = DeezerPurple,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_deezer),
                                    contentDescription = "Deezer Logo",
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column {
                            Text(
                                "Deezer",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Lossless FLAC Sign In",
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
                    IconButton(onClick = { showManualArlSheet = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Manual ARL Entry", tint = DeezerPurple)
                    }
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload Page")
                    }
                    IconButton(onClick = {
                        hasCapturedArl = false
                        isLoading = false
                        showGeoBlockBanner = false
                        pagesLoaded = 0
                        QualityModeSelector.clearDeezerAccount(context)
                        CookieManager.getInstance().removeAllCookies(null)
                        webViewRef?.loadUrl(DEEZER_LOGIN_URL)
                        SnackbarController.showMessage("Cookies cleared. Refreshing Deezer login...")
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

            // Geo-block / blank-page banner
            AnimatedVisibility(
                visible = showGeoBlockBanner && !hasCapturedArl,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Deezer may not be available in your region",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "If the page is blank or not loading, Deezer is geo-blocked in your country. " +
                                    "Use Manual ARL Entry instead — log in via a desktop browser (with VPN if needed), " +
                                    "copy the 'arl' cookie, and paste it below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                        Button(
                            onClick = { showManualArlSheet = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Enter ARL Manually", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
                                setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.setSupportMultipleWindows(true)
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true

                                // Use standard clean Chrome Mobile User-Agent without WebView identifiers
                                settings.userAgentString =
                                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                                // Strip X-Requested-With header to prevent Google & Cloudflare "browser is unsecure" blocks
                                try {
                                    if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        androidx.webkit.WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                    }
                                } catch (e: Throwable) {
                                    Log.w("DeezerWebView", "Header allowlist not supported: ${e.message}")
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
                                    private fun inspectAllDomainCookies() {
                                        if (hasCapturedArl) return
                                        val arl = extractArlFromCookies()
                                        if (arl != null) {
                                            post { handleArlFound(arl) }
                                        }
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        Log.d(TAG, "Page started: $url")
                                        view?.evaluateJavascript(
                                            "window.chrome = window.chrome || { runtime: {} };",
                                            null
                                        )
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        Log.d(TAG, "Page finished: $url")
                                        view?.evaluateJavascript(
                                            "window.chrome = window.chrome || { runtime: {} };",
                                            null
                                        )
                                        inspectAllDomainCookies()

                                        // Detect geo-block: after the login URL redirects,
                                        // check if the page body is empty or shows a non-login URL
                                        pagesLoaded++
                                        val currentUrl = url.orEmpty()
                                        val isPostLogin = !currentUrl.contains("/login") && pagesLoaded > 1
                                        if (isPostLogin && !hasCapturedArl) {
                                            // Check page content to detect blank/geo-blocked state
                                            view?.evaluateJavascript(
                                                "(function() { return document.body ? document.body.innerText.length : 0; })()"
                                            ) { bodyLength ->
                                                val len = bodyLength?.replace("\"", "")?.toIntOrNull() ?: 0
                                                Log.d(TAG, "Post-login body text length: $len (url=$currentUrl)")
                                                if (len < 50) {
                                                    // Page is blank or nearly empty — likely geo-blocked
                                                    showGeoBlockBanner = true
                                                    Log.w(TAG, "Geo-block detected: blank page after login redirect")
                                                }
                                            }
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        inspectAllDomainCookies()
                                        return false
                                    }

                                    override fun shouldInterceptRequest(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): android.webkit.WebResourceResponse? {
                                        val reqUrl = request?.url?.toString()
                                        if (reqUrl != null && (reqUrl.contains("deezer.com") || reqUrl.contains("ajax/gw-light.php"))) {
                                            inspectAllDomainCookies()
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        val isMainFrame = request?.isForMainFrame == true
                                        if (isMainFrame) {
                                            Log.w(TAG, "WebView main frame error: code=${error?.errorCode}, desc=${error?.description}")
                                            showGeoBlockBanner = true
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: android.webkit.WebResourceResponse?
                                    ) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        val isMainFrame = request?.isForMainFrame == true
                                        if (isMainFrame && (errorResponse?.statusCode ?: 200) >= 400) {
                                            Log.w(TAG, "WebView HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                                            showGeoBlockBanner = true
                                        }
                                    }
                                }

                                loadUrl(DEEZER_LOGIN_URL)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    if (showManualArlSheet) {
        ManualDeezerArlSheet(
            onDismiss = { showManualArlSheet = false },
            onConnect = { arl ->
                showManualArlSheet = false
                handleArlFound(arl)
            }
        )
    }
}

@Composable
private fun ManualDeezerArlSheet(
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    val context = LocalContext.current
    var arlInput by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

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
                    color = DeezerPurple.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = DeezerPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        "Manual Deezer ARL",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Essential for region-restricted countries",
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
                        "In your browser: Open deezer.com (use VPN if needed), log in, press F12 ➔ Application ➔ Cookies ➔ copy the value of 'arl'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = arlInput,
                onValueChange = { arlInput = it },
                label = { Text("Deezer ARL Cookie") },
                placeholder = { Text("192-character ARL token") },
                singleLine = false,
                maxLines = 3,
                trailingIcon = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrBlank()) {
                            arlInput = clip.trim()
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (arlInput.isNotBlank()) onConnect(arlInput.trim())
                })
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (arlInput.isNotBlank()) {
                        onConnect(arlInput.trim())
                    }
                },
                enabled = arlInput.length >= 20 && !isChecking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Text("Verify & Connect Deezer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
