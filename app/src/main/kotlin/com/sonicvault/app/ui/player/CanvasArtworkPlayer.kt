/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sonicvault.app.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.sonicvault.app.innertube.YouTube
import com.sonicvault.app.utils.StreamClientUtils
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.Locale

private const val CanvasPlaybackStallCheckIntervalMs = 1_000L
private const val CanvasPlaybackStallTimeoutMs = 5_000L

/** Describes an available video resolution track. */
data class VideoResolution(
    val height: Int,
    val trackGroupIndex: Int,
    val trackIndexInGroup: Int,
) {
    val label: String
        get() = "${height}p"
}

@Composable
internal fun CanvasArtworkPlayer(
    primaryUrl: String?,
    fallbackUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    resizeMode: MutableState<Int> = remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) },
    selectedResolutionHeight: Int? = null,
    onAvailableResolutions: (List<VideoResolution>) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val primary = primaryUrl?.takeIf { it.isNotBlank() }
    val fallback = fallbackUrl?.takeIf { it.isNotBlank() }
    val initial = primary ?: fallback ?: return
    var currentUrl by remember(initial) { mutableStateOf(initial) }
    var isVideoReady by remember(initial) { mutableStateOf(false) }
    var videoFailed by remember(initial) { mutableStateOf(false) }
    val shouldPlay by rememberUpdatedState(isPlaying)
    val currentResizeMode by resizeMode

    val okHttpClient =
        remember {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) {
                        return@addInterceptor chain.proceed(
                            request
                                .newBuilder()
                                .header("User-Agent", CanvasPlaybackUserAgent)
                                .build(),
                        )
                    }

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    chain.proceed(
                        StreamClientUtils
                            .applyRequestProfile(
                                request.newBuilder(),
                                requestProfile,
                            ).build(),
                    )
                }.build()
        }
    val mediaSourceFactory =
        remember(okHttpClient) {
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    OkHttpDataSource.Factory(okHttpClient),
                ),
            )
        }
    val renderersFactory =
        remember(context) {
            DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        }
    val trackSelector =
        remember(context) {
            DefaultTrackSelector(context)
        }
    val exoPlayer =
        remember(initial, mediaSourceFactory, renderersFactory, trackSelector) {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    trackSelectionParameters =
                        trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                            .build()
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    playWhenReady = isPlaying
                }
        }

    // Discover available video resolutions when tracks change
    DisposableEffect(exoPlayer) {
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
                    onAvailableResolutions(resolutions.sortedByDescending { it.height })
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Apply resolution override when selectedResolutionHeight changes
    LaunchedEffect(selectedResolutionHeight, exoPlayer) {
        if (selectedResolutionHeight == null) {
            // Back to Auto: clear any manual video track override
            exoPlayer.trackSelectionParameters =
                exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            return@LaunchedEffect
        }

        val mappedTrackInfo = trackSelector.currentMappedTrackInfo ?: return@LaunchedEffect
        for (rendererIndex in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getRendererType(rendererIndex) != C.TRACK_TYPE_VIDEO) continue
            val trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex)
            for (groupIndex in 0 until trackGroups.length) {
                val group = trackGroups[groupIndex]
                for (trackIndex in 0 until group.length) {
                    val format = group.getFormat(trackIndex)
                    if (format.height == selectedResolutionHeight) {
                        val override =
                            TrackSelectionOverride(group, listOf(trackIndex))
                        exoPlayer.trackSelectionParameters =
                            exoPlayer.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(override)
                                .build()
                        return@LaunchedEffect
                    }
                }
            }
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    LaunchedEffect(currentUrl, isPlaying, primary, fallback, exoPlayer) {
        if (!isPlaying || fallback.isNullOrBlank() || currentUrl != primary) return@LaunchedEffect

        var lastPosition = exoPlayer.currentPosition
        var stalledForMs = 0L

        while (isActive && isPlaying && currentUrl == primary) {
            delay(CanvasPlaybackStallCheckIntervalMs)

            val currentPosition = exoPlayer.currentPosition
            val playbackState = exoPlayer.playbackState
            val positionAdvanced = currentPosition != lastPosition
            val isActivelyRendering =
                playbackState == Player.STATE_READY &&
                    exoPlayer.isPlaying &&
                    positionAdvanced

            stalledForMs =
                if (isActivelyRendering) {
                    0L
                } else {
                    stalledForMs + CanvasPlaybackStallCheckIntervalMs
                }

            if (stalledForMs >= CanvasPlaybackStallTimeoutMs) {
                currentUrl = fallback
                isVideoReady = false
                return@LaunchedEffect
            }

            lastPosition = currentPosition
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                    exoPlayer.setCanvasPlayback(shouldPlay)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(exoPlayer, primary, fallback) {
        val listener =
            object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.tag(CanvasPlaybackLogTag).w(error, "Canvas playback failed")
                    val next =
                        when (currentUrl) {
                            primary -> fallback?.takeIf { it != currentUrl }
                            else -> null
                        }
                    if (!next.isNullOrBlank()) {
                        currentUrl = next
                        isVideoReady = false
                    } else {
                        videoFailed = true
                    }
                }

                override fun onRenderedFirstFrame() {
                    isVideoReady = true
                    if (shouldPlay) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (!shouldPlay) return
                    exoPlayer.setCanvasPlayback(isPlaying = true)
                }

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    if (shouldPlay && !playWhenReady) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (shouldPlay && !isPlaying) {
                        exoPlayer.setCanvasPlayback(isPlaying = true)
                    }
                }
            }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(currentUrl, exoPlayer) {
        val normalized = currentUrl.trim()
        isVideoReady = false
        videoFailed = false
        val lowercaseUrl = normalized.lowercase(Locale.ROOT)
        val mimeType =
            when {
                lowercaseUrl.contains("m3u8") -> MimeTypes.APPLICATION_M3U8
                lowercaseUrl.contains("mp4") -> MimeTypes.VIDEO_MP4
                primary != null && currentUrl == primary -> MimeTypes.APPLICATION_M3U8
                fallback != null && currentUrl == fallback -> MimeTypes.VIDEO_MP4
                else -> MimeTypes.APPLICATION_M3U8
            }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(normalized)
                .setMimeType(mimeType)
                .build()

        exoPlayer.stop()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.setCanvasPlayback(isPlaying)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "canvasAlpha",
    )

    if (!videoFailed) {
        ContentFrame(
            player = exoPlayer,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = currentResizeMode.toContentScale(),
            keepContentOnReset = false,
            shutter = {},
            modifier = modifier.alpha(alpha),
        )
    }
}

internal fun Int.toContentScale(): ContentScale =
    when (this) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> ContentScale.Crop

        AspectRatioFrameLayout.RESIZE_MODE_FILL -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT,
        -> ContentScale.FillBounds

        RESIZE_MODE_STRETCH -> ContentScale.FillBounds

        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        -> ContentScale.Fit

        else -> ContentScale.Fit
    }

private fun ExoPlayer.setCanvasPlayback(isPlaying: Boolean) {
    if (isPlaying) {
        if (playbackState == Player.STATE_ENDED) seekTo(0)
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    } else {
        pause()
    }
}

private const val CanvasPlaybackLogTag = "CanvasPlayback"
private const val CanvasPlaybackUserAgent =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"