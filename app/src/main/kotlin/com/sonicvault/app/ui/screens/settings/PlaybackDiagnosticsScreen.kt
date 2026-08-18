/*
 * SonicVault (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package com.sonicvault.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sonicvault.app.LocalPlayerAwareWindowInsets
import com.sonicvault.app.R
import com.sonicvault.app.ui.component.IconButton
import com.sonicvault.app.ui.utils.backToMain
import com.sonicvault.app.utils.PlaybackDiagnosticsRecorder
import com.sonicvault.app.utils.PlaybackDiagnosticsRecorder.Entry
import com.sonicvault.app.utils.PlaybackDiagnosticsRecorder.Kind

@Composable
fun PlaybackDiagnosticsScreen(navController: NavController) {
    val entries by PlaybackDiagnosticsRecorder.entries.collectAsState()
    val totalErrors by PlaybackDiagnosticsRecorder.totalErrors.collectAsState()
    val totalRetries by PlaybackDiagnosticsRecorder.totalRetries.collectAsState()
    val totalRecoveries by PlaybackDiagnosticsRecorder.totalRecoveries.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.playback_diagnostics),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                PlaybackDiagnosticsRecorder.clear()
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.diagnostics_cleared),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            },
                        ) {
                            Icon(painterResource(R.drawable.clear_all), contentDescription = stringResource(R.string.diagnostics_clear))
                        }
                    }
                },
            )
        },
    ) { innerPadding: PaddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        ),
                    ),
        ) {
            DiagnosticsSummaryRow(
                errors = totalErrors,
                retries = totalRetries,
                recoveries = totalRecoveries,
            )
            if (entries.isEmpty()) {
                DiagnosticsEmptyState(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = entries,
                        key = { it.id },
                    ) { entry ->
                        DiagnosticsEntryCard(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsSummaryRow(
    errors: Long,
    retries: Long,
    recoveries: Long,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticsSummaryChip(
            label = stringResource(R.string.diagnostics_errors),
            value = errors.toString(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        DiagnosticsSummaryChip(
            label = stringResource(R.string.diagnostics_retries),
            value = retries.toString(),
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.weight(1f),
        )
        DiagnosticsSummaryChip(
            label = stringResource(R.string.diagnostics_recoveries),
            value = recoveries.toString(),
            color = Color(0xFF43B581),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiagnosticsSummaryChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsEntryCard(entry: Entry) {
    val kindColor =
        when (entry.kind) {
            Kind.ERROR -> MaterialTheme.colorScheme.error
            Kind.RETRY -> MaterialTheme.colorScheme.tertiary
            Kind.RECOVERED -> Color(0xFF43B581)
            Kind.SKIPPED -> MaterialTheme.colorScheme.secondary
            Kind.STOPPED -> MaterialTheme.colorScheme.outline
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = kindColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter =
                                painterResource(
                                    when (entry.kind) {
                                        Kind.ERROR -> R.drawable.error
                                        Kind.RETRY -> R.drawable.slow_motion_video
                                        Kind.RECOVERED -> R.drawable.status
                                        Kind.SKIPPED -> R.drawable.skip_next
                                        Kind.STOPPED -> R.drawable.pause
                                    },
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = kindColor,
                        )
                    }
                }
                Text(
                    text =
                        when (entry.kind) {
                            Kind.ERROR -> stringResource(R.string.diagnostics_error)
                            Kind.RETRY -> stringResource(R.string.diagnostics_retry)
                            Kind.RECOVERED -> stringResource(R.string.diagnostics_recovered)
                            Kind.SKIPPED -> stringResource(R.string.diagnostics_skipped)
                            Kind.STOPPED -> stringResource(R.string.diagnostics_stopped)
                        },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = kindColor,
                )
                if (entry.retryCount > 0 && entry.kind != Kind.ERROR) {
                    Text(
                        text = stringResource(R.string.diagnostics_retry_count, entry.retryCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = entry.elapsedFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!entry.title.isNullOrBlank()) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (entry.errorCode != null || !entry.errorReason.isNullOrBlank()) {
                Text(
                    text =
                        listOfNotNull(
                            entry.errorCode?.let { "code=$it" },
                            entry.errorReason,
                        ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (!entry.detail.isNullOrBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DiagnosticsMetaBadge(
                    text =
                        if (entry.networkMetered) {
                            stringResource(R.string.diagnostics_cellular)
                        } else {
                            stringResource(R.string.diagnostics_wifi)
                        },
                    icon = if (entry.networkMetered) R.drawable.android_cell else R.drawable.wifi_proxy,
                )
                if (entry.wasCached) {
                    DiagnosticsMetaBadge(
                        text = stringResource(R.string.diagnostics_cached),
                        icon = R.drawable.download,
                    )
                }
                if (!entry.mediaId.isNullOrBlank()) {
                    Text(
                        text = entry.mediaId.take(12),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsMetaBadge(
    text: String,
    icon: Int,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiagnosticsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.info),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.diagnostics_no_entries),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.diagnostics_no_entries_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
