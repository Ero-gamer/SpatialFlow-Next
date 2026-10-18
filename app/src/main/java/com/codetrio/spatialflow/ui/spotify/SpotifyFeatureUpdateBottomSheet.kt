@file:OptIn(ExperimentalMaterial3Api::class)

package com.codetrio.spatialflow.ui.spotify

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codetrio.spatialflow.R

/**
 * Announcement Bottom Sheet for version 1.8.2 app updaters announcing Spotify playlist integration.
 */
@Composable
fun SpotifyFeatureUpdateBottomSheet(
    onConnectSpotify: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val spotifyGreen = Color(0xFF1DB954)
    val youtubeRed = Color(0xFFFF0000)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dual Brand Badges Hero
            Row(
                horizontalArrangement = Arrangement.spacedBy((-12).dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = youtubeRed,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_youtube_music),
                            contentDescription = "YouTube Music",
                            tint = Color.White,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = spotifyGreen,
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_spotify_logo),
                            contentDescription = "Spotify",
                            tint = Color.Black,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Text(
                text = "Spotify Integration is Here!",
                style = MaterialTheme.typography.headlineMedium.copy(letterSpacing = (-0.5).sp),
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Now you can add Spotify and integrate your playlists directly from your Account.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Feature Highlights
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = spotifyGreen, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Dual Platform Account Sync", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Manage YouTube Music and Spotify side-by-side in 1 connected accounts portal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = spotifyGreen, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Sync Spotify Playlists & Home Feed", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Browse your Spotify Home recommendations, Liked Tracks, and custom playlists.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Connect Spotify Button (Primary)
            Button(
                onClick = onConnectSpotify,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = spotifyGreen, contentColor = Color.Black)
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_spotify_logo), contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Connect Spotify Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Open Account Settings Button (Secondary)
            OutlinedButton(
                onClick = onOpenAccount,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("View Account Settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dismiss Button
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Maybe Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
