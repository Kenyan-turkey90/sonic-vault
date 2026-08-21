/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sonicvault.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.sonicvault.app.R
import com.sonicvault.app.models.MediaMetadata

/**
 * Small pill chip shown on the Now Playing screen (video mode off) that switches the
 * current stream to a combined audio+video stream, YouTube-Music style.
 */
@Composable
internal fun VideoModeToggleChip(
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.switch_to_video),
    iconRes: Int = R.drawable.videocam,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * Full-screen video player overlay. When video mode is on and the sheet is expanded, this
 * replaces the artwork/background with the actual video rendered from the service player
 * (combined audio+video stream), with a Now Playing header and transport controls that
 * mirror the audio player (shuffle / previous / play-pause / next / repeat) plus a
 * fullscreen toggle.
 */
@Composable
internal fun VideoModeOverlay(
    player: Player,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    isLoading: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    guestNotice: String? = null,
    onSignIn: (() -> Unit)? = null,
    onSwipeUpToSkip: (() -> Unit)? = null,
) {
    val isLandscape =
        LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Resize mode + resolution controls for the fullscreen video surface.
    // These work on any design style, not just V8/V9.
    val resizeMode = remember { mutableIntStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var availableResolutions by remember { mutableStateOf(emptyList<VideoResolution>()) }
    var selectedResolutionHeight by remember { mutableStateOf<Int?>(null) }

    // Discover available video resolutions from the live player's tracks.
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val resolutions = mutableListOf<VideoResolution>()
                    for (groupIndex in 0 until tracks.groups.size) {
                        val group = tracks.groups[groupIndex]
                        if (group.type == C.TRACK_TYPE_VIDEO) {
                            for (trackIndex in 0 until group.length) {
                                val format = group.getTrackFormat(trackIndex)
                                val height = format.height
                                if (height > 0) {
                                    resolutions.add(
                                        VideoResolution(
                                            height = height,
                                            trackGroupIndex = groupIndex,
                                            trackIndexInGroup = trackIndex,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    availableResolutions = resolutions.sortedByDescending { it.height }
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Apply the manual resolution override to the main player when the picker changes.
    LaunchedEffect(selectedResolutionHeight, player) {
        if (selectedResolutionHeight == null) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            return@LaunchedEffect
        }
        val tracks = player.currentTracks
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                if (format.height == selectedResolutionHeight) {
                    val override =
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                    player.trackSelectionParameters =
                        player.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(override)
                            .build()
                    return@LaunchedEffect
                }
            }
        }
    }

    // Auto-hide the controls only in fullscreen (landscape) after a few seconds of
    // inactivity. In portrait the controls stay visible.
    var controlsLastInteractedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsLastInteractedAt, isLoading, isLandscape) {
        if (isLoading) {
            controlsVisible = true
            return@LaunchedEffect
        }
        if (!isLandscape) {
            controlsVisible = true
            return@LaunchedEffect
        }
        controlsVisible = true
        delay(3500L)
        if (controlsLastInteractedAt <= System.currentTimeMillis() - 3500L) {
            controlsVisible = false
        }
    }
    val showControls = if (isLandscape) (controlsVisible || isLoading) else true

    fun pokeControls() {
        controlsLastInteractedAt = System.currentTimeMillis()
        controlsVisible = true
    }

    var swipeAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { pokeControls() })
                    detectVerticalDragGestures(
                        onDragStart = { pokeControls() },
                        onDragEnd = {
                            pokeControls()
                            swipeAccumulator = 0f
                        },
                        onVerticalDrag = { _, dragAmount ->
                            swipeAccumulator += dragAmount
                            if (swipeAccumulator < -140f) {
                                swipeAccumulator = 0f
                                onSwipeUpToSkip?.invoke()
                            }
                        },
                    )
                },
    ) {
        ContentFrame(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = resizeMode.intValue.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = Modifier.fillMaxSize(),
        )

        // Buffering spinner
        if (isLoading) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        if (guestNotice != null && onSignIn != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.videocam),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = guestNotice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onSignIn) {
                    Text(text = stringResource(R.string.login))
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.fillMaxSize(),
        ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
        // Now Playing header (top-left)
        mediaMetadata?.let { metadata ->
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 20.dp, top = 16.dp, end = 140.dp),
            ) {
                Text(
                    text = stringResource(R.string.now_playing).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = metadata.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.artists.isNotEmpty()) {
                    Text(
                        text = metadata.artists.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Exit (switch to audio) chip (top-right)
        Surface(
            onClick = {
                pokeControls()
                onExit()
            },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.switch_to_audio),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }

        // Resize mode + quality overlay (below the exit chip on the top-right).
        VideoPlayerOverlayControls(
            resizeMode = resizeMode,
            availableResolutions = availableResolutions,
            selectedResolutionHeight = selectedResolutionHeight,
            onResolutionSelected = { selectedResolutionHeight = it },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 76.dp, end = 8.dp),
        )

        // Bottom overlay: progress bar + transport controls (below the video).
        // Centered with a bounded width so it reads as a compact block in both orientations.
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            // Fullscreen toggle row (aligned end, above the progress bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                VideoOverlayControl(
                    iconRes = if (isLandscape) R.drawable.fullscreen_exit else R.drawable.fullscreen,
                    contentDescription =
                        stringResource(
                            if (isLandscape) {
                                R.string.video_exit_fullscreen
                            } else {
                                R.string.video_fullscreen
                            },
                        ),
                    onClick = onToggleFullscreen,
                    small = true,
                    modifier = Modifier.padding(end = 8.dp, bottom = 6.dp),
                )
            }

            // Seekable progress bar with elapsed / total duration
            val safeDuration = if (duration > 0L && duration != C.TIME_UNSET) duration else 1L
            val displayedSliderPosition = sliderPosition ?: position
            val progressFraction =
                (displayedSliderPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(displayedSliderPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 16.dp),
                )
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        onSliderValueChange((fraction * safeDuration.toFloat()).toLong())
                    },
                    onValueChangeFinished = onSliderValueChangeFinished,
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color(0xFF40CC71),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        ),
                )
                Text(
                    text = formatDuration(safeDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(end = 16.dp),
                )
            }

            // Transport controls (matching the audio player's row)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                VideoOverlayControl(
                    iconRes = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    contentDescription = stringResource(R.string.video_shuffle),
                    onClick = onToggleShuffle,
                    tint = if (shuffleModeEnabled) Color(0xFF40CC71) else Color.White,
                )
                VideoOverlayControl(
                    iconRes = R.drawable.skip_previous,
                    contentDescription = stringResource(R.string.widget_previous),
                    onClick = onSkipPrevious,
                )
                VideoOverlayControl(
                    iconRes = if (isPlaying) R.drawable.pause else R.drawable.play,
                    contentDescription = stringResource(R.string.widget_pause),
                    onClick = onPlayPause,
                    primary = true,
                )
                VideoOverlayControl(
                    iconRes = R.drawable.skip_next,
                    contentDescription = stringResource(R.string.next),
                    onClick = onSkipNext,
                )
VideoOverlayControl(
                    iconRes =
                        when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            Player.REPEAT_MODE_OFF,
                            Player.REPEAT_MODE_ALL,
                            -> R.drawable.repeat
                            else -> R.drawable.repeat
                        },
                    contentDescription = stringResource(R.string.video_repeat),
                    onClick = onToggleRepeat,
                    tint =
                        if (repeatMode != Player.REPEAT_MODE_OFF) {
                            Color(0xFF40CC71)
                        } else {
                            Color.White
                        },
                )
                PlayerDownloadButton(
                    mediaMetadata = mediaMetadata,
                    shape = CircleShape,
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    iconSize = 26.dp,
                    iconTint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        }
        }
    }
}
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L || durationMs == C.TIME_UNSET) return "0:00"
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes >= 60L) {
        val hours = minutes / 60L
        "%d:%02d:%02d".format(hours, minutes % 60L, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun VideoOverlayControl(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    small: Boolean = false,
    tint: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    val buttonSize = if (small) 44.dp else if (primary) 84.dp else 52.dp
    val iconSize = if (small) 24.dp else if (primary) 44.dp else 26.dp
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = tint,
        modifier = modifier.size(buttonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
