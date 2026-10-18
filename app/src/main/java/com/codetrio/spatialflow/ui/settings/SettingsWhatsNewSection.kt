@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.codetrio.spatialflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.codetrio.spatialflow.ui.explore.ImageSemanticSize
import com.codetrio.spatialflow.ui.explore.SpatialImage
import com.codetrio.spatialflow.update.GitHubReleaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WhatsNewScreen(navController: NavController) {
    val releases by produceState<List<GitHubReleaseClient.ReleaseInfo>?>(initialValue = null) {
        withContext(Dispatchers.IO) {
            val client = GitHubReleaseClient("MythicalSHUB", "SpatialFlow")
            value = client.getAllReleases()
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("What's New") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (releases == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (releases!!.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("No releases found.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            val colorScheme = MaterialTheme.colorScheme
            val colorRoles = remember(colorScheme) {
                listOf(
                    colorScheme.primaryContainer to colorScheme.onPrimaryContainer,
                    colorScheme.secondaryContainer to colorScheme.onSecondaryContainer,
                    colorScheme.tertiaryContainer to colorScheme.onTertiaryContainer,
                    colorScheme.errorContainer to colorScheme.onErrorContainer
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp)
            ) {
                val groupedReleases = releases!!.groupBy { release ->
                    release.tagName.removePrefix("v").removePrefix("V").substringBefore('.')
                }

                groupedReleases.forEach { (majorVersion, majorReleases) ->
                    item(key = "header_$majorVersion") {
                        Text(
                            text = "Version $majorVersion Series",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }

                    itemsIndexed(majorReleases, key = { idx, rel -> "rel_${majorVersion}_${idx}_${rel.tagName}" }) { index, release ->
                        val (containerColor, onContainerColor) = colorRoles[index % colorRoles.size]

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 0, total = 2),
                                color = containerColor
                            ) {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = release.tagName,
                                            style = MaterialTheme.typography.titleLarge,
                                            color = onContainerColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = getSettingsSegmentedShape(index = 1, total = 2),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val rawChangelog = release.changelog ?: ""
                                    val lines = rawChangelog.split("\n")
                                    
                                    lines.forEach { line ->
                                        val trimmed = line.trim()
                                        if (trimmed.isEmpty()) return@forEach
                                        
                                        // Try to parse line as an Image or GIF
                                        val imgUrl = when {
                                            trimmed.startsWith("![") -> {
                                                Regex("!\\s*\\[.*?]\\s*\\((.*?)\\)").find(trimmed)?.groupValues?.getOrNull(1)
                                            }
                                            trimmed.startsWith("<img", ignoreCase = true) -> {
                                                Regex("<img.*?src=[\"'](.*?)[\"'].*?>", RegexOption.IGNORE_CASE).find(trimmed)?.groupValues?.getOrNull(1)
                                            }
                                            trimmed.startsWith("http") && trimmed.contains(Regex("\\.(gif|png|jpe?g|webp)", RegexOption.IGNORE_CASE)) -> {
                                                trimmed
                                            }
                                            else -> null
                                        }

                                        if (imgUrl != null) {
                                            SpatialImage(
                                                model = imgUrl.trim(),
                                                contentDescription = "Release Image",
                                                size = ImageSemanticSize.Cover,
                                                shape = RoundedCornerShape(12.dp),
                                                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 120.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            )
                                            return@forEach
                                        }

                                        // Clean bold markdown markers (e.g. **bold**)
                                        val cleanLine = trimmed
                                            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                                            .replace(Regex("__(.*?)__"), "$1")
                                        
                                        val primaryColor = MaterialTheme.colorScheme.primary

                                        when {
                                            // 1. Headers: #, ##, ###, ####
                                            cleanLine.startsWith("#") -> {
                                                val cleanHeader = cleanLine.replace(Regex("^#+\\s*"), "")
                                                Text(
                                                    text = cleanHeader,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryColor,
                                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                                )
                                            }
                                            // 2. Callout Boxes: Important:, Note:, Warning:
                                            cleanLine.startsWith("Important:", ignoreCase = true) ||
                                            cleanLine.startsWith("Note:", ignoreCase = true) ||
                                            cleanLine.startsWith("Warning:", ignoreCase = true) -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
                                                        .padding(vertical = 4.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(primaryColor.copy(alpha = 0.08f))
                                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(4.dp)
                                                            .fillMaxHeight()
                                                            .background(primaryColor, RoundedCornerShape(2.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = cleanLine,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        lineHeight = 20.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            // 3. Bullet points (List items)
                                            cleanLine.startsWith("•") || cleanLine.startsWith("-") || cleanLine.startsWith("*") -> {
                                                val cleanBullet = cleanLine
                                                    .removePrefix("•")
                                                    .removePrefix("-")
                                                    .removePrefix("*")
                                                    .trim()
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                                                    verticalAlignment = Alignment.Top
                                                ) {
                                                    Text(
                                                        text = "•",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = primaryColor,
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    )
                                                    Text(
                                                        text = cleanBullet,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f),
                                                        lineHeight = 20.sp
                                                    )
                                                }
                                            }
                                            // 4. Horizontal Dividers: ---
                                            cleanLine == "---" -> {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp)
                                                        .height(1.dp)
                                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                                )
                                            }
                                            // 5. Standard paragraph text
                                            else -> {
                                                Text(
                                                    text = cleanLine,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 20.sp
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
        }
    }
}
