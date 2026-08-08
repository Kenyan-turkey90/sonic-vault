package com.sonicvault.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.compose.PlayerSurface

/**
 * Full-screen video playback layer for the now-playing screen.
 *
 * Renders the live media video from [player] onto a [PlayerSurface] and overlays a minimal set of
 * controls (return to audio, play/pause) so it reads as a proper video player rather than the
 * artwork-based audio UI. It is intentionally style-agnostic: it is drawn on top of any of the
 * player design variants selected in the app.
 */
@Composable
internal fun VideoPlayerLayer(
    player: Player,
    isPlaying: Boolean,
    mediaTitle: String?,
    onTogglePlay: () -> Unit,
    onExitVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hasVideoTrack by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    hasVideoTrack = videoSize.width > 0 && videoSize.height > 0
                }
            }
        player.addListener(listener)
        hasVideoTrack = player.videoSize.width > 0 && player.videoSize.height > 0
        onDispose { player.removeListener(listener) }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .zIndex(20f),
    ) {
        if (hasVideoTrack) {
            PlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // The current stream has no video track (audio only / local file).
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.VideocamOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(0.dp))
                    Text(
                        text = "No video track in this stream",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Spacer(Modifier.width(0.dp))
                    TextButton(onClick = onExitVideo) {
                        Text("Return to music", color = Color.White)
                    }
                }
            }
        }

        // Top bar: collapse / return to audio + current title.
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onExitVideo) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Return to music",
                    tint = Color.White,
                )
            }
            Text(
                text = mediaTitle.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            Text(
                text = "Video",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        // Center play/pause.
        if (hasVideoTrack) {
            IconButton(
                onClick = onTogglePlay,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Bottom hint: current state.
        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isPlaying) "Now playing with video" else "Paused",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}