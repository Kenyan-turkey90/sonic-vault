/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.sonicvault.app.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Custom resize mode constant for stretch (fill bounds without aspect ratio preservation).
 * Mirrors the value used internally; mapped in [resizeModeLabel] and the caller's
 * [toContentScale] extension.
 */
internal const val RESIZE_MODE_STRETCH = 100

/**
 * Full-screen overlay with video resize mode toggle + resolution picker.
 * Designed to sit on top of [CanvasArtworkPlayer] in landscape/fullscreen mode.
 */
@Composable
fun VideoPlayerOverlayControls(
    resizeMode: MutableState<Int>,
    availableResolutions: List<VideoResolution>,
    selectedResolutionHeight: Int?,
    onResolutionSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showResizeMenu by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Top-right button cluster
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Resize mode toggle button
            OverlayButton(
                label = resizeModeLabel(resizeMode.value),
                onClick = { showResizeMenu = !showResizeMenu },
            )

            // Quality / resolution button (only if multiple resolutions available)
            if (availableResolutions.size > 1) {
                OverlayButton(
                    label =
                        if (selectedResolutionHeight != null) {
                            "${selectedResolutionHeight}p"
                        } else {
                            "Auto"
                        },
                    onClick = { showQualitySheet = true },
                )
            }
        }

        // Resize mode dropdown
        AnimatedVisibility(
            visible = showResizeMenu,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp),
        ) {
            ResizeModeMenu(
                currentMode = resizeMode.value,
                onModeSelected = { mode ->
                    resizeMode.value = mode
                    showResizeMenu = false
                },
                onDismiss = { showResizeMenu = false },
            )
        }
    }

    // Quality picker bottom sheet
    if (showQualitySheet) {
        QualityPickerSheet(
            resolutions = availableResolutions,
            selectedHeight = selectedResolutionHeight,
            onResolutionSelected = {
                onResolutionSelected(it)
                showQualitySheet = false
            },
            onDismiss = { showQualitySheet = false },
        )
    }
}

@Composable
private fun OverlayButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ResizeModeMenu(
    currentMode: Int,
    onModeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes =
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom",
            RESIZE_MODE_STRETCH to "Stretch",
        )

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(4.dp),
    ) {
        modes.forEach { (mode, label) ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = label,
                    color =
                        if (mode == currentMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.White
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (mode == currentMode) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun QualityPickerSheet(
    resolutions: List<VideoResolution>,
    selectedHeight: Int?,
    onResolutionSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Video Quality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Auto option
            QualityOption(
                label = "Auto",
                isSelected = selectedHeight == null,
                onClick = { onResolutionSelected(null) },
            )

            // Available resolutions (sorted highest first)
            resolutions.sortedByDescending { it.height }.forEach { resolution ->
                QualityOption(
                    label = resolution.label,
                    isSelected = resolution.height == selectedHeight,
                    onClick = { onResolutionSelected(resolution.height) },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QualityOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                ),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun resizeModeLabel(mode: Int): String =
    when (mode) {
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        RESIZE_MODE_STRETCH -> "Stretch"
        else -> "Fit"
    }