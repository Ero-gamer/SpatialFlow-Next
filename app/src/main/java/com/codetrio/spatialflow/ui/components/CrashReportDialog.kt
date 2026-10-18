package com.codetrio.spatialflow.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R
import com.codetrio.spatialflow.util.TelegramHelper

/**
 * Parsed details from CrashHandler report format.
 */
private data class ParsedCrash(
    val timestamp: String? = null,
    val appVersion: String? = null,
    val osVersion: String? = null,
    val device: String? = null,
    val exceptionType: String = "App Crash",
    val exceptionMessage: String? = null
)

private fun parseCrashLog(log: String): ParsedCrash {
    var timestamp: String? = null
    var appVersion: String? = null
    var osVersion: String? = null
    var device: String? = null
    var exceptionType: String? = null
    var exceptionMessage: String? = null

    val lines = log.lines()
    for (i in lines.indices) {
        val line = lines[i].trim()
        when {
            line.startsWith("Timestamp:") -> timestamp = line.removePrefix("Timestamp:").trim()
            line.startsWith("App Version:") -> appVersion = line.removePrefix("App Version:").trim()
            line.startsWith("OS Version:") -> osVersion = line.removePrefix("OS Version:").trim()
            line.startsWith("Device:") -> device = line.removePrefix("Device:").trim()
            line.startsWith("Exception Details:") -> {
                for (j in (i + 1) until lines.size) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty()) {
                        if (nextLine.contains(":")) {
                            val parts = nextLine.split(":", limit = 2)
                            exceptionType = parts[0].substringAfterLast('.').trim()
                            exceptionMessage = parts[1].trim()
                        } else {
                            exceptionType = nextLine.substringAfterLast('.').trim()
                        }
                        break
                    }
                }
            }
        }
    }

    return ParsedCrash(
        timestamp = timestamp,
        appVersion = appVersion,
        osVersion = osVersion,
        device = device,
        exceptionType = exceptionType ?: "App Crash",
        exceptionMessage = exceptionMessage
    )
}

/**
 * Modern Material 3 Expressive Bottom Sheet for crash reporting.
 * Adheres strictly to DESIGN_SKILL.md accessibility (>=48dp touch targets, WCAG AA contrast,
 * scalable sp typography, semantic color tokens, 4dp baseline grid, and safe area insets).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CrashReportBottomSheet(
    crashLog: String,
    onDismiss: () -> Unit,
    onReport: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDetails by remember { mutableStateOf(false) }

    val parsed = remember(crashLog) { parseCrashLog(crashLog) }

    val copyCrashLog: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SpatialFlow Crash Log", crashLog)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Header: Title & Subtitle ─────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Crash Report",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "SpatialFlow closed unexpectedly",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Segmented Material 3 Expressive List ─────────────────────────
            val totalSegments = if (showDetails) 4 else 3
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                // Segment 0: Exception & Error Summary
                ListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes(index = 0, count = totalSegments),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        parsed.timestamp?.let { ts ->
                            Text(
                                text = ts,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    },
                    supportingContent = {
                        parsed.exceptionMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                ) {
                    Text(
                        text = parsed.exceptionType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Segment 1: Device & Environment Information
                ListItem(
                    onClick = {},
                    shapes = ListItemDefaults.segmentedShapes(index = 1, count = totalSegments),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    },
                    supportingContent = {
                        val envItems = listOfNotNull(
                            parsed.device?.let { "Device: $it" },
                            parsed.osVersion?.let { "OS: $it" },
                            parsed.appVersion?.let { "App: v$it" }
                        )
                        if (envItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                envItems.forEach { item ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ) {
                                        Text(
                                            text = item,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Device & Environment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Segment 2: Technical Stacktrace Accordion
                ListItem(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showDetails = !showDetails
                    },
                    shapes = ListItemDefaults.segmentedShapes(index = 2, count = totalSegments),
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Icon(
                            imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showDetails) "Collapse technical details" else "Expand technical details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (showDetails) "Tap to collapse stacktrace log" else "Tap to inspect stacktrace log",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                ) {
                    Text(
                        text = "Technical Stacktrace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Segment 3: Expandable Monospace Log Viewer (Inside Segmented List)
                AnimatedVisibility(
                    visible = showDetails,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(
                            topStart = 4.dp,
                            topEnd = 4.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        ),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        val scrollState = rememberScrollState()
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(scrollState)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = crashLog,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Action Controls (M3 Expressive Hierarchy, Touch Targets >= 48dp) ───
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Hero CTA: GitHub Issue Button (High-contrast filled + Spring Corner Morphing)
                val githubInteraction = remember { MutableInteractionSource() }
                val isGithubPressed by githubInteraction.collectIsPressedAsState()
                val githubCorner by animateDpAsState(
                    targetValue = if (isGithubPressed) 8.dp else 18.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "GitHubCornerMorph"
                )

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val issueBody = """
                            |### Describe the bug
                            |Please describe what you were doing when the crash occurred (optional):
                            |
                            |---
                            |
                            |**Crash Log:**
                            |```
                            |$crashLog
                            |```
                        """.trimMargin()

                        val githubUrl = "https://github.com/MythicalSHUB/SpatialFlow/issues/new" +
                                "?title=" + Uri.encode("App Crash Report: ${parsed.exceptionType}") +
                                "&body=" + Uri.encode(issueBody)

                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser to submit issue", Toast.LENGTH_SHORT).show()
                        }
                        onReport()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(githubCorner),
                    interactionSource = githubInteraction,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_github),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Report on GitHub",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Secondary Connected CTA: M3 Expressive ButtonGroup (Telegram & Copy Log with Pushing Neighbor Physics)
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    expandedRatio = 0.25f,
                    overflowIndicator = {}
                ) {
                    val scope = this

                    // Telegram Reporting Button
                    customItem(
                        buttonGroupContent = {
                            val telegramInteraction = remember { MutableInteractionSource() }
                            val isTelegramPressed by telegramInteraction.collectIsPressedAsState()
                            val telegramCorner by animateDpAsState(
                                targetValue = if (isTelegramPressed) 6.dp else 16.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "TelegramCornerMorph"
                            )

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val formattedLog = if (crashLog.length > 3500) {
                                        crashLog.take(3500).trimEnd() + "\n... [truncated]"
                                    } else {
                                        crashLog.trim()
                                    }
                                    val telegramMsg = "⚠️ **SPATIALFLOW CRASH REPORT**\n\n```\n$formattedLog\n```"
                                    try {
                                        TelegramHelper.openTelegram(context = context, message = telegramMsg)
                                    } catch (e: Exception) {
                                        copyCrashLog()
                                        TelegramHelper.openTelegram(context = context, domain = "SpatialFlow")
                                    }
                                    onReport()
                                },
                                modifier = with(scope) {
                                    Modifier
                                        .animateWidth(telegramInteraction)
                                        .weight(1f)
                                        .height(48.dp)
                                },
                                shape = RoundedCornerShape(telegramCorner),
                                interactionSource = telegramInteraction,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_telegram),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Telegram",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        menuContent = {}
                    )

                    // Copy Log Button
                    customItem(
                        buttonGroupContent = {
                            val copyInteraction = remember { MutableInteractionSource() }
                            val isCopyPressed by copyInteraction.collectIsPressedAsState()
                            val copyCorner by animateDpAsState(
                                targetValue = if (isCopyPressed) 6.dp else 16.dp,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "CopyCornerMorph"
                            )

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    copyCrashLog()
                                },
                                modifier = with(scope) {
                                    Modifier
                                        .animateWidth(copyInteraction)
                                        .weight(1f)
                                        .height(48.dp)
                                },
                                shape = RoundedCornerShape(copyCorner),
                                interactionSource = copyInteraction,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy crash log",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy Log",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        menuContent = {}
                    )
                }

                // Tertiary Action: Dismiss (TextButton + Spring Corner Morphing)
                val dismissInteraction = remember { MutableInteractionSource() }
                val isDismissPressed by dismissInteraction.collectIsPressedAsState()
                val dismissCorner by animateDpAsState(
                    targetValue = if (isDismissPressed) 8.dp else 16.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "DismissCornerMorph"
                )

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(dismissCorner),
                    interactionSource = dismissInteraction,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

