@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings.sourcepool

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.codetrio.spatialflow.R
import androidx.compose.ui.res.painterResource
import com.codetrio.spatialflow.data.sourcepool.HealthVerdict
import com.codetrio.spatialflow.data.sourcepool.SourcePoolClient
import com.codetrio.spatialflow.data.sourcepool.SourcePoolSummary
import com.codetrio.spatialflow.ui.components.SegmentedGroup
import com.codetrio.spatialflow.ui.components.SegmentedRow
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexBody
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexDisplay
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexLabel
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexTitle
import com.codetrio.spatialflow.ui.theme.GoogleSansFlexUI

@Composable
fun SourcePoolStatusScreen(
    navController: NavController,
    viewModel: SourcePoolViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing = (uiState as? SourcePoolUiState.Success)?.isRefreshing == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_archivetune),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "Source Pool",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = GoogleSansFlexTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                        )
                        PulsingOperationalDot()
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate back",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                actions = {
                    val rotateTransition = rememberInfiniteTransition(label = "rotate_refresh")
                    val angle by rotateTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(900, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "spin"
                    )

                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !isRefreshing,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh source pool status",
                            modifier = (if (isRefreshing) Modifier.rotate(angle) else Modifier).size(26.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is SourcePoolUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            LoadingIndicator(
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Checking source status…",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = GoogleSansFlexTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 20.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Connecting to edge gateway",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansFlexBody,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is SourcePoolUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "Connection Failed",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = GoogleSansFlexTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansFlexBody,
                                    fontSize = 15.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            FilledTonalButton(
                                onClick = { viewModel.refresh() },
                                modifier = Modifier.defaultMinSize(minHeight = 52.dp)
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = GoogleSansFlexUI,
                                        fontSize = 16.sp
                                    )
                                )
                            }
                        }
                    }
                }

                is SourcePoolUiState.Success -> {
                    CompactDashboardContent(
                        state = state,
                        isRefreshing = isRefreshing,
                        onDisconnectKey = { viewModel.disconnectKey() },
                        onSaveKey = { viewModel.saveApiKey(it) }
                    )
                }
            }

            AnimatedVisibility(
                visible = isRefreshing,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            }
        }
    }
}

@Composable
private fun CompactDashboardContent(
    state: SourcePoolUiState.Success,
    isRefreshing: Boolean,
    onDisconnectKey: () -> Unit,
    onSaveKey: (String) -> Unit
) {
    var showKeyDialog by remember { mutableStateOf(false) }

    if (showKeyDialog) {
        var enteredKey by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = {
                Text(
                    text = "SourcePool Access Token",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = GoogleSansFlexTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your personal token. It will be securely stored using device hardware Keystore (AES-256 GCM).",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = GoogleSansFlexBody,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = enteredKey,
                        onValueChange = { enteredKey = it },
                        label = { Text("API Token", fontFamily = GoogleSansFlexLabel) },
                        placeholder = { Text("atp_...", fontFamily = GoogleSansFlexBody) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveKey(enteredKey.trim())
                        showKeyDialog = false
                    },
                    enabled = enteredKey.trim().isNotEmpty(),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Save", fontFamily = GoogleSansFlexUI, fontSize = 15.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showKeyDialog = false },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Cancel", fontFamily = GoogleSansFlexUI, fontSize = 15.sp)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "compact_hero") {
            CompactHeroBanner(
                summary = state.summary,
                isRefreshing = isRefreshing,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }

        item(key = "group_mode") {
            SegmentedGroup(title = "Mode & Access") {
                if (state.isKeyConfigured) {
                    item {
                        SegmentedRow(
                            minHeight = 72.dp,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_token),
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = "Personal Token Active",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = GoogleSansFlexTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp
                                    )
                                )
                            },
                            subtitle = {
                                Text(
                                    text = "Hardware Keystore secured",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansFlexBody,
                                        fontSize = 14.sp
                                    )
                                )
                            },
                            trailingContent = {
                                TextButton(
                                    onClick = onDisconnectKey,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text(
                                        text = "Disconnect",
                                        color = MaterialTheme.colorScheme.error,
                                        fontFamily = GoogleSansFlexUI,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        )
                    }
                    item {
                        SegmentedRow(
                            minHeight = 72.dp,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_token),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = "Update Token",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = GoogleSansFlexTitle,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 17.sp
                                    )
                                )
                            },
                            subtitle = {
                                Text(
                                    text = "Replace stored personal token",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = GoogleSansFlexBody,
                                        fontSize = 14.sp
                                    )
                                )
                            },
                            trailingContent = {
                                TonalPill(
                                    text = "Change",
                                    color = MaterialTheme.colorScheme.primary,
                                    icon = Icons.Rounded.Refresh
                                )
                            },
                            onClick = { showKeyDialog = true }
                        )
                    }
                } else {
                    if (SourcePoolClient.isCommunityGatewayConfigured) {
                        item {
                            SegmentedRow(
                                minHeight = 72.dp,
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF2E7D32).copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                title = {
                                    Text(
                                        text = "Community Gateway",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansFlexTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 17.sp
                                        )
                                    )
                                },
                                subtitle = {
                                    Text(
                                        text = "Default • Protected via Edge Gateway",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansFlexBody,
                                            fontSize = 14.sp
                                        )
                                    )
                                },
                                trailingContent = {
                                    TonalPill(
                                        text = "Active",
                                        color = Color(0xFF2E7D32),
                                        icon = Icons.Rounded.CheckCircle
                                    )
                                }
                            )
                        }
                        item {
                            SegmentedRow(
                                minHeight = 72.dp,
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_token),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                title = {
                                    Text(
                                        text = "Custom Token (BYOK)",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansFlexTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 17.sp
                                        )
                                    )
                                },
                                subtitle = {
                                    Text(
                                        text = "Optional: override with private token",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansFlexBody,
                                            fontSize = 14.sp
                                        )
                                    )
                                },
                                trailingContent = {
                                    TonalPill(
                                        text = "Connect",
                                        color = MaterialTheme.colorScheme.primary,
                                        icon = Icons.Rounded.Lock
                                    )
                                },
                                onClick = { showKeyDialog = true }
                            )
                        }
                    } else {
                        item {
                            SegmentedRow(
                                minHeight = 72.dp,
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFFFFA000).copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_token),
                                            contentDescription = null,
                                            tint = Color(0xFFFFA000),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                },
                                title = {
                                    Text(
                                        text = "Personal Token Required",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansFlexTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 17.sp
                                        )
                                    )
                                },
                                subtitle = {
                                    Text(
                                        text = "Unofficial build: connect token to stream",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = GoogleSansFlexBody,
                                            fontSize = 14.sp
                                        )
                                    )
                                },
                                trailingContent = {
                                    TonalPill(
                                        text = "Connect",
                                        color = MaterialTheme.colorScheme.primary,
                                        icon = Icons.Rounded.Lock
                                    )
                                },
                                onClick = { showKeyDialog = true }
                            )
                        }
                    }
                }
            }
        }

        if (state.categories.isNotEmpty()) {
            item(key = "group_services_compact") {
                SegmentedGroup(title = "Services") {
                    state.categories.forEach { category ->
                        val s = category.service.lowercase()
                        val (serviceColor, serviceDrawable) = when {
                            s.contains("qobuz") -> Pair(Color(0xFF0288D1), R.drawable.ic_qobuz)
                            s.contains("tidal") -> Pair(Color(0xFF00ACC1), R.drawable.ic_tidal)
                            s.contains("apple") -> Pair(Color(0xFFE53935), R.drawable.ic_apple_music)
                            s.contains("deezer") -> Pair(Color(0xFF8E24AA), R.drawable.ic_deezer)
                            s.contains("spotify") -> Pair(Color(0xFF1DB954), R.drawable.ic_spotify_logo)
                            s.contains("youtube") || s.contains("ytm") -> Pair(Color(0xFFFF0000), R.drawable.ic_youtube_music)
                            else -> Pair(MaterialTheme.colorScheme.primary, R.drawable.ic_archivetune)
                        }

                        val (healthColor, healthIcon) = when {
                            category.isOperational -> Pair(Color(0xFF2E7D32), Icons.Rounded.CheckCircle)
                            category.isDown -> Pair(MaterialTheme.colorScheme.error, Icons.Rounded.ErrorOutline)
                            else -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Sensors)
                        }

                        item {
                            SegmentedRow(
                                minHeight = 68.dp,
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(serviceColor.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = serviceDrawable),
                                            contentDescription = null,
                                            tint = serviceColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                },
                                title = {
                                    Text(
                                        text = category.label.ifBlank {
                                            "${category.service.uppercase()} ${category.kind.uppercase()}"
                                        },
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GoogleSansFlexTitle,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        )
                                    )
                                },
                                subtitle = {
                                    val details = buildString {
                                        append("${category.alive}/${category.total} Online")
                                        if (category.premium > 0) {
                                            append(" • ${category.premium} Premium")
                                        }
                                        category.uptimePct?.let {
                                            append(" • %.0f%% Uptime".format(it))
                                        }
                                    }
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = GoogleSansFlexBody,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    TonalPill(
                                        text = category.health.uppercase(),
                                        color = healthColor,
                                        icon = healthIcon
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHeroBanner(
    summary: SourcePoolSummary,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor, statusIcon) = when (summary.overallHealth) {
        HealthVerdict.OPERATIONAL -> Triple("Operational", Color(0xFF2E7D32), Icons.Rounded.CheckCircle)
        HealthVerdict.DEGRADED -> Triple("Degraded", Color(0xFFE65100), Icons.Rounded.WarningAmber)
        HealthVerdict.DOWN -> Triple("Outage", MaterialTheme.colorScheme.error, Icons.Rounded.ErrorOutline)
        HealthVerdict.UNKNOWN -> Triple("Checking…", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.Refresh)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Text(
                        text = if (isRefreshing) "Refreshing…" else statusText,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = GoogleSansFlexTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TonalPill(
                    text = "${summary.totalAlive}/${summary.totalEntries} Sources",
                    color = statusColor,
                    icon = Icons.Rounded.Sensors
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip(
                    label = "ONLINE",
                    value = "${summary.totalAlive}",
                    icon = Icons.Rounded.Sensors,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "PREMIUM",
                    value = "${summary.totalPremium}",
                    icon = Icons.Rounded.Star,
                    color = Color(0xFF8E24AA),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "UPTIME",
                    value = summary.averageUptimePct?.let { "%.0f%%".format(it) } ?: "—",
                    icon = Icons.Rounded.Speed,
                    color = Color(0xFF0288D1),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = GoogleSansFlexDisplay,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                    fontSize = 22.sp
                ),
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = GoogleSansFlexLabel,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TonalPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = GoogleSansFlexUI,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = color
            )
        }
    }
}

@Composable
private fun PulsingOperationalDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.size(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32).copy(alpha = alphaAnim * 0.3f))
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E7D32).copy(alpha = alphaAnim))
        )
    }
}
